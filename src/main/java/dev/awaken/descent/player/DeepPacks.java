package dev.awaken.descent.player;

import dev.awaken.core.AwakenConfig;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.DescentModule;
import dev.awaken.descent.DescentSync;
import dev.awaken.descent.Strata;
import dev.awaken.descent.entity.GraveEcho;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Deep Pack in play: pickups inside the Descent go into it, resting at a Waystone or climbing out banks it, and
 * dying leaves a Grave Echo that holds it (difficulty rules in {@link DeathRules}).
 */
public final class DeepPacks {
    private DeepPacks() {}

    private static final Map<UUID, Long> FULL_WARNED = new HashMap<>();

    public static boolean inDescent(ServerPlayer player) {
        return Strata.isDescent(player.level().dimension());
    }

    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isSpectator() || !inDescent(player)) return;
        if (!DescentModule.enabled()) return;
        ItemEntity item = event.getItemEntity();
        // things you dropped yourself are already yours
        if (item.getOwner() == player) return;
        event.setCanPickup(TriState.FALSE);
        if (item.hasPickUpDelay() || item.isRemoved()) return;
        UUID target = item.getTarget();
        if (target != null && !target.equals(player.getUUID())) return;
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        DeepPack pack = DescentSync.pack(player);
        int moved = pack.insert(stack);
        if (moved > 0) {
            player.take(item, moved);
            if (stack.isEmpty()) {
                item.discard();
                stack.setCount(before);
            }
            DescentSync.packChanged(player);
        } else {
            long now = player.level().getGameTime();
            Long last = FULL_WARNED.get(player.getUUID());
            if (last == null || now - last > 20 * 15) {
                FULL_WARNED.put(player.getUUID(), now);
                Signal.toast(player, Component.literal("Deep Pack full · rest at a Waystone to make it yours"));
            }
        }
    }

    /** Moves the Deep Pack into the inventory (what doesn't fit drops at your feet, still yours). */
    public static int bank(ServerPlayer player, String why) {
        DeepPack pack = DescentSync.pack(player);
        if (pack.isEmpty()) return 0;
        int items = pack.totalItems();
        List<ItemStack> stacks = pack.takeAll();
        for (ItemStack s : stacks) {
            if (!player.getInventory().add(s) && !s.isEmpty()) player.drop(s, false, Prediction.SERVER_ONLY);
        }
        DescentSync.packChanged(player);
        Signal.toast(player, Component.literal(why + " The Deep Pack is yours: " + items + " item" + (items == 1 ? "" : "s") + "."));
        return items;
    }

    /** Death inside the Descent: split the pack by difficulty, leave a Grave Echo where you fell. */
    public static void onDeath(ServerPlayer player) {
        ServerLevel level = player.level();
        DescentData d = DescentSync.data(player);
        d.diedInDescent = true;
        DeepPack pack = DescentSync.pack(player);
        List<ItemStack> all = pack.takeAll();
        DescentSync.packChanged(player);
        if (all.isEmpty()) return;
        if (dev.awaken.above.Above.consumeCloneSave(player)) {
            // The Clone Vat grew a body for you: everything you carried comes back with it.
            d.pendingReturn.addAll(all);
            dev.awaken.signal.Signal.toast(player, net.minecraft.network.chat.Component.literal("The Clone Vat kept your Deep Pack"));
            return;
        }
        AwakenConfig.Difficulty diff = DescentModule.difficulty();
        DeathRules.Split split = DeathRules.split(diff, all);
        d.pendingReturn.addAll(split.kept());
        if (split.echo().isEmpty()) return;
        BlockPos at = safeGravePos(player);
        GraveEcho echo = GraveEcho.create(level, at, player, split.echo());
        if (echo == null) return;
        int depth = Strata.depthAt(level.dimension(), at);
        d.graves.add(new DescentData.Grave(level.dimension().identifier().toString(), at, echo.getUUID(), depth));
        int max = DeathRules.maxEchoes(diff);
        while (d.graves.size() > max) {
            DescentData.Grave old = d.graves.removeFirst();
            // a loaded echo crumbles now; an unloaded one crumbles when it next loads and finds it was forgotten
            ServerLevel l = player.level().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(old.dimension())));
            if (l != null && l.getEntity(old.id()) instanceof GraveEcho g) g.crumble();
        }
    }

    private static BlockPos safeGravePos(ServerPlayer player) {
        BlockPos p = player.blockPosition();
        int minY = player.level().getMinY() + 2;
        if (p.getY() < minY) p = new BlockPos(p.getX(), minY, p.getZ());
        if (p.getY() > player.level().getMaxY() - 2) p = new BlockPos(p.getX(), player.level().getMaxY() - 2, p.getZ());
        return p;
    }

    /** After respawning: give back what STORY difficulty kept, and point at the Grave Echo. */
    public static void afterRespawn(ServerPlayer player) {
        DescentData d = DescentSync.data(player);
        if (!d.pendingReturn.isEmpty()) {
            int n = 0;
            for (ItemStack s : d.pendingReturn) {
                n += s.getCount();
                ItemStack c = s.copy();
                if (!player.getInventory().add(c) && !c.isEmpty()) player.drop(c, false, Prediction.SERVER_ONLY);
            }
            d.pendingReturn.clear();
            Signal.toast(player, Component.literal("Story difficulty kept half your Deep Pack: " + n + " items."));
        }
        if (!d.graves.isEmpty()) {
            DescentData.Grave g = d.graves.getLast();
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Your Grave Echo waits"))
                    .row(Component.literal("Where"), Component.literal(Strata.label(g.depth())))
                    .row(Component.literal("At"), Component.literal(g.pos().getX() + " " + g.pos().getY() + " " + g.pos().getZ()))
                    .body(Component.literal("It holds your Deep Pack. Reach it to take everything back."))
                    .footer(Component.literal(DeathRules.maxEchoes(DescentModule.difficulty()) == 1
                            ? "Die again before you reach it and it is gone" : "It is marked on your screen"))
                    .ttl(20 * 10).build());
        }
    }

    /** The owner reached their Grave Echo: everything returns to the Deep Pack (or the inventory outside it). */
    public static void recover(ServerPlayer player, GraveEcho echo) {
        DescentData d = DescentSync.data(player);
        d.graves.removeIf(g -> g.id().equals(echo.getUUID()));
        List<ItemStack> items = echo.takeItems();
        int n = 0;
        DeepPack pack = DescentSync.pack(player);
        boolean descent = inDescent(player);
        for (ItemStack s : items) {
            n += s.getCount();
            if (descent) pack.insert(s);
            if (!s.isEmpty() && !player.getInventory().add(s) && !s.isEmpty()) player.drop(s, false, Prediction.SERVER_ONLY);
        }
        DescentSync.packChanged(player);
        DescentSync.push(player);
        player.level().playSound(null, echo.blockPosition(), DescentContent.SND_GRAVE_RECOVER.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        Signal.toast(player, Component.literal("You took back your Grave Echo: " + n + " items."));
    }

    public static void forget(UUID id) {
        FULL_WARNED.remove(id);
    }
}
