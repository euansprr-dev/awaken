package dev.awaken.body;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Fences, gates and walls (SPEC §17.27; Euan, 2026-10-26: "make every fence and wall of every type mantleable,
 * everywhere, the city included"): the probe and the path both sides run, read from the real collision shapes, so any
 * fence, gate, wall, pane or bar on a block, or a modded shape taller than its block, is the same thing. A rail (a top
 * no wider than {@link #RAIL_MAX}) {@link #LOW_MIN}-{@link #HIGH_MAX} over the feet with floor beyond is vaulted in one
 * motion at crawl height (the eye rises ~0.4 over a 1.5 rail, as a vaulter's does); with a drop beyond, or a wall
 * walked at, the body goes up onto its top instead and stops there. Every tick of a path is checked against the
 * blocks (the server's move is the same and never corrects it); a body never lands where vanilla would push it out.
 * Nothing here touches a player: the client predicts with exactly what the server checks, and GameTests call it.
 */
public final class VaultRules {
    private VaultRules() {}

    /**
     * What a crossing is: a running vault (one hand planted, the legs swinging over), a walking one (both hands, a
     * lighter hop), a rail on a block climbed over in one motion (a garden wall), up onto the top (a wall's coping to
     * walk, a rail over a drop), and over a rail from a hang on it (the hang's climb out where there's no top to stand).
     */
    public static final int VAULT = 0, HOP = 1, HIGH = 2, ONTO = 3, OVER = 4;

    /** A top this far over the feet is a low rail (vanilla's jump clears 1.25), up to this; then a rail on a block. */
    public static final double LOW_MIN = 1.26, LOW_MAX = 1.65, HIGH_MAX = 2.6;
    /** A rail's top is no wider than this across (a fence 0.25, a wall 0.375-0.5; a block's 1.0 is a mantle's ledge). */
    public static final double RAIL_MAX = 0.62;
    /** A top this wide or wider is a wall's coping: walking at it goes up onto it, running vaults it. */
    public static final double WALL_MIN = 0.3;
    /** The whole obstacle, along the way, past what the body itself spans: a rail on a block at most. */
    public static final double EXTENT_MAX = 1.1;
    /** The way must cross the rail within ~53° of square on (the share of the way along the rail's normal). */
    public static final double SQUARE_MIN = 0.6;
    /** The feet clear the top by this; the body's front stays this short of a face until they have. */
    public static final double CLEAR = 0.06, MARGIN = 0.04;
    /** The floor beyond may be up to a step higher, or this much lower (deeper: up onto the top instead, never over). */
    public static final double DROP = 2.0, STEP = 0.6;
    /** The face this far in front of the body: walking, running (a running vault takes off early in its stride). */
    public static final double REACH_WALK = 0.6, REACH_RUN = 1.3;
    /** The server's extra reach (its body may be a tick behind) and how near its landing must be to the claimed one. */
    public static final double REACH_SERVER = 0.6, LAND_TOL = 1.0;
    /** Over the top at this speed: walking, running (at least; the run's own up to the most), a rail on a block. */
    public static final double V_WALK = 0.2, V_RUN = 0.28, V_MAX = 0.42, V_HIGH = 0.24, V_ONTO = 0.16;
    /** Rising, blocks a tick (a mantle rises ~0.45), walking a little slower. */
    public static final double RISE_RUN = 0.42, RISE_WALK = 0.32;
    /** The arc over the top: running, walking (the hands carry the body: barely any). */
    public static final double HUMP_RUN = 0.08, HUMP_WALK = 0.03;
    /** Coming down beyond, the body keeps this share of the speed it went over with. */
    public static final double KEEP = 0.85;
    /** The crossing's box height up and over (Pose.SWIMMING's); down beyond it stands again. */
    public static final double CRAWL = 0.6;
    public static final float EXHAUSTION = 0.05F;
    /** The server keeps the crawl box this many ticks past the path (its packets may lag). */
    public static final int GRACE = 4;

    static final double W = BodyRules.HALF_WIDTH, H = BodyRules.HEIGHT;
    /** The probe's step along the way. */
    static final double STEP_S = 1 / 32.0;

    /**
     * A crossing's plan. (x, y, z): the feet at the start; (dx, dz): the way (a unit); top: the highest collision the
     * body crosses; floor: the feet's height at the end. Along the way the body's centre goes 0 → sA while it rises
     * (the front never reaches the face before the feet clear the top), sA → sB over the top, then down beyond at
     * vDown a tick. (hx, hy, hz): where a hand plants on the rail; rail: the top's width across; tall: a collision
     * taller than its block (fence, wall, gate).
     */
    public record Vault(int kind, double x, double y, double z, double dx, double dz, double top, double floor, double sA, double sB,
                        double vDown, double hump, int rise, int over, int down, double hx, double hy, double hz, double rail, boolean tall) {
        public int ticks() {
            return rise + over + down;
        }

        /** Along the way where the body ends. */
        public double sEnd() {
            return sB + vDown * down;
        }

        public double endX() {
            return x + dx * sEnd();
        }

        public double endZ() {
            return z + dz * sEnd();
        }

        /** Going over: 0 on the ground, 1 while the body is over the top, back to 0 at the end (for the looks). */
        public float overAt(float k) {
            if (k <= 0 || k >= ticks()) return 0;
            if (k < rise) return k / rise;
            if (k <= rise + over) return 1;
            return 1 - (k - rise - over) / down;
        }

        /** The feet on tick k (0 = the start, {@link #ticks} = the end), into out[0..2]. */
        public void at(double k, double[] out) {
            double topC = top + CLEAR, s, py;
            if (k <= rise) {
                double u = Mth.clamp(k / rise, 0, 1);
                s = sA * u;
                py = y + (topC - y) * (1 - (1 - u) * (1 - u)); // fast off the ground, easing onto the height
            } else if (k <= rise + over) {
                double u = (k - rise) / over;
                s = sA + (sB - sA) * u;
                py = topC + hump * Math.sin(u * Math.PI);
            } else {
                // down beyond, standing again: fast off the top, easing onto the floor. Vanilla's camera eases the eye up
                // from the crawl's as the feet drop, so the view comes down level and lands without a dip
                double j = Math.min(k - rise - over, down), u = j / down;
                s = sB + vDown * j;
                py = topC - (topC - floor) * (1 - (1 - u) * (1 - u));
            }
            out[0] = x + dx * s;
            out[1] = py;
            out[2] = z + dz * s;
        }

        /** The kind's plant: a running vault's one hand, a hop's two, a climb's two (for the looks). */
        public boolean twoHands() {
            return kind != VAULT;
        }
    }

    // ---- the probe --------------------------------------------------------------------------------------------------

    /** Per-thread scratch (the integrated server and the client probe at the same time). */
    private static final class Scratch {
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        /** A scan's answer, a path point, the plant (x, top, z) and the rail there (width, normal axis, along). */
        final double[] t = new double[4], p = new double[3], plant = new double[3], rail = new double[3];
    }

    /** Coming down beyond: the shares of the speed tried, the farthest first; the lines a plant is looked for on. */
    private static final double[] KEEPS = {KEEP, 0.45, 0}, SIDES = {0, 0.2, -0.2};

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * The crossing ahead of a body with feet at (px, py, pz) facing {@code yaw}, or null. {@code run}: running (or the
     * free-run held), {@code speed}: its horizontal speed. Reads the obstacle the legs would run into within
     * {@code reach}: its top, the rail (the top's width across, at the crossing), its extent along the way, what's
     * beyond. A low rail is vaulted (running: one hand; walking: a hop) when the floor beyond takes the landing, else
     * the body goes up onto its top; a wall walked at is climbed onto (its coping is a walk). A rail on a block (a garden
     * wall) is climbed over in one motion when there's floor beyond; nothing else (a jump catches its top as a hang).
     */
    public static @Nullable Vault find(BlockGetter level, CollisionContext ctx, double px, double py, double pz, float yaw,
                                       double reach, boolean run, double speed) {
        double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        Scratch sc = SCRATCH.get();
        BlockPos.MutableBlockPos m = sc.m;
        // nothing at all in the way of the legs within reach: the cheap answer (the free-run asks every tick)
        double rx = dx * (reach + W), rz = dz * (reach + W);
        if (!BodyRules.blocked(level, ctx, m, px + Math.min(0, rx) - W, py + STEP, pz + Math.min(0, rz) - W,
                px + Math.max(0, rx) + W, py + 1.2, pz + Math.max(0, rz) + W)) return null;
        // 1. the face: the first step where the legs would run into something; then exactly where they touch
        double sTouch = -1;
        for (double s = 0; s <= reach + 1e-6; s += STEP_S) {
            if (legs(level, ctx, m, px + dx * s, py, pz + dz * s)) {
                sTouch = s;
                break;
            }
        }
        if (sTouch <= 0) return null;
        double lo = Math.max(0, sTouch - STEP_S), hi = sTouch;
        for (int i = 0; i < 6; i++) {
            double mid = (lo + hi) / 2;
            if (legs(level, ctx, m, px + dx * mid, py, pz + dz * mid)) hi = mid;
            else lo = mid;
        }
        double sFree = lo; // the body's centre this far along still clears the face
        // 2. the obstacle over a step's height, until the body is past it: its highest top
        double top = Double.NaN, sClear = -1;
        boolean tall = false;
        double[] t = sc.t;
        for (double s = sTouch; s <= sTouch + EXTENT_MAX + 2 * W / SQUARE_MIN + 1e-6; s += STEP_S) {
            double cx = px + dx * s, cz = pz + dz * s;
            scan(level, ctx, m, cx - W + 1e-3, cz - W + 1e-3, cx + W - 1e-3, cz + W - 1e-3, py + STEP, py + HIGH_MAX + 0.01, t);
            if (t[0] == Double.POSITIVE_INFINITY) return null; // it reaches over any vault (a wall too high, an overhang)
            if (Double.isNaN(t[0])) {
                sClear = s;
                break;
            }
            if (Double.isNaN(top) || t[0] > top) top = t[0];
            if (t[1] != 0) tall = true;
        }
        if (sClear < 0 || Double.isNaN(top)) return null;
        lo = sClear - STEP_S;
        hi = sClear;
        for (int i = 0; i < 6; i++) {
            double mid = (lo + hi) / 2, cx = px + dx * mid, cz = pz + dz * mid;
            scan(level, ctx, m, cx - W + 1e-3, cz - W + 1e-3, cx + W - 1e-3, cz + W - 1e-3, py + STEP, top + 0.01, t);
            if (Double.isNaN(t[0])) hi = mid;
            else lo = mid;
        }
        sClear = hi;
        double rise = top - py;
        if (rise < LOW_MIN || rise > HIGH_MAX + 1e-6) return null;
        // 3. the rail: where the way's centre line crosses the top (or either side of it: a lone post off the middle)
        if (!plant(level, ctx, sc, px, pz, dx, dz, sFree, sClear, top)) return null;
        double hx = sc.plant[0], hy = sc.plant[1], hz = sc.plant[2], rail = sc.rail[0], sPlant = sc.rail[2];
        double square = sc.rail[1] == 0 ? Math.abs(dx) : Math.abs(dz); // the way's share along the rail's normal
        if (rail > RAIL_MAX + 1e-6 || square < SQUARE_MIN) return null;
        if (sClear - sFree - 2 * W / square > EXTENT_MAX + 1e-6) return null; // a deep base: get up on it first
        // standing on the ground: solid from a step's height to the top under the hand (never a beam over open space)
        if (!solid(level, ctx, m, hx, hz, py + STEP, hy - 0.05)) return null;
        boolean low = rise <= LOW_MAX + 1e-6;
        Vault v;
        // a wall walked at: up onto its coping (a walk; on a block there's no hang to climb out of: the block's top
        // juts out under its face where the hanging feet would be)
        if (rail >= WALL_MIN && !run && (v = onto(level, ctx, sc, px, py, pz, dx, dz, sFree, sPlant, top, hx, hy, hz, rail, tall)) != null) return v;
        int kind = !low ? HIGH : run ? VAULT : HOP;
        double speedOver = !low ? (run ? V_HIGH : V_HIGH * 0.8) : run ? Mth.clamp(speed, V_RUN, V_MAX) : V_WALK;
        v = over(level, ctx, sc, kind, px, py, pz, dx, dz, sFree, sClear, top, speedOver, py - DROP, py + STEP, hx, hy, hz, rail, tall, run);
        if (v != null) return v;
        // a drop beyond (or nowhere to land): up onto the top and stop there, never over into it
        return onto(level, ctx, sc, px, py, pz, dx, dz, sFree, sPlant, top, hx, hy, hz, rail, tall);
    }

    /**
     * Over a rail from a hang on it (the hang's climb out where its top is no place to stand; SPEC §17.27): the body
     * rises from the hold at (px, py, pz) facing (fx, fz), over the rail whose top is {@code top}, down to the floor
     * beyond (from the top down to its own height plus {@link #DROP}). Null when there's nowhere to come down.
     */
    public static @Nullable Vault over(BlockGetter level, CollisionContext ctx, double px, double py, double pz, double fx, double fz, double top) {
        Scratch sc = SCRATCH.get();
        BlockPos.MutableBlockPos m = sc.m;
        double[] t = sc.t;
        // the far side: from the hold on along the way, over the rail (it may stand back from the face the body hangs
        // against) until the body has cleared it
        double sClear = -1;
        boolean seen = false;
        for (double s = 0; s <= 1.8; s += STEP_S) {
            double cx = px + fx * s, cz = pz + fz * s;
            scan(level, ctx, m, cx - W + 1e-3, cz - W + 1e-3, cx + W - 1e-3, cz + W - 1e-3, top - 1.0, top + 0.01, t);
            if (t[0] == Double.POSITIVE_INFINITY) return null;
            if (!Double.isNaN(t[0])) seen = true;
            else if (seen) {
                sClear = s;
                break;
            }
        }
        if (sClear < 0) return null;
        if (!plant(level, ctx, sc, px, pz, fx, fz, 0, sClear, top)) return null;
        if (sc.rail[0] >= WALL_MIN) return null; // a wall's coping: the climb out stands on it
        return over(level, ctx, sc, OVER, px, py, pz, fx, fz, 0, sClear, top, V_HIGH * 0.8, top - 1.5 - DROP, top + 0.05,
                sc.plant[0], sc.plant[1], sc.plant[2], sc.rail[0], true, false);
    }

    /** The legs' box at centre (x, z), feet at y: a step's height doesn't count as running into something. */
    private static boolean legs(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double x, double y, double z) {
        return BodyRules.blocked(level, ctx, m, x - W, y + STEP, z - W, x + W, y + 1.2, z + W);
    }

    /**
     * Over the top and down beyond: a plan whose every tick is clear, the body coming down along the way at the speed
     * it went over with (or less, then straight down, when that's what fits), onto floor between floorLo and floorHi.
     */
    private static @Nullable Vault over(BlockGetter level, CollisionContext ctx, Scratch sc, int kind, double px, double py, double pz,
                                        double dx, double dz, double sFree, double sClear, double top, double v, double floorLo, double floorHi,
                                        double hx, double hy, double hz, double rail, boolean tall, boolean run) {
        double sA = Math.max(0, sFree - MARGIN), sB = sClear + MARGIN, topC = top + CLEAR;
        int rise = Math.max(2, (int) Math.ceil((topC - py) / (run ? RISE_RUN : RISE_WALK) - 1e-6));
        int over = Math.max(2, (int) Math.round((sB - sA) / v)); // the run's own pace over the top, give or take
        double hump = kind == VAULT ? HUMP_RUN : HUMP_WALK;
        for (double keep : KEEPS) {
            double vDown = v * keep;
            // where it comes down decides how far it falls: read the floor, then again where that puts the landing
            double floor = floorAt(level, ctx, sc, px + dx * (sB + vDown * 4), pz + dz * (sB + vDown * 4), floorLo, floorHi);
            int down = 0;
            for (int i = 0; i < 2 && !Double.isNaN(floor); i++) {
                down = Math.max(3, (int) Math.round(4.4 * Math.sqrt(Math.max(0.05, topC - floor))));
                double sEnd = sB + vDown * down;
                floor = floorAt(level, ctx, sc, px + dx * sEnd, pz + dz * sEnd, floorLo, floorHi);
            }
            if (Double.isNaN(floor)) continue;
            down = Math.max(3, (int) Math.round(4.4 * Math.sqrt(Math.max(0.05, topC - floor))));
            // the floor read where the body really ends, at the height it ends at
            double sEnd = sB + vDown * down, at = floorAt(level, ctx, sc, px + dx * sEnd, pz + dz * sEnd, floorLo, floorHi);
            if (Double.isNaN(at) || Math.abs(at - floor) > 1e-6) continue;
            Vault plan = new Vault(kind, px, py, pz, dx, dz, top, floor, sA, sB, vDown, hump, rise, over, down, hx, hy, hz, rail, tall);
            if (clear(level, ctx, sc, plan)) return plan;
        }
        return null;
    }

    /** Up onto the top: the body rises clear of it, moves over its middle and settles on it, standing room over it. */
    private static @Nullable Vault onto(BlockGetter level, CollisionContext ctx, Scratch sc, double px, double py, double pz, double dx, double dz,
                                        double sFree, double sPlant, double top, double hx, double hy, double hz, double rail, boolean tall) {
        double sA = Math.max(0, sFree - MARGIN), topC = top + CLEAR;
        int rise = Math.max(2, (int) Math.ceil((topC - py) / RISE_WALK - 1e-6));
        int over = Math.max(2, (int) Math.ceil((sPlant - sA) / V_ONTO - 1e-6));
        // the top under the body's middle there: what it stands on (a wall's post a little higher than its side)
        double cx = px + dx * sPlant, cz = pz + dz * sPlant;
        double[] t = sc.t;
        scan(level, ctx, sc.m, cx - W + 1e-3, cz - W + 1e-3, cx + W - 1e-3, cz + W - 1e-3, top - 0.5, top + 0.01, t);
        if (Double.isNaN(t[0]) || t[0] == Double.POSITIVE_INFINITY || t[0] < top - 1e-3) return null;
        Vault plan = new Vault(ONTO, px, py, pz, dx, dz, top, top, sA, sPlant, 0, 0, rise, over, 2, hx, hy, hz, rail, tall);
        return clear(level, ctx, sc, plan) ? plan : null;
    }

    /**
     * Every tick of the plan (four steps a tick) clear: the crawl box up and over, the standing box down beyond and at
     * the end. The server's own move follows the same path and never corrects it (its box is the crawl's until the end,
     * never bigger than this side's), and nothing pushes the body out where it lands.
     */
    static boolean clear(BlockGetter level, CollisionContext ctx, Scratch sc, Vault v) {
        double[] p = sc.p;
        int n = v.ticks() * 4, up = (v.rise() + v.over()) * 4;
        for (int i = 1; i <= n; i++) {
            v.at(i / 4.0, p);
            double h = i <= up ? CRAWL : H;
            if (BodyRules.blocked(level, ctx, sc.m, p[0] - W + 1e-3, p[1] + 1e-3, p[2] - W + 1e-3, p[0] + W - 1e-3, p[1] + h - 1e-3, p[2] + W - 1e-3)) return false;
        }
        v.at(v.ticks(), p);
        return !BodyRules.blocked(level, ctx, sc.m, p[0] - W + 1e-3, p[1] + 1e-3, p[2] - W + 1e-3, p[0] + W - 1e-3, p[1] + H, p[2] + W - 1e-3);
    }

    /**
     * The floor under a standing body centred at (x, z), between lo and hi: NaN for none, something in the way, or a
     * corner of the body over a drop (a rail at a terrace's edge with a sliver of floor behind it is no landing).
     */
    private static double floorAt(BlockGetter level, CollisionContext ctx, Scratch sc, double x, double z, double lo, double hi) {
        double[] t = sc.t;
        scan(level, ctx, sc.m, x - W + 0.02, z - W + 0.02, x + W - 0.02, z + W - 0.02, lo - 0.01, hi + 0.01, t);
        if (Double.isNaN(t[0]) || t[0] == Double.POSITIVE_INFINITY) return Double.NaN;
        double floor = t[0];
        for (int c = 0; c < 4; c++) {
            double cx = x + ((c & 1) == 0 ? -1 : 1) * (W - 0.03), cz = z + ((c & 2) == 0 ? -1 : 1) * (W - 0.03);
            scan(level, ctx, sc.m, cx - 0.01, cz - 0.01, cx + 0.01, cz + 0.01, floor - STEP, floor + 0.01, t);
            if (Double.isNaN(t[0]) || t[0] == Double.POSITIVE_INFINITY) return Double.NaN;
        }
        return floor;
    }

    /**
     * Where a hand plants: the way's centre line (or a line 0.2 to either side: a lone post off the middle) where it
     * crosses the top, between sFree and sClear. sc.plant = (x, the top there, z); sc.rail = {the rail's width across,
     * its normal axis (0 x, 2 z), the centre's distance along the way when over the plant}. False when nothing is there.
     */
    private static boolean plant(BlockGetter level, CollisionContext ctx, Scratch sc, double px, double pz, double dx, double dz,
                                 double sFree, double sClear, double top) {
        double[] t = sc.t;
        for (double side : SIDES) {
            double ox = dz * side, oz = -dx * side;
            double first = -1, last = -1;
            for (double s = sFree; s <= sClear + W + 1e-6; s += STEP_S / 2) {
                double x = px + ox + dx * s, z = pz + oz + dz * s;
                scan(level, ctx, sc.m, x - 0.01, z - 0.01, x + 0.01, z + 0.01, top - 0.5, top + 0.01, t);
                boolean hit = !Double.isNaN(t[0]) && t[0] != Double.POSITIVE_INFINITY;
                if (hit && first < 0) first = s;
                if (hit) last = s;
                else if (first >= 0) break;
            }
            if (first < 0) continue;
            double s = (first + last) / 2, x = px + ox + dx * s, z = pz + oz + dz * s;
            rail(level, ctx, sc.m, x, z, top - 0.5, top + 0.01, dx, dz, t);
            if (Double.isNaN(t[0])) continue;
            sc.plant[0] = x;
            sc.plant[1] = t[0];
            sc.plant[2] = z;
            sc.rail[0] = t[1];
            sc.rail[1] = t[2];
            // the body's centre over the plant: along the way, the plant's own distance (its side offset is across)
            sc.rail[2] = s;
            return true;
        }
        return false;
    }

    /**
     * The collision box under the point (x, z) with the highest top in (yLo, yHi]: out = {its top, its width across (the
     * smaller of its two sides), the axis of its normal (0 x, 2 z: across the shorter side; a square post: the way's
     * main axis)}; out[0] NaN when there's none.
     */
    public static void rail(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double x, double z, double yLo, double yHi,
                            double dx, double dz, double[] out) {
        out[0] = Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(yLo) - 1; by <= Mth.floor(yHi); by++) {
            BlockState s = level.getBlockState(m.set(bx, by, bz));
            if (s.isAir()) continue;
            VoxelShape shape = s.getCollisionShape(level, m, ctx);
            if (shape.isEmpty()) continue;
            List<AABB> boxes = shape.toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB b = boxes.get(i);
                if (!(b.minX + bx <= x && b.maxX + bx >= x && b.minZ + bz <= z && b.maxZ + bz >= z)) continue;
                double top = b.maxY + by;
                if (top <= yLo || top > yHi || !Double.isNaN(out[0]) && top <= out[0]) continue;
                double sx = b.maxX - b.minX, sz = b.maxZ - b.minZ;
                out[0] = top;
                out[1] = Math.min(sx, sz);
                out[2] = Math.abs(sx - sz) < 1e-6 ? (Math.abs(dx) >= Math.abs(dz) ? 0 : 2) : sx < sz ? 0 : 2;
            }
        }
    }

    /** Whether the column at (x, z) is solid from y0 to y1, no gap of a fifth of a block (a rail standing on something). */
    private static boolean solid(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double x, double z, double y0, double y1) {
        for (double y = y0 + 0.1; y < y1; y += 0.2) {
            if (!BodyRules.blocked(level, ctx, m, x - 0.01, y - 0.01, z - 0.01, x + 0.01, y + 0.01, z + 0.01)) return false;
        }
        return true;
    }

    /**
     * The highest collision top over a footprint in (yLo, yHi]: out[0] = that top, NaN for none, +∞ when something
     * reaches above yHi; out[1] = 1 when that top is a collision taller than its block (fence, wall, gate).
     */
    static void scan(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m, double x0, double z0, double x1, double z1,
                     double yLo, double yHi, double[] out) {
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
                if (top <= yLo) continue;
                if (top > yHi) {
                    out[0] = Double.POSITIVE_INFINITY;
                    out[1] = 0;
                    return;
                }
                boolean t = shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0 + 1e-6;
                if (Double.isNaN(best) || top > best + 1e-6) {
                    best = top;
                    tall = t;
                } else if (Math.abs(top - best) <= 1e-6) {
                    tall |= t;
                }
            }
        }
        out[0] = best;
        out[1] = tall ? 1 : 0;
    }

    // ---- a rail underfoot (the wall-walk's balance) -------------------------------------------------------------------

    /** Walking the top of a rail, the body is drawn back over its middle at most this much a tick (blocks). */
    public static final double BALANCE = 0.05;

    /**
     * The rail a body with feet at (x, y, z) stands on, when it's narrower than the body (a wall's coping, a fence):
     * out = {the axis it runs along (0 x, 2 z), the coordinate of its middle across}; false when the feet are on
     * anything else (a broad top, a junction post, the ground).
     */
    public static boolean railUnder(BlockGetter level, CollisionContext ctx, double x, double y, double z, double[] out) {
        Scratch sc = SCRATCH.get();
        BlockPos.MutableBlockPos m = sc.m;
        double[] t = sc.t;
        // what holds the feet up: a top at their height under the footprint, and nothing broader
        scan(level, ctx, m, x - W + 0.01, z - W + 0.01, x + W - 0.01, z + W - 0.01, y - 0.05, y + 0.01, t);
        if (Double.isNaN(t[0]) || t[0] == Double.POSITIVE_INFINITY || t[1] == 0 || Math.abs(t[0] - y) > 0.02) return false;
        // the run: the boxes under the feet with that top, their extent along x and z
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        int bx0 = Mth.floor(x - W), bx1 = Mth.floor(x + W), bz0 = Mth.floor(z - W), bz1 = Mth.floor(z + W), by = Mth.floor(y - 1e-3);
        for (int bx = bx0; bx <= bx1; bx++) for (int bz = bz0; bz <= bz1; bz++) for (int yy = by - 1; yy <= by; yy++) {
            BlockState s = level.getBlockState(m.set(bx, yy, bz));
            if (s.isAir()) continue;
            VoxelShape shape = s.getCollisionShape(level, m, ctx);
            if (shape.isEmpty()) continue;
            List<AABB> boxes = shape.toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB b = boxes.get(i);
                if (Math.abs(b.maxY + yy - y) > 0.02) continue;
                if (!(b.maxX + bx > x - W && b.minX + bx < x + W && b.maxZ + bz > z - W && b.minZ + bz < z + W)) continue;
                x0 = Math.min(x0, b.minX + bx);
                x1 = Math.max(x1, b.maxX + bx);
                z0 = Math.min(z0, b.minZ + bz);
                z1 = Math.max(z1, b.maxZ + bz);
            }
        }
        if (x0 > x1) return false;
        double sx = x1 - x0, sz = z1 - z0;
        // narrower than the body one way, and running the other way under it
        if (sz < 2 * W - 0.05 && sx > sz + 0.2) {
            out[0] = 0;
            out[1] = (z0 + z1) / 2;
            return true;
        }
        if (sx < 2 * W - 0.05 && sz > sx + 0.2) {
            out[0] = 2;
            out[1] = (x0 + x1) / 2;
            return true;
        }
        return false;
    }
}
