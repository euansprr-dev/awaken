package dev.awaken.storage;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Laying a section of slots out in order (SPEC §56). Pure: it reads what's there and says what should be, and it checks
 * the sum of every item before anyone writes. Same input, same output; a second Tidy changes nothing.
 */
final class Tidy {
    private Tidy() {}

    /** What the section should hold; each from/to pair is one item's trip (section slot numbers). */
    record Plan(ItemStack[] after, int[] from, int[] to, boolean changed) {}

    private static final class Pile {
        final ItemStack proto;
        final List<int[]> sources = new ArrayList<>(); // slot, count
        int total;

        Pile(ItemStack proto) {
            this.proto = proto;
        }
    }

    private record Out(ItemStack stack, Pile pile) {}

    private static final Comparator<Out> ORDER = Comparator.<Out>comparingInt(o -> Sorts.rank(o.stack().getItem()))
            .thenComparing(o -> -o.stack().getCount())
            .thenComparingInt(o -> o.stack().getDamageValue())
            .thenComparingInt(o -> ItemStack.hashItemAndComponents(o.stack()));

    /** @return the arrangement, or null if it can't be done without changing what's there */
    static @Nullable Plan plan(ItemStack[] now, boolean[] locked, int cols, int slotLimit) {
        List<Pile> piles = new ArrayList<>();
        for (int i = 0; i < now.length; i++) {
            if (locked[i] || now[i].isEmpty()) continue;
            Pile p = null;
            for (Pile q : piles) {
                if (ItemStack.isSameItemSameComponents(q.proto, now[i])) {
                    p = q;
                    break;
                }
            }
            if (p == null) piles.add(p = new Pile(now[i]));
            p.sources.add(new int[] {i, now[i].getCount()});
            p.total += now[i].getCount();
        }
        List<Out> out = new ArrayList<>();
        for (Pile p : piles) {
            int max = Math.min(p.proto.getMaxStackSize(), slotLimit);
            for (int n = p.total; n > 0; n -= max) out.add(new Out(p.proto.copyWithCount(Math.min(n, max)), p));
        }
        out.sort(ORDER);

        int[] at = place(out, locked, cols, true);
        if (at == null) at = place(out, locked, cols, false);
        if (at == null) return null;

        ItemStack[] after = new ItemStack[now.length];
        for (int i = 0; i < now.length; i++) after[i] = locked[i] ? now[i] : ItemStack.EMPTY;
        for (int k = 0; k < out.size(); k++) after[at[k]] = out.get(k).stack();
        if (!sameTotals(now, after)) return null;

        // where each stack's items came from, for the flights
        List<int[]> trips = new ArrayList<>();
        int[] next = new int[piles.size()];
        int[] used = new int[piles.size()];
        for (int k = 0; k < out.size(); k++) {
            Pile p = out.get(k).pile();
            int pi = piles.indexOf(p);
            int need = out.get(k).stack().getCount();
            while (need > 0 && next[pi] < p.sources.size()) {
                int[] src = p.sources.get(next[pi]);
                int take = Math.min(need, src[1] - used[pi]);
                if (src[0] != at[k]) trips.add(new int[] {src[0], at[k]});
                need -= take;
                used[pi] += take;
                if (used[pi] >= src[1]) {
                    next[pi]++;
                    used[pi] = 0;
                }
            }
        }
        int[] from = new int[trips.size()], to = new int[trips.size()];
        for (int k = 0; k < trips.size(); k++) {
            from[k] = trips.get(k)[0];
            to[k] = trips.get(k)[1];
        }
        boolean changed = false;
        for (int i = 0; i < now.length; i++) changed |= !ItemStack.matches(now[i], after[i]);
        return new Plan(after, from, to, changed);
    }

    /**
     * Where each stack goes, in order. Strict: every sort starts its own row. Otherwise a sort moves to a new row only when
     * it doesn't fit what's left of this one, is at most a row long, and everything after it still fits.
     */
    private static int @Nullable [] place(List<Out> out, boolean[] locked, int cols, boolean strict) {
        int[] at = new int[out.size()];
        int p = 0, placed = 0;
        Sort last = null;
        for (int k = 0; k < out.size(); k++) {
            Sort s = Sorts.of(out.get(k).stack());
            if (s != last) {
                last = s;
                if (p % cols != 0) {
                    int nextRow = p + cols - p % cols;
                    if (strict) {
                        p = nextRow;
                    } else {
                        int group = 0;
                        for (int j = k; j < out.size() && Sorts.of(out.get(j).stack()) == s; j++) group++;
                        int room = 0;
                        for (int i = p; i < nextRow && i < locked.length; i++) if (!locked[i]) room++;
                        int left = out.size() - placed, free = 0;
                        for (int i = nextRow; i < locked.length; i++) if (!locked[i]) free++;
                        if (group > room && group <= cols && left <= free) p = nextRow;
                    }
                }
            }
            while (p < locked.length && locked[p]) p++;
            if (p >= locked.length) return null;
            at[k] = p++;
            placed++;
        }
        return at;
    }

    /** Every item and component set adds up to exactly what it did. */
    static boolean sameTotals(ItemStack[] a, ItemStack[] b) {
        List<ItemStack> kinds = new ArrayList<>();
        List<int[]> sum = new ArrayList<>();
        count(a, kinds, sum, 1);
        count(b, kinds, sum, -1);
        for (int[] n : sum) if (n[0] != 0) return false;
        return true;
    }

    private static void count(ItemStack[] stacks, List<ItemStack> kinds, List<int[]> sum, int sign) {
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            int found = -1;
            for (int i = 0; i < kinds.size(); i++) {
                if (ItemStack.isSameItemSameComponents(kinds.get(i), s)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                kinds.add(s);
                sum.add(new int[1]);
                found = kinds.size() - 1;
            }
            sum.get(found)[0] += sign * s.getCount();
        }
    }
}
