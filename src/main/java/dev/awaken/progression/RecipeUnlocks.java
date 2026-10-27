package dev.awaken.progression;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod recipes unlock in the recipe book the first time you hold one of their ingredients (vanilla's own rule for its
 * recipes, plan v2 §4.5). Every 40 ticks per player (staggered) the inventory is checked against a cached
 * item → AWAKEN recipes map, rebuilt whenever the server's recipe manager changes (a datapack reload).
 */
final class RecipeUnlocks {
    private RecipeUnlocks() {}

    private static RecipeManager builtFor;
    private static Map<Item, List<RecipeHolder<?>>> byItem = Map.of();
    private static final List<RecipeHolder<?>> FOUND = new ArrayList<>();

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || (sp.tickCount + sp.getId()) % 40 != 0) return;
        check(sp);
    }

    /** Unlocks every AWAKEN recipe with an ingredient in the player's inventory. Returns how many were new. */
    static int check(ServerPlayer sp) {
        RecipeManager rm = sp.level().getServer().getRecipeManager();
        if (rm != builtFor) rebuild(rm);
        if (byItem.isEmpty()) return 0;
        Inventory inv = sp.getInventory();
        var book = sp.getRecipeBook();
        FOUND.clear();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            List<RecipeHolder<?>> recipes = byItem.get(s.getItem());
            if (recipes == null) continue;
            for (RecipeHolder<?> r : recipes) if (!book.contains(r.id()) && !FOUND.contains(r)) FOUND.add(r);
        }
        if (FOUND.isEmpty()) return 0;
        int n = sp.awardRecipes(List.copyOf(FOUND));
        FOUND.clear();
        return n;
    }

    private static void rebuild(RecipeManager rm) {
        Map<Item, List<RecipeHolder<?>>> map = new HashMap<>();
        for (RecipeHolder<?> r : rm.getRecipes()) {
            if (!r.id().identifier().getNamespace().equals(Awaken.MODID) || r.value().isSpecial()) continue;
            // a recipe that takes a Blueprint is learned from the Blueprint, not from a bone it also uses (docs/arms-progression/PLAN.md G5)
            List<Item> blueprints = r.value().placementInfo().ingredients().stream().flatMap(Ingredient::items).map(Holder::value)
                    .filter(i -> dev.awaken.arms.ArmsItems.blueprintStyle(i) != null).distinct().toList();
            if (!blueprints.isEmpty()) {
                for (Item bp : blueprints) map.computeIfAbsent(bp, k -> new ArrayList<>()).add(r);
                continue;
            }
            for (Ingredient ing : r.value().placementInfo().ingredients()) {
                ing.items().map(Holder::value).distinct().forEach(item -> {
                    List<RecipeHolder<?>> list = map.computeIfAbsent(item, k -> new ArrayList<>());
                    if (!list.contains(r)) list.add(r);
                });
            }
        }
        byItem = map;
        builtFor = rm;
        Awaken.LOG.info("AWAKEN recipe unlocks: {} ingredient items lead to mod recipes", map.size());
    }

    static void forget() {
        builtFor = null;
        byItem = Map.of();
    }
}
