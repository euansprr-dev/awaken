package dev.awaken.bigday;

import dev.awaken.core.AwakenCommands;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.HearthRecord;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Big Days (SPEC §43.8): the raid. {@code /awaken admin bigday raid [first]} warns your home's raid for tonight,
 * {@code /awaken admin bigday dawn} ends it as first light would.
 */
public final class BigDayModule {
    private BigDayModule() {}

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> BigDays.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> BigDays.died(e));
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("bigday")
                .then(Commands.literal("raid").executes(c -> BigDays.force(c.getSource().getPlayerOrException(), false) ? 1 : 0)
                        .then(Commands.literal("first").executes(c -> BigDays.force(c.getSource().getPlayerOrException(), true) ? 1 : 0)))
                .then(Commands.literal("dawn").executes(c -> BigDays.dawnNow(c.getSource().getPlayerOrException()) ? 1 : 0)))));
        AwakenTests.register("bigday_away_raid", 40, BigDayModule::awayRaid);
    }

    /**
     * A raid while nobody's home breaks only doors and windows on its side and takes at most a chest's worth of food
     * (never the rest of the chest), and the mend puts every block back.
     */
    static void awayRaid(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(4, 1, 4));
        HearthRecord rec = new HearthRecord(UUID.randomUUID(), "test", o, dev.awaken.hearth.HearthService.OVERWORLD);
        BlockPos east = o.east(3), west = o.west(3), chest = o.north(2);
        h.setBlock(east.subtract(h.absolutePos(BlockPos.ZERO)), Blocks.GLASS);
        h.setBlock(west.subtract(h.absolutePos(BlockPos.ZERO)), Blocks.GLASS);
        h.setBlock(o.east(2).south(2).subtract(h.absolutePos(BlockPos.ZERO)), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, net.minecraft.core.Direction.EAST));
        h.setBlock(chest.subtract(h.absolutePos(BlockPos.ZERO)), Blocks.CHEST);
        ChestBlockEntity c = (ChestBlockEntity) level.getBlockEntity(chest);
        h.assertTrue(c != null, "a chest");
        for (int i = 0; i < 12; i++) c.setItem(i, new ItemStack(Items.BREAD, 4));
        c.setItem(12, new ItemStack(Items.DIAMOND, 3));
        h.assertTrue(BigDays.breakable(level.getBlockState(east)) && !BigDays.breakable(level.getBlockState(chest)), "glass breaks, a chest never");
        List<ItemStack> carried = BigDays.carry(level, rec, 8);
        h.assertTrue(carried.size() == BigDays.CARRIED && carried.stream().allMatch(s -> s.is(Items.BREAD)), "a chest's worth of food, nothing else (" + carried.size() + ")");
        h.assertTrue(c.getItem(12).is(Items.DIAMOND) && c.getItem(11).is(Items.BREAD), "the rest stays");
        h.assertTrue(BigDays.compass(o, o.east(400)).equals("east") && BigDays.compass(o, o.north(400)).equals("north"), "bearings read plainly");
        h.succeed();
    }
}
