package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trampled grass (SPEC §41.2): grass pressed flat along a herd's way, lying along {@code axis}. It springs back on random
 * ticks over a day or two: {@code stage} 0 fresh (flat and pale), 1 flat and greener, 2 half standing, then it is what stood
 * there again ({@code from}: 0 the game's short grass, 1 meadow grass, 2 long grass, 3 wild rye, 4 the game's tall grass).
 * Not a sway class: pressed grass lies still. Drops nothing.
 */
public class TrampledGrassBlock extends VegetationBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty FROM = IntegerProperty.create("from", 0, 4);
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    public static final int SHORT = 0, MEADOW = 1, LONG = 2, RYE = 3, TALL = 4;
    private static final VoxelShape FLAT = Block.column(16.0, 0.0, 1.0), RISING = Block.column(16.0, 0.0, 5.0);

    public TrampledGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(FROM, MEADOW).setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FROM, STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAGE) == 2 ? RISING : FLAT;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WildLife.springBack(state, level, pos, random);
    }
}
