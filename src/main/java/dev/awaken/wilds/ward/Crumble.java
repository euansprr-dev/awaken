package dev.awaken.wilds.ward;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.UUID;

/**
 * Blocks placed inside a ward, waiting to crumble. Every block waits the same {@link #DELAY}, so the queue is in due
 * order and a tick only looks at its head. When one is due and still the block that was placed, it comes apart (the
 * grains and the chime of {@link WardFx}) and the item goes back to whoever placed it: into their inventory, at their
 * feet when it's full, where the block stood when they've gone. A container drops what was put in it, as breaking it
 * would; a shulker box keeps its contents in the item that comes back.
 */
public final class Crumble {
    private Crumble() {}

    /** Three seconds (SPEC §17). */
    public static final int DELAY = 60;
    /** A placer further than this gets the item where the block stood (they left; it waits for them there). */
    static final double RETURN_RANGE = 64;

    record Pending(ResourceKey<Level> dimension, BlockPos pos, BlockState placed, ItemStack item, UUID placer, long due) {}

    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();

    /** Queues a block a player just placed inside a ward. */
    static void queue(ServerLevel level, BlockPos pos, BlockState placed, ItemStack item, ServerPlayer placer) {
        QUEUE.addLast(new Pending(level.dimension(), pos.immutable(), placed, item, placer.getUUID(), level.getGameTime() + DELAY));
        WardFx.play(level, WardFx.Kind.MARK, pos, placed);
    }

    static int pending() {
        return QUEUE.size();
    }

    static void tick(MinecraftServer server) {
        while (!QUEUE.isEmpty()) {
            Pending p = QUEUE.peekFirst();
            ServerLevel level = server.getLevel(p.dimension());
            if (level != null && level.getGameTime() < p.due()) return;
            QUEUE.pollFirst();
            if (level != null) crumble(server, level, p);
        }
    }

    /** Server stopping: everything still waiting crumbles now, so leaving can't keep a block in a ward. */
    static void flush(MinecraftServer server) {
        while (!QUEUE.isEmpty()) {
            Pending p = QUEUE.pollFirst();
            ServerLevel level = server.getLevel(p.dimension());
            if (level != null) crumble(server, level, p);
        }
    }

    static void clear() {
        QUEUE.clear();
    }

    /** GameTests: crumble everything due by {@code now} in this level, whatever the clock says. */
    static int runDue(ServerLevel level, long now) {
        int n = 0;
        var it = QUEUE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.dimension() != level.dimension() || p.due() > now) continue;
            it.remove();
            if (crumble(level.getServer(), level, p)) n++;
        }
        return n;
    }

    /** Takes the block back and returns its item. False when it was already gone or changed (nothing to give back). */
    private static boolean crumble(MinecraftServer server, ServerLevel level, Pending p) {
        if (!level.hasChunkAt(p.pos())) return false; // unloaded: it stays; wards hold where someone is looking
        BlockState now = level.getBlockState(p.pos());
        if (!now.is(p.placed().getBlock())) return false;
        ItemStack back = p.item().copy();
        BlockEntity be = level.getBlockEntity(p.pos());
        // a shulker box never spills: its contents ride home in the item, the way breaking it would carry them
        if (be instanceof ShulkerBoxBlockEntity) back.applyComponents(be.collectComponents());
        level.setBlock(p.pos(), now.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        WardFx.play(level, WardFx.Kind.CRUMBLE, p.pos(), now);
        giveBack(server, level, p, back);
        return true;
    }

    private static void giveBack(MinecraftServer server, ServerLevel level, Pending p, ItemStack back) {
        if (back.isEmpty()) return;
        ServerPlayer placer = server.getPlayerList().getPlayer(p.placer());
        boolean near = placer != null && placer.level() == level
                && placer.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5) <= RETURN_RANGE * RETURN_RANGE;
        if (near && placer.getInventory().add(back) && back.isEmpty()) return;
        double x = near ? placer.getX() : p.pos().getX() + 0.5, y = near ? placer.getY() + 0.2 : p.pos().getY() + 0.25, z = near ? placer.getZ() : p.pos().getZ() + 0.5;
        ItemEntity drop = new ItemEntity(level, x, y, z, back, 0, 0.1, 0);
        drop.setNoPickUpDelay();
        if (near) drop.setTarget(placer.getUUID());
        level.addFreshEntity(drop);
    }

    /** The item a placement used: the matching block item in either hand, else the block's own item. */
    static ItemStack itemFor(ServerPlayer p, BlockState placed) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = p.getItemInHand(hand);
            if (held.getItem() instanceof BlockItem bi && bi.getBlock() == placed.getBlock()) return held.copyWithCount(1);
        }
        return new ItemStack(placed.getBlock().asItem());
    }
}
