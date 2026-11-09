package dev.awaken.gates.realms.kit;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Small things every builder wants: a slab turned top-half, a stair facing a way, one hung upside down, a candle lit. */
public final class Build {
    private Build() {}

    public static BlockState top(BlockState slab) {
        return slab.setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    public static BlockState stair(BlockState s, Direction f) {
        return s.setValue(StairBlock.FACING, f);
    }

    public static BlockState under(BlockState s, Direction f) {
        return s.setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, Half.TOP);
    }

    public static BlockState lit(BlockState s) {
        return s.getBlock().getStateDefinition().getProperty("lit") instanceof BooleanProperty p ? s.setValue(p, true) : s;
    }
}
