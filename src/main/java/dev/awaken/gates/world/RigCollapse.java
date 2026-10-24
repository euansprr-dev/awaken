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
 * Collapse Run (SPEC §17 "Gate Rig"; Ember Mines, Glass Desert, Bell Crypt): the room is a pit crossed by one causeway
 * from the door you came in by to the door that leads on. Step off the landing and the causeway crumbles behind you a
 * row at a time on a beat, while bridges ahead drop and knit on a rhythm of their own and gaps ask for a jump. Reach the
 * far landing before the floor under you is gone; fall and the pit throws you back to the start, and the causeway knits
 * itself together again for the next try.
 *
 * <p>Pure geometry and timing shared by the painter, the rig director and the solvability check. The causeway is the
 * two door axes (4 wide, the doorways' width); rows are steps along it from the entry (breadth-first over its cells).
 */
public final class RigCollapse {
    private RigCollapse() {}

    /** Cell kinds of the room's interior. */
    static final byte PIT = 0, LANDING = 1, WAVE = 2, BRIDGE = 3, GAP = 4;
    /** Rows at each end that never fall: where a run starts and where it ends. */
    static final int LANDING_ROWS = 2;
    /** The pit: its floor block sits this far below the room's floor (the band's bottom row). */
    public static final int PIT_DY = -4;
    /** Warning before a row falls (its cracks glow and grit trickles), and how long the far end waits before it knits. */
    static final int WARN = 10, HOLD = 40, PERIOD = 40;
    /** The solvability check's runner: a sprint a little slower than the game's (0.28 blocks a tick), with a margin. */
    static final double RUNNER = 0.26;
    static final int MARGIN = 6;

    /**
     * One room's run. Arrays are over the interior (index {@code (x - x0) + (z - z0) * w}). Tracks 0 and 1 run from the
     * entry and from the exit; groups 0..rows-1 are the rows (bridges included), in order from the entry.
     */
    public record Run(int x0, int z0, int w, int d, byte[] kind, short[] row, BlockState[] tile, int rows, int beat, int lead,
                      int down, int gaps, int bridges, BlockState pit, int slack) {

        int at(int x, int z) {
            int ix = x - x0, iz = z - z0;
            return ix < 0 || iz < 0 || ix >= w || iz >= d ? -1 : ix + iz * w;
        }

        public byte kindAt(int x, int z) {
            int i = at(x, z);
            return i < 0 ? PIT : kind[i];
        }

        /** The block painted under the walking level of a causeway column (null: open pit). */
        public BlockState tileAt(int x, int z) {
            int i = at(x, z);
            return i < 0 || kind[i] == PIT || kind[i] == GAP ? null : tile[i];
        }

        public int rowAt(int x, int z) {
            int i = at(x, z);
            return i < 0 ? -1 : row[i];
        }

        /** The middle of row k (floor-local block-edge coordinates): where the pit puts you back, which way you face. */
        public double[] rowCentre(int k) {
            double sx = 0, sz = 0;
            int n = 0;
            for (int i = 0; i < kind.length; i++) {
                if (row[i] != k || kind[i] == PIT) continue;
                sx += x0 + i % w + 0.5;
                sz += z0 + i / w + 0.5;
                n++;
            }
            return n == 0 ? new double[]{x0 + w / 2.0, z0 + d / 2.0} : new double[]{sx / n, sz / n};
        }

        /** When row k (counted from the end a run starts at) falls, in ticks after the run starts. */
        public int fallAt(int k) {
            return lead + (k - LANDING_ROWS) * beat;
        }
    }

    // ---- building -----------------------------------------------------------------------------------------------------

    /** Whether the room can hold a run: exactly the two doors of the critical path, both at its floor, a long enough way. */
    static boolean fits(Room r) {
        if (r.pathIn == null || r.pathOut == null || r.doors.size() != 2) return false;
        for (Room.Door d : r.doors) if (d.y != r.dy) return false;
        return true;
    }

    /**
     * Builds the room's run, trying gentler variants until one passes the solvability check (the last variant has no
     * bridges or gaps and always passes). Null if the room can't hold one.
     */
    static RigRoom build(Room r, Theme theme, Palette p, Kit k, int rank) {
        if (!fits(r)) return null;
        for (int variant = 0; variant < 4; variant++) {
            Run run = layout(r, theme, p, k, rank, variant);
            if (run == null) return null;
            int slack = Math.min(solve(run, true), solve(run, false));
            if (slack >= MARGIN) return room(r, new Run(run.x0, run.z0, run.w, run.d, run.kind, run.row, run.tile, run.rows, run.beat,
                    run.lead, run.down, run.gaps, run.bridges, run.pit, slack));
        }
        return null;
    }

    private static Run layout(Room r, Theme theme, Palette p, Kit k, int rank, int variant) {
        int x0 = r.x0(), z0 = r.z0(), w = r.x1() - x0 + 1, d = r.z1() - z0 + 1;
        byte[] kind = new byte[w * d];
        short[] row = new short[w * d];
        Arrays.fill(row, (short) -1);
        // the causeway: both door axes, the doorways' 4 blocks wide
        for (int iz = 0; iz < d; iz++) {
            for (int ix = 0; ix < w; ix++) {
                int x = x0 + ix, z = z0 + iz;
                if (RoomGeometry.onAxisOf(r, r.pathIn, x, z, 1) || RoomGeometry.onAxisOf(r, r.pathOut, x, z, 1)) kind[ix + iz * w] = WAVE;
            }
        }
        // rows: steps from the entry's first interior row, breadth first over the causeway
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
        // the far landing is the rows touching the exit's doorway, whatever their step count
        int exitRow = rows - 1;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == 0) continue;
            int x = x0 + i % w, z = z0 + i / w;
            if (RoomGeometry.depth(r.pathOut, x, z) == RoomGeometry.INSET && inDoorSlot(r.pathOut, x, z)) exitRow = Math.min(exitRow, row[i]);
        }
        rows = exitRow + 1;
        int crumbling = rows - 2 * LANDING_ROWS;
        if (crumbling < 6) return null;
        long seed = r.seed;
        // harder at higher ranks; each variant takes something away until the run passes the check
        int beat = rank <= 1 ? 5 : 4;
        int lead = rank <= 1 ? 24 : 20;
        int down = rank >= 3 ? 16 : 12;
        int gaps = variant >= 2 || rank == 0 ? 0 : Math.max(1, crumbling / (rank >= 3 ? 10 : 12));
        int gapLen = rank >= 3 && variant == 0 ? 2 : 1;
        int bridges = variant >= 1 || rank == 0 || crumbling < 12 ? 0 : (rank >= 2 && crumbling >= 20 ? 2 : 1);
        if (variant == 3) lead += 10;
        byte[] rowKind = new byte[rows];
        Arrays.fill(rowKind, WAVE);
        for (int i = 0; i < LANDING_ROWS; i++) {
            rowKind[i] = LANDING;
            rowKind[rows - 1 - i] = LANDING;
        }
        // bridges in the far half (the wave has fallen behind by then), gaps spread between, never touching each other
        int lo = LANDING_ROWS + 3, hi = rows - LANDING_ROWS - 4;
        for (int b = 0; b < bridges; b++) {
            for (int t = 0; t < 8; t++) {
                int s = lo + (hi - lo) / 2 + Hash.range(seed, 300 + b, t, Math.max(1, (hi - lo) / 2));
                if (s + 1 > hi || !clear(rowKind, s - 2, s + 3)) continue;
                rowKind[s] = rowKind[s + 1] = BRIDGE;
                break;
            }
        }
        for (int g = 0; g < gaps; g++) {
            for (int t = 0; t < 8; t++) {
                int s = lo + Hash.range(seed, 400 + g, t, Math.max(1, hi - lo));
                if (s + gapLen - 1 > hi || !clear(rowKind, s - 3, s + gapLen + 2)) continue;
                for (int j = 0; j < gapLen; j++) rowKind[s + j] = GAP;
                break;
            }
        }
        int placedGaps = 0, placedBridges = 0;
        for (int i = 0; i < rows; i++) {
            if (rowKind[i] == GAP && (i == 0 || rowKind[i - 1] != GAP)) placedGaps++;
            if (rowKind[i] == BRIDGE && (i == 0 || rowKind[i - 1] != BRIDGE)) placedBridges++;
        }
        BlockState[] tile = new BlockState[w * d];
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == 0) continue;
            if (row[i] < 0 || row[i] >= rows) {
                // past the exit landing (an L's corner beyond the door): solid landing too
                kind[i] = LANDING;
                row[i] = (short) (rows - 1);
            } else kind[i] = rowKind[row[i]];
            int x = x0 + i % w, z = z0 + i / w;
            tile[i] = kind[i] == LANDING ? p.floorAlt() : ((x + z) & 1) == 0 ? p.floor() : k.band();
        }
        return new Run(x0, z0, w, d, kind, row, tile, rows, beat, lead, down, placedGaps, placedBridges, pitFloor(theme, p), 0);
    }

    private static boolean inDoorSlot(Room.Door d, int x, int z) {
        return x >= d.gx * 16 && x < d.gx * 16 + 16 && z >= d.gz * 16 && z < d.gz * 16 + 16;
    }

    private static boolean clear(byte[] rowKind, int a, int b) {
        for (int i = Math.max(0, a); i <= Math.min(rowKind.length - 1, b); i++) if (rowKind[i] != WAVE) return false;
        return true;
    }

    /** What glows at the bottom of the pit: embers, a sea of sand, or the void with its stars. */
    static BlockState pitFloor(Theme t, Palette p) {
        return switch (t) {
            case EMBER_MINES -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case GLASS_DESERT -> Blocks.SAND.defaultBlockState();
            case BELL_CRYPT -> GatesRegistry.VOID_GLASS.get().defaultBlockState();
            default -> p.fill();
        };
    }

    /** The room's groups, tracks and triggers from its run. */
    private static RigRoom room(Room r, Run run) {
        // groups: one per row that can fall (wave rows and bridge rows)
        List<List<RigRoom.Cell>> byRow = new ArrayList<>();
        for (int k = 0; k < run.rows; k++) byRow.add(new ArrayList<>());
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < run.kind.length; i++) {
            if (run.kind[i] != WAVE && run.kind[i] != BRIDGE) continue;
            byRow.get(run.row[i]).add(new RigRoom.Cell(run.x0 + i % run.w, -1, run.z0 + i / run.w, run.tile[i], air));
        }
        List<RigRoom.Group> groups = new ArrayList<>();
        for (int k = 0; k < run.rows; k++) groups.add(new RigRoom.Group(k, List.copyOf(byRow.get(k)), RigRoom.Anim.FALL, RigRoom.Anim.RISE));
        List<RigRoom.Track> tracks = List.of(track(0, run, true), track(1, run, false));
        List<RigRoom.Trigger> triggers = List.of(
                new RigRoom.Trigger(RigRoom.On.ZONE, rowBox(run, LANDING_ROWS), 0, false, 40),
                new RigRoom.Trigger(RigRoom.On.ZONE, rowBox(run, run.rows - 1 - LANDING_ROWS), 1, false, 40));
        return new RigRoom(RigRoom.Kind.COLLAPSE_RUN, groups, tracks, triggers, run, null, null);
    }

    /** The first row that falls, as a box a player steps into (rows 0 to 2 above the floor). */
    private static RigRoom.Box rowBox(Run run, int k) {
        int ax = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (int i = 0; i < run.kind.length; i++) {
            if (run.row[i] != k) continue;
            int x = run.x0 + i % run.w, z = run.z0 + i / run.w;
            ax = Math.min(ax, x);
            az = Math.min(az, z);
            bx = Math.max(bx, x);
            bz = Math.max(bz, z);
        }
        return new RigRoom.Box(ax, 0, az, bx, 2, bz);
    }

    /** Row k as seen from the end a run starts at. */
    private static int rowFrom(Run run, int k, boolean forward) {
        return forward ? k : run.rows - 1 - k;
    }

    private static RigRoom.Track track(int id, Run run, boolean forward) {
        List<RigRoom.Beat> beats = new ArrayList<>();
        int last = 0;
        byte[] rowKind = rowKinds(run);
        for (int k = LANDING_ROWS; k < run.rows - LANDING_ROWS; k++) {
            int g = rowFrom(run, k, forward);
            if (rowKind[g] != WAVE) continue;
            int at = run.fallAt(k);
            beats.add(new RigRoom.Beat(at - WARN, RigRoom.Cue.WARN, g));
            beats.add(new RigRoom.Beat(at, RigRoom.Cue.APPLY, g));
            last = Math.max(last, at);
        }
        // the bridges drop on their own rhythm while the run lasts: down for `down` ticks at the end of every period
        for (int g = 0; g < run.rows; g++) {
            if (rowKind[g] != BRIDGE) continue;
            for (int m = 0; m * PERIOD < last; m++) {
                int drop = m * PERIOD + PERIOD - run.down;
                beats.add(new RigRoom.Beat(drop - WARN, RigRoom.Cue.WARN, g));
                beats.add(new RigRoom.Beat(drop, RigRoom.Cue.APPLY, g));
                beats.add(new RigRoom.Beat((m + 1) * PERIOD, RigRoom.Cue.RESTORE, g));
            }
        }
        // the far landing holds a moment, then the causeway knits back from the far end to the start
        int knit = last + HOLD;
        int step = 0;
        for (int k = run.rows - 1; k >= 0; k--) {
            int g = rowFrom(run, k, forward);
            if (rowKind[g] != WAVE && rowKind[g] != BRIDGE) continue;
            beats.add(new RigRoom.Beat(knit + step++, RigRoom.Cue.RESTORE, g));
        }
        beats.sort((a, b) -> a.at() != b.at() ? Integer.compare(a.at(), b.at()) : Integer.compare(a.cue().ordinal(), b.cue().ordinal()));
        return new RigRoom.Track(id, knit + step + 1, false, RigRoom.Empty.RESET, List.copyOf(beats));
    }

    static byte[] rowKinds(Run run) {
        byte[] out = new byte[run.rows];
        for (int i = 0; i < run.kind.length; i++) if (run.row[i] >= 0 && run.row[i] < run.rows) out[run.row[i]] = run.kind[i];
        return out;
    }

    // ---- solvability -----------------------------------------------------------------------------------------------

    /**
     * Runs the solvability check's runner from one end: it moves a row every 1/{@link #RUNNER} ticks, jumps the gaps,
     * waits on the row before a bridge for a window it can cross, and must be off every row {@link #MARGIN} ticks before
     * it falls. Returns the smallest margin it kept (negative: the run can't be made).
     */
    public static int solve(Run run, boolean forward) {
        byte[] rowKind = rowKinds(run);
        double per = 1.0 / RUNNER;
        double t = 0; // the runner reaches the first falling row as the run starts
        double slack = Double.MAX_VALUE;
        int k = LANDING_ROWS;
        int end = run.rows - LANDING_ROWS;
        while (k < end) {
            int g = rowFrom(run, k, forward);
            byte kind = rowKind[g];
            if (kind == GAP) {
                int len = 0;
                while (k + len < end && rowKind[rowFrom(run, k + len, forward)] == GAP) len++;
                if (len > 2) return -1;
                t += len * per;
                k += len;
                continue;
            }
            if (kind == BRIDGE) {
                int len = 0;
                while (k + len < end && rowKind[rowFrom(run, k + len, forward)] == BRIDGE) len++;
                double cross = len * per;
                double start = window(t, cross, run.down);
                if (start > t) {
                    // wait on the row before the bridge: it must not fall in the meantime
                    int prev = k - 1;
                    if (prev >= LANDING_ROWS && rowKind[rowFrom(run, prev, forward)] == WAVE) {
                        slack = Math.min(slack, run.fallAt(prev) - start);
                    }
                    t = start;
                }
                t += cross;
                k += len;
                continue;
            }
            t += per;
            slack = Math.min(slack, run.fallAt(k) - t);
            k++;
        }
        return (int) Math.floor(Math.min(slack, 1000));
    }

    /**
     * The first time at or after {@code t} a crossing of {@code cross} ticks fits while the bridge is up: it is back
     * {@link RigRoom#RISE_TICKS} after its period starts (2 ticks to step on), and the runner is off 4 ticks before it drops.
     */
    private static double window(double t, double cross, int down) {
        for (int m = 0; m < 1000; m++) {
            double up0 = m == 0 ? 0 : m * PERIOD + RigRoom.RISE_TICKS + 2, up1 = m * PERIOD + PERIOD - down - 4;
            if (up1 < t) continue;
            double s = Math.max(t, up0);
            if (s + cross <= up1) return s;
        }
        return t;
    }
}
