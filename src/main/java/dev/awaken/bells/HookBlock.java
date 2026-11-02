package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A block with hooks bells hang on (SPEC §43.16): the Bell Frame, the Bell Beam, the Great Beam. Use a bell on it to hang
 * it on the free hook nearest where you touched; sneak-use with an empty hand to take one down; use with an empty hand to
 * ring the bell nearest your hand. Its bells are kept in a {@link HooksEntity}.
 */
public abstract class HookBlock extends HorizontalDirectionalBlock implements EntityBlock {
    protected HookBlock(BlockBehaviour.Properties p) {
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HooksEntity(pos, state);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        super.triggerEvent(state, level, pos, b0, b1);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(b0, b1);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(BellContent.BELL.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) Hanging.hang(sl, pos, sp, stack, hit.getLocation());
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            if (sp.isShiftKeyDown()) {
                if (!Hanging.takeDown(sl, pos, sp, hit.getLocation())) usedEmpty(sl, pos, sp);
            } else if (!Hanging.ringByHand(sl, pos, hit.getLocation())) usedEmpty(sl, pos, sp);
        }
        return InteractionResult.SUCCESS;
    }

    /** Used with nothing to ring or take down. */
    protected void usedEmpty(ServerLevel level, BlockPos pos, ServerPlayer p) {
        Bells.say(p, "[Nothing hangs here yet. Use a bell on it to hang it.]");
    }
}
