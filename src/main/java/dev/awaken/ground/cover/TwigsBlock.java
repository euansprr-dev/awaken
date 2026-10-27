package dev.awaken.ground.cover;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A few fallen sticks lying one way: {@link #WOOD} 0 oak, 1 birch, 2 spruce bark; {@link #FACING} the way they point. */
public final class TwigsBlock extends GroundCoverBlock {
    public static final IntegerProperty WOOD = IntegerProperty.create("wood", 0, 2);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape ALONG_X = Block.box(1, 0, 5, 15, 2, 11), ALONG_Z = Block.box(5, 0, 1, 11, 2, 15);

    public TwigsBlock(Properties properties) {
        super(properties, ALONG_X);
        registerDefaultState(defaultBlockState().setValue(WOOD, 0).setValue(FACING, Direction.EAST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WOOD, FACING);
    }

    @Override
    protected VoxelShape shapeFor(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
}
