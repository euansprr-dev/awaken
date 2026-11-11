package dev.awaken.finds.sets;

import dev.awaken.finds.kit.Mats;
import dev.awaken.finds.verb.Ctx;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Small things the set files share: the four ways, a log on its side, a leaf that stays, a stair turned, a part filled. */
final class Bits {
    private Bits() {}

    /** North, east, south, west. */
    static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    static BlockState axis(BlockState log, Direction.Axis a) {
        return log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, a) : log;
    }

    static BlockState leaf(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState stair(Mats m, Direction facing) {
        BlockState st = m.stairs();
        return st.hasProperty(StairBlock.FACING) ? st.setValue(StairBlock.FACING, facing) : st;
    }

    static BlockState top(BlockState slab) {
        return slab.hasProperty(SlabBlock.TYPE) ? slab.setValue(SlabBlock.TYPE, SlabType.TOP) : slab;
    }

    /** Every place of a part becomes one thing. */
    static void fill(Ctx c, String part, BlockState with) {
        for (int i = 0; i < c.count(part); i++) c.set(part, i, with);
    }
}
