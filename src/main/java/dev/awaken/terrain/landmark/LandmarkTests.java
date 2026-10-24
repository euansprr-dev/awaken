package dev.awaken.terrain.landmark;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.fault.FaultBlocks;
import dev.awaken.terrain.fault.FaultNoise;
import dev.awaken.terrain.fault.FaultLinesFeature;
import dev.awaken.terrain.fault.FloatingShardFeature;
import dev.awaken.terrain.frost.FrostBlocks;
import dev.awaken.terrain.glass.GlassBlocks;
import dev.awaken.terrain.glass.GlassShardsFeature;
import dev.awaken.terrain.glass.MirrorPoolFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The second pair of Awakened biomes (docs/modules/lands.md): Glass Steppe and Shattered Highlands. Their blocks, data
 * trees, biomes, features (nothing floats that should stand, nothing is cut at a chunk border, cost per chunk), and
 * every landmark (the Frost Spires' Aurora Spire too): in its box, on or over the right ground, cost, and where it finds
 * sites.
 */
public final class LandmarkTests {
    private LandmarkTests() {}

    /** A landmark's shape is built once per landmark on a worldgen thread: the hero budget (SPEC §15). */
    static final double SHAPE_MS = 2.0;

    public static void register() {
        AwakenTests.register("terrain_lands2_blocks", 20, LandmarkTests::blocks);
        AwakenTests.registerIsolated("terrain_lands2_trees_grow", 600, LandmarkTests::trees);
        AwakenTests.register("terrain_lands2_biomes", 20, LandmarkTests::biomes);
        AwakenTests.registerIsolated("terrain_glass_features", 600, LandmarkTests::glassFeatures);
        AwakenTests.register("terrain_fault_noise", 200, LandmarkTests::faultNoise);
        AwakenTests.registerIsolated("terrain_fault_features", 800, LandmarkTests::faultFeatures);
        AwakenTests.register("terrain_landmark_shapes", 200, LandmarkTests::shapes);
        AwakenTests.registerIsolated("terrain_landmark_builds_in_box", 1200, LandmarkTests::builds);
        AwakenTests.register("terrain_landmark_sites", 600, LandmarkTests::sites);
    }

    // ---- blocks -----------------------------------------------------------------------------------------------------

    private static void blocks(GameTestHelper h) {
        for (var b : GlassBlocks.all()) h.assertTrue(b.get().asItem() != Items.AIR, b.getId() + " has an item");
        for (var b : FaultBlocks.all()) h.assertTrue(b.get().asItem() != Items.AIR, b.getId() + " has an item");
        for (var b : List.of(GlassBlocks.GLASSBARK_LOG, GlassBlocks.STRIPPED_GLASSBARK_LOG, GlassBlocks.GLASSBARK_WOOD, GlassBlocks.STRIPPED_GLASSBARK_WOOD)) {
            is(h, b.get(), BlockTags.LOGS, net.minecraft.tags.BlockItemTags.LOGS_THAT_BURN.block(), BlockTags.MINEABLE_WITH_AXE);
            h.assertTrue(b.get().asItem().builtInRegistryHolder().is(ItemTags.LOGS_THAT_BURN), b.getId() + " item burns as a log");
            h.assertTrue(b.get().builtInRegistryHolder().getData(NeoForgeDataMaps.FLAMMABLE) != null, b.getId() + " burns");
        }
        for (var b : List.of(GlassBlocks.GLASSBARK_LOG, GlassBlocks.GLASSBARK_WOOD)) {
            h.assertTrue(b.get().builtInRegistryHolder().getData(NeoForgeDataMaps.TRANSFORMABLES) != null, b.getId() + " strips with an axe");
        }
        is(h, GlassBlocks.GLASSBARK_LEAVES.get(), BlockTags.LEAVES, BlockTags.MINEABLE_WITH_HOE);
        h.assertTrue(GlassBlocks.GLASSBARK_LEAVES.get() instanceof LeavesBlock, "glassbark leaves decay like leaves");
        is(h, GlassBlocks.GLASSBARK_PLANKS.get(), BlockTags.PLANKS);
        is(h, GlassBlocks.GLASSBARK_SAPLING.get(), BlockTags.SAPLINGS);
        for (Block b : List.of(GlassBlocks.FUSED_GLASS.get(), GlassBlocks.SUNSHARD.get(), GlassBlocks.SCORCHED_GLASS.get(), FaultBlocks.FAULTSTONE.get(),
                FaultBlocks.FAULT_SEAM.get(), FaultBlocks.RIVEN_SHALE.get())) {
            is(h, b, BlockTags.MINEABLE_WITH_PICKAXE);
            h.assertTrue(b.defaultBlockState().getLightEmission() == 0, b + " glows only in its model, never as light");
        }
        h.assertTrue(GlassBlocks.SUNSHARD_CLUSTER.get().defaultBlockState().getLightEmission() == 0, "clusters glow only in their model");
        is(h, FaultBlocks.FAULTSTONE.get(), BlockTags.CANNOT_SUPPORT_SNOW_LAYER);
        is(h, FaultBlocks.FAULT_SEAM.get(), BlockTags.CANNOT_SUPPORT_SNOW_LAYER);
        is(h, GlassBlocks.FUSED_GLASS.get(), TagKey.create(Registries.BLOCK, Awaken.id("lands/glints")));
        is(h, FaultBlocks.FAULTSTONE.get(), TagKey.create(Registries.BLOCK, Awaken.id("lands/faults")));
        var features = h.getLevel().registryAccess().lookupOrThrow(Registries.FEATURE);
        for (var key : List.of(GlassBlocks.GLASSBARK_TREE, GlassBlocks.TALL_GLASSBARK_TREE)) {
            h.assertTrue(features.get(key).isPresent(), key.identifier() + " exists for the sapling");
        }
        h.succeed();
    }

    @SafeVarargs
    static void is(GameTestHelper h, Block block, TagKey<Block>... tags) {
        for (TagKey<Block> t : tags) h.assertTrue(block.builtInRegistryHolder().is(t), block + " is in #" + t.location());
    }

    // ---- data trees -------------------------------------------------------------------------------------------------

    /** Each glassbark from six seeds on a grass platform: it grows, stands, has a canopy, nothing loose or decaying. */
    private static void trees(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(0, 2, 0));
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        StringBuilder report = new StringBuilder();
        Object[][] specs = {{"glassbark", 6, 30, 6, 16, 12}, {"tall_glassbark", 10, 50, 9, 22, 13}, {"fallen_glassbark", 3, 0, 0, 3, 13}};
        for (Object[] spec : specs) {
            ResourceKey<Feature> key = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/" + spec[0]));
            Feature feature = features.getOrThrow(key).value();
            int grown = 0, logs = 0, leaves = 0, top = 0, reach = 0;
            for (long seed = 1; seed <= 6; seed++) {
                platform(level, origin, Blocks.GRASS_BLOCK.defaultBlockState(), 16);
                if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 7919L), origin)) continue;
                grown++;
                Census c = census(level, origin, 16, 30, s -> s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES), s -> s.is(BlockTags.LOGS));
                String what = spec[0] + " seed " + seed;
                h.assertTrue(c.decaying == 0, what + ": " + c.decaying + " leaves born decaying");
                h.assertTrue(c.loose == 0, what + ": " + c.loose + " blocks hang loose (first " + c.firstLoose + ")");
                h.assertTrue(c.reach <= (int) spec[5], what + " reaches " + c.reach + " from its trunk");
                h.assertTrue(c.top >= (int) spec[3] && c.top <= (int) spec[4], what + " top " + c.top);
                logs += c.wood;
                leaves += c.leaves;
                top = Math.max(top, c.top);
                reach = Math.max(reach, c.reach);
            }
            h.assertTrue(grown >= 5, spec[0] + " grows (" + grown + " of 6)");
            h.assertTrue(logs / grown >= (int) spec[1], spec[0] + " has wood: " + logs / grown);
            h.assertTrue(leaves / grown >= (int) spec[2], spec[0] + " has a canopy: " + leaves / grown);
            report.append(String.format(" %s: %d logs, %d leaves, top %d, reach %d;", spec[0], logs / grown, leaves / grown, top, reach));
        }
        clear(level, origin, 16, 30);
        Awaken.LOG.info("[terrain] lands2 trees:{}", report);
        h.succeed();
    }

    // ---- biomes -----------------------------------------------------------------------------------------------------

    private static void biomes(GameTestHelper h) {
        var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        var placed = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        String[][] rows = {{"glass_steppe", "gate_scar", "savanna", "trees_savanna", "lands/glass_shards", "lands/glassbark_trees", "lands/mirror_pool"},
                {"shattered_highlands", "shard_drift", "windswept_hills", "trees_windswept_hills", "lands/floating_shard", "lands/highland_trees", "lands/fault_lines"}};
        for (String[] r : rows) {
            Holder<Biome> b = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Awaken.id(r[0])));
            h.assertTrue(b.is(BiomeTags.IS_OVERWORLD), r[0] + " is an overworld biome");
            h.assertTrue(b.is(TagKey.create(Registries.BIOME, Awaken.id("has_structure/" + r[1]))), r[0] + " has its landmark " + r[1]);
            h.assertTrue(b.is(TagKey.create(Registries.BIOME, Awaken.id("archetype/" + r[2]))), r[0] + " stands in for " + r[2]);
            var steps = b.value().getGenerationSettings().features();
            var veg = steps.get(GenerationStep.Decoration.VEGETAL_DECORATION.ordinal());
            for (int k = 4; k <= 5; k++) {
                Holder<PlacedFeature> f = placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id(r[k])));
                h.assertTrue(veg.contains(f), r[0] + " places " + r[k]);
            }
            Holder<PlacedFeature> local = placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id(r[6])));
            h.assertTrue(steps.get(GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal()).contains(local), r[0] + " runs " + r[6] + " before vegetation");
            h.assertTrue(veg.stream().noneMatch(f -> f.is(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(r[3])))),
                    r[0] + " has none of its archetype's trees");
        }
        var structures = h.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Landmark l : Landmark.values()) h.assertTrue(structures.get(LandmarkWorldgen.structure(l)).isPresent(), l + " is a structure of the lands pack");
        var noises = h.getLevel().registryAccess().lookupOrThrow(Registries.NOISE);
        h.assertTrue(noises.get(FaultNoise.FAULT).isPresent() && noises.get(FaultNoise.TRENCH).isPresent(), "the fault noises are in the lands pack");
        h.succeed();
    }

    // ---- glass features ---------------------------------------------------------------------------------------------

    private static void glassFeatures(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(0, 2, 0)).offset(5200, 0, 5200);
        forceChunks(level, origin, 2, true);
        StringBuilder log = new StringBuilder();
        // shard spires: they stand (every crystal joins the ground), stay near their centre, reach their heights
        var shards = new GlassShardsFeature(UniformInt.of(3, 7), UniformInt.of(8, 14), 3.5F);
        double total = 0, worst = 0;
        int built = 0, tallest = 0, crystals = 0;
        for (long seed = 1; seed <= 8; seed++) {
            platform(level, origin, Blocks.GRASS_BLOCK.defaultBlockState(), 16);
            long t0 = System.nanoTime();
            boolean ok = shards.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 31L), origin);
            double ms = (System.nanoTime() - t0) / 1e6;
            if (seed > 1) {
                total += ms;
                worst = Math.max(worst, ms);
            }
            if (!ok) continue;
            built++;
            Census c = census(level, origin, 16, 24, s -> s.is(GlassBlocks.SUNSHARD.get()), s -> s.is(GlassBlocks.SUNSHARD.get()));
            h.assertTrue(c.loose == 0, "shards seed " + seed + ": " + c.loose + " crystal blocks float (first " + c.firstLoose + ")");
            h.assertTrue(c.reach <= 13, "shards seed " + seed + " reach " + c.reach);
            h.assertTrue(c.top >= 7 && c.top <= 17, "shards seed " + seed + " stand " + c.top + " tall");
            tallest = Math.max(tallest, c.top);
            crystals += c.wood;
        }
        h.assertTrue(built >= 7, "shard spires grow on steppe ground (" + built + " of 8)");
        log.append(String.format(" shard spires: %d blocks avg, tallest %d, %.3f ms avg, %.3f worst;", crystals / Math.max(1, built), tallest, total / 7, worst));
        // mirror pools: water level with the rim, nothing spills, black glass under it
        var pool = new MirrorPoolFeature(UniformInt.of(3, 7));
        total = 0;
        worst = 0;
        int pools = 0, water = 0;
        for (long seed = 1; seed <= 6; seed++) {
            platform(level, origin, Blocks.GRASS_BLOCK.defaultBlockState(), 16);
            long t0 = System.nanoTime();
            boolean ok = pool.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 131L), origin);
            double ms = (System.nanoTime() - t0) / 1e6;
            total += ms;
            worst = Math.max(worst, ms);
            if (!ok) continue;
            pools++;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int here = 0;
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    for (int y = -4; y <= 0; y++) {
                        BlockPos p = m.setWithOffset(origin, x, y, z).immutable();
                        if (!level.getBlockState(p).is(Blocks.WATER)) continue;
                        here++;
                        for (Direction d : Direction.Plane.HORIZONTAL) {
                            BlockState n = level.getBlockState(p.relative(d));
                            h.assertTrue(!n.isAir(), "pool seed " + seed + " spills at " + p.subtract(origin) + " toward " + d);
                        }
                        BlockState below = level.getBlockState(p.below());
                        h.assertTrue(below.is(Blocks.WATER) || below.is(GlassBlocks.SCORCHED_GLASS.get()), "pool seed " + seed + " bed at " + p.subtract(origin) + " is " + below);
                        h.assertTrue(level.getBlockState(p.above()).isAir() || level.getBlockState(p.above()).is(Blocks.WATER), "pool seed " + seed + " open above");
                    }
                }
            }
            h.assertTrue(here >= 15, "pool seed " + seed + " holds water (" + here + ")");
            water += here;
        }
        h.assertTrue(pools >= 5, "mirror pools form on level steppe (" + pools + " of 6)");
        log.append(String.format(" mirror pools: %d water blocks avg, %.3f ms avg, %.3f worst;", water / Math.max(1, pools), total / 6, worst));
        clear(level, origin, 16, 24);
        forceChunks(level, origin, 2, false);
        Awaken.LOG.info("[terrain] glass features:{}", log);
        h.succeed();
    }

    // ---- faults -----------------------------------------------------------------------------------------------------

    /**
     * The faults as the painter lays them, on this world's seed (a 1024-block square, every fourth row): how much of
     * the land is fault, how wide a fault crossed is and its seam, how often one crosses a line of travel, how much of
     * the band opens into trench (the studio numbers in lands.md).
     */
    private static void faultNoise(GameTestHelper h) {
        RandomState rs = h.getLevel().getChunkSource().randomState();
        Noise fault = FaultNoise.fault(rs), trench = FaultNoise.trench(rs);
        h.assertTrue(fault != null && trench != null, "the fault noises exist in a new world");
        int size = 1024, band = 0, open = 0, runs = 0, runLen = 0, seamRuns = 0, seamLen = 0, longest = 0;
        long t0 = System.nanoTime();
        for (int z = 0; z < size; z += 8) {
            int run = 0, srun = 0;
            for (int x = 0; x < size; x++) {
                double d = FaultNoise.distance(fault, x, z);
                double o = d < FaultNoise.MAX_HALF ? FaultNoise.open(trench.get(x, 0, z)) : 0;
                boolean in = d < FaultNoise.half(o), sin = d < FaultNoise.SEAM_HALF;
                if (in) {
                    band++;
                    run++;
                    if (FaultNoise.trenchDepth(d, o) > 0) open++;
                } else if (run > 0) {
                    runs++;
                    runLen += run;
                    longest = Math.max(longest, run);
                    run = 0;
                }
                if (sin) srun++;
                else if (srun > 0) {
                    seamRuns++;
                    seamLen += srun;
                    srun = 0;
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        double cells = size * (size / 8.0);
        double share = band / cells, width = runs == 0 ? 0 : runLen / (double) runs, seamWidth = seamRuns == 0 ? 0 : seamLen / (double) seamRuns;
        double perKm = runs / (size / 8.0) * (1000.0 / size);
        Awaken.LOG.info("[terrain] fault noise: band {}% of the land, crossings {} per km of transect, mean run {} blocks (longest {}), seam run {}, {}% of the band opens; {} ms for {} columns",
                String.format("%.1f", share * 100), String.format("%.1f", perKm), String.format("%.1f", width), longest, String.format("%.1f", seamWidth),
                String.format("%.0f", band == 0 ? 0 : open * 100.0 / band), String.format("%.0f", ms), (int) cells);
        h.assertTrue(share > 0.005 && share < 0.08, "faults cover a little of the land: " + share);
        h.assertTrue(width >= 2.0 && width <= 8.0, "a fault crossed is a few blocks wide: " + width);
        h.assertTrue(perKm >= 2 && perKm <= 30, "faults cross a transect every few hundred blocks: " + perKm + " per km");
        h.assertTrue(open > 0 && open < band, "some of the faults open into trenches");
        h.succeed();
    }

    private static void faultFeatures(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        RandomState rs = level.getChunkSource().randomState();
        Noise fault = FaultNoise.fault(rs), trench = FaultNoise.trench(rs);
        h.assertTrue(fault != null && trench != null, "the fault noises exist");
        // a stretch of fault that opens, somewhere out past the other tests
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int fx = 0, fz = 0;
        boolean found = false;
        for (int x = 9000; x < 13000 && !found; x += 3) {
            for (int z = 9000; z < 9600 && !found; z += 3) {
                if (Math.abs(fault.get(x, 0, z)) > 0.01) continue;
                if (FaultNoise.distance(fault, x, z) < 0.5 && FaultNoise.open(trench.get(x, 0, z)) > 0.8) {
                    fx = x;
                    fz = z;
                    found = true;
                }
            }
        }
        h.assertTrue(found, "an open fault is found");
        int y0 = h.absolutePos(BlockPos.ZERO).getY() + 20;
        BlockPos origin = new BlockPos(fx, y0, fz);
        int cx0 = fx >> 4, cz0 = fz >> 4;
        forceChunks(level, origin, 3, true);
        // ground as the highlands' surface lays it: riven shale over stone, its top at y0
        BlockState stone = Blocks.STONE.defaultBlockState(), shale = FaultBlocks.RIVEN_SHALE.get().defaultBlockState();
        BlockState fs = FaultBlocks.FAULTSTONE.get().defaultBlockState(), seam = FaultBlocks.FAULT_SEAM.get().defaultBlockState();
        int minX = (cx0 - 1) << 4, minZ = (cz0 - 1) << 4, maxX = ((cx0 + 1) << 4) + 15, maxZ = ((cz0 + 1) << 4) + 15;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = y0 + 1; y <= y0 + 30; y++) level.setBlock(m.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = y0 - 14; y < y0 - 2; y++) level.setBlock(m.set(x, y, z), stone, Block.UPDATE_CLIENTS);
                for (int y = y0 - 2; y <= y0; y++) level.setBlock(m.set(x, y, z), shale, Block.UPDATE_CLIENTS);
            }
        }
        // paint the nine chunks, each on its own as worldgen would
        double total = 0, worst = 0;
        int painted = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long t0 = System.nanoTime();
                painted += FaultLinesFeature.paint(level, fault, trench, (cx0 + dx) << 4, (cz0 + dz) << 4, level.getSeaLevel(), null);
                double ms = (System.nanoTime() - t0) / 1e6;
                total += ms;
                worst = Math.max(worst, ms);
            }
        }
        h.assertTrue(painted > 0, "the fault is laid");
        // the fault: only band columns touched, never deeper than the V allows, every floor lined, the seam on the line;
        // across chunk borders it runs on: no step between neighbouring floors is bigger at a border than inside a chunk
        int[][] floor = new int[maxX - minX + 1][maxZ - minZ + 1];
        int deepest = 0, stepInside = 0, stepBorder = 0, seams = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int top = y0;
                while (top > y0 - 14 && level.getBlockState(m.set(x, top, z)).isAir()) top--;
                floor[x - minX][z - minZ] = top;
                BlockState f = level.getBlockState(m.set(x, top, z));
                double d = FaultNoise.distance(fault, x, z);
                boolean fault2 = f.is(fs.getBlock()) || f.is(seam.getBlock());
                if (fault2) h.assertTrue(d < FaultNoise.MAX_HALF + 0.01, "only band columns are fault: " + x + "," + z + " is " + d + " from the line");
                if (y0 - top > 0) h.assertTrue(fault2, "the trench floor is lined at " + x + "," + z + ": " + f);
                if (f.is(seam.getBlock())) seams++;
                h.assertTrue(y0 - top <= FaultNoise.TRENCH_DEPTH, "trench depth " + (y0 - top) + " at " + x + "," + z);
                deepest = Math.max(deepest, y0 - top);
            }
        }
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int step = Math.abs(floor[x - minX][z - minZ] - floor[x + 1 - minX][z - minZ]);
                if (((x + 1) & 15) == 0) stepBorder = Math.max(stepBorder, step);
                else stepInside = Math.max(stepInside, step);
            }
        }
        h.assertTrue(seams > 10, "the seam runs down the line (" + seams + " columns)");
        h.assertTrue(deepest >= 4, "the open fault is a trench (" + deepest + " deep)");
        h.assertTrue(stepBorder <= Math.max(stepInside, 2) + 1, "the trench runs on across chunk borders (step " + stepBorder + " at a border, " + stepInside + " inside)");
        // floating shards over the fault: in open air, well clear of the ground under them, the seam at their tips
        int shards = 0, shardBlocks = 0, gap = Integer.MAX_VALUE;
        double sTotal = 0;
        for (int k = 0; k < 6; k++) {
            int sx = fx + (k - 3) * 9, sz = fz + (k % 2) * 3;
            long t0 = System.nanoTime();
            boolean ok = FloatingShardFeature.build(level, RandomSource.create(77L + k), sx, sz, 2 + k % 2, 10);
            sTotal += (System.nanoTime() - t0) / 1e6;
            if (ok) shards++;
        }
        boolean tip = false;
        for (int x = fx - 40; x <= fx + 40; x++) {
            for (int z = fz - 14; z <= fz + 14; z++) {
                int ground = floor[Math.max(0, Math.min(maxX - minX, x - minX))][Math.max(0, Math.min(maxZ - minZ, z - minZ))];
                for (int y = y0 + 1; y <= y0 + 30; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    shardBlocks++;
                    gap = Math.min(gap, y - ground - 1);
                    if (s.is(seam.getBlock()) && level.getBlockState(m.set(x, y - 1, z)).isAir()) tip = true;
                }
            }
        }
        h.assertTrue(shards >= 4, "floating shards form over the fault (" + shards + " of 6)");
        h.assertTrue(gap >= 6, "every shard hangs well clear of the ground under it (closest " + gap + " blocks)");
        h.assertTrue(tip, "a shard's tip is the seam's light");
        // leave nothing standing out there
        for (int x = minX - 20; x <= maxX + 20; x++) {
            for (int z = minZ - 20; z <= maxZ + 20; z++) {
                for (int y = y0 - 14; y <= y0 + 30; y++) {
                    if (!level.getBlockState(m.set(x, y, z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        forceChunks(level, origin, 3, false);
        Awaken.LOG.info("[terrain] fault features: {} fault columns, {} seam, deepest {}, floor step {} at borders / {} inside; {} ms a chunk avg, {} worst (live level); {} shards, {} blocks, {} ms each",
                painted, seams, deepest, stepBorder, stepInside, String.format("%.3f", total / 9), String.format("%.3f", worst), shards, shardBlocks,
                String.format("%.3f", sTotal / 6));
        h.succeed();
    }

    // ---- landmarks --------------------------------------------------------------------------------------------------

    /**
     * Shapes from eight seeds each: sizes as the art direction says, everything standing joins the ground, and the cost
     * of a shape against the hero budget (other work on the machine only ever adds time: the fastest seed is the honest
     * cost).
     */
    private static void shapes(GameTestHelper h) {
        StringBuilder log = new StringBuilder();
        for (long warm = 101; warm <= 103; warm++) {
            GateScar.plan(warm);
            ShardDrift.build(warm, 0.3F, new int[16]);
            AuroraSpire.plan(warm);
        }
        double total = 0, worst = 0, best = Double.MAX_VALUE;
        for (long seed = 1; seed <= 8; seed++) {
            long t0 = System.nanoTime();
            GateScar.Plan p = GateScar.plan(seed * 1_000_003L);
            double ms = (System.nanoTime() - t0) / 1e6;
            total += ms;
            worst = Math.max(worst, ms);
            best = Math.min(best, ms);
            String what = "gate scar seed " + seed;
            h.assertTrue(p.radius >= 15 && p.radius < 26, what + " is " + Math.round(p.radius * 2) + " across");
            h.assertTrue(p.depth >= 7 && p.depth <= 11, what + " is " + p.depth + " deep");
            h.assertTrue(p.rayAngle.length >= 8 && p.rayAngle.length <= 12, what + " splashes " + p.rayAngle.length + " rays");
            int spire = p.voxels.count(GateScar.SHARD);
            h.assertTrue(spire >= 600, what + " has its spires: " + spire + " blocks");
            int[] b = p.voxels.bounds();
            h.assertTrue(b[4] >= 16, what + " spires rise " + b[4]);
            int stumps = p.voxels.count(GateScar.TILES) + p.voxels.count(GateScar.POLISHED) + p.voxels.count(GateScar.CHISELED);
            h.assertTrue(stumps >= 20, what + ": the gate's stumps stand in the middle (" + stumps + " blocks)");
            h.assertTrue(p.voxels.get(0, p.air(0), 0) == GateScar.SHARD, what + ": a crystal stands where the gate was");
            int loose = unstood(p.voxels, GateScar.CLUSTER);
            h.assertTrue(loose == 0, what + ": " + loose + " cells not joined to a standing column");
            if (seed == 1) log.append(String.format(" gate scar: %d across, depth %.1f, %d spire blocks, top %d;", Math.round(p.radius * 2), p.depth, spire, b[4]));
        }
        log.append(String.format(" gate scar plan %.2f ms fastest, %.2f avg, %.2f worst;", best, total / 8, worst));
        h.assertTrue(best <= SHAPE_MS, "a gate scar plan costs " + best + " ms at its fastest");
        total = 0;
        worst = 0;
        best = Double.MAX_VALUE;
        for (long seed = 1; seed <= 8; seed++) {
            float angle = seed * 0.7F;
            int[] ground = new int[16];
            for (int i = 0; i < ground.length; i++) ground[i] = (int) (Math.sin(seed + i) * 6);
            long t0 = System.nanoTime();
            Voxels v = ShardDrift.build(seed * 1_000_003L, angle, ground);
            double ms = (System.nanoTime() - t0) / 1e6;
            total += ms;
            worst = Math.max(worst, ms);
            best = Math.min(best, ms);
            ShardDrift.Shard[] shards = ShardDrift.layout(seed * 1_000_003L, angle);
            String what = "shard drift seed " + seed;
            h.assertTrue(shards.length >= 6, what + " has " + shards.length + " shards");
            h.assertTrue(shards[0].radius() >= 5, what + "'s great shard is " + shards[0].radius() + " across");
            int cells = v.count(ShardDrift.SHALE) + v.count(ShardDrift.FAULT) + v.count(ShardDrift.SEAM);
            h.assertTrue(cells >= 400, what + " has " + cells + " rock blocks");
            h.assertTrue(v.count(ShardDrift.SEAM) >= shards.length, what + " shows the seam at its tips");
            // every shard hangs: nothing within eight blocks of the ground under it
            for (int i = 0; i < shards.length; i++) h.assertTrue(shards[i].lift() >= 10, what + " shard " + i + " hangs only " + shards[i].lift() + " up");
            if (seed == 1) log.append(String.format(" shard drift: %d shards, the great one %.1f across, %d rock blocks;", shards.length, shards[0].radius() * 2, cells));
        }
        log.append(String.format(" shard drift %.2f ms fastest, %.2f avg, %.2f worst;", best, total / 8, worst));
        h.assertTrue(best <= SHAPE_MS, "a shard drift costs " + best + " ms at its fastest");
        total = 0;
        worst = 0;
        best = Double.MAX_VALUE;
        for (long seed = 1; seed <= 8; seed++) {
            long t0 = System.nanoTime();
            AuroraSpire.Plan p = AuroraSpire.plan(seed * 1_000_003L);
            double ms = (System.nanoTime() - t0) / 1e6;
            total += ms;
            worst = Math.max(worst, ms);
            best = Math.min(best, ms);
            String what = "aurora spire seed " + seed;
            Voxels v = p.voxels;
            int[] b = v.bounds();
            int spar = v.count(AuroraSpire.SPAR);
            h.assertTrue(p.tarn >= 8.5 && p.tarn <= 13.5, what + "'s tarn is " + Math.round(p.tarn * 2) + " across");
            h.assertTrue(b[4] >= 40 && b[4] >= p.height - 3, what + "'s great needle rises " + b[4] + " (" + p.height + " planned)");
            h.assertTrue(spar >= 1500, what + " has " + spar + " frostspar blocks");
            // the great needle stands on the tarn's middle; nothing reaches the grid's edge (it would be cut off flat)
            h.assertTrue(v.get(0, 0, 0) == AuroraSpire.SPAR, what + ": the great needle stands in the middle");
            int edge = Math.max(Math.max(-b[0], b[3]), Math.max(-b[2], b[5]));
            h.assertTrue(edge < v.extent, what + " reaches " + edge + " of its " + v.extent + ": nothing cut off");
            h.assertTrue(v.count(AuroraSpire.CLUSTER) >= 4, what + " has slivers on its ice");
            int loose = unstood(v, AuroraSpire.CLUSTER);
            h.assertTrue(loose == 0, what + ": " + loose + " cells not joined to a standing column");
            if (seed == 1) log.append(String.format(" aurora spire: tarn %d across, %d tall, %d frostspar blocks, reach %d;", Math.round(p.tarn * 2), b[4], spar, edge));
        }
        log.append(String.format(" aurora spire %.2f ms fastest, %.2f avg, %.2f worst;", best, total / 8, worst));
        h.assertTrue(best <= SHAPE_MS, "an aurora spire costs " + best + " ms at its fastest");
        Awaken.LOG.info("[terrain] landmark shapes:{}", log);
        h.succeed();
    }

    /** Cells of a voxel shape (but its loose crystal slivers, {@code cluster}) that no walk from a standing column's foot reaches face to face. */
    static int unstood(Voxels v, byte cluster) {
        int n = v.cells.length, sxz = v.sx * v.sx;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int x = -v.extent; x <= v.extent; x++) {
            for (int z = -v.extent; z <= v.extent; z++) {
                int f = v.foot[v.column(x, z)];
                if (f == Voxels.NO_FOOT) continue;
                int i = v.index(x, f, z);
                if (v.cells[i] == Voxels.EMPTY || seen[i]) continue;
                seen[i] = true;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            int z = i % v.sx, x = (i / v.sx) % v.sx, y = i / sxz;
            int[] nb = {x + 1 < v.sx ? i + v.sx : -1, x > 0 ? i - v.sx : -1, z + 1 < v.sx ? i + 1 : -1, z > 0 ? i - 1 : -1,
                    y + 1 < v.sy ? i + sxz : -1, y > 0 ? i - sxz : -1};
            for (int j : nb) {
                if (j < 0 || seen[j] || v.cells[j] == Voxels.EMPTY) continue;
                seen[j] = true;
                queue[tail++] = j;
            }
        }
        int loose = 0;
        for (int i = 0; i < n; i++) if (v.cells[i] != Voxels.EMPTY && v.cells[i] != cluster && !seen[i]) loose++;
        return loose;
    }

    /**
     * A Gate Scar and an Aurora Spire on gently sloping ground and a Shard Drift over flat rock, chunk by chunk as
     * worldgen does: every block inside the box; the scar's crystal standing on the ground, its heart black glass well
     * below the plain; the spire's tarn level ice; the drift's shards all hanging clear. Cost per chunk logged.
     */
    private static void builds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        StringBuilder log = new StringBuilder();
        Landmark[] kinds = Landmark.values();
        BlockPos[] origins = new BlockPos[kinds.length];
        LandmarkPiece[] pieces = new LandmarkPiece[kinds.length];
        h.startSequence().thenExecute(() -> {
            for (int n = 0; n < kinds.length; n++) {
                BlockPos origin = origins[n] = h.absolutePos(new BlockPos(0, 2, 0)).offset(7000 + 500 * n, 20, 7000);
                forceChunks(level, origin, 4, true);
                // ground: grass on dirt on stone, rising one block every ten along x under what reshapes it
                ground(level, origin, 54, kinds[n] != Landmark.SHARD_DRIFT);
                LandmarkPiece piece = switch (kinds[n]) {
                    case GATE_SCAR -> LandmarkPiece.scar(origin, 424242L);
                    case SHARD_DRIFT -> LandmarkPiece.drift(origin, 424242L, 0.6F, new int[16]);
                    case AURORA_SPIRE -> LandmarkPiece.spire(origin, 424242L);
                };
                pieces[n] = piece;
                BoundingBox box = piece.getBoundingBox();
                int chunks = 0, placed = 0;
                double total = 0, worst = 0;
                for (int ccx = box.minX() >> 4; ccx <= box.maxX() >> 4; ccx++) {
                    for (int ccz = box.minZ() >> 4; ccz <= box.maxZ() >> 4; ccz++) {
                        BoundingBox clip = new BoundingBox(ccx << 4, level.getMinY(), ccz << 4, (ccx << 4) + 15, level.getMaxY(), (ccz << 4) + 15);
                        long t0 = System.nanoTime();
                        placed += piece.build(level, clip);
                        double ms = (System.nanoTime() - t0) / 1e6;
                        total += ms;
                        worst = Math.max(worst, ms);
                        chunks++;
                    }
                }
                h.assertTrue(placed > (kinds[n] == Landmark.SHARD_DRIFT ? 400 : 3000), kinds[n] + " built " + placed + " blocks");
                log.append(String.format(" %s: %d blocks over %d chunks, %.2f ms per chunk avg, %.2f worst (live level);", kinds[n].getSerializedName(),
                        placed, chunks, total / chunks, worst));
            }
        }).thenIdle(100).thenExecute(() -> {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int n = 0; n < kinds.length; n++) {
                BlockPos o = origins[n];
                BoundingBox box = pieces[n].getBoundingBox();
                String what = kinds[n].getSerializedName();
                int outside = 0;
                BlockPos firstOutside = null;
                Set<Block> mine = switch (kinds[n]) {
                    case GATE_SCAR -> Set.of(GlassBlocks.SUNSHARD.get(), GlassBlocks.FUSED_GLASS.get(), GlassBlocks.SCORCHED_GLASS.get(), Blocks.DEEPSLATE_TILES);
                    case SHARD_DRIFT -> Set.of(FaultBlocks.RIVEN_SHALE.get(), FaultBlocks.FAULTSTONE.get(), FaultBlocks.FAULT_SEAM.get());
                    case AURORA_SPIRE -> Set.of(FrostBlocks.FROSTSPAR.get(), FrostBlocks.FROSTSPAR_CLUSTER.get(), FrostBlocks.HOARFROST.get(), Blocks.PACKED_ICE);
                };
                int r = 56;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        for (int y = -20; y <= 80; y++) {
                            BlockState s = level.getBlockState(m.setWithOffset(o, x, y, z));
                            if (!mine.contains(s.getBlock())) continue;
                            if (!box.isInside(m) && outside++ == 0) firstOutside = m.immutable().subtract(o);
                        }
                    }
                }
                h.assertTrue(outside == 0, what + ": " + outside + " blocks outside its box (first " + firstOutside + ")");
                switch (kinds[n]) {
                    case GATE_SCAR -> scarBuilt(h, level, o, what);
                    case SHARD_DRIFT -> driftBuilt(h, level, o, what, mine, r);
                    case AURORA_SPIRE -> spireBuilt(h, level, o, what, LandmarkWorldgen.spire(424242L), log);
                }
                wipe(level, o, r);
                forceChunks(level, o, 4, false);
            }
            Awaken.LOG.info("[terrain] landmark builds:{}", log);
        }).thenSucceed();
    }

    /** The scar's crystal stands on the ground; near the heart (between its crystal and the gate's stumps) the bowl is deep and its glass black. */
    private static void scarBuilt(GameTestHelper h, ServerLevel level, BlockPos o, String what) {
        Census c = census(level, o.offset(0, -14, 0), 60, 60, s -> s.is(GlassBlocks.SUNSHARD.get()), s -> s.is(GlassBlocks.SUNSHARD.get()));
        h.assertTrue(c.loose == 0, what + ": " + c.loose + " crystal blocks float (first " + c.firstLoose + ")");
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean black = false;
        int deepest = 0;
        for (int[] d : new int[][]{{3, 0}, {0, 3}, {-2, 0}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}}) {
            int hx = o.getX() + d[0], hz = o.getZ() + d[1];
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, hx, hz) - 1;
            if (!level.getBlockState(m.set(hx, top, hz)).is(GlassBlocks.SCORCHED_GLASS.get())) continue;
            deepest = Math.max(deepest, o.getY() - 1 - top);
            black = true;
        }
        h.assertTrue(black, what + "'s heart is scorched glass");
        h.assertTrue(deepest >= 5, what + "'s bowl is " + deepest + " deep near the heart");
    }

    /** The drift's shards all hang clear of the flat ground under it (its top at o.y - 1). */
    private static void driftBuilt(GameTestHelper h, ServerLevel level, BlockPos o, String what, Set<Block> mine, int r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int lowest = Integer.MAX_VALUE, blocks = 0;
        for (int y = 0; y <= 70; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (!mine.contains(level.getBlockState(m.setWithOffset(o, x, y, z)).getBlock())) continue;
                    blocks++;
                    lowest = Math.min(lowest, y);
                }
            }
        }
        h.assertTrue(blocks >= 400, what + " hangs " + blocks + " blocks of rock");
        h.assertTrue(lowest >= 8, what + "'s lowest rock is " + lowest + " over the ground");
    }

    /**
     * On sloping ground the spire's tarn lies level, packed ice at the centre's ground across all of it, a hoarfrost ring
     * round it, nothing of the frostspar floats, and the great needle stands its height.
     */
    private static void spireBuilt(GameTestHelper h, ServerLevel level, BlockPos o, String what, AuroraSpire.Plan p, StringBuilder log) {
        Census c = census(level, o.offset(0, -14, 0), 40, 90, s -> s.is(FrostBlocks.FROSTSPAR.get()), s -> s.is(FrostBlocks.FROSTSPAR.get()));
        h.assertTrue(c.loose == 0, what + ": " + c.loose + " frostspar blocks float (first " + c.firstLoose + ")");
        int tall = c.top - 14;
        h.assertTrue(tall >= p.height - 4, what + "'s great needle stands " + tall + " (" + p.height + " planned)");
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int disc = 0, ice = 0, ring = 0, frost = 0, t = (int) Math.ceil(p.tarn + AuroraSpire.RING);
        for (int x = -t; x <= t; x++) {
            for (int z = -t; z <= t; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < p.tarn - 1) {
                    disc++;
                    if (level.getBlockState(m.setWithOffset(o, x, -1, z)).is(Blocks.PACKED_ICE)) ice++;
                } else if (d > p.tarn + 0.5 && d < p.tarn + AuroraSpire.RING - 1) {
                    ring++;
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, o.getX() + x, o.getZ() + z) - 1;
                    BlockState s = level.getBlockState(m.set(o.getX() + x, top, o.getZ() + z));
                    if (s.is(FrostBlocks.HOARFROST.get()) || s.is(FrostBlocks.FROSTSPAR.get()) || s.is(FrostBlocks.FROSTSPAR_CLUSTER.get())) frost++;
                }
            }
        }
        // where a needle's foot stands on the ice the ice is under it, so nearly all the disc reads as ice at o.y - 1
        h.assertTrue(ice >= disc * 0.9, what + "'s tarn lies level: " + ice + " of " + disc + " ice at the centre's ground");
        h.assertTrue(frost >= ring * 0.8, what + " is ringed with hoarfrost: " + frost + " of " + ring);
        log.append(String.format(" aurora spire: needle %d tall, tarn %d of %d level, ring %d of %d frosted;", tall, ice, disc, frost, ring));
    }

    /**
     * Sites on a real overworld: a Gate Scar finds dry, level savanna high over the sea; a Shard Drift finds a fault line
     * in the windswept hills; an Aurora Spire finds dry, level snow for its tarn (the GameTest world has no Lithostitched, so nothing places the Awakened biomes: their
     * archetypes stand in).
     */
    private static void sites(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var access = level.registryAccess();
        Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
        NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(source, settings);
        long seed = 777L;
        RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());
        var sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
        var resolver = source.createResolver(sampler);
        var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
        Noise fault = FaultNoise.fault(random);
        h.assertTrue(fault != null, "the fault noise exists");
        StringBuilder log = new StringBuilder();
        Predicate<Holder<Biome>> steppe = b -> b.is(net.minecraft.world.level.biome.Biomes.SAVANNA) || b.is(net.minecraft.world.level.biome.Biomes.SAVANNA_PLATEAU);
        // the highlands' archetypes and the other mountains (the archetypes alone are rare on one vanilla seed's sample)
        Predicate<Holder<Biome>> hills = b -> b.is(net.minecraft.world.level.biome.Biomes.WINDSWEPT_HILLS)
                || b.is(net.minecraft.world.level.biome.Biomes.WINDSWEPT_GRAVELLY_HILLS) || b.is(BiomeTags.IS_MOUNTAIN);
        // the Frost Spires' archetypes: the snowy lowlands it awakens
        Predicate<Holder<Biome>> snow = b -> b.is(net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA) || b.is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS)
                || b.is(net.minecraft.world.level.biome.Biomes.GROVE) || b.is(net.minecraft.world.level.biome.Biomes.ICE_SPIKES);
        for (Landmark l : Landmark.values()) {
            var spread = (RandomSpreadStructurePlacement) sets.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id(l.getSerializedName()))).value().placement();
            h.assertTrue(spread.spacing() * 16 >= 300 && spread.spacing() * 16 <= 600, l + ": one candidate per " + spread.spacing() * 16 + " blocks");
            Predicate<Holder<Biome>> valid = switch (l) {
                case GATE_SCAR -> steppe;
                case SHARD_DRIFT -> hills;
                case AURORA_SPIRE -> snow;
            };
            // candidates whose region has the biome (a vanilla seed's sample is mostly lowland: the search is what's tested)
            int found = 0, candidates = 0;
            for (int i = -15; i <= 15 && candidates < 40; i++) {
                for (int j = -15; j <= 15 && candidates < 40; j++) {
                    ChunkPos chunk = spread.getPotentialStructureChunk(seed, i * spread.spacing(), j * spread.spacing());
                    Holder<Biome> here = resolver.getNoiseBiome(QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(100),
                            QuartPos.fromBlock(chunk.getMiddleBlockZ()));
                    if (!valid.test(here)) continue;
                    candidates++;
                    var ctx = new Structure.GenerationContext(access, gen, source, sampler, random, level.getServer().getStructureTemplateManager(),
                            seed, chunk, level, valid);
                    BlockPos at;
                    if (l == Landmark.SHARD_DRIFT) {
                        double[] s = LandmarkStructure.driftSite(ctx);
                        at = s == null ? null : new BlockPos((int) s[0], (int) s[2], (int) s[1]);
                    } else {
                        at = LandmarkStructure.levelSite(ctx, l);
                    }
                    if (at == null) continue;
                    found++;
                    Holder<Biome> b = resolver.getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()));
                    h.assertTrue(valid.test(b), l + " site in " + b.getRegisteredName());
                    int top = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    int floor = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    h.assertTrue(top == floor && top > gen.getSeaLevel(), l + " site on dry land at " + at.toShortString());
                    h.assertTrue(at.getY() == top + 1, l + " centre is the first air over the ground: " + at.getY() + " over " + top);
                    switch (l) {
                        case SHARD_DRIFT -> h.assertTrue(FaultNoise.distance(fault, at.getX(), at.getZ()) < FaultNoise.HALF, l + " hangs over the fault at " + at.toShortString());
                        case GATE_SCAR -> h.assertTrue(top > gen.getSeaLevel() + 12, l + " bowl stays above the sea at " + at.toShortString());
                        case AURORA_SPIRE -> h.assertTrue(top > gen.getSeaLevel() + 2, l + " tarn stays above the sea at " + at.toShortString());
                    }
                }
            }
            log.append(String.format(" %s: %d of %d candidates;", l.getSerializedName(), found, candidates));
            if (l == Landmark.SHARD_DRIFT) log.append(" drift misses " + LandmarkStructure.DRIFT_MISSES + ";");
            h.assertTrue(found >= 1, l + " finds sites in its archetype (" + found + " of " + candidates + ", drift misses " + LandmarkStructure.DRIFT_MISSES + ")");
        }
        Awaken.LOG.info("[terrain] landmark sites:{}", log);
        h.succeed();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    static void forceChunks(ServerLevel level, BlockPos origin, int r, boolean force) {
        int cx0 = origin.getX() >> 4, cz0 = origin.getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) level.setChunkForced(cx0 + dx, cz0 + dz, force);
        if (force) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) level.getChunk(cx0 + dx, cz0 + dz);
    }

    /** A square platform of the given top over dirt at origin.y - 1, everything above it cleared. */
    static void platform(ServerLevel level, BlockPos origin, BlockState top, int r) {
        clear(level, origin, r, 30);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                level.setBlock(m.setWithOffset(origin, x, -1, z), top, Block.UPDATE_CLIENTS);
                for (int y = -6; y <= -2; y++) level.setBlock(m.setWithOffset(origin, x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    static void clear(ServerLevel level, BlockPos origin, int r, int up) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = up; y >= -6; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    m.setWithOffset(origin, x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Grass over dirt over stone, its top at origin.y - 1, rising one block every ten along x if sloped; air above. */
    static void ground(ServerLevel level, BlockPos origin, int r, boolean slope) {
        wipe(level, origin, r);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            int top = slope ? Math.floorDiv(x, 10) : 0;
            for (int z = -r; z <= r; z++) {
                for (int y = -16; y < top - 4; y++) level.setBlock(m.setWithOffset(origin, x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = top - 4; y < top - 1; y++) level.setBlock(m.setWithOffset(origin, x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.setWithOffset(origin, x, top - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    static void wipe(ServerLevel level, BlockPos origin, int r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 80; y >= -20; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    m.setWithOffset(origin, x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** What stands round {@code origin}: its material, the part of it that is wood, how high and far it reaches, what floats. */
    static final class Census {
        int wood, leaves, decaying, top, reach, loose;
        BlockPos firstLoose;
    }

    /**
     * Counts the material round {@code origin} (above origin.y) and walks it face to face from every piece of it resting
     * on solid ground that is not itself the material: anything the walk never reaches hangs loose.
     */
    static Census census(ServerLevel level, BlockPos origin, int r, int up, Predicate<BlockState> material, Predicate<BlockState> wood) {
        Census c = new Census();
        Set<BlockPos> all = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = -6; y <= up; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockState s = level.getBlockState(m.setWithOffset(origin, x, y, z));
                    if (!material.test(s)) continue;
                    BlockPos p = m.immutable();
                    all.add(p);
                    if (wood.test(s)) {
                        c.wood++;
                        c.top = Math.max(c.top, y);
                        BlockState below = level.getBlockState(p.below());
                        if (!material.test(below) && below.isFaceSturdy(level, p.below(), Direction.UP)) queue.add(p);
                    }
                    if (s.is(BlockTags.LEAVES)) {
                        c.leaves++;
                        if (s.hasProperty(LeavesBlock.DISTANCE) && s.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE && !s.getValue(LeavesBlock.PERSISTENT)) {
                            c.decaying++;
                        }
                    }
                    c.reach = Math.max(c.reach, Math.max(Math.abs(x), Math.abs(z)));
                }
            }
        }
        Set<BlockPos> seen = new HashSet<>(queue);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (all.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        for (BlockPos p : all) if (!seen.contains(p) && c.loose++ == 0) c.firstLoose = p.subtract(origin);
        return c;
    }
}
