package dev.awaken.kin.works;

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

import java.util.ArrayList;
import java.util.List;

/**
 * The Drop's heap (SPEC §43.12): up to {@link #CAP} stacks, in the order they landed. The Totters take from the top (the
 * last to land). Synced to clients so the heap is drawn as real things; each client notes when it first saw a stack, so it
 * falls in and settles. Nothing in it grows or changes.
 */
public final class DropBlockEntity extends BlockEntity {
    public static final int CAP = 64, SCHEMA = 1;

    private final List<ItemStack> heap = new ArrayList<>();
    /** Client only: the tick each heap slot's stack was first seen, for its fall. */
    public final List<Long> seen = new ArrayList<>();

    public DropBlockEntity(BlockPos pos, BlockState state) {
        super(WorksContent.DROP_BE.get(), pos, state);
    }

    public List<ItemStack> heap() {
        return heap;
    }

    public int size() {
        return heap.size();
    }

    public boolean full() {
        return heap.size() >= CAP;
    }

    /** Tips a stack onto the heap: onto a stack of the same thing on top if there's room, else as a new stack. What doesn't fit comes back. */
    public ItemStack add(ItemStack in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = in.copy();
        if (!heap.isEmpty()) {
            ItemStack top = heap.getLast();
            if (ItemStack.isSameItemSameComponents(top, s) && top.getCount() < top.getMaxStackSize()) {
                int n = Math.min(s.getCount(), top.getMaxStackSize() - top.getCount());
                top.grow(n);
                s.shrink(n);
            }
        }
        if (!s.isEmpty() && heap.size() < CAP) {
            heap.add(s);
            s = ItemStack.EMPTY;
        }
        changed();
        return s;
    }

    /** The top of the heap, taken off (an armful: one stack). */
    public ItemStack take() {
        if (heap.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = heap.removeLast();
        changed();
        return s;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.store("heap", ItemStack.CODEC.listOf(), List.copyOf(heap));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        heap.clear();
        heap.addAll(in.read("heap", ItemStack.CODEC.listOf()).orElse(List.of()).stream().map(ItemStack::copy).toList());
        if (level != null && level.isClientSide()) {
            long now = level.getGameTime();
            while (seen.size() > heap.size()) seen.removeLast();
            while (seen.size() < heap.size()) seen.add(now);
        }
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
        if (level != null) for (ItemStack s : heap) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, s);
        heap.clear();
    }
}
