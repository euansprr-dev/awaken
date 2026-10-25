package dev.awaken.terrain.landmark;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.frost.FrostBlocks;
import dev.awaken.terrain.frost.FrostSpiresFeature;
import dev.awaken.terrain.hero.HeroWorldgen;
import dev.awaken.terrain.hero.HeroTree;
import dev.awaken.terrain.landmark.LandmarkTests.Census;
import dev.awaken.terrain.mire.MireBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static dev.awaken.terrain.landmark.LandmarkTests.census;
import static dev.awaken.terrain.landmark.LandmarkTests.clear;
import static dev.awaken.terrain.landmark.LandmarkTests.forceChunks;
import static dev.awaken.terrain.landmark.LandmarkTests.is;
import static dev.awaken.terrain.landmark.LandmarkTests.platform;

/**
 * The third pair of Awakened biomes (docs/modules/lands.md): Frost Spires and Mirror Marsh. Their blocks, data trees
 * (the bluepine's tiers narrow toward its top; the weepwoods' tendrils hang in curtains and stand in water), biomes and
 * features (a sheaf of frost spires bursts every way from one root and stands; the weepwoods grow in the shallows and
 * never hang a strand into the water). Their landmark and hero tree are tested with the others (LandmarkTests,
 * HeroTests).
 */
public final class Lands3Tests {
    private Lands3Tests() {}

    public static void register() {
        AwakenTests.register("terrain_lands3_blocks", 20, Lands3Tests::blocks);
        AwakenTests.registerIsolated("terrain_lands3_trees_grow", 600, Lands3Tests::trees);
        AwakenTests.register("terrain_lands3_biomes", 20, Lands3Tests::biomes);
        AwakenTests.registerIsolated("terrain_frost_features", 600, Lands3Tests::frostFeatures);
        AwakenTests.registerIsolated("terrain_mire_trees_in_water", 600, Lands3Tests::mireTrees);
    }

    // ---- blocks -----------------------------------------------------------------------------------------------------

    private static void blocks(GameTestHelper h) {
        for (var b : FrostBlocks.all()) h.assertTrue(b.get().asItem() != Items.AIR, b.getId() + " has an item");
        for (var b : MireBlocks.all()) h.assertTrue(b.get().asItem() != Items.AIR, b.getId() + " has an item");
        for (var b : List.of(FrostBlocks.BLUEPINE_LOG, FrostBlocks.STRIPPED_BLUEPINE_LOG, FrostBlocks.BLUEPINE_WOOD, FrostBlocks.STRIPPED_BLUEPINE_WOOD,
                MireBlocks.WEEPWOOD_LOG, MireBlocks.STRIPPED_WEEPWOOD_LOG, MireBlocks.WEEPWOOD_WOOD, MireBlocks.STRIPPED_WEEPWOOD_WOOD)) {
            is(h, b.get(), BlockTags.LOGS, net.minecraft.tags.BlockItemTags.LOGS_THAT_BURN.block(), BlockTags.MINEABLE_WITH_AXE);
            h.assertTrue(b.get().asItem().builtInRegistryHolder().is(ItemTags.LOGS_THAT_BURN), b.getId() + " item burns as a log");
            h.assertTrue(b.get().builtInRegistryHolder().getData(NeoForgeDataMaps.FLAMMABLE) != null, b.getId() + " burns");
        }
        for (var b : List.of(FrostBlocks.BLUEPINE_LOG, FrostBlocks.BLUEPINE_WOOD, MireBlocks.WEEPWOOD_LOG, MireBlocks.WEEPWOOD_WOOD)) {
            h.assertTrue(b.get().builtInRegistryHolder().getData(NeoForgeDataMaps.TRANSFORMABLES) != null, b.getId() + " strips with an axe");
        }
        for (var b : List.of(FrostBlocks.BLUEPINE_LEAVES, MireBlocks.WEEPWOOD_LEAVES)) {
            is(h, b.get(), BlockTags.LEAVES, BlockTags.MINEABLE_WITH_HOE);
            h.assertTrue(b.get() instanceof LeavesBlock, b.getId() + " decays like leaves");
        }
        is(h, FrostBlocks.BLUEPINE_PLANKS.get(), BlockTags.PLANKS);
        is(h, MireBlocks.WEEPWOOD_PLANKS.get(), BlockTags.PLANKS);
        is(h, FrostBlocks.BLUEPINE_SAPLING.get(), BlockTags.SAPLINGS);
        is(h, MireBlocks.WEEPWOOD_SAPLING.get(), BlockTags.SAPLINGS);
        is(h, FrostBlocks.FROSTSPAR.get(), BlockTags.MINEABLE_WITH_PICKAXE, TagKey.create(Registries.BLOCK, Awaken.id("lands/frost_glints")));
        // the ground: soil that saplings take root in; hoarfrost never holds snow, so the biome reads ice-blue from afar
        for (Block b : List.of(FrostBlocks.HOARFROST.get(), MireBlocks.MIREPEAT.get())) is(h, b, BlockTags.DIRT, BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.SUPPORTS_VEGETATION);
        is(h, FrostBlocks.HOARFROST.get(), BlockTags.CANNOT_SUPPORT_SNOW_LAYER);
        for (Block b : List.of(FrostBlocks.FROSTSPAR.get(), FrostBlocks.FROSTSPAR_CLUSTER.get(), MireBlocks.MOONBLOOM.get())) {
            h.assertTrue(b.defaultBlockState().getLightEmission() == 0, b + " glows only in its model, never as light");
        }
        is(h, MireBlocks.WEEPWOOD_TENDRILS.get(), BlockTags.REPLACEABLE_BY_TREES);
        var features = h.getLevel().registryAccess().lookupOrThrow(Registries.FEATURE);
        for (var key : List.of(FrostBlocks.BLUEPINE_TREE, FrostBlocks.GIANT_BLUEPINE_TREE, MireBlocks.WEEPWOOD_TREE, MireBlocks.GREAT_WEEPWOOD_TREE)) {
            h.assertTrue(features.get(key).isPresent(), key.identifier() + " exists for the sapling");
        }
        h.succeed();
    }

    // ---- data trees -------------------------------------------------------------------------------------------------

    /** A data tree and what it must look like: wood, canopy, its top's height range and how far it may reach. */
    private record TreeSpec(String name, int minLogs, int minLeaves, int minTop, int maxTop, int maxReach) {}

    /**
     * Each tree from six seeds on a platform: it grows, stands, has its canopy, nothing loose or born decaying, within
     * reach of its trunk. The bluepines' tiers narrow toward the top (a pagoda, not a cloud); the weepwoods hang curtains.
     */
    private static void trees(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(0, 2, 0));
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        Predicate<BlockState> material = s -> s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(MireBlocks.WEEPWOOD_TENDRILS.get());
        StringBuilder report = new StringBuilder();
        List<TreeSpec> specs = List.of(new TreeSpec("bluepine", 12, 60, 11, 20, 9), new TreeSpec("giant_bluepine", 90, 160, 22, 40, 12),
                new TreeSpec("weepwood", 14, 70, 7, 17, 12), new TreeSpec("great_weepwood", 40, 120, 9, 22, 12),
                new TreeSpec("fallen_bluepine", 4, 0, 0, 3, 13), new TreeSpec("fallen_weepwood", 3, 0, 0, 3, 13));
        for (TreeSpec spec : specs) {
            Feature feature = features.getOrThrow(ResourceKey.create(Registries.FEATURE, Awaken.id("lands/" + spec.name()))).value();
            int grown = 0, logs = 0, leaves = 0, top = 0, reach = 0, strands = 0, narrowing = 0;
            for (long seed = 1; seed <= 6; seed++) {
                clear(level, origin, 16, 44); // a giant overtops the platform's own clearing
                platform(level, origin, Blocks.GRASS_BLOCK.defaultBlockState(), 16);
                if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 7919L), origin)) continue;
                grown++;
                Census c = census(level, origin, 16, 44, material, s -> s.is(BlockTags.LOGS));
                String what = spec.name() + " seed " + seed;
                h.assertTrue(c.decaying == 0, what + ": " + c.decaying + " leaves born decaying");
                h.assertTrue(c.loose == 0, what + ": " + c.loose + " blocks hang loose (first " + c.firstLoose + ")");
                h.assertTrue(c.reach <= spec.maxReach(), what + " reaches " + c.reach + " from its trunk");
                h.assertTrue(c.top >= spec.minTop() && c.top <= spec.maxTop(), what + " top " + c.top);
                logs += c.wood;
                leaves += c.leaves;
                top = Math.max(top, c.top);
                reach = Math.max(reach, c.reach);
                if (spec.name().endsWith("bluepine") && !spec.name().startsWith("fallen") && tiersNarrow(level, origin, c.top)) narrowing++;
                if (spec.name().endsWith("weepwood") && !spec.name().startsWith("fallen")) strands += count(level, origin, 16, 44, s -> s.is(MireBlocks.WEEPWOOD_TENDRILS.get()));
            }
            h.assertTrue(grown >= 5, spec.name() + " grows (" + grown + " of 6)");
            h.assertTrue(logs / grown >= spec.minLogs(), spec.name() + " has wood: " + logs / grown);
            h.assertTrue(leaves / grown >= spec.minLeaves(), spec.name() + " has a canopy: " + leaves / grown);
            if (spec.name().equals("bluepine") || spec.name().equals("giant_bluepine")) {
                h.assertTrue(narrowing >= grown - 1, spec.name() + "'s tiers narrow toward its top (" + narrowing + " of " + grown + ")");
            }
            if (spec.name().equals("weepwood") || spec.name().equals("great_weepwood")) {
                h.assertTrue(strands / grown >= 12, spec.name() + " hangs a curtain of tendrils (" + strands / grown + ")");
            }
            report.append(String.format(Locale.ROOT, " %s: %d logs, %d leaves, top %d, reach %d%s;", spec.name(), logs / grown, leaves / grown, top, reach,
                    strands > 0 ? ", " + strands / grown + " tendrils" : ""));
        }
        clear(level, origin, 16, 44);
        Awaken.LOG.info("[terrain] lands3 trees:{}", report);
        h.succeed();
    }

    /** The leaves' widest reach from the trunk in the lower third of the canopy is beyond the widest in its top third. */
    private static boolean tiersNarrow(ServerLevel level, BlockPos origin, int top) {
        int lowest = top, low = 0, high = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] reachAt = new int[top + 2];
        for (int y = 0; y <= top + 1; y++) {
            for (int x = -16; x <= 16; x++) {
                for (int z = -16; z <= 16; z++) {
                    if (!level.getBlockState(m.setWithOffset(origin, x, y, z)).is(BlockTags.LEAVES)) continue;
                    lowest = Math.min(lowest, y);
                    reachAt[y] = Math.max(reachAt[y], Math.max(Math.abs(x), Math.abs(z)));
                }
            }
        }
        int span = top + 1 - lowest;
        for (int y = lowest; y <= top + 1; y++) {
            if (y < lowest + span / 3) low = Math.max(low, reachAt[y]);
            else if (y > top + 1 - span / 3) high = Math.max(high, reachAt[y]);
        }
        return low > high;
    }

    private static int count(ServerLevel level, BlockPos origin, int r, int up, Predicate<BlockState> what) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = -6; y <= up; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) if (what.test(level.getBlockState(m.setWithOffset(origin, x, y, z)))) n++;
            }
        }
        return n;
    }

    // ---- biomes -----------------------------------------------------------------------------------------------------

    private static void biomes(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var placed = access.lookupOrThrow(Registries.PLACED_FEATURE);
        // biome, its landmark or hero tree, its archetype and the archetype's trees (gone), then our vegetation
        String[][] rows = {{"frost_spires", "aurora_spire", "snowy_taiga", "trees_taiga", "lands/frost_spires", "lands/small_frost_spires",
                "lands/bluepine_trees", "lands/frostspar_clusters"},
                {"mirror_marsh", "weeping_matriarch", "swamp", "trees_swamp", "lands/weepwood_trees", "lands/moonblooms"}};
        for (String[] r : rows) {
            Holder<Biome> b = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Awaken.id(r[0])));
            h.assertTrue(b.is(BiomeTags.IS_OVERWORLD), r[0] + " is an overworld biome");
            h.assertTrue(b.is(TagKey.create(Registries.BIOME, Awaken.id("has_structure/" + r[1]))), r[0] + " has its " + r[1]);
            h.assertTrue(b.is(TagKey.create(Registries.BIOME, Awaken.id("archetype/" + r[2]))), r[0] + " stands in for " + r[2]);
            var veg = b.value().getGenerationSettings().features().get(GenerationStep.Decoration.VEGETAL_DECORATION.ordinal());
            for (int k = 4; k < r.length; k++) {
                Holder<PlacedFeature> f = placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id(r[k])));
                h.assertTrue(veg.contains(f), r[0] + " places " + r[k]);
            }
            h.assertTrue(veg.stream().noneMatch(f -> f.is(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(r[3])))),
                    r[0] + " has none of its archetype's trees");
        }
        var structures = access.lookupOrThrow(Registries.STRUCTURE);
        h.assertTrue(structures.get(LandmarkWorldgen.structure(Landmark.AURORA_SPIRE)).isPresent(), "the Aurora Spire is a structure of the lands pack");
        h.assertTrue(structures.get(HeroWorldgen.structure(HeroTree.WEEPING_MATRIARCH)).isPresent(), "the Weeping Matriarch is a structure of the lands pack");
        var noises = access.lookupOrThrow(Registries.NOISE);
        for (String n : List.of("lands/rime", "lands/peat")) {
            h.assertTrue(noises.get(ResourceKey.create(Registries.NOISE, Awaken.id(n))).isPresent(), n + " is in the lands pack");
        }
        h.succeed();
    }

    // ---- Frost Spires -----------------------------------------------------------------------------------------------

    /**
     * Sheaves on snowy ground, eight seeds: every needle joins the ground (nothing floats), the sheaf stays within its
     * extent, the tallest reaches the feature's height, needles splay out on most sides of the root (a burst, never all
     * one way), the root freezes the ground round it to hoarfrost. Cost logged (a live level: above worldgen's).
     */
    private static void frostFeatures(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(0, 2, 0)).offset(5600, 0, 5600);
        forceChunks(level, origin, 2, true);
        var spires = new FrostSpiresFeature(UniformInt.of(4, 8), UniformInt.of(9, 16), 0.5F);
        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1);
        double total = 0, best = Double.MAX_VALUE;
        int built = 0, tallest = 0, crystals = 0, frost = 0;
        for (long seed = 1; seed <= 8; seed++) {
            platform(level, origin, Blocks.GRASS_BLOCK.defaultBlockState(), 16);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) level.setBlock(m.setWithOffset(origin, x, 0, z), snow, Block.UPDATE_CLIENTS);
            long t0 = System.nanoTime();
            boolean ok = spires.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 31L), origin.above());
            double ms = (System.nanoTime() - t0) / 1e6;
            total += ms;
            best = Math.min(best, ms);
            if (!ok) continue;
            built++;
            String what = "frost spires seed " + seed;
            Census c = census(level, origin, 16, 24, s -> s.is(FrostBlocks.FROSTSPAR.get()), s -> s.is(FrostBlocks.FROSTSPAR.get()));
            h.assertTrue(c.loose == 0, what + ": " + c.loose + " crystal blocks float (first " + c.firstLoose + ")");
            h.assertTrue(c.reach <= 13, what + " reaches " + c.reach);
            h.assertTrue(c.top >= 8 && c.top <= 19, what + " stands " + c.top + " tall");
            int sides = 0;
            for (int q = 0; q < 4; q++) {
                int qx = q % 2 == 0 ? 1 : -1, qz = q < 2 ? 1 : -1, n = 0;
                for (int x = 3; x <= 13; x++) {
                    for (int z = 0; z <= 13; z++) {
                        for (int y = 2; y <= 20; y++) if (level.getBlockState(m.setWithOffset(origin, qx * x, y, qz * z)).is(FrostBlocks.FROSTSPAR.get())) n++;
                    }
                }
                if (n > 0) sides++;
            }
            h.assertTrue(sides >= 2, what + " bursts every way from its root (" + sides + " of 4 sides)");
            int f = count(level, origin, 8, 0, s -> s.is(FrostBlocks.HOARFROST.get()));
            h.assertTrue(f >= 8, what + " freezes the ground round its root (" + f + " hoarfrost)");
            tallest = Math.max(tallest, c.top);
            crystals += c.wood;
            frost += f;
        }
        h.assertTrue(built >= 7, "frost spires grow on snowy ground (" + built + " of 8)");
        clear(level, origin, 16, 24);
        forceChunks(level, origin, 2, false);
        Awaken.LOG.info("[terrain] frost features: spires {} blocks avg, tallest {}, {} hoarfrost round the root; {} ms avg, {} fastest (live level)",
                crystals / Math.max(1, built), tallest, frost / Math.max(1, built), String.format(Locale.ROOT, "%.3f", total / 8),
                String.format(Locale.ROOT, "%.3f", best));
        h.succeed();
    }

    // ---- Mirror Marsh -----------------------------------------------------------------------------------------------

    /** Peat with one block of water over it at origin.y - 1, as the marsh's edge: a tree's foot goes in the water. */
    private static void shallows(ServerLevel level, BlockPos origin) {
        platform(level, origin.below(), MireBlocks.MIREPEAT.get().defaultBlockState(), 16);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) level.setBlock(m.setWithOffset(origin, x, -1, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Weepwoods grown in shallow water (one block over the peat, as the marsh's placement allows two): they stand, their
     * canopy is whole, and no tendril ever hangs into the water. Moonblooms float on water and nowhere else.
     */
    private static void mireTrees(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(0, 2, 0)).offset(6400, 0, 5600);
        forceChunks(level, origin, 2, true);
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        int grown = 0, strands = 0;
        for (String name : List.of("weepwood", "great_weepwood")) {
            Feature feature = features.getOrThrow(ResourceKey.create(Registries.FEATURE, Awaken.id("lands/" + name))).value();
            for (long seed = 1; seed <= 4; seed++) {
                // whatever stood here (the world's own trees at the box's edge, the last seed's tree) would count as loose
                clear(level, origin, 16, 30);
                shallows(level, origin);
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 104729L), origin.below())) continue;
                grown++;
                String what = name + " seed " + seed + " in water";
                Census c = census(level, origin, 16, 30,
                        s -> s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(MireBlocks.WEEPWOOD_TENDRILS.get()), s -> s.is(BlockTags.LOGS));
                h.assertTrue(c.decaying == 0 && c.loose == 0, what + ": " + c.decaying + " decaying, " + c.loose + " loose (first " + c.firstLoose + ")");
                h.assertTrue(c.wood > 5 && c.leaves > 30, what + " grows (" + c.wood + " logs, " + c.leaves + " leaves)");
                int wet = 0;
                for (int x = -16; x <= 16; x++) {
                    for (int z = -16; z <= 16; z++) {
                        if (level.getBlockState(m.setWithOffset(origin, x, -1, z)).is(MireBlocks.WEEPWOOD_TENDRILS.get())) wet++;
                    }
                }
                h.assertTrue(wet == 0, what + ": " + wet + " tendrils hang into the water");
                strands += count(level, origin, 16, 30, s -> s.is(MireBlocks.WEEPWOOD_TENDRILS.get()));
            }
        }
        h.assertTrue(grown >= 6, "weepwoods grow in the shallows (" + grown + " of 8)");
        // moonblooms: on water, never on the peat
        clear(level, origin, 16, 30);
        shallows(level, origin);
        BlockState bloom = MireBlocks.MOONBLOOM.get().defaultBlockState();
        h.assertTrue(bloom.canSurvive(level, origin), "a moonbloom floats on the water");
        BlockPos dry = origin.offset(20, 0, 0);
        level.setBlock(dry.below(), MireBlocks.MIREPEAT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(dry, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        h.assertFalse(bloom.canSurvive(level, dry), "a moonbloom does not sit on dry peat");
        clear(level, origin, 16, 30);
        forceChunks(level, origin, 2, false);
        Awaken.LOG.info("[terrain] mire trees in water: {} grown, {} tendrils avg, none in the water", grown, strands / Math.max(1, grown));
        h.succeed();
    }
}
