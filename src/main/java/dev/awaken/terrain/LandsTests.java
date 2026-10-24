package dev.awaken.terrain;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.block.LumenCapBlock;
import dev.awaken.terrain.tree.LandsTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Awakened Lands slice (docs/modules/lands.md): blocks, data trees and the two biomes. */
final class LandsTests {
    private LandsTests() {}

    /** A data tree and what it must look like: trunk height range and the most it may reach out from its trunk. */
    private record TreeSpec(ResourceKey<Feature> key, int minLogs, int minLeaves, int minTop, int maxTop, int maxReach) {}

    static void register() {
        AwakenTests.register("terrain_lands_blocks", 20, h -> {
            for (var b : LandsBlocks.all()) {
                Block block = b.get();
                h.assertTrue(block.asItem() != Items.AIR, b.getId() + " has an item");
                boolean burns = !(block instanceof net.minecraft.world.level.block.SaplingBlock) && b != LandsBlocks.LUMEN_CAP;
                h.assertTrue(!burns || block.builtInRegistryHolder().getData(NeoForgeDataMaps.FLAMMABLE) != null, b.getId() + " burns");
            }
            for (var b : List.of(LandsBlocks.ELDER_LOG, LandsBlocks.STRIPPED_ELDER_LOG, LandsBlocks.ELDER_WOOD, LandsBlocks.STRIPPED_ELDER_WOOD,
                    LandsBlocks.LUMEN_LOG, LandsBlocks.STRIPPED_LUMEN_LOG, LandsBlocks.LUMEN_WOOD, LandsBlocks.STRIPPED_LUMEN_WOOD)) {
                is(h, b.get(), BlockTags.LOGS, net.minecraft.tags.BlockItemTags.LOGS_THAT_BURN.block(), BlockTags.MINEABLE_WITH_AXE, BlockTags.PREVENTS_NEARBY_LEAF_DECAY);
                h.assertTrue(b.get().asItem().builtInRegistryHolder().is(ItemTags.LOGS_THAT_BURN), b.getId() + " item burns as a log");
            }
            for (var b : List.of(LandsBlocks.ELDER_LOG, LandsBlocks.ELDER_WOOD, LandsBlocks.LUMEN_LOG, LandsBlocks.LUMEN_WOOD)) {
                h.assertTrue(b.get().builtInRegistryHolder().getData(NeoForgeDataMaps.TRANSFORMABLES) != null, b.getId() + " strips with an axe");
            }
            for (var b : List.of(LandsBlocks.ELDER_LEAVES, LandsBlocks.LUMEN_LEAVES)) {
                is(h, b.get(), BlockTags.LEAVES, BlockTags.MINEABLE_WITH_HOE);
                h.assertTrue(b.get() instanceof LeavesBlock, b.getId() + " decays like leaves");
                h.assertTrue(b.get().defaultBlockState().getLightEmission() == 0, b.getId() + " glows only in its model, never as light");
            }
            for (var b : List.of(LandsBlocks.ELDER_PLANKS, LandsBlocks.LUMEN_PLANKS)) is(h, b.get(), BlockTags.PLANKS, BlockTags.MINEABLE_WITH_AXE);
            for (var b : List.of(LandsBlocks.ELDER_SAPLING, LandsBlocks.LUMEN_SAPLING)) is(h, b.get(), BlockTags.SAPLINGS);
            for (var b : List.of(LandsBlocks.LUMEN_MOSS, LandsBlocks.LUMEN_CAP, LandsBlocks.LUMEN_VINES)) is(h, b.get(), BlockTags.REPLACEABLE_BY_TREES);
            h.assertTrue(LandsBlocks.LUMEN_CAP.get().defaultBlockState().getLightEmission() == LumenCapBlock.LIGHT, "lumen caps give a little light");
            h.assertTrue(LandsBlocks.LUMEN_MOSS.get().defaultBlockState().getLightEmission() == 0, "lumen moss only glows in its model");
            // the saplings' trees exist (the GameTest world is new, so it has the lands pack)
            var features = h.getLevel().registryAccess().lookupOrThrow(Registries.FEATURE);
            for (var key : List.of(LandsTrees.ELDER_TREE, LandsTrees.YOUNG_ELDER_TREE, LandsTrees.LUMEN_TREE, LandsTrees.SMALL_LUMEN_TREE)) {
                h.assertTrue(features.get(key).isPresent(), key.identifier() + " exists for the saplings");
            }
            h.succeed();
        });

        // Every data tree, grown from several seeds on a grass platform: it stands, it has wood and a canopy, no leaf is
        // born decaying, nothing hangs loose, and it stays within reach of its trunk (a feature writes one chunk out at most).
        AwakenTests.registerIsolated("terrain_lands_trees_grow", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(new BlockPos(0, 2, 0));
            var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
            List<TreeSpec> specs = List.of(
                    new TreeSpec(LandsTrees.ELDER_TREE, 60, 150, 18, 34, 13),
                    new TreeSpec(LandsTrees.YOUNG_ELDER_TREE, 9, 25, 9, 18, 11),
                    new TreeSpec(LandsTrees.LUMEN_TREE, 40, 120, 10, 22, 13),
                    new TreeSpec(LandsTrees.SMALL_LUMEN_TREE, 6, 20, 6, 13, 9),
                    new TreeSpec(key("fallen_elder"), 4, 0, 0, 3, 13),
                    new TreeSpec(key("fallen_lumen"), 3, 0, 0, 3, 13));
            StringBuilder report = new StringBuilder();
            for (TreeSpec spec : specs) {
                Feature feature = features.getOrThrow(spec.key()).value();
                int grown = 0, logs = 0, leaves = 0, top = 0, reach = 0;
                for (long seed = 1; seed <= 6; seed++) {
                    platform(level, origin);
                    if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 7919L), origin)) continue;
                    grown++;
                    Census c = census(level, origin, 16, 40);
                    h.assertTrue(c.decaying == 0, spec.key().identifier() + " seed " + seed + ": " + c.decaying + " leaves born decaying");
                    h.assertTrue(c.loose == 0, spec.key().identifier() + " seed " + seed + ": " + c.loose + " blocks hang loose (first " + c.firstLoose + ")");
                    h.assertTrue(c.reach <= spec.maxReach(), spec.key().identifier() + " seed " + seed + " reaches " + c.reach + " from its trunk");
                    h.assertTrue(c.top >= spec.minTop() && c.top <= spec.maxTop(), spec.key().identifier() + " seed " + seed + " top " + c.top);
                    logs += c.logs;
                    leaves += c.leaves;
                    top = Math.max(top, c.top);
                    reach = Math.max(reach, c.reach);
                }
                h.assertTrue(grown >= 5, spec.key().identifier() + " grows (" + grown + " of 6)");
                h.assertTrue(logs / grown >= spec.minLogs(), spec.key().identifier() + " has wood: " + logs / grown);
                h.assertTrue(leaves / grown >= spec.minLeaves(), spec.key().identifier() + " has a canopy: " + leaves / grown);
                report.append(String.format(" %s: %d logs, %d leaves, top %d, reach %d;", spec.key().identifier().getPath(), logs / grown,
                        leaves / grown, top, reach));
            }
            clear(level, origin, 16, 40);
            Awaken.LOG.info("[terrain] data trees:{}", report);
            h.succeed();
        });

        AwakenTests.register("terrain_lands_biomes", 20, h -> {
            var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            var placed = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            for (String[] b : new String[][]{{"elderwood", "lands/elderwood_trees", "elder_colossus"}, {"lumen_forest", "lands/lumen_trees", "lumen_spire"}}) {
                Holder<Biome> biome = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Awaken.id(b[0])));
                h.assertTrue(biome.is(BiomeTags.IS_OVERWORLD) && biome.is(BiomeTags.IS_FOREST), b[0] + " is an overworld forest");
                h.assertTrue(biome.is(TagKey.create(Registries.BIOME, Awaken.id("has_structure/" + b[2]))), b[0] + " grows its hero tree " + b[2]);
                Holder<PlacedFeature> trees = placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id(b[1])));
                var veg = biome.value().getGenerationSettings().features().get(GenerationStep.Decoration.VEGETAL_DECORATION.ordinal());
                h.assertTrue(veg.contains(trees), b[0] + " grows its own trees");
                h.assertTrue(veg.stream().noneMatch(f -> f.is(ResourceKey.create(Registries.PLACED_FEATURE, net.minecraft.resources.Identifier.withDefaultNamespace(
                        b[0].equals("elderwood") ? "birch_tall" : "dark_forest_vegetation")))), b[0] + " has none of its archetype's trees");
            }
            h.succeed();
        });
    }

    private static ResourceKey<Feature> key(String name) {
        return ResourceKey.create(Registries.FEATURE, Awaken.id("lands/" + name));
    }

    @SafeVarargs
    private static void is(net.minecraft.gametest.framework.GameTestHelper h, Block block, TagKey<Block>... tags) {
        for (TagKey<Block> t : tags) h.assertTrue(block.builtInRegistryHolder().is(t), block + " is in #" + t.location());
    }

    /** A 33x33 grass platform over dirt at origin.y - 1, everything above it cleared. */
    static void platform(ServerLevel level, BlockPos origin) {
        clear(level, origin, 16, 40);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                level.setBlock(m.setWithOffset(origin, x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.setWithOffset(origin, x, -2, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.setWithOffset(origin, x, -3, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    static void clear(ServerLevel level, BlockPos origin, int r, int up) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = up; y >= -1; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) level.setBlock(m.setWithOffset(origin, x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** What a grown tree is made of: wood, leaves, the highest wood, the furthest reach, and blocks hanging loose. */
    static final class Census {
        int logs, leaves, decaying, top, reach, loose;
        BlockPos firstLoose;
    }

    static boolean treeMaterial(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(LandsBlocks.LUMEN_VINES.get());
    }

    /**
     * Counts the tree round {@code origin}, and walks it face to face from every piece of wood resting on the ground:
     * any tree block that walk never reaches hangs loose.
     */
    static Census census(ServerLevel level, BlockPos origin, int r, int up) {
        Census c = new Census();
        Set<BlockPos> tree = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 0; y <= up; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockState s = level.getBlockState(m.setWithOffset(origin, x, y, z));
                    if (!treeMaterial(s)) continue;
                    BlockPos p = m.immutable();
                    tree.add(p);
                    if (s.is(BlockTags.LOGS)) {
                        c.logs++;
                        c.top = Math.max(c.top, y);
                        BlockState below = level.getBlockState(p.below());
                        if (!treeMaterial(below) && below.isFaceSturdy(level, p.below(), Direction.UP)) queue.add(p);
                    } else if (s.is(BlockTags.LEAVES)) {
                        c.leaves++;
                        if (s.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE && !s.getValue(LeavesBlock.PERSISTENT)) c.decaying++;
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
                if (tree.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        for (BlockPos p : tree) {
            if (!seen.contains(p)) {
                if (c.loose++ == 0) c.firstLoose = p.subtract(origin);
            }
        }
        return c;
    }
}
