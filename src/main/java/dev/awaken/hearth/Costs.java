package dev.awaken.hearth;

import dev.awaken.progression.Rank;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hearth prices. Mostly overworld materials plus a little essence, and every rank you hold above an upgrade's tier makes
 * it 15% cheaper (compounding: ×0.85 per rank, never below one of each item). Home is never a tax on adventuring.
 */
public final class Costs {
    private Costs() {}

    public static final double STEP = 0.85;

    public record Part(Item item, int count) {
        public Component name() {
            return Component.literal(count + " × ").append(item.getName(item.getDefaultInstance()));
        }
    }

    /** Price multiplier for a player of {@code playerRank} buying something of {@code tierRank}. */
    public static double multiplier(int playerRank, int tierRank) {
        return Math.pow(STEP, Math.max(0, playerRank - tierRank));
    }

    /** One item count after the rank discount (rounded up, at least 1). */
    public static int reduce(int base, int playerRank, int tierRank) {
        if (base <= 0) return 0;
        return Math.max(1, (int) Math.ceil(base * multiplier(playerRank, tierRank) - 1e-9));
    }

    public static List<Part> price(List<Part> base, Rank player, Rank tier) {
        List<Part> out = new ArrayList<>(base.size());
        for (Part p : base) {
            int n = reduce(p.count(), player.ordinal(), tier.ordinal());
            if (n > 0) out.add(new Part(p.item(), n));
        }
        return out;
    }

    /** Whole-percent discount for display ("−28%"). */
    public static int discountPercent(Rank player, Rank tier) {
        return (int) Math.round((1 - multiplier(player.ordinal(), tier.ordinal())) * 100);
    }

    public static int countOf(Player p, Item item) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    public static boolean canAfford(Player p, List<Part> parts) {
        if (p.hasInfiniteMaterials()) return true;
        for (Part part : parts) if (countOf(p, part.item()) < part.count()) return false;
        return true;
    }

    /** Removes the parts from the inventory. Call {@link #canAfford} first. */
    public static void pay(Player p, List<Part> parts) {
        if (p.hasInfiniteMaterials()) return;
        var inv = p.getInventory();
        for (Part part : parts) {
            int left = part.count();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(part.item())) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        inv.setChanged();
    }

    /** "have 12" or "ready" for a cost row. */
    public static Component have(Player p, Part part) {
        int n = countOf(p, part.item());
        return Component.literal(p.hasInfiniteMaterials() || n >= part.count() ? "ready" : "have " + n);
    }
}
