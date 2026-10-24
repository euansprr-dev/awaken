package dev.awaken.compat.client.jei;

import dev.awaken.Awaken;
import dev.awaken.compat.Discovered;
import dev.awaken.compat.Reveal;
import dev.awaken.compat.SpoilerRule;
import dev.awaken.compat.client.Spoilers;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The spoiler guard in JEI (SPEC §14). Every Awaken item the player hasn't discovered ({@link Discovered}) is hidden from
 * the item list and from recipe slots, and every recipe whose Awaken result is undiscovered is hidden; both come back
 * the moment the record grows. Awaken's own recipes are guarded by their key item (a machine by itself, tempering by the
 * Forge, a Codex page by its item); recipes in other categories (JEI's anvil, other mods') by their Awaken outputs, shown
 * once any of those is discovered. A category whose stations are all hidden hides itself (JEI's own rule). The decisions
 * are {@link SpoilerRule}'s and {@link Reveal} says what changed, so this only translates them into JEI calls, on the
 * client tick, when the record, the game mode or the setting changes.
 */
final class SpoilerGuard {
    private SpoilerGuard() {}

    private static final List<UidContext> CONTEXTS = List.of(UidContext.Ingredient, UidContext.Recipe);

    /** A guarded recipe: shown once any of its key items may be shown. */
    private record Guarded(IRecipeType<?> type, Object recipe, List<Item> keys) {}

    // registered by the plugin while JEI registers recipes (Awaken's categories)
    private static final List<Guarded> OWN = new ArrayList<>();
    // recipes JEI moved out of sight for good (Awaken's crafting-table recipes, now in Awaken's Crafting tab)
    private static final Set<Object> MOVED = Collections.newSetFromMap(new IdentityHashMap<>());

    private static IJeiRuntime runtime;
    private static boolean built;
    private static final Map<Item, List<ItemStack>> STACKS = new IdentityHashMap<>();
    private static final List<Guarded> GUARDED = new ArrayList<>();
    private static final Reveal<Item> ITEMS = new Reveal<>();
    private static final Reveal<Guarded> RECIPES = new Reveal<>();
    private static int lastSize = -1;
    private static boolean lastActive;
    private static Player lastPlayer;
    private static int resendWait;
    private static final int RESEND_WAIT = 40;

    static void clearOwn() {
        OWN.clear();
        MOVED.clear();
    }

    static <T> void own(IRecipeType<T> type, T recipe, Item key) {
        OWN.add(new Guarded(type, recipe, List.of(key)));
    }

    static void moved(Object recipe) {
        MOVED.add(recipe);
    }

    static void start(IJeiRuntime rt) {
        stop();
        runtime = rt;
        lastSize = -1;
    }

    static void stop() {
        runtime = null;
        built = false;
        lastPlayer = null;
        STACKS.clear();
        GUARDED.clear();
        ITEMS.clear();
        RECIPES.clear();
    }

    static IJeiRuntime runtime() {
        return runtime;
    }

    /** Client tick: build the index once per JEI start, then apply only when something changed. */
    static void tick() {
        if (runtime == null) return;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        if (!built) {
            build();
            built = true;
        }
        boolean active = Spoilers.active();
        int size = Discovered.of(p).size();
        if (p != lastPlayer) {
            // a new client player (respawn, another dimension) starts empty until the server re-sends its record, which
            // only grows: keep the current view a moment rather than hiding everything for a tick
            lastPlayer = p;
            resendWait = size < lastSize ? RESEND_WAIT : 0;
        }
        if (resendWait > 0 && size < lastSize) {
            resendWait--;
            return;
        }
        resendWait = 0;
        if (size == lastSize && active == lastActive) return;
        apply(p, active);
        lastSize = size;
        lastActive = active;
    }

    private static void build() {
        long t0 = System.nanoTime();
        for (ItemStack s : runtime.getIngredientManager().getAllItemStacks()) {
            if (Discovered.isAwaken(s.getItem())) STACKS.computeIfAbsent(s.getItem(), k -> new ArrayList<>()).add(s);
        }
        GUARDED.addAll(OWN);
        Set<IRecipeType<?>> own = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Guarded g : OWN) own.add(g.type());
        // everything else that makes an Awaken item: found through JEI's own output index, one lookup per item
        IRecipeManager rm = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        Map<Object, Guarded> others = new IdentityHashMap<>();
        List<Guarded> found = new ArrayList<>();
        for (var e : STACKS.entrySet()) {
            IFocus<ItemStack> focus = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, e.getValue().getFirst());
            List<IRecipeCategory<?>> cats = rm.createRecipeCategoryLookup().limitFocus(List.of(focus)).includeHidden().get().toList();
            for (IRecipeCategory<?> cat : cats) {
                IRecipeType<?> type = cat.getRecipeType();
                if (own.contains(type)) continue;
                List<?> recipes = rm.createRecipeLookup(type).limitFocus(List.of(focus)).includeHidden().get().toList();
                for (Object r : recipes) {
                    if (type == RecipeTypes.CRAFTING && MOVED.contains(r)) continue;
                    Guarded g = others.get(r);
                    if (g == null) {
                        g = new Guarded(type, r, new ArrayList<>(2));
                        others.put(r, g);
                        found.add(g);
                    }
                    if (!g.keys().contains(e.getKey())) g.keys().add(e.getKey());
                }
            }
        }
        GUARDED.addAll(found);
        Awaken.LOG.info("[compat] JEI spoiler guard: {} Awaken items, {} Awaken recipes, {} more in other categories ({} ms)",
                STACKS.size(), OWN.size(), found.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    private static void apply(Player p, boolean guard) {
        List<ItemStack> hide = new ArrayList<>(), show = new ArrayList<>();
        ITEMS.update(STACKS.keySet(), item -> SpoilerRule.shows(p, item, guard), item -> hide.addAll(STACKS.get(item)),
                item -> show.addAll(STACKS.get(item)));
        var visibility = runtime.getJeiHelpers().getIngredientVisibility();
        if (!hide.isEmpty()) visibility.hideIngredients(VanillaTypes.ITEM_STACK, hide, CONTEXTS);
        if (!show.isEmpty()) visibility.unhideIngredients(VanillaTypes.ITEM_STACK, show, CONTEXTS);

        Map<IRecipeType<?>, List<Object>> hideR = new LinkedHashMap<>(), showR = new LinkedHashMap<>();
        RECIPES.update(GUARDED, g -> SpoilerRule.showsAny(p, g.keys(), guard),
                g -> hideR.computeIfAbsent(g.type(), k -> new ArrayList<>()).add(g.recipe()),
                g -> showR.computeIfAbsent(g.type(), k -> new ArrayList<>()).add(g.recipe()));
        IRecipeManager rm = runtime.getRecipeManager();
        hideR.forEach((type, list) -> hide(rm, type, list));
        showR.forEach((type, list) -> unhide(rm, type, list));
        Awaken.LOG.info("[compat] JEI spoiler guard applied: {} items hidden (+{} -{}), {} recipes hidden (+{} -{})",
                ITEMS.hiddenCount(), hide.size(), show.size(), RECIPES.hiddenCount(),
                hideR.values().stream().mapToInt(List::size).sum(), showR.values().stream().mapToInt(List::size).sum());
    }

    /** How many Awaken items the item list shows right now (for tours and tests). */
    static int visibleAwakenItems() {
        if (runtime == null) return -1;
        int n = 0;
        for (ItemStack s : runtime.getIngredientFilter().getFilteredItemStacks()) if (Discovered.isAwaken(s.getItem())) n++;
        return n;
    }

    @SuppressWarnings("unchecked")
    private static <T> void hide(IRecipeManager rm, IRecipeType<T> type, List<Object> recipes) {
        rm.hideRecipes(type, (List<T>) (List<?>) recipes);
    }

    @SuppressWarnings("unchecked")
    private static <T> void unhide(IRecipeManager rm, IRecipeType<T> type, List<Object> recipes) {
        rm.unhideRecipes(type, (List<T>) (List<?>) recipes);
    }
}
