package dev.awaken.body;

import dev.awaken.powers.ladder.PowerLadder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Spike S3 (Carillon Stage 0, throwaway): the Runner's Kit numbers (SPEC §16.3) and the plain probes both sides run:
 * where a wall to run on is, where an edge to hang from is, whether there is room to climb out. Like {@link BodyRules},
 * nothing here touches a player, so the client predicts with exactly what the server checks and GameTests call it.
 * Every probe takes a {@link BlockGetter}; the client passes a {@link ProbeWindow} so one tick's probes share their
 * block lookups (the "batched" probes).
 */
public final class RunnerRules {
    private RunnerRules() {}

    // ---- wall-run ---------------------------------------------------------------------------------------------------
    public static final int RUN_TICKS = 35, RUN_GRIP = 10;
    /** Vertical speed target during the grip (eases to 0 by its end), then the sink's target (smoothstep), b/t. */
    public static final double RUN_GRIP_UP = 0.06, RUN_SINK = -0.26;
    /** Share of the gap to the target the vertical speed closes each tick. */
    public static final double RUN_APPROACH = 0.35;
    /** Feet at least this far above the ground; facing within this many degrees of the wall. */
    public static final double RUN_MIN_HEIGHT = 1.5, RUN_MAX_ANGLE = 35;
    static final double COS_RUN = Math.cos(Math.toRadians(RUN_MAX_ANGLE));
    /** Speed along the wall (spike decision: entry speed kept within these; ~11 blocks over 35 ticks at the floor). */
    public static final double RUN_SPEED_MIN = 0.30, RUN_SPEED_MAX = 0.42;
    /** How far from the body's side the wall may be, and the gap the run keeps (well inside 1/16: the float kick). */
    public static final double RUN_REACH = 0.35, RUN_GAP = 0.01;
    /** Horizontal speed along the wall needed to start (a standing jump never runs). */
    public static final double RUN_MIN_ALONG = 0.12;
    // ---- wall jump --------------------------------------------------------------------------------------------------
    public static final double WALL_JUMP_UP = 0.42, WALL_JUMP_OUT = 0.5;
    /** Share of the along-wall speed a wall jump keeps (spike decision). */
    public static final double WALL_JUMP_KEEP = 0.8;
    /** Each linked run is this share of the one before (35, 28, 22, 18, 14); links before you must land. */
    public static final double LINK_SHARE = 0.8;
    public static final int LINKS = 4;
    // ---- ledge hang -------------------------------------------------------------------------------------------------
    /** An airborne grab: the edge's top this far above the feet. */
    public static final double HANG_LO = 1.2, HANG_HI = 2.4;
    /** The hold: feet this far under the top, so in first person your eyes just clear the lip (S3's 1.85 hid it). */
    public static final double HANG_DROP = 1.55;
    /** How far in front of the body the face may be; the gap the hold keeps; free space over the lip for the hands. */
    public static final double HANG_REACH = 0.45, HANG_GAP = 0.02, HANG_HEADROOM = 0.5;
    /** Sprint-rate hunger while hanging (vanilla: 0.1 per block sprinted, ~0.28 blocks a tick). */
    public static final float HANG_EXHAUSTION = 0.028F;
    /** Sneak forward at an edge with at least this drop, for this many ticks, to lower into a hang. */
    public static final double LOWER_DROP = 4.0;
    public static final int LOWER_HOLD = 6;
    // ---- shimmy and ledge jumps -------------------------------------------------------------------------------------
    /** 1.6 blocks a second. */
    public static final double SHIMMY = 0.08;
    /** Up to a ledge this much higher; sideways this far; back off the wall with this push. */
    public static final double LEDGE_UP = 2.5, LEDGE_SIDE = 3.0, LEDGE_BACK_OUT = 0.5, LEDGE_BACK_UP = 0.42;
    /** A climb out may land this much over the lip (vanilla's step: a stair eave's back half, a roof's next slab). */
    public static final double CLIMB_STEP = 0.6;
    /** The climb-out's two-tick load and eight-tick push, shared with its server appearance window. */
    public static final int CLIMB_OUT_TICKS = 10;
    // ---- jump help --------------------------------------------------------------------------------------------------
    public static final int COYOTE = 3, BUFFER = 4;
    public static final double SNAP = 0.3;
    // ---- server -----------------------------------------------------------------------------------------------------
    /** Extra reach the server allows (its position may be a tick off). */
    public static final double SERVER_TOL = 0.3;
    /** A claimed start must be this close to a position the server saw in its last {@link #HISTORY} ticks. */
    public static final double CLAIM_TOL = 0.75;
    public static final int HISTORY = 12;
    /** Ticks the server keeps a run going past the client's length; ticks without wall contact before it ends one. */
    public static final int RUN_GRACE = 6, RUN_MISS = 5;

    /**
     * Wall Stride's vertical run (SPEC §17.2): facing a wall (within {@link #CLIMB_MAX_ANGLE} of its normal, within
     * {@link #CLIMB_REACH}), early in a jump, holding forward: {@link #CLIMB_TICKS} ticks up it at {@link #CLIMB_VY},
     * easing off over the last three (about 3.7 blocks: a lip six up is caught), into the ledge's grab when one comes in
     * reach. A jump kicks
     * back off it ({@link #CLIMB_KICK_OUT} out, {@link #CLIMB_KICK_UP} up) and turns you round.
     */
    public static final int CLIMB_TICKS = 12;
    public static final double CLIMB_VY = 0.34, CLIMB_REACH = 0.45, CLIMB_MAX_ANGLE = 35, CLIMB_KICK_OUT = 0.42, CLIMB_KICK_UP = 0.4;
    static final double COS_CLIMB = Math.cos(Math.toRadians(CLIMB_MAX_ANGLE));

    /** The vertical run's rise at tick {@code age}. */
    public static double climbTarget(int age) {
        return age < CLIMB_TICKS - 3 ? CLIMB_VY : CLIMB_VY * Math.max(0, CLIMB_TICKS - age) / 3.0;
    }
    /** The hang is re-checked every this many ticks; this many failed checks in a row end it. */
    public static final int HANG_CHECK = 4, HANG_MISS = 3;

    static final double W = BodyRules.HALF_WIDTH, H = BodyRules.HEIGHT;

    /** Probe statistics (client cost report): probes run and the faces they scanned. */
    public static long probes;

    // ---- wall-run ---------------------------------------------------------------------------------------------------

    /** Length and grip of a run that is link {@code link} of a chain (0 = a fresh start), at rank E. */
    public static int runLength(int link) {
        return (int) Math.round(RUN_TICKS * Math.pow(LINK_SHARE, link));
    }

    // ---- ranks improve the moves (SPEC §16.3, §17) -------------------------------------------------------------------
    /** From D a wall-run lasts this long (E: {@link #RUN_TICKS}). */
    public static final int RUN_TICKS_D = 40;

    public static int runLength(int link, Rank rank) {
        return (int) Math.round((rank.ordinal() >= Rank.D.ordinal() ? RUN_TICKS_D : RUN_TICKS) * Math.pow(LINK_SHARE, link));
    }

    /** Wall jumps in a chain before you must land: 4, 5 from C. */
    public static int links(Rank rank) {
        return rank.ordinal() >= Rank.C.ordinal() ? LINKS + 1 : LINKS;
    }

    /** Shimmy speed (blocks a tick): 1.6 b/s, 2.2 from B. */
    public static double shimmy(Rank rank) {
        return rank.ordinal() >= Rank.B.ordinal() ? 0.11 : SHIMMY;
    }

    /** A ledge jump's reach up and sideways: one block more from B. */
    public static double ledgeUp(Rank rank) {
        return rank.ordinal() >= Rank.B.ordinal() ? LEDGE_UP + 1 : LEDGE_UP;
    }

    public static double ledgeSide(Rank rank) {
        return rank.ordinal() >= Rank.B.ordinal() ? LEDGE_SIDE + 1 : LEDGE_SIDE;
    }

    public static int runGrip(int link) {
        return (int) Math.round(RUN_GRIP * Math.pow(LINK_SHARE, link));
    }

    /** The vertical speed the run is easing toward on tick {@code age} (0-based). */
    public static double runTarget(int age, int ticks, int grip) {
        return runTarget(age, ticks, grip, false);
    }

    /** The same for a Wall Stride run: a gentler lift through its long grip, and a shallower sink. */
    public static double runTarget(int age, int ticks, int grip, boolean stride) {
        double up = stride ? STRIDE_GRIP_UP : RUN_GRIP_UP, sink = stride ? STRIDE_SINK : RUN_SINK;
        if (age < grip) return up * (1 - age / (double) Math.max(1, grip));
        double u = Mth.clamp((age - grip) / (double) Math.max(1, ticks - grip), 0, 1);
        return sink * u * u * (3 - 2 * u);
    }

    // ---- Wall Stride (the rank-D passive that replaces the Wall Run power; SPEC §16.3) --------------------------------
    public static final int STRIDE_TICKS = 80, STRIDE_GRIP = 50;
    public static final double STRIDE_GRIP_UP = 0.04, STRIDE_SINK = -0.14;
    /** The power that is Wall Stride (its id stays the old Wall Run's, so saves keep it). */
    public static final String STRIDE_POWER = "awaken:wall_run";

    /** A run's length and grip with or without Wall Stride, link {@code link} of a chain. */
    public static int runLength(int link, Rank rank, boolean stride) {
        return stride ? (int) Math.round(STRIDE_TICKS * Math.pow(LINK_SHARE, link)) : runLength(link, rank);
    }

    /**
     * A run's length with Wall Stride at {@code level} (0 = without it). Its ladder (SPEC 34): Spark Steps (2) a half second
     * longer, Street Runner (5) a second more on each wall chained from the last.
     */
    public static int runLength(int link, Rank rank, int level) {
        if (level <= 0) return runLength(link, rank);
        int n = runLength(link, rank, true);
        if (level >= 2) n += (int) PowerLadder.num("wall_run", 2, "extra", 10);
        if (level >= 5 && link > 0) n += (int) PowerLadder.num("wall_run", 5, "chain", 20);
        return n;
    }

    /** The run's vertical target with Wall Stride at {@code level}: Steady (4) never sinks. */
    public static double runTarget(int age, int ticks, int grip, int level) {
        if (level < 4) return runTarget(age, ticks, grip, level > 0);
        return age < grip ? STRIDE_GRIP_UP * (1 - age / (double) Math.max(1, grip)) : 0;
    }

    /** Kick-off (3): a wall jump leaves with this much more of the run's speed. */
    public static double strideKick(int level) {
        return level >= 3 ? 1 + PowerLadder.num("wall_run", 3, "kick", 0.35f) : 1;
    }

    public static int runGrip(int link, boolean stride) {
        return stride ? (int) Math.round(STRIDE_GRIP * Math.pow(LINK_SHARE, link)) : runGrip(link);
    }

    /**
     * A wall beside the body to run along. axis = the wall's normal axis (0 x, 2 z); side = which way along it the wall
     * is from the body (+1/-1); plane = the face's coordinate; gap = from the body's side to it; (tx, tz) = the way
     * along the wall the facing points.
     */
    public record Wall(int axis, int side, double plane, double gap, double tx, double tz) {
        public long id() {
            return ((long) axis << 56) ^ ((long) (side + 1) << 52) ^ (Math.round(plane * 16) & 0xFFFFFFFFFFFFL);
        }

        /** Unit normal from the wall toward the body. */
        public double nx() {
            return axis == 0 ? -side : 0;
        }

        public double nz() {
            return axis == 2 ? -side : 0;
        }
    }

    /**
     * The wall a body with feet at (px, py, pz), facing {@code yaw}, can run along: the facing within
     * {@link #RUN_MAX_ANGLE} of an axis, a face on one side within {@code reach} that covers the whole body (feet and
     * head bands: at least two blocks of wall beside you). The nearer side wins.
     */
    public static @Nullable Wall findWall(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                          double px, double py, double pz, float yaw, double reach) {
        probes++;
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int axis;
        double tx, tz;
        if (Math.abs(fx) >= COS_RUN) {
            axis = 2;
            tx = Math.signum(fx);
            tz = 0;
        } else if (Math.abs(fz) >= COS_RUN) {
            axis = 0;
            tx = 0;
            tz = Math.signum(fz);
        } else {
            return null;
        }
        return wallOn(level, ctx, m, px, py, pz, axis, 0, reach, tx, tz);
    }

    /** The wall in front of a body facing {@code yaw}: the facing within {@link #CLIMB_MAX_ANGLE} of an axis. */
    public static @Nullable Wall wallAhead(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                           double px, double py, double pz, float yaw, double reach) {
        probes++;
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        if (Math.abs(fx) >= COS_CLIMB) return wallOn(level, ctx, m, px, py, pz, 0, fx > 0 ? 1 : -1, reach, 0, 0);
        if (Math.abs(fz) >= COS_CLIMB) return wallOn(level, ctx, m, px, py, pz, 2, fz > 0 ? 1 : -1, reach, 0, 0);
        return null;
    }

    /** The wall on one side (side 0: the nearer of both) of the body along the normal axis. */
    public static @Nullable Wall wallOn(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                        double px, double py, double pz, int axis, int onlySide, double reach, double tx, double tz) {
        double c = axis == 0 ? px : pz, t = axis == 0 ? pz : px;
        Wall best = null;
        for (int side = 1; side >= -1; side -= 2) {
            if (onlySide != 0 && side != onlySide) continue;
            double from = c + side * W;
            double lo = nearestFace(level, ctx, m, axis, side, from, reach, py + 0.05, py + 0.6, t - 0.25, t + 0.25);
            if (Double.isNaN(lo)) continue;
            double hi = nearestFace(level, ctx, m, axis, side, from, reach, py + 1.2, py + 1.75, t - 0.25, t + 0.25);
            if (Double.isNaN(hi)) continue;
            double gap = Math.min(Math.abs(lo - from), Math.abs(hi - from));
            if (best == null || gap < best.gap) best = new Wall(axis, side, hi, gap, tx, tz);
        }
        return best;
    }

    /** Whether there is ground (any collision top) within {@code depth} under the feet. */
    public static boolean groundWithin(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double px, double py, double pz, double depth) {
        probes++;
        return BodyRules.blocked(level, ctx, m, px - W + 0.01, py - depth, pz - W + 0.01, px + W - 0.01, py - 1e-3, pz + W - 0.01);
    }

    // ---- edges ------------------------------------------------------------------------------------------------------

    /**
     * An edge to hang from. (hx, hy, hz) = where the feet hold; top = the edge's top; the body faces +sign along axis
     * (0 x, 2 z) toward the face at {@code plane}.
     */
    public record Edge(double hx, double hy, double hz, double top, int axis, int sign, double plane) {
        /** Unit facing toward the wall. */
        public double fx() {
            return axis == 0 ? sign : 0;
        }

        public double fz() {
            return axis == 2 ? sign : 0;
        }

        /** The coordinate along the edge (the other horizontal axis). */
        public double along() {
            return axis == 0 ? hz : hx;
        }
    }

    /**
     * The edge in front of a body with feet at (px, py, pz), facing +sign along axis: a face within reach under the
     * hands (a band {@code hw} wide around the body's middle plus {@code tOff} along the edge), whose top is between
     * lo and hi above the feet, has {@link #HANG_HEADROOM} free over the lip, and where the hanging body fits flush with
     * the face. A fence's, gate's or wall's top is an edge like any other (SPEC §17.27): one standing on the floor you
     * stand on never is (the hanging feet would be in that floor).
     */
    public static @Nullable Edge findEdge(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                          double px, double py, double pz, int axis, int sign, double reach,
                                          double lo, double hi, double tOff, double hw) {
        probes++;
        double c = axis == 0 ? px : pz, t = (axis == 0 ? pz : px) + tOff;
        double from = c + sign * W;
        double face = nearestFace(level, ctx, m, axis, sign, from, reach, py + lo - 0.5, py + hi, t - hw, t + hw);
        if (Double.isNaN(face)) return null;
        double a0 = sign > 0 ? face + 0.01 : face - 0.26, a1 = sign > 0 ? face + 0.26 : face - 0.01;
        double[] top = SCRATCH.get();
        // the lip: a top in the band with room for the hands over it, the one nearest where a hold would put it
        if (axis == 0) ledgeTop(level, ctx, m, a0, t - hw, a1, t + hw, py + lo, py + hi, py + HANG_DROP, top);
        else ledgeTop(level, ctx, m, t - hw, a0, t + hw, a1, py + lo, py + hi, py + HANG_DROP, top);
        if (Double.isNaN(top[0])) return null;
        double ty = top[0];
        // room for the hands over the lip (out to half a block from the face)
        double b0 = sign > 0 ? face + 0.01 : face - 0.5, b1 = sign > 0 ? face + 0.5 : face - 0.01;
        if (blockedAxis(level, ctx, m, axis, b0, b1, ty + 1e-3, ty + HANG_HEADROOM, t - hw, t + hw)) return null;
        // the hanging body, flush with the face
        double hc = face - sign * (W + HANG_GAP), hy = ty - HANG_DROP;
        double bt = axis == 0 ? pz : px;
        if (blockedAxis(level, ctx, m, axis, hc - W + 1e-3, hc + W - 1e-3, hy + 1e-3, hy + H - 1e-3, bt - W + 1e-3, bt + W - 1e-3)) {
            // a rail standing back on what's under it (a fence or wall on a block, at a roof's edge; SPEC §17.27): the body
            // hangs flush with that face instead, the hands reaching in to the rail
            if (top[1] == 0) return null;
            double d = 0.025;
            for (; d <= RAIL_INSET + 1e-6; d += 0.025) {
                hc = face - sign * (W + HANG_GAP + d);
                if (!blockedAxis(level, ctx, m, axis, hc - W + 1e-3, hc + W - 1e-3, hy + 1e-3, hy + H - 1e-3, bt - W + 1e-3, bt + W - 1e-3)) break;
            }
            if (d > RAIL_INSET + 1e-6) return null;
        }
        return axis == 0 ? new Edge(hc, hy, bt, ty, 0, sign, face) : new Edge(bt, hy, hc, ty, 2, sign, face);
    }

    /** How far a rail may stand back from the face under it for a hang on it (a fence's post is 0.375 in, a wall's 0.25). */
    public static final double RAIL_INSET = 0.45;

    /** Why {@link #findEdge} says no (diagnostics for the spike's server re-checks), or "ok". */
    public static String whyNot(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                double px, double py, double pz, int axis, int sign, double reach, double lo, double hi) {
        double c = axis == 0 ? px : pz, t = axis == 0 ? pz : px, hw = 0.2;
        double from = c + sign * W;
        double face = nearestFace(level, ctx, m, axis, sign, from, reach, py + lo - 0.5, py + hi, t - hw, t + hw);
        if (Double.isNaN(face)) return String.format("no face within %.2f of %.3f (band y %.2f..%.2f)", reach, from, py + lo - 0.5, py + hi);
        double a0 = sign > 0 ? face + 0.01 : face - 0.26, a1 = sign > 0 ? face + 0.26 : face - 0.01;
        double[] top = SCRATCH.get();
        if (axis == 0) ledgeTop(level, ctx, m, a0, t - hw, a1, t + hw, py + lo, py + hi, py + HANG_DROP, top);
        else ledgeTop(level, ctx, m, t - hw, a0, t + hw, a1, py + lo, py + hi, py + HANG_DROP, top);
        if (Double.isNaN(top[0])) return String.format("face %.3f: no top with room over it in y %.2f..%.2f", face, py + lo, py + hi);
        double b0 = sign > 0 ? face + 0.01 : face - 0.5, b1 = sign > 0 ? face + 0.5 : face - 0.01;
        if (blockedAxis(level, ctx, m, axis, b0, b1, top[0] + 1e-3, top[0] + HANG_HEADROOM, t - hw, t + hw)) return "no room over the lip";
        double hc = face - sign * (W + HANG_GAP), hy = top[0] - HANG_DROP;
        if (blockedAxis(level, ctx, m, axis, hc - W + 1e-3, hc + W - 1e-3, hy + 1e-3, hy + H - 1e-3, t - W + 1e-3, t + W - 1e-3)) return "the hanging body doesn't fit";
        return "ok";
    }

    /**
     * The lip in a column just inside a face: of every collision top in (yLo, yHi] with {@link #HANG_HEADROOM} of air
     * over it in the column, the one nearest {@code prefer}; out[0] = its y (NaN: none), out[1] = 1 when it is a fence,
     * wall or gate top (collision taller than its block: an edge too since SPEC §17.27). Unlike the mantle's surface, a
     * ledge with another one a block above it is still a ledge.
     */
    static void ledgeTop(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                         double x0, double z0, double x1, double z1, double yLo, double yHi, double prefer, double[] out) {
        double best = Double.NaN;
        boolean tall = false;
        int bx0 = Mth.floor(x0), bx1 = Mth.floor(x1 - 1e-7), bz0 = Mth.floor(z0), bz1 = Mth.floor(z1 - 1e-7);
        int by0 = Mth.floor(yLo) - 1, by1 = Mth.floor(yHi);
        for (int bx = bx0; bx <= bx1; bx++) for (int bz = bz0; bz <= bz1; bz++) for (int by = by0; by <= by1; by++) {
            BlockState s = level.getBlockState(m.set(bx, by, bz));
            if (s.isAir()) continue;
            VoxelShape shape = s.getCollisionShape(level, m, ctx);
            if (shape.isEmpty()) continue;
            List<AABB> boxes = shape.toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB b = boxes.get(i);
                if (!(b.maxX + bx > x0 && b.minX + bx < x1 && b.maxZ + bz > z0 && b.minZ + bz < z1)) continue;
                double top = b.maxY + by;
                if (top <= yLo || top > yHi + 1e-6) continue;
                if (!Double.isNaN(best) && Math.abs(top - prefer) >= Math.abs(best - prefer)) continue;
                // room over it, in this column
                if (BodyRules.blocked(level, ctx, new BlockPos.MutableBlockPos(), x0, top + 1e-3, z0, x1, top + HANG_HEADROOM, z1)) continue;
                best = top;
                tall = shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0 + 1e-6;
            }
        }
        out[0] = best;
        out[1] = tall ? 1 : 0;
    }

    /** A drop from a hang to the ledge below (Shift; SPEC §17.25): its top this far under the held one, the most. */
    public static final double DROP_MIN = 0.9, DROP_MAX = 3.2, DROP_OUT = 1.4;

    /**
     * The ledge under a hang that a drop catches (Shift; SPEC §17.25), the nearest one below: the same face direction,
     * its top {@link #DROP_MIN}-{@link #DROP_MAX} under the held one, its face up to {@link #DROP_OUT} out from the
     * hanging body (a sill or a balcony sticking out) or a little in; and the way to it free: out (or down) first, then
     * down (or in). Null for none. The body hangs at (hx, hy, hz) from {@code held}.
     */
    public static @Nullable Edge edgeBelow(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                           double hx, double hy, double hz, Edge held, double tol) {
        int axis = held.axis(), sign = held.sign();
        double fx = held.fx(), fz = held.fz();
        double lo = held.top() - DROP_MAX - hy, hi = held.top() - DROP_MIN - hy;
        for (double d = 0; d <= DROP_OUT + 1e-6; d += 0.15) {
            Edge e = findEdge(level, ctx, m, hx - fx * d, hy, hz - fz * d, axis, sign, HANG_REACH + tol, lo, hi, 0, 0.2);
            if (e == null) continue;
            // the way: level at whichever height is higher of the two holds' line, then straight along the other
            boolean out = ((axis == 0 ? e.hx() : e.hz()) - (axis == 0 ? hx : hz)) * sign < 0;
            double c0 = Math.min(axis == 0 ? hx : hz, axis == 0 ? e.hx() : e.hz()) - W, c1 = Math.max(axis == 0 ? hx : hz, axis == 0 ? e.hx() : e.hz()) + W;
            double t = axis == 0 ? hz : hx, levelY = out ? hy : e.hy();
            boolean across = axis == 0 ? BodyRules.blocked(level, ctx, m, c0 + 1e-3, levelY + 1e-3, t - W + 1e-3, c1 - 1e-3, levelY + H - 1e-3, t + W - 1e-3)
                    : BodyRules.blocked(level, ctx, m, t - W + 1e-3, levelY + 1e-3, c0 + 1e-3, t + W - 1e-3, levelY + H - 1e-3, c1 - 1e-3);
            if (across) continue;
            double cx = out ? e.hx() : hx, cz = out ? e.hz() : hz;
            if (BodyRules.blocked(level, ctx, m, cx - W + 1e-3, e.hy() + 1e-3, cz - W + 1e-3, cx + W - 1e-3, hy + H - 1e-3, cz + W - 1e-3)) continue;
            return e;
        }
        return null;
    }

    /** The edge found again near a hold: same face direction, top within ±tol of {@code topY}. */
    public static @Nullable Edge edgeNear(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                          double px, double py, double pz, int axis, int sign, double topY, double tol, double reach) {
        return findEdge(level, ctx, m, px, py, pz, axis, sign, reach, topY - tol - py, topY + tol - py, 0, 0.2);
    }

    /**
     * Where the feet go when climbing out over an edge, or null: something to stand on just past the face (the edge's
     * own top, or a walkable step up to {@link #CLIMB_STEP} over it: a stair eave's back half, a roof's next slab),
     * a standing body fits there, and the way up in front of the face and over the lip is clear to standing height.
     * A wall's coping (SPEC §17.27) is stood on with the body over its middle; a fence's or gate's rail is no place to
     * stand (VaultRules.over climbs over it instead).
     */
    public static double @Nullable [] climbOut(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, Edge e) {
        probes++;
        double sc = e.plane() + e.sign() * (W + 0.08), t = e.along();
        double hc = e.axis() == 0 ? e.hx() : e.hz();
        // what the feet land on: not a drop behind a thin lip
        double[] top = SCRATCH.get();
        if (e.axis() == 0) BodyRules.surface(level, ctx, m, sc - W + 0.02, t - W + 0.02, sc + W - 0.02, t + W - 0.02, e.top() - 0.5, e.top() + CLIMB_STEP, top);
        else BodyRules.surface(level, ctx, m, t - W + 0.02, sc - W + 0.02, t + W - 0.02, sc + W - 0.02, e.top() - 0.5, e.top() + CLIMB_STEP, top);
        if (Double.isNaN(top[0]) || top[0] < e.top() - 0.05) return null;
        if (top[1] != 0) {
            // a fence, wall or gate top: only a wall's coping takes the feet, the body centred on it
            double lx = e.axis() == 0 ? e.plane() + e.sign() * 0.05 : t, lz = e.axis() == 0 ? t : e.plane() + e.sign() * 0.05;
            VaultRules.rail(level, ctx, m, lx, lz, e.top() - 0.5, e.top() + 0.01, e.fx(), e.fz(), top);
            if (Double.isNaN(top[0]) || top[1] < VaultRules.WALL_MIN || top[1] > VaultRules.RAIL_MAX) return null;
            sc = e.plane() + e.sign() * top[1] / 2;
            if (e.axis() == 0) BodyRules.surface(level, ctx, m, sc - W + 0.02, t - W + 0.02, sc + W - 0.02, t + W - 0.02, e.top() - 0.5, e.top() + CLIMB_STEP, top);
            else BodyRules.surface(level, ctx, m, t - W + 0.02, sc - W + 0.02, t + W - 0.02, sc + W - 0.02, e.top() - 0.5, e.top() + CLIMB_STEP, top);
            if (Double.isNaN(top[0]) || top[0] < e.top() - 0.05) return null;
        }
        double sy = top[0];
        // the standing body there and the way over the lip to it, from the hold's column on
        double o0 = Math.min(hc, sc) - W + 1e-3, o1 = Math.max(hc, sc) + W - 1e-3;
        if (blockedAxis(level, ctx, m, e.axis(), o0, o1, sy + 1e-3, sy + H, t - W + 1e-3, t + W - 1e-3)) return null;
        // the way up: in front of the face from the hold to standing height over the lip
        if (blockedAxis(level, ctx, m, e.axis(), hc - W + 1e-3, hc + W - 1e-3, e.hy() + 1e-3, sy + H, t - W + 1e-3, t + W - 1e-3)) return null;
        return e.axis() == 0 ? new double[]{sc, sy, t} : new double[]{t, sy, sc};
    }

    /**
     * Jump help's edge snap: falling at a face whose top is at most {@link #SNAP} above the feet, with standing room on
     * it: the lift that puts the feet on top this tick (vanilla moves vertically first), or NaN.
     */
    public static double snapLift(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double px, double py, double pz, double vx, double vz) {
        double h = Math.sqrt(vx * vx + vz * vz);
        if (h < 0.05) return Double.NaN;
        probes++;
        int axis = Math.abs(vx) >= Math.abs(vz) ? 0 : 2, sign = (axis == 0 ? vx : vz) > 0 ? 1 : -1;
        double c = axis == 0 ? px : pz, t = axis == 0 ? pz : px;
        double face = nearestFace(level, ctx, m, axis, sign, c + sign * W, h + 0.05, py + 1e-3, py + SNAP, t - W + 0.01, t + W - 0.01);
        if (Double.isNaN(face)) return Double.NaN;
        double a0 = sign > 0 ? face + 0.01 : face - 0.3, a1 = sign > 0 ? face + 0.3 : face - 0.01;
        double[] top = SCRATCH.get();
        if (axis == 0) BodyRules.surface(level, ctx, m, a0, t - W + 0.01, a1, t + W - 0.01, py, py + SNAP + 0.01, top);
        else BodyRules.surface(level, ctx, m, t - W + 0.01, a0, t + W - 0.01, a1, py, py + SNAP + 0.01, top);
        if (Double.isNaN(top[0]) || top[1] != 0) return Double.NaN;
        double lift = top[0] - py;
        if (lift <= 1e-3 || lift > SNAP + 1e-6) return Double.NaN;
        // room to stand once over the lip
        double sc = face + sign * (W + 0.01);
        if (blockedAxis(level, ctx, m, axis, sc - W + 1e-3, sc + W - 1e-3, top[0] + 1e-3, top[0] + H, t - W + 1e-3, t + W - 1e-3)) return Double.NaN;
        return lift + 0.002;
    }

    /**
     * Sneaking forward off an edge (facing within 30° of an axis, nothing under the body 0.35 ahead): the hang just
     * over it, facing back at the face the feet stood on, with at least {@link #LOWER_DROP} free below; or null.
     */
    public static @Nullable Edge lowerEdge(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double px, double py, double pz, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int axis = Math.abs(fx) >= Math.abs(fz) ? 0 : 2;
        if (Math.max(Math.abs(fx), Math.abs(fz)) < Math.cos(Math.toRadians(30))) return null;
        int sign = (axis == 0 ? fx : fz) > 0 ? 1 : -1;
        probes++;
        double c = axis == 0 ? px : pz, t = axis == 0 ? pz : px;
        double ac = c + sign * 0.35;
        if (blockedAxis(level, ctx, m, axis, ac - W + 0.01, ac + W - 0.01, py - 0.6, py - 1e-3, t - W + 0.01, t + W - 0.01)) return null;
        // the face the feet stood on: back from past the edge
        double face = nearestFace(level, ctx, m, axis, -sign, c + sign * 0.6, 0.9, py - 0.5, py - 0.01, t - 0.25, t + 0.25);
        if (Double.isNaN(face)) return null;
        double hc = face + sign * (W + HANG_GAP);
        double hx = axis == 0 ? hc : px, hz = axis == 0 ? pz : hc;
        Edge e = edgeNear(level, ctx, m, hx, py - HANG_DROP, hz, axis, -sign, py, 0.3, 0.1);
        if (e == null || !lowerWorth(level, ctx, m, e)) return null;
        return e;
    }

    /**
     * Whether lowering into a hang on this edge is worth it: a real drop under it, or a ledge below for a drop to catch
     * (a facade's ledges are about three apart: SPEC §17.25). Ground near under an edge with no ledge is a step down.
     */
    public static boolean lowerWorth(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, Edge e) {
        return !groundWithin(level, ctx, m, e.hx(), e.top(), e.hz(), LOWER_DROP) || edgeBelow(level, ctx, m, e.hx(), e.hy(), e.hz(), e, 0) != null;
    }

    /** Scratch for surface tops, per thread (the integrated server and the client probe at the same time). */
    private static final ThreadLocal<double[]> SCRATCH = ThreadLocal.withInitial(() -> new double[3]);

    // ---- the one-button free-run (SPEC §17.14) -------------------------------------------------------------------------

    /** What the ground ahead of a free-runner asks for: nothing, a vault, a wall to jump at, an edge to leap from. */
    public static final int AHEAD_NONE = 0, AHEAD_VAULT = 1, AHEAD_WALL = 2, AHEAD_LEAP = 3;
    /** A vault clears a top this far over the feet (a plain jump's reach; the mantle takes 1.1-1.6 when it's yours). */
    public static final double VAULT_LO = 0.6, VAULT_HI = 1.2;
    /** Taller than this is a wall to jump at; a drop at least this deep ahead is an edge to leap from. */
    public static final double WALL_MIN = 1.6, LEAP_DROP = 2.0;
    /** How far ahead of the body's front the ground is read (blocks, plus two ticks of the speed for the edge). */
    public static final double AHEAD_REACH = 0.45;

    /**
     * The ground ahead of a body with feet at (px, py, pz) running along (fx, fz) at {@code speed}: a vault (a top
     * 0.6-1.2 over the feet with room over it and over the head), a wall (taller than 1.6), or an edge (no floor for
     * 2 blocks down, two ticks ahead). {@code out[0]}: the obstacle's top over the feet (NaN for an edge or nothing).
     */
    public static int ahead(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double px, double py, double pz,
                            double fx, double fz, double speed, double[] out) {
        out[0] = Double.NaN;
        double qx = px + fx * (W + AHEAD_REACH), qz = pz + fz * (W + AHEAD_REACH);
        int bx = Mth.floor(qx), bz = Mth.floor(qz), by0 = Mth.floor(py + 0.05);
        double top = Double.NaN;
        for (int by = by0; by <= by0 + 4; by++) {
            m.set(bx, by, bz);
            VoxelShape s = level.getBlockState(m).getCollisionShape(level, m, ctx);
            if (s.isEmpty()) break;
            double t = by + s.max(net.minecraft.core.Direction.Axis.Y);
            if (t <= py + 0.05) continue;
            top = t;
            if (s.max(net.minecraft.core.Direction.Axis.Y) < 1) break;
        }
        if (!Double.isNaN(top)) {
            double rise = top - py;
            out[0] = rise;
            if (rise > WALL_MIN) return AHEAD_WALL;
            if (rise <= VAULT_LO || rise > VAULT_HI) return AHEAD_NONE;
            // room over the top for the body, and over the head for the jump's arc
            int ty = Mth.floor(top + 1e-3);
            for (int dy = 0; dy < 2; dy++) {
                m.set(bx, ty + dy, bz);
                if (!level.getBlockState(m).getCollisionShape(level, m, ctx).isEmpty()) return AHEAD_NONE;
            }
            m.set(Mth.floor(px), by0 + 2, Mth.floor(pz));
            if (!level.getBlockState(m).getCollisionShape(level, m, ctx).isEmpty()) return AHEAD_NONE;
            return AHEAD_VAULT;
        }
        // an edge: two ticks on, no floor for LEAP_DROP down
        double ex = px + fx * (W + speed * 2 + 0.1), ez = pz + fz * (W + speed * 2 + 0.1);
        int ebx = Mth.floor(ex), ebz = Mth.floor(ez);
        for (int by = Mth.floor(py - 0.01); by >= Mth.floor(py - LEAP_DROP); by--) {
            m.set(ebx, by, ebz);
            if (!level.getBlockState(m).getCollisionShape(level, m, ctx).isEmpty()) return AHEAD_NONE;
            if (!level.getFluidState(m).isEmpty()) return AHEAD_NONE;
        }
        return AHEAD_LEAP;
    }

    /** Jump magnetism: a pull of at most this many degrees a tick, this many in all, for this many ticks, and only toward
     * a target within this many degrees of the way you're going. */
    public static final double PULL_STEP = 2.5, PULL_MAX = 14, PULL_CONE = 30;
    public static final int PULL_TICKS = 8;

    /**
     * Turns the horizontal motion (vx, vz) toward the point (tx, tz) seen from (px, pz) by at most {@code maxDeg},
     * keeping its speed, into out[0..2). Returns the degrees turned (signed magnitude, 0 when the target is more than
     * {@link #PULL_CONE} off the way you're going or the motion is too slow to steer).
     */
    public static double bend(double px, double pz, double vx, double vz, double tx, double tz, double maxDeg, double[] out) {
        out[0] = vx;
        out[1] = vz;
        double h = Math.sqrt(vx * vx + vz * vz), dx = tx - px, dz = tz - pz, d = Math.sqrt(dx * dx + dz * dz);
        if (h < 0.05 || d < 0.3 || maxDeg <= 0) return 0;
        double off = Math.atan2(vx * dz - vz * dx, vx * dx + vz * dz);
        if (Math.abs(Math.toDegrees(off)) > PULL_CONE) return 0;
        double turn = Math.copySign(Math.min(Math.abs(off), Math.toRadians(maxDeg)), off);
        double c = Math.cos(turn), s = Math.sin(turn);
        out[0] = vx * c - vz * s;
        out[1] = vx * s + vz * c;
        return Math.toDegrees(Math.abs(turn));
    }

    // ---- the floating kick ------------------------------------------------------------------------------------------

    /**
     * Exactly vanilla's {@code ServerGamePacketListenerImpl.noBlocksAround}: the box grown by 1/16 and 0.55 down is all
     * air. When this is false the server never counts the player as floating.
     */
    public static boolean noBlocksAround(net.minecraft.world.level.LevelReader level, AABB box) {
        AABB b = box.inflate(0.0625).expandTowards(0.0, -0.55, 0.0);
        return level.findBlocksIn(b).filterState(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase::isAir).allMatched();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** {@link BodyRules#blocked} with the box given along the normal axis (a), height and the other axis (t). */
    static boolean blockedAxis(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, int axis,
                               double a0, double a1, double y0, double y1, double t0, double t1) {
        return axis == 0 ? BodyRules.blocked(level, ctx, m, a0, y0, t0, a1, y1, t1) : BodyRules.blocked(level, ctx, m, t0, y0, a0, t1, y1, a1);
    }

    /**
     * The nearest collision face going +sign along axis from {@code from} (a body's side), within reach, overlapping
     * heights y0..y1 and the other axis t0..t1; NaN if none. Something already overlapping counts as touching.
     */
    static double nearestFace(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, int axis, int sign,
                              double from, double reach, double y0, double y1, double t0, double t1) {
        double r0 = sign > 0 ? from - 1e-4 : from - reach, r1 = sign > 0 ? from + reach : from + 1e-4;
        int a0 = Mth.floor(r0), a1 = Mth.floor(r1 - 1e-7), b0 = Mth.floor(t0), b1 = Mth.floor(t1 - 1e-7);
        int by0 = Mth.floor(y0) - 1, by1 = Mth.floor(y1 - 1e-7);
        double best = Double.NaN;
        for (int a = a0; a <= a1; a++) for (int b = b0; b <= b1; b++) for (int by = by0; by <= by1; by++) {
            int bx = axis == 0 ? a : b, bz = axis == 0 ? b : a;
            BlockState s = level.getBlockState(m.set(bx, by, bz));
            if (s.isAir()) continue;
            VoxelShape shape = s.getCollisionShape(level, m, ctx);
            if (shape.isEmpty()) continue;
            List<AABB> boxes = shape.toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB bb = boxes.get(i);
                if (!(bb.maxY + by > y0 && bb.minY + by < y1)) continue;
                double tMin = axis == 0 ? bb.minZ + bz : bb.minX + bx, tMax = axis == 0 ? bb.maxZ + bz : bb.maxX + bx;
                if (!(tMax > t0 && tMin < t1)) continue;
                double lo = axis == 0 ? bb.minX + bx : bb.minZ + bz, hi = axis == 0 ? bb.maxX + bx : bb.maxZ + bz;
                double f;
                if (sign > 0) {
                    if (hi <= from + 1e-6) continue;
                    f = Math.max(lo, from);
                    if (f > from + reach) continue;
                    if (Double.isNaN(best) || f < best) best = f;
                } else {
                    if (lo >= from - 1e-6) continue;
                    f = Math.min(hi, from);
                    if (f < from - reach) continue;
                    if (Double.isNaN(best) || f > best) best = f;
                }
            }
        }
        return best;
    }

    /**
     * One tick's block lookups shared by every probe (the batched probes): a 12×12×12 window around the body, filled
     * lazily and forgotten when {@link #begin} moves it on. Counts lookups and misses for the cost report. No
     * allocation after construction.
     */
    public static final class ProbeWindow implements BlockGetter {
        private static final int N = 12;
        private final BlockState[] states = new BlockState[N * N * N];
        private final int[] stamp = new int[N * N * N];
        private int gen, ox, oy, oz;
        private BlockGetter level;
        public long lookups, misses;

        public ProbeWindow begin(BlockGetter level, double x, double y, double z) {
            this.level = level;
            gen++;
            ox = Mth.floor(x) - N / 2;
            oy = Mth.floor(y) - 4;
            oz = Mth.floor(z) - N / 2;
            return this;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            lookups++;
            int x = pos.getX() - ox, y = pos.getY() - oy, z = pos.getZ() - oz;
            if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) {
                misses++;
                return level.getBlockState(pos);
            }
            int i = (y * N + z) * N + x;
            if (stamp[i] != gen) {
                misses++;
                stamp[i] = gen;
                states[i] = level.getBlockState(pos);
            }
            return states[i];
        }

        @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) { return level.getBlockEntity(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }
}
