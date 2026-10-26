package dev.awaken.village.trouble;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The carved token a family nails up by its door for whoever brought their own home (SPEC §28.7): a round of oak on a
 * peg, your mark cut into it. Its facing points out of the wall, as a ladder's does. Use it to read what it says.
 */
public final class CarvedToken extends HorizontalDirectionalBlock {
    private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(4, 3, 14, 12, 12, 16));

    public CarvedToken(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE.get(s.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        Direction out = s.getValue(FACING);
        BlockPos wall = pos.relative(out.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, out);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState s = defaultBlockState().setValue(FACING, face);
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    protected BlockState updateShape(BlockState s, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos np,
                                     BlockState ns, RandomSource random) {
        if (dir.getOpposite() == s.getValue(FACING) && !s.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s, level, ticks, pos, dir, np, ns, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) TroubleDirector.readToken(sl, sp, pos);
        return InteractionResult.SUCCESS;
    }
}
