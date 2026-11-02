package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A bell lying (or hanging) where it was won (SPEC §43.16): under a branch where the Lampwing slept, on a Lord's arena floor,
 * where a village hid it. Use it to lift it: the bell rings out clear and it's yours to carry home. It can't be broken; it
 * goes once everyone it was left for has lifted theirs. Drawn by its renderer; {@code glow} while something sleeps in it.
 */
public final class FoundBellBlock extends Block implements EntityBlock {
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final BooleanProperty GLOW = BooleanProperty.create("glow");
    private static final VoxelShape HUNG = Block.box(3, 3, 3, 13, 16, 13), SITTING = Block.box(2, 0, 2, 14, 13, 14);

    public FoundBellBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(HANGING, false).setValue(GLOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(HANGING, GLOW);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(HANGING) ? HUNG : SITTING;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoundBellEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) FoundBells.lift(sl, pos, sp);
        return InteractionResult.SUCCESS;
    }
}
