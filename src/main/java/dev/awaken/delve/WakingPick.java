package dev.awaken.delve;

import dev.awaken.delve.api.Lodes;
import dev.awaken.delve.api.Pick;
import dev.awaken.delve.forged.DelveForged;
import dev.awaken.delve.forged.ForgedPicks;
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
 * the Delver's Old Pick). Nothing makes one now: the stone at the end of an adit gives its old head (§49.1) and picks are
 * forged. Saves that hold one keep it for good: it mines as it did, its verses ({@link PickVerses}) wake as they did,
 * and a War Anvil remakes it for free. Any pick that {@link Pick.Gift#HUM hums} does so here (the server names the nearest
 * lode while you hold it; the client sings it at that ore's note as you face it). Picks are kept through death.
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

    /**
     * The pick in the stone, used: its old head comes away in your hand, once a player, ever (SPEC §49.1). Anyone who pulled
     * the finished pick before this was the way already has what it would make. The stone stays for whoever comes next.
     */
    public static boolean pull(ServerPlayer p, BlockPos at) {
        if (Progression.profile(p).counter(PULLED) > 0) {
            p.sendOverlayMessage(Component.literal("You've already had yours from this stone. It stays for whoever comes next.").withColor(0xC8C2B0));
            return false;
        }
        give(p);
        p.level().playSound(null, at, SoundEvents.TRIAL_SPAWNER_EJECT_ITEM, SoundSource.PLAYERS, 0.7F, 0.6F);
        p.level().playSound(null, at, DelveContent.TRUE_STRIKE.get(), SoundSource.PLAYERS, 0.5F, 0.7F);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Delver's Old Head"))
                .body(Component.literal("The haft has rotted away, but the head is good. Take it to a War Anvil with a log and some string, and you can hang it on a new haft yourself."))
                .moment().ttl(20 * 14).build());
        return true;
    }

    /** Where no adit is near: the Hearth hands the head over, once. */
    public static void handOver(ServerPlayer p) {
        if (Progression.profile(p).counter(PULLED) > 0) return;
        give(p);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Delver's Old Head"))
                .body(Component.literal("There's no old adit near your home, so your Hearth kept this for you: the head of a Delver's pick. Take it to a War Anvil with a log and some string, and you can hang it on a new haft yourself."))
                .moment().ttl(20 * 14).build());
    }

    private static void give(ServerPlayer p) {
        Progression.count(p, PULLED);
        ItemStack head = new ItemStack(DelveForged.OLD_HEAD.get());
        if (!p.getInventory().add(head)) p.drop(head, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- kept through death -------------------------------------------------------------------------------------------

    private static final Map<UUID, List<ItemStack>> HELD = new HashMap<>();

    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = new ArrayList<>();
        e.getDrops().removeIf(ie -> {
            if (!kept(ie.getItem())) return false;
            kept.add(ie.getItem().copy());
            return true;
        });
        if (!kept.isEmpty()) HELD.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).addAll(kept);
    }

    /** A pick is yours for a career (SPEC §49.3): the old one, any forged one, and the head until it's hung. */
    private static boolean kept(ItemStack s) {
        return s.is(DelveContent.WAKING_PICK.get()) || ForgedPicks.is(s) || s.is(DelveForged.OLD_HEAD.get());
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
            int reach = Pick.has(p.getMainHandItem(), Pick.Gift.HUM) ? Pick.humReach(p.getMainHandItem()) : 0;
            boolean holding = reach > 0;
            Long was = TOLD.get(p.getUUID());
            if (!holding || !(p.level() instanceof ServerLevel level)) {
                if (was != null && was != -1L) DelveNet.send(p, new DelveNet.PickHum(-1, 0L));
                if (was != null) TOLD.put(p.getUUID(), -1L);
                continue;
            }
            Optional<Lodes.View> best = nearest(level, BlockPos.containing(p.getEyePosition()), reach);
            int ore = best.map(v -> v.ore().ordinal()).orElse(-1);
            long at = best.map(v -> v.nearest().asLong()).orElse(0L);
            long key = ore < 0 ? -1L : at * 31 + ore;
            if (was != null && was == key) continue;
            TOLD.put(p.getUUID(), key);
            DelveNet.send(p, new DelveNet.PickHum(ore, at));
        }
    }

    /** The nearest lode of any ore with a block within {@code reach} of {@code eye} (a lone broken heart doesn't count). */
    public static Optional<Lodes.View> nearest(ServerLevel level, BlockPos eye, int reach) {
        Lodes.View best = null;
        for (Ore o : Ore.values()) {
            Optional<Lodes.View> v = Lodes.nearest(level, eye, o, reach).filter(x -> !x.heartBroken() || x.size() > 1);
            if (v.isPresent() && v.get().distance() <= reach && (best == null || v.get().distance() < best.distance())) best = v.get();
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
