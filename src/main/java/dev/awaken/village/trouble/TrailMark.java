package dev.awaken.village.trouble;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A clue on the ground (SPEC §28.7): scuffed earth where someone was dragged along, turned the way the trail runs, or a
 * strip of their clothes caught on the way ({@code look} picks the scuff's shape, or the cloth's colour). Flat, no
 * collision, gone with a touch; the trouble that laid it takes it away again.
 */
public final class TrailMark extends HorizontalDirectionalBlock {
    public static final IntegerProperty LOOK = IntegerProperty.create("look", 0, 3);
    private static final VoxelShape FLAT = Block.box(0, 0, 0, 16, 1, 16);

    public TrailMark(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOOK, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LOOK);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FLAT;
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState s, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos np,
                                     BlockState ns, RandomSource random) {
        return dir == Direction.DOWN && !s.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(s, level, ticks, pos, dir, np, ns, random);
    }
}
