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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The grip on a climb line (SPEC §17.28; Euan's pipes): the numbers and the probe both sides run. A climb line is a
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
    public static final int HOP_TICKS = 8, HOP_EVERY = 12;
    /** A hop's load under its start, and how far over its top it goes before settling (blocks). */
    public static final double HOP_DIP = 0.08, HOP_OVER = 0.12;
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
            // the line's front: the face of its shape toward the body, where the column sticks out most (a drainpipe's
            // head is deeper than its pipe: standing off the pipe alone, the climb would run into the head and stop a
            // block short of the top); the body stands just in front of it
            double front = frontOf(level, pos, s, wall, b);
            double hx = cx, hz = cz;
            if (wall.getAxis() == Direction.Axis.X) hx = front - fx * (w + GAP);
            else hz = front - fz * (w + GAP);
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

    /** How far down and up a line's column is read for its front (blocks). */
    static final int COLUMN_DOWN = 8, COLUMN_UP = 16;

    /** The column's front toward the body (world, on the wall's axis): the most any line cell with this facing sticks out. */
    private static double frontOf(BlockGetter level, BlockPos pos, BlockState s, Direction wall, AABB here) {
        boolean x = wall.getAxis() == Direction.Axis.X;
        boolean pos1 = (x ? wall.getStepX() : wall.getStepZ()) > 0;
        double best = x ? (pos1 ? pos.getX() + here.minX : pos.getX() + here.maxX) : (pos1 ? pos.getZ() + here.minZ : pos.getZ() + here.maxZ);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Direction facing = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (int dy = -COLUMN_DOWN; dy <= COLUMN_UP; dy++) {
            if (dy == 0) continue;
            m.set(pos.getX(), pos.getY() + dy, pos.getZ());
            BlockState c = level.getBlockState(m);
            if (!isLine(c) || !c.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || c.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing) continue;
            VoxelShape cs = c.getShape(level, m);
            if (cs.isEmpty()) continue;
            AABB b = cs.bounds();
            double f = x ? (pos1 ? m.getX() + b.minX : m.getX() + b.maxX) : (pos1 ? m.getZ() + b.minZ : m.getZ() + b.maxZ);
            best = pos1 ? Math.min(best, f) : Math.max(best, f);
        }
        return best;
    }

    /** Whether a body-wide column at (x, z) is free of blocks from y0 up to y1 (entities don't count). */
    public static boolean free(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double x, double z, double y0, double y1) {
        double w = BodyRules.HALF_WIDTH - 1e-3;
        return !BodyRules.blocked(level, ctx, m, x - w, y0 + 1e-3, z - w, x + w, y1 - 1e-3, z + w);
    }

    /** Whether a body with its feet at (x, y, z) can rise by v: no block in the way (entities don't count). */
    public static boolean canRise(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double x, double y, double z, double v) {
        double w = BodyRules.HALF_WIDTH - 1e-3;
        return !BodyRules.blocked(level, ctx, m, x - w, y + 1e-3, z - w, x + w, y + BodyRules.HEIGHT + v, z + w);
    }

    /**
     * Where a climb up the line from feet at y stops (the grip's own climb, in 0.2 steps then finer): the hands find no
     * more line above, or the head meets something. For tests and checks; the client climbs tick by tick on the same rule.
     */
    public static double climbTop(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, Line l, double y) {
        for (int i = 0; i < 400; i++) {
            if (!goesOn(level, m, l.bx(), l.bz(), y + 0.02)) return y;
            double v = 0.2;
            while (v > 0.005 && (!canRise(level, ctx, m, l.x(), y, l.z(), v) || !goesOn(level, m, l.bx(), l.bz(), y + v))) v *= 0.5;
            if (v <= 0.005) return y;
            y += v;
        }
        return y;
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

    /** A lip over a line's top: its top this far over the feet (an eave or cornice the line runs up under), out to here. */
    public static final double LIP_LO = 1.6, LIP_HI = 3.2, LIP_OUT = 1.5;

    /**
     * Where the top of a line leads (SPEC §17.28, "the top"): a lip over the head as a hang, or null. The nearest one the
     * hang climbs out from, first:
     * the line's own top or a wall's top right in front (a flat roof's edge, a parapet's gap, a terrace), then an eave
     * or cornice the line runs up under, hung from its outer face (out from the line, then up). Feet at (x, y, z) in
     * front of the line, facing it (axis, sign toward the wall); the way out (level) and then up to the hold is free.
     * {@code tol}: the server's extra reach.
     */
    public static RunnerRules.@Nullable Edge topLip(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                                    double x, double y, double z, int axis, int sign, double tol) {
        double fx = axis == 0 ? sign : 0, fz = axis == 2 ? sign : 0, w = BodyRules.HALF_WIDTH, h = BodyRules.HEIGHT;
        RunnerRules.Edge first = null;
        for (double d = 0; d <= LIP_OUT + 1e-6; d += 0.15) {
            RunnerRules.Edge e = RunnerRules.findEdge(level, ctx, m, x - fx * d, y, z - fz * d, axis, sign, RunnerRules.HANG_REACH + tol,
                    LIP_LO, LIP_HI, 0, 0.2);
            if (e == null) continue;
            // over the head (the line's own top right in front, or an overhang's lip out past the body)
            if (e.top() < y + h - 0.2) continue;
            // out level from the line, then up (or down) to the hold
            double c0 = Math.min(axis == 0 ? x : z, axis == 0 ? e.hx() : e.hz()) - w, c1 = Math.max(axis == 0 ? x : z, axis == 0 ? e.hx() : e.hz()) + w;
            double t = axis == 0 ? z : x;
            boolean out1 = axis == 0 ? BodyRules.blocked(level, ctx, m, c0 + 1e-3, y + 1e-3, t - w + 1e-3, c1 - 1e-3, y + h - 1e-3, t + w - 1e-3)
                    : BodyRules.blocked(level, ctx, m, t - w + 1e-3, y + 1e-3, c0 + 1e-3, t + w - 1e-3, y + h - 1e-3, c1 - 1e-3);
            if (out1) continue;
            double y0 = Math.min(y, e.hy()), y1 = Math.max(y, e.hy()) + h;
            if (BodyRules.blocked(level, ctx, m, e.hx() - w + 1e-3, y0 + 1e-3, e.hz() - w + 1e-3, e.hx() + w - 1e-3, y1 - 1e-3, e.hz() + w - 1e-3)) continue;
            // a lip the hang climbs out from (the pipe's head under a slab cornice is a hold with no way on: the slab's
            // own lip, further out, is the one); a hold with no climb out only when there's nothing better
            if (RunnerRules.climbOut(level, ctx, m, e) != null) return e;
            if (first == null) first = e;
        }
        return first;
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
        return hopY(age) - hopY(age - 1);
    }

    /**
     * Where a hop has the body after {@code age} ticks, over where it started: a load (down {@link #HOP_DIP}), a launch
     * that slows like a throw against gravity to {@link #HOP_OVER} over the top, then settles onto it. Ends at
     * {@link #HOP} exactly.
     */
    static double hopY(int age) {
        double u = Mth.clamp(age / (double) HOP_TICKS, 0, 1);
        if (u <= 0.25) return -HOP_DIP * smooth(u / 0.25);
        if (u <= 0.85) {
            double s = (u - 0.25) / 0.6, e = 1 - (1 - s) * (1 - s);
            return -HOP_DIP + (HOP + HOP_OVER + HOP_DIP) * e;
        }
        return HOP + HOP_OVER - HOP_OVER * smooth((u - 0.85) / 0.15);
    }

    static double smooth(double u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }
}
