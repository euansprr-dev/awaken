package dev.awaken.powers;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a ground wave can really go (SPEC §50.4). It rides the top of the ground: up a step of two at most, down a drop of four
 * at most, and across water. A wall or a cliff ends it. The server asks who it reaches and every client walks the same ground
 * to pick the blocks that ripple, so what you see moving is what gets hit.
 */
public final class WaveSurface {
    private WaveSurface() {}

    public static final int NONE = Integer.MIN_VALUE;
    public static final int CLIMB = 2, DROP = 4;
    /** How far below a caster the wave still finds ground to start on. */
    private static final int START_DROP = 6;
    public static final double STEP = 0.7;

    /** Something a wave can ride or be stopped by: anything with a body, and the top of a fluid. */
    public static boolean stands(Level level, BlockPos p, BlockState s) {
        if (s.isAir()) return false;
        return !s.getFluidState().isEmpty() || !s.getCollisionShape(level, p).isEmpty();
    }

    /** The y of the top block of the ground near {@code y} in this column, or {@link #NONE} (a wall, a drop, an unloaded chunk). */
    public static int top(Level level, BlockPos.MutableBlockPos p, int x, int y, int z, int up, int down) {
        p.set(x, y, z);
        if (!level.isLoaded(p)) return NONE;
        boolean above = stands(level, p.setY(y + up + 1), level.getBlockState(p));
        for (int cy = y + up; cy >= y - down; cy--) {
            p.setY(cy);
            boolean here = stands(level, p, level.getBlockState(p));
            if (here && !above) return cy;
            above = here;
        }
        return NONE;
    }

    /** The ground a wave starts on under a caster, or {@link #NONE} when they are too high up to reach it. */
    public static int start(Level level, double x, double y, double z) {
        return top(level, new BlockPos.MutableBlockPos(), Mth.floor(x), Mth.floor(y + 0.01), Mth.floor(z), 1, START_DROP);
    }

    /** Whether the wave gets to a place, the height of the ground it ends on there and how much it climbed getting there. */
    public record Reach(boolean reached, int y, int climb) {}

    public static Reach reach(Level level, double ox, int y, double oz, double tx, double tz) {
        double dx = tx - ox, dz = tz - oz;
        double len = Math.sqrt(dx * dx + dz * dz);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int steps = (int) Math.ceil(len / STEP), climb = 0, cy = y;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int top = top(level, p, Mth.floor(ox + dx * t), cy, Mth.floor(oz + dz * t), CLIMB, DROP);
            if (top == NONE) return new Reach(false, cy, climb);
            if (top > cy) climb += top - cy;
            cy = top;
        }
        return new Reach(true, cy, climb);
    }
}
