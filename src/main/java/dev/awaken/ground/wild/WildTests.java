package dev.awaken.ground.wild;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.ground.api.WildPlants;
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
