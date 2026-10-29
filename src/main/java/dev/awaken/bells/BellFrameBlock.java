package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The bell frame (SPEC §43.7): two posts and a beam with a bell under it, set inside your own Hearth. Your bells hang
 * here, the belfry and the tower grow round it, and it rings your song at dawn and dusk. One per home.
 */
public final class BellFrameBlock extends HorizontalDirectionalBlock {
    private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Shapes.or(Block.box(1, 0, 6, 3, 16, 10),
            Block.box(13, 0, 6, 15, 16, 10), Block.box(0, 14, 5.5, 16, 16, 10.5), Block.box(4, 3, 4, 12, 12.5, 12)));

    public BellFrameBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE.get(s.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel sl) BellTower.placed(sl, pos, by instanceof ServerPlayer sp ? sp : null);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) BellTower.used(sl, pos, sp);
        return InteractionResult.SUCCESS;
    }
}
