package dev.awaken.delve;

import dev.awaken.delve.api.Lodes;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Delver's Pick (SPEC §34.4), item {@code awaken:waking_pick} (the cave module's relic keeps {@code delvers_pick}:
 * the Delver's Old Pick). Found stuck in the stone at the end of an adit (each player pulls their own; a home with no
 * adit near hands it over instead), it starts asleep: it mines like a stone pick and <b>hums</b> when its head points
 * at a lode within {@value #HUM_REACH} (the server names the nearest lode while you hold it; the client sings it at
 * that ore's note as you face it). Kept through death; its verses ({@link PickVerses}) and their gifts ({@link PickGifts}).
 */
public final class WakingPick {
    private WakingPick() {}

    public static final int HUM_REACH = 16;
    /** How many times the player has pulled the pick from the stone or been handed it (0: never). */
    public static final String PULLED = "awaken:delve/pick_pulled";

    public static final class PickItem extends Item {
        public PickItem(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            int v = Math.max(0, PickVerses.verse(stack));
            out.accept(Component.literal(PickVerses.VERSES[v].title).withStyle(ChatFormatting.DARK_PURPLE));
            for (int i = 0; i <= v; i++) out.accept(Component.literal(PickVerses.VERSES[i].gift).withStyle(i == v ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            if (v + 1 < PickVerses.VERSES.length) out.accept(Component.literal("Next: " + PickVerses.wakesWhen(PickVerses.VERSES[v + 1])).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    public static boolean carries(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(DelveContent.WAKING_PICK.get())) return true;
        return false;
    }

    /** The pick in the stone, used: your own pick, if you don't carry one. The pick in the stone stays for whoever comes next. */
    public static boolean pull(ServerPlayer p, BlockPos at) {
        if (carries(p)) {
            p.sendOverlayMessage(Component.literal("You already carry the Delver's Pick. This one stays for whoever comes next.").withColor(0xC8C2B0));
            return false;
        }
        boolean first = Progression.profile(p).counter(PULLED) == 0;
        give(p);
        p.level().playSound(null, at, SoundEvents.TRIAL_SPAWNER_EJECT_ITEM, SoundSource.PLAYERS, 0.7F, 0.6F);
        p.level().playSound(null, at, DelveContent.TRUE_STRIKE.get(), SoundSource.PLAYERS, 0.5F, 0.7F);
        if (first) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Delver's Pick"))
                    .body(Component.literal("It's asleep, but it still knows metal. Hold it and it hums when its head points at a lode nearby."))
                    .moment().ttl(20 * 12).build());
        }
        return true;
    }

    /** Where no adit is near: the Hearth hands the pick over, once (and again only if you've lost yours). */
    public static void handOver(ServerPlayer p) {
        if (carries(p) || Progression.profile(p).counter(PULLED) > 0) return;
        give(p);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Delver's Pick"))
                .body(Component.literal("There's no old adit near your home, so your Hearth kept this for you: a Delver's pick, asleep. Hold it and it hums when its head points at a lode nearby."))
                .moment().ttl(20 * 12).build());
    }

    /** The Forge makes a lost pick again, at the furthest verse you'd woken (SPEC §34.4). */
    static void reforge(ServerPlayer p) {
        give(p);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Delver's Pick, again"))
                .body(Component.literal("The Forge remembers the pick you lost. This one wakes where yours had got to: " + PickVerses.VERSES[PickVerses.best(p)].title + "."))
                .moment().ttl(20 * 10).build());
    }

    /** A pick into the player's hands, at the furthest verse they'd woken (a pick lost keeps its verses on the player). */
    private static void give(ServerPlayer p) {
        Progression.count(p, PULLED);
        ItemStack pick = new ItemStack(DelveContent.WAKING_PICK.get());
        PickVerses.set(pick, PickVerses.best(p), p.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));
        if (!p.getInventory().add(pick)) p.drop(pick, false, net.minecraft.util.Prediction.SERVER_ONLY);
        PickVerses.check(p);
    }

    // ---- kept through death -------------------------------------------------------------------------------------------

    private static final Map<UUID, List<ItemStack>> HELD = new HashMap<>();

    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = new ArrayList<>();
        e.getDrops().removeIf(ie -> {
            if (!ie.getItem().is(DelveContent.WAKING_PICK.get())) return false;
            kept.add(ie.getItem().copy());
            return true;
        });
        if (!kept.isEmpty()) HELD.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).addAll(kept);
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = HELD.remove(p.getUUID());
        if (kept == null) return;
        for (ItemStack st : kept) p.getInventory().placeItemBackInInventory(st, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- the hum --------------------------------------------------------------------------------------------------------

    /** What each holder was last told (ore ordinal and block; -1 none), so the client hears only changes. */
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    /** Twice a second: for each player holding the pick, the nearest lode within reach of their eyes (any ore). */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean holding = p.getMainHandItem().is(DelveContent.WAKING_PICK.get());
            Long was = TOLD.get(p.getUUID());
            if (!holding || !(p.level() instanceof ServerLevel level)) {
                if (was != null && was != -1L) DelveNet.send(p, new DelveNet.PickHum(-1, 0L));
                if (was != null) TOLD.put(p.getUUID(), -1L);
                continue;
            }
            Optional<Lodes.View> best = nearest(level, BlockPos.containing(p.getEyePosition()));
            int ore = best.map(v -> v.ore().ordinal()).orElse(-1);
            long at = best.map(v -> v.nearest().asLong()).orElse(0L);
            long key = ore < 0 ? -1L : at * 31 + ore;
            if (was != null && was == key) continue;
            TOLD.put(p.getUUID(), key);
            DelveNet.send(p, new DelveNet.PickHum(ore, at));
        }
    }

    /** The nearest lode of any ore with a block within {@value #HUM_REACH} of {@code eye} (a lone broken heart doesn't count). */
    static Optional<Lodes.View> nearest(ServerLevel level, BlockPos eye) {
        Lodes.View best = null;
        for (Ore o : Ore.values()) {
            Optional<Lodes.View> v = Lodes.nearest(level, eye, o, HUM_REACH).filter(x -> !x.heartBroken() || x.size() > 1);
            if (v.isPresent() && v.get().distance() <= HUM_REACH && (best == null || v.get().distance() < best.distance())) best = v.get();
        }
        return Optional.ofNullable(best);
    }

    static void forget(UUID id) {
        TOLD.remove(id);
    }

    static void clear() {
        TOLD.clear();
        HELD.clear();
    }
}
