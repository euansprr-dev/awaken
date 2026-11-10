package dev.awaken.anims.station;

import net.minecraft.world.item.ItemStack;

/**
 * Living slots' reading of a tick (SPEC §64.7), kept free of client classes so the GameTests can check it: from what
 * the slots held and what they hold now, which stacks travelled from one slot to another. Only moves made without
 * carrying (a shift-click, a number key) count: a stack that came from or went to the pointer was in your hand. A tick
 * that changed more than {@link #MOST} slots (a Tidy, a sort) is left to whoever made it.
 */
public final class SlotMoves {
    private SlotMoves() {}

    public static final int MOST = 6;

    /**
     * The moves of one tick, as triples (from slot, to slot, how many) written into {@code out}; returns how many moves.
     * {@code byPointer}: the stack on the pointer changed this tick.
     */
    public static int read(ItemStack[] was, ItemStack[] now, boolean byPointer, int[] out) {
        if (byPointer) return 0;
        int n = Math.min(was.length, now.length), changed = 0;
        for (int i = 0; i < n; i++) if (!ItemStack.matches(was[i], now[i])) changed++;
        if (changed == 0 || changed > MOST) return 0;
        int[] lost = new int[n], gained = new int[n];
        for (int i = 0; i < n; i++) {
            ItemStack a = was[i], b = now[i];
            if (ItemStack.matches(a, b)) continue;
            if (ItemStack.isSameItemSameComponents(a, b)) {
                int d = b.getCount() - a.getCount();
                if (d > 0) gained[i] = d;
                else lost[i] = -d;
            } else {
                lost[i] = a.getCount();
                gained[i] = b.getCount();
            }
        }
        int moves = 0;
        for (int g = 0; g < n && (moves + 1) * 3 <= out.length; g++) {
            if (gained[g] <= 0) continue;
            for (int l = 0; l < n; l++) {
                if (lost[l] <= 0 || l == g || !ItemStack.isSameItemSameComponents(was[l], now[g])) continue;
                int k = Math.min(lost[l], gained[g]);
                lost[l] -= k;
                out[moves * 3] = l;
                out[moves * 3 + 1] = g;
                out[moves * 3 + 2] = k;
                moves++;
                break;
            }
        }
        return moves;
    }
}
