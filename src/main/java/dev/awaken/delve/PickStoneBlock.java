package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The Delver's Pick in the stone (SPEC §34.4), at the end of an adit's drift: a mound of rock with the pick's head
 * driven into it (its facing points out of the drift's end face, toward you). Use it to pull your own pick; it stays for
 * the next player. It can't be broken.
 */
public final class PickStoneBlock extends HorizontalDirectionalBlock {
    private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Shapes.or(Block.box(3, 0, 5, 13, 5, 15), Block.box(6, 5, 7, 10, 15, 12)));

    public PickStoneBlock(BlockBehaviour.Properties p) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) WakingPick.pull(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
