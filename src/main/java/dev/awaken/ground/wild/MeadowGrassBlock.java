package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Meadow grass (SPEC §41.2): knee high, the base of every wild field. A {@link BushBlock}, so it sways as the game's own
 * grass does. A grazed lawn is meadow grass that remembers what stood there ({@code regrow}: 0 nothing, 1 long grass, 2 rye)
 * and grows back through four stages on random ticks, over a few days; plain meadow grass takes no random ticks at all.
 * Bone meal grows it to long grass.
 */
public class MeadowGrassBlock extends BushBlock {
    public static final IntegerProperty REGROW = IntegerProperty.create("regrow", 0, 2);
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 3);
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 14.0);

    public MeadowGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(REGROW, 0).setValue(GROWTH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(REGROW, GROWTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(REGROW) != 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WildLife.regrowLawn(state, level, pos, random);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return level.getBlockState(pos.above()).isAir() && level.isInsideBuildHeight(pos.above());
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        WildLife.growTall(level, pos, WildBlocks.LONG_GRASS.get());
    }
}
