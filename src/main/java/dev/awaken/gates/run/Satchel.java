package dev.awaken.gates.run;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Satchel: loot that is carried, not owned, until it is claimed (Shrine extraction or the Lord's death). It has no
 * slot limit and never refuses (SPEC §13, "Gate spoils"). Loot moves in and out; nothing is ever copied. The same
 * merging list also holds what is held for you ({@link Held}).
 */
public final class Satchel {
    private Satchel() {}

    /** Moves all of {@code stack} into the list, topping up matching stacks first. The stack ends empty; returns the count moved. */
    public static int add(List<ItemStack> satchel, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int before = stack.getCount();
        for (ItemStack s : satchel) {
            if (stack.isEmpty()) break;
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                stack.shrink(move);
            }
        }
        while (!stack.isEmpty()) {
            int move = Math.min(stack.getCount(), stack.getMaxStackSize());
            satchel.add(stack.split(move));
        }
        satchel.removeIf(ItemStack::isEmpty);
        return before - stack.getCount();
    }

    public static int count(List<ItemStack> satchel) {
        int n = 0;
        for (ItemStack s : satchel) if (!s.isEmpty()) n++;
        return n;
    }

    public static int items(List<ItemStack> satchel) {
        int n = 0;
        for (ItemStack s : satchel) n += s.getCount();
        return n;
    }

    /** Takes everything out (the satchel ends empty) and returns it. */
    public static List<ItemStack> drain(List<ItemStack> satchel) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : satchel) if (!s.isEmpty()) out.add(s);
        satchel.clear();
        return out;
    }

    /**
     * STORY death rule: keeps half of every stack (and every other single item), returns what was kept; the rest is
     * destroyed. The satchel ends empty either way.
     */
    public static List<ItemStack> keepHalf(List<ItemStack> satchel) {
        List<ItemStack> kept = new ArrayList<>();
        int single = 0;
        for (ItemStack s : satchel) {
            if (s.isEmpty()) continue;
            if (s.getCount() >= 2) {
                kept.add(s.copyWithCount(s.getCount() / 2));
            } else if (single++ % 2 == 0) {
                kept.add(s.copy());
            }
        }
        satchel.clear();
        return kept;
    }

    public static int takeCount(List<ItemStack> satchel, net.minecraft.world.item.Item item, int max) {
        int taken = 0;
        for (ItemStack s : satchel) {
            if (taken >= max) break;
            if (s.is(item)) {
                int t = Math.min(max - taken, s.getCount());
                s.shrink(t);
                taken += t;
            }
        }
        satchel.removeIf(ItemStack::isEmpty);
        return taken;
    }
}
