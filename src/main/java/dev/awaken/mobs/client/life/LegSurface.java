package dev.awaken.mobs.client.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * What a foot finds in the blocks (SPEC §23.1, "On real ground"). No client classes, so a test can walk feet on any world.
 * Reads block shapes in place, with no allocation: it runs for feet every frame.
 */
public class LegSurface implements Legs.Ground {
    /** How far apart a leg's line is sampled (blocks), and at most how many samples. */
    private static final double SAMPLE = 0.25;
    private static final int MOST = 16;

    private final Supplier<? extends BlockGetter> world;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    public LegSurface(Supplier<? extends BlockGetter> world) {
        this.world = world;
    }

    /** The top of the ground under (x, z), from a block above the body's feet down to three below; y if none. */
    @Override
    public double top(double x, double y, double z) {
        BlockGetter level = world.get();
        if (level == null) return y;
        probe.set(Mth.floor(x), Mth.floor(y + 0.99), Mth.floor(z));
        for (int k = 0; k < 4; k++) {
            VoxelShape shape = level.getBlockState(probe).getCollisionShape(level, probe);
            if (!shape.isEmpty()) {
                double top = probe.getY() + shape.max(Direction.Axis.Y);
                if (top <= y + 1.0) return Math.max(top, y - 1.0);
            }
            probe.move(0, -1, 0);
        }
        return y;
    }

    /**
     * The surface in the foot's own column, or NaN where a foot can't stand: higher than a step (a wall), more than a step
     * down (a drop), or with something solid just above it.
     */
    @Override
    public double footing(double x, double y, double z) {
        BlockGetter level = world.get();
        if (level == null) return y;
        int cx = Mth.floor(x), cz = Mth.floor(z);
        double lx = x - cx, lz = z - cz;
        for (int cell = Mth.floor(y + 0.99), last = Mth.floor(y - 1.0) - 1; cell >= last; cell--) {
            probe.set(cx, cell, cz);
            VoxelShape shape = level.getBlockState(probe).getCollisionShape(level, probe);
            if (shape.isEmpty()) continue;
            double high = shape.max(Direction.Axis.Y, lz, lx); // for Y the two others come Z first, then X
            if (high == Double.NEGATIVE_INFINITY) continue; // a fence's post, a wall's arm: this column of it is open
            double top = cell + high;
            if (top > y + 1.0 || top < y - 1.0) return Double.NaN;
            return solid(level, cx, top + 0.05, cz, lx, lz) ? Double.NaN : top;
        }
        return Double.NaN;
    }

    /** Whether a straight leg between these two points passes through nothing (the two ends themselves aren't counted). */
    @Override
    public boolean clear(double x0, double y0, double z0, double x1, double y1, double z1) {
        BlockGetter level = world.get();
        if (level == null) return true;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int n = Math.min(MOST, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / SAMPLE));
        for (int k = 1; k < n; k++) {
            double t = (double) k / n, x = x0 + dx * t, y = y0 + dy * t, z = z0 + dz * t;
            int cx = Mth.floor(x), cz = Mth.floor(z);
            if (solid(level, cx, y, cz, x - cx, z - cz)) return false;
        }
        return true;
    }

    private boolean solid(BlockGetter level, int cx, double y, int cz, double lx, double lz) {
        int cell = Mth.floor(y);
        probe.set(cx, cell, cz);
        VoxelShape shape = level.getBlockState(probe).getCollisionShape(level, probe);
        if (shape.isEmpty()) return false;
        double ly = y - cell;
        return ly >= shape.min(Direction.Axis.Y, lz, lx) && ly <= shape.max(Direction.Axis.Y, lz, lx);
    }
}
