package dev.awaken.ground.wild;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.ground.api.WildPlants;
import dev.awaken.ground.wild.gen.WildGrowth;
import dev.awaken.ground.wild.gen.WildLattice;
import dev.awaken.ground.wild.gen.WildGroundFeature;
import dev.awaken.ground.wild.gen.WildnessPlacement;
import dev.awaken.living.wild.WildField;
import dev.awaken.terrain.PlantSway;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/** The wild's plants (SPEC §41.2): every block loads, drops and sways; grazing grows back; trails spring back; cover. */
public final class WildTests {
    private WildTests() {}

    public static void register() {
        AwakenTests.register("wild_plants_blocks", 40, WildTests::blocks);
        AwakenTests.register("wild_plants_crop", 40, WildTests::crop);
        AwakenTests.register("wild_plants_trample", 40, WildTests::trample);
        AwakenTests.register("wild_plants_cover", 40, WildTests::cover);
        AwakenTests.register("wild_plants_bands", 20, WildTests::bands);
        AwakenTests.register("wild_plants_ground", 40, WildTests::ground);
        AwakenTests.register("wild_plants_giant", 40, WildTests::giant);
        AwakenTests.register("wild_plants_data", 20, WildTests::data);
        AwakenTests.register("wild_plants_cost", 40, WildTests::cost);
    }

    /**
     * A chunk's grass costs little: the meadow's feature from the pack over a fresh 16 by 16 lawn, reading one chunk's lattice
     * worked out afresh (as worldgen does: one aligned chunk a run), best of five after a warm-up.
     */
    private static void cost(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.FEATURE, dev.awaken.Awaken.id("wild/ground_meadow"));
        var feature = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.FEATURE).getValueOrThrow(key);
        h.assertTrue(feature instanceof WildGroundFeature, "the meadow's grass is a wild_ground feature");
        WildGroundFeature f = (WildGroundFeature) feature;
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        RandomSource r = RandomSource.create(5);
        long best = Long.MAX_VALUE, lattice = Long.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
                for (int y = 1; y < 5; y++) h.setBlock(x, y, z, Blocks.AIR);
            }
            // a chunk far off whose lattice no one has read: its wildness stands in for this lawn's
            int bx = ((o.getX() >> 4) + 40 + k * 3) << 4, bz = o.getZ() & ~15;
            long t0 = System.nanoTime();
            WildLattice.at(level, bx, bz);
            long t1 = System.nanoTime();
            f.decorate(level, r, o.getX(), o.getZ(), o.getX() + 16, o.getZ() + 16,
                    (x, z) -> WildLattice.at(level, bx + ((x - o.getX()) & 15), bz + ((z - o.getZ()) & 15)), false);
            long t2 = System.nanoTime();
            if (k == 0) continue;                                         // the warm-up
            best = Math.min(best, t2 - t0);
            lattice = Math.min(lattice, t1 - t0);
        }
        double ms = best / 1e6;
        dev.awaken.Awaken.LOG.info("wild_plants_cost: a chunk's lattice and grass {} ms (the lattice alone {} ms)", String.format("%.3f", ms),
                String.format("%.3f", lattice / 1e6));
        h.assertTrue(ms < AwakenTests.cpuBudget(1.5), "a chunk's wildness and grass under 1.5 ms: " + ms);
        h.succeed();
    }

    /** The land's numbers (SPEC §41.3): the terrain lean, the band weights read between band middles, the lawn's colour. */
    private static void bands(GameTestHelper h) {
        h.assertTrue(WildField.lean(0.0, -1F, 1F, -1F) == 0.0, "tamed land leans nowhere");
        double most = WildField.lean(1.0, -1F, 1F, -1F);
        h.assertTrue(Math.abs(most - (WildField.LEAN_RUGGED + WildField.LEAN_FOREST + WildField.LEAN_VALLEY)) < 1e-9, "rugged wet valleys lean most: " + most);
        h.assertTrue(WildField.lean(1.0, 0.5F, -0.5F, 0.5F) == 0.0, "smooth dry peaks lean nowhere");
        List<Float> w = List.of(0F, 0.5F, 2F, 4F);
        h.assertTrue(WildnessPlacement.weight(w, 0.1) == 0 && WildnessPlacement.weight(w, 0.85) == 4 && WildnessPlacement.weight(w, 0.575) == 2,
                "a band's weight holds at its middle");
        double prev = WildnessPlacement.weight(w, 0);
        for (double x = 0; x <= 1.0; x += 0.01) {
            double k = WildnessPlacement.weight(w, x);
            h.assertTrue(k >= prev - 1e-9 && k - prev < 0.2, "rising smoothly at " + x + ": " + k);
            prev = k;
        }
        int lawn = 0xFF79C05A;
        int tamed = WildLawn.shift(lawn, 0.05), pasture = WildLawn.shift(lawn, 0.3), deep = WildLawn.shift(lawn, 0.95);
        h.assertTrue(pasture == lawn, "pasture keeps the biome's colour");
        h.assertTrue((tamed >> 8 & 255) > (lawn >> 8 & 255) && (tamed >> 16 & 255) < (lawn >> 16 & 255), "tamed is a touch greener");
        h.assertTrue((deep >> 16 & 255) > (lawn >> 16 & 255) && (deep & 255) < (lawn & 255), "deep wild is warmer");
        h.succeed();
    }

    /** The grass by band: tamed land's tall grass mowed, deep wild given its plants at the role's rate, one plant a column. */
    private static void ground(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 12; x++) for (int z = 0; z < 4; z++) {
            h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 1; y < 5; y++) h.setBlock(x, y, z, Blocks.AIR);
        }
        for (int x = 0; x < 6; x++) for (int z = 0; z < 4; z++) DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), h.absolutePos(new BlockPos(x, 1, z)), 3);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, dev.awaken.Awaken.id("wild/meadow"));
        WildGroundFeature f = new WildGroundFeature(tag, true, 0F, List.of(
                new WildGroundFeature.Role(List.of(WildBlocks.LONG_GRASS.get()), List.of(0F, 0F, 256F, 256F), 0, false)));
        int changed = f.decorate(level, RandomSource.create(7), o.getX(), o.getZ(), o.getX() + 12, o.getZ() + 4,
                (x, z) -> x - o.getX() < 6 ? 0.05 : 0.85, false);
        h.assertTrue(changed == 48, "every column changed: " + changed);
        for (int x = 0; x < 12; x++) for (int z = 0; z < 4; z++) {
            BlockState s = level.getBlockState(h.absolutePos(new BlockPos(x, 1, z)));
            if (x < 6) h.assertTrue(s.is(Blocks.SHORT_GRASS) && level.getBlockState(h.absolutePos(new BlockPos(x, 2, z))).isAir(), "mowed at " + x + "," + z + ": " + s);
            else h.assertTrue(s.is(WildBlocks.LONG_GRASS.get()) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                    && level.getBlockState(h.absolutePos(new BlockPos(x, 2, z))).is(WildBlocks.LONG_GRASS.get()), "long grass in the deep wild at " + x + "," + z + ": " + s);
        }
        h.succeed();
    }

    /** An old giant stands on open ground: a two-by-two trunk, root flares, leaves above. */
    private static void giant(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -3; x < 5; x++) for (int z = -3; z < 5; z++) {
            h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 1; y < 10; y++) h.setBlock(x, y, z, Blocks.AIR);
        }
        WildGrowth.OldGiant g = new WildGrowth.OldGiant(Blocks.OAK_LOG, Blocks.OAK_LEAVES, 16, 16);
        h.assertTrue(g.grow(level, h.absolutePos(new BlockPos(0, 1, 0)), RandomSource.create(3)), "it grows");
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) for (int y = 1; y <= 16; y++)
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(dx, y, dz))).is(Blocks.OAK_LOG), "trunk at " + dx + "," + y + "," + dz);
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(-1, 1, 0))).is(Blocks.OAK_LOG), "a root flare at its foot");
        BlockState crown = level.getBlockState(h.absolutePos(new BlockPos(0, 18, 0)));
        h.assertTrue(crown.is(Blocks.OAK_LEAVES) && crown.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT), "a crown that never decays: " + crown);
        // it gives up where it would not stand free
        h.setBlock(10, 0, 10, Blocks.GRASS_BLOCK);
        h.setBlock(10, 3, 10, Blocks.STONE);
        h.assertFalse(g.grow(level, h.absolutePos(new BlockPos(10, 1, 10)), RandomSource.create(3)), "not through stone");
        h.succeed();
    }

    /** The pack is whole: every wild placed feature in wild.json is a loaded placed feature. */
    private static void data(GameTestHelper h) {
        var placed = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        com.google.gson.JsonObject table;
        try (var in = WildTests.class.getResourceAsStream("/datapacks/awakened_ground/wild.json")) {
            table = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | NullPointerException e) {
            throw new AssertionError("wild.json", e);
        }
        int n = 0;
        for (var g : table.entrySet()) {
            for (var p : g.getValue().getAsJsonObject().getAsJsonArray("placed")) {
                String id = "wild/" + g.getKey() + "_" + p.getAsString();
                h.assertTrue(placed.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, dev.awaken.Awaken.id(id))).isPresent(),
                        id + " is a loaded placed feature");
                n++;
            }
        }
        h.assertTrue(n >= 30, "the wild's placed features: " + n);
        h.succeed();
    }

    /** A floor of grass at y 0 and air above, 7 by 7. */
    private static void lawn(GameTestHelper h) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 1; y < 5; y++) h.setBlock(x, y, z, Blocks.AIR);
        }
    }

    private static void blocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // every block has a loot table the server loaded, and drops what SPEC says without a tool
        for (var holder : WildBlocks.blocks()) {
            Block b = holder.get();
            var key = b.getLootTable();
            h.assertTrue(key.isPresent(), "a loot table for " + b);
            LootTable t = level.getServer().reloadableRegistries().getLootTable(key.get());
            h.assertTrue(t != LootTable.EMPTY, "the loot table loads for " + b);
        }
        lawn(h);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.assertTrue(drops(level, at, WildBlocks.COW_PARSLEY.get().defaultBlockState()).getFirst().is(WildBlocks.COW_PARSLEY.get().asItem()),
                "cow parsley drops itself");
        BlockState clover = WildBlocks.CLOVER.get().defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 3);
        h.assertTrue(drops(level, at, clover).getFirst().getCount() == 3, "three patches of clover drop three");
        h.assertTrue(drops(level, at, WildBlocks.WILD_SWARD.get().defaultBlockState()).getFirst().is(Items.DIRT), "sward without silk drops dirt");
        h.assertTrue(drops(level, at, WildBlocks.TRAMPLED_GRASS.get().defaultBlockState()).isEmpty(), "trampled grass drops nothing");
        // the grasses and flowers stand on a lawn, and each carries its sway
        h.assertTrue(WildBlocks.MEADOW_GRASS.get().defaultBlockState().canSurvive(level, at), "meadow grass on grass");
        h.assertTrue(PlantSway.rule(WildBlocks.MEADOW_GRASS.get().defaultBlockState()) == PlantSway.ROOTED, "meadow grass sways rooted");
        BlockState lower = WildBlocks.LONG_GRASS.get().defaultBlockState();
        h.assertTrue(PlantSway.rule(lower) == PlantSway.LOWER, "long grass's foot sways as a lower half");
        h.assertTrue(PlantSway.rule(lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)) == PlantSway.UPPER, "its head as an upper");
        h.assertTrue(PlantSway.rule(WildBlocks.RYE_STEMS.get().defaultBlockState()) == null, "rye's foot stands still");
        h.assertTrue(PlantSway.rule(WildBlocks.CLOVER.get().defaultBlockState()) == PlantSway.FLOWERBED, "clover lies as a flower bed");
        h.assertTrue(WildBlocks.THISTLE.get().defaultBlockState().canSurvive(level, at), "thistle on grass");
        h.setBlock(5, 0, 5, Blocks.SAND);
        h.assertTrue(WildBlocks.DRY_TUSSOCK.get().defaultBlockState().canSurvive(level, h.absolutePos(new BlockPos(5, 1, 5))), "a dry tussock on sand");
        h.assertFalse(WildBlocks.THISTLE.get().defaultBlockState().canSurvive(level, h.absolutePos(new BlockPos(5, 1, 5))), "a thistle not on sand");
        h.succeed();
    }

    private static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state) {
        return Block.getDrops(state, level, pos, null);
    }

    /** Grazed long grass and rye are a lawn that remembers them, and grow back through four stages. */
    private static void crop(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        lawn(h);
        BlockPos grass = h.absolutePos(new BlockPos(1, 1, 1)), rye = h.absolutePos(new BlockPos(4, 1, 4));
        WildLife.restore(level, grass, TrampledGrassBlock.LONG);
        WildLife.restore(level, rye, TrampledGrassBlock.RYE);
        h.assertTrue(level.getBlockState(rye).is(WildBlocks.RYE_STEMS.get()) && level.getBlockState(rye.above(2)).is(WildBlocks.WILD_RYE.get()), "rye stands three high");
        h.assertTrue(WildPlants.crop(level, grass.above()), "long grass crops from its head");
        h.assertTrue(WildPlants.crop(level, rye.above(2)), "rye crops from its ears");
        BlockState g = level.getBlockState(grass), r = level.getBlockState(rye);
        h.assertTrue(g.is(WildBlocks.MEADOW_GRASS.get()) && g.getValue(MeadowGrassBlock.REGROW) == 1, "a grazed lawn remembering long grass: " + g);
        h.assertTrue(r.is(WildBlocks.MEADOW_GRASS.get()) && r.getValue(MeadowGrassBlock.REGROW) == 2, "a grazed lawn remembering rye: " + r);
        h.assertTrue(level.getBlockState(rye.above()).isAir() && level.getBlockState(rye.above(2)).isAir(), "nothing left above the grazed rye");
        tickUntil(level, grass, s -> s.is(WildBlocks.LONG_GRASS.get()), h, "long grass grows back");
        tickUntil(level, rye, s -> s.is(WildBlocks.RYE_STEMS.get()), h, "rye grows back");
        h.assertTrue(level.getBlockState(rye.above(2)).is(WildBlocks.WILD_RYE.get()), "with its ears");
        h.succeed();
    }

    /** A herd presses rye flat; the trail stands back up as rye. A light step only bends meadow grass, and pressing again keeps it fresh. */
    private static void trample(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        lawn(h);
        BlockPos rye = h.absolutePos(new BlockPos(2, 1, 2)), meadow = h.absolutePos(new BlockPos(5, 1, 5));
        WildLife.restore(level, rye, TrampledGrassBlock.RYE);
        level.setBlock(meadow, WildBlocks.MEADOW_GRASS.get().defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(WildPlants.trample(level, rye.above(), WildPlants.HERD), "a herd presses the rye");
        BlockState flat = level.getBlockState(rye);
        h.assertTrue(flat.is(WildBlocks.TRAMPLED_GRASS.get()) && flat.getValue(TrampledGrassBlock.STAGE) == 0
                && flat.getValue(TrampledGrassBlock.FROM) == TrampledGrassBlock.RYE, "flat and fresh, remembering rye: " + flat);
        h.assertTrue(level.getBlockState(rye.above()).isAir(), "nothing standing above the trail");
        h.assertTrue(WildPlants.trample(level, meadow, WildPlants.LIGHT), "a light step on meadow grass");
        h.assertTrue(level.getBlockState(meadow).getValue(TrampledGrassBlock.STAGE) == 1, "a light step leaves it bent, not flat");
        h.assertTrue(WildPlants.trample(level, meadow, WildPlants.STAMPEDE), "a stampede presses it flat again");
        h.assertFalse(WildPlants.trample(level, meadow, WildPlants.LIGHT), "a light step on a fresh trail changes nothing");
        tickUntil(level, rye, s -> s.is(WildBlocks.RYE_STEMS.get()), h, "the trail springs back as rye");
        tickUntil(level, meadow, s -> s.is(WildBlocks.MEADOW_GRASS.get()), h, "and as meadow grass");
        h.succeed();
    }

    /** Rye hides a crouched body completely in the middle of a patch; the open lawn hides nothing. */
    private static void cover(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        lawn(h);
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) WildLife.restore(level, h.absolutePos(new BlockPos(x, 1, z)), TrampledGrassBlock.RYE);
        double mid = WildPlants.coverAt(level, h.absolutePos(new BlockPos(2, 1, 2)));
        double edge = WildPlants.coverAt(level, h.absolutePos(new BlockPos(1, 1, 2)));
        double open = WildPlants.coverAt(level, h.absolutePos(new BlockPos(5, 1, 5)));
        h.assertTrue(Math.abs(mid - 1.0) < 1e-6, "the middle of the rye hides you: " + mid);
        h.assertTrue(Math.abs(dev.awaken.living.api.Wildness.coverAt(level, h.absolutePos(new BlockPos(2, 1, 2))) - mid) < 1e-6,
                "the herds read the same cover through Wildness.coverAt");
        h.assertTrue(edge < mid && edge > 0.7, "its edge a little less: " + edge);
        h.assertTrue(open == 0.0, "the open lawn hides nothing: " + open);
        h.assertTrue(Math.abs(WildPlants.coverAt(level, h.absolutePos(new BlockPos(2, 0, 2))) - mid) < 1e-6, "given the ground, the plant on it");
        h.succeed();
    }

    /** Random ticks on {@code pos} until its block passes {@code done} (the odds per tick are small; this many is days of them). */
    private static void tickUntil(ServerLevel level, BlockPos pos, java.util.function.Predicate<BlockState> done, GameTestHelper h, String what) {
        RandomSource r = RandomSource.create(41);
        for (int i = 0; i < 4000 && !done.test(level.getBlockState(pos)); i++) {
            level.getBlockState(pos).randomTick(level, pos, r);
        }
        h.assertTrue(done.test(level.getBlockState(pos)), what + ": " + level.getBlockState(pos));
    }
}
