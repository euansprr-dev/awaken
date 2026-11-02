package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Court's threshold (SPEC §46.9): the fifth Proving's hall. A round court of black stone under a dark dome, its floor
 * white marble inlaid with a black star and two rings of gold, ringed by eight shut doors, one for each Sovereign of
 * the Hollow Court, each with its own light showing through its keyhole. North stands the Court's own door, banded in
 * gold, and past it a stair goes down into the dark; south is the stair you come down. Built by the trials' generator in
 * every hall cell at {@link #AT_X}, {@link #AT_Z}. Pure: {@link #block} says what stands at a spot. Local x runs east,
 * z south, dy up from the trials floor (you stand at dy 1).
 * <p>
 * At three quarters of the Keeper's Shadow's health the eight doors sink into the floor; at half the dark comes in
 * through them over the marble as far as the inner ring of gold; at a quarter the Court's door begins to slide open.
 */
public final class Threshold {
    private Threshold() {}

    /** The court's centre, this far east and south of its cell's north-west corner (west of the Belfry). */
    public static final int AT_X = 110, AT_Z = 256;
    /** The court's floor to r 24; its wall to 27, 20 high; the dome over it to dy 36. */
    public static final int R = 24, WALL_OUT = 27, WALL_TOP = 20;
    /** The inner ring of gold: inside it the dark never comes. */
    public static final double LIGHT_R = 10.5;
    /** The doors' passages end here (along each door's axis), its Sovereign's light behind coloured glass. */
    static final int PASS_END = 33;
    /** The Court's door (north): its opening |x| <= 4, its two leaves at these z. */
    public static final int GD_HALF = 4, LEAF_S = -(R + 1), LEAF_N = -(R + 2);
    /** The vestibule past it to z -33; the steps down from z -34, one a block, 24 of them into the dark. */
    static final int VEST_N = -33, STEPS = 24;
    /** The way in (south): the stair up from the wall to the landing at dy 12, the arch out at its back. */
    static final int STAIR_S = R + 2, LANDING = 12, ARCH_Z = R + 23;
    public static final int WALL = 35, NORTH = VEST_N - STEPS - 2, BACK = ARCH_Z + 2, LOW = -STEPS - 3, ROOF = 37;
    /** Where you wake (on the landing, facing the court) and past which you've gone out (through the arch behind you). */
    public static final int WAKE_Z = R + 18, WAKE_DY = LANDING + 1, OUT_Z = ARCH_Z + 1;
    /** Down the Court's steps past this: gone out (open only once its door is). */
    public static final int OUT_N = VEST_N - 6;

    private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState BASE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState CAPITAL = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState LEAF = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState POST = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
    private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();
    private static final BlockState SOUL_LAMP = Blocks.SOUL_LANTERN.defaultBlockState();
    static final BlockState FIRE = Blocks.SOUL_CAMPFIRE.defaultBlockState();
    static final BlockState DARK = Blocks.SCULK.defaultBlockState();

    /** The eight Sovereigns' doors, from east-south-east round by south: Hunger, Silence, Sleep, Machines, Mirrors, Storms,
     * Roots, Ash; each its colour in the keyhole and the passage, and the light behind it. */
    private static final DyeColor[] COLOUR = {DyeColor.RED, DyeColor.WHITE, DyeColor.PURPLE, DyeColor.ORANGE, DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BROWN};
    private static final Block[] GLOW = {Blocks.SHROOMLIGHT, Blocks.SEA_LANTERN, Blocks.PEARLESCENT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT,
            Blocks.SEA_LANTERN, Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.SHROOMLIGHT};
    private static final double[] COS = new double[8], SIN = new double[8];

    static {
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            COS[k] = Math.cos(a);
            SIN[k] = Math.sin(a);
        }
    }

    /** The braziers round the court, on the star's rays between the doors (none north or south), {x, z}. */
    static final int[][] BRAZIERS = {{19, 0}, {13, 13}, {-13, 13}, {-19, 0}, {-13, -13}, {13, -13}};

    public static BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(AT_X, 0, AT_Z);
    }

    public static boolean spans(int x, int z) {
        return Math.abs(x) <= WALL && z >= NORTH && z <= BACK;
    }

    static double r(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 83492791 ^ z * 19349663;
        return (h ^ (h >>> 13)) & 0x7fffffff;
    }

    /** The Sovereign door nearest a spot by its bearing from the middle (0..7). */
    static int door(int x, int z) {
        double a = Math.toDegrees(Math.atan2(z, x));
        return Math.floorMod((int) Math.round((a - 22.5) / 45.0), 8);
    }

    /** Along door k's axis, out from the middle. */
    static double u(int k, int x, int z) {
        return x * COS[k] + z * SIN[k];
    }

    /** Across door k's axis. */
    static double v(int k, int x, int z) {
        return -x * SIN[k] + z * COS[k];
    }

    /** The top of a Sovereign door's opening |v| from its middle (a round head). */
    static int doorTop(double av) {
        return av <= 1.5 ? 8 : 7;
    }

    /** The Court's door's opening's top |x| from its middle. */
    static int gdTop(int ax) {
        return ax <= 2 ? 15 : ax == 3 ? 14 : 13;
    }

    /** The block at a local spot, or null for air. */
    public static @Nullable BlockState block(int x, int dy, int z) {
        if (dy < LOW || dy > ROOF || !spans(x, z)) return null;
        double r = r(x, z);
        if (dy > WALL_TOP) return r < WALL_OUT ? dome(x, dy, z, r) : null;
        if (r < R) return court(x, dy, z, r);
        int ax = Math.abs(x);
        if (ax <= 6 && z < 0) return great(x, dy, z, r);
        if (ax <= 6 && z > 0) return wayIn(x, dy, z, r);
        if (r < WALL_OUT) return wall(x, dy, z, r);
        return passage(x, dy, z);
    }

    // ---- the court -----------------------------------------------------------------------------------------------------

    private static @Nullable BlockState court(int x, int dy, int z, double r) {
        if (dy >= 1) {
            for (int[] b : BRAZIERS) {
                if (b[0] != x || b[1] != z) continue;
                return dy == 1 ? CHISELED : dy == 2 ? GILDED : dy == 3 ? FIRE : null;
            }
            // light the eye can't see, on a 6-block grid: the court is dim, never dark
            if (dy == 4 && Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3 && r < R - 2) return lightOf(11);
            return null;
        }
        if (dy == 0) return floor(x, z, r);
        if (dy == -1) return POLISHED;
        return dy >= -4 ? BASE : null;
    }

    /**
     * The court's floor: white marble with a black eight-pointed star whose rays point between the doors, a gold boss in
     * the middle, a ring of gold at 10 (the dark's edge), a black band at 17, a chequered ring and a black margin.
     */
    static BlockState floor(int x, int z, double r) {
        if (r < 1.5) return CHISELED;
        if (r < 2.5) return GOLD;
        if (r >= LIGHT_R - 1 && r < LIGHT_R) return GOLD;
        if (r >= 16.5 && r < 18) return BRICKS;
        if (r >= 22) return BRICKS;
        if (r >= 18) return ((x + z) & 1) == 0 ? QUARTZ : POLISHED;
        double a = Math.atan2(z, x), step = Math.PI / 4, off = a - Math.round(a / step) * step;
        if (Math.abs(r * Math.sin(off)) < 0.5) return POLISHED;
        return QUARTZ;
    }

    // ---- the wall and its eight doors ----------------------------------------------------------------------------------

    /** Whether a spot of the wall's inner face is one of its white pilasters (between the doors; they run on up the ribs). */
    private static boolean pilaster(int x, int z, double r) {
        double a = Math.atan2(z, x), step = Math.PI / 4, off = a - Math.round(a / step) * step;
        return r * Math.abs(Math.sin(off)) < 1.1;
    }

    /** Door k's leaf at a spot (its sinking shifts the pattern down: {@code dy} is the leaf's own row). */
    static BlockState leaf(int k, double av, int dy) {
        // a keyhole of its Sovereign's glass: the round head, the slot under it
        if ((av < 0.5 && dy >= 3 && dy <= 6) || (av < 1.5 && dy == 6)) return glass(k);
        return dy == 1 || dy == doorTop(av) ? TILES : LEAF;
    }

    static BlockState glass(int k) {
        return Blocks.STAINED_GLASS.pick(COLOUR[k]).defaultBlockState();
    }

    /** Whether a spot is in a Sovereign door's leaf (in the wall's second course, inside the opening): its door, or -1. */
    static int leafOf(int x, int dy, int z) {
        double r = r(x, z);
        if (dy < 1 || r < R || r >= WALL_OUT || Math.abs(x) <= 6) return -1; // the Court's door and the way in are their own
        int k = door(x, z);
        double u = u(k, x, z), av = Math.abs(v(k, x, z));
        return av <= 2.5 && dy <= doorTop(av) && u >= R + 0.5 && u < R + 2.0 ? k : -1;
    }

    private static @Nullable BlockState wall(int x, int dy, int z, double r) {
        if (dy < -4) return null;
        if (dy < 0) return dy == -1 ? POLISHED : BASE;
        int k = door(x, z);
        double u = u(k, x, z), av = Math.abs(v(k, x, z));
        boolean face = r < R + 1;
        if (av <= 2.5) {
            if (dy == 0) return BRICKS; // the threshold
            if (dy <= doorTop(av)) return u >= R + 0.5 && u < R + 2.0 ? leaf(k, av, dy) : null;
            if (face && dy == doorTop(av) + 1) return av < 0.5 ? GOLD : CHISELED; // the head, its keystone gold
        } else if (face && av <= 3.5 && dy >= 1 && dy <= 9) {
            return CHISELED; // the jambs
        }
        if (dy == 0) return BRICKS;
        if (face) {
            if (pilaster(x, z, r)) return dy == 1 || dy >= WALL_TOP - 1 ? CAPITAL : PILLAR;
            if (dy == 1) return POLISHED;
            if (dy == 10) return GILDED;
            if (dy >= WALL_TOP - 1) return CHISELED;
        }
        return Math.floorMod(hash(x, dy, z), 9) == 0 ? CRACKED : BRICKS;
    }

    /** A Sovereign's passage behind its door: six deep, its end coloured glass with the light behind. */
    private static @Nullable BlockState passage(int x, int dy, int z) {
        int k = door(x, z);
        double u = u(k, x, z), av = Math.abs(v(k, x, z));
        if (u < WALL_OUT - 1.5 || u >= PASS_END + 1 || av > 3.5 || dy < -2 || dy > 9) return null;
        if (dy < 0) return dy == -1 ? POLISHED : BASE;
        if (u >= PASS_END) return av <= 2.5 && dy >= 1 && dy <= 8 ? GLOW[k].defaultBlockState() : BRICKS;
        if (dy == 0) return av <= 2.5 ? POLISHED : BRICKS;
        if (av > 2.5 || dy == 9) return BRICKS;
        if (u >= PASS_END - 1) return glass(k);
        return null;
    }

    // ---- the Court's door, its vestibule and the steps down -------------------------------------------------------------

    /** The Court's door's leaves, {@code s} steps open (0 shut, 4 open): each leaf slides out into the wall, the gold bar first. */
    static @Nullable BlockState gdLeaf(int x, int dy, int s) {
        if (x == 0) return s == 0 ? GOLD : null;
        int from = x < 0 ? x + s : x - s; // the leaf's own column now at x
        if ((x < 0 && from > -1) || (x > 0 && from < 1)) return null;
        int ax = Math.abs(from);
        if (dy == 1) return GILDED;
        if (dy == 4 || dy == 11) return GOLD;
        if (ax + Math.abs(dy - 8) == 3) return GOLD; // the star, its halves parting
        return BRICKS;
    }

    private static @Nullable BlockState great(int x, int dy, int z, double r) {
        int ax = Math.abs(x);
        if (z > -WALL_OUT) {
            if (dy < -4) return null;
            if (dy < 0) return dy == -1 ? POLISHED : BASE;
            if (dy == 0) return BRICKS;
            int top = gdTop(ax);
            if (ax <= GD_HALF && dy <= top) return z == LEAF_S || z == LEAF_N ? gdLeaf(x, dy, 0) : null;
            if (z > -(R + 1)) {
                if (ax <= GD_HALF && dy == top + 1) return ax == 0 ? GOLD : CAPITAL;
                if (ax > GD_HALF && dy <= 16) return dy == 16 ? CAPITAL : PILLAR;
            }
            return wall(x, dy, z, r);
        }
        if (z >= VEST_N) {
            // the vestibule: lit warm, a gilded runner up its middle
            if (dy < -2) return null;
            if (dy < 0) return BASE;
            if (dy == 0) return ax == 0 ? GILDED : ax <= 4 ? POLISHED : BRICKS;
            if (ax >= 5) return dy < 17 ? BRICKS : dy == 17 ? CHISELED : null;
            if (dy == 16) return BRICKS;
            if (dy == 6 && ax == 0 && Math.floorMod(z, 3) == 0) return lightOf(15);
            return null;
        }
        // the steps down into the dark, a lamp on a post every sixth
        int k = VEST_N - 1 - z, top = -(k + 1);
        if (k >= STEPS || ax > 5 || dy > top + 4 || dy < top - 2) return null;
        if (dy <= top) return ax <= 4 && dy < top ? BASE : BRICKS;
        if (ax < 5) return null;
        if (dy <= top + 2) return dy == top + 2 ? CHISELED : BRICKS;
        if (k % 6 == 3) return dy == top + 3 ? POST : LAMP;
        return null;
    }

    // ---- the way in --------------------------------------------------------------------------------------------------

    private static @Nullable BlockState wayIn(int x, int dy, int z, double r) {
        int ax = Math.abs(x);
        if (z < WALL_OUT) {
            if (dy < -4) return null;
            if (dy < 0) return dy == -1 ? POLISHED : BASE;
            if (dy == 0) return BRICKS;
            int top = ax <= 1 ? 9 : ax == 2 ? 8 : 7;
            if (ax <= 3 && dy <= top) return null;
            if (z < R + 1) {
                if (ax <= 3 && dy == top + 1) return ax == 0 ? GOLD : CAPITAL;
                if (ax == 4 && dy <= 9) return PILLAR;
            }
            return wall(x, dy, z, r);
        }
        if (z > ARCH_Z) return dy == LANDING && ax <= 2 ? POLISHED : null; // through the arch, into the dark
        if (ax > 5) return null;
        if (z == ARCH_Z) {
            // the landing's back wall, an arch through it: the way out
            if (dy < LANDING - 2 || dy > LANDING + 8) return null;
            if (ax <= 2 && dy > LANDING && dy <= LANDING + (ax <= 1 ? 5 : 4)) return null;
            if (dy == LANDING && ax <= 2) return POLISHED;
            return dy == LANDING + 8 ? CHISELED : BRICKS;
        }
        int fl = Math.max(0, Math.min(LANDING, z - STAIR_S));
        if (dy < fl - 2 || dy > fl + 6) return null;
        if (ax <= 3) {
            if (dy <= fl) return dy == fl ? BRICKS : BASE;
            if (dy == fl + 3 && ax == 0 && Math.floorMod(z, 4) == 0) return lightOf(9);
            return null;
        }
        if (dy <= fl + 4) return BRICKS;
        if (dy == fl + 5) return CHISELED;
        if (ax == 4 && Math.floorMod(z, 4) == 2) return SOUL_LAMP;
        return null;
    }

    // ---- the dome ----------------------------------------------------------------------------------------------------

    /** A dark dome over the court, eight white ribs running up from the pilasters to a ring of gold round a glass eye. */
    private static @Nullable BlockState dome(int x, int dy, int z, double r) {
        double h = dy - WALL_TOP;
        if (sq(r / 27.0) + sq(h / 16.5) > 1) return null;
        if (sq(r / 25.0) + sq(h / 14.5) <= 1) return null;
        if (r < 3.5) return GLASS;
        if (r < 4.5) return GOLD;
        double a = Math.atan2(z, x), step = Math.PI / 4, off = a - Math.round(a / step) * step;
        if (r * Math.abs(Math.sin(off)) < 0.8) return QUARTZ;
        return Math.floorMod(hash(x, dy, z), 5) == 0 ? BRICKS : TILES;
    }

    private static double sq(double d) {
        return d * d;
    }

    static BlockState lightOf(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    static BlockState fire(boolean lit) {
        return FIRE.setValue(CampfireBlock.LIT, lit);
    }

    // ---- what changes, worked out once -----------------------------------------------------------------------------------

    private static int[][][] LEAVES;
    private static int[][][] DARK_BANDS;

    /** Each Sovereign door's leaf spots, {x, dy, z, |v| * 10}. */
    static int[][][] leaves() {
        if (LEAVES == null) {
            List<List<int[]>> by = new ArrayList<>();
            for (int k = 0; k < 8; k++) by.add(new ArrayList<>());
            for (int x = -WALL_OUT; x <= WALL_OUT; x++) {
                for (int z = -WALL_OUT; z <= WALL_OUT; z++) {
                    for (int dy = 1; dy <= 8; dy++) {
                        int k = leafOf(x, dy, z);
                        if (k >= 0) by.get(k).add(new int[]{x, dy, z, (int) Math.round(Math.abs(v(k, x, z)) * 10)});
                    }
                }
            }
            int[][][] out = new int[8][][];
            for (int k = 0; k < 8; k++) out[k] = by.get(k).toArray(new int[0][]);
            LEAVES = out;
        }
        return LEAVES;
    }

    /**
     * The floor the dark takes, in bands by how far each spot is from the nearest door's mouth: the thresholds and the
     * passages first, then across the marble as far as the inner ring of gold. {x, dy, z}.
     */
    static int[][][] darkBands() {
        if (DARK_BANDS == null) {
            List<List<int[]>> bands = new ArrayList<>();
            for (int x = -WALL; x <= WALL; x++) {
                for (int z = -WALL; z <= WALL; z++) {
                    double r = r(x, z);
                    if (r < LIGHT_R) continue;
                    int band;
                    if (r < R) {
                        double best = 99;
                        for (int k = 0; k < 8; k++) best = Math.min(best, Math.hypot(x - R * COS[k], z - R * SIN[k]));
                        band = 1 + (int) best;
                    } else {
                        // a door's threshold or its passage floor
                        if (Math.abs(x) <= 6) continue;
                        int k = door(x, z);
                        double u = u(k, x, z), av = Math.abs(v(k, x, z));
                        if (av > 2.5 || u >= PASS_END || u < R - 0.5) continue;
                        band = 0;
                    }
                    if (block(x, 0, z) == null) continue;
                    while (bands.size() <= band) bands.add(new ArrayList<>());
                    bands.get(band).add(new int[]{x, 0, z});
                }
            }
            int[][][] out = new int[bands.size()][][];
            for (int b = 0; b < out.length; b++) out[b] = bands.get(b).toArray(new int[0][]);
            DARK_BANDS = out;
        }
        return DARK_BANDS;
    }

    // ---- as a hall ---------------------------------------------------------------------------------------------------

    /** The band a fight changes: the floor (the dark), the braziers, the doors (to the Court's door's head). */
    static final int BAND_LO = 0, BAND_HI = 15;
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
        @Override public @Nullable BlockState block(int x, int dy, int z) { return Threshold.block(x, dy, z); }

        @Override
        public boolean standing(ServerLevel level, BlockPos c) {
            return is(level, c, 0, 0, 0) && is(level, c, 23, 4, 10) && is(level, c, 0, 8, LEAF_S) && is(level, c, 0, 35, 0)
                    && is(level, c, 0, LANDING, WAKE_Z);
        }
    };
}
