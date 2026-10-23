package dev.awaken.hearth.block;

import dev.awaken.hearth.HearthContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** The ring's place on its course (1-based, 0 = not on a course). Drawn as a number inside the ring. */
public class CheckpointRingBlockEntity extends BlockEntity {
    private int index;

    public CheckpointRingBlockEntity(BlockPos pos, BlockState state) {
        super(HearthContent.CHECKPOINT_RING_BE.get(), pos, state);
    }

    public int index() {
        return index;
    }

    public void setIndex(int i) {
        if (i == index) return;
        index = i;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("index", index);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        index = in.getIntOr("index", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
