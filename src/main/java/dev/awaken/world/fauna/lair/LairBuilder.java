package dev.awaken.world.fauna.lair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

/**
 * The nine lairs as code (no template files): each named beast's home, built around its centre with the floor layer at
 * y = 0 (the beast stands at {@link LairStyle#den}). Every chunk the piece touches rebuilds the same blueprint from the
 * same seed and keeps only its own blocks, so every random choice is made before any block is placed and never
 * depends on the terrain. The core disc sits on ground the beardifier flattened; only the scattered ring outside it
 * follows the terrain column by column.
 *
 * <p>Art direction (docs/modules/structures.md, "Lairs"): a home, not a monument. Each has one focal element 5-9 blocks
 * tall that reads from the edge of sight (the uprooted oak, the wyrm's ribcage, the antler trees, the throne's arch, the
 * coiled serpent, the hollow stump, the bower's vault, the horned cairn, the giant toadstools), a sign of the beast at
 * its den (a nest, a bed, a wallow), what it eats or keeps (hay, bones, cocoons, offerings), and one warm light.
 */
public final class LairBuilder {
    private LairBuilder() {}

    /** Where blocks go. Coordinates are relative: x and z from the centre, y from the floor layer. */
    public interface Sink {
        void set(int x, int y, int z, BlockState state);

        /** The terrain's top block in this column, relative to the floor (only asked for columns being placed). */
        int ground(int x, int z);

        /** True when this column belongs to the chunk being built (the others are skipped). */
        boolean here(int x, int z);

        /** {@code state} from y downwards until something solid, at most {@code depth} blocks (foundations). */
        void fillDown(int x, int y, int z, BlockState state, int depth);

        /** W5: natural cave air the lair opens into, relative to its centre (x, y, z), or null (PLAN 8.2, joined lairs). */
        default int @org.jspecify.annotations.Nullable [] joint() {
            return null;
        }

        /** A Pattern Stand (arms, docs/arms-progression/PLAN.md §3.4): a fallen hunter's rack, with a pattern of the land's rank. Sinks that don't place stands ignore it. */
        default void stand(int x, int y, int z, net.minecraft.core.Direction facing, String kind) {}
    }

    /** The blueprint's version, saved on each piece (1 = before the structures redesign). */
    public static final int STYLE = 2;

    public static void build(LairStyle style, long seed, int variant, Sink s) {
        RandomSource r = RandomSource.create(seed);
        switch (style) {
            case WALLOW -> wallow(s, r, seed);
            case GLASS_HOLLOW -> glassHollow(s, r, seed, variant);
            case ANTLER_GROVE -> antlerGrove(s, r, seed, variant);
            case TOAD_THRONE -> toadThrone(s, r, seed);
            case SERPENT_SHRINE -> serpentShrine(s, r, seed);
            case SILK_HOLLOW -> silkHollow(s, r, seed, variant);
            case PETAL_BOWER -> petalBower(s, r, seed);
            case RAMS_CAIRN -> ramsCairn(s, r, seed, variant);
            case SPORE_RING -> sporeRing(s, r, seed);
            case SUNKEN_SHRINE -> DeepLairs.sunkenShrine(s, r, seed, variant);
            case CRYSTAL_NEST -> DeepLairs.crystalNest(s, r, seed, variant);
        }
        // the den itself stays clear: carpets, petals and snow round it, never on it (the sea floor's den is open water)
        if (style.placement != LairStyle.Placement.SEABED && s.here(style.denX, style.denZ)) {
            for (int dy = 0; dy < 4; dy++) s.set(style.denX, style.denY + dy, style.denZ, AIR);
        }
        // a fallen hunter's rack a few blocks from the den, turned to face it: the one who lost to this beast left their pattern
        if (style.placement == LairStyle.Placement.SURFACE) {
            double a = noise(seed, 7, 11) * Math.PI * 2;
            int x = style.denX + (int) Math.round(Math.cos(a) * 6), z = style.denZ + (int) Math.round(Math.sin(a) * 6);
            if (s.here(x, z)) s.stand(x, s.ground(x, z) + 1, z, net.minecraft.core.Direction.getApproximateNearest(style.denX - x, 0, style.denZ - z), "rack");
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** A stable 0..1 value per column (no RandomSource: the same in every chunk whatever is built first). */
    static double noise(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + z * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /** A smoother value: the average of a cell and its neighbours, for patches rather than speckle. */
    static double patch(long seed, int x, int z) {
        double v = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) v += noise(seed, (x + dx) >> 1, (z + dz) >> 1);
        return v / 9.0;
    }

    static double dist(int x, int z) {
        return Math.sqrt(x * x + z * z);
    }

    private static BlockState of(Block b) {
        return b.defaultBlockState();
    }

    /** The floor disc: palette chosen by distance band and patch noise; cleared above, founded below. */
    private interface Floor {
        BlockState at(int x, int z, double d, double n);
    }

    private static void disc(Sink s, int radius, long seed, Floor floor, int clear) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = dist(x, z);
                if (d > radius + 0.4 || !s.here(x, z)) continue;
                BlockState b = floor.at(x, z, d, patch(seed, x, z));
                if (b == null) continue;
                s.set(x, 0, z, b);
                s.fillDown(x, -1, z, b.is(Blocks.WATER) ? of(Blocks.MUD) : of(Blocks.DIRT), 4);
                for (int y = 1; y <= clear; y++) s.set(x, y, z, AIR);
            }
        }
    }

    private static void column(Sink s, int x, int z, int y0, int height, BlockState state) {
        if (!s.here(x, z)) return;
        for (int y = y0; y < y0 + height; y++) s.set(x, y, z, state);
    }

    private static void put(Sink s, int x, int y, int z, BlockState state) {
        if (s.here(x, z)) s.set(x, y, z, state);
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState leaves(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    /** A rough ball of stone sitting on the floor. */
    private static void boulder(Sink s, int cx, int cz, double rad, long seed, BlockState... palette) {
        int R = (int) Math.ceil(rad);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (!s.here(cx + x, cz + z)) continue;
                for (int y = 0; y <= R + 1; y++) {
                    double d = Math.sqrt(x * x + (y - 0.3) * (y - 0.3) * 1.6 + z * z);
                    if (d > rad + noise(seed, cx + x * 7 + y, cz + z * 7 - y) * 0.6) continue;
                    BlockState b = palette[(int) (noise(seed + 17, cx + x + y * 3, cz + z) * palette.length)];
                    s.set(cx + x, y + 1, cz + z, b);
                }
            }
        }
    }

    /** A horizontal log of {@code length} along x or z, lying on the floor. */
    private static void fallenLog(Sink s, int x0, int z0, int length, boolean alongX, Block log, boolean moss, long seed) {
        for (int i = 0; i < length; i++) {
            int x = alongX ? x0 + i : x0, z = alongX ? z0 : z0 + i;
            if (!s.here(x, z)) continue;
            s.set(x, 1, z, log(log, alongX ? Direction.Axis.X : Direction.Axis.Z));
            if (moss && noise(seed, x, z) < 0.6) s.set(x, 2, z, of(Blocks.MOSS_CARPET));
        }
    }

    /** Items scattered on the ring just outside the floor, each on the terrain of its own column. */
    private static void ringScatter(Sink s, RandomSource r, int count, double from, double to, BlockState... states) {
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double d = from + r.nextDouble() * (to - from);
            BlockState b = states[r.nextInt(states.length)];
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (!s.here(x, z)) continue;
            int g = s.ground(x, z);
            if (g < -6 || g > 6) continue;
            s.set(x, g + 1, z, b);
        }
    }

    private static BlockState vine(Direction towardsWall) {
        BooleanProperty p = VineBlock.PROPERTY_BY_DIRECTION.get(towardsWall);
        return Blocks.VINE.defaultBlockState().setValue(p, true);
    }

    /** A lit candle cluster (warm light at the den: offerings, a herder's vigil). */
    private static BlockState candles(int n) {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n))).setValue(CandleBlock.LIT, true);
    }

    /** A dripstone fang (tooth, claw, horn tip) pointing up or down. */
    private static BlockState fang(boolean up) {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, up ? Direction.UP : Direction.DOWN)
                .setValue(PointedDripstoneBlock.THICKNESS, SpeleothemThickness.TIP);
    }

    /** A line of blocks from a to b (inclusive), stepped one cell at a time. */
    private static void line(Sink s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int n = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            put(s, (int) Math.round(Mth.lerp(t, x0, x1)), (int) Math.round(Mth.lerp(t, y0, y1)), (int) Math.round(Mth.lerp(t, z0, z1)), state);
        }
    }

    // ---- the Briar Sow: a bramble wallow (forest, plains, meadow) ----------------------------------------------------

    /**
     * A mud wallow under a great uprooted oak: the trunk lies across the north side, its root ball reared up four blocks
     * with glow berries hanging under it (the warm light); a gouged scratching tree, stolen hay heaped by the den, a
     * trampled path out to the east, and a thick ring of berry brambles.
     */
    private static void wallow(Sink s, RandomSource r, long seed) {
        disc(s, 7, seed, (x, z, d, n) -> {
            if (d < 1.6 && x > 0) return of(Blocks.WATER);
            if (d < 3.2) return n < 0.55 ? of(Blocks.MUD) : of(Blocks.PACKED_MUD);
            if (x > 3 && Math.abs(z) <= 1) return of(Blocks.DIRT_PATH); // the trampled way out
            if (d < 5.2) return n < 0.4 ? of(Blocks.MUD) : n < 0.7 ? of(Blocks.COARSE_DIRT) : of(Blocks.ROOTED_DIRT);
            return n < 0.5 ? of(Blocks.COARSE_DIRT) : of(Blocks.PODZOL);
        }, 8);
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = dist(x, z);
                if (d < 4.5 || d > 7.2 || !s.here(x, z) || (x > 3 && Math.abs(z) <= 1)) continue;
                double n = noise(seed + 3, x, z);
                if (n < 0.22) s.set(x, 1, z, of(Blocks.MOSS_CARPET));
                else if (n < 0.34) s.set(x, 1, z, Blocks.LEAF_LITTER.defaultBlockState().setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + (int) (n * 10) % 4));
            }
        }
        boolean flip = r.nextBoolean();
        // the uprooted oak: a trunk lying west to east across the north side, the root ball reared up at its west end
        fallenLog(s, -4, -6, 8, true, Blocks.OAK_LOG, true, seed);
        for (int x = -7; x <= -5; x++) {
            for (int z = -8; z <= -4; z++) {
                for (int y = 1; y <= 5; y++) {
                    double d = Math.sqrt((x + 6) * (x + 6) * 1.6 + (y - 3) * (y - 3) * 0.7 + (z + 6) * (z + 6));
                    if (d > 2.6 + noise(seed + 9, x * 5 + y, z) * 0.6 || !s.here(x, z)) continue;
                    double n = noise(seed + 11, x + y * 13, z);
                    s.set(x, y, z, n < 0.45 ? of(Blocks.ROOTED_DIRT) : n < 0.75 ? of(Blocks.MANGROVE_ROOTS) : of(Blocks.DIRT));
                }
            }
        }
        // glow berries under the root ball, and roots hanging from it
        BlockState berries = Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, true);
        put(s, -4, 3, -6, of(Blocks.MANGROVE_ROOTS));
        put(s, -4, 2, -6, berries);
        put(s, -6, 5, -3, of(Blocks.HANGING_ROOTS));
        put(s, -5, 1, -3, of(Blocks.HANGING_ROOTS));
        // the scratching tree: stripped and gouged by tusks
        column(s, 5, -4, 1, 6, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
        put(s, 5, 3, -4, log(Blocks.OAK_LOG, Direction.Axis.Y));
        put(s, 6, 6, -4, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        put(s, 4, 5, -4, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        column(s, -3, 4, 1, 2, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
        fallenLog(s, flip ? 4 : -4, flip ? 2 : 3, 4, false, Blocks.BIRCH_LOG, true, seed);
        boulder(s, -6, 1, 1.3, seed, of(Blocks.MOSSY_COBBLESTONE), of(Blocks.COBBLESTONE));
        // the hoard: hay dragged in from somewhere's farm, half eaten
        put(s, -2, 1, 2, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        put(s, -3, 1, 1, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        put(s, -2, 2, 2, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        put(s, -1, 1, 3, of(Blocks.MOSS_CARPET));
        // the bramble ring: berry bushes two deep, broken where the path leaves
        BlockState bramble = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
        for (int i = 0; i < 40; i++) {
            double a = i * Mth.TWO_PI / 40 + noise(seed, i, 77) * 0.1;
            if (Math.cos(a) > 0.95) continue;
            for (double d : new double[]{7.6, 8.6}) {
                int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
                if (!s.here(x, z) || noise(seed + 5, x, z) < 0.25) continue;
                int g = s.ground(x, z);
                if (g >= -4 && g <= 4) s.set(x, g + 1, z, noise(seed + 6, x, z) < 0.8 ? bramble : of(Blocks.BUSH));
            }
        }
        ringScatter(s, r, 6, 9.0, 10.5, of(Blocks.FIREFLY_BUSH), of(Blocks.BUSH));
    }

    // ---- the Dune Wyrm: a glass hollow round a wyrm's bones (desert, savanna, badlands) -------------------------------

    /**
     * A sand hollow glazed where the wyrm's heat fused it: a ribcage arching seven blocks over its spine, the skull at the
     * west end with dripstone teeth, glass vents glowing over magma (the warm light), shed scales of terracotta, and broken
     * sandstone pillars round the rim, some leaning.
     */
    private static void glassHollow(Sink s, RandomSource r, long seed, int variant) {
        boolean red = variant == 1;
        BlockState sand = of(red ? Blocks.RED_SAND : Blocks.SAND);
        BlockState stone = of(red ? Blocks.SMOOTH_RED_SANDSTONE : Blocks.SMOOTH_SANDSTONE);
        BlockState cut = of(red ? Blocks.CUT_RED_SANDSTONE : Blocks.CUT_SANDSTONE);
        BlockState carved = of(red ? Blocks.CHISELED_RED_SANDSTONE : Blocks.CHISELED_SANDSTONE);
        disc(s, 8, seed, (x, z, d, n) -> {
            double g = noise(seed + 5, x, z);
            if (g < 0.07) return Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState();
            if (g < 0.13) return of(Blocks.GLASS);
            if (g < 0.16) return Blocks.STAINED_GLASS.pick(DyeColor.YELLOW).defaultBlockState();
            return n < 0.62 ? sand : stone;
        }, 9);
        // the spine along x, a vertebra standing up every other block
        BlockState spineX = log(Blocks.BONE_BLOCK, Direction.Axis.X), boneY = log(Blocks.BONE_BLOCK, Direction.Axis.Y);
        for (int x = -6; x <= 6; x++) {
            put(s, x, 1, 0, spineX);
            if ((x & 1) == 0) put(s, x, 2, 0, boneY);
        }
        // the ribs: half rings over the spine at odd x (the den at x = 0 stays clear), the middle ones tallest
        for (int x = -7; x <= 5; x += 3) {
            double rad = 4.8 - Math.abs(x + 1) * 0.22;
            boolean snapped = noise(seed + 21, x, 0) < 0.3;
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k <= 10; k++) {
                    double t = k / 10.0 * Math.PI / 2;
                    int z = side * (int) Math.round(Math.cos(t) * rad * 0.85), y = 1 + (int) Math.round(Math.sin(t) * rad * 1.45);
                    if (snapped && side > 0 && k > 6) break;
                    put(s, x, y, z, k < 3 ? boneY : log(Blocks.BONE_BLOCK, Direction.Axis.Z));
                }
            }
        }
        // the skull at the west end: a bone block brow over dripstone teeth
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) put(s, -8, dy, dz, spineX);
            put(s, -9, 3, dz, spineX);
            put(s, -9, 1, dz, spineX);
        }
        put(s, -9, 2, -1, fang(false));
        put(s, -9, 2, 1, fang(false));
        put(s, -8, 4, -1, boneY);
        put(s, -8, 4, 1, boneY);
        // glass vents over magma: the hollow still glows where the wyrm lay
        for (int[] v : new int[][]{{3, 3}, {-3, -4}, {5, -2}}) {
            put(s, v[0], -1, v[1], of(Blocks.MAGMA_BLOCK));
            put(s, v[0], 0, v[1], Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState());
        }
        // shed scales
        for (int i = 0; i < 9; i++) {
            int x = (int) Math.round((r.nextDouble() - 0.5) * 13), z = (int) Math.round((r.nextDouble() - 0.5) * 13);
            if (Math.abs(z) <= 0 || dist(x, z - 5) < 1.5) continue;
            put(s, x, 1, z, Blocks.DYED_TERRACOTTA.pick(r.nextBoolean() ? DyeColor.ORANGE : DyeColor.RED).defaultBlockState());
        }
        // broken pillars round the hollow, some leaning out
        for (int i = 0; i < 7; i++) {
            double a = i * Mth.TWO_PI / 7 + 0.4;
            int x = (int) Math.round(Math.cos(a) * 7.6), z = (int) Math.round(Math.sin(a) * 7.6);
            int h = 2 + r.nextInt(5);
            boolean lean = r.nextBoolean();
            for (int y = 1; y <= h; y++) {
                int ox = lean && y > h / 2 ? (int) Math.signum(x) : 0, oz = lean && y > h / 2 ? (int) Math.signum(z) : 0;
                put(s, x + ox, y, z + oz, y == h && h >= 4 ? carved : cut);
            }
        }
        ringScatter(s, r, 10, 8.5, 10.5, of(Blocks.DEAD_BUSH), sand, of(Blocks.DEAD_BUSH));
    }

    // ---- the Old Antler: a grove of antler trees round an old altar (taiga, snowy plains) ----------------------------

    /**
     * Six pale antler trees, six to eight blocks tall, branching twice into tines tipped with fence posts, soul lanterns
     * hanging from their lower tines; mossy standing stones between them; at the north an altar of mossy bricks holding a
     * shed antler in bone, with lit candles left by someone who still comes here (the warm light).
     */
    private static void antlerGrove(Sink s, RandomSource r, long seed, int variant) {
        boolean snowy = variant == 1;
        disc(s, 7, seed, (x, z, d, n) -> {
            if (snowy && n < 0.6) return of(Blocks.SNOW_BLOCK);
            if (n < 0.35) return of(Blocks.GRAVEL);
            if (n < 0.6) return of(Blocks.COARSE_DIRT);
            return of(Blocks.MOSSY_COBBLESTONE);
        }, 10);
        // the altar on the north side: mossy bricks, a shed antler in bone, candles
        for (int x = -1; x <= 1; x++) for (int z = -6; z <= -4; z++) {
            put(s, x, 1, z, (x + z) % 2 == 0 ? of(Blocks.MOSSY_STONE_BRICKS) : of(Blocks.CRACKED_STONE_BRICKS));
        }
        put(s, 0, 2, -5, log(Blocks.BONE_BLOCK, Direction.Axis.X));
        put(s, -1, 2, -5, log(Blocks.BONE_BLOCK, Direction.Axis.Y));
        put(s, 1, 2, -5, log(Blocks.BONE_BLOCK, Direction.Axis.Y));
        put(s, -1, 3, -5, Blocks.SPRUCE_FENCE.defaultBlockState());
        put(s, 1, 3, -5, Blocks.SPRUCE_FENCE.defaultBlockState());
        put(s, -1, 2, -4, candles(3));
        put(s, 1, 2, -6, candles(2));
        put(s, 0, 2, -4, of(Blocks.SOUL_LANTERN));
        // six antler trees: a pale trunk, tines out to both sides twice, fence tips
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6 + Mth.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 6.2), z = (int) Math.round(Math.sin(a) * 6.2);
            int h = 6 + r.nextInt(3);
            column(s, x, z, 1, h, post);
            int tx = (int) Math.round(-Math.sin(a)), tz = (int) Math.round(Math.cos(a));
            Direction.Axis ax = Math.abs(tx) > Math.abs(tz) ? Direction.Axis.X : Direction.Axis.Z;
            for (int level : new int[]{h - 3, h - 1}) {
                for (int side = -1; side <= 1; side += 2) {
                    int bx = x + tx * side, bz = z + tz * side;
                    put(s, bx, level, bz, log(Blocks.STRIPPED_SPRUCE_LOG, ax));
                    put(s, bx + tx * side, level + 1, bz + tz * side, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
                    put(s, bx + tx * side, level + 2, bz + tz * side, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
            put(s, x, h, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            if (i % 2 == 0) {
                put(s, x + tx, h - 4, z + tz, of(Blocks.IRON_CHAIN));
                put(s, x + tx, h - 5, z + tz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        // mossy standing stones between the trees
        for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6;
            int x = (int) Math.round(Math.cos(a) * 7.0), z = (int) Math.round(Math.sin(a) * 7.0);
            if (z < -5 && Math.abs(x) <= 2) continue;
            column(s, x, z, 1, 1 + r.nextInt(2), of(Blocks.MOSSY_COBBLESTONE));
        }
        if (snowy) {
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                if (dist(x, z) > 7.2 || !s.here(x, z) || noise(seed + 8, x, z) > 0.55) continue;
                if (z >= -6 && z <= -4 && Math.abs(x) <= 1) continue;
                s.set(x, 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (noise(seed, x, z) * 2)));
            }
        } else {
            for (int x = -6; x <= 6; x++) for (int z = -3; z <= 6; z++) {
                if (dist(x, z) > 6 || !s.here(x, z) || noise(seed + 9, x, z) > 0.2 || (x == 0 && z == 1)) continue;
                s.set(x, 1, z, noise(seed + 10, x, z) < 0.1 ? of(Blocks.FERN) : of(Blocks.MOSS_CARPET));
            }
        }
        ringScatter(s, r, 10, 7.5, 9.5, of(Blocks.FERN), of(Blocks.MOSSY_COBBLESTONE), of(Blocks.SWEET_BERRY_BUSH));
    }

    // ---- the Mire Mother: a throne in the bog (swamp, mangrove swamp) -----------------------------------------------

    /**
     * A mud mound where she sits, under an arch of mangrove roots rising six blocks behind it like a throne's back, three
     * froglights set in its shoulders (the warm light); pools of lily pads and frogspawn round it, big dripleaf, root arches
     * at the edge.
     */
    private static void toadThrone(Sink s, RandomSource r, long seed) {
        disc(s, 8, seed, (x, z, d, n) -> {
            if (d < 3.3) return of(Blocks.MUD);
            if (d < 6.4 && n > 0.47) return of(Blocks.WATER);
            return n < 0.3 ? of(Blocks.MUDDY_MANGROVE_ROOTS) : n < 0.6 ? of(Blocks.MUD) : of(Blocks.MOSS_BLOCK);
        }, 8);
        // the mound she sits on
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            double d = dist(x, z);
            if (!s.here(x, z) || d > 3.2) continue;
            s.set(x, 1, z, d < 2.0 ? of(Blocks.MOSS_BLOCK) : of(Blocks.MUD));
        }
        Block[] lights = {Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT};
        for (int i = 0; i < 3; i++) {
            double a = i * Mth.TWO_PI / 3 + r.nextDouble();
            int x = (int) Math.round(Math.cos(a) * 2.8), z = (int) Math.round(Math.sin(a) * 2.8);
            put(s, x, 1, z, of(lights[i]));
        }
        // the throne's back: an arch of mangrove roots and mud bricks behind her (north)
        for (int k = 0; k <= 12; k++) {
            double t = k / 12.0 * Math.PI;
            int x = (int) Math.round(-Math.cos(t) * 3.6), y = 1 + (int) Math.round(Math.sin(t) * 5.0);
            put(s, x, y, -3, k % 3 == 0 ? of(Blocks.MUD_BRICKS) : of(Blocks.MANGROVE_ROOTS));
            put(s, x, y, -4, of(Blocks.MANGROVE_ROOTS));
            if (k > 2 && k < 10) put(s, x, y - 1, -2, vine(Direction.NORTH));
        }
        put(s, 0, 7, -3, of(Blocks.OCHRE_FROGLIGHT));
        // pools: lily pads and frogspawn
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            double d = dist(x, z);
            if (d < 3.3 || d > 6.4 || !s.here(x, z) || patch(seed, x, z) <= 0.47) continue;
            double n = noise(seed + 2, x, z);
            if (n < 0.3) s.set(x, 1, z, of(Blocks.LILY_PAD));
            else if (n < 0.38) s.set(x, 1, z, of(Blocks.FROGSPAWN));
        }
        // root arches at the edge
        for (int i = 0; i < 4; i++) {
            double a = i * Mth.HALF_PI + 0.6 + r.nextDouble() * 0.4;
            int x = (int) Math.round(Math.cos(a) * 6.8), z = (int) Math.round(Math.sin(a) * 6.8);
            int x2 = (int) Math.round(Math.cos(a + 0.45) * 6.8), z2 = (int) Math.round(Math.sin(a + 0.45) * 6.8);
            line(s, x, 1, z, (x + x2) / 2, 4, (z + z2) / 2, of(Blocks.MANGROVE_ROOTS));
            line(s, (x + x2) / 2, 4, (z + z2) / 2, x2, 1, z2, of(Blocks.MANGROVE_ROOTS));
        }
        ringScatter(s, r, 10, 8.0, 10.0, of(Blocks.FIREFLY_BUSH), of(Blocks.BIG_DRIPLEAF), of(Blocks.FIREFLY_BUSH));
    }

    // ---- the Plumed Coil: the serpent's own shrine (jungles) ---------------------------------------------------------

    /**
     * An overgrown stone-brick court where a stone serpent coils up round the nest, one and a half turns to seven blocks,
     * and its head bends over the den with froglight eyes (the warm light); broken pillars hung with vines at the corners,
     * bamboo crowding in.
     */
    private static void serpentShrine(Sink s, RandomSource r, long seed) {
        disc(s, 7, seed, (x, z, d, n) -> {
            if (d < 5.4) return n < 0.45 ? of(Blocks.MOSSY_STONE_BRICKS) : n < 0.7 ? of(Blocks.STONE_BRICKS) : of(Blocks.CRACKED_STONE_BRICKS);
            return n < 0.5 ? of(Blocks.MOSS_BLOCK) : of(Blocks.COARSE_DIRT);
        }, 9);
        // four broken pillars, vines down their outer faces
        int[][] corners = {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}};
        for (int[] c : corners) {
            int h = 2 + r.nextInt(4);
            column(s, c[0], c[1], 1, h, of(Blocks.MOSSY_STONE_BRICKS));
            put(s, c[0], h + 1, c[1], of(Blocks.CHISELED_STONE_BRICKS));
            int ox = Integer.signum(c[0]), oz = Integer.signum(c[1]);
            for (int y = 1; y <= h; y++) {
                put(s, c[0] + ox, y, c[1], vine(ox > 0 ? Direction.WEST : Direction.EAST));
                put(s, c[0], y, c[1] + oz, vine(oz > 0 ? Direction.NORTH : Direction.SOUTH));
            }
        }
        // the nest: a ring of leaves round the den, moss inside
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            double d = dist(x, z);
            if (!s.here(x, z)) continue;
            if (d > 1.4 && d < 2.4) s.set(x, 1, z, leaves(Blocks.JUNGLE_LEAVES));
            else if (d <= 1.4) s.set(x, 1, z, of(Blocks.MOSS_CARPET));
        }
        // the coil: a helix of stone round the nest, rising to a head that bends over the den
        double turns = 1.4;
        int steps = 44;
        int hx = 0, hy = 0, hz = 0;
        for (int k = 0; k <= steps; k++) {
            double t = k / (double) steps;
            double a = t * turns * Mth.TWO_PI;
            double rad = 3.6 - t * 1.2;
            int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad), y = 1 + (int) Math.round(t * 6.0);
            if (Math.abs(x) <= 0 && Math.abs(z) <= 0) continue;
            double n = noise(seed + 31, k, 0);
            put(s, x, y, z, n < 0.4 ? of(Blocks.MOSSY_STONE_BRICKS) : n < 0.8 ? of(Blocks.STONE_BRICKS) : of(Blocks.CHISELED_STONE_BRICKS));
            if (y > 1) put(s, x, y - 1, z, of(Blocks.MOSSY_STONE_BRICKS));
            hx = x;
            hy = y;
            hz = z;
        }
        // the head: a block reaching in over the den, eyes of froglight, a dripstone fang
        int ix = hx - Integer.signum(hx), iz = hz - Integer.signum(hz);
        put(s, ix, hy + 1, iz, of(Blocks.CHISELED_STONE_BRICKS));
        put(s, ix, hy, iz, of(Blocks.MOSSY_STONE_BRICKS));
        put(s, hx, hy + 1, hz, of(Blocks.OCHRE_FROGLIGHT));
        put(s, ix, hy - 1, iz, fang(false));
        // bamboo crowding in from outside
        for (int i = 0; i < 7; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            int x = (int) Math.round(Math.cos(a) * 8.5), z = (int) Math.round(Math.sin(a) * 8.5);
            int h = 4 + r.nextInt(5);
            if (!s.here(x, z)) continue;
            int g = s.ground(x, z);
            if (g < -5 || g > 5) continue;
            for (int y = 1; y <= h; y++) s.set(x, g + y, z, of(Blocks.BAMBOO));
        }
        ringScatter(s, r, 8, 7.5, 9.5, of(Blocks.FERN), of(Blocks.MOSSY_COBBLESTONE));
    }

    // ---- the Weaver Queen: a hollow stump strung with silk (dark forest, pale garden) --------------------------------

    /**
     * A giant hollow stump nine blocks tall with a jagged broken crown and an opening toward the den, silk inside and egg
     * sacs heaped at its foot; four dead trunks with silk strung between their tops and a silk canopy sagging over the
     * clearing; cocooned prey, bones and a skull; glow lichen on the bark (the warm light, if a cold one).
     */
    private static void silkHollow(Sink s, RandomSource r, long seed, int variant) {
        boolean pale = variant == 1;
        Block log = pale ? Blocks.PALE_OAK_LOG : Blocks.DARK_OAK_LOG;
        Block dead = pale ? Blocks.STRIPPED_PALE_OAK_LOG : Blocks.STRIPPED_DARK_OAK_LOG;
        disc(s, 7, seed, (x, z, d, n) -> n < 0.4 ? of(Blocks.COARSE_DIRT) : n < 0.65 ? of(Blocks.GRAVEL)
                : pale ? of(Blocks.PALE_MOSS_BLOCK) : of(Blocks.ROOTED_DIRT), 11);
        // the hollow stump: a ring of trunk round (-4, -4), opening to the south-east, jagged at the top
        BlockState trunk = log(log, Direction.Axis.Y);
        for (int x = -7; x <= -1; x++) {
            for (int z = -7; z <= -1; z++) {
                double d = Math.sqrt((x + 4) * (x + 4) + (z + 4) * (z + 4));
                if (!s.here(x, z) || d > 2.9) continue;
                boolean shell = d > 1.6;
                boolean door = x >= -3 && z >= -3 && x + z >= -5;
                int h = shell ? 6 + (int) (noise(seed, x, z) * 4) : 0;
                for (int y = 1; y <= Math.max(h, 1); y++) {
                    if (shell && !(door && y <= 3)) s.set(x, y, z, trunk);
                    else if (!shell && noise(seed + 2, x * 3 + y, z) < 0.5) s.set(x, y, z, of(Blocks.COBWEB));
                }
            }
        }
        // glow lichen creeping over the ground round the stump
        BlockState lichen = of(Blocks.GLOW_LICHEN).setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.DOWN), true);
        for (int x = -8; x <= 0; x++) {
            for (int z = -8; z <= 0; z++) {
                double d = Math.sqrt((x + 4) * (x + 4) + (z + 4) * (z + 4));
                if (d > 2.9 && d < 4.2 && noise(seed + 44, x, z) < 0.4) put(s, x, 1, z, lichen);
            }
        }
        // egg sacs at its foot
        for (int[] e : new int[][]{{-2, -5}, {-1, -5}, {-2, -6}, {-5, -2}}) put(s, e[0], 1, e[1], of(Blocks.MUSHROOM_STEM));
        put(s, -1, 2, -5, of(Blocks.COBWEB));
        // four dead trunks and silk strung between their tops
        int[][] posts = new int[4][];
        for (int i = 0; i < 4; i++) {
            double a = i * Mth.HALF_PI + Mth.PI / 4 + (r.nextDouble() - 0.5) * 0.3;
            int x = (int) Math.round(Math.cos(a) * 6.4), z = (int) Math.round(Math.sin(a) * 6.4);
            int h = 7 + r.nextInt(3);
            posts[i] = new int[]{x, z, h};
            column(s, x, z, 1, h, log(dead, Direction.Axis.Y));
            put(s, x + (int) Math.signum(-x), h - 2, z, log(dead, Direction.Axis.X));
        }
        for (int i = 0; i < 4; i++) {
            int[] a = posts[i], b = posts[(i + 1) % 4], c = posts[(i + 2) % 4];
            strand(s, a, b, seed + i);
            if (i < 2) strand(s, a, c, seed + 10 + i);
        }
        // a silk canopy sagging over the east of the clearing
        for (int x = 0; x <= 5; x++) for (int z = -3; z <= 4; z++) {
            if (!s.here(x, z) || noise(seed + 12, x, z) > 0.45) continue;
            int y = 7 - (int) Math.round(Math.sin((x / 5.0) * Math.PI) * 1.5);
            s.set(x, y, z, of(Blocks.COBWEB));
        }
        // silk on the floor, cocooned prey, old bones, a skull, mushrooms in the gloom (the den stays clear)
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            double d = dist(x, z);
            if (d > 7.2 || !s.here(x, z) || (Math.abs(x - 2) <= 1 && Math.abs(z - 2) <= 1)) continue;
            if (Math.sqrt((x + 4) * (x + 4) + (z + 4) * (z + 4)) < 3.2) continue;
            double n = noise(seed + 4, x, z);
            if (n < 0.13) s.set(x, 1, z, of(Blocks.COBWEB));
            else if (n < 0.16) s.set(x, 1, z, of(Blocks.BROWN_MUSHROOM));
            else if (n < 0.175) s.set(x, 1, z, log(Blocks.BONE_BLOCK, (x & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z));
            else if (pale && n > 0.9) s.set(x, 1, z, of(Blocks.PALE_MOSS_CARPET));
        }
        put(s, 5, 1, -1, log(Blocks.BONE_BLOCK, Direction.Axis.Y));
        put(s, 5, 2, -1, of(Blocks.COBWEB));
        put(s, 4, 1, -1, of(Blocks.SKELETON_SKULL));
        ringScatter(s, r, 8, 7.5, 9.5, of(Blocks.COBWEB), of(Blocks.BROWN_MUSHROOM));
    }

    /** Cobweb along the line between two post tops, sagging in the middle. */
    private static void strand(Sink s, int[] a, int[] b, long seed) {
        int steps = (int) Math.ceil(Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(Mth.lerp(t, a[0], b[0])), z = (int) Math.round(Mth.lerp(t, a[1], b[1]));
            int y = (int) Math.round(Mth.lerp(t, a[2], b[2]) - Math.sin(t * Math.PI) * 2);
            if (s.here(x, z) && noise(seed, x, z) < 0.8) s.set(x, y, z, of(Blocks.COBWEB));
        }
    }

    // ---- the Empress Moth: a vaulted petal bower (cherry grove, flower and birch forests) -----------------------------

    /**
     * Four cherry trunks that lean in as they rise and meet in a dome of blossom eight blocks up, flowering azalea at its
     * crown; under it a silk chrysalis hangs over the den with end rods glowing beside it like moonlight (the warm light
     * is the lanterns at the trunks), spore blossoms and petals everywhere, a ring of flowers outside.
     */
    private static void petalBower(Sink s, RandomSource r, long seed) {
        disc(s, 7, seed, (x, z, d, n) -> d < 5.6 ? of(Blocks.MOSS_BLOCK) : n < 0.5 ? of(Blocks.ROOTED_DIRT) : of(Blocks.MOSS_BLOCK), 11);
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            if (dist(x, z) > 7.2 || !s.here(x, z)) continue;
            double n = noise(seed + 6, x, z);
            if (n < 0.65) {
                s.set(x, 1, z, Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 1 + (int) (n * 6) % 4)
                        .setValue(FlowerBedBlock.FACING, Direction.from2DDataValue((int) (n * 40) & 3)));
            }
        }
        // the trunks lean in as they rise: straight to 5, a step in at 6 and 7
        int[][] trunks = {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}};
        for (int[] t : trunks) {
            column(s, t[0], t[1], 1, 5, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
            int ix = t[0] - Integer.signum(t[0]), iz = t[1] - Integer.signum(t[1]);
            put(s, ix, 6, iz, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
            put(s, ix - Integer.signum(t[0]), 7, iz - Integer.signum(t[1]), log(Blocks.CHERRY_LOG, Direction.Axis.Y));
            put(s, t[0], 3, t[1] - Integer.signum(t[1]), Blocks.LANTERN.defaultBlockState());
        }
        // the dome
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            double d = dist(x, z);
            if (!s.here(x, z)) continue;
            if (d <= 6.4 && d > 4.6 + noise(seed + 1, x, z) * 0.8) s.set(x, 7, z, leaves(Blocks.CHERRY_LEAVES));
            if (d <= 5.8 + noise(seed + 1, x, z) * 0.6) s.set(x, 8, z, leaves(Blocks.CHERRY_LEAVES));
            if (d <= 4.0) s.set(x, 9, z, leaves(Blocks.CHERRY_LEAVES));
            if (d <= 2.2) s.set(x, 10, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
        }
        // the chrysalis over the den, end rods glowing either side of it
        put(s, 0, 7, 0, of(Blocks.MUSHROOM_STEM));
        put(s, 0, 6, 0, of(Blocks.MUSHROOM_STEM));
        put(s, 0, 5, 0, of(Blocks.COBWEB));
        put(s, 2, 7, 0, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        put(s, -2, 7, 0, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        put(s, 0, 7, 2, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        int[][] hangs = {{3, 1}, {-3, -1}, {1, -3}, {-1, 3}};
        for (int[] h : hangs) put(s, h[0], 7, h[1], of(Blocks.SPORE_BLOSSOM));
        ringScatter(s, r, 14, 7.5, 9.5, of(Blocks.FLOWERING_AZALEA), of(Blocks.PINK_TULIP), of(Blocks.ALLIUM), of(Blocks.AZALEA),
                of(Blocks.LILY_OF_THE_VALLEY));
    }

    // ---- the Stonehorn: a horned cairn on a mountain ledge (peaks, windswept hills) -----------------------------------

    /**
     * A cairn of stacked stones nine blocks tall crowned by a ram's skull with horns curling in bone, a henge of standing
     * stones round the ledge (one pair still under its lintel), boulders, and candles left at the cairn's foot by whoever
     * climbs up here (the warm light).
     */
    private static void ramsCairn(Sink s, RandomSource r, long seed, int variant) {
        boolean snowy = variant == 1;
        disc(s, 8, seed, (x, z, d, n) -> {
            if (snowy) return n < 0.45 ? of(Blocks.SNOW_BLOCK) : n < 0.6 ? of(Blocks.PACKED_ICE) : of(Blocks.STONE);
            return n < 0.35 ? of(Blocks.STONE) : n < 0.55 ? of(Blocks.ANDESITE) : n < 0.75 ? of(Blocks.GRAVEL) : of(Blocks.CALCITE);
        }, 11);
        // the cairn on the north side: a cone of stacked stones
        for (int y = 1; y <= 8; y++) {
            double rad = 3.0 - y * 0.32;
            for (int x = -3; x <= 3; x++) for (int z = -9; z <= -1; z++) {
                if (!s.here(x, z) || dist(x, z + 5) > rad + noise(seed + y, x, z) * 0.5) continue;
                double n = noise(seed + 30 + y, x, z);
                s.set(x, y, z, n < 0.5 ? of(Blocks.COBBLESTONE) : n < 0.8 ? of(Blocks.ANDESITE) : of(Blocks.MOSSY_COBBLESTONE));
            }
        }
        // the skull and its curling horns
        put(s, 0, 9, -5, log(Blocks.BONE_BLOCK, Direction.Axis.Z));
        put(s, 0, 9, -4, of(Blocks.SKELETON_SKULL));
        for (int side = -1; side <= 1; side += 2) {
            put(s, side, 10, -5, log(Blocks.BONE_BLOCK, Direction.Axis.X));
            put(s, side * 2, 10, -5, log(Blocks.BONE_BLOCK, Direction.Axis.X));
            put(s, side * 3, 9, -5, log(Blocks.BONE_BLOCK, Direction.Axis.Y));
            put(s, side * 3, 8, -4, log(Blocks.BONE_BLOCK, Direction.Axis.Z));
            put(s, side * 2, 8, -3, fang(false));
        }
        put(s, -1, 1, -1, candles(3));
        put(s, 1, 1, -2, candles(2));
        // standing stones round the ledge, one pair under a lintel
        int[] heights = new int[7];
        int[][] pos = new int[7][];
        for (int i = 0; i < 7; i++) {
            double a = i * Mth.TWO_PI / 7 + 0.2;
            int x = (int) Math.round(Math.cos(a) * 7.4), z = (int) Math.round(Math.sin(a) * 7.4);
            pos[i] = new int[]{x, z};
            if (z < -5 && Math.abs(x) < 4) continue;
            heights[i] = 3 + r.nextInt(3);
            column(s, x, z, 1, heights[i], of(Blocks.POLISHED_ANDESITE));
            if (r.nextBoolean()) put(s, x, heights[i] + 1, z, of(Blocks.STONE_BRICK_WALL));
        }
        if (heights[1] > 0 && heights[2] > 0) {
            int top = Math.min(heights[1], heights[2]) + 1;
            line(s, pos[1][0], top, pos[1][1], pos[2][0], top, pos[2][1], of(Blocks.POLISHED_ANDESITE));
        }
        boulder(s, 5, -3, 1.4, seed, of(Blocks.STONE), of(Blocks.ANDESITE), of(Blocks.TUFF));
        boulder(s, -5, 3, 1.2, seed + 3, of(Blocks.DIORITE), of(Blocks.STONE));
        if (snowy) {
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                if (dist(x, z) > 8.2 || !s.here(x, z) || noise(seed + 12, x, z) > 0.5) continue;
                if (z <= -1 && dist(x, z + 5) < 3.4) continue;
                s.set(x, 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (noise(seed, x, z) * 3)));
            }
        }
        ringScatter(s, r, 10, 8.5, 10.5, of(Blocks.COBBLESTONE), of(Blocks.ANDESITE));
    }

    // ---- the Mycelord: a ring of giant toadstools (mushroom fields) -----------------------------------------------------

    /**
     * A fairy ring round a mycelium clearing: three giant toadstools (stems seven to nine blocks, broad caps with
     * shroomlights and spore blossoms underneath, the warm light), five smaller ones between, a toppled cap at the edge,
     * and small mushrooms in an inner ring.
     */
    private static void sporeRing(Sink s, RandomSource r, long seed) {
        disc(s, 8, seed, (x, z, d, n) -> {
            double g = noise(seed + 7, x, z);
            if (g < 0.05 && d > 2.5) return of(Blocks.SHROOMLIGHT);
            return n < 0.6 ? of(Blocks.MYCELIUM) : of(Blocks.PODZOL);
        }, 11);
        for (int i = 0; i < 8; i++) {
            double a = i * Mth.TWO_PI / 8 + r.nextDouble() * 0.3;
            boolean giant = i % 3 == 0;
            double ring = giant ? 6.6 : 6.9;
            int x = (int) Math.round(Math.cos(a) * ring), z = (int) Math.round(Math.sin(a) * ring);
            int h = giant ? 7 + r.nextInt(3) : 2 + r.nextInt(3);
            boolean red = r.nextBoolean();
            column(s, x, z, 1, h, of(Blocks.MUSHROOM_STEM));
            BlockState cap = (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
            int cr = giant ? 2 : 1;
            for (int dx = -cr - (giant ? 1 : 0); dx <= cr + (giant ? 1 : 0); dx++) {
                for (int dz = -cr - (giant ? 1 : 0); dz <= cr + (giant ? 1 : 0); dz++) {
                    double dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd > cr + (giant ? 1.2 : 0.5)) continue;
                    put(s, x + dx, h + 1, z + dz, cap);
                    if (giant && dd > cr + 0.3) put(s, x + dx, h, z + dz, cap); // the rim droops
                    if (giant && dd <= cr - 0.5) put(s, x + dx, h + 2, z + dz, cap);
                    if (giant && dd > 0.9 && dd <= cr && noise(seed + 40, x + dx, z + dz) < 0.35) put(s, x + dx, h, z + dz, of(Blocks.SPORE_BLOSSOM));
                }
            }
            if (h >= 3) put(s, x, h, z, of(Blocks.SHROOMLIGHT));
        }
        // a toppled cap lying at the edge
        int tx = 3 + r.nextInt(2), tz = -6;
        for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) put(s, tx + dx, dy, tz, of(Blocks.RED_MUSHROOM_BLOCK));
        line(s, tx, 2, tz + 1, tx, 2, tz + 4, of(Blocks.MUSHROOM_STEM));
        // small mushrooms in an inner ring
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = dist(x, z);
            if (d < 3.5 || d > 5.5 || !s.here(x, z)) continue;
            double n = noise(seed + 11, x, z);
            if (n < 0.3) s.set(x, 1, z, (n < 0.15 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
        }
        ringScatter(s, r, 8, 8.5, 10.5, of(Blocks.RED_MUSHROOM), of(Blocks.BROWN_MUSHROOM));
    }

    /** For the tests: the den must be standing room (two clear blocks over a solid floor). */
    public static boolean standingRoom(net.minecraft.world.level.Level level, BlockPos den) {
        return level.getBlockState(den).isAir() && level.getBlockState(den.above()).isAir() && !level.getBlockState(den.below()).isAir();
    }

    /** P3: room for a beast at the den in air or in water (no collision there and above it, something under it). */
    public static boolean openRoom(net.minecraft.world.level.Level level, BlockPos den) {
        return level.getBlockState(den).getCollisionShape(level, den).isEmpty() && level.getBlockState(den.above()).getCollisionShape(level, den.above()).isEmpty()
                && !level.getBlockState(den.below()).isAir() && level.getBlockState(den.below()).getFluidState().isEmpty();
    }
}
