package dev.awaken.arms.anvil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a War Anvil holds (schemaVersion 2): the weapon being made, if any, and the finished one standing in its slot
 * until it's drawn; and a Lord's anvil's colour (§46.6, -1 the gold of none; a v1 save reads none), carried on the
 * dropped anvil as its custom model data's colour. Synced to clients near so the work, the slot and the colour are
 * drawn. Broken, it drops what stands in the slot and gives back what the work took in.
 */
public class WarAnvilBlockEntity extends BlockEntity {
    public static final int SCHEMA = 2;
    /** A Lord's anvil with no Lord's colour: the gold its runes were cut in. */
    public static final int GOLD = 0xF3C443;

    private @Nullable AnvilWork work;
    private ItemStack slot = ItemStack.EMPTY;
    private int lord = -1;
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

    /** The Lord's colour its runes and Heart are lit in (RGB), or -1 for gold. */
    public int lord() {
        return lord;
    }

    void setLord(int rgb) {
        lord = rgb < 0 ? -1 : rgb & 0xFFFFFF;
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
        if (lord >= 0) out.putInt("lord", lord);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        boolean was = !slot.isEmpty();
        slot = in.read("slot", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        work = in.read("work", AnvilWork.CODEC).orElse(null);
        int wasLord = lord;
        lord = in.getIntOr("lord", -1);
        // on a client, a weapon stood in the slot by this update wakes (not one already there when the anvil came into view)
        if (level != null && level.isClientSide() && synced && !was && !slot.isEmpty()) stoodAt = level.getGameTime();
        // and a new colour is drawn: the block's tint reads it, so its section is built again
        if (level != null && level.isClientSide() && synced && wasLord != lord)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        synced = true;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (lord >= 0) components.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(lord)));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        CustomModelData d = components.get(DataComponents.CUSTOM_MODEL_DATA);
        Integer c = d == null ? null : d.getColor(0);
        lord = c == null ? -1 : c & 0xFFFFFF;
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
        if (work != null && !work.piece.isEmpty()) Containers.dropItemStack(level, x, y, z, work.piece.copy()); // the weapon being worked
        work = null;
    }
}
