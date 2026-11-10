package dev.awaken.finds.kit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One prop of the finds' kit (SPEC §58.8): a small block with a horizontal facing and a state of 0..3. What a state
 * means is the prop's own (a lamp cold or lit, a cairn one to four stones high, a dial's quarter turn); its models
 * come from tools/content/finds_content.py. A prop does nothing by itself: the find it stands in gives it meaning.
 */
public final class PropBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 3);

    private final VoxelShape shape;

    public PropBlock(BlockBehaviour.Properties p, float x0, float y0, float z0, float x1, float y1, float z1, boolean solid) {
        super(p);
        this.shape = Block.box(x0, y0, z0, x1, y1, z1);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(STATE, 0));
    }

    /** This prop in a state, facing a way. */
    public BlockState of(int state, Direction facing) {
        return defaultBlockState().setValue(STATE, state & 3).setValue(FACING, facing);
    }

    public BlockState of(int state) {
        return defaultBlockState().setValue(STATE, state & 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, STATE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
