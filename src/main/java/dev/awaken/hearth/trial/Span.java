package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The Broken Span (SPEC §46.7): the third Proving's hall, an old stone bridge over nothing, broken off at both ends; the
 * land it joined is gone. Its middle is a round bastion on a great pier, the fight's floor; an arm runs north to a
 * ragged end, and one south to a gatehouse, the way out. Built by the trials' generator in every hall cell at
 * {@link #AT_X}, {@link #AT_Z} (clear of the Belfry and the Hall of Reflections). Pure: {@link #block} says what stands
 * at a spot. Local x runs east, z south, dy up from the trials floor (you stand at dy 1).
 * <p>
 * You fall onto the bastion out of the dark. The north arm is the Unmade: its stones lift off and come together, and it
 * stands on the bastion's rim. At two thirds the bastion's outer ring gives way; at a third the Span's lamps go out.
 */
public final class Span {
    private Span() {}

    /** The bastion's centre, this far east and south of its cell's north-west corner. */
    public static final int AT_X = 256, AT_Z = 424;
    /** The deck's half width inside its parapets (at {@link #RAIL}); the cornice under them sticks out one more. */
    public static final int DECK = 4, RAIL = 5;
    /** The bastion: its floor to r 17.5, its parapet on the rim; the outer ring past {@link #FALL_R} gives way at two thirds. */
    public static final double BASTION_R = 17.5, FALL_R = 12.5;
    /** The north arm's ragged end, the south arm's gatehouse (its arch over z 52 to 55) and the broken end past it. */
    public static final int NORTH_END = -66, GATE_N = 50, GATE_S = 57, ARCH_N = 52, ARCH_S = 55, BACK = 64;
    /** Its piers under the arms (z of their middles), each 7 square. */
    static final int[] PIERS = {-52, -32, 32};
    public static final int WALL = 18, LOW = -40, ROOF = 13;
    /** Where the north arm meets the bastion: everything north of this is the Unmade until it stands up. */
    public static final int ARM_N = -17;
    /** Where you wake after a fall: on the south arm, facing the bastion. */
    public static final int WAKE_Z = 46;
    /** Past the gatehouse's arch: you've gone out. */
    public static final int OUT_Z = 58;
    /** Where it lands when it stands up: on the bastion's north rim, facing the door. */
    public static final int LAND_Z = -12;
    /** Where the north arm's stones come together over it (dy). */
    public static final int GATHER_DY = 9;

    private static final BlockState BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState ROAD = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState TUFF_SMOOTH = Blocks.POLISHED_TUFF.defaultBlockState();
    private static final BlockState TUFF_CARVED = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
    private static final BlockState CORE = Blocks.STONE.defaultBlockState();
    private static final BlockState RUBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState CAP = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    private static final BlockState POST = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();
    private static final BlockState HUNG = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);

    /** The bastion's centre (dy 0 at the trials floor) in cell {@code cell}. */
    public static BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(AT_X, 0, AT_Z);
    }

    public static boolean spans(int x, int z) {
        return Math.abs(x) <= WALL && z >= NORTH_END && z <= BACK;
    }

    static double r(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 83492791 ^ z * 19349663;
        return (h ^ (h >>> 13)) & 0x7fffffff;
    }

    /** The block at a local spot, or null for air. */
    public static @Nullable BlockState block(int x, int dy, int z) {
        if (dy < LOW || dy > ROOF || !spans(x, z)) return null;
        if (z >= GATE_N && z <= GATE_S) return gate(x, dy, z);
        double r = r(x, z);
        if (r < BASTION_R) return bastion(x, dy, z, r);
        if (Math.abs(x) <= RAIL + 1) return arm(x, dy, z);
        return null;
    }

    // ---- the bastion -------------------------------------------------------------------------------------------------

    /** Whether a spot is in an arm's opening through the bastion's parapet (north and south). */
    private static boolean opening(int x) {
        return Math.abs(x) <= DECK;
    }

    private static @Nullable BlockState bastion(int x, int dy, int z, double r) {
        boolean rim = r >= BASTION_R - 1;
        if (dy >= 1) {
            if (light(x, dy, z, r) instanceof BlockState l) return l;
            if (!rim || opening(x)) return null;
            if (dy == 1) return Math.floorMod(hash(x, 1, z), 9) == 0 ? CRACKED : BRICK;
            boolean lamp = lampPost(x, z);
            if (dy == 2) return lamp ? CHISELED : CAP;
            if (lamp) return dy == 3 ? POST : dy == 4 ? LAMP : null;
            return null;
        }
        if (dy == 0) return floor(x, z, r);
        if (dy == -1) return BRICK;
        // the corbels: the rim steps in under the floor, then the pier's round body goes down into the dark
        if (dy >= -5) return r < BASTION_R - 1.3 * (-1 - dy) ? (dy == -2 ? TUFF_SMOOTH : BRICK) : opening(x) ? BRICK : null;
        if (r >= 13) return dy >= -9 && opening(x) ? BRICK : null; // the arms' joins, down to their arches' springing
        int bottom = -32 - Math.floorMod(hash(x, 0, z), 7);
        if (dy < bottom) return null;
        if (dy == bottom) return RUBBLE;
        if (r < 11.5) return CORE;
        if (Math.floorMod(-dy, 7) == 0) return ROAD; // a band every seven courses
        // slits round the pier, dark against the stone
        if (dy <= -10 && dy >= -12 && Math.abs(Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x))), 45) - 22) <= 2) return null;
        return Math.floorMod(hash(x, dy, z), 6) == 0 ? CRACKED : BRICK;
    }

    /** Eight lamps on the parapet, one in each eighth (none in the arms' openings). */
    private static boolean lampPost(int x, int z) {
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            if (x == (int) Math.round((BASTION_R - 0.5) * Math.cos(a)) && z == (int) Math.round((BASTION_R - 0.5) * Math.sin(a))) return true;
        }
        return false;
    }

    /**
     * The bastion's floor: a carved stone in the middle, a road ring at 6 with eight roads out to the seam at 12, and the
     * seam itself in tuff; past it, the older, more broken stone that gives way.
     */
    private static BlockState floor(int x, int z, double r) {
        if (r < 1.5) return CHISELED;
        if (Math.abs(r - 6) < 0.5) return ROAD;
        if (r >= FALL_R - 1 && r < FALL_R) return TUFF;
        if (r >= BASTION_R - 1) return TUFF_SMOOTH;
        if (r > 6 && r < FALL_R - 1) {
            double a = Math.atan2(z, x);
            double step = Math.PI / 4;
            double off = a - Math.round(a / step) * step;
            if (Math.abs(r * Math.sin(off)) < 0.5) return ROAD;
        }
        int h = hash(x, 0, z);
        if (r >= FALL_R) return h % 4 == 0 ? CRACKED : h % 7 == 0 ? ANDESITE : BRICK;
        return h % 9 == 0 ? CRACKED : BRICK;
    }

    /** Whether a spot of the bastion falls when its outer ring gives way (the south arm's road across it stays). */
    public static boolean falls(int x, int z) {
        double r = r(x, z);
        return r >= FALL_R && r < BASTION_R && !(opening(x) && z > 0);
    }

    // ---- the arms ----------------------------------------------------------------------------------------------------

    /** Whether a spot of an arm is broken away at its ragged end. */
    private static boolean broken(int x, int dy, int z) {
        int north = z - NORTH_END, south = BACK - z;
        int edge = Math.min(north, south);
        if (edge >= 6) return false;
        return edge < Math.floorMod(hash(x, dy, z), 7) - (dy < -1 ? 1 : 0);
    }

    private static @Nullable BlockState arm(int x, int dy, int z) {
        int ax = Math.abs(x);
        if (broken(x, dy, z)) return null;
        if (dy >= 1) {
            if (light(x, dy, z, 99) instanceof BlockState l) return l;
            if (ax != RAIL) return null;
            boolean lamp = Math.floorMod(z, 8) == 4;
            if (dy == 1) return Math.floorMod(hash(x, 1, z), 9) == 0 ? CRACKED : BRICK;
            if (dy == 2) return lamp ? CHISELED : CAP;
            if (lamp) return dy == 3 ? POST : dy == 4 ? LAMP : null;
            return null;
        }
        if (ax == RAIL + 1) return dy == -1 ? TUFF_SMOOTH : null; // the cornice under the parapets
        if (dy == 0) {
            if (ax <= 1) return ROAD;
            if (ax == RAIL) return TUFF;
            return Math.floorMod(hash(x, 0, z), 7) == 0 ? CRACKED : BRICK;
        }
        if (dy == -1) return BRICK;
        BlockState p = pier(x, dy, z);
        if (p != null) return p;
        return ax <= DECK ? arch(dy, z) : null;
    }

    /** A pier: 7 square, capped in tuff, banded, its foot broken off in the dark. */
    private static @Nullable BlockState pier(int x, int dy, int z) {
        for (int p : PIERS) {
            int dz = Math.abs(z - p), ax = Math.abs(x);
            if (dy == -2 && ax <= 4 && dz <= 4) return TUFF_SMOOTH;
            int half = dy < -24 ? 2 : 3;
            if (ax > half || dz > half) continue;
            int bottom = -30 - Math.floorMod(hash(x, 0, z), 6);
            if (dy < bottom) return null;
            if (dy == bottom) return RUBBLE;
            if (ax < half && dz < half) return CORE;
            if (Math.floorMod(-dy, 6) == 0) return ROAD;
            return Math.floorMod(hash(x, dy, z), 6) == 0 ? CRACKED : BRICK;
        }
        return null;
    }

    /**
     * The gaps the arches span along z, between the supports' faces (the bastion's joins, the piers, the gatehouse's
     * foundation); the first runs out past the last north pier to the broken end, and springs from that pier alone.
     */
    private static final int[][] GAPS = {{NORTH_END - 99, -56}, {-48, -36}, {-28, -18}, {18, 28}, {36, GATE_N - 1}};

    /**
     * The arches between the supports: a round barrel under the deck springing from each face, its ring of stones in
     * tuff. Filled from the deck down to its curve; nothing under the deck over a support but the support.
     */
    private static @Nullable BlockState arch(int dy, int z) {
        for (int[] g : GAPS) {
            if (z < g[0] || z > g[1]) continue;
            boolean open = g[0] < NORTH_END;
            int e = open ? g[1] - z : Math.min(z - g[0], g[1] - z); // blocks from the nearer face
            double h = open ? 6 : (g[1] - g[0]) / 2.0 + 0.5;
            double k = h - Math.min(e, h);
            double under = -2 - h + Math.sqrt(h * h - k * k);
            if (dy < under - 1) return null;
            return dy < under ? TUFF : BRICK; // the arch's ring, then the fill over it
        }
        return null;
    }

    // ---- the gatehouse -----------------------------------------------------------------------------------------------

    /** The height of the arch's opening over the deck at x (a round arch, its crown at dy 6). */
    private static int archOpen(int ax) {
        return ax > DECK ? 0 : (int) Math.round(1 + Math.sqrt(25.0 - ax * ax));
    }

    private static @Nullable BlockState gate(int x, int dy, int z) {
        int ax = Math.abs(x);
        if (ax > 9) return null;
        // its foundation: a pier the gatehouse's width
        if (dy <= -2) {
            int bottom = -22 - Math.floorMod(hash(x, 0, z), 5);
            if (dy < bottom || ax > 8) return null;
            if (dy == bottom) return RUBBLE;
            return Math.floorMod(-dy, 6) == 0 ? ROAD : BRICK;
        }
        if (dy == -1) return BRICK;
        boolean tower = ax >= 6;
        if (dy == 0) return tower ? BRICK : ax <= 1 ? ROAD : BRICK;
        if (tower) {
            if (dy > 11) return null;
            if (dy == 11) return (ax + z) % 2 == 0 ? CAP : null; // crenels
            if (dy == 10) return TUFF_CARVED;
            if (dy == 5 && ax == 6 && (z == ARCH_N - 1 || z == ARCH_S + 1)) return LAMP; // lamps either side of the arch
            if ((ax == 7 || ax == 8) && (z == GATE_N + 1 || z == GATE_S - 1) && dy >= 3 && dy <= 6) return null; // the towers' windows
            return Math.floorMod(-dy, 4) == 0 ? TUFF : BRICK;
        }
        // the deck between the towers: open to the sky but for the arch
        if (z < ARCH_N || z > ARCH_S) return light(x, dy, z, 99) instanceof BlockState l ? l : null;
        if (dy == 6 && ax == 0 && z == ARCH_N + 1) return HUNG; // a warm lamp under the crown: the way out
        if (dy == 7 && ax == 0 && z == ARCH_N + 1) return CHAIN;
        if (dy <= archOpen(ax)) return null;
        if (dy > 10) return null;
        if (dy == 10) return TUFF_CARVED;
        return dy == archOpen(ax) + 1 ? TUFF : BRICK;
    }

    // ---- light -------------------------------------------------------------------------------------------------------

    /**
     * Light the eye can't see, at dy 3: 13 over the bastion on a 6-block grid, 11 down the middle of the arms every 6.
     * When the lamps go out (a third) the bastion's falls to {@link #DARK} and the arms' goes.
     */
    private static @Nullable BlockState light(int x, int dy, int z, double r) {
        if (dy != 3) return null;
        if (r < BASTION_R - 2) return Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3 ? lightOf(13) : null;
        if (r >= BASTION_R && x == 0 && Math.floorMod(z, 6) == 0) return lightOf(11);
        return null;
    }

    /** The bastion's light once the lamps are out: the Unmade and the stars still show. */
    public static final int DARK = 7;

    static BlockState lightOf(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    /** Whether a block is one of the Span's lamps (visible or not): they go out at a third. */
    static boolean lamp(BlockState s) {
        return s.is(Blocks.LANTERN) || s.is(Blocks.LIGHT);
    }

    // ---- as a hall ---------------------------------------------------------------------------------------------------

    /** The band a fight changes: the north arm (its arches down to dy -12), the bastion's ring and the lamps (to dy 4). */
    static final int BAND_LO = -12, BAND_HI = 6;

    static final Hall HALL = new Hall() {
        @Override public int atX() { return AT_X; }
        @Override public int atZ() { return AT_Z; }
        @Override public int wall() { return WALL; }
        @Override public int north() { return NORTH_END; }
        @Override public int back() { return BACK; }
        @Override public int low() { return LOW; }
        @Override public int roof() { return ROOF; }
        @Override public int bandLo() { return BAND_LO; }
        @Override public int bandHi() { return BAND_HI; }
        @Override public @Nullable BlockState block(int x, int dy, int z) { return Span.block(x, dy, z); }

        @Override
        public boolean standing(ServerLevel level, BlockPos c) {
            return is(level, c, 0, 0, 0) && is(level, c, 0, 0, -40) && is(level, c, 0, 10, ARCH_N) && is(level, c, 0, -20, 0);
        }
    };
}
