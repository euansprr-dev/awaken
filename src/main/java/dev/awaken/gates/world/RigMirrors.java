package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.RigBlocks;
import dev.awaken.gates.content.Palette;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Mirror Hall (SPEC §17 "Gate Rig"; Mirror Palace, Precursor Lab): a lens in one wall throws a beam of light across
 * the hall at chest height, the door that leads on is barred with light, and the seal beside it opens when the beam
 * reaches it. Mirrors stand on pedestals at the crossings of the hall's lattice; using one turns it a quarter. The beam
 * burns whatever monster stands in it, so the light can be routed through a fight. In the bigger halls a pavilion of
 * mirrors stands against one wall: its front is cracked, and only a spirit (Ghost Step) walks through it to the cache
 * inside; once someone is in, the cracked glass falls away for everyone.
 *
 * <p>Pure: the puzzle is built from the room's seed by walking a path of turns from the lens to the seal (the witness
 * that it can be solved), then every mirror on it is turned wrong and decoys are added.
 */
public final class RigMirrors {
    private RigMirrors() {}

    /** The beam runs one block above the floor: chest height, through anyone standing in it. */
    public static final int BEAM_DY = 1;
    /** Mirrors stand on the hall's lattice: slot offsets 3, 6, 9 and 12, three blocks apart. */
    static boolean lattice(int v) {
        int o = Math.floorMod(v, 16);
        return o == 3 || o == 6 || o == 9 || o == 12;
    }

    /** The pavilion: 5 by 9 inside, a wall of mirrors round it four high, its front cracked. */
    static final int CHAMBER_W = 7, CHAMBER_D = 10, CHAMBER_H = 4;

    public record Mirror(int x, int z, int start, int solution, boolean decoy) {}

    /**
     * One hall's puzzle. Lens and seal are wall cells at {@link #BEAM_DY}; {@code chamber} is the pavilion's inside
     * (null without one), {@code crack} its cracked front, {@code shell} the rest of its walls and roof.
     */
    public record Puzzle(int x0, int z0, int x1, int z1, int lensX, int lensZ, int lensDir, int sealX, int sealZ, int sealDir,
                         List<Mirror> mirrors, RigRoom.Box chamber, List<int[]> crack, List<int[]> shell, int turns) {

        public boolean interior(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        public Mirror mirrorAt(int x, int z) {
            for (Mirror m : mirrors) if (m.x == x && m.z == z) return m;
            return null;
        }

        /** The pavilion's footprint with its walls (no beam, no pedestal, no walking through). */
        public boolean pavilion(int x, int z) {
            if (chamber == null) return false;
            return x >= chamber.x0() - 1 && x <= chamber.x1() + 1 && z >= chamber.z0() - 1 && z <= chamber.z1() + 1;
        }

        /** The room as the light sees it, with every mirror at the given turns (index order of {@link #mirrors}). */
        public RigBeam.Grid grid(int[] turns) {
            return new RigBeam.Grid() {
                @Override
                public RigBeam.Hit at(int x, int z) {
                    if (x == sealX && z == sealZ) return RigBeam.Hit.RECEIVER;
                    if (!interior(x, z) || pavilion(x, z)) return RigBeam.Hit.SOLID;
                    return mirrorAt(x, z) != null ? RigBeam.Hit.MIRROR : RigBeam.Hit.EMPTY;
                }

                @Override
                public int turn(int x, int z) {
                    for (int i = 0; i < mirrors.size(); i++) if (mirrors.get(i).x == x && mirrors.get(i).z == z) return turns[i];
                    return 0;
                }
            };
        }

        public boolean solvedBy(int[] turns) {
            return RigBeam.trace(grid(turns), lensX, lensZ, lensDir, null) == RigBeam.pack(sealX, sealZ);
        }

        public int[] starts() {
            int[] t = new int[mirrors.size()];
            for (int i = 0; i < t.length; i++) t[i] = mirrors.get(i).start;
            return t;
        }

        public int[] solution() {
            int[] t = new int[mirrors.size()];
            for (int i = 0; i < t.length; i++) t[i] = mirrors.get(i).solution;
            return t;
        }
    }

    // ---- building ---------------------------------------------------------------------------------------------------

    static boolean fits(Room r) {
        return r.pathIn != null && r.pathOut != null && r.pathOut.y == r.dy;
    }

    static RigRoom build(Room r, Palette p, Kit k, int rank) {
        if (!fits(r)) return null;
        SplittableRandom rnd = new SplittableRandom(Hash.of(r.seed, 0x4D1, 7));
        int x0 = r.x0(), z0 = r.z0(), x1 = r.x1(), z1 = r.z1();
        int[] chamber = r.area() >= 2 ? chamber(r) : null;
        RigRoom.Box inside = chamber == null ? null : new RigRoom.Box(chamber[0], 0, chamber[1], chamber[2], CHAMBER_H - 1, chamber[3]);
        // the seal stands beside the door that leads on, on the lattice line next to the doorway
        Room.Door out = r.pathOut;
        int sealDir = out.side;
        int want = rank <= 1 ? 2 : rank == 2 ? 3 : 4;
        if (r.area() == 1) want = Math.min(want, 3);
        for (int turns = want; turns >= 2; turns--) {
            for (int lat : rnd.nextBoolean() ? new int[]{3, 12} : new int[]{12, 3}) {
                int[] seal = wallCell(r, out, lat);
                for (int[] lens : lenses(r, rnd, seal)) {
                    Puzzle pz = search(r, rnd, x0, z0, x1, z1, inside, lens, seal, sealDir, turns, rank);
                    if (pz != null) return room(r, p, k, pz);
                }
            }
        }
        return null;
    }

    /** The wall cell at lateral {@code lat} of a door's slot, in the wall layer next to the room's interior. */
    private static int[] wallCell(Room r, Room.Door d, int lat) {
        return switch (d.side) {
            case 0 -> new int[]{d.gx * 16 + lat, r.z0() - 1};
            case 2 -> new int[]{d.gx * 16 + lat, r.z1() + 1};
            case 1 -> new int[]{r.x1() + 1, d.gz * 16 + lat};
            default -> new int[]{r.x0() - 1, d.gz * 16 + lat};
        };
    }

    /** Lens candidates: lattice cells in walls clear of every doorway and its frame, shining into the room; shuffled. */
    private static List<int[]> lenses(Room r, SplittableRandom rnd, int[] seal) {
        List<int[]> out = new ArrayList<>();
        for (int side = 0; side < 4; side++) {
            boolean alongX = side == 0 || side == 2;
            int lo = alongX ? r.x0() : r.z0(), hi = alongX ? r.x1() : r.z1();
            for (int c = lo; c <= hi; c++) {
                if (!lattice(c)) continue;
                int wx = alongX ? c : side == 1 ? r.x1() + 1 : r.x0() - 1;
                int wz = alongX ? (side == 0 ? r.z0() - 1 : r.z1() + 1) : c;
                if (wx == seal[0] && wz == seal[1]) continue;
                if (nearDoor(r, side, wx, wz)) continue;
                out.add(new int[]{wx, wz, (side + 2) % 4});
            }
        }
        for (int i = out.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] t = out.get(i);
            out.set(i, out.get(j));
            out.set(j, t);
        }
        return out.size() > 12 ? out.subList(0, 12) : out;
    }

    private static boolean nearDoor(Room r, int side, int wx, int wz) {
        for (Room.Door d : r.doors) {
            if (d.side != side) continue;
            int lat = (side == 0 || side == 2) ? wx - d.gx * 16 : wz - d.gz * 16;
            boolean inSlot = (side == 0 || side == 2) ? wx >= d.gx * 16 && wx < d.gx * 16 + 16 : wz >= d.gz * 16 && wz < d.gz * 16 + 16;
            if (inSlot && lat >= RoomGeometry.DOOR_LO - 2 && lat <= RoomGeometry.DOOR_HI + 2) return true;
        }
        return false;
    }

    /** Where a mirror may stand: a lattice crossing off every door axis, clear of features and the pavilion. */
    private static boolean mirrorSpot(Room r, int x, int z, RigRoom.Box chamber) {
        if (!lattice(x) || !lattice(z)) return false;
        if (x <= r.x0() || x >= r.x1() || z <= r.z0() || z >= r.z1()) return false;
        if (RoomGeometry.onDoorAxis(r, x, z, 2)) return false;
        if (chamber != null && x >= chamber.x0() - 2 && x <= chamber.x1() + 2 && z >= chamber.z0() - 2 && z <= chamber.z1() + 2) return false;
        for (Room.Feature f : r.features) if (Math.abs(f.x() - x) <= 2 && Math.abs(f.z() - z) <= 2) return false;
        return true;
    }

    private static final int BUDGET = 4000;

    private static Puzzle search(Room r, SplittableRandom rnd, int x0, int z0, int x1, int z1, RigRoom.Box chamber,
                                 int[] lens, int[] seal, int sealDir, int turns, int rank) {
        Puzzle frame = new Puzzle(x0, z0, x1, z1, lens[0], lens[1], lens[2], seal[0], seal[1], sealDir, List.of(), chamber, List.of(), List.of(), 0);
        List<int[]> path = new ArrayList<>(); // turn points: x, z, in, out
        int[] budget = {BUDGET};
        if (!walk(r, rnd, frame, lens[0], lens[1], lens[2], turns, path, budget)) return null;
        List<Mirror> mirrors = new ArrayList<>();
        for (int[] t : path) mirrors.add(new Mirror(t[0], t[1], t[3], t[3], false));
        // the light's cells on the solution: decoys never stand on them
        Puzzle solved = withMirrors(frame, mirrors);
        java.util.Set<Long> lit = new java.util.HashSet<>();
        RigBeam.trace(solved.grid(solved.solution()), lens[0], lens[1], lens[2], (ax, az, bx, bz, dir) -> {
            int n = Math.abs(bx - ax) + Math.abs(bz - az);
            for (int i = 0; i <= n; i++) lit.add(RigBeam.pack(ax + RigBeam.DX[dir] * i, az + RigBeam.DZ[dir] * i));
        });
        int decoys = rank <= 1 ? 1 : rank == 2 ? 2 : 3;
        List<int[]> spots = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (mirrorSpot(r, x, z, chamber) && !lit.contains(RigBeam.pack(x, z)) && solved.mirrorAt(x, z) == null) spots.add(new int[]{x, z});
        }
        for (int i = 0; i < decoys && !spots.isEmpty(); i++) {
            int[] s = spots.remove(rnd.nextInt(spots.size()));
            int o = rnd.nextInt(4);
            mirrors.add(new Mirror(s[0], s[1], o, o, true));
        }
        // every mirror on the way starts wrong, and the hall must not start solved
        for (int i = 0; i < mirrors.size(); i++) {
            Mirror m = mirrors.get(i);
            if (!m.decoy) mirrors.set(i, new Mirror(m.x, m.z, Math.floorMod(m.solution + 1 + rnd.nextInt(3), 4), m.solution, false));
        }
        Puzzle pz = withMirrors(frame, mirrors);
        for (int tries = 0; tries < 8 && pz.solvedBy(pz.starts()); tries++) {
            Mirror m = mirrors.get(0);
            int next = Math.floorMod(m.start + 1, 4);
            if (next == m.solution) next = Math.floorMod(next + 1, 4);
            mirrors.set(0, new Mirror(m.x, m.z, next, m.solution, false));
            pz = withMirrors(frame, mirrors);
        }
        if (pz.solvedBy(pz.starts()) || !pz.solvedBy(pz.solution())) return null;
        List<int[]> crack = new ArrayList<>(), shell = new ArrayList<>();
        if (chamber != null) pavilionCells(chamber, r, crack, shell);
        return new Puzzle(x0, z0, x1, z1, lens[0], lens[1], lens[2], seal[0], seal[1], sealDir, List.copyOf(mirrors), chamber,
                List.copyOf(crack), List.copyOf(shell), path.size());
    }

    private static Puzzle withMirrors(Puzzle f, List<Mirror> mirrors) {
        return new Puzzle(f.x0, f.z0, f.x1, f.z1, f.lensX, f.lensZ, f.lensDir, f.sealX, f.sealZ, f.sealDir, List.copyOf(mirrors), f.chamber,
                f.crack, f.shell, f.turns);
    }

    /** Depth-first over turn points: from (x, z) heading {@code dir} with {@code left} turns to place before the seal. */
    private static boolean walk(Room r, SplittableRandom rnd, Puzzle f, int x, int z, int dir, int left, List<int[]> path, int[] budget) {
        if (--budget[0] < 0) return false;
        List<int[]> ahead = new ArrayList<>();
        int cx = x, cz = z;
        while (true) {
            cx += RigBeam.DX[dir];
            cz += RigBeam.DZ[dir];
            if (cx == f.sealX && cz == f.sealZ) {
                if (left == 0 && dir == f.sealDir) return true;
                break;
            }
            if (!f.interior(cx, cz) || f.pavilion(cx, cz) || turnPoint(path, cx, cz)) break;
            if (left > 0 && mirrorSpot(r, cx, cz, f.chamber) && Math.abs(cx - x) + Math.abs(cz - z) >= 3 && !lit(f, path, cx, cz)) {
                ahead.add(new int[]{cx, cz});
                if (ahead.size() > 8) break;
            }
        }
        if (left == 0) return false;
        for (int i = ahead.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] t = ahead.get(i);
            ahead.set(i, ahead.get(j));
            ahead.set(j, t);
        }
        for (int[] p : ahead) {
            int first = rnd.nextBoolean() ? 1 : 3;
            for (int turn : new int[]{first, 4 - first}) {
                int nd = (dir + turn) % 4;
                path.add(new int[]{p[0], p[1], dir, RigBeam.turnFor(dir, nd)});
                if (walk(r, rnd, f, p[0], p[1], nd, left - 1, path, budget)) return true;
                path.removeLast();
            }
        }
        return false;
    }

    /** A turn point already on the path: light can't pass through a mirror it has already used. */
    private static boolean turnPoint(List<int[]> path, int x, int z) {
        for (int[] t : path) if (t[0] == x && t[1] == z) return true;
        return false;
    }

    /** Whether the path's light so far (lens to the last turn) crosses (x, z): a mirror there would cut it. */
    private static boolean lit(Puzzle f, List<int[]> path, int x, int z) {
        int ax = f.lensX, az = f.lensZ;
        for (int[] t : path) {
            if (Math.min(ax, t[0]) <= x && x <= Math.max(ax, t[0]) && Math.min(az, t[1]) <= z && z <= Math.max(az, t[1])) return true;
            ax = t[0];
            az = t[1];
        }
        return false;
    }

    /**
     * A pavilion against a wall with no doorway near it: {x0, z0, x1, z1} of its inside, or null. Its front faces the
     * hall, a full {@link #CHAMBER_D} deep so a spirit stepping through the glass from anywhere in reach lands inside.
     */
    static int[] chamber(Room r) {
        int start = (int) Math.floorMod(Hash.of(r.seed, 0xC4A, 1), 4L);
        for (int k = 0; k < 4; k++) {
            int side = (start + k) % 4;
            boolean alongX = side == 0 || side == 2;
            int lo = alongX ? r.x0() : r.z0(), hi = alongX ? r.x1() : r.z1();
            int span = alongX ? r.z1() - r.z0() + 1 : r.x1() - r.x0() + 1;
            if (span < CHAMBER_D + 8 || hi - lo + 1 < CHAMBER_W + 4) continue;
            for (int a = lo + 1; a + CHAMBER_W - 1 <= hi - 1; a++) {
                int ia0 = a + 1, ia1 = a + CHAMBER_W - 2;
                int[] in;
                switch (side) {
                    case 0 -> in = new int[]{ia0, r.z0(), ia1, r.z0() + CHAMBER_D - 2};
                    case 2 -> in = new int[]{ia0, r.z1() - CHAMBER_D + 2, ia1, r.z1()};
                    case 1 -> in = new int[]{r.x1() - CHAMBER_D + 2, ia0, r.x1(), ia1};
                    default -> in = new int[]{r.x0(), ia0, r.x0() + CHAMBER_D - 2, ia1};
                }
                if (pavilionClear(r, in)) return in;
            }
        }
        return null;
    }

    private static boolean pavilionClear(Room r, int[] in) {
        for (int x = in[0] - 1; x <= in[2] + 1; x++) {
            for (int z = in[1] - 1; z <= in[3] + 1; z++) {
                if (x < r.x0() || x > r.x1() || z < r.z0() || z > r.z1()) continue;
                if (RoomGeometry.onDoorAxis(r, x, z, 4) || RoomGeometry.tunnel(r, x, z) != null) return false;
                for (Room.Feature f : r.features) if (Math.abs(f.x() - x) <= 2 && Math.abs(f.z() - z) <= 2) return false;
            }
        }
        return true;
    }

    /** The pavilion's walls: the cracked front (toward the hall) and the rest (sides, the roof); {x, dy, z}. */
    private static void pavilionCells(RigRoom.Box c, Room r, List<int[]> crack, List<int[]> shell) {
        for (int x = c.x0() - 1; x <= c.x1() + 1; x++) {
            for (int z = c.z0() - 1; z <= c.z1() + 1; z++) {
                if (x < r.x0() || x > r.x1() || z < r.z0() || z > r.z1()) continue;
                boolean edge = x < c.x0() || x > c.x1() || z < c.z0() || z > c.z1();
                if (edge) {
                    boolean front = isFront(c, r, x, z);
                    for (int dy = 0; dy < CHAMBER_H; dy++) (front ? crack : shell).add(new int[]{x, dy, z});
                }
                shell.add(new int[]{x, CHAMBER_H, z});
            }
        }
    }

    /** The wall of the pavilion that faces the hall (its corners are plain mirror). */
    private static boolean isFront(RigRoom.Box c, Room r, int x, int z) {
        boolean againstN = c.z0() == r.z0(), againstS = c.z1() == r.z1(), againstW = c.x0() == r.x0(), againstE = c.x1() == r.x1();
        if (againstN) return z == c.z1() + 1 && x >= c.x0() && x <= c.x1();
        if (againstS) return z == c.z0() - 1 && x >= c.x0() && x <= c.x1();
        if (againstW) return x == c.x1() + 1 && z >= c.z0() && z <= c.z1();
        if (againstE) return x == c.x0() - 1 && z >= c.z0() && z <= c.z1();
        return false;
    }

    // ---- the room ---------------------------------------------------------------------------------------------------

    /** Groups: 0 the seal lights, 1 the light bars fall, 2 the pavilion's cracked front. Tracks: 0 open the way, 1 open the pavilion. */
    private static RigRoom room(Room r, Palette p, Kit k, Puzzle pz) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState seal = GatesRegistry.RIG_SEAL.get().defaultBlockState().setValue(RigBlocks.Seal.FACING, GatePainter.dir((pz.sealDir + 2) % 4));
        List<RigRoom.Group> groups = new ArrayList<>();
        groups.add(new RigRoom.Group(0, List.of(new RigRoom.Cell(pz.sealX, BEAM_DY, pz.sealZ, seal, seal.setValue(RigBlocks.Seal.LIT, true))),
                RigRoom.Anim.IGNITE, RigRoom.Anim.NONE));
        List<RigRoom.Cell> bars = new ArrayList<>();
        Room.Door d = r.pathOut;
        BlockState bar = GatesRegistry.BARS.get().defaultBlockState();
        for (int lat = RoomGeometry.DOOR_LO; lat <= RoomGeometry.DOOR_HI; lat++) {
            int x, z;
            switch (d.side) {
                case 0 -> { x = d.gx * 16 + lat; z = d.gz * 16; }
                case 2 -> { x = d.gx * 16 + lat; z = d.gz * 16 + 15; }
                case 1 -> { x = d.gx * 16 + 15; z = d.gz * 16 + lat; }
                default -> { x = d.gx * 16; z = d.gz * 16 + lat; }
            }
            for (int y = 0; y < RoomGeometry.DOOR_HEIGHT; y++) bars.add(new RigRoom.Cell(x, d.y - r.dy + y, z, bar, air));
        }
        groups.add(new RigRoom.Group(1, List.copyOf(bars), RigRoom.Anim.SHATTER, RigRoom.Anim.NONE));
        List<RigRoom.Cell> crack = new ArrayList<>();
        BlockState cracked = GatesRegistry.MIRROR_CRACKED.get().defaultBlockState();
        for (int[] c : pz.crack) crack.add(new RigRoom.Cell(c[0], c[1], c[2], cracked, air));
        groups.add(new RigRoom.Group(2, List.copyOf(crack), RigRoom.Anim.SHATTER, RigRoom.Anim.NONE));
        List<RigRoom.Track> tracks = List.of(
                new RigRoom.Track(0, 30, false, RigRoom.Empty.KEEP, List.of(new RigRoom.Beat(0, RigRoom.Cue.APPLY, 0),
                        new RigRoom.Beat(14, RigRoom.Cue.WARN, 1), new RigRoom.Beat(24, RigRoom.Cue.APPLY, 1))),
                new RigRoom.Track(1, 40, false, RigRoom.Empty.KEEP, List.of(new RigRoom.Beat(0, RigRoom.Cue.WARN, 2),
                        new RigRoom.Beat(30, RigRoom.Cue.APPLY, 2))));
        List<RigRoom.Trigger> triggers = new ArrayList<>();
        triggers.add(new RigRoom.Trigger(RigRoom.On.BEAM, new RigRoom.Box(pz.sealX, BEAM_DY, pz.sealZ, pz.sealX, BEAM_DY, pz.sealZ), 0, true, 0));
        if (pz.chamber != null) triggers.add(new RigRoom.Trigger(RigRoom.On.ZONE, pz.chamber, 1, true, 0));
        return new RigRoom(RigRoom.Kind.MIRROR_HALL, groups, tracks, triggers, null, pz, null);
    }
}
