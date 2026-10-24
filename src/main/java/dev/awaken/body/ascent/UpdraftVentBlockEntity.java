package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An updraft vent's column. Nothing saved: the column is the block's (radius and height in {@link Updrafts}). Each side
 * lists its loaded vents with {@link Updrafts} (the client for the moving air, the server for Aether's refill), so a
 * query is a short list walk, not block reads.
 */
public class UpdraftVentBlockEntity extends BlockEntity {
    private int soundIn;

    public UpdraftVentBlockEntity(BlockPos pos, BlockState state) {
        super(AscentContent.UPDRAFT_VENT_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) Updrafts.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level != null) Updrafts.remove(level, worldPosition);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) Updrafts.remove(level, worldPosition);
        super.onChunkUnloaded();
    }

    /** Client: the column's breath, heard near it (a soft rush every few seconds, pitched a little at random). */
    static void clientTick(Level level, BlockPos pos, BlockState state, UpdraftVentBlockEntity be) {
        if (--be.soundIn > 0) return;
        be.soundIn = 50 + level.getRandom().nextInt(30);
        Updrafts.Client.breathe(level, pos);
    }
}
