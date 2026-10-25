package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Palette;
import dev.awaken.gates.content.Theme;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gauntlet (SPEC §17.5 "Gauntlet"; Clockwork Foundry, Frost Monastery; content version 5): the room is a pit crossed
 * by a course from the door you came in by to the door that leads on, built from what the body can do. Gaps to jump,
 * ledges that crack under you and fall, presses that slam down out of the ceiling on a beat, a wall to run along over
 * the drop. Rings of light stand over the course: pass one and the pit throws you back there instead of to the start.
 * The clock runs from the first step off the landing to the far one, and the time wins a medal against the course's
 * own par.
 *
 * <p>Pure geometry and timing shared by the painter, the rig director and the solvability check, like the Collapse
 * Run's: the course is the two door axes (4 wide), rows are steps along it from the entry (breadth-first). Everything
 * is a function of the room and its rank.
 */
public final class RigGauntlet {
    private RigGauntlet() {}

    /** Cell (and row) kinds. RING is a floor row with a checkpoint ring over it; WALL a column of the run's wall. */
    static final byte PIT = 0, LANDING = 1, FLOOR = 2, GAP = 3, LEDGE = 4, PRESS = 5, RUN = 6, WALL = 7, RING = 8;
    /** Rows at each end that never change: where the clock starts and stops. */
    static final int LANDING_ROWS = 2;
    /** The pit's floor block sits this far below the room's floor (as the Collapse Run's). */
    public static final int PIT_DY = -4;
    /** A press: rows 0..PRESS_H-1 over the floor, down out of the ceiling in PRESS_TICKS; its floor glows PRESS_WARN before. */
    public static final int PRESS_H = 3, PRESS_TICKS = 4, PRESS_WARN = 12;
    /** A ledge cracks the moment you land on it, falls LEDGE_FALL later and knits back LEDGE_BACK after that. */
    public static final int LEDGE_FALL = 14, LEDGE_BACK = 60;
    /** The run's wall stands this many rows over the floor (and down into the pit). */
    public static final int WALL_TOP = 4;
    /** The solvability check's runner (a sprint is 0.28 a tick), its wall-run, and the margin it keeps from a press. */
    static final double RUNNER = 0.26, WALLRUN = 0.32;
    static final int MARGIN = 3;
    /** The longest gap a sprint jump clears, the longest wall-run the check allows (E-rank runs last 35 ticks). */
    static final int MAX_GAP = 3, MAX_RUN = 7;
    /** The fewest rows a course has between its landings. */
    static final int MIN_COURSE = 14;

    /**
     * One room's course. Arrays are over the interior (index {@code (x - x0) + (z - z0) * w}); {@code rowKind} per row.
     * Groups 0..ledges-1 are the ledges in order, then the presses; track 0 is the presses' loop (when there are any),
     * then a track per ledge. {@code rings}: the rows with a checkpoint ring, in order. {@code par}: the check's time from
     * the first step off the landing to the far one, in ticks.
     */
    public record Course(int x0, int z0, int w, int d, byte[] kind, short[] row, BlockState[] tile, int rows, byte[] rowKind,
                         int[] rings, int period, int down, BlockState pit, BlockState ram, BlockState wall, int ledges,
                         int presses, int runs, int gaps, int par, int slack, int edge) {

        /** A trial's course: its chasm (after row {@link #edge}) is the leap, crossed only by a second jump or a dash. */
        public boolean trial() {
            return edge >= 0;
        }

        /** The trial's chasm: how many rows of open pit follow the edge (0 for any other course). */
        public int chasm() {
            int n = 0;
            for (int k = edge + 1; edge >= 0 && k < rows && rowKind[k] == GAP; k++) n++;
            return n;
        }

        int at(int x, int z) {
            int ix = x - x0, iz = z - z0;
            return ix < 0 || iz < 0 || ix >= w || iz >= d ? -1 : ix + iz * w;
        }

        public byte kindAt(int x, int z) {
            int i = at(x, z);
            return i < 0 ? PIT : kind[i];
        }

        /** Open to the pit: no floor here (the pit, a gap, the drop under a wall-run). */
        public boolean open(int x, int z) {
            byte k = kindAt(x, z);
            return k == PIT || k == GAP || k == RUN;
        }

        public boolean wall(int x, int z) {
            return kindAt(x, z) == WALL;
        }

        /** Over a press: its ceiling is the press, parked. */
        public boolean press(int x, int z) {
            return kindAt(x, z) == PRESS;
        }

        /** The block painted under the walking level of a course column (null: open). */
        public BlockState tileAt(int x, int z) {
            int i = at(x, z);
            return i < 0 || tile[i] == null ? null : tile[i];
        }

        public int rowAt(int x, int z) {
            int i = at(x, z);
            return i < 0 || kind[i] == PIT || kind[i] == WALL ? -1 : row[i];
        }

        /** The first row of the course proper (the clock starts here) and the first row of the far landing. */
        public int first() {
            return LANDING_ROWS;
        }

        public int last() {
            return rows - LANDING_ROWS;
        }

        /**
         * The middle of row k (floor-local block-edge coordinates), always on the row: a straight row's middle, a corner
         * row's cell nearest its middle.
         */
        public double[] rowCentre(int k) {
            double sx = 0, sz = 0;
            int n = 0;
            for (int i = 0; i < kind.length; i++) {
                if (row[i] != k || kind[i] == PIT || kind[i] == WALL) continue;
                sx += x0 + i % w + 0.5;
                sz += z0 + i / w + 0.5;
                n++;
            }
            if (n == 0) return new double[]{x0 + w / 2.0, z0 + d / 2.0};
            double mx = sx / n, mz = sz / n;
            if (rowAt((int) Math.floor(mx), (int) Math.floor(mz)) == k) return new double[]{mx, mz};
            double best = Double.MAX_VALUE, bx = mx, bz = mz;
            for (int i = 0; i < kind.length; i++) {
                if (row[i] != k || kind[i] == PIT || kind[i] == WALL) continue;
                double cx = x0 + i % w + 0.5, cz = z0 + i / w + 0.5, dd = (cx - mx) * (cx - mx) + (cz - mz) * (cz - mz);
                if (dd < best) {
                    best = dd;
                    bx = cx;
                    bz = cz;
                }
            }
            return new double[]{bx, bz};
        }

        /** Where a ring sends you back to: its row's middle, and a row on (which way you face). */
        public double[] ringAt(int ring) {
            int k = ring < 0 ? 0 : rings[ring];
            double[] c = rowCentre(k), on = rowCentre(Math.min(rows - 1, k + 2));
            return new double[]{c[0], c[1], on[0], on[1]};
        }

        /** The ledge (0..ledges-1) or press (0..presses-1) a row belongs to, or -1: counted along the rows. */
        public int segmentOf(int k) {
            byte kk = rowKind[k];
            if (kk != LEDGE && kk != PRESS) return -1;
            int n = -1;
            for (int i = 0; i <= k; i++) if (rowKind[i] == kk && (i == 0 || rowKind[i - 1] != kk)) n++;
            return n;
        }

        /** Press j's beat in the loop: when it comes down (it stays down {@link #down}). Alternating, half a period apart. */
        public int pressAt(int j) {
            return Math.floorMod(j * (period / 2) + period - down, period);
        }
    }

    // ---- building -----------------------------------------------------------------------------------------------------

    /** Whether the room can hold a course: exactly the two doors of the critical path, both at its floor. */
    static boolean fits(Room r) {
        if (r.pathIn == null || r.pathOut == null || r.doors.size() != 2) return false;
        for (Room.Door d : r.doors) if (d.y != r.dy) return false;
        return true;
    }

    /**
     * Builds the room's course, trying gentler variants until the check passes (the last has only gaps and ledges and a
     * long press beat). Null if the room can't hold one (too short for two obstacles).
     */
    static RigRoom build(Room r, Theme theme, Palette p, Kit k, int rank) {
        if (!fits(r)) return null;
        for (int variant = 0; variant < 4; variant++) {
            Course c = layout(r, theme, p, k, rank, variant);
            if (c == null) return null;
            int[] res = solve(c);
            if (res[0] >= MARGIN) {
                Course done = new Course(c.x0, c.z0, c.w, c.d, c.kind, c.row, c.tile, c.rows, c.rowKind, c.rings, c.period, c.down,
                        c.pit, c.ram, c.wall, c.ledges, c.presses, c.runs, c.gaps, res[1], res[0], -1);
                return room(r, done);
            }
        }
        return null;
    }

    /** The course's lanes: its cells (FLOOR, else 0), their rows from the entry, the rows to the far landing, how each lies. */
    private record Lanes(int x0, int z0, int w, int d, byte[] kind, short[] row, int rows, int[] across) {}

    /** The two door axes, 4 wide, rows breadth-first from the entry to the exit's doorway (as the Collapse Run's). */
    private static Lanes lanes(Room r) {
        int x0 = r.x0(), z0 = r.z0(), w = r.x1() - x0 + 1, d = r.z1() - z0 + 1;
        byte[] kind = new byte[w * d];
        short[] row = new short[w * d];
        Arrays.fill(row, (short) -1);
        for (int iz = 0; iz < d; iz++) {
            for (int ix = 0; ix < w; ix++) {
                int x = x0 + ix, z = z0 + iz;
                if (RoomGeometry.onAxisOf(r, r.pathIn, x, z, 1) || RoomGeometry.onAxisOf(r, r.pathOut, x, z, 1)) kind[ix + iz * w] = FLOOR;
            }
        }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == 0) continue;
            int x = x0 + i % w, z = z0 + i / w;
            if (RoomGeometry.depth(r.pathIn, x, z) == RoomGeometry.INSET && inDoorSlot(r.pathIn, x, z)) {
                row[i] = 0;
                q.add(i);
            }
        }
        if (q.isEmpty()) return null;
        int rows = 0;
        while (!q.isEmpty()) {
            int i = q.poll();
            rows = Math.max(rows, row[i] + 1);
            int ix = i % w, iz = i / w;
            int[][] n = {{ix + 1, iz}, {ix - 1, iz}, {ix, iz + 1}, {ix, iz - 1}};
            for (int[] c : n) {
                if (c[0] < 0 || c[1] < 0 || c[0] >= w || c[1] >= d) continue;
                int j = c[0] + c[1] * w;
                if (kind[j] != 0 && row[j] < 0) {
                    row[j] = (short) (row[i] + 1);
                    q.add(j);
                }
            }
        }
        int exitRow = rows - 1;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == 0) continue;
            int x = x0 + i % w, z = z0 + i / w;
            if (RoomGeometry.depth(r.pathOut, x, z) == RoomGeometry.INSET && inDoorSlot(r.pathOut, x, z)) exitRow = Math.min(exitRow, row[i]);
        }
        rows = exitRow + 1;
        // which way each row runs: all its cells on one line across (a straight stretch), or a corner
        int[] across = new int[rows];
        for (int kk = 0; kk < rows; kk++) across[kk] = straight(kind, row, w, kk);
        return new Lanes(x0, z0, w, d, kind, row, rows, across);
    }

    private static Course layout(Room r, Theme theme, Palette p, Kit k, int rank, int variant) {
        Lanes ln = lanes(r);
        if (ln == null) return null;
        int rows = ln.rows;
        // a course worth a clock: at least 14 rows between the landings (a single slot's room is too short)
        if (rows - 2 * LANDING_ROWS < MIN_COURSE) return null;
        long seed = r.seed;
        boolean tall = r.height >= PRESS_H + 3;
        // what this rank asks, gentler with each variant
        int gap = rank >= 3 && variant == 0 ? 3 : 2;
        int ledgesEach = rank >= 3 && variant == 0 ? 3 : 2;
        int pressesEach = variant >= 3 || !tall ? 0 : rank >= 3 && variant == 0 ? 3 : rank >= 2 ? 2 : 1;
        int runLen = rank >= 3 && variant == 0 ? 6 : 5;
        boolean runs = variant < 2;
        int period = variant == 0 ? (rank >= 3 ? 40 : 48) : 56, down = rank >= 3 && variant == 0 ? 18 : 16;
        // the obstacles in an order of the room's own, round again in a long room; a floor row before the first and after
        // each (two in a long room), a ring in place of it after every second one (not just before the end)
        byte[] menu = {GAP, LEDGE, PRESS, RUN};
        for (int i = menu.length - 1; i > 0; i--) {
            int j = Hash.range(seed, 0x6A0, i, i + 1);
            byte t = menu[i];
            menu[i] = menu[j];
            menu[j] = t;
        }
        int pad = rows - 2 * LANDING_ROWS >= 20 ? 2 : 1;
        // the room's own order first; another turn of it if that fits fewer obstacles in (a short room)
        byte[] best = null;
        int bestPlaced = -1;
        for (int turn = 0; turn < menu.length; turn++) {
            byte[] trial = new byte[rows];
            int n = plan(r, ln.kind, ln.row, ln.w, ln.d, ln.x0, ln.z0, ln.across, seed, rotate(menu, turn), pad, trial, rows, gap, ledgesEach,
                    pressesEach, runs ? runLen : 0, tall);
            if (n > bestPlaced) {
                bestPlaced = n;
                best = trial;
            }
            if (n >= 3) break;
        }
        if (bestPlaced < 2) return null;
        return course(ln, best, seed, theme, p, k, period, down, -1);
    }

    /**
     * The course from its rows' obstacles ({@code chosen}, 0 for floor; the landings added here): every cell takes its
     * row's kind, the wall stands beside each wall-run's rows (and one either side), the tiles are chosen.
     */
    private static Course course(Lanes ln, byte[] chosen, long seed, Theme theme, Palette p, Kit k, int period, int down, int edge) {
        int rows = ln.rows, x0 = ln.x0, z0 = ln.z0, w = ln.w, d = ln.d;
        byte[] kind = ln.kind;
        short[] row = ln.row;
        byte[] rowKind = new byte[rows];
        Arrays.fill(rowKind, FLOOR);
        for (int i = 0; i < LANDING_ROWS; i++) {
            rowKind[i] = LANDING;
            rowKind[rows - 1 - i] = LANDING;
        }
        List<Integer> rings = new ArrayList<>();
        int ledgeCount = 0, pressCount = 0, runCount = 0, gapCount = 0;
        for (int i = 0; i < rows; i++) {
            if (chosen[i] == 0) continue;
            rowKind[i] = chosen[i];
            if (chosen[i] == RING) rings.add(i);
            boolean head = i == 0 || chosen[i - 1] != chosen[i];
            if (!head) continue;
            switch (chosen[i]) {
                case LEDGE -> ledgeCount++;
                case PRESS -> pressCount++;
                case RUN -> runCount++;
                case GAP -> {
                    // a ledge's gap (1 row, a ledge after it) belongs to the ledges
                    if (i + 1 >= rows || chosen[i + 1] != LEDGE) gapCount++;
                }
                default -> {}
            }
        }
        BlockState[] tile = new BlockState[w * d];
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == 0) continue;
            if (row[i] < 0 || row[i] >= rows) {
                // past the exit landing (an L's corner beyond the door): landing too
                kind[i] = LANDING;
                row[i] = (short) (rows - 1);
            } else kind[i] = rowKind[row[i]];
        }
        for (int a = 0; a < rows; a++) {
            if (rowKind[a] != RUN || (a > 0 && rowKind[a - 1] == RUN)) continue;
            int b = a;
            while (b + 1 < rows && rowKind[b + 1] == RUN) b++;
            markWall(kind, row, w, d, a - 1, b + 1, side(seed, a));
        }
        for (int i = 0; i < kind.length; i++) {
            int x = x0 + i % w, z = z0 + i / w;
            tile[i] = switch (kind[i]) {
                case LANDING, RING -> p.floorAlt();
                case FLOOR -> ((x + z) & 1) == 0 ? p.floor() : k.band();
                case LEDGE -> k.trim();
                case PRESS -> ((x + z) & 1) == 0 ? p.floorAlt() : k.band();
                default -> null;
            };
        }
        int[] ringRows = rings.stream().mapToInt(Integer::intValue).toArray();
        return new Course(x0, z0, w, d, kind, row, tile, rows, rowKind, ringRows, period, down, pitFloor(theme, p), ram(theme),
                GatesRegistry.RUN_WALL.get().defaultBlockState(), ledgeCount, pressCount, runCount, gapCount, 0, 0, edge);
    }

    // ---- the Leaper's Gauntlet --------------------------------------------------------------------------------------

    /** The trial's chasm (rows of open pit after the edge): wider than any sprint jump, inside a leap's reach. */
    public static final int LEAP = 5;
    /** The check's leap: up to this wide, crossed at an air dash's pace. */
    static final int MAX_LEAP = 7;
    static final double LEAP_SPEED = 0.6;

    /**
     * The Leaper's Gauntlet's course (SPEC §17.10), laid by hand in its straight hall: a gap, two ledges, a ring, two
     * presses, a wall-run, a ring, a run-up of four rows to the edge and the chasm ({@link #LEAP} rows) only a second
     * jump or a dash crosses, then on to the far landing. Gentle beats (it teaches): a 56-tick loop, presses down 16.
     */
    static RigRoom trial(Room r, Theme theme, Palette p, Kit k) {
        Lanes ln = lanes(r);
        if (ln == null) return null;
        boolean tall = r.height >= PRESS_H + 3;
        byte[] seq = {0, 0, GAP, GAP, 0, GAP, LEDGE, LEDGE, GAP, LEDGE, LEDGE, RING, 0,
                tall ? PRESS : 0, tall ? PRESS : 0, 0, 0, tall ? PRESS : 0, tall ? PRESS : 0, 0, 0,
                RUN, RUN, RUN, RUN, RUN, RING, 0, 0, 0, 0};
        int first = LANDING_ROWS, edgeRow = first + seq.length - 1, after = edgeRow + 1 + LEAP;
        if (after + 1 + LANDING_ROWS > ln.rows) return null;
        byte[] chosen = new byte[ln.rows];
        System.arraycopy(seq, 0, chosen, first, seq.length);
        for (int i = 0; i < LEAP; i++) chosen[edgeRow + 1 + i] = GAP;
        // the wall-run's wall on whichever side it fits
        int runAt = first + 21;
        long seed = r.seed;
        if (!wallFits(r, ln.kind, ln.row, ln.w, ln.d, ln.x0, ln.z0, runAt - 1, runAt + 5, side(seed, runAt))) {
            for (int i = 0; i < 5; i++) chosen[runAt + i] = i < 2 ? GAP : 0;
        }
        Course c = course(ln, chosen, seed, theme, p, k, 56, 16, edgeRow);
        int[] res = solve(c);
        Course done = new Course(c.x0, c.z0, c.w, c.d, c.kind, c.row, c.tile, c.rows, c.rowKind, c.rings, c.period, c.down,
                c.pit, c.ram, c.wall, c.ledges, c.presses, c.runs, c.gaps, res[1], res[0], edgeRow);
        return res[0] < 0 ? null : room(r, done);
    }

    private static byte[] rotate(byte[] menu, int turn) {
        byte[] out = new byte[menu.length];
        for (int i = 0; i < menu.length; i++) out[i] = menu[(i + turn) % menu.length];
        return out;
    }

    /**
     * Lays the obstacles along the course in {@code menu}'s order (round again in a long room) into {@code out} (per
     * row, 0 for floor): each at its full size, or a smaller one where that won't fit (one ledge, one press); a floor
     * row before the first and after each ({@code pad}), a ring in place of it after every second one over a straight
     * row. Returns how many it placed.
     */
    private static int plan(Room r, byte[] kind, short[] row, int w, int d, int x0, int z0, int[] across, long seed, byte[] menu, int pad,
                            byte[] out, int rows, int gap, int ledgesEach, int pressesEach, int runLen, boolean tall) {
        int pos = LANDING_ROWS + pad, end = rows - LANDING_ROWS, placed = 0;
        for (int round = 0; round < 3; round++) {
            for (byte seg : menu) {
                int count = switch (seg) {
                    case LEDGE -> ledgesEach;
                    case PRESS -> tall ? pressesEach : 0;
                    default -> 1;
                };
                int len = 0;
                for (; count > 0; count--) {
                    len = switch (seg) {
                        case GAP -> gap;
                        case LEDGE -> 3 * count;
                        case PRESS -> 4 * count - 2;
                        case RUN -> runLen;
                        default -> 0;
                    };
                    if (len > 0 && pos + len + 1 <= end && fitsAt(r, kind, row, w, d, x0, z0, across, seed, seg, pos, len)) break;
                    len = 0;
                }
                if (len == 0) continue;
                switch (seg) {
                    case GAP, RUN -> {
                        for (int i = 0; i < len; i++) out[pos + i] = seg;
                    }
                    case LEDGE -> {
                        // a gap, a ledge (2 rows), a gap, a ledge...
                        for (int l = 0; l < count; l++) {
                            out[pos + 3 * l] = GAP;
                            out[pos + 3 * l + 1] = LEDGE;
                            out[pos + 3 * l + 2] = LEDGE;
                        }
                    }
                    case PRESS -> {
                        for (int l = 0; l < count; l++) {
                            out[pos + 4 * l] = PRESS;
                            out[pos + 4 * l + 1] = PRESS;
                        }
                    }
                    default -> {}
                }
                pos += len;
                placed++;
                // a ring stands over a straight row (it spans the course)
                if (placed % 2 == 0 && pos + 8 <= end && across[pos] != 0) out[pos] = RING;
                pos += pad;
            }
        }
        return placed;
    }

    /** Presses and wall-runs need a straight stretch with a row of it either side; a wall-run room for its wall. */
    private static boolean fitsAt(Room r, byte[] kind, short[] row, int w, int d, int x0, int z0, int[] across, long seed, byte seg, int pos, int len) {
        if ((seg == PRESS || seg == RUN) && !straightRun(across, pos - 1, pos + len)) return false;
        return seg != RUN || wallFits(r, kind, row, w, d, x0, z0, pos - 1, pos + len, side(seed, pos));
    }

    /** Which side of the course the run's wall stands on (+1 or -1 across it), by the room's seed. */
    private static int side(long seed, int row) {
        return Hash.range(seed, 0x6A1, row, 2) == 0 ? -1 : 1;
    }

    private static boolean inDoorSlot(Room.Door d, int x, int z) {
        return x >= d.gx * 16 && x < d.gx * 16 + 16 && z >= d.gz * 16 && z < d.gz * 16 + 16;
    }

    /**
     * How row k lies: 1 when all its cells share an x (it runs across z: you travel along x), 2 when they share a z
     * (you travel along z), 0 for a corner.
     */
    private static int straight(byte[] kind, short[] row, int w, int k) {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        boolean sameX = true, sameZ = true;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == 0 || row[i] != k) continue;
            int ix = i % w, iz = i / w;
            if (x == Integer.MIN_VALUE) {
                x = ix;
                z = iz;
            }
            if (ix != x) sameX = false;
            if (iz != z) sameZ = false;
        }
        if (x == Integer.MIN_VALUE) return 0;
        return sameX ? 1 : sameZ ? 2 : 0;
    }

    private static boolean straightRun(int[] across, int a, int b) {
        if (a < 0 || b >= across.length) return false;
        for (int k = a; k <= b; k++) if (across[k] == 0 || across[k] != across[a]) return false;
        return true;
    }

    /** The wall's cells for rows a..b on one side: the cell just outside the course beside each row's outermost cell. */
    private static List<Integer> wallCells(byte[] kind, short[] row, int w, int d, int a, int b, int side) {
        List<Integer> out = new ArrayList<>();
        for (int k = a; k <= b; k++) {
            int best = -1, bestV = side > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            // a row that shares a z lies across x: the wall is beside its end cells in x
            boolean alongX = straight(kind, row, w, k) == 2;
            for (int i = 0; i < kind.length; i++) {
                if (kind[i] == 0 || kind[i] == WALL || row[i] != k) continue;
                int ix = i % w, iz = i / w;
                int v = alongX ? ix : iz;
                if (side > 0 ? v > bestV : v < bestV) {
                    bestV = v;
                    best = i;
                }
            }
            if (best < 0) return null;
            int ix = best % w + (alongX ? side : 0), iz = best / w + (alongX ? 0 : side);
            if (ix < 0 || iz < 0 || ix >= w || iz >= d) return null;
            out.add(ix + iz * w);
        }
        return out;
    }

    /** The wall can stand: its cells are in the room's open interior, off the course and off every door's axis. */
    private static boolean wallFits(Room r, byte[] kind, short[] row, int w, int d, int x0, int z0, int a, int b, int side) {
        List<Integer> cells = wallCells(kind, row, w, d, a, b, side);
        if (cells == null) return false;
        for (int i : cells) {
            int x = x0 + i % w, z = z0 + i / w;
            if (kind[i] != 0 || !RoomGeometry.inside(r, x, z) || RoomGeometry.onDoorAxis(r, x, z, 1)) return false;
        }
        return true;
    }

    private static void markWall(byte[] kind, short[] row, int w, int d, int a, int b, int side) {
        List<Integer> cells = wallCells(kind, row, w, d, a, b, side);
        if (cells == null) return;
        for (int i : cells) kind[i] = WALL;
    }

    /** What lies at the bottom of the drop: the Foundry's slag glow, the Monastery's sea of cloud. */
    static BlockState pitFloor(Theme t, Palette p) {
        return switch (t) {
            case CLOCKWORK_FOUNDRY -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case FROST_MONASTERY -> GatesRegistry.CLOUD.get().defaultBlockState();
            default -> p.fill();
        };
    }

    /** The press: riveted iron in the Foundry, a ram of rime-hard ice in the Monastery. */
    static BlockState ram(Theme t) {
        return (t == Theme.FROST_MONASTERY ? GatesRegistry.FROST_RAM : GatesRegistry.PRESS).get().defaultBlockState();
    }

    // ---- groups, tracks, triggers ---------------------------------------------------------------------------------

    private static RigRoom room(Room r, Course c) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<List<RigRoom.Cell>> ledges = new ArrayList<>(), presses = new ArrayList<>();
        for (int i = 0; i < c.ledges; i++) ledges.add(new ArrayList<>());
        for (int i = 0; i < c.presses; i++) presses.add(new ArrayList<>());
        for (int i = 0; i < c.kind.length; i++) {
            int x = c.x0 + i % c.w, z = c.z0 + i / c.w;
            if (c.kind[i] == LEDGE) ledges.get(c.segmentOf(c.row[i])).add(new RigRoom.Cell(x, -1, z, c.tile[i], air));
            if (c.kind[i] == PRESS) {
                List<RigRoom.Cell> g = presses.get(c.segmentOf(c.row[i]));
                for (int dy = 0; dy < PRESS_H; dy++) g.add(new RigRoom.Cell(x, dy, z, air, c.ram));
            }
        }
        List<RigRoom.Group> groups = new ArrayList<>();
        for (List<RigRoom.Cell> l : ledges) groups.add(new RigRoom.Group(groups.size(), List.copyOf(l), RigRoom.Anim.FALL, RigRoom.Anim.RISE));
        for (List<RigRoom.Cell> l : presses) groups.add(new RigRoom.Group(groups.size(), List.copyOf(l), RigRoom.Anim.PRESS, RigRoom.Anim.LIFT));
        List<RigRoom.Track> tracks = new ArrayList<>();
        List<RigRoom.Trigger> triggers = new ArrayList<>();
        if (c.presses > 0) {
            // the presses' loop: each comes down on its beat and stays down `down` ticks; it starts with the clock
            List<RigRoom.Beat> beats = new ArrayList<>();
            for (int j = 0; j < c.presses; j++) {
                int g = c.ledges + j, at = c.pressAt(j);
                beats.add(new RigRoom.Beat(Math.floorMod(at - PRESS_WARN, c.period), RigRoom.Cue.WARN, g));
                beats.add(new RigRoom.Beat(at, RigRoom.Cue.APPLY, g));
                beats.add(new RigRoom.Beat(Math.floorMod(at + c.down, c.period), RigRoom.Cue.RESTORE, g));
            }
            beats.sort((a, b) -> a.at() != b.at() ? Integer.compare(a.at(), b.at()) : Integer.compare(a.cue().ordinal(), b.cue().ordinal()));
            tracks.add(new RigRoom.Track(0, c.period, true, RigRoom.Empty.RESET, List.copyOf(beats)));
            triggers.add(new RigRoom.Trigger(RigRoom.On.ZONE, rowBox(c, c.first(), c.first()), 0, false, 0));
        }
        int ledge = 0;
        for (int k = 0; k < c.rows; k++) {
            if (c.rowKind[k] != LEDGE || (k > 0 && c.rowKind[k - 1] == LEDGE)) continue;
            int id = tracks.size();
            tracks.add(new RigRoom.Track(id, LEDGE_FALL + LEDGE_BACK + 1, false, RigRoom.Empty.RESET, List.of(
                    new RigRoom.Beat(0, RigRoom.Cue.WARN, ledge),
                    new RigRoom.Beat(LEDGE_FALL, RigRoom.Cue.APPLY, ledge),
                    new RigRoom.Beat(LEDGE_FALL + LEDGE_BACK, RigRoom.Cue.RESTORE, ledge))));
            triggers.add(new RigRoom.Trigger(RigRoom.On.ZONE, rowBox(c, k, k + 1), id, false, 0));
            ledge++;
        }
        return new RigRoom(RigRoom.Kind.GAUNTLET, groups, tracks, triggers, null, null, null, c);
    }

    /** Rows a..b as a box a player stands in (feet 0 to 2 over the floor). */
    static RigRoom.Box rowBox(Course c, int a, int b) {
        int ax = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (int i = 0; i < c.kind.length; i++) {
            if (c.row[i] < a || c.row[i] > b || c.kind[i] == PIT || c.kind[i] == WALL) continue;
            int x = c.x0 + i % c.w, z = c.z0 + i / c.w;
            ax = Math.min(ax, x);
            az = Math.min(az, z);
            bx = Math.max(bx, x);
            bz = Math.max(bz, z);
        }
        return new RigRoom.Box(ax, 0, az, bx, 2, bz);
    }

    // ---- solvability ------------------------------------------------------------------------------------------------

    /**
     * Runs the check's runner from the first row off the landing (the clock and the presses start together): a row
     * every 1/{@link #RUNNER} ticks, gaps of up to {@link #MAX_GAP} jumped, a wall-run of up to {@link #MAX_RUN} at
     * {@link #WALLRUN}, off every ledge before it falls, through every press while it is up ({@link #MARGIN} clear
     * either side), waiting on the row before for its window. Returns {slack, par}: the smallest margin kept (negative:
     * no way through) and the time to the far landing.
     */
    public static int[] solve(Course c) {
        double per = 1.0 / RUNNER;
        double t = 0, slack = 1000;
        int k = c.first(), end = c.last();
        while (k < end) {
            byte kind = c.rowKind[k];
            int len = 0;
            while (k + len < end && c.rowKind[k + len] == kind && kind != FLOOR && kind != RING) len++;
            if (len == 0) len = 1;
            switch (kind) {
                case GAP -> {
                    if (c.trial() && k == c.edge() + 1) {
                        // the trial's chasm: the leap, at a dash's pace
                        if (len > MAX_LEAP) return new int[]{-1, 0};
                        t += len / LEAP_SPEED;
                    } else {
                        if (len > MAX_GAP) return new int[]{-1, 0};
                        t += len * per;
                    }
                }
                case RUN -> {
                    if (len > MAX_RUN) return new int[]{-1, 0};
                    t += len / WALLRUN;
                }
                case LEDGE -> {
                    double on = t;
                    t += len * per;
                    slack = Math.min(slack, LEDGE_FALL - (t - on));
                }
                case PRESS -> {
                    double cross = len * per;
                    int j = c.segmentOf(k);
                    double s = window(c, j, t, cross);
                    if (s < 0) return new int[]{-1, 0};
                    t = s + cross;
                }
                default -> t += per;
            }
            k += len;
        }
        return new int[]{(int) Math.floor(slack), (int) Math.ceil(t)};
    }

    /**
     * The first time at or after {@code t} a crossing of {@code cross} ticks clears press j: it is down from its beat
     * for {@code down} ticks every period, and the runner keeps {@link #MARGIN} from either end of that.
     */
    static double window(Course c, int j, double t, double cross) {
        int at = c.pressAt(j);
        for (int m = 0; m < 64; m++) {
            // the open stretch before drop m: from the last one's lift to this one's fall
            double d0 = m * (double) c.period + at;
            double open0 = d0 - c.period + c.down + MARGIN, open1 = d0 - MARGIN;
            double s = Math.max(t, open0);
            if (s + cross <= open1) return s;
        }
        return -1;
    }
}
