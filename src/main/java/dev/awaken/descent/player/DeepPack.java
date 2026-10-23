package dev.awaken.descent.player;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Deep Pack: 27 slots of loot picked up inside the Descent. It only becomes yours when you rest at a Waystone or
 * climb out. Pure container logic (no player access), so the GameTests can drive it directly.
 */
public final class DeepPack {
    public static final int SIZE = 27;

    private final NonNullList<ItemStack> slots = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public DeepPack() {}

    public static final MapCodec<DeepPack> MAP_CODEC = ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").xmap(DeepPack::of, DeepPack::stacks);

    public static final StreamCodec<RegistryFriendlyByteBuf, DeepPack> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(DeepPack::of, DeepPack::stacks);

    public static DeepPack of(List<ItemStack> items) {
        DeepPack p = new DeepPack();
        for (int i = 0; i < Math.min(SIZE, items.size()); i++) p.slots.set(i, items.get(i).copy());
        return p;
    }

    public List<ItemStack> stacks() {
        return new ArrayList<>(slots);
    }

    public ItemStack get(int i) {
        return slots.get(i);
    }

    public int used() {
        int n = 0;
        for (ItemStack s : slots) if (!s.isEmpty()) n++;
        return n;
    }

    public boolean isEmpty() {
        return used() == 0;
    }

    /**
     * Moves as much of {@code stack} as fits into the pack (the stack shrinks). Returns how many items moved.
     */
    public int insert(ItemStack stack) {
        int moved = 0;
        for (int i = 0; i < SIZE && !stack.isEmpty(); i++) {
            ItemStack s = slots.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                stack.shrink(n);
                moved += n;
            }
        }
        for (int i = 0; i < SIZE && !stack.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                int n = Math.min(stack.getCount(), stack.getMaxStackSize());
                slots.set(i, stack.split(n));
                moved += n;
            }
        }
        return moved;
    }

    /** Empties the pack, returning its stacks (non-empty only). */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (!slots.get(i).isEmpty()) out.add(slots.get(i));
            slots.set(i, ItemStack.EMPTY);
        }
        return out;
    }

    public int totalItems() {
        int n = 0;
        for (ItemStack s : slots) n += s.getCount();
        return n;
    }
}
