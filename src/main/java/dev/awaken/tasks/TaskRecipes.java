package dev.awaken.tasks;

import dev.awaken.tasks.net.TaskPayloads;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The recipes the band's stage draws (SPEC §57): for each thing a shown step still needs that you place as a block and
 * craft at a table (a Fitting Stand, a crafting table, a War Anvil), the crafting recipe that carries its own id. Read from
 * the server's recipes, so the picture can never disagree with the game. Materials (an ingot, a hide) get none: where they
 * come from is said on the item.
 */
public final class TaskRecipes {
    private TaskRecipes() {}

    private static final int MOST = 32;

    /** The recipes for the unmet needs of the rows on the page (not the ones waiting in Later). */
    static List<TaskPayloads.RecipeRow> of(ServerPlayer p, List<TaskPayloads.StepRow> rows) {
        Map<String, TaskPayloads.RecipeRow> out = new LinkedHashMap<>();
        for (TaskPayloads.StepRow row : rows) {
            if (!row.shown()) continue;
            add(p, out, row.needs());
            for (TaskPayloads.ChoiceRow c : row.choices()) add(p, out, c.needs());
        }
        return List.copyOf(out.values());
    }

    private static void add(ServerPlayer p, Map<String, TaskPayloads.RecipeRow> out, List<TaskPayloads.NeedRow> needs) {
        for (TaskPayloads.NeedRow n : needs) {
            if (n.met() || n.item().isEmpty() || n.item().startsWith("#") || out.containsKey(n.item()) || out.size() >= MOST) continue;
            TaskPayloads.RecipeRow r = recipe(p, n.item());
            if (r != null) out.put(n.item(), r);
        }
    }

    /** The crafting recipe named after {@code item}, for something placed as a block; null for anything else. */
    public static TaskPayloads.@Nullable RecipeRow recipe(ServerPlayer p, String item) {
        Identifier id = Identifier.tryParse(item);
        if (id == null) return null;
        Optional<Item> made = BuiltInRegistries.ITEM.getOptional(id);
        if (made.isEmpty() || !(made.get() instanceof BlockItem)) return null;
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        Optional<RecipeHolder<?>> holder = p.level().getServer().getRecipeManager().byKey(key);
        if (holder.isEmpty()) return null;
        Recipe<?> r = holder.get().value();
        List<List<String>> cells = new ArrayList<>();
        int w, h;
        ItemStack result;
        if (r instanceof ShapedRecipe shaped) {
            w = shaped.getWidth();
            h = shaped.getHeight();
            for (Optional<Ingredient> ing : shaped.getIngredients()) cells.add(ing.map(TaskRecipes::items).orElse(List.of()));
            result = shaped.assemble(CraftingInput.EMPTY);
        } else if (r instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ings = shapeless.placementInfo().ingredients();
            w = Math.min(3, ings.size());
            h = (ings.size() + 2) / 3;
            for (Ingredient ing : ings) cells.add(items(ing));
            result = shapeless.assemble(CraftingInput.EMPTY);
        } else {
            return null;
        }
        if (cells.size() > 9 || result.getItem() != made.get()) return null;
        return new TaskPayloads.RecipeRow(item, w, h, cells, Math.max(1, result.getCount()));
    }

    private static List<String> items(Ingredient ing) {
        List<String> out = new ArrayList<>();
        ing.items().limit(8).forEach((Holder<Item> holder) -> out.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
        return out;
    }
}
