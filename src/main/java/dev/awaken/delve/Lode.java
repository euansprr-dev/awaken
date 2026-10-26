package dev.awaken.delve;

import net.minecraft.core.BlockPos;

/**
 * One lode (PLAN 7.3): a seam of one ore, its blocks ({@code cells}, {@code BlockPos.asLong}, the heart first) and the
 * box round them. Pure data from the world seed ({@link LodePlans}); worldgen writes it, everything else reads it.
 * {@code id} is the heart's position, unique in a world.
 */
public record Lode(long id, Ore ore, long heart, long[] cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public BlockPos heartPos() {
        return BlockPos.of(heart);
    }

    public int size() {
        return cells.length;
    }

    public boolean touches(int x0, int z0, int x1, int z1) {
        return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }

    public boolean holds(long pos) {
        int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
        for (long c : cells) if (c == pos) return true;
        return false;
    }

    /** Squared distance from (x, y, z) to the nearest of its blocks' centres, and that block in {@code out[0]}. */
    public double nearest(double x, double y, double z, long[] out) {
        double best = Double.MAX_VALUE;
        for (long c : cells) {
            double dx = BlockPos.getX(c) + 0.5 - x, dy = BlockPos.getY(c) + 0.5 - y, dz = BlockPos.getZ(c) + 0.5 - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                out[0] = c;
            }
        }
        return best;
    }
}
