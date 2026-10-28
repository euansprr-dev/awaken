package dev.awaken.citizens;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Where a body can stand, and who can see it (SPEC §28.11, Euan 2026-10-28: "the forge man is dying in a wall",
 * "people are spawning in front of me like ghosts"). A standing cell has a floor to stand on (never water, fire or
 * magma) and two clear rows over it for the body; a body is only ever placed on one. Unloaded ground is taken on trust:
 * it is checked again when a body is placed there.
 */
public final class Footing {
    private Footing() {}

    /** How far a body is put out of a block, or a spot moved off one (blocks). */
    public static final int NUDGE = 3;

    /** True when a body can stand with its feet in cell (x, y, z): floor under it, two clear rows, no water or fire. */
    public static boolean standable(BlockGetter level, int x, int y, int z) {
        return standable(level, x, y, z, 0.5);
    }

    /**
     * As {@link #standable(BlockGetter, int, int, int)}, with what may lie underfoot in the cell itself up to
     * {@code under} high (a carpet or a snow layer 0.25, a bottom slab or a step 0.5).
     */
    static boolean standable(BlockGetter level, int x, int y, int z, double under) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState feet = level.getBlockState(m.set(x, y, z));
        if (!feet.getFluidState().isEmpty() || bad(feet)) return false;
        VoxelShape fs = feet.getCollisionShape(level, m, CollisionContext.empty());
        double lift = fs.isEmpty() ? 0 : fs.max(Direction.Axis.Y);
        if (lift > under) return false;
        BlockState head = level.getBlockState(m.set(x, y + 1, z));
        if (!head.getFluidState().isEmpty() || !head.getCollisionShape(level, m, CollisionContext.empty()).isEmpty()) return false;
        // standing on something low lifts the head into the row above
        if (lift > 0) return level.getBlockState(m.set(x, y + 2, z)).getCollisionShape(level, m, CollisionContext.empty()).isEmpty();
        BlockState floor = level.getBlockState(m.set(x, y - 1, z));
        if (bad(floor) || !floor.getFluidState().isEmpty() && floor.getCollisionShape(level, m, CollisionContext.empty()).isEmpty()) return false;
        VoxelShape s = floor.getCollisionShape(level, m, CollisionContext.empty());
        return !s.isEmpty() && s.max(Direction.Axis.Y) >= 0.5;
    }

    /** Standable where the chunk is loaded; true where it isn't (checked again when a body goes there). */
    public static boolean standableIfLoaded(Level level, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y + 0.01), bz = (int) Math.floor(z);
        if (!level.isLoaded(new BlockPos(bx, by, bz))) return true;
        return standable(level, bx, by, bz);
    }

    /**
     * Open ground to stand on at (x, z): feet on row y, or a row down, or a row up onto a full block (a slope, never a
     * rim, a slab or a wall), nothing higher than a carpet underfoot, nothing over the head (no roof, no awning). The feet height, NaN when none;
     * y where the chunk isn't loaded.
     */
    public static double open(Level level, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y + 0.01), bz = (int) Math.floor(z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, by, bz);
        if (!level.isLoaded(m)) return y;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        for (int dy : DY) {
            int fy = by + dy;
            if (top > fy || !standable(level, bx, fy, bz, 0.25)) continue;
            if (dy > 0 && !level.getBlockState(m.set(bx, fy - 1, bz)).isCollisionShapeFullBlock(level, m)) continue;
            return fy;
        }
        return Double.NaN;
    }

    private static boolean bad(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.POWDER_SNOW);
    }

    /** Somewhere else a body may not stand (another body's place, a spot that's taken). */
    public interface Taken {
        boolean taken(double x, double z);
    }

    public static final Taken NOBODY = (x, z) -> false;

    /**
     * The nearest standing cell to (x, y, z) within {@code r} blocks, on its row or one up or down, not taken: its feet
     * (the cell's middle) into {@code out}; false when there is none.
     */
    public static boolean nearest(Level level, double x, double y, double z, int r, double[] out, Taken taken) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y + 0.01), bz = (int) Math.floor(z);
        double best = Double.MAX_VALUE;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double flat = dx * dx + dz * dz;
                if (flat > r * r + 0.5 || flat >= best) continue;
                double cx = bx + dx + 0.5, cz = bz + dz + 0.5;
                if (taken.taken(cx, cz)) continue;
                for (int dy : DY) {
                    double cost = flat + dy * dy * 1.5;
                    if (cost >= best) continue;
                    if (!level.isLoaded(new BlockPos(bx + dx, by + dy, bz + dz)) || !standable(level, bx + dx, by + dy, bz + dz)) continue;
                    best = cost;
                    out[0] = cx;
                    out[1] = by + dy;
                    out[2] = cz;
                }
            }
        }
        return best < Double.MAX_VALUE;
    }

    private static final int[] DY = {0, 1, -1};

    // ---- who can see a place -------------------------------------------------------------------------------------------

    /** Nobody has a body appear (or vanish) this near them, even behind them or behind a wall (SPEC §28.11). */
    public static final double NEAR = 24;
    /** Past this a body coming into range in plain view is a speck at the edge of sight. */
    public static final double FAR = 44;
    /** Half the cone a player sees (wider than any field of view the game allows, for the edges of the screen). */
    static final double VIEW_COS = Math.cos(Math.toRadians(80));

    /**
     * True when no player could see a body at (x, y, z) come or go: nobody within {@link #NEAR} of it at all, and nobody
     * nearer than {@link #FAR} looking its way with a clear line from their eyes to its head or middle. Spectators
     * count like anyone.
     */
    public static boolean unseen(Level level, List<? extends ServerPlayer> players, double x, double y, double z) {
        for (int k = 0; k < players.size(); k++) {
            ServerPlayer p = players.get(k);
            Vec3 eye = p.getEyePosition();
            double dx = x - eye.x, dy = y + 1.0 - eye.y, dz = z - eye.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > FAR) continue;
            if (dist < NEAR) return false;
            Vec3 look = p.getViewVector(1f);
            if ((dx * look.x + dy * look.y + dz * look.z) / dist < VIEW_COS) continue; // not looking that way
            if (!blocked(level, p, eye, x, y + 1.6, z) || !blocked(level, p, eye, x, y + 0.9, z)) return false;
        }
        return true;
    }

    private static boolean blocked(Level level, ServerPlayer p, Vec3 eye, double x, double y, double z) {
        return level.clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS;
    }
}
