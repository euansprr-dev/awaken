package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Checklist;
import dev.awaken.signal.Signal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Track (docs/arms-progression/PLAN.md §3.5): one weapon at a time on the task card (top right) with what it still needs,
 * and the first thing missing on the Now line (priority 52, under places and rifts). Ends by itself when the weapon is made.
 */
final class ArmsTrack {
    private ArmsTrack() {}

    static final int PRIORITY = 52;

    static void init() {
        Checklist.register(ArmsTrack::task);
        NowHooks.register(ArmsTrack::now);
    }

    private record Tracked(String style, int tier, String recipe, Item weapon) {}

    private static @Nullable Tracked tracked(ServerPlayer p) {
        String t = ArmsProgress.of(p).tracked;
        int colon = t.indexOf(':');
        if (colon <= 0) return null;
        String style = t.substring(0, colon);
        int tier = ArmsArmoury.tierIndex(t.substring(colon + 1));
        Item weapon = ArmsItems.item(ArmsArmoury.recipeId(style, Math.max(0, tier)));
        return tier < 0 || weapon == null ? null : new Tracked(style, tier, ArmsArmoury.recipeId(style, tier), weapon);
    }

    /** What the card says for a need: "E-rank Part (bone, flint, quartz)", or an item's name. */
    static String label(ArmsPayloads.NeedRow n) {
        Identifier id = Identifier.parse(n.id());
        if (n.kind().equals("tag")) {
            String name = ArmsLang.get("tag.item." + id.getNamespace() + "." + id.getPath());
            List<String> some = new ArrayList<>();
            BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).ifPresent(set -> {
                for (var h : set) {
                    if (some.size() >= 3) break;
                    some.add(ArmsLang.itemName(BuiltInRegistries.ITEM.getKey(h.value())).toLowerCase(java.util.Locale.ROOT));
                }
            });
            return (name == null ? ArmsLang.pretty(id.getPath()) : name) + (some.isEmpty() ? "" : " (" + String.join(", ", some) + ")");
        }
        return ArmsLang.itemName(id);
    }

    private static String weaponName(Tracked t) {
        return ArmsLang.itemName(BuiltInRegistries.ITEM.getKey(t.weapon()));
    }

    static Checklist.@Nullable Task task(ServerPlayer p) {
        Tracked t = tracked(p);
        if (t == null) return null;
        if (ArmsForge.count(p, t.weapon()) > 0) {
            // made: it lets go, with a word
            ArmsProgress g = ArmsProgress.of(p);
            g.tracked = "";
            p.setData(ArmsContent.PROGRESS, g);
            Signal.toast(p, Component.literal("MADE · " + weaponName(t)));
            return null;
        }
        List<Checklist.Row> rows = new ArrayList<>();
        for (ArmsPayloads.NeedRow n : ArmsArmoury.needs(p, t.style(), t.tier())) {
            rows.add(new Checklist.Row(label(n), "", n.have() >= n.need(), Math.min(n.have(), n.need()), n.need(), null));
        }
        return new Checklist.Task("arms:track", "Making: " + weaponName(t), rows);
    }

    private static List<NowHooks.Suggestion> now(ServerPlayer p) {
        Tracked t = tracked(p);
        if (t == null) return List.of();
        for (ArmsPayloads.NeedRow n : ArmsArmoury.needs(p, t.style(), t.tier())) {
            if (n.have() >= n.need()) continue;
            String text = weaponName(t) + ": still need " + (n.need() - n.have()) + " more " + label(n);
            return List.of(NowHooks.Suggestion.of("arms:track", PRIORITY, text));
        }
        return List.of(NowHooks.Suggestion.of("arms:track", PRIORITY, "You have everything for " + weaponName(t) + ": make it at a Forge"));
    }
}
