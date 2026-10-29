package dev.awaken.armour;

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

import java.util.List;

/**
 * What a Fitting Stand holds (schemaVersion 1): the suit fitted on it (helmet, chestplate, leggings, boots) and the piece
 * being made, if any. Synced to clients near so the suit and the work are drawn. Broken, it drops the suit and gives
 * back what the work took in.
 */
public class FittingStandBlockEntity extends BlockEntity {
    public static final int SCHEMA = 1;

    private final ItemStack[] suit = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private @Nullable ForgeWork work;
    /** The game tick the suit last woke (all four of one set fitted), for the flare; not saved (a client sees it change). */
    public long wokeAt = -1000;
    private boolean synced;

    public FittingStandBlockEntity(BlockPos pos, BlockState state) {
        super(ArmourContent.STAND_BE.get(), pos, state);
    }

    public @Nullable ForgeWork work() {
        return work;
    }

    public ItemStack suit(int slot) {
        return suit[slot];
    }

    void setWork(@Nullable ForgeWork work) {
        this.work = work;
        changed();
    }

    void setSuit(int slot, ItemStack stack) {
        suit[slot] = stack;
        changed();
    }

    /** The set all four pieces on the stand share, or null. */
    public @Nullable ArmourSet wholeSet() {
        ArmourSet set = null;
        for (ItemStack s : suit) {
            ArmourLooks.Look look = ArmourLooks.of(s);
            if (look == null || (set != null && look.set() != set)) return null;
            set = look.set();
        }
        return set;
    }

    void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, FittingStandBlockEntity be) {
        if (be.work != null && level instanceof ServerLevel sl) ArmourForge.tick(sl, be, be.work);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schemaVersion", SCHEMA);
        out.store("suit", ItemStack.OPTIONAL_CODEC.listOf(), List.of(suit));
        if (work != null) out.store("work", ForgeWork.CODEC, work);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ArmourSet before = wholeSet();
        List<ItemStack> s = in.read("suit", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < 4; i++) suit[i] = i < s.size() ? s.get(i) : ItemStack.EMPTY;
        work = in.read("work", ForgeWork.CODEC).orElse(null);
        // on a client, a set made whole by this update wakes (not one already whole when the stand came into view)
        ArmourSet after = wholeSet();
        if (level != null && level.isClientSide() && synced && after != null && after != before) wokeAt = level.getGameTime();
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
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (int i = 0; i < 4; i++) {
            if (!suit[i].isEmpty()) Containers.dropItemStack(level, x, y, z, suit[i]);
            suit[i] = ItemStack.EMPTY;
        }
        if (work != null) for (ItemStack m : work.materials) if (!m.isEmpty()) Containers.dropItemStack(level, x, y, z, m.copy());
        work = null;
    }
}
