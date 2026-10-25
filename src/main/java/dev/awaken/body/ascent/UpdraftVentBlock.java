package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The updraft vent: a Precursor grate breathing a column of rising air ({@link Updrafts}; SPEC §17.6). */
public class UpdraftVentBlock extends BaseEntityBlock {
    public UpdraftVentBlock(Properties p) {
        super(p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UpdraftVentBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // the client hears the column it stands near; nothing to tick on the server
        return level.isClientSide() ? createTickerHelper(type, AscentContent.UPDRAFT_VENT_BE.get(), UpdraftVentBlockEntity::clientTick) : null;
    }
}
