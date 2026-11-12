package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.crawl.Sculpt.Role;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A line of beats being cut (SPEC §72.2): a {@link Sculpt} and a cursor in a frame of its own. {@code a} runs along the
 * line, {@code c} across it (+c is the right hand as you go), {@code y} is the world's. The lane is three wide (c -1 to
 * 1) between walls at c ±2. Each beat writes its cells from the cursor on and moves the cursor and the floor
 * ({@link #feet}) to where the next one starts; it records the moves it asks for, where a fall puts you back, where
 * monsters hold, where a family is first taught and its signs. The frame turns a quarter about a pad ({@link #turn}) or a
 * landing ({@link #landing}); a well turns at every corner. {@link #span} is the box written since it was last taken, so a
 * well can see that no flight runs into another.
 */
public final class BeatLine {
    public final Sculpt s;
    public final String leg;
    /** The frame: the world cell of (a 0, c 0) and the way along. */
    int ox, oz, dx, dz;
    /** Where the next beat starts: the first free cell along, and the floor there. */
    public int a, feet;
    /** The head room over an ordinary stretch. */
    public static final int HEAD = 5;
    public final int startX, startY, startZ;
    public final List<Land.Move> moves = new ArrayList<>();
    public final List<int[]> catches = new ArrayList<>();
    public final List<Land.Hold> holds = new ArrayList<>();
    public final List<Land.Lesson> lessons = new ArrayList<>();
    public final List<String> beats = new ArrayList<>();
    /** Traps the beats ask for (x0, y0, z0, x1, y1, z1, kind: 0 a press, 2 floor that gives way, period, phase). */
    public final List<int[]> traps = new ArrayList<>();
    /** Finds at the end of the side routes (x, y, z of the cache). */
    public final List<int[]> finds = new ArrayList<>();
    /** Anchor rings for the rope over a side route (x, y, z). */
    public final List<int[]> anchors = new ArrayList<>();
    /** The crossing onto each side route (a stride up a wall, a swing or a leap over a pit): never asked for, taken by the walk check. */
    public final List<Land.Leap> leaps = new ArrayList<>();
    public final java.util.Set<Land.Family> families = java.util.EnumSet.noneOf(Land.Family.class);
    /** The lowest a body may be in this line before the run puts it back (the lowest catch ledge, less three). */
    public int lowest = Integer.MAX_VALUE;
    public int turns;
    /** The lowest and highest floor any cell of it has (for the box it takes). */
    public int yLo = Integer.MAX_VALUE, yHi = Integer.MIN_VALUE;
    private int holdIndex;
    /** The box (x0, y0, z0, x1, y1, z1) of every cell written since {@link #span} was last called. */
    private final int[] span = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

    public BeatLine(String leg, int x, int feet, int z, int dx, int dz) {
        this.s = new Sculpt(leg);
        this.leg = leg;
        this.ox = x;
        this.oz = z;
        this.dx = dx;
        this.dz = dz;
        this.a = 0;
        this.feet = feet;
        startX = x;
        startY = feet;
        startZ = z;
    }

    private void touch(int a0, int y0, int c0, int a1, int y1, int c1) {
        int xa = x(a0, c0), za = z(a0, c0), xb = x(a1, c1), zb = z(a1, c1);
        span[0] = Math.min(span[0], Math.min(xa, xb));
        span[1] = Math.min(span[1], Math.min(y0, y1));
        span[2] = Math.min(span[2], Math.min(za, zb));
        span[3] = Math.max(span[3], Math.max(xa, xb));
        span[4] = Math.max(span[4], Math.max(y0, y1));
        span[5] = Math.max(span[5], Math.max(za, zb));
    }

    /** The box written since the last call (x0, y0, z0, x1, y1, z1), and a fresh one begun. */
    public int[] span() {
        int[] out = span.clone();
        span[0] = span[1] = span[2] = Integer.MAX_VALUE;
        span[3] = span[4] = span[5] = Integer.MIN_VALUE;
        return out;
    }

    public int x(int a, int c) {
        return ox + dx * a - dz * c;
    }

    public int z(int a, int c) {
        return oz + dz * a + dx * c;
    }

    /** The world way along, and the world way to the right. */
    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    /** The yaw (degrees) of a way in this frame: (1, 0) is along, (0, 1) to the right. */
    public float yaw(double da, double dc) {
        double wx = dx * da - dz * dc, wz = dz * da + dx * dc;
        return (float) (Mth.atan2(-wx, wz) * Mth.RAD_TO_DEG);
    }

    // ---- cells ------------------------------------------------------------------------------------------------------

    /** Floor at feet {@code f} (a whole block) over a box of the frame, head room to {@code roof}. */
    public void floor(int a0, int a1, int c0, int c1, int f, int roof) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) for (int c = Math.min(c0, c1); c <= Math.max(c0, c1); c++) {
            s.column(x(a, c), z(a, c), 2 * f, roof, 0);
        }
        touch(a0, f - 1, c0, a1, roof, c1);
        yLo = Math.min(yLo, f);
        yHi = Math.max(yHi, roof);
    }

    /** One column: feet twice their height (odd a half step), the roof, water on the floor. */
    public void col(int a, int c, int feet2, int roof, int wet) {
        s.column(x(a, c), z(a, c), feet2, roof, wet);
        touch(a, Math.floorDiv(feet2, 2) - 1, c, a, roof, c);
        yLo = Math.min(yLo, Math.floorDiv(feet2, 2));
        yHi = Math.max(yHi, roof);
    }

    /** A pit: its bed at {@code bed} with {@code wet} of water on it, open up to {@code roof}. */
    public void pit(int a0, int a1, int c0, int c1, int bed, int wet, int roof) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) for (int c = Math.min(c0, c1); c <= Math.max(c0, c1); c++) {
            s.column(x(a, c), z(a, c), 2 * bed, roof, wet);
        }
        touch(a0, bed - 1, c0, a1, roof, c1);
        yLo = Math.min(yLo, bed);
        yHi = Math.max(yHi, roof);
    }

    public void solid(int a0, int y0, int c0, int a1, int y1, int c1, Role role, int arg) {
        touch(a0, y0, c0, a1, y1, c1);
        s.solid(x(a0, c0), y0, z(a0, c0), x(a1, c1), y1, z(a1, c1), role, arg);
    }

    public void keep(int a0, int y0, int c0, int a1, int y1, int c1) {
        touch(a0, y0, c0, a1, y1, c1);
        s.keep(x(a0, c0), y0, z(a0, c0), x(a1, c1), y1, z(a1, c1));
    }

    public void wall(int a0, int y0, int c0, int a1, int y1, int c1) {
        touch(a0, y0, c0, a1, y1, c1);
        s.wall(x(a0, c0), y0, z(a0, c0), x(a1, c1), y1, z(a1, c1));
    }

    /** Air carved whatever the columns say (a pocket under a floor, an alcove over a sill). */
    public void airBox(int a0, int y0, int c0, int a1, int y1, int c1) {
        touch(a0, y0, c0, a1, y1, c1);
        s.air(x(a0, c0), y0, z(a0, c0), x(a1, c1), y1, z(a1, c1));
    }

    /** A trap over a box of the frame: {@code kind} 0 a press, 2 floor that gives way. */
    public void trap(int kind, int a0, int y0, int c0, int a1, int y1, int c1, int period, int phase) {
        int xa = x(a0, c0), za = z(a0, c0), xb = x(a1, c1), zb = z(a1, c1);
        traps.add(new int[]{Math.min(xa, xb), Math.min(y0, y1), Math.min(za, zb), Math.max(xa, xb), Math.max(y0, y1), Math.max(za, zb), kind, period, phase});
    }

    public void find(int a, int c, int y) {
        finds.add(new int[]{x(a, c), y, z(a, c)});
    }

    /** A side route's crossing, from where the feet stand (a0, c0, y0) to where they land (a1, c1, y1): which move it takes. */
    public void leap(String move, int a0, int c0, int y0, int a1, int c1, int y1) {
        leaps.add(new Land.Leap(move, x(a0, c0), y0, z(a0, c0), x(a1, c1), y1, z(a1, c1)));
    }

    /** An anchor ring set in the roof cell (a, c, y), for the rope. */
    public void anchor(int a, int c, int y) {
        anchors.add(new int[]{x(a, c), y, z(a, c)});
        solid(a, y, c, a, y, c, Role.ANCHOR, 0);
    }

    /** Moves the frame sideways by {@code dc} (the lane goes on from there). */
    public void shift(int dc) {
        ox = x(0, dc);
        oz = z(0, dc);
    }

    /** The world's 2D direction (Direction.get2DDataValue) of a way in the frame: (1, 0) along, (0, 1) to the right. */
    public int dir2D(int da, int dc) {
        int wx = dx * da - dz * dc, wz = dz * da + dx * dc;
        return wz > 0 ? 0 : wx < 0 ? 1 : wz < 0 ? 2 : 3;
    }

    /** The axis (0 x, 2 z) a beam lying across the lane has in the world. */
    public int across() {
        return dx != 0 ? 2 : 0;
    }

    public void light(int a, int c, int y) {
        s.lights.add(new int[]{x(a, c), y, z(a, c)});
    }

    /**
     * The frame of a stretch: walls at c ±2 (or the given sides) from under its lowest floor to its roof, and the stone
     * under its floors, so nothing another part does opens the side of a run or takes the floor from under a take-off.
     */
    public void frame(int a0, int a1, int yLo, int yHi, int cLeft, int cRight) {
        wall(a0, yLo, cLeft, a1, yHi, cLeft);
        wall(a0, yLo, cRight, a1, yHi, cRight);
    }

    /** The floor block under a stretch of floor kept solid. */
    public void underfoot(int a0, int a1, int c0, int c1, int f) {
        wall(a0, f - 2, c0, a1, f - 1, c1);
    }

    // ---- what a beat says about itself ------------------------------------------------------------------------------

    /** A move from (a0, c0, y0) to (a1, c1, y1), facing (da, dc) in the frame. */
    public void move(String beat, String move, Land.Family family, boolean needed, int a0, int c0, int y0, int a1, int c1, int y1, double da, double dc, int arg) {
        move(beat, move, family, needed, a0, c0, y0, a1, c1, y1, da, dc, arg, 0);
    }

    /** The same, and the move may end up to {@code over} cells on past its end along the way it faces. */
    public void move(String beat, String move, Land.Family family, boolean needed, int a0, int c0, int y0, int a1, int c1, int y1, double da, double dc, int arg, int over) {
        moves.add(new Land.Move(leg, beat, move, family, needed, x(a0, c0), y0, z(a0, c0), x(a1, c1), y1, z(a1, c1), yaw(da, dc), arg, over));
        if (needed) families.add(family);
    }

    /** Where a fall in this stretch puts you back: feet on this cell. */
    public void catchAt(int a, int c, int y) {
        catches.add(new int[]{x(a, c), y, z(a, c)});
        lowest = Math.min(lowest, y - 3);
    }

    public void hold(Land.HoldKind kind, int a, int c, int y, int radius, int count) {
        holds.add(new Land.Hold(leg, kind, holdIndex++, x(a, c), y, z(a, c), radius, count));
    }

    /** Where a family is taught the first time, the way its gold figure shows it (frame points, feet), and the plain words. */
    public void lesson(Land.Family family, int a, int c, int y, double[][] path, String words) {
        List<double[]> p = new ArrayList<>();
        for (double[] q : path) p.add(new double[]{ox + dx * q[0] - dz * q[1] + 0.5, q[2], oz + dz * q[0] + dx * q[1] + 0.5});
        lessons.add(new Land.Lesson(family, x(a, c), y, z(a, c), p, words));
    }

    /** Turns the frame a quarter (right: +1, left: -1) about a 3x3 pad at the cursor; the cursor is past the pad. */
    public void turn(int side, int roof) {
        floor(a, a + 2, -1, 1, feet, roof);
        underfoot(a, a + 2, -1, 1, feet);
        int cx = x(a + 1, 0), cz = z(a + 1, 0);
        int ndx = side > 0 ? -dz : dz, ndz = side > 0 ? dx : -dx;
        // the walls of the pad's far side and the side the turn leaves from, now the new frame's
        dx = ndx;
        dz = ndz;
        ox = cx;
        oz = cz;
        a = 2;
        wall(-1, feet - 2, side > 0 ? -2 : 2, 1, roof, side > 0 ? -2 : 2);
        wall(-2, feet - 2, -1, -2, roof, 1);
        turns++;
    }

    /**
     * Turns the frame a quarter (right: +1, left: -1) about a landing five by five at the cursor, walled on the two sides
     * nobody comes or goes by; the cursor is past it. A landing is somewhere to stand and fight (a hold), and a well's corner.
     */
    public void landing(int side, int roof) {
        floor(a, a + 4, -2, 2, feet, roof);
        underfoot(a, a + 4, -2, 2, feet);
        int cx = x(a + 2, 0), cz = z(a + 2, 0);
        int ndx = side > 0 ? -dz : dz, ndz = side > 0 ? dx : -dx;
        dx = ndx;
        dz = ndz;
        ox = cx;
        oz = cz;
        a = 3;
        // the far side (as you came) and the side you didn't turn to
        wall(-3, feet - 2, side > 0 ? -3 : 3, 3, roof, side > 0 ? -3 : 3);
        wall(-3, feet - 2, -3, -3, roof, 3);
        // the corner between the way in and the way out
        wall(3, feet - 2, side > 0 ? 3 : -3, 3, roof, side > 0 ? 2 : -2);
        turns++;
    }

    /** The cell where the line ends: the last one before the cursor, on its middle. */
    public int endX() {
        return x(a - 1, 0);
    }

    public int endZ() {
        return z(a - 1, 0);
    }

    /** A cell the feet stand on near the middle of the line (its middle catch): where its leg is, for the route and the tours. */
    public int[] middle() {
        if (catches.isEmpty()) return new int[]{x(a / 2, 0), feet, z(a / 2, 0)};
        return catches.get(catches.size() / 2).clone();
    }
}
