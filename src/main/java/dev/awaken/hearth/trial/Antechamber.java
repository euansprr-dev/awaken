package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cradle's antechamber (SPEC §47.10): the last Proving's hall. A round white hall whose floor is a clock face (twelve
 * gold marks round its rim, a ring of gold in the middle, the Loop), under a black ceiling set with stars. Lit windows
 * stand between the marks. North is the Seed's door, a great round door of turning rings; past it a short tunnel runs to
 * a wall of light. South an arch leads back out into the dark. Built by the trials' generator in every hall cell at
 * {@link #AT_X}, {@link #AT_Z}. Pure: {@link #block} says what stands at a spot. Local x runs east, z south, dy up from the
 * trials floor (you stand at dy 1).
 * <p>
 * At three quarters of the Last Loop's health night sweeps round the floor like a clock's hand; at half the outer ring of
 * the floor falls away a mark at a time; at a quarter the Seed's door begins to turn and open.
 */
public final class Antechamber {
    private Antechamber() {}

    /** The hall's middle, this far east and south of its cell's north-west corner (south of the Threshold). */
    public static final int AT_X = 110, AT_Z = 420;
    /** The floor's radius; the wall's outside; the wall's top; the ceiling. */
    public static final int R = 22, WALL_OUT = 25, WALL_TOP = 16, CEIL = 18;
    /** The Loop, the gold ring in the floor's middle (its middle radius), and where the outer ring breaks away. */
    public static final double LOOP_R = 8, RING_IN = 16;
    /** The Seed's door: its plane, its middle's height, its rings' outer radii (core, three rings, the frame). */
    public static final int DOOR_Z = -23, DOOR_DY = 7;
    static final double[] DOOR_RINGS = {1.5, 3, 4.5, 6.5, 8};
    /** The tunnel past the door, to the wall of light; the corridor back out the south arch. */
    public static final int TUNNEL_N = -34, ARCH_Z = 24, BACK = 33;
    public static final int WALL = 26, NORTH = TUNNEL_N - 1, LOW = -9, ROOF = CEIL;
    /** Where you wake (looking north at the door), and the way out both ends. */
    public static final int WAKE_Z = 17, OUT_Z = 30, OUT_N = -30;
    /** The causeway's half-width: the outer ring stays this far either side of the line from the arch to the middle. */
    static final int CAUSEWAY = 3;

    private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState BRICKS = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState CORE = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState PANE = Blocks.STAINED_GLASS.pick(DyeColor.WHITE).defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState SKY = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
    private static final BlockState STAR = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    /** Night's floor; the path of light to the door once it's won. */
    static final BlockState NIGHT = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
    static final BlockState NIGHT_SEAM = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    static final BlockState PATH = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    /** The light the eye can't see: bright by day, dim at night. */
    static final int DAY_LIGHT = 13, NIGHT_LIGHT = 4;

    public static BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(AT_X, 0, AT_Z);
    }

    public static boolean spans(int x, int z) {
        return Math.abs(x) <= WALL && z >= NORTH && z <= BACK;
    }

    static double r(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /** A spot's angle round the clock, in degrees from north (12) round through east (3). */
    static double clock(int x, int z) {
        return Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(x, -z)) * 10), 3600) / 10.0;
    }

    /** The twelfth of the clock a spot is in: 0 from 12 to 1, round through the east. */
    static int hour(int x, int z) {
        return (int) (clock(x, z) / 30) % 12;
    }

    /** How far a spot is to the side of the line from the middle out through mark {@code k} (and whether it's outward). */
    private static double offMark(int k, int x, int z) {
        double a = Math.toRadians(k * 30);
        double ux = Math.sin(a), uz = -Math.cos(a);
        if (x * ux + z * uz < 0) return 99;
        return Math.abs(x * uz - z * ux);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73428767 ^ y * 912931 ^ z * 19349663;
        h ^= h >>> 13;
        return h * 0x5bd1e995 ^ h >>> 15;
    }

    public static @Nullable BlockState block(int x, int dy, int z) {
        if (dy < LOW || dy > ROOF || !spans(x, z)) return null;
        double r = r(x, z);
        if (z <= DOOR_Z && Math.abs(x) <= 9) {
            BlockState d = doorway(x, dy, z);
            if (d != null || z < DOOR_Z) return d;
        }
        if (z >= ARCH_Z - 2 && Math.abs(x) <= 4 && r >= R) return wayOut(x, dy, z, r);
        if (z > DOOR_Z && z < 0 && r >= R && dy >= 0 && dy <= WALL_TOP) {
            // the round opening through the wall to the Seed's door, a chiselled rim round it
            double d = doorR(x, dy);
            if (d < DOOR_RINGS[4]) return dy == 0 ? QUARTZ : null;
            if (d < DOOR_RINGS[4] + 1) return CHISELED;
        }
        if (dy == ROOF || dy == CEIL - 1) return r < WALL_OUT ? ceiling(x, dy, z, r) : null;
        if (r < R) return floor(x, dy, z, r);
        if (r < WALL_OUT) return wall(x, dy, z, r);
        return null;
    }

    // ---- the floor ---------------------------------------------------------------------------------------------------

    private static @Nullable BlockState floor(int x, int dy, int z, double r) {
        if (dy > 0) {
            // light the eye can't see, on a 6-block grid
            if (dy == 4 && Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3 && r < R - 2) return lightOf(DAY_LIGHT);
            return null;
        }
        if (dy == 0) return face(x, z, r);
        // the inner disc's root narrows under it; the outer ring is a thin plate (it falls away)
        if (r >= RING_IN) return dy == -1 ? BRICKS : null;
        return r < RING_IN - 2.2 * (-dy - 1) ? (dy == -1 ? BRICKS : CALCITE) : null;
    }

    /**
     * The clock's face: white quartz, a gold boss with a light in it, the Loop (a ring of gold at 8), the dial between it
     * and the seam in twelve sectors of quartz and calcite, the seam (quartz bricks at 16, where the outer ring breaks),
     * and twelve gold marks out to the rim, those at 12, 3, 6 and 9 twice as wide.
     */
    static BlockState face(int x, int z, double r) {
        if (r < 0.8) return CORE;
        if (r < 1.8) return GOLD;
        if (Math.abs(r - LOOP_R) < 0.5) return GOLD;
        if (r > LOOP_R + 0.5 && r < RING_IN - 0.5) return hour(x, z) % 2 == 0 ? QUARTZ : CALCITE;
        if (Math.abs(r - RING_IN) < 0.5) return BRICKS;
        if (r >= 18 && r < 21.5) {
            for (int k = 0; k < 12; k++) if (offMark(k, x, z) < (k % 3 == 0 ? 1.1 : 0.6)) return GOLD;
        }
        return QUARTZ;
    }

    /** The same spot at night: the white goes black, the gold stays, the boss still lit. */
    static BlockState night(int x, int z, double r) {
        BlockState day = face(x, z, r);
        if (day == GOLD || day == CORE) return day;
        return day == BRICKS ? NIGHT_SEAM : NIGHT;
    }

    // ---- the wall, its windows, the ceiling --------------------------------------------------------------------------

    private static @Nullable BlockState wall(int x, int dy, int z, double r) {
        if (dy < 0) return dy >= LOW ? CALCITE : null;
        boolean face = r < R + 1;
        int near = (int) Math.round(clock(x, z) / 30) % 12;
        double off = offMark(near, x, z);
        // a pilaster at each mark, running up to the ceiling
        if (face && off < 1.3) return dy == WALL_TOP ? CHISELED : PILLAR;
        if (dy == 0 || dy == WALL_TOP) return CHISELED;
        if (face && (dy == 1 || dy == 12)) return GOLD;
        // a tall lit window between the marks: white glass, the light behind it
        double mid = Math.abs(((clock(x, z) + 15) % 30) - 15);
        double across = Math.toRadians(mid) * r;
        int half = (int) (clock(x, z) / 30);
        boolean door = half == 11 || half == 0, arch = half == 5 || half == 6;
        if (!door && !arch && across < 1.6 && dy >= 3 && dy <= 10) {
            if (face) return dy == 10 ? CHISELED : PANE;
            if (r < R + 2) return CORE;
        }
        return CALCITE;
    }

    private static BlockState ceiling(int x, int dy, int z, double r) {
        if (dy == CEIL - 1) {
            // the cornice's lip round the edge; otherwise open up to the ceiling
            return r >= R - 1 ? CHISELED : null;
        }
        if (Math.abs(r - 10.5) < 0.5 || Math.abs(r - 4.5) < 0.4) return GOLD;
        if (r >= R - 1) return CHISELED;
        return Math.floorMod(hash(x, dy, z), 31) == 0 ? STAR : SKY;
    }

    // ---- the Seed's door and the tunnel past it ----------------------------------------------------------------------

    /** How far a spot of the door's plane is from its middle. */
    static double doorR(int x, int dy) {
        return Math.sqrt((double) x * x + (double) (dy - DOOR_DY) * (dy - DOOR_DY));
    }

    /** Which of the door's parts a spot is in: 0 the core, 1-3 its rings, 4 the frame, -1 outside it. */
    static int doorPart(int x, int dy) {
        double d = doorR(x, dy);
        for (int k = 0; k < DOOR_RINGS.length; k++) if (d < DOOR_RINGS[k]) return k;
        return -1;
    }

    /**
     * The door's leaf at a spot, {@code open} parts gone from the middle out (0 shut, 4 open: the frame stays), its
     * rings turned by {@code turn} (eighths of a turn, the middle ring the other way).
     */
    static @Nullable BlockState doorLeaf(int x, int dy, int open, int turn) {
        int part = doorPart(x, dy);
        if (part < 0) return null;
        if (part == 4) return CHISELED;
        if (part < open) return null;
        if (part == 0) return CORE;
        double a = Math.toDegrees(Math.atan2(x, dy - DOOR_DY)) + 360;
        int dir = part == 2 ? -1 : 1;
        int seg = Math.floorMod((int) Math.floor(a / 45.0) + dir * turn + part, 8);
        return seg % 2 == 0 ? GOLD : QUARTZ;
    }

    private static @Nullable BlockState doorway(int x, int dy, int z) {
        double d = doorR(x, dy);
        if (z == DOOR_Z) {
            if (dy < 0) return dy >= LOW ? CALCITE : null;
            if (dy == 0) return QUARTZ;
            BlockState leaf = doorLeaf(x, dy, 0, 0);
            if (leaf != null) return leaf;
            return null; // the wall round it
        }
        // the tunnel to the wall of light: round, white glass with the light behind it, a quartz floor
        int t = DOOR_Z - z; // 1 .. 11
        double rad = 6.5 - t * 0.12;
        if (z == TUNNEL_N) return d < rad + 1.5 && dy >= 0 ? CORE : null;
        if (z < TUNNEL_N) return d < rad + 1.5 && dy >= 0 ? CALCITE : null;
        if (dy == 0) return Math.abs(x) <= 4 ? (x == 0 ? GOLD : QUARTZ) : d < rad + 1.5 ? CALCITE : null;
        if (dy < 0) return Math.abs(x) <= 6 && dy >= -2 ? CALCITE : null;
        if (d < rad) return dy == 3 && x == 0 && t % 3 == 0 ? lightOf(15) : null;
        if (d < rad + 1) return t % 4 == 2 ? PILLAR : GLASS;
        if (d < rad + 1.5) return CORE;
        return null;
    }

    // ---- the way out -------------------------------------------------------------------------------------------------

    private static @Nullable BlockState wayOut(int x, int dy, int z, double r) {
        int ax = Math.abs(x);
        if (dy < 0) return dy >= -2 && ax <= 3 ? CALCITE : null;
        if (z > OUT_Z) return dy == 0 && ax <= 2 ? BRICKS : null; // into the dark
        if (dy == 0) return ax <= 2 ? (ax == 0 ? GOLD : BRICKS) : CALCITE;
        if (r < WALL_OUT) {
            // the arch through the wall
            if (ax <= 2 && dy <= 4 || ax <= 1 && dy == 5) return null;
            if (dy > WALL_TOP) return null;
            return dy == 5 || dy == 6 && ax <= 2 ? CHISELED : CALCITE;
        }
        // the short corridor: low walls, open to the dark
        if (ax == 3 && dy <= 2) return dy == 2 ? CHISELED : CALCITE;
        return null;
    }

    static BlockState lightOf(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    // ---- what changes, worked out once -----------------------------------------------------------------------------

    private static int[][][] HOURS;
    private static int[][][] RING;
    private static int[][] DOOR;

    /** The floor's spots by the hour they're in, {x, z} (night sweeps them in turn). */
    static int[][][] hours() {
        if (HOURS == null) {
            List<List<int[]>> by = new ArrayList<>();
            for (int k = 0; k < 12; k++) by.add(new ArrayList<>());
            for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (r(x, z) < R) by.get(hour(x, z)).add(new int[]{x, z});
            HOURS = pack(by);
        }
        return HOURS;
    }

    /** The outer ring's spots by hour, {x, dy, z} (it falls away a mark at a time). */
    static int[][][] ring() {
        if (RING == null) {
            List<List<int[]>> by = new ArrayList<>();
            for (int k = 0; k < 12; k++) by.add(new ArrayList<>());
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    if (!falls(x, z)) continue;
                    for (int dy = -1; dy <= 0; dy++) by.get(hour(x, z)).add(new int[]{x, dy, z});
                }
            }
            RING = pack(by);
        }
        return RING;
    }

    /** The door's spots, {x, dy}. */
    static int[][] door() {
        if (DOOR == null) {
            List<int[]> out = new ArrayList<>();
            for (int x = -9; x <= 9; x++) for (int dy = 1; dy <= DOOR_DY + 9; dy++) if (doorPart(x, dy) >= 0) out.add(new int[]{x, dy});
            DOOR = out.toArray(new int[0][]);
        }
        return DOOR;
    }

    private static int[][][] pack(List<List<int[]>> by) {
        int[][][] out = new int[by.size()][][];
        for (int k = 0; k < out.length; k++) out[k] = by.get(k).toArray(new int[0][]);
        return out;
    }

    /** Whether a floor spot falls with the outer ring (the causeway from the arch to the middle stays: you wake on it). */
    static boolean falls(int x, int z) {
        double r = r(x, z);
        return r >= RING_IN && r < R && !(z > 0 && Math.abs(x) <= CAUSEWAY);
    }

    // ---- as a hall ---------------------------------------------------------------------------------------------------

    /** The band a fight changes: the floor and its plate, the lights, the Seed's door. */
    static final int BAND_LO = -1, BAND_HI = 16;
    static final int QUIET = Block.UPDATE_CLIENTS;

    static final Hall HALL = new Hall() {
        @Override public int atX() { return AT_X; }
        @Override public int atZ() { return AT_Z; }
        @Override public int wall() { return WALL; }
        @Override public int north() { return NORTH; }
        @Override public int back() { return BACK; }
        @Override public int low() { return LOW; }
        @Override public int roof() { return ROOF; }
        @Override public int bandLo() { return BAND_LO; }
        @Override public int bandHi() { return BAND_HI; }
        @Override public @Nullable BlockState block(int x, int dy, int z) { return Antechamber.block(x, dy, z); }

        @Override
        public boolean standing(ServerLevel level, BlockPos c) {
            return is(level, c, 0, 0, 0) && is(level, c, 18, 0, 6) && is(level, c, 0, DOOR_DY, DOOR_Z) && is(level, c, 0, 0, WAKE_Z)
                    && is(level, c, 0, CEIL, 0) && is(level, c, 0, 0, TUNNEL_N);
        }
    };
}
