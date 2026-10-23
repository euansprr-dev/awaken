package dev.awaken.progression.client;

import dev.awaken.progression.Perk;
import dev.awaken.progression.Reward;
import dev.awaken.signal.client.Ui;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Rewards and perks in plain words for the Status screen ("+2 stat points, Hunter's lantern, +5% move speed"). */
final class Rewards {
    private Rewards() {}

    static String describe(Reward r) {
        List<String> parts = new ArrayList<>();
        if (r.levels() > 0) {
            parts.add(r.levels() >= 1 ? trim(r.levels()) + " level" + (r.levels() == 1 ? "" : "s")
                    : Math.round(r.levels() * 100) + "% of a level");
        }
        if (r.statPoints() > 0) parts.add("+" + r.statPoints() + " stat point" + (r.statPoints() > 1 ? "s" : ""));
        r.stat().ifPresent(s -> parts.add("+" + Math.max(1, r.statAmount()) + " " + s.name()));
        r.power().ifPresent(p -> parts.add("the power " + Ui.pretty(p)));
        r.item().ifPresent(i -> parts.add((r.itemCount() > 1 ? r.itemCount() + " × " : "") + itemName(i)));
        for (Perk p : r.perks()) parts.add(perk(p));
        return parts.isEmpty() ? "recognition" : String.join(", ", parts);
    }

    static String perk(Perk p) {
        String name = Ui.pretty(p.id()).toLowerCase(java.util.Locale.ROOT);
        double a = p.amount();
        if (a == 1.0) return name;
        if (Math.abs(a) < 1) return (a > 0 ? "+" : "") + Math.round(a * 100) + "% " + name;
        return (a > 0 ? "+" : "") + trim(a) + " " + name;
    }

    static String perks(List<Perk> perks) {
        List<String> out = new ArrayList<>();
        for (Perk p : perks) out.add(perk(p));
        return String.join(", ", out);
    }

    private static String itemName(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return Ui.pretty(id);
        return BuiltInRegistries.ITEM.getOptional(key).map(i -> new ItemStack(i).getHoverName().getString()).orElse(Ui.pretty(id));
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : String.format("%.1f", d);
    }
}
