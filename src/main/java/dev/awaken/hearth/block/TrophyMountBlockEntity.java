package dev.awaken.hearth.block;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.trophy.TrophyData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Holds the trophy hanging on a mount. Synced to clients so the renderer can show it. */
public class TrophyMountBlockEntity extends BlockEntity {
    private ItemStack trophy = ItemStack.EMPTY;

    public TrophyMountBlockEntity(BlockPos pos, BlockState state) {
        super(HearthContent.TROPHY_MOUNT_BE.get(), pos, state);
    }

    public ItemStack trophy() {
        return trophy;
    }

    public boolean isEmpty() {
        return trophy.isEmpty();
    }

    public @Nullable String trophyBoss() {
        TrophyData d = trophy.get(HearthContent.TROPHY.get());
        return d == null ? null : d.bossId();
    }

    public void setTrophy(ItemStack stack) {
        this.trophy = stack.copyWithCount(stack.isEmpty() ? 0 : 1);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public ItemStack take() {
        ItemStack out = trophy;
        setTrophy(ItemStack.EMPTY);
        return out;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!trophy.isEmpty()) out.store("trophy", ItemStack.OPTIONAL_CODEC, trophy);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        trophy = in.read("trophy", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !trophy.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, trophy);
        trophy = ItemStack.EMPTY;
    }
}
