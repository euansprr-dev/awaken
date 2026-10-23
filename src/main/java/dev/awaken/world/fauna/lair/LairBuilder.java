package dev.awaken.world.fauna.lair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The nine lairs as code (no template files): each named beast's den, built around its centre with the floor layer at
 * y = 0 (the beast stands at {@link LairStyle#den}). Every chunk the piece touches rebuilds the same blueprint from the
 * same seed and keeps only its own blocks, so every random choice is made before any block is placed and never
 * depends on the terrain. The core disc sits on ground the beardifier flattened; only the scattered ring outside it
 * follows the terrain column by column.
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
    }

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
                s.fillDown(x, -1, z, b.is(Blocks.WATER) ? Blocks.MUD.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 4);
                for (int y = 1; y <= clear; y++) s.set(x, y, z, AIR);
            }
        }
    }

    private static void column(Sink s, int x, int z, int y0, int height, BlockState state) {
        if (!s.here(x, z)) return;
        for (int y = y0; y < y0 + height; y++) s.set(x, y, z, state);
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
            if (moss && noise(seed, x, z) < 0.6) s.set(x, 2, z, Blocks.MOSS_CARPET.defaultBlockState());
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

    // ---- the Briar Sow: a bramble wallow (forest, plains, meadow) ----------------------------------------------------

    private static void wallow(Sink s, RandomSource r, long seed) {
        disc(s, 7, seed, (x, z, d, n) -> {
            if (d < 1.6 && x > 0) return Blocks.WATER.defaultBlockState();
            if (d < 3.2) return n < 0.55 ? Blocks.MUD.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
            if (d < 5.2) return n < 0.4 ? Blocks.MUD.defaultBlockState() : n < 0.7 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
            return n < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
        }, 6);
        // churned edges: moss and leaf litter
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = dist(x, z);
                if (d < 4.5 || d > 7.2 || !s.here(x, z)) continue;
                double n = noise(seed + 3, x, z);
                if (n < 0.22) s.set(x, 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                else if (n < 0.34) s.set(x, 1, z, Blocks.LEAF_LITTER.defaultBlockState().setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + (int) (n * 10) % 4));
            }
        }
        boolean flip = r.nextBoolean();
        fallenLog(s, -5, flip ? -3 : 2, 5, true, Blocks.OAK_LOG, true, seed);
        fallenLog(s, flip ? 4 : -4, -5, 4, false, Blocks.BIRCH_LOG, true, seed);
        // tusk-scarred stumps
        column(s, -3, 4, 1, 2, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
        column(s, 5, 3, 1, 1, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
        boulder(s, -6, -1, 1.3, seed, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        boulder(s, 3, -6, 1.1, seed + 9, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        // the bramble ring: berry bushes all round
        BlockState berries = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
        ringScatter(s, r, 18, 7.0, 9.0, berries, berries, Blocks.BUSH.defaultBlockState());
    }

    // ---- the Dune Wyrm: a glass hollow with a wyrm's bones (desert, savanna, badlands) -------------------------------

    private static void glassHollow(Sink s, RandomSource r, long seed, int variant) {
        boolean red = variant == 1;
        BlockState sand = (red ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
        BlockState stone = (red ? Blocks.SMOOTH_RED_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
        BlockState cut = (red ? Blocks.CUT_RED_SANDSTONE : Blocks.CUT_SANDSTONE).defaultBlockState();
        BlockState carved = (red ? Blocks.CHISELED_RED_SANDSTONE : Blocks.CHISELED_SANDSTONE).defaultBlockState();
        disc(s, 8, seed, (x, z, d, n) -> {
            double g = noise(seed + 5, x, z);
            if (g < 0.07) return Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.ORANGE).defaultBlockState();
            if (g < 0.13) return Blocks.GLASS.defaultBlockState();
            if (g < 0.16) return Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState();
            return n < 0.62 ? sand : stone;
        }, 7);
        // the wyrm's bones: a spine along x, ribs arching up both sides
        BlockState spine = log(Blocks.BONE_BLOCK, Direction.Axis.X), rib = log(Blocks.BONE_BLOCK, Direction.Axis.Y);
        for (int x = -6; x <= 6; x++) {
            if (s.here(x, 0)) s.set(x, x % 3 == 0 ? 0 : 1, 0, spine);
        }
        int[][] arc = {{1, 1}, {2, 2}, {3, 3}, {3, 4}, {2, 5}};
        for (int x = -4; x <= 4; x += 2) {
            int broken = r.nextInt(5);
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < arc.length; k++) {
                    if (x == broken * 2 - 4 && side > 0 && k > 2) break; // a few ribs snapped off
                    if (s.here(x, side * arc[k][0])) s.set(x, arc[k][1], side * arc[k][0], rib);
                }
            }
        }
        // the skull at the east end
        for (int dx = 0; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 2; dy++) {
            if (s.here(7 + dx, dz)) s.set(7 + dx, dy, dz, rib);
        }
        // broken pillars round the hollow
        for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6 + 0.4;
            int x = (int) Math.round(Math.cos(a) * 7.3), z = (int) Math.round(Math.sin(a) * 7.3);
            int h = 1 + r.nextInt(4);
            column(s, x, z, 1, h, cut);
            if (s.here(x, z) && h >= 3) s.set(x, h + 1, z, carved);
        }
        ringScatter(s, r, 8, 8.5, 10.5, Blocks.DEAD_BUSH.defaultBlockState(), sand);
    }

    // ---- the Old Antler: a grove of antler posts round an old altar (taiga, snowy plains) ----------------------------

    private static void antlerGrove(Sink s, RandomSource r, long seed, int variant) {
        boolean snowy = variant == 1;
        disc(s, 7, seed, (x, z, d, n) -> {
            if (snowy && n < 0.6) return Blocks.SNOW_BLOCK.defaultBlockState();
            if (n < 0.35) return Blocks.GRAVEL.defaultBlockState();
            if (n < 0.6) return Blocks.COARSE_DIRT.defaultBlockState();
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }, 7);
        // the altar on the north side: mossy bricks, a chiselled heart and a soul lantern
        for (int x = -1; x <= 1; x++) for (int z = -6; z <= -4; z++) {
            if (s.here(x, z)) s.set(x, 1, z, (x + z) % 2 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        }
        if (s.here(0, -5)) {
            s.set(0, 2, -5, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            s.set(0, 3, -5, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // six antler posts: a pale trunk with branching tines
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6 + Mth.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 6.2), z = (int) Math.round(Math.sin(a) * 6.2);
            int h = 4 + r.nextInt(2);
            column(s, x, z, 1, h, post);
            // tines out to the sides (tangent to the ring) and one up
            int tx = (int) Math.round(-Math.sin(a)), tz = (int) Math.round(Math.cos(a));
            BlockState branch = log(Blocks.STRIPPED_SPRUCE_LOG, Math.abs(tx) > Math.abs(tz) ? Direction.Axis.X : Direction.Axis.Z);
            if (s.here(x + tx, z + tz)) s.set(x + tx, h - 1, z + tz, branch);
            if (s.here(x - tx, z - tz)) s.set(x - tx, h, z - tz, branch);
            if (s.here(x + tx * 2, z + tz * 2)) s.set(x + tx * 2, h, z + tz * 2, Blocks.SPRUCE_FENCE.defaultBlockState());
            if (s.here(x - tx * 2, z - tz * 2)) s.set(x - tx * 2, h + 1, z - tz * 2, Blocks.SPRUCE_FENCE.defaultBlockState());
            if (i % 2 == 0 && s.here(x, z)) s.set(x, h + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        if (snowy) {
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                if (dist(x, z) > 7.2 || !s.here(x, z) || noise(seed + 8, x, z) > 0.55) continue;
                if (z >= -6 && z <= -4 && Math.abs(x) <= 1) continue;
                s.set(x, 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (noise(seed, x, z) * 2)));
            }
        }
        ringScatter(s, r, 10, 7.5, 9.5, Blocks.FERN.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.SWEET_BERRY_BUSH.defaultBlockState());
    }

    // ---- the Mire Mother: a throne mound in the bog (swamp, mangrove swamp) -------------------------------------------

    private static void toadThrone(Sink s, RandomSource r, long seed) {
        disc(s, 8, seed, (x, z, d, n) -> {
            if (d < 3.3) return Blocks.MUD.defaultBlockState();
            if (d < 6.4 && n > 0.47) return Blocks.WATER.defaultBlockState();
            return n < 0.3 ? Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState() : n < 0.6 ? Blocks.MUD.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
        }, 6);
        // the mound she sits on: mud rising to moss, glowing froglights set in its shoulders
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            double d = dist(x, z);
            if (!s.here(x, z) || d > 3.2) continue;
            s.set(x, 1, z, d < 2.0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MUD.defaultBlockState());
        }
        Block[] lights = {Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT};
        for (int i = 0; i < 3; i++) {
            double a = i * Mth.TWO_PI / 3 + r.nextDouble();
            int x = (int) Math.round(Math.cos(a) * 2.8), z = (int) Math.round(Math.sin(a) * 2.8);
            if (s.here(x, z)) s.set(x, 1, z, lights[i].defaultBlockState());
        }
        // lily pads on the pools, fireflies and dripleaf round the edge, mangrove roots arching up
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            double d = dist(x, z);
            if (d < 3.3 || d > 6.4 || !s.here(x, z) || patch(seed, x, z) <= 0.47) continue;
            if (noise(seed + 2, x, z) < 0.3) s.set(x, 1, z, Blocks.LILY_PAD.defaultBlockState());
        }
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            int x = (int) Math.round(Math.cos(a) * 7), z = (int) Math.round(Math.sin(a) * 7);
            column(s, x, z, 1, 2 + r.nextInt(3), Blocks.MANGROVE_ROOTS.defaultBlockState());
        }
        ringScatter(s, r, 10, 8.0, 10.0, Blocks.FIREFLY_BUSH.defaultBlockState(), Blocks.BIG_DRIPLEAF.defaultBlockState(), Blocks.FIREFLY_BUSH.defaultBlockState());
    }

    // ---- the Plumed Coil: an overgrown serpent shrine (jungles) ------------------------------------------------------

    private static void serpentShrine(Sink s, RandomSource r, long seed) {
        disc(s, 7, seed, (x, z, d, n) -> {
            if (d < 5.4) return n < 0.45 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : n < 0.7 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            return n < 0.5 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }, 8);
        // four broken pillars, vines down their outer faces
        int[][] corners = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
        for (int[] c : corners) {
            int h = 2 + r.nextInt(4);
            column(s, c[0], c[1], 1, h, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            if (s.here(c[0], c[1])) s.set(c[0], h + 1, c[1], Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            int ox = Integer.signum(c[0]), oz = Integer.signum(c[1]);
            for (int y = 1; y <= h; y++) {
                if (s.here(c[0] + ox, c[1])) s.set(c[0] + ox, y, c[1], vine(ox > 0 ? Direction.WEST : Direction.EAST));
                if (s.here(c[0], c[1] + oz)) s.set(c[0], y, c[1] + oz, vine(oz > 0 ? Direction.NORTH : Direction.SOUTH));
            }
        }
        // the coil's nest: a ring of leaves round the centre
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            double d = dist(x, z);
            if (!s.here(x, z)) continue;
            if (d > 2.4 && d < 3.3) s.set(x, 1, z, leaves(Blocks.JUNGLE_LEAVES));
            else if (d <= 2.4) s.set(x, 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
        // a serpent's head carved into the north edge
        column(s, 0, -6, 1, 3, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        if (s.here(0, -6)) s.set(0, 4, -6, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        // bamboo clumps outside
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            int x = (int) Math.round(Math.cos(a) * 8.5), z = (int) Math.round(Math.sin(a) * 8.5);
            int h = 3 + r.nextInt(4);
            if (!s.here(x, z)) continue;
            int g = s.ground(x, z);
            if (g < -5 || g > 5) continue;
            for (int y = 1; y <= h; y++) s.set(x, g + y, z, Blocks.BAMBOO.defaultBlockState());
        }
        ringScatter(s, r, 8, 7.5, 9.5, Blocks.FERN.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    }

    // ---- the Weaver Queen: a hollow of dead trunks and silk (dark forest, pale garden) --------------------------------

    private static void silkHollow(Sink s, RandomSource r, long seed, int variant) {
        boolean pale = variant == 1;
        Block log = pale ? Blocks.PALE_OAK_LOG : Blocks.DARK_OAK_LOG;
        Block dead = pale ? Blocks.STRIPPED_PALE_OAK_LOG : Blocks.STRIPPED_DARK_OAK_LOG;
        disc(s, 7, seed, (x, z, d, n) -> n < 0.4 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.65 ? Blocks.GRAVEL.defaultBlockState()
                : pale ? Blocks.PALE_MOSS_BLOCK.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState(), 9);
        // the hollow tree: a ring of trunk with a broken crown and silk inside
        BlockState trunk = log(log, Direction.Axis.Y);
        for (int x = -4; x <= -2; x++) for (int z = -4; z <= -2; z++) {
            boolean shell = x != -3 || z != -3;
            int h = 5 + (int) (noise(seed, x, z) * 3);
            if (!s.here(x, z)) continue;
            for (int y = 1; y <= h; y++) s.set(x, y, z, shell ? trunk : Blocks.COBWEB.defaultBlockState());
        }
        // four dead trunks and silk strung between their tops
        int[][] posts = new int[4][];
        for (int i = 0; i < 4; i++) {
            double a = i * Mth.HALF_PI + Mth.PI / 4 + (r.nextDouble() - 0.5) * 0.3;
            int x = (int) Math.round(Math.cos(a) * 6.2), z = (int) Math.round(Math.sin(a) * 6.2);
            int h = 5 + r.nextInt(2);
            posts[i] = new int[]{x, z, h};
            column(s, x, z, 1, h, log(dead, Direction.Axis.Y));
        }
        for (int i = 0; i < 4; i++) {
            int[] a = posts[i], b = posts[(i + 1) % 4], c = posts[(i + 2) % 4];
            strand(s, a, b, seed + i);
            if (i < 2) strand(s, a, c, seed + 10 + i);
        }
        // silk on the floor, old bones, a skull, mushrooms in the gloom (the den itself stays clear)
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            double d = dist(x, z);
            if (d > 7.2 || !s.here(x, z) || (Math.abs(x - 2) <= 1 && Math.abs(z - 2) <= 1)) continue;
            if (x >= -4 && x <= -2 && z >= -4 && z <= -2) continue;
            double n = noise(seed + 4, x, z);
            if (n < 0.13) s.set(x, 1, z, Blocks.COBWEB.defaultBlockState());
            else if (n < 0.16) s.set(x, 1, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
            else if (n < 0.175) s.set(x, 1, z, log(Blocks.BONE_BLOCK, (x & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z));
            else if (pale && n > 0.9) s.set(x, 1, z, Blocks.PALE_MOSS_CARPET.defaultBlockState());
        }
        if (s.here(4, -1)) s.set(4, 1, -1, Blocks.SKELETON_SKULL.defaultBlockState());
        ringScatter(s, r, 8, 7.5, 9.5, Blocks.COBWEB.defaultBlockState(), Blocks.BROWN_MUSHROOM.defaultBlockState());
    }

    /** Cobweb along the line between two post tops, sagging in the middle. */
    private static void strand(Sink s, int[] a, int[] b, long seed) {
        int steps = (int) Math.ceil(Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(Mth.lerp(t, a[0], b[0])), z = (int) Math.round(Mth.lerp(t, a[1], b[1]));
            int y = (int) Math.round(Mth.lerp(t, a[2], b[2]) - Math.sin(t * Math.PI) * 1.5);
            if (s.here(x, z) && noise(seed, x, z) < 0.8) s.set(x, y, z, Blocks.COBWEB.defaultBlockState());
        }
    }

    // ---- the Empress Moth: a petal bower under hanging lanterns (cherry grove, flower and birch forests) --------------

    private static void petalBower(Sink s, RandomSource r, long seed) {
        disc(s, 7, seed, (x, z, d, n) -> d < 5.6 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.5 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState(), 8);
        // petals over the floor
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            if (dist(x, z) > 7.2 || !s.here(x, z)) continue;
            double n = noise(seed + 6, x, z);
            if (n < 0.65) {
                s.set(x, 1, z, Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 1 + (int) (n * 6) % 4)
                        .setValue(FlowerBedBlock.FACING, Direction.from2DDataValue((int) (n * 40) & 3)));
            }
        }
        // the bower: four cherry trunks under a leaf roof, spore blossoms and lanterns hanging beneath
        int[][] trunks = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
        for (int[] t : trunks) column(s, t[0], t[1], 1, 5, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = dist(x, z);
            if (!s.here(x, z)) continue;
            if (d <= 5.6 + noise(seed + 1, x, z) * 0.8) s.set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
            if (d <= 3.6) s.set(x, 7, z, leaves(Blocks.CHERRY_LEAVES));
            if (d <= 2.2) s.set(x, 8, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
        }
        int[][] hangs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int i = 0; i < hangs.length; i++) {
            int x = hangs[i][0] * (i < 2 ? 1 : 1), z = hangs[i][1];
            if (!s.here(x, z)) continue;
            s.set(x, 5, z, i % 2 == 0 ? Blocks.SPORE_BLOSSOM.defaultBlockState() : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        int[][] lamps = {{3, 1}, {-3, -1}, {1, -3}, {-1, 3}};
        for (int[] l : lamps) if (s.here(l[0], l[1])) s.set(l[0], 5, l[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        ringScatter(s, r, 12, 7.5, 9.5, Blocks.FLOWERING_AZALEA.defaultBlockState(), Blocks.PINK_TULIP.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(),
                Blocks.AZALEA.defaultBlockState());
    }

    // ---- the Stonehorn: a ram's cairn on a mountain ledge (peaks, windswept hills) ------------------------------------

    private static void ramsCairn(Sink s, RandomSource r, long seed, int variant) {
        boolean snowy = variant == 1;
        disc(s, 8, seed, (x, z, d, n) -> {
            if (snowy) return n < 0.45 ? Blocks.SNOW_BLOCK.defaultBlockState() : n < 0.6 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            return n < 0.35 ? Blocks.STONE.defaultBlockState() : n < 0.55 ? Blocks.ANDESITE.defaultBlockState() : n < 0.75 ? Blocks.GRAVEL.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
        }, 8);
        // the cairn on the north side: a cone of stacked stones
        for (int y = 1; y <= 5; y++) {
            double rad = 2.6 - y * 0.5;
            for (int x = -3; x <= 3; x++) for (int z = -8; z <= -2; z++) {
                if (!s.here(x, z) || dist(x, z + 5) > rad + noise(seed + y, x, z) * 0.5) continue;
                double n = noise(seed + 30 + y, x, z);
                s.set(x, y, z, n < 0.5 ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.8 ? Blocks.ANDESITE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            }
        }
        // standing stones round the ledge
        for (int i = 0; i < 7; i++) {
            double a = i * Mth.TWO_PI / 7 + 0.2;
            int x = (int) Math.round(Math.cos(a) * 7.4), z = (int) Math.round(Math.sin(a) * 7.4);
            if (z < -5 && Math.abs(x) < 4) continue;
            column(s, x, z, 1, 2 + r.nextInt(2), Blocks.POLISHED_ANDESITE.defaultBlockState());
            if (s.here(x, z) && r.nextBoolean()) s.set(x, 3 + r.nextInt(2), z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        boulder(s, 5, -3, 1.4, seed, Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState());
        boulder(s, -5, 3, 1.2, seed + 3, Blocks.DIORITE.defaultBlockState(), Blocks.STONE.defaultBlockState());
        if (snowy) {
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                if (dist(x, z) > 8.2 || !s.here(x, z) || noise(seed + 12, x, z) > 0.5) continue;
                if (z <= -2 && dist(x, z + 5) < 3.2) continue;
                s.set(x, 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (noise(seed, x, z) * 3)));
            }
        }
        ringScatter(s, r, 10, 8.5, 10.5, Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState());
    }

    // ---- the Mycelord: a fairy ring of toadstools (mushroom fields) ----------------------------------------------------

    private static void sporeRing(Sink s, RandomSource r, long seed) {
        disc(s, 8, seed, (x, z, d, n) -> {
            double g = noise(seed + 7, x, z);
            if (g < 0.05 && d > 2.5) return Blocks.SHROOMLIGHT.defaultBlockState();
            return n < 0.6 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
        }, 9);
        // eight toadstools round the ring: a stem, a red or brown cap, a shroomlight underneath
        for (int i = 0; i < 8; i++) {
            double a = i * Mth.TWO_PI / 8 + r.nextDouble() * 0.3;
            int x = (int) Math.round(Math.cos(a) * 6.8), z = (int) Math.round(Math.sin(a) * 6.8);
            int h = 2 + r.nextInt(3);
            boolean red = r.nextBoolean();
            column(s, x, z, 1, h, Blocks.MUSHROOM_STEM.defaultBlockState());
            BlockState cap = (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (!s.here(x + dx, z + dz)) continue;
                s.set(x + dx, h + 1, z + dz, cap);
                if (red && dx == 0 && dz == 0) s.set(x, h + 2, z, cap);
            }
            if (s.here(x, z) && h >= 3) s.set(x, h, z, Blocks.SHROOMLIGHT.defaultBlockState());
        }
        // small mushrooms in an inner ring
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = dist(x, z);
            if (d < 3.5 || d > 5.5 || !s.here(x, z)) continue;
            double n = noise(seed + 11, x, z);
            if (n < 0.3) s.set(x, 1, z, (n < 0.15 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
        }
        ringScatter(s, r, 8, 8.5, 10.5, Blocks.RED_MUSHROOM.defaultBlockState(), Blocks.BROWN_MUSHROOM.defaultBlockState());
    }

    /** For the tests: the den must be standing room (two clear blocks over a solid floor). */
    public static boolean standingRoom(net.minecraft.world.level.Level level, BlockPos den) {
        return level.getBlockState(den).isAir() && level.getBlockState(den.above()).isAir() && !level.getBlockState(den.below()).isAir();
    }
}
