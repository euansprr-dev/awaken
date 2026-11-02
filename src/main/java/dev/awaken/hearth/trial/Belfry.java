package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * The Drowned Belfry (SPEC §46.4): the first Proving's hall, a flooded nave of hanging bells. The trials dimension's
 * generator builds it in every hall (even) cell, centred {@link #AT} blocks into the cell from its north-west corner (the
 * box arena keeps the corner). Pure: {@link #block} says what stands at a spot, so worldgen, the hall's repair and tests
 * all read one layout. Local x runs east, z south (the door is south, the Tenor north), dy up from the trials floor.
 * <p>
 * The fight is on the chancel, a dry raised floor down the middle (you stand at dy 1); the aisles either side and the
 * narthex inside the door are knee-deep water over a drowned floor (you stand at dy 0). Fallen bells lie on the chancel
 * as cover. The Great Bell Tenor hangs over the pit at the chancel's north end, sealed under the floor until the water
 * drains. Out of the door a stair climbs to a landing: the way out.
 */
public final class Belfry {
    private Belfry() {}

    /** The hall's centre, this far east and south of its cell's north-west corner. */
    public static final int AT = 256;
    /** The side walls at |x| 31, the end walls at z -52 and 52, the stair's back wall at z 63. */
    public static final int WALL_X = 31, NORTH = -52, SOUTH = 52, BACK = 63;
    public static final int LOW = -9, ROOF = 44;
    /** The chancel: the dry raised floor the fight is on. */
    public static final int CHANCEL_X = 14, CHANCEL_N = -51, CHANCEL_S = 34;
    /** The pit under the Tenor: its centre, radius and depth. */
    public static final int PIT_Z = -38, PIT_R = 7, PIT_DEEP = 6;
    /** Where the Ringer drops into the light. */
    public static final int DROP_Z = -6;
    /** Where you wake: in the water inside the door, facing the chancel. */
    public static final int WAKE_Z = 48;
    /** The stair's top (you stand at dy 4): on it you've climbed out. */
    public static final int LANDING_Z = 58, LANDING_DY = 4;

    static final int[] PILLARS = {-44, -32, -20, -8, 4, 16, 28};
    static final int PILLAR_X = 17;
    /** Fallen bells on the chancel (x, z, which way the mouth faces: 0 south, 1 north, 2 east, 3 west), clear of the drop. */
    static final int[][] COVER = {{-8, -10, 2}, {9, 4, 3}, {-6, 16, 0}, {7, -24, 1}, {11, -14, 3}};
    /** Bells sunk in the aisles, the same shape, lying in the water. */
    static final int[][] SUNK = {{24, -30, 1}, {-23, 12, 0}};
    /** Bells hanging from the roof: x, z, size (2 or 3), the lip's dy. */
    static final int[][] HUNG = {{-23, -26, 3, 16}, {23, -2, 3, 19}, {-23, 22, 3, 15}, {23, 24, 2, 21}, {8, -16, 2, 23},
            {-9, 8, 2, 20}, {0, 20, 3, 26}, {-24, -46, 2, 18}, {24, -44, 3, 22}};
    /** The Tenor's lip, over the pit. */
    static final int TENOR_LIP = 12;

    private static final BlockState BRICK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED_BRICK = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState CRACKED_TILE = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    private static final BlockState DARK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState IRON = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState TREAD = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState GLASS = Blocks.DARK_PRISMARINE.defaultBlockState();
    private static final BlockState SEA = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    private static final BlockState SOUL = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState WARM = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState STEP = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    private static final BlockState WET_STEP = STEP.setValue(SlabBlock.WATERLOGGED, true);
    private static final BlockState PEW = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
            .setValue(StairBlock.HALF, Half.BOTTOM).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT).setValue(StairBlock.WATERLOGGED, true);
    // the bells: bronze (exposed copper), its verdigris (weathered) and an inscribed band; waxed, so they keep their colour
    private static final BlockState BRONZE = Blocks.COPPER_BLOCK.waxed().exposed().defaultBlockState();
    private static final BlockState VERDIGRIS = Blocks.COPPER_BLOCK.waxed().weathered().defaultBlockState();
    private static final BlockState BAND = Blocks.CUT_COPPER.waxed().exposed().defaultBlockState();
    private static final BlockState GREEN_BAND = Blocks.CUT_COPPER.waxed().weathered().defaultBlockState();
    private static final BlockState WORDS = Blocks.CHISELED_COPPER.waxed().exposed().defaultBlockState();

    /** The hall's centre (dy 0 at the trials floor) in cell {@code cell}. */
    public static BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(AT, 0, AT);
    }

    /** Whether a column (local) is the Belfry's at all. */
    public static boolean spans(int x, int z) {
        return Math.abs(x) <= WALL_X && z >= NORTH && z <= BACK;
    }

    /** The block at a local spot, or null for air. */
    public static @Nullable BlockState block(int x, int dy, int z) {
        int ax = Math.abs(x);
        if (dy < LOW || dy > ROOF || !spans(x, z)) return null;
        if (z > SOUTH) return stair(ax, dy, z);
        if (dy == ROOF) return TILE;
        if (ax == WALL_X || z == NORTH || z == SOUTH) return wall(x, ax, dy, z);
        double pr = pitR(x, z);
        if (dy < -2 || (dy == -2 && pr <= PIT_R)) {
            BlockState in = pr <= PIT_R + 1 ? pit(pr, dy) : null;
            return in != null ? in : dy == -4 && x == 0 && z == PIT_Z ? lightOf(12) : null; // the pit's own light, seen once it opens
        }
        if (dy == -2) return BRICK;
        BlockState pillar = pillar(ax, dy, z);
        if (pillar != null) return pillar;
        boolean chancel = chancel(x, z);
        if (dy <= 0) {
            if (chancel) return dy == -1 ? BRICK : floor(x, z, pr);
            if (dy == -1) return drownedFloor(x, ax, z);
            if (step(x, z)) return WET_STEP;
            BlockState sunk = fallenAt(SUNK, x, dy, 0, z);
            if (sunk != null) return sunk;
            if (z >= CHANCEL_S + 4 && z <= 44 && (z - CHANCEL_S - 4) % 4 == 1 && ax >= 4 && ax <= 12) return PEW;
            return WATER;
        }
        if (dy >= 40 && dy <= 41 && ((ax <= WALL_X - 1 && isPillarZ(z)) || ax == PILLAR_X)) return POLISHED; // the ribs
        BlockState bell = tenor(x, dy, z);
        if (bell == null) bell = hung(x, dy, z);
        if (bell != null) return bell;
        // a bracket off each pillar's chancel side, a chain and a soul lantern under it
        if (ax == PILLAR_X - 2 && isPillarZ(z)) {
            if (dy == 7) return POLISHED;
            if (dy == 6) return CHAIN;
            if (dy == 5) return SOUL;
        }
        if (chancel) {
            BlockState cover = fallenAt(COVER, x, dy, 1, z);
            if (cover != null) return cover;
        } else {
            BlockState sunk = fallenAt(SUNK, x, dy, 0, z);
            if (sunk != null) return sunk;
        }
        return light(x, dy, z);
    }

    // ---- the parts --------------------------------------------------------------------------------------------------

    static boolean chancel(int x, int z) {
        return Math.abs(x) <= CHANCEL_X && z >= CHANCEL_N && z <= CHANCEL_S;
    }

    /** The waterlogged half-steps up onto the chancel, along both sides and across its south end. */
    static boolean step(int x, int z) {
        int ax = Math.abs(x);
        return (ax == CHANCEL_X + 1 && z >= CHANCEL_N && z <= CHANCEL_S) || (z == CHANCEL_S + 1 && ax <= CHANCEL_X + 1)
                || (z == SOUTH - 1 && ax <= 2);
    }

    static double pitR(int x, int z) {
        return Math.sqrt(x * x + (double) (z - PIT_Z) * (z - PIT_Z));
    }

    /** The chancel floor over the pit, which gives way when the water drains. */
    public static boolean lid(int x, int z) {
        return chancel(x, z) && pitR(x, z) <= PIT_R;
    }

    private static boolean isPillarZ(int z) {
        for (int p : PILLARS) if (p == z) return true;
        return false;
    }

    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        return (h ^ (h >>> 13)) & 0x7fffffff;
    }

    private static BlockState wall(int x, int ax, int dy, int z) {
        if (z == SOUTH && ax <= 2) {
            if (dy == 0) return POLISHED; // the threshold, a step up out of the water
            if (dy >= 1 && dy <= 3) return null; // the door: you fit, the Ringer doesn't
            if (dy == 4) return CHISELED;
        }
        if (dy <= 0) return DARK;
        // the rose on the north wall, over the Tenor: a ring of dark glass round a sea lantern, eight spokes
        if (z == NORTH) {
            double r = Math.sqrt(x * x + (double) (dy - 27) * (dy - 27));
            if (r <= 1.2) return SEA;
            if (r >= 5.0 && r <= 6.3) return GLASS;
            if (r < 5.0 && (x == 0 || dy == 27 || Math.abs(x) == Math.abs(dy - 27))) return GLASS;
        }
        if (dy == 12 || dy == 30) return TILE;
        boolean pilaster = ax == WALL_X ? isPillarZ(z) || isPillarZ(z - 1) || isPillarZ(z + 1) : x % 8 == 0;
        if (pilaster) return POLISHED;
        return hash(x + dy * 31, z) % 7 == 0 ? CRACKED_BRICK : BRICK;
    }

    private static @Nullable BlockState stair(int ax, int dy, int z) {
        if (ax > 3 || dy < -2 || dy > 9) return null;
        if (dy == 9) return POLISHED;
        if (z == BACK || ax == 3) return BRICK;
        // half a block a step from the threshold (top 1) to the landing (top 4): slabs at 53, 55, 57
        int top = z >= LANDING_Z ? LANDING_DY - 1 : (z - SOUTH - 1) / 2 + 1;
        boolean half = z < LANDING_Z && (z - SOUTH) % 2 == 1;
        if (dy < top) return BRICK;
        if (dy == top) return half ? STEP : TILE;
        if (dy == 8 && ax == 0 && z == LANDING_Z + 2) return WARM; // the way out is lit warm, the hall cold
        return null;
    }

    private static BlockState floor(int x, int z, double pr) {
        if (pr <= PIT_R) return pr > PIT_R - 1 ? CRACKED_TILE : (pr <= 1.5 ? CHISELED : POLISHED); // the lid's rosette
        if (Math.abs(x) == CHANCEL_X) return DARK;
        if (Math.abs(x) <= 1) return DARK; // the runner down the middle
        if (hash(x, z) % 11 == 0) return CRACKED_TILE;
        return ((x + z) & 1) == 0 ? TILE : POLISHED;
    }

    private static BlockState drownedFloor(int x, int ax, int z) {
        if ((ax == 24 && Math.floorMod(z, 8) == 4) || (z == 41 && (ax == 8 || ax == 20))) return SEA; // light from under the water
        return hash(x, z) % 9 == 0 ? CRACKED_TILE : POLISHED;
    }

    private static @Nullable BlockState pillar(int ax, int dy, int z) {
        if (Math.abs(ax - PILLAR_X) > 1 || dy < -1 || dy > 39) return null;
        for (int p : PILLARS) {
            int dz = z - p;
            if (Math.abs(dz) > 1) continue;
            if (dy <= 0 || dy >= 38) return dy <= 0 ? DARK : POLISHED;
            return ax != PILLAR_X && dz != 0 ? POLISHED : BRICK;
        }
        return null;
    }

    /** The pit under the lid: terraces a block down a ring, a pool at the bottom; solid rock round it. */
    private static @Nullable BlockState pit(double pr, int dy) {
        if (pr > PIT_R) return BRICK;
        int k = Math.max(1, Math.min(PIT_DEEP, PIT_R - (int) Math.floor(pr)));
        int surface = -1 - k;
        if (dy > surface) return null;
        if (dy == surface) return k == PIT_DEEP && pr < 2.0 ? WATER : TREAD;
        return BRICK;
    }

    /** A bell lying on its side, from {@code list} (resting on dy {@code base}): crown, shoulder, hollow body, a flared lip. */
    private static @Nullable BlockState fallenAt(int[][] list, int x, int dy, int base, int z) {
        for (int[] b : list) {
            int dx = x - b[0], dz = z - b[1];
            if (Math.abs(dx) > 2 || Math.abs(dz) > 2) continue;
            // a: along the axis toward the mouth (-2 crown .. 2 lip); p: across it
            int a = switch (b[2]) {
                case 0 -> dz;
                case 1 -> -dz;
                case 2 -> dx;
                default -> -dx;
            };
            BlockState s = fallen(a, b[2] <= 1 ? dx : dz, dy - base);
            if (s != null) return s;
        }
        return null;
    }

    /** The cell of a fallen bell at a height {@code h} above its base, or null. */
    static @Nullable BlockState fallen(int a, int p, int h) {
        int ap = Math.abs(p);
        if (h < 0 || h > 3 || ap > 2) return null;
        return switch (a) {
            case -2 -> ap == 0 && h == 1 ? IRON : null;
            case -1 -> ap <= 1 && h <= 2 ? GREEN_BAND : null;
            case 0, 1 -> ap <= 1 && h <= 2 && !(ap == 0 && h <= 1) ? (a == 0 ? VERDIGRIS : BRONZE) : null;
            case 2 -> (ap == 2 && h <= 2) || (h == 3 && ap <= 1) ? BAND : null;
            default -> null;
        };
    }

    private static @Nullable BlockState hung(int x, int dy, int z) {
        for (int[] b : HUNG) {
            int dx = x - b[0], dz = z - b[1];
            if (Math.abs(dx) > 3 || Math.abs(dz) > 3) continue;
            BlockState s = upright(dx, dy - b[3], dz, b[2] == 2 ? SMALL : MIDDLE, false);
            if (s != null) return s;
        }
        return null;
    }

    private static @Nullable BlockState tenor(int x, int dy, int z) {
        int dz = z - PIT_Z;
        if (Math.abs(x) > 8 || Math.abs(dz) > 8 || dy < TENOR_LIP) return null;
        return upright(x, dy - TENOR_LIP, dz, TENOR, true);
    }

    // rows of an upright bell from the lip up: a radius, and whether the row is hollow (the mouth)
    private static final double[][] SMALL = {{2.0, 1}, {1.5, 1}, {1.5, 0}, {1.0, 0}};
    private static final double[][] MIDDLE = {{3.0, 1}, {2.6, 1}, {2.3, 1}, {2.2, 0}, {1.6, 0}};
    private static final double[][] TENOR = {{7.2, 1}, {6.4, 1}, {6.0, 1}, {5.7, 1}, {5.5, 1}, {5.4, 1}, {5.3, 1}, {5.1, 0},
            {4.6, 0}, {3.8, 0}, {2.6, 0}};

    /** A bell hanging mouth down, its chain (four for the Tenor) running up to the roof, a clapper inside. */
    private static @Nullable BlockState upright(int dx, int h, int dz, double[][] rows, boolean great) {
        if (h < 0) return null;
        double d2 = dx * dx + dz * dz;
        int crown = rows.length;
        if (h >= crown) {
            if (h == crown) return d2 <= (great ? 2.1 : 0.1) ? IRON : null;
            if (h > crown && h < ROOF) {
                boolean hanger = great ? d2 > 0.5 && d2 <= 1.1 : d2 < 0.1;
                return hanger ? CHAIN : null;
            }
            return null;
        }
        double r = rows[h][0];
        boolean hollow = rows[h][1] > 0;
        if (d2 > (r + 0.3) * (r + 0.3)) return null;
        if (hollow && d2 <= (r - 0.7) * (r - 0.7)) {
            // the clapper: a chain from the shoulder down the middle, its weight at the lip
            if (d2 < 0.1) return h == 0 ? IRON : CHAIN;
            if (great && h == 0 && d2 <= 2.1) return IRON;
            return null;
        }
        if (h == 0 || h == 1) return BAND;
        if (great && h == 4) return WORDS;
        return h >= crown - 2 ? VERDIGRIS : BRONZE;
    }

    /** Light the eye can't see: a pool over where the Ringer drops, a softer fill down the chancel. */
    private static @Nullable BlockState light(int x, int dy, int z) {
        // the chancel is lit to read a fight by: 14 at head height on a 5-block grid, its floor never darker than 6 (the
        // first tour's hall, lit from 8 up, was black where you fight); a pool of 15 where it drops; the narthex at 11
        if (dy == 5 && x == 0 && z == DROP_Z) return lightOf(15);
        if (dy == 4 && Math.abs(x) <= 10 && Math.floorMod(x, 5) == 0 && z >= CHANCEL_N + 2 && z <= CHANCEL_S - 3
                && Math.floorMod(z - CHANCEL_N - 2, 5) == 0) return lightOf(14);
        if (dy == 4 && x == 0 && z == WAKE_Z - 4) return lightOf(11);
        return null;
    }

    private static BlockState lightOf(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    // ---- water ------------------------------------------------------------------------------------------------------

    /** Whether a block holds the hall's water (the drain takes it). */
    public static boolean wet(@Nullable BlockState s) {
        if (s == null) return false;
        if (s.is(Blocks.WATER)) return true;
        return s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);
    }

    /** The same block once the water's gone. */
    public static BlockState dry(BlockState s) {
        if (s.is(Blocks.WATER)) return Blocks.AIR.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.WATERLOGGED)) return s.setValue(BlockStateProperties.WATERLOGGED, false);
        return s;
    }

    // ---- as a hall ----------------------------------------------------------------------------------------

    /**
     * The Belfry as a {@link Hall}: what the Provings and the trials' generator read. It stands as built where a run would
     * see it first: the chancel, the door's water and a side wall (a hall cell's chunks made before the Belfry are void).
     */
    static final Hall HALL = new Hall() {
        @Override public int atX() { return AT; }
        @Override public int atZ() { return AT; }
        @Override public int wall() { return WALL_X; }
        @Override public int north() { return NORTH; }
        @Override public int back() { return BACK; }
        @Override public int low() { return LOW; }
        @Override public int roof() { return ROOF; }
        @Override public int bandLo() { return -1; }
        @Override public int bandHi() { return 4; }
        @Override public @Nullable BlockState block(int x, int dy, int z) { return Belfry.block(x, dy, z); }

        @Override
        public boolean standing(ServerLevel level, BlockPos c) {
            return is(level, c, 0, 0, 0) && is(level, c, 0, 0, WAKE_Z) && is(level, c, WALL_X, 10, 0) && is(level, c, 0, 0, PIT_Z);
        }
    };

    /** Whether the fluid at a spot is the hall's still water (for tests). */
    public static boolean water(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(Fluids.WATER);
    }
}
