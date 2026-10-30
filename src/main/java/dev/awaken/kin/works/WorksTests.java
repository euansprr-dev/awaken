package dev.awaken.kin.works;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;

import java.util.List;

/** GameTests for the works (SPEC §43.12): one per mechanism. CI runs them. */
final class WorksTests {
    private WorksTests() {}

    static void register() {
        AwakenTests.register("works_plan_builds", 40, WorksTests::planBuilds);
        AwakenTests.register("works_bays_sort", 40, WorksTests::baysSort);
    }

    /**
     * A Smeltery plan builds block by block from the chest beside it: floor, walls, roof, the door toward the front,
     * windows walled up when there's no glass, the furnace and barrel, and the vent stone last in the middle.
     */
    static void planBuilds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // up in the air, clear of the test's own ground, so every block of the room is the plan's to lay
        BlockPos c = h.absolutePos(new BlockPos(0, 4, 0));
        level.setBlock(c, WorksContent.WORK_PLAN.get().defaultBlockState().setValue(WorkPlanBlock.FACING, Direction.NORTH), 3);
        h.assertTrue(level.getBlockEntity(c) instanceof WorkPlanBlockEntity, "the plan has its block entity");
        WorkPlanBlockEntity be = (WorkPlanBlockEntity) level.getBlockEntity(c);
        be.work = "smeltery";
        BlockPos chestAt = c.east(5);
        level.setBlock(chestAt, Blocks.CHEST.defaultBlockState(), 3);
        Container chest = (Container) level.getBlockEntity(chestAt);
        for (int i = 0; i < 4; i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(4, new ItemStack(Items.OAK_DOOR));
        chest.setItem(5, new ItemStack(Items.FURNACE));
        chest.setItem(6, new ItemStack(Items.BARREL));
        chest.setItem(8, new ItemStack(Items.OAK_LOG, 16));
        // the middle waits for its vent stone: everything else goes up, then the plan says what it's waiting on
        int placed = 0;
        while (placed < 400 && WorkPlans.build(level, be)) placed++;
        h.assertTrue(be.waiting.contains("vent"), "with no vent stone it waits on one (" + be.waiting + ")");
        chest.setItem(7, new ItemStack(WorksContent.VENT_STONE_ITEM.get()));
        while (placed < 400 && WorkPlans.build(level, be)) placed++;
        List<WorkPlans.Entry> all = WorkPlans.layout(c, Direction.NORTH, WorkPlans.plan("smeltery"));
        h.assertTrue(be.step == all.size(), "every block of the plan is done (" + be.step + " of " + all.size() + ")");
        h.assertTrue(level.getBlockState(c).is(WorksContent.VENT_STONE.get()), "the vent stone sits where the plan lay");
        h.assertTrue(level.getBlockState(c.north(3)).getBlock() instanceof DoorBlock, "the door faces the way the plan did");
        h.assertTrue(level.getBlockState(c.below()).is(Blocks.COBBLESTONE), "the floor is stone (" + level.getBlockState(c.below()) + ")");
        h.assertTrue(level.getBlockState(c.above(4)).is(Blocks.COBBLESTONE), "the roof is on");
        for (WorkPlans.Entry e : all)
            if (e.mat() == WorkPlans.Mat.GLASS) h.assertTrue(level.getBlockState(e.pos()).is(Blocks.COBBLESTONE), "a window with no glass is walled up");
        h.assertFalse(WorkPlans.build(level, be), "a finished plan builds nothing more");
        h.succeed();
    }

    /** Everything you drop has a bay: ore and metal, wood, gems, food, plants, drops, gear, bells, stone, and odd things. */
    static void baysSort(GameTestHelper h) {
        Object[][] cases = {
                {Items.RAW_IRON, Storehouse.Bay.METAL}, {Items.IRON_INGOT, Storehouse.Bay.METAL}, {Items.OAK_LOG, Storehouse.Bay.WOOD},
                {Items.STICK, Storehouse.Bay.WOOD}, {Items.DIAMOND, Storehouse.Bay.GEMS}, {Items.BREAD, Storehouse.Bay.FOOD},
                {Items.WHEAT_SEEDS, Storehouse.Bay.PLANTS}, {Items.BONE, Storehouse.Bay.DROPS}, {Items.WOOL.white(), Storehouse.Bay.DROPS},
                {Items.IRON_SWORD, Storehouse.Bay.GEAR}, {Items.COBBLESTONE, Storehouse.Bay.STONE}, {Items.DIRT, Storehouse.Bay.STONE},
                {WorksContent.WOKEN_CLAPPER.get(), Storehouse.Bay.BELLS}, {Items.PAINTING, Storehouse.Bay.ODD}};
        for (Object[] k : cases) {
            ItemStack s = new ItemStack((net.minecraft.world.item.Item) k[0]);
            h.assertTrue(Storehouse.Bay.of(s) == k[1], s.getHoverName().getString() + " goes to " + k[1] + ", not " + Storehouse.Bay.of(s));
        }
        // a Totter's armful goes onto the shelf, and what doesn't fit comes back
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(0, 2, 0));
        level.setBlock(at, Blocks.BARREL.defaultBlockState(), 3);
        Container barrel = (Container) level.getBlockEntity(at);
        for (int i = 0; i < barrel.getContainerSize() - 1; i++) barrel.setItem(i, new ItemStack(Items.RAW_IRON, 64));
        ItemStack left = Storehouse.shelve(level, at, new ItemStack(Items.RAW_IRON, 70));
        h.assertTrue(left.getCount() == 6, "the last slot takes 64 and 6 come back (" + left.getCount() + ")");
        h.assertFalse(Storehouse.fits(barrel, new ItemStack(Items.RAW_IRON)), "the bay is full");
        h.succeed();
    }
}
