package dev.awaken.hearth.settle;

import dev.awaken.hearth.HearthRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Hearth's chests and barrels, and putting things away in them (one that already holds the item first). Whoever does
 * the carrying at home uses this (SPEC §43.6: the beasts carry and sort; nothing here makes items).
 */
public final class Chests {
    private Chests() {}

    private record Cached(long at, List<BlockPos> list) {}
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    public static void clear() {
        CACHE.clear();
    }

    public static void forget(HearthRecord h) {
        CACHE.remove(h.owner);
    }

    /** Chests and barrels inside the Hearth (cached for 30 s). */
    public static List<BlockPos> all(ServerLevel level, HearthRecord h) {
        long now = level.getGameTime();
        Cached c = CACHE.get(h.owner);
        if (c != null && now - c.at < 600) return c.list;
        List<BlockPos> out = new ArrayList<>();
        int r = h.radius();
        for (int cx = SectionPos.blockToSectionCoord(h.pos.getX() - r); cx <= SectionPos.blockToSectionCoord(h.pos.getX() + r); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(h.pos.getZ() - r); cz <= SectionPos.blockToSectionCoord(h.pos.getZ() + r); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && h.contains(be.getBlockPos())) out.add(be.getBlockPos());
                }
            }
        }
        CACHE.put(h.owner, new Cached(now, out));
        return out;
    }

    /**
     * Puts a stack in the chests within {@code reach} of {@code near} (one already holding it first): what didn't fit
     * comes back (the carrier keeps it to hand over, rather than drop it).
     */
    public static ItemStack put(ServerLevel level, HearthRecord h, ItemStack stack, BlockPos near, int reach) {
        ItemStack left = stack;
        for (int tries = 0; tries < 3 && !left.isEmpty(); tries++) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos p : all(level, h)) {
                double d = p.distSqr(near);
                if (d > (double) reach * reach || !(level.getBlockEntity(p) instanceof Container c) || !hasRoom(c, left)) continue;
                if (holds(c, left.getItem())) d -= 10_000; // a chest that already holds it, if one's in reach
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            if (best == null || !(level.getBlockEntity(best) instanceof Container c)) break;
            left = HopperBlockEntity.addItem(null, c, left, null);
            c.setChanged();
        }
        return left;
    }

    /** Puts a stack away anywhere at home (sorting by what's already in the chests) and drops what doesn't fit at {@code near}. */
    public static void store(ServerLevel level, HearthRecord h, ItemStack stack, BlockPos near) {
        ItemStack left = stack;
        for (int tries = 0; tries < 3 && !left.isEmpty(); tries++) {
            BlockPos p = choose(level, h, left, near);
            if (p == null || !(level.getBlockEntity(p) instanceof Container c)) break;
            left = HopperBlockEntity.addItem(null, c, left, null);
            c.setChanged();
        }
        if (!left.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, left);
            drop.setPickUpDelay(40);
            level.addFreshEntity(drop);
        }
    }

    /** Where a stack should go: a chest that already holds it, otherwise the nearest chest within 16 of {@code near}. */
    static @Nullable BlockPos choose(ServerLevel level, HearthRecord h, ItemStack stack, BlockPos near) {
        BlockPos matching = null, nearest = null;
        double dm = Double.MAX_VALUE, dn = Double.MAX_VALUE;
        for (BlockPos p : all(level, h)) {
            if (!(level.getBlockEntity(p) instanceof Container c) || !hasRoom(c, stack)) continue;
            double d = p.distSqr(near);
            if (holds(c, stack.getItem()) && d < dm) {
                dm = d;
                matching = p;
            }
            if (d < dn && d <= 16 * 16) {
                dn = d;
                nearest = p;
            }
        }
        return matching != null ? matching : nearest;
    }

    static boolean holds(Container c, Item item) {
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
        return false;
    }

    static boolean hasRoom(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }
}
