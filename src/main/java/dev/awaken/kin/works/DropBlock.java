package dev.awaken.kin.works;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Drop (SPEC §43.12): the Storehouse's sunken bin. Use it with something in hand and the whole stack goes in; sneak and
 * use it with an empty hand to empty your bag into it (your hotbar, armour and off hand stay). Plain use says what's in
 * it and who's shelving it.
 */
public final class DropBlock extends BaseEntityBlock {
    /** A low rim round a sunken floor: you can stand on the rim and look down into the heap. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 2, 0, 16, 10, 2), Block.box(0, 2, 14, 16, 10, 16),
            Block.box(0, 2, 2, 2, 10, 14), Block.box(14, 2, 2, 16, 10, 14));

    public DropBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DropBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DropBlockEntity be)
            Storehouse.tip(sl, sp, be, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DropBlockEntity be) {
            if (sp.isShiftKeyDown()) Storehouse.emptyBag(sl, sp, be);
            else Storehouse.status(sl, sp, be);
        }
        return InteractionResult.SUCCESS;
    }
}
