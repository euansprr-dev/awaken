package dev.awaken.finds;

import dev.awaken.living.region.RegionMap;
import net.minecraft.core.BlockPos;

/**
 * One find standing in the world (SPEC §58.4): its kind, the ground block at its middle, its seed and quarter turn, and
 * the land it's in. A pure function of the world's seed and its cell, so nothing about it is saved.
 */
public record Site(Kind kind, int layer, int x, int y, int z, long seed, int rot, RegionMap.Group group, int rank) {
    /** The heart block: two under the ground, where no scene builds and nobody digs by accident. */
    public BlockPos heart() {
        return new BlockPos(x, y - 2, z);
    }

    public BlockPos ground() {
        return new BlockPos(x, y, z);
    }

    public String key() {
        return kind.id() + "@" + x + "," + z;
    }

    /** Whether a position lies in the site's box, with {@code extra} blocks of margin. */
    public boolean holds(BlockPos p, int extra) {
        int r = kind.reach() + extra;
        return Math.abs(p.getX() - x) <= r && Math.abs(p.getZ() - z) <= r && p.getY() >= y - 64 && p.getY() <= y + 128;
    }

    public BlockPos world(BlockPos rel) {
        return new BlockPos(x + rel.getX(), y + rel.getY(), z + rel.getZ());
    }

    public BlockPos rel(BlockPos world) {
        return new BlockPos(world.getX() - x, world.getY() - y, world.getZ() - z);
    }
}
