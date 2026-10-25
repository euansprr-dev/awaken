package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Palette;
import dev.awaken.gates.content.Theme;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Gravity Wells (SPEC §17 "Gate Rig"; Sky Archipelago, Precursor Lab, Nightmare): chasms too wide to jump cut the
 * way across the room, and over each one a well turns which way is down. Step in at the rim and you fall up to the
 * ceiling, walk it across, and drop softly on the far ledge as you step out. While monsters fight in the room the
 * whole room surges on a rhythm: everyone, monsters too, falls to the ceiling and back.
 *
 * <p>Pure: bands along the way through (the axis from the door you come in by to the door that leads on) are ledge,
 * chasm or ledge again; a well is a chasm and one row of ledge on each side, floor to ceiling.
 */
public final class RigWells {
    private RigWells() {}

    /** The chasms' floor sits at the band's bottom, like the collapse pit. */
    public static final int PIT_DY = RigCollapse.PIT_DY;
    /** A chasm is never narrower than this: farther than any sprint jump. */
    static final int MIN_CHASM = 5;
    /** The surge's rhythm while monsters live: warned, then everyone falls up for a while. */
    static final int SURGE_LOOP = 280, SURGE_WARN = 120, SURGE_ON = 150, SURGE_OFF = 210;

    /**
     * {@code alongX}: the way through runs along x (doors east and west). {@code a0}/{@code dirA}: the entry side's
     * interior row and the way it counts (+1 or -1). Chasms are [from, to) in rows from the entry.
     */
    public record Field(boolean alongX, int a0, int dirA, int length, int c0, int c1, int[] chasmFrom, int[] chasmTo, int height,
                        BlockState pit) {

        /** Rows from the entry of a floor-local column. */
        public int along(int x, int z) {
            return ((alongX ? x : z) - a0) * dirA;
        }

        public int chasmAt(int a) {
            for (int i = 0; i < chasmFrom.length; i++) if (a >= chasmFrom[i] && a < chasmTo[i]) return i;
            return -1;
        }

        /** Inside a well: a chasm and the ledge row either side of it. */
        public int wellAt(int a) {
            for (int i = 0; i < chasmFrom.length; i++) if (a >= chasmFrom[i] - 1 && a < chasmTo[i] + 1) return i;
            return -1;
        }

        public boolean rim(int a) {
            for (int i = 0; i < chasmFrom.length; i++) if (a == chasmFrom[i] - 1 || a == chasmTo[i]) return true;
            return false;
        }

        /** The well's box in floor-local blocks (rows from the pit floor to the ceiling). */
        public RigRoom.Box box(int i) {
            int a = chasmFrom[i] - 1, b = chasmTo[i];
            int p = a0 + a * dirA, q = a0 + b * dirA;
            int lo = Math.min(p, q), hi = Math.max(p, q);
            return alongX ? new RigRoom.Box(lo, PIT_DY + 1, c0, hi, height, c1) : new RigRoom.Box(c0, PIT_DY + 1, lo, c1, height, hi);
        }

        public int wells() {
            return chasmFrom.length;
        }
    }

    static boolean fits(Room r) {
        if (r.pathIn == null || r.pathOut == null || r.pathOut.side != (r.pathIn.side + 2) % 4) return false;
        for (Room.Door d : r.doors) if (d.y != r.dy) return false;
        return r.height >= 7;
    }

    static RigRoom build(Room r, Theme theme, Palette p, int rank) {
        if (!fits(r)) return null;
        boolean alongX = r.pathIn.side == 1 || r.pathIn.side == 3;
        int lo = alongX ? r.x0() : r.z0(), hi = alongX ? r.x1() : r.z1();
        boolean fromLow = r.pathIn.side == 3 || r.pathIn.side == 0;
        int a0 = fromLow ? lo : hi, dirA = fromLow ? 1 : -1;
        int length = hi - lo + 1;
        int c0 = alongX ? r.z0() : r.x0(), c1 = alongX ? r.z1() : r.x1();
        int wide = rank >= 3 ? 7 : 6;
        int[] from, to;
        if (length >= 26) {
            int w1 = MIN_CHASM + Hash.range(r.seed, 0x3E1, 1, wide - MIN_CHASM + 1), w2 = MIN_CHASM + Hash.range(r.seed, 0x3E1, 2, wide - MIN_CHASM + 1);
            from = new int[]{4, length - 4 - w2};
            to = new int[]{4 + w1, length - 4};
        } else {
            int w = Math.min(wide, length - 6);
            if (w < MIN_CHASM) return null;
            int s = (length - w) / 2;
            from = new int[]{s};
            to = new int[]{s + w};
        }
        Field field = new Field(alongX, a0, dirA, length, c0, c1, from, to, r.height, pitFloor(theme, p));
        // a side door must open onto a ledge, never into a well
        for (Room.Door d : r.doors) {
            if (d == r.pathIn || d == r.pathOut) continue;
            for (int lat = RoomGeometry.DOOR_LO - 1; lat <= RoomGeometry.DOOR_HI + 1; lat++) {
                int x = d.side == 0 || d.side == 2 ? d.gx * 16 + lat : d.side == 1 ? r.x1() : r.x0();
                int z = d.side == 1 || d.side == 3 ? d.gz * 16 + lat : d.side == 0 ? r.z0() : r.z1();
                if (field.wellAt(field.along(x, z)) >= 0) return null;
            }
        }
        if (!solvable(field) || reachableWithoutWells(field)) return null;
        // one flag group: the surge. Track 0 is its loop while the room's monsters fight.
        List<RigRoom.Group> groups = List.of(new RigRoom.Group(0, List.of(), RigRoom.Anim.NONE, RigRoom.Anim.NONE));
        List<RigRoom.Track> tracks = List.of(new RigRoom.Track(0, SURGE_LOOP, true, RigRoom.Empty.RESET, List.of(
                new RigRoom.Beat(SURGE_WARN, RigRoom.Cue.WARN, 0), new RigRoom.Beat(SURGE_ON, RigRoom.Cue.APPLY, 0),
                new RigRoom.Beat(SURGE_OFF, RigRoom.Cue.RESTORE, 0))));
        List<RigRoom.Trigger> triggers = List.of(new RigRoom.Trigger(RigRoom.On.ENCOUNTER,
                new RigRoom.Box(r.x0(), 0, r.z0(), r.x1(), r.height, r.z1()), 0, false, 0));
        return new RigRoom(RigRoom.Kind.GRAVITY_WELLS, groups, tracks, triggers, null, null, field, null);
    }

    static BlockState pitFloor(Theme t, Palette p) {
        return switch (t) {
            case PRECURSOR_LAB -> GatesRegistry.LAB_PANEL.get().defaultBlockState();
            case NIGHTMARE -> GatesRegistry.VOID_GLASS.get().defaultBlockState();
            case SKY_ARCHIPELAGO -> GatesRegistry.CLOUD.get().defaultBlockState();
            default -> p.fill();
        };
    }

    // ---- solvability ------------------------------------------------------------------------------------------------

    /**
     * Breadth-first over two layers of (row, across) cells: the floor (ledges) and the ceiling (inside wells only).
     * A floor cell in a well lifts you to the ceiling above it; stepping off the ceiling out of a well drops you onto the
     * floor below, which must be ledge. Solvable when the exit's ledge row is reached from the entry's.
     */
    public static boolean solvable(Field f) {
        return reach(f, true);
    }

    /** The design check: without the wells, the chasms can't be crossed (so the wells are the way). */
    public static boolean reachableWithoutWells(Field f) {
        return reach(f, false);
    }

    private static boolean reach(Field f, boolean wells) {
        int across = f.c1 - f.c0 + 1, n = f.length * across;
        boolean[] seen = new boolean[n * 2];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int c = 0; c < across; c++) {
            seen[c * f.length] = true;
            q.add(c * f.length);
        }
        while (!q.isEmpty()) {
            int s = q.poll();
            boolean ceiling = s >= n;
            int cell = s % n, a = cell % f.length, c = cell / f.length;
            if (!ceiling && a == f.length - 1) return true;
            List<Integer> next = new ArrayList<>(6);
            int[][] steps = {{a + 1, c}, {a - 1, c}, {a, c + 1}, {a, c - 1}};
            for (int[] st : steps) {
                if (st[0] < 0 || st[1] < 0 || st[0] >= f.length || st[1] >= across) continue;
                int j = st[1] * f.length + st[0];
                if (!ceiling) {
                    if (f.chasmAt(st[0]) < 0) next.add(j);
                } else if (f.wellAt(st[0]) >= 0) next.add(n + j);
                else if (f.chasmAt(st[0]) < 0) next.add(j); // off the ceiling, down onto the ledge below
            }
            if (wells && !ceiling && f.wellAt(a) >= 0) next.add(n + cell);
            for (int j : next) {
                if (!seen[j]) {
                    seen[j] = true;
                    q.add(j);
                }
            }
        }
        return false;
    }
}
