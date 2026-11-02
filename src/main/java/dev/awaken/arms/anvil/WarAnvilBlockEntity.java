package dev.awaken.arms.anvil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What a War Anvil holds (schemaVersion 1): the weapon being made, if any, and the finished one standing in its slot
 * until it's drawn. Synced to clients near so the work and the slot are drawn. Broken, it drops what stands in the slot
 * and gives back what the work took in.
 */
public class WarAnvilBlockEntity extends BlockEntity {
    public static final int SCHEMA = 1;

    private @Nullable AnvilWork work;
    private ItemStack slot = ItemStack.EMPTY;
    /** The game tick the slot was last filled, for its glow waking (a client sees it change; not saved). */
    public long stoodAt = -1000;
    private boolean synced;

    public WarAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(AnvilContent.ANVIL_BE.get(), pos, state);
    }

    public @Nullable AnvilWork work() {
        return work;
    }

    public ItemStack slot() {
        return slot;
    }

    void setWork(@Nullable AnvilWork work) {
        this.work = work;
        changed();
    }

    void setSlot(ItemStack stack) {
        slot = stack;
        changed();
    }

    void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, WarAnvilBlockEntity be) {
        if (be.work != null && level instanceof ServerLevel sl) WarAnvil.tick(sl, be, be.work);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schemaVersion", SCHEMA);
        if (!slot.isEmpty()) out.store("slot", ItemStack.CODEC, slot);
        if (work != null) out.store("work", AnvilWork.CODEC, work);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        boolean was = !slot.isEmpty();
        slot = in.read("slot", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        work = in.read("work", AnvilWork.CODEC).orElse(null);
        // on a client, a weapon stood in the slot by this update wakes (not one already there when the anvil came into view)
        if (level != null && level.isClientSide() && synced && !was && !slot.isEmpty()) stoodAt = level.getGameTime();
        synced = true;
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
        if (level == null) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.7, z = pos.getZ() + 0.5;
        if (!slot.isEmpty()) Containers.dropItemStack(level, x, y, z, slot);
        slot = ItemStack.EMPTY;
        if (work != null) for (ItemStack m : work.materials) if (!m.isEmpty()) Containers.dropItemStack(level, x, y, z, m.copy());
        work = null;
    }
}
