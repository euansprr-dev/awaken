package dev.awaken.compat.client.jei;

import dev.awaken.Awaken;
import dev.awaken.compat.Discovered;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The JEI side's game hooks: the guard's client tick and the autopilot's {@code compat jei ...} lines. Loaded only with JEI. */
public final class JeiHooks {
    private JeiHooks() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> SpoilerGuard.tick());
        Awaken.LOG.info("[compat] JEI hooks ready (spoiler guard)");
    }

    /**
     * Autopilot lines: {@code count} (Awaken items the list shows), {@code category <path>} (open an Awaken category),
     * {@code recipes <item id>} (what makes it), {@code search <text>} (the list's filter; {@code search} alone clears it),
     * {@code missing} (the registered Awaken items JEI has no entry for at all, and those it lists in several kinds).
     */
    public static String autopilot(String args) {
        var rt = SpoilerGuard.runtime();
        if (rt == null) return "JEI has not started";
        String[] a = args.trim().split("\\s+", 2);
        String rest = a.length > 1 ? a[1].trim() : "";
        switch (a[0]) {
            case "count" -> {
                return "visible Awaken items " + SpoilerGuard.visibleAwakenItems();
            }
            case "category" -> {
                IRecipeType<?> type = rt.getRecipeManager().getRecipeType(Awaken.id(rest)).orElse(null);
                if (type == null) return "no category awaken:" + rest;
                rt.getRecipesGui().showTypes(List.of(type));
                return "showing awaken:" + rest;
            }
            case "recipes" -> {
                var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(rest));
                if (item.isEmpty()) return "no item " + rest;
                var focus = rt.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(item.get()));
                rt.getRecipesGui().show(focus);
                return "recipes for " + rest;
            }
            case "missing" -> {
                // every registered Awaken item against JEI's whole ingredient list (hidden ones included): what no one can find
                Map<Item, Integer> listed = new IdentityHashMap<>();
                for (ItemStack s : rt.getIngredientManager().getAllItemStacks())
                    if (Discovered.isAwaken(s.getItem())) listed.merge(s.getItem(), 1, Integer::sum);
                List<String> missing = new ArrayList<>();
                int registered = 0;
                for (Item item : BuiltInRegistries.ITEM) {
                    if (!Discovered.isAwaken(item)) continue;
                    registered++;
                    if (!listed.containsKey(item)) missing.add(BuiltInRegistries.ITEM.getKey(item).getPath());
                }
                List<String> variants = new ArrayList<>();
                listed.forEach((item, n) -> { if (n > 1) variants.add(BuiltInRegistries.ITEM.getKey(item).getPath() + " x" + n); });
                Awaken.LOG.info("[compat] JEI lists {} of {} Awaken items; not in its list ({}): {}", listed.size(), registered, missing.size(),
                        String.join(", ", missing));
                Awaken.LOG.info("[compat] JEI lists these Awaken items in several kinds: {}", variants.isEmpty() ? "none" : String.join(", ", variants));
                return listed.size() + " of " + registered + " Awaken items in JEI, " + missing.size() + " missing";
            }
            case "search" -> {
                rt.getIngredientFilter().setFilterText(rest);
                return "filter '" + rest + "' shows " + rt.getIngredientFilter().getFilteredItemStacks().size() + " items, "
                        + SpoilerGuard.visibleAwakenItems() + " of them Awaken's";
            }
            default -> {
                return "unknown: " + args;
            }
        }
    }
}
