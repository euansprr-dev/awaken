package dev.awaken.body;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The grip on a climb line (SPEC §17.23; Euan's pipes): the numbers and the probe both sides run. A climb line is a
 * block in {@code #awaken:runner_climbs} (Carillon's drainpipes, trellises, hoist ropes and hanging ladders, every
 * ladder); a line with a facing sits against a wall (its facing points away from it, as a ladder's does), a rope hangs
 * free. The body holds the line from in front of it, centred on it. Nothing here touches a player.
 */
public final class GripRules {
    private GripRules() {}

    public static final TagKey<Block> CLIMBS = TagKey.create(Registries.BLOCK, dev.awaken.Awaken.id("runner_climbs"));

    /** Climbing, blocks a tick: hand over hand (a ladder's pace), and with the free-run held (its climb, §17.14). */
    public static final double CLIMB = 0.2, CLIMB_FREE = 0.3;
    /** The slide: Shift held speeds up by this a tick to the top speed; letting go brakes to a hold over these ticks. */
    public static final double SLIDE_ACCEL = 0.06, SLIDE_MAX = 0.65;
    public static final int BRAKE_TICKS = 4;
    /** Jump alone: a lunge up the line, over these ticks, at most once per {@link #HOP_EVERY}. */
    public static final double HOP = 1.2;
    public static final int HOP_TICKS = 6, HOP_EVERY = 10;
    /** Jump with back: kicked off the wall (the ledge back-jump's numbers); with left or right: swung out sideways. */
    public static final double KICK_OUT = RunnerRules.LEDGE_BACK_OUT, KICK_UP = RunnerRules.LEDGE_BACK_UP;
    public static final double SIDE_OUT = 0.42, SIDE_AWAY = 0.12, SIDE_UP = 0.36;
    /** A line is taken facing it within this angle (a wall's); a hand reaches this far over the feet. */
    public static final double FACING_MAX = 70, REACH = 1.75;
    static final double COS_FACING = Math.cos(Math.toRadians(FACING_MAX));
    /** The gap kept between the body and the line; how far the server lets the body be from the line it accepted. */
    public static final double GAP = 0.02, SERVER_REACH = 0.9;
    /** Hunger while gripping: half a hang's (the legs carry some of it). */
    public static final float EXHAUSTION = RunnerRules.HANG_EXHAUSTION * 0.5F;
    /** The server re-checks the line every this many ticks; this many misses in a row end the grip. */
    public static final int CHECK = 4, MISS = 3;

    /**
     * A line: where the body holds it (x, z), the way the body faces to hold it (fx, fz: toward the wall, or toward a
     * free rope), the line's own column, and the line's centre (cx, cz: where the hands go).
     */
    public record Line(double x, double z, double fx, double fz, int bx, int bz, double cx, double cz, double radius, boolean free) {
        public float yaw() {
            return (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
        }
    }

    public static boolean isLine(BlockState s) {
        return s.is(CLIMBS);
    }

    /**
     * The line the body at (x, y, z) is in: its feet's cell, else the cell at its chest, holds a climb line. A free
     * rope is held from the side the body is on (or, right on it, the way it looks).
     */
    public static @Nullable Line find(BlockGetter level, BlockPos.MutableBlockPos m, double x, double y, double z, float yaw) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        m.set(bx, Mth.floor(y + 0.1), bz);
        BlockState s = level.getBlockState(m);
        if (isLine(s)) return line(level, m, s, x, z, yaw);
        m.set(bx, Mth.floor(y + 1.2), bz);
        s = level.getBlockState(m);
        return isLine(s) ? line(level, m, s, x, z, yaw) : null;
    }

    /** The line in this cell, held by a body at (x, z) looking along yaw. */
    static @Nullable Line line(BlockGetter level, BlockPos pos, BlockState s, double x, double z, float yaw) {
        VoxelShape shape = s.getShape(level, pos);
        if (shape.isEmpty()) return null;
        AABB b = shape.bounds();
        double cx = pos.getX() + (b.minX + b.maxX) * 0.5, cz = pos.getZ() + (b.minZ + b.maxZ) * 0.5;
        double w = BodyRules.HALF_WIDTH;
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction wall = s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            double fx = wall.getStepX(), fz = wall.getStepZ();
            // the line's front: the face of its shape toward the body; the body stands just in front of it
            double hx = cx, hz = cz;
            if (wall.getAxis() == Direction.Axis.X) hx = fx > 0 ? pos.getX() + b.minX - w - GAP : pos.getX() + b.maxX + w + GAP;
            else hz = fz > 0 ? pos.getZ() + b.minZ - w - GAP : pos.getZ() + b.maxZ + w + GAP;
            double radius = wall.getAxis() == Direction.Axis.X ? (b.maxZ - b.minZ) * 0.5 : (b.maxX - b.minX) * 0.5;
            return new Line(hx, hz, fx, fz, pos.getX(), pos.getZ(), cx, cz, radius, false);
        }
        // a free rope: from the body's side of it, or the way it looks when right on it
        double radius = Math.max(b.maxX - b.minX, b.maxZ - b.minZ) * 0.5;
        double dx = cx - x, dz = cz - z, d = Math.sqrt(dx * dx + dz * dz);
        double fx, fz;
        if (d < 0.05) {
            fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
            fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        } else {
            fx = dx / d;
            fz = dz / d;
        }
        double off = w + radius + GAP;
        return new Line(cx - fx * off, cz - fz * off, fx, fz, pos.getX(), pos.getZ(), cx, cz, radius, true);
    }

    /** Whether the line's column holds a climb line in the cell at height y. */
    public static boolean lineAt(BlockGetter level, BlockPos.MutableBlockPos m, int bx, int bz, double y) {
        m.set(bx, Mth.floor(y), bz);
        return isLine(level.getBlockState(m));
    }

    /** Whether a body with its feet at y still holds the line in this column (its feet or its chest are on it). */
    public static boolean holds(BlockGetter level, BlockPos.MutableBlockPos m, int bx, int bz, double y) {
        return lineAt(level, m, bx, bz, y + 0.1) || lineAt(level, m, bx, bz, y + 1.2);
    }

    /** Whether the hands can reach on up the line from feet at y (climbing; else the top is here). */
    public static boolean goesOn(BlockGetter level, BlockPos.MutableBlockPos m, int bx, int bz, double y) {
        return lineAt(level, m, bx, bz, y + REACH);
    }

    /** Whether a body looking along yaw faces this line well enough to take it. */
    public static boolean facing(Line l, float yaw) {
        if (l.free()) return true;
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return fx * l.fx() + fz * l.fz() >= COS_FACING;
    }

    /** The server's check: the body at (x, y, z) is still at a line of this column, near enough to hold it. */
    public static boolean stillHolds(BlockGetter level, BlockPos.MutableBlockPos m, int bx, int bz, double x, double y, double z) {
        boolean any = false;
        for (double dy = -0.5; dy <= 2.0 && !any; dy += 0.5) any = lineAt(level, m, bx, bz, y + dy);
        if (!any) return false;
        double dx = x - (bx + 0.5), dz = z - (bz + 0.5);
        return dx * dx + dz * dz <= SERVER_REACH * SERVER_REACH;
    }

    /** The slide's speed (down, positive) a tick after {@code v}. */
    public static double slide(double v) {
        return Math.min(SLIDE_MAX, Math.max(0, v) + SLIDE_ACCEL);
    }

    /** The hop's rise from tick {@code age - 1} to {@code age} (smoothstep over its ticks; HOP in all). */
    public static double hopStep(int age) {
        return HOP * (smooth(age / (double) HOP_TICKS) - smooth((age - 1) / (double) HOP_TICKS));
    }

    static double smooth(double u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }
}
