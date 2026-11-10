package dev.awaken.finds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The block a find's state lives in (SPEC §58.5): two under the ground at the site's middle, unseen and unbreakable.
 * It has no item; the world and {@code /awaken finds} place it.
 */
public final class FindHeartBlock extends Block implements EntityBlock {
    public FindHeartBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FindHeartBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != FindsContent.HEART_BE.get()) return null;
        BlockEntityTicker<FindHeartBlockEntity> t = level.isClientSide() ? FindHeartBlockEntity::clientTick : FindHeartBlockEntity::serverTick;
        return (BlockEntityTicker<T>) t;
    }
}
