package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.Land.Family;
import dev.awaken.gates.realms.check.MoveProof;
import dev.awaken.gates.realms.kit.crawl.Sculpt.Role;
import dev.awaken.progression.Rank;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The beats (SPEC §72.2): pieces of movement made by hand, each with sizes that roll. A beat is cut into a {@link BeatLine}
 * from its cursor on; it lays its floors, pits, walls and the pieces a kind's skin dresses (beams, lips, scuffs, rails,
 * lines), says the moves it asks for, where a fall puts you back, and where its family is taught, and moves the cursor
 * and the floor to where the next beat starts.
 * <p>
 * Every size is set from the body's own rules (MoveProof proves it at generation, at no more than 80% of a move's reach)
 * and so that the move is really needed: a gulf is past a sprint jump and past a jump that catches the far lip (7 at
 * least), a slide gate is one block high, a mantle is past a jump (a rise of 1.5), a lip is out of a jump's reach.
 * The frame of a beat is its lane (three wide, c -1 to 1) between walls at c ±2.
 */
public final class Beats {
    private Beats() {}

    /** A beat: its family, the lowest rank it comes at, and which way it takes the floor (+1 up, -1 down, 0 level). */
    public enum Beat {
        // low
        SLIDE(Family.LOW, Rank.E, 0, "A slide gate"),
        SLIDEJUMP(Family.LOW, Rank.D, 0, "A slide jump"),
        CHUTE(Family.LOW, Rank.D, -1, "A chute"),
        DROPSLIDE(Family.LOW, Rank.E, -1, "A landing slide"),
        TEETH(Family.LOW, Rank.C, 0, "Closing teeth"),
        RAIL(Family.FLOW, Rank.E, 0, "A rail run"),
        // wall
        GULF(Family.WALL, Rank.E, 1, "A wall-run gulf"),
        CHIMNEY(Family.WALL, Rank.D, 1, "A chimney"),
        SWITCHBACK(Family.WALL, Rank.B, 1, "A switchback"),
        RUNHANG(Family.WALL, Rank.C, 1, "A run to a hang"),
        CORNER(Family.WALL, Rank.A, 1, "A corner run"),
        // ledge
        MANTLE(Family.LEDGE, Rank.E, 1, "Mantle steps"),
        SHIMMY(Family.LEDGE, Rank.D, 0, "A hang and shimmy"),
        DROPHANG(Family.LEDGE, Rank.C, -1, "A drop to a hang"),
        JUMPHANG(Family.LEDGE, Rank.E, 1, "A jump to a hang"),
        LEDGES(Family.LEDGE, Rank.C, 1, "Ledge to ledge"),
        BROKEN(Family.LEDGE, Rank.B, 1, "A broken wall"),
        // grip
        PIPE(Family.GRIP, Rank.E, 1, "A pipe line"),
        CHAINFALL(Family.GRIP, Rank.D, -1, "A chain fall"),
        SWING(Family.GRIP, Rank.B, -1, "A swing between ropes"),
        // flow and fall
        CRUMBLE(Family.FLOW, Rank.D, 0, "A crumble run"),
        DROPROLL(Family.FLOW, Rank.E, -1, "A drop and roll"),
        // side routes: never on the way
        STRIDE(Family.SIDE, Rank.E, 0, "A stride wall"),
        ANCHOR(Family.SIDE, Rank.D, 0, "A swing out"),
        LEAP(Family.SIDE, Rank.C, 0, "A leap gap");

        public final Family family;
        public final Rank rank;
        public final int dy;
        /** What the beat is called where a player reads it (the bench hall's signs, a leg's name). */
        public final String title;

        Beat(Family family, Rank rank, int dy, String title) {
            this.family = family;
            this.rank = rank;
            this.dy = dy;
            this.title = title;
        }

        /** A beat on the way (a side route is never one). */
        public boolean onTheWay() {
            return family != Family.SIDE;
        }

        /** Whether its moves are really needed: a slide, a run, a hang. A rail, a chute, a crumble or a drop is the way's flow. */
        public boolean asks() {
            return switch (this) {
                case CHUTE, RAIL, PIPE, CRUMBLE, DROPROLL, STRIDE, ANCHOR, LEAP -> false;
                default -> true;
            };
        }
    }

    private static final int HEAD = BeatLine.HEAD;

    // ---- the plain words (SPEC §72.7: said once, the first time a family is met) ------------------------------------

    public static final String SAY_SLIDE = "Too low to walk under? Sprint, then crouch to slide through.";
    public static final String SAY_SLIDEJUMP = "Slide, and jump as you come out: the slide's speed carries you further than a run.";
    public static final String SAY_RUN = "Too far to jump? Sprint, jump beside the wall and hold Jump to run along it.";
    public static final String SAY_CHIMNEY = "Run up the wall, jump to the other wall, and keep going up between them.";
    public static final String SAY_SWITCH = "Run the first wall, and jump across to the other one where it ends.";
    public static final String SAY_MANTLE = "Too high to jump? Press Jump facing the ledge and you'll climb up onto it.";
    public static final String SAY_SHIMMY = "Sneak off the edge to hang from it, hold left or right to move along, then press Jump to climb up.";
    public static final String SAY_DROPHANG = "Sneak off the edge to hang, press Shift to drop to the ledge below, then let go.";
    public static final String SAY_JUMPHANG = "Jump at the high ledge and you'll catch it. Press Jump to climb up.";
    public static final String SAY_LEDGES = "Can't climb out? Press Jump while hanging to leap up to the next ledge.";
    public static final String SAY_ROPE = "Jump to the rope and you'll catch it. Hold Shift to slide down.";
    public static final String SAY_SWING = "On the rope, press Jump with left or right to swing across to the next one.";

    // ---- pieces every beat is made of -------------------------------------------------------------------------------

    /** Floor at f over a stretch of the lane, head room {@code head}, the stone under it kept. */
    static void ground(BeatLine L, int a0, int a1, int f, int head) {
        L.floor(a0, a1, -1, 1, f, f + head);
        L.underfoot(a0, a1, -1, 1, f);
    }

    /** The walls at c ±2 from {@code lo} to {@code hi} along a stretch. */
    static void sides(BeatLine L, int a0, int a1, int lo, int hi) {
        L.frame(a0, a1, lo, hi, -2, 2);
    }

    /** A plain stretch of floor, {@code n} long. */
    public static void pad(BeatLine L, int n) {
        int a0 = L.a, f = L.feet;
        ground(L, a0, a0 + n - 1, f, HEAD);
        sides(L, a0, a0 + n - 1, f - 2, f + HEAD);
        L.catchAt(a0 + n / 2, 0, f);
        L.a += n;
    }

    /** Half steps up or down by {@code dy} blocks (two cells a block): what takes up a leg's height between beats. */
    public static void stairs(BeatLine L, int dy) {
        if (dy == 0) return;
        int a0 = L.a, f2 = 2 * L.feet, step = dy > 0 ? 1 : -1, n = 2 * Math.abs(dy), lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int k = 0; k < n; k++) {
            f2 += step;
            int from = CrawlLand.Part.airFrom(f2), roof = from + HEAD;
            for (int c = -1; c <= 1; c++) L.col(a0 + k, c, f2, roof, 0);
            L.underfoot(a0 + k, a0 + k, -1, 1, Math.floorDiv(f2, 2));
            lo = Math.min(lo, Math.floorDiv(f2, 2) - 2);
            hi = Math.max(hi, roof);
        }
        sides(L, a0, a0 + n - 1, lo, hi);
        L.a += n;
        L.feet += dy;
        L.catchAt(L.a - 1, 0, L.feet);
    }

    /** A quarter turn on a 3x3 pad (+1 right, -1 left). */
    public static void turn(BeatLine L, int side) {
        L.turn(side, L.feet + HEAD);
        L.catchAt(0, 0, L.feet);
    }

    // ---- building one -----------------------------------------------------------------------------------------------

    /**
     * Cuts a beat at the cursor, sized {@code t} (0 the smallest, an E gate's, 1 the largest, an S gate's), on a side
     * ({@code side} ±1: the wall a run is along, the lip a shimmy is on), for a body of {@code rank}. False when this beat
     * can't be had at this size (nothing the proof would pass): the line is then thrown away by the one cutting it.
     */
    public static boolean build(Beat b, BeatLine L, double t, int side, Rank rank, Random r) {
        L.beats.add(b.name().toLowerCase());
        return switch (b) {
            case SLIDE -> slide(L, t, r, false, HEAD);
            case SLIDEJUMP -> slideJump(L, t);
            case CHUTE -> chute(L, t);
            case DROPSLIDE -> dropSlide(L, t, r);
            case TEETH -> slide(L, t, r, true, HEAD);
            case RAIL -> rail(L, t, r);
            case GULF -> gulf(L, t, side, rank, true);
            case CHIMNEY -> chimney(L, t, side, rank);
            case SWITCHBACK -> switchback(L, t, side, rank);
            case RUNHANG -> runHang(L, t, side, rank);
            case CORNER -> corner(L, t, side, rank);
            case MANTLE -> mantle(L, t, r, 2 + (int) Math.round(2 * t));
            case SHIMMY -> shimmy(L, t, side);
            case DROPHANG -> dropHang(L);
            case JUMPHANG -> jumpHang(L, t);
            case LEDGES -> ledges(L);
            case BROKEN -> mantle(L, t, r, 1) && jumpHang(L, t) && mantle(L, t, r, 1);
            case PIPE -> pipe(L, t);
            case CHAINFALL -> chainFall(L, t);
            case SWING -> swing(L, side, rank);
            case CRUMBLE -> crumble(L, t, r);
            case DROPROLL -> dropRoll(L, t);
            case STRIDE, ANCHOR, LEAP -> side(L, b, side);
        };
    }

    // ---- low --------------------------------------------------------------------------------------------------------

    /**
     * A slide gate: two cells to run up (dust on the second, the draught's mark), then one or two cells one block high
     * under a fallen beam or lintel with the rock fallen in over it, then out. At C and up one gate in three is narrow (the
     * middle cell only). {@code teeth}: the low cells are a press that comes down on a cycle (TEETH, C and up).
     */
    static boolean slide(BeatLine L, double t, Random r, boolean teeth, int runHead) {
        int a0 = L.a, F = L.feet, n = teeth || t >= 0.4 ? 2 : 1, head = F + HEAD;
        boolean narrow = !teeth && t >= 0.4 && r.nextInt(3) == 0;
        L.floor(a0, a0 + 1, -1, 1, F, F + runHead);
        L.underfoot(a0, a0 + 1, -1, 1, F);
        L.solid(a0 + 1, F - 1, -1, a0 + 1, F - 1, 1, Role.DUST, 0);
        for (int a = a0 + 2; a <= a0 + 1 + n; a++) for (int c = -1; c <= 1; c++) {
            if (narrow && c != 0) continue;
            L.floor(a, a, c, c, F, F + 1);
        }
        L.underfoot(a0 + 2, a0 + 1 + n, -1, 1, F);
        L.solid(a0 + 2, F + 1, -1, a0 + 1 + n, F + 1, 1, teeth ? Role.TOOTH : Role.BEAM, L.across());
        // the rock come down over it: kept, so nothing another part does opens a way over the top
        L.wall(a0 + 2, F + 2, -1, a0 + 1 + n, head, 1);
        if (narrow) {
            L.wall(a0 + 2, F, -1, a0 + 1 + n, F, -1);
            L.wall(a0 + 2, F, 1, a0 + 1 + n, F, 1);
        }
        int e = a0 + 2 + n;
        ground(L, e, e + 1, F, HEAD);
        sides(L, a0, e + 1, F - 2, Math.max(head, F + runHead));
        // the slide's lane is clear at the feet, and standing room before and after
        L.keep(a0, F, narrow ? 0 : -1, e + 1, F, narrow ? 0 : 1);
        L.keep(a0, F + 1, -1, a0 + 1, F + 1, 1);
        L.keep(e, F + 1, -1, e + 1, F + 1, 1);
        L.move(teeth ? "teeth" : "slide", "slide", Family.LOW, true, a0, 0, F, e, 0, F, 1, 0, 0, 2);
        L.catchAt(a0, 0, F);
        L.catchAt(e + 1, 0, F);
        L.lesson(Family.LOW, a0 + 1, 0, F, new double[][]{{a0, 0, F}, {a0 + 1.5, 0, F}, {e + 0.5, 0, F}}, SAY_SLIDE);
        if (teeth) L.trap(0, a0 + 2, F, -1, a0 + 1 + n, F, 1, 100, r.nextInt(100));
        L.a = e + 2;
        return true;
    }

    /** Slide under a low beam and jump out of it over a pit of two or three (the slide's speed is what carries it). */
    static boolean slideJump(BeatLine L, double t) {
        int a0 = L.a, F = L.feet, n = t >= 0.5 ? 2 : 1, g = t >= 0.5 ? 3 : 2;
        ground(L, a0, a0 + 2, F, HEAD);
        L.solid(a0 + 2, F - 1, -1, a0 + 2, F - 1, 1, Role.DUST, 0);
        int s0 = a0 + 3, s1 = a0 + 2 + n, o = s1 + 1, p0 = o + 1, p1 = o + g, l0 = p1 + 1, l1 = p1 + 4;
        L.floor(s0, s1, -1, 1, F, F + 1);
        L.underfoot(s0, s1, -1, 1, F);
        L.solid(s0, F + 1, -1, s1, F + 1, 1, Role.BEAM, L.across());
        L.wall(s0, F + 2, -1, s1, F + HEAD, 1);
        ground(L, o, o, F, 4);
        L.pit(p0, p1, -1, 1, F - 5, 2, F + 4);
        ground(L, l0, l1, F, HEAD);
        sides(L, a0, l1, F - 7, F + HEAD);
        L.keep(a0, F, -1, l1, F, 1);
        L.keep(a0, F + 1, -1, a0 + 2, F + 1, 1);
        L.keep(o, F + 1, -1, p1, F + 3, 1);
        L.keep(l0, F + 1, -1, l1, F + 1, 1);
        L.move("slidejump", "slidejump", Family.LOW, true, a0, 0, F, l0, 0, F, 1, 0, 0, 3);
        L.catchAt(a0, 0, F);
        L.catchAt(l0 + 1, 0, F);
        L.lesson(Family.LOW, a0 + 2, 0, F, new double[][]{{a0, 0, F}, {s0, 0, F}, {o + 0.5, 0, F + 0.2}, {p0 + 1, 0, F + 1.2}, {l0 + 0.5, 0, F}}, SAY_SLIDEJUMP);
        L.a = l1 + 1;
        return true;
    }

    /**
     * A chute: a long run of half steps under a low roof of lintels, down 4 to 8, that the stair slide carries you down. You
     * can walk it, bent under the lintels; sliding takes it in a breath. Its ceilings sit on whole cells (never a slab hung
     * low, which the slide's reading of the steps ahead would take for a rise).
     */
    static boolean chute(BeatLine L, double t) {
        int a0 = L.a, F = L.feet, K = 8 + 2 * (int) Math.round(4 * t);
        ground(L, a0, a0 + 1, F, HEAD);
        L.solid(a0 + 1, F - 1, -1, a0 + 1, F - 1, 1, Role.DUST, 0);
        int lo = F;
        for (int k = 1; k <= K; k++) {
            int a = a0 + 1 + k, f2 = 2 * F - k, from = CrawlLand.Part.airFrom(f2);
            // the ceiling two over the feet, on a whole cell: 2 over a whole step, 2.5 over a half one
            int roof = (int) Math.ceil(f2 / 2.0 + 1.8);
            for (int c = -1; c <= 1; c++) L.col(a, c, f2, roof, 0);
            L.underfoot(a, a, -1, 1, Math.floorDiv(f2, 2));
            if ((k & 1) == 0) L.solid(a, roof, -1, a, roof, 1, Role.BEAM, L.across());
            L.wall(a, roof, -1, a, roof + 1, 1);
            L.keep(a, from, -1, a, roof - 1, 1);
            lo = Math.min(lo, Math.floorDiv(f2, 2));
        }
        int foot = F - K / 2, e = a0 + 2 + K;
        ground(L, e, e + 3, foot, HEAD);
        sides(L, a0, e + 3, foot - 2, F + HEAD);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.keep(e, foot, -1, e + 3, foot + 1, 1);
        L.move("chute", "chute", Family.LOW, false, a0, 0, F, e, 0, foot, 1, 0, 0, 3);
        L.catchAt(a0, 0, F);
        L.catchAt(e + 1, 0, foot);
        L.a = e + 4;
        L.feet = foot;
        return true;
    }

    /** Drop three off an edge, land running and slide straight under a low beam. */
    static boolean dropSlide(BeatLine L, double t, Random r) {
        int a0 = L.a, F = L.feet;
        ground(L, a0, a0 + 1, F, HEAD);
        sides(L, a0, a0 + 1, F - 5, F + HEAD);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.catchAt(a0, 0, F);
        L.a = a0 + 2;
        L.feet = F - 3;
        // the landing is the slide's run-up, open to the edge above
        L.move("dropslide", "fall:3", Family.FLOW, false, a0, 0, F, a0 + 2, 0, F - 3, 1, 0, 0, 1);
        L.keep(a0 + 2, F - 3, -1, a0 + 3, F + 1, 1);
        return slide(L, t, r, false, HEAD + 3);
    }

    /** Benches, coffins or rails across the way, one after another, to vault or jump: the way's flow. */
    static boolean rail(BeatLine L, double t, Random r) {
        int a0 = L.a, F = L.feet, n = 2 + (int) Math.round(2 * t), len = 3 * n + 1;
        ground(L, a0, a0 + len - 1, F, HEAD);
        sides(L, a0, a0 + len - 1, F - 2, F + HEAD);
        for (int i = 0; i < n; i++) {
            int a = a0 + 1 + 3 * i;
            // a whole bench, or one with a gap a body can take (the side the gap is on rolls)
            int gap = t >= 0.4 && r.nextInt(3) == 0 ? (r.nextBoolean() ? 1 : -1) : 2;
            for (int c = -1; c <= 1; c++) if (c != gap) L.solid(a, F, c, a, F, c, Role.RAIL, L.across());
        }
        L.keep(a0, F + 1, -1, a0 + len - 1, F + 2, 1);
        L.move("rail", "walk:" + (len - 1), Family.FLOW, false, a0, 0, F, a0 + len - 1, 0, F, 1, 0, 0, 1);
        L.catchAt(a0, 0, F);
        L.catchAt(a0 + len - 1, 0, F);
        L.a = a0 + len;
        return true;
    }

    // ---- wall -------------------------------------------------------------------------------------------------------

    /**
     * A gulf you cross along a wall: three to take off from, a gap of 7 to 9 over deep water (past a sprint jump and past a
     * jump that catches the far lip), the far shelf a block up. The wall on {@code side} runs the whole way, scuffed at
     * running height over the gap; after the shelf's first cell it stands back a cell, so a run ends there and comes down
     * on the shelf. The run ends standing or hanging from the shelf's lip ("+climb").
     */
    static boolean gulf(BeatLine L, double t, int side, Rank rank, boolean teach) {
        int want = 7 + (int) Math.round(2 * t);
        int[] fit = GULF_FIT.computeIfAbsent(key(rank, side, want), k -> searchGulf(want, side, rank));
        if (fit.length == 0) return false;
        return gulfOf(L, fit[0], fit[1], side, teach, "gulf");
    }

    /**
     * The widest gulf up to {@code want} the body's slow and fast runs both cross: its far shelf a block up, else level
     * (7 at least: past a sprint jump that catches the lip), else a block down (8 at least, for the same reason).
     */
    private static int[] searchGulf(int want, int side, Rank rank) {
        for (int up = 1; up >= -1; up--) for (int g = Math.max(want, up < 0 ? 8 : 7); g >= (up < 0 ? 8 : 7); g--) {
            BeatLine L = scratch();
            gulfOf(L, g, up, side, false, "gulf");
            if (proves(L, rank)) return new int[]{g, up};
        }
        return NONE;
    }

    /** A search's cache key: the body's rank (never above D, as MoveProof proves), the side, and the beat's own size. */
    private static int key(Rank rank, int side, int size) {
        int body = Math.min(rank.ordinal(), Rank.D.ordinal());
        return (size * 4 + body * 2) + (side > 0 ? 1 : 0);
    }

    /** The gulf's cells for a gap {@code g} and a far shelf {@code up} higher. */
    static boolean gulfOf(BeatLine L, int g, int up, int side, boolean teach, String beat) {
        int a0 = L.a, F = L.feet, s = side, p0 = a0 + 3, p1 = a0 + 2 + g, f0 = p1 + 1, f1 = p1 + 3, far = F + up;
        ground(L, a0, a0 + 2, F, HEAD + 1);
        L.pit(p0, p1, -1, 1, F - 8, 2, F + HEAD + 1);
        L.floor(f0, f1, -1, 1, far, far + HEAD);
        L.underfoot(f0, f1, -1, 1, far);
        // the far shelf's face stands from the water to its lip: kept, so a run that comes in low catches it
        L.wall(f0, F - 8, -1, f0, far - 1, 1);
        // the run wall, the other wall, and the run wall standing back after the shelf's first cell
        L.wall(a0, F - 9, 2 * s, f0, far + HEAD, 2 * s);
        L.wall(a0, F - 9, -2 * s, f1, far + HEAD, -2 * s);
        L.floor(f0 + 1, f1, 2 * s, 2 * s, far, far + HEAD);
        L.underfoot(f0 + 1, f1, 2 * s, 2 * s, far);
        L.wall(f0 + 1, far - 2, 3 * s, f1, far + HEAD, 3 * s);
        L.wall(p0, F - 9, -1, p1, F - 9, 1);
        L.solid(p0, F + 1, 2 * s, p1, F + 2, 2 * s, Role.SCUFF, 0);
        // the flight: clear from the take-off to the shelf, at the run's height
        L.keep(a0, F, -1, a0 + 2, F + 2, 1);
        L.keep(p0, F, -1, p1, F + 4, 1);
        L.keep(f0, far, -1, f1, far + 2, 1);
        L.move(beat, "run+climb", Family.WALL, true, a0 + 1, s, F, f0, s, far, 1, 0, 0, 2);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(f0 + 1, 0, far);
        if (teach) L.lesson(Family.WALL, a0 + 1, s, F, new double[][]{{a0 + 1, s, F}, {p0, s, F + 0.8}, {p0 + g / 2.0, s, F + 1.2}, {f0 + 0.5, s, far}}, SAY_RUN);
        L.a = f1 + 1;
        L.feet = far;
        return true;
    }

    /**
     * A run to a hang: the gulf, but the far side is a face whose lip is two or three up: the run ends at it and catches the
     * lip. The gap and the lip are the largest the body's run proves at (the jump that catches a lip that high reaches 5).
     */
    static boolean runHang(BeatLine L, double t, int side, Rank rank) {
        int[] fit = RUNHANG_FIT.computeIfAbsent(key(rank, side, 0), k -> searchRunHang(side, rank));
        if (fit.length == 0) return false;
        return gulfOf(L, fit[0], fit[1], side, true, "runhang");
    }

    private static final ConcurrentHashMap<Integer, int[]> RUNHANG_FIT = new ConcurrentHashMap<>(), CHIMNEY_FIT = new ConcurrentHashMap<>(),
            SWITCH_FIT = new ConcurrentHashMap<>(), SWING_FIT = new ConcurrentHashMap<>(), GULF_FIT = new ConcurrentHashMap<>();
    /** What a search found nothing for (a ConcurrentHashMap holds no nulls). */
    private static final int[] NONE = new int[0];

    private static int[] searchRunHang(int side, Rank rank) {
        for (int up = 3; up >= 2; up--) for (int g = 7; g >= 5; g--) {
            BeatLine L = scratch();
            gulfOf(L, g, up, side, false, "runhang");
            if (proves(L, rank)) return new int[]{g, up};
        }
        return NONE;
    }

    /**
     * A chimney: a corridor two wide over a pit, its walls running on; wall-jump from one to the other up to a lip at its
     * end, and climb out. How many jumps grows with the rank (2 at D, 4 at A and S); the corridor's length and the lip's
     * height are what the body's own chain proves, slow and fast.
     */
    static boolean chimney(BeatLine L, double t, int side, Rank rank) {
        int links = 2 + (int) Math.round(2 * t);
        int[] fit = CHIMNEY_FIT.computeIfAbsent(key(rank, side, links), k -> searchChimney(links, side, rank));
        if (fit.length == 0) return false;
        chimneyOf(L, fit[0], fit[1], links, side, true);
        return true;
    }

    private static int[] searchChimney(int links, int side, Rank rank) {
        for (int up = links + 3; up >= links + 1; up--) for (int len = 4; len <= 14; len++) {
            BeatLine L = scratch();
            chimneyOf(L, len, up, links, side, false);
            if (proves(L, rank)) return new int[]{len, up};
        }
        return NONE;
    }

    /** The chimney's cells: take-off (the lane), the corridor {@code len} long (c 0 and 1 if side is +1), the lip {@code up} high. */
    static void chimneyOf(BeatLine L, int len, int up, int links, int side, boolean teach) {
        int a0 = L.a, F = L.feet, s = side, c0 = s > 0 ? 0 : -1, c1 = s > 0 ? 1 : 0, wa = s > 0 ? -1 : 1, wb = s > 0 ? 2 : -2;
        int p0 = a0 + 3, p1 = a0 + 2 + len, top = F + up, roof = top + 4;
        ground(L, a0, a0 + 2, F, HEAD);
        L.pit(p0, p1, c0, c1, F - 6, 2, roof);
        int e = p1 + 1;
        L.floor(e, e + 2, -1, 1, top, top + HEAD);
        L.underfoot(e, e + 2, -1, 1, top);
        L.solid(e, top - 1, c0, e, top - 1, c1, Role.LIP, 0);
        L.wall(e, F - 7, -1, e, top - 2, 1);
        // the two walls of the corridor, scuffed where the feet go; the take-off's walls go up with them
        L.wall(p0, F - 7, wa, p1, roof, wa);
        L.wall(p0, F - 7, wb, p1, roof, wb);
        L.wall(a0, F - 2, -2, a0 + 2, roof, -2);
        L.wall(a0, F - 2, 2, a0 + 2, roof, 2);
        L.wall(e, top - 2, -2, e + 2, top + HEAD, -2);
        L.wall(e, top - 2, 2, e + 2, top + HEAD, 2);
        L.wall(p0, F - 7, c0, p1, F - 7, c1);
        for (int k = 0; k <= links; k++) {
            int y = F + 1 + (int) Math.round(k * 1.3);
            if (y + 1 >= roof) break;
            L.solid(p0, y, k % 2 == 0 ? wa : wb, p1, y + 1, k % 2 == 0 ? wa : wb, Role.SCUFF, 0);
        }
        L.keep(p0, F, c0, p1, roof - 1, c1);
        L.keep(a0, F, -1, a0 + 2, F + 2, 1);
        L.keep(e, top, -1, e + 2, top + 2, 1);
        // the take-off is on the lane cell next to the first wall
        int cs = s > 0 ? 0 : 0;
        L.move("chimney", "chain:" + links + "+climb", Family.WALL, true, a0 + 1, cs, F, e, cs, top, 1, 0, 0, 2);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(e + 1, 0, top);
        if (teach) L.lesson(Family.WALL, a0 + 1, cs, F, new double[][]{{a0 + 1, cs, F}, {p0 + 0.5, c0, F + 1}, {p0 + 1.5, c1, F + 2.3},
                {p0 + 2.5, c0, F + 3.6}, {e - 0.5, c1, top - 1.6}, {e + 0.5, cs, top}}, SAY_CHIMNEY);
        L.a = e + 3;
        L.feet = top;
    }

    /**
     * A switchback: a long gulf (9 to 12) with the wall on one side for its first half and on the other side for its
     * second: run the first, jump across where it ends, run the second to the shelf. Sized by the body's own proof.
     */
    static boolean switchback(BeatLine L, double t, int side, Rank rank) {
        int[] fit = SWITCH_FIT.computeIfAbsent(key(rank, side, 0), k -> searchSwitch(side, rank));
        if (fit.length == 0) return false;
        switchOf(L, fit[0], fit[1], side, true);
        return true;
    }

    private static int[] searchSwitch(int side, Rank rank) {
        for (int g = 12; g >= 9; g--) for (int h = g / 2 - 1; h <= g / 2 + 1; h++) {
            BeatLine L = scratch();
            switchOf(L, g, h, side, false);
            if (proves(L, rank)) return new int[]{g, h};
        }
        return NONE;
    }

    static void switchOf(BeatLine L, int g, int h, int side, boolean teach) {
        int a0 = L.a, F = L.feet, s = side, p0 = a0 + 3, p1 = a0 + 2 + g, f0 = p1 + 1, f1 = p1 + 3, far = F + 1, sw = p0 + h;
        ground(L, a0, a0 + 2, F, HEAD + 1);
        L.pit(p0, p1, -1, 1, F - 8, 2, F + HEAD + 1);
        // wall A on side s for the take-off and the first h cells; after it, the side is open over the pit
        L.wall(a0, F - 9, 2 * s, sw - 1, F + HEAD + 1, 2 * s);
        L.pit(sw, p1, 2 * s, 2 * s, F - 8, 2, F + HEAD + 1);
        L.wall(sw, F - 9, 3 * s, f1, F + HEAD + 1, 3 * s);
        // wall B on the other side from the switch on; before it that side is open over the pit
        L.wall(a0, F - 2, -2 * s, a0 + 2, F + HEAD + 1, -2 * s);
        L.pit(p0, sw - 1, -2 * s, -2 * s, F - 8, 2, F + HEAD + 1);
        L.wall(p0, F - 9, -3 * s, sw - 1, F + HEAD + 1, -3 * s);
        L.wall(sw, F - 9, -2 * s, f1, far + HEAD, -2 * s);
        L.solid(p0, F + 1, 2 * s, sw - 1, F + 2, 2 * s, Role.SCUFF, 0);
        L.solid(sw, F + 1, -2 * s, p1, F + 2, -2 * s, Role.SCUFF, 0);
        L.floor(f0, f1, -1, 1, far, far + HEAD);
        L.floor(f0, f1, 2 * s, 2 * s, far, far + HEAD);
        L.underfoot(f0, f1, -1, 1, far);
        L.underfoot(f0, f1, 2 * s, 2 * s, far);
        L.wall(f0, F - 8, -1, f0, far - 1, 1);
        L.wall(p0, F - 9, -2, p1, F - 9, 2);
        L.wall(p0, F - 9, -3, p1, F - 9, 3);
        L.keep(p0, F, -1, p1, F + 4, 1);
        L.keep(a0, F, -1, a0 + 2, F + 2, 1);
        L.keep(f0, far, -1, f1, far + 2, 1);
        L.move("switchback", "switch+climb", Family.WALL, true, a0 + 1, s, F, f0, -s, far, 1, 0, 0, 2);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(f0 + 1, 0, far);
        if (teach) L.lesson(Family.WALL, a0 + 1, s, F, new double[][]{{a0 + 1, s, F}, {p0 + 1, s, F + 1}, {sw - 0.5, s, F + 1.2}, {sw + 1, -s, F + 1.4}, {f0 + 0.5, -s, far}}, SAY_SWITCH);
        L.a = f1 + 1;
        L.feet = far;
    }

    /** A corner run: a gulf, a pad that turns, and another gulf on the outside wall of the turn. */
    static boolean corner(BeatLine L, double t, int side, Rank rank) {
        if (!gulf(L, Math.min(t, 0.5), side, rank, true)) return false;
        turn(L, side);
        // a cell of floor between the turn and the next take-off
        pad(L, 1);
        return gulf(L, Math.min(t, 0.5), -side, rank, false);
    }

    // ---- ledge ------------------------------------------------------------------------------------------------------

    /**
     * Mantle steps: {@code n} rises of a block and a half (past a jump, inside a mantle), two or three cells deep, as stone
     * steps or plinths in the kind's own stone; an odd count ends with a half step so the way goes on at a whole block.
     */
    static boolean mantle(BeatLine L, double t, Random r, int n) {
        int a0 = L.a, F = L.feet, f2 = 2 * F, a = a0, lo = F - 2, hi = F + HEAD;
        // the approach
        ground(L, a, a + 1, F, HEAD);
        L.keep(a, F, -1, a + 1, F + 3, 1);
        a += 2;
        L.catchAt(a0, 0, F);
        for (int i = 0; i < n; i++) {
            int y0 = Math.floorDiv(f2, 2), nf2 = f2 + 3, depth = 2 + r.nextInt(2);
            int from = CrawlLand.Part.airFrom(nf2), y1 = Math.floorDiv(nf2, 2), roof = from + HEAD;
            for (int k = 0; k < depth; k++) for (int c = -1; c <= 1; c++) L.col(a + k, c, nf2, roof, 0);
            L.underfoot(a, a + depth - 1, -1, 1, y1);
            // the riser: plinth from the floor below up to under the step's top, a half step its top when it ends on one
            L.solid(a, Math.floorDiv(f2, 2), -1, a, y1 - 1, 1, Role.PLINTH, 0);
            if ((nf2 & 1) != 0) L.solid(a, y1, -1, a, y1, 1, Role.PLINTH_TOP, 0);
            L.keep(a, from, -1, a + depth - 1, from + 3, 1);
            // from the last cell before the riser onto the step
            L.move("mantle", "mantle", Family.LEDGE, true, a - 1, 0, y0, a, 0, y1, 1, 0, 0, 1);
            if (i == 0) L.lesson(Family.LEDGE, a - 1, 0, y0, new double[][]{{a - 1.5, 0, y0}, {a - 0.6, 0, y0}, {a - 0.4, 0, y0 + 1.2}, {a + 0.4, 0, nf2 / 2.0}}, SAY_MANTLE);
            L.catchAt(a + depth - 1, 0, y1);
            hi = Math.max(hi, roof);
            a += depth;
            f2 = nf2;
        }
        if ((f2 & 1) != 0) {
            f2++;
            int y = f2 / 2;
            ground(L, a, a, y, HEAD);
            L.keep(a, y, -1, a, y + 1, 1);
            hi = Math.max(hi, y + HEAD);
            a++;
        }
        sides(L, a0, a - 1, lo, hi);
        L.a = a;
        L.feet = f2 / 2;
        return true;
    }

    /**
     * Hang and shimmy: the floor ends, and along the wall on {@code side} runs a hand slot (the lip at floor height, a slab
     * over it: no room to stand or crawl) over a drop. Step to the slot's first cell, sneak off it to hang, shimmy along the
     * slot six to ten cells and climb up at its last. The drop's roof is low: no jump or run across it.
     */
    static boolean shimmy(BeatLine L, double t, int side) {
        int a0 = L.a, F = L.feet, s = side, ls = 6 + (int) Math.round(4 * t), en = a0 + 2, ex = en + ls - 1;
        ground(L, a0, a0 + 1, F, HEAD);
        // the slot's first and last cells: floor with head room
        L.floor(en, en, s, s, F, F + HEAD);
        L.floor(ex, ex, s, s, F, F + HEAD);
        L.underfoot(en, en, s, s, F);
        L.underfoot(ex, ex, s, s, F);
        // the slot between: the lip, the cap over it, the wall over that
        for (int a = en + 1; a < ex; a++) {
            L.col(a, s, 2 * F, F + 1, 0);
            L.solid(a, F - 1, s, a, F - 1, s, Role.LIP, 0);
            L.solid(a, F, s, a, F, s, Role.CAP, 0);
            L.wall(a, F + 1, s, a, F + HEAD, s);
            L.wall(a, F - 3, s, a, F - 2, s);
        }
        // the drop beside it: the rest of the lane, a low roof over it
        L.pit(en, ex, -s, 0, F - 6, 2, F + 2);
        L.wall(en, F + 2, -s, ex, F + HEAD, 0);
        L.wall(en, F - 7, -1, ex, F - 7, 1);
        int e = ex + 1;
        ground(L, e, e + 2, F, HEAD);
        sides(L, a0, e + 2, F - 7, F + HEAD);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.keep(en, F, s, en, F + 1, s);
        L.keep(ex, F, s, ex, F + 1, s);
        L.keep(en, F - 2, 0, ex, F + 1, 0);
        L.keep(e, F, -1, e + 2, F + 1, 1);
        L.move("shimmy", "lower+shimmy:" + s * (ls - 1) + "+climb", Family.LEDGE, true, en, s, F, ex, s, F, 0, -s, 0, 0);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(e + 1, 0, F);
        L.lesson(Family.LEDGE, en, s, F, new double[][]{{en, s, F}, {en, 0, F - 1.5}, {en + ls / 2.0, 0, F - 1.5}, {ex, 0, F - 1.5}, {ex, s, F}}, SAY_SHIMMY);
        L.a = e + 3;
        return true;
    }

    /**
     * A drop to a hang, down seven: sneak off the edge into a hang, drop (Shift) to the sill three below set in the wall
     * under you (a slot over it too low to stand in), let go onto the floor. Walking off is a fall the run catches.
     */
    static boolean dropHang(BeatLine L) {
        int a0 = L.a, F = L.feet, bot = F - 7, d0 = a0 + 2, d1 = a0 + 4;
        ground(L, a0, a0 + 1, F, HEAD);
        // the face under the edge: the floor, the stone under it, the slot, the sill, the stone down to the floor below
        L.airBox(a0 + 1, F - 3, -1, a0 + 1, F - 3, 1);
        L.keep(a0 + 1, F - 3, -1, a0 + 1, F - 3, 1);
        L.solid(a0 + 1, F - 4, -1, a0 + 1, F - 4, 1, Role.LIP, 0);
        L.wall(a0 + 1, F - 2, -1, a0 + 1, F - 2, 1);
        L.wall(a0 + 1, bot - 1, -1, a0 + 1, F - 5, 1);
        L.floor(d0, d1, -1, 1, bot, F + HEAD);
        L.underfoot(d0, d1, -1, 1, bot);
        int e = d1 + 1;
        ground(L, e, e + 1, bot, HEAD);
        sides(L, a0, e + 1, bot - 2, F + HEAD);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.keep(d0, bot, -1, d1, F + 1, 1);
        L.move("drophang", "lower+drop+let", Family.LEDGE, true, a0 + 1, 0, F, d0, 0, bot, 1, 0, 0, 1);
        L.catchAt(a0, 0, F);
        L.catchAt(d0 + 1, 0, bot);
        L.lesson(Family.LEDGE, a0 + 1, 0, F, new double[][]{{a0 + 1, 0, F}, {d0, 0, F - 1.5}, {d0, 0, F - 4.5}, {d0 + 0.3, 0, bot}}, SAY_DROPHANG);
        L.a = e + 2;
        L.feet = bot;
        return true;
    }

    /** A jump to a hang: a gap of two or three, the far lip three up (out of a jump's reach, in the catch's). */
    static boolean jumpHang(BeatLine L, double t) {
        int a0 = L.a, F = L.feet, g = t >= 0.5 ? 3 : 2, p0 = a0 + 3, p1 = a0 + 2 + g, w = p1 + 1, top = F + 3;
        ground(L, a0, a0 + 2, F, HEAD);
        L.pit(p0, p1, -1, 1, F - 6, 2, top + 5);
        L.floor(w, w + 2, -1, 1, top, top + HEAD);
        L.underfoot(w, w + 2, -1, 1, top);
        L.solid(w, top - 1, -1, w, top - 1, 1, Role.LIP, 0);
        L.wall(w, F - 7, -1, w, top - 2, 1);
        L.wall(p0, F - 7, -1, p1, F - 7, 1);
        sides(L, a0, w + 2, F - 7, top + HEAD);
        L.keep(a0, F, -1, a0 + 2, F + 2, 1);
        L.keep(p0, F, -1, p1, top + 4, 1);
        L.keep(w, top, -1, w + 2, top + 1, 1);
        L.move("jumphang", "jumphang+climb", Family.LEDGE, true, a0 + 1, 0, F, w, 0, top, 1, 0, 0, 2);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(w + 1, 0, top);
        L.lesson(Family.LEDGE, a0 + 1, 0, F, new double[][]{{a0 + 1, 0, F}, {p0 + 0.5, 0, F + 1.2}, {w - 0.4, 0, top - 1.5}, {w + 0.5, 0, top}}, SAY_JUMPHANG);
        L.a = w + 3;
        L.feet = top;
        return true;
    }

    /**
     * Ledge to ledge, up six: jump to a lip, and two ledge jumps up the face to the next lips (each a slot too low to
     * climb out into), then out at the top.
     */
    static boolean ledges(BeatLine L) {
        int a0 = L.a, F = L.feet, p0 = a0 + 3, p1 = a0 + 4, w = a0 + 5, top = F + 6;
        ground(L, a0, a0 + 2, F, HEAD);
        L.pit(p0, p1, -1, 1, F - 6, 2, top + 3);
        L.col(w, -1, 2 * top, top + HEAD, 0);
        L.col(w, 0, 2 * top, top + HEAD, 0);
        L.col(w, 1, 2 * top, top + HEAD, 0);
        L.solid(w, F + 3, -1, w, F + 3, 1, Role.CAP, 0);
        L.solid(w, F + 4, -1, w, F + 4, 1, Role.STEP, 0);
        L.solid(w, F + 5, -1, w, F + 5, 1, Role.LIP, 0);
        L.wall(w, F - 7, -1, w, F + 2, 1);
        L.wall(w + 1, F + 2, -1, w + 1, F + 4, 1);
        L.floor(w + 1, w + 3, -1, 1, top, top + HEAD);
        L.underfoot(w + 1, w + 3, -1, 1, top);
        L.wall(p0, F - 7, -1, p1, F - 7, 1);
        sides(L, a0, w + 3, F - 7, top + HEAD);
        L.keep(a0, F, -1, a0 + 2, F + 2, 1);
        L.keep(p0, F, -1, p1, top + 2, 1);
        L.keep(w, top, -1, w + 3, top + 1, 1);
        L.move("ledges", "jumphang+ledge:0+ledge:0+climb", Family.LEDGE, true, a0 + 1, 0, F, w, 0, top, 1, 0, 0, 2);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(w + 1, 0, top);
        L.lesson(Family.LEDGE, a0 + 1, 0, F, new double[][]{{a0 + 1, 0, F}, {w - 0.4, 0, F + 1.5}, {w - 0.4, 0, F + 3}, {w - 0.4, 0, F + 4.5}, {w + 0.5, 0, top}}, SAY_LEDGES);
        L.a = w + 4;
        L.feet = top;
        return true;
    }

    // ---- grip -------------------------------------------------------------------------------------------------------

    /** A line up a wall (a pipe, a chain, a ladder, ivy) five to eight, to a shelf; anyone can climb it, the grip does it fast. */
    static boolean pipe(BeatLine L, double t) {
        int a0 = L.a, F = L.feet, hp = 5 + (int) Math.round(3 * t), top = F + hp, w = a0 + 3;
        ground(L, a0, a0 + 2, F, hp + 3);
        L.solid(a0 + 2, F, 0, a0 + 2, top - 1, 0, Role.LINE, L.dir2D(1, 0));
        L.floor(w, w + 2, -1, 1, top, top + HEAD);
        L.underfoot(w, w + 2, -1, 1, top);
        L.wall(w, F - 2, -1, w, top - 2, 1);
        sides(L, a0, w + 2, F - 2, top + HEAD);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.keep(a0 + 2, F, -1, a0 + 2, top + 1, -1);
        L.keep(a0 + 2, F, 1, a0 + 2, top + 1, 1);
        L.keep(a0 + 2, top, 0, a0 + 2, top + 1, 0);
        L.keep(w, top, -1, w + 2, top + 1, 1);
        L.light(a0 + 1, -1, F + hp / 2);
        L.move("pipe", "grip", Family.GRIP, false, a0 + 1, 0, F, w, 0, top, 1, 0, 0, 2);
        L.catchAt(a0 + 1, 0, F);
        L.catchAt(w + 1, 0, top);
        L.a = w + 3;
        L.feet = top;
        return true;
    }

    /** A chain fall: a rope or chain hung down the middle of a drop of 8 to 12; jump to it and slide down. */
    static boolean chainFall(BeatLine L, double t) {
        int a0 = L.a, F = L.feet, dc = 8 + (int) Math.round(4 * t), bot = F - dc, d0 = a0 + 2, d1 = a0 + 4, roof = F + HEAD;
        ground(L, a0, a0 + 1, F, HEAD);
        L.floor(d0, d1, -1, 1, bot, roof);
        L.underfoot(d0, d1, -1, 1, bot);
        L.wall(a0 + 1, bot - 2, -1, a0 + 1, F - 2, 1);
        // hung from the roof, a block off the floor
        L.solid(a0 + 3, bot + 1, 0, a0 + 3, roof - 1, 0, Role.LINE, -1);
        L.wall(a0 + 3, roof, 0, a0 + 3, roof + 1, 0);
        int e = d1 + 1;
        ground(L, e, e + 1, bot, HEAD);
        sides(L, a0, e + 1, bot - 2, roof);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.keep(d0, bot, -1, d1, roof - 1, -1);
        L.keep(d0, bot, 1, d1, roof - 1, 1);
        L.keep(d0, bot, 0, d0, roof - 1, 0);
        L.keep(d1, bot, 0, d1, roof - 1, 0);
        L.light(a0 + 3, 1, bot + dc / 2);
        L.move("chainfall", "rope+slip", Family.GRIP, true, a0 + 1, 0, F, a0 + 3, 0, bot, 1, 0, 0, 2);
        L.catchAt(a0, 0, F);
        L.catchAt(d1, 0, bot);
        L.lesson(Family.GRIP, a0 + 1, 0, F, new double[][]{{a0 + 1, 0, F}, {a0 + 2.6, 0, F + 0.6}, {a0 + 2.6, 0, F - dc / 2.0}, {a0 + 2.6, 0, bot}}, SAY_ROPE);
        L.a = e + 2;
        L.feet = bot;
        return true;
    }

    /**
     * A swing between ropes: a rope down the middle of a deep drop over water, a second one in a bay off to one side over
     * the way on; jump to the first, swing across to the second, slide down. Where the second hangs is where the body's own
     * swing passes, slow and fast (MoveProof.swingAt).
     */
    static boolean swing(BeatLine L, int side, Rank rank) {
        int[] fit = SWING_FIT.computeIfAbsent(key(rank, side, 0), k -> searchSwing(side, rank));
        if (fit.length == 0) return false;
        swingOf(L, fit[0], fit[1], side, true);
        return true;
    }

    private static int[] searchSwing(int side, Rank rank) {
        for (int da = -1; da <= 1; da++) for (int dc = 2; dc <= 3; dc++) {
            BeatLine L = scratch();
            swingOf(L, da, dc, side, false);
            if (proves(L, rank)) return new int[]{da, dc};
        }
        return NONE;
    }

    /** The swing's cells: the second rope {@code da} along and {@code dc} across from the first. */
    static void swingOf(BeatLine L, int da, int dc, int side, boolean teach) {
        int a0 = L.a, F = L.feet, s = side, d = 9, bot = F - d, roof = F + HEAD + 1, r1 = a0 + 3, r2 = r1 + da, c2 = s * dc;
        int d0 = a0 + 2, d1 = a0 + 5, cb = s * (dc + 1);
        ground(L, a0, a0 + 1, F, HEAD + 1);
        // the drop: water under the first rope, the way on under the second, in its bay
        L.pit(d0, d1, -1, 1, bot - 4, 2, roof);
        for (int c = Math.min(s * 2, cb); c <= Math.max(s * 2, cb); c++) {
            if (Math.abs(c) <= 1) continue;
            L.floor(d0, d1, c, c, bot, roof);
            L.underfoot(d0, d1, c, c, bot);
        }
        L.wall(d0, bot - 6, -1, d1, bot - 5, 1);
        L.wall(a0 + 1, bot - 6, -1, a0 + 1, F - 2, 1);
        L.wall(a0, F - 2, -2 * s, a0 + 1, roof, -2 * s);
        L.wall(d0, bot - 6, -2 * s, d1, roof, -2 * s);
        L.wall(a0, F - 2, 2 * s, a0 + 1, roof, 2 * s);
        L.wall(d0, bot - 1, cb + s, d1 + 3, roof, cb + s);
        L.solid(r1, bot - 1, 0, r1, roof - 1, 0, Role.LINE, -1);
        L.solid(r2, bot + 1, c2, r2, roof - 1, c2, Role.LINE, -1);
        L.wall(r1, roof, 0, r1, roof + 1, 0);
        L.wall(r2, roof, c2, r2, roof + 1, c2);
        L.keep(d0, bot, -1, d1, roof - 1, 1);
        L.keep(d0, bot, Math.min(2 * s, cb), d1, roof - 1, Math.max(2 * s, cb));
        L.wall(a0 + 1, bot - 6, 2 * s, a0 + 1, roof, cb + s);
        L.light(r1, -s, F);
        int e = d1 + 1;
        // the pit's far end is rock, but for where the way on goes
        for (int c = -1; c <= 1; c++) if (Math.abs(c - c2) > 1) L.wall(e, bot - 6, c, e, roof, c);
        L.move("swing", "rope+swing:" + (-s) + "+slip", Family.GRIP, true, a0 + 1, 0, F, r2, c2, bot, 1, 0, 0, 2);
        L.catchAt(a0, 0, F);
        L.catchAt(d1, c2, bot);
        if (teach) L.lesson(Family.GRIP, a0 + 1, 0, F, new double[][]{{a0 + 1, 0, F}, {r1 - 0.4, 0, F}, {r2 - 0.4, c2, F + 0.3}, {r2 - 0.4, c2, bot}}, SAY_SWING);
        // the way on goes from the bay along the second rope's line: the frame moves over to it
        L.shift(c2);
        ground(L, e, e + 2, bot, HEAD);
        sides(L, e, e + 2, bot - 2, bot + HEAD);
        L.keep(e, bot, -1, e + 2, bot + 1, 1);
        L.catchAt(e + 1, 0, bot);
        L.a = e + 3;
        L.feet = bot;
    }

    // ---- flow and fall ----------------------------------------------------------------------------------------------

    /** A crumble run: four to seven cells of floor one block thick over a pit, that gives way a breath after it's stood on. */
    static boolean crumble(BeatLine L, double t, Random r) {
        int a0 = L.a, F = L.feet, n = 4 + (int) Math.round(3 * t), b0 = a0 + 2, b1 = a0 + 1 + n;
        boolean narrow = t >= 0.5 && r.nextBoolean();
        ground(L, a0, a0 + 1, F, HEAD);
        L.pit(b0, b1, -1, 1, F - 6, 2, F + HEAD);
        int c0 = narrow ? 0 : -1, c1 = narrow ? 0 : 1;
        L.solid(b0, F - 1, c0, b1, F - 1, c1, Role.CRUMBLE, 0);
        L.wall(b0, F - 7, -1, b1, F - 7, 1);
        int e = b1 + 1;
        ground(L, e, e + 2, F, HEAD);
        sides(L, a0, e + 2, F - 7, F + HEAD);
        L.keep(a0, F, -1, e + 2, F + 1, 1);
        L.trap(2, b0, F - 1, c0, b1, F - 1, c1, 0, 0);
        L.move("crumble", "walk:" + (n + 2), Family.FLOW, false, a0 + 1, 0, F, e + 1, 0, F, 1, 0, 0, 1);
        L.catchAt(a0, 0, F);
        L.catchAt(e + 1, 0, F);
        L.a = e + 3;
        return true;
    }

    /** A drop of four to six onto ground: land crouching or sprinting and roll. */
    static boolean dropRoll(BeatLine L, double t) {
        int a0 = L.a, F = L.feet, d = 4 + (int) Math.round(2 * t), bot = F - d;
        ground(L, a0, a0 + 1, F, HEAD);
        L.floor(a0 + 2, a0 + 5, -1, 1, bot, F + HEAD);
        L.underfoot(a0 + 2, a0 + 5, -1, 1, bot);
        L.wall(a0 + 1, bot - 2, -1, a0 + 1, F - 2, 1);
        sides(L, a0, a0 + 5, bot - 2, F + HEAD);
        L.keep(a0, F, -1, a0 + 1, F + 1, 1);
        L.keep(a0 + 2, bot, -1, a0 + 5, F + 1, 1);
        L.move("droproll", "fall:" + d, Family.FLOW, false, a0 + 1, 0, F, a0 + 2, 0, bot, 1, 0, 0, 2);
        L.catchAt(a0, 0, F);
        L.catchAt(a0 + 4, 0, bot);
        L.a = a0 + 6;
        L.feet = bot;
        return true;
    }

    // ---- side routes ------------------------------------------------------------------------------------------------

    /**
     * A side route, never on the way: a bay off one side of a short stretch, with a find for whoever takes it. A stride
     * wall: a niche high up a sheer wall (past a jump's catch, in Wall Stride's climb). A swing out: a ledge across a pit,
     * an anchor ring in the roof over it for the rope. A leap gap: a ledge across a pit too wide to jump, with no wall to
     * run (a double jump or a dash).
     */
    static boolean side(BeatLine L, Beat b, int side) {
        int a0 = L.a, F = L.feet, s = side, len = 5, mid = a0 + 2, roof = F + HEAD + 2;
        ground(L, a0, a0 + len - 1, F, HEAD + 2);
        L.wall(a0, F - 2, -2 * s, a0 + len - 1, roof, -2 * s);
        L.keep(a0, F, -1, a0 + len - 1, F + 1, 1);
        switch (b) {
            case STRIDE -> {
                // the bay: a cell out from the lane, its far wall sheer, a niche five up it (out of a jump's catch)
                L.floor(a0 + 1, a0 + 3, 2 * s, 2 * s, F, roof);
                L.underfoot(a0 + 1, a0 + 3, 2 * s, 2 * s, F);
                L.wall(a0, F - 2, 2 * s, a0, roof, 2 * s);
                L.wall(a0 + 4, F - 2, 2 * s, a0 + 4, roof, 2 * s);
                L.wall(a0, F - 2, 3 * s, mid - 1, roof, 3 * s);
                L.wall(mid + 1, F - 2, 3 * s, a0 + 4, roof, 3 * s);
                L.col(mid, 3 * s, 2 * (F + 5), F + 8, 0);
                L.solid(mid, F + 4, 3 * s, mid, F + 4, 3 * s, Role.LIP, 0);
                L.wall(mid, F - 2, 3 * s, mid, F + 3, 3 * s);
                L.wall(mid, F + 8, 3 * s, mid, roof, 3 * s);
                L.wall(mid, F + 4, 4 * s, mid, F + 8, 4 * s);
                L.keep(mid, F + 5, 3 * s, mid, F + 6, 3 * s);
                L.find(mid, 3 * s, F + 5);
            }
            case ANCHOR, LEAP -> {
                // the bay: a pit six across and the ledge beyond it; an anchor ring in the roof over its middle
                int gap = 6, c0 = 2 * s, c1 = s * (1 + gap), lc = s * (2 + gap), back = lc + 2 * s;
                L.pit(a0 + 1, a0 + 3, Math.min(c0, c1), Math.max(c0, c1), F - 7, 2, roof);
                L.floor(a0 + 1, a0 + 3, lc, lc + s, F, roof);
                L.underfoot(a0 + 1, a0 + 3, lc, lc + s, F);
                L.wall(a0, F - 8, Math.min(c0, back), a0, roof, Math.max(c0, back));
                L.wall(a0 + 4, F - 8, Math.min(c0, back), a0 + 4, roof, Math.max(c0, back));
                L.wall(a0 + 1, F - 2, back, a0 + 3, roof, back);
                L.wall(a0 + 1, F - 8, Math.min(c0, c1), a0 + 3, F - 8, Math.max(c0, c1));
                L.keep(a0 + 1, F, lc, a0 + 3, F + 1, lc + s);
                L.find(mid, lc + s, F);
                if (b == Beat.ANCHOR) L.anchor(mid, s * (2 + gap / 2), roof);
            }
            default -> {}
        }
        L.catchAt(mid, 0, F);
        L.a = a0 + len;
        return true;
    }

    // ---- the searches' scratch ----------------------------------------------------------------------------------------

    /** A line at the origin going +z, for the searches: plain stone, proved by itself. */
    static BeatLine scratch() {
        BeatLine L = new BeatLine("search", 0, 96, 0, 0, 1);
        pad(L, 3);
        return L;
    }

    /** Whether every move of a line holds on its own cells in plain stone, for a body of this rank. */
    static boolean proves(BeatLine L, Rank rank) {
        pad(L, 3);
        L.s.done();
        var view = L.s.view(Sculpt.PLAIN);
        for (Land.Move m : L.moves) if (MoveProof.prove(view, m, rank) != null) return false;
        return true;
    }
}
