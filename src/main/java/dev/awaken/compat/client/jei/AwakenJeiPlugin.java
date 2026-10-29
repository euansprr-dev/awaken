package dev.awaken.compat.client.jei;

import dev.awaken.Awaken;
import dev.awaken.above.AboveItems;
import dev.awaken.above.MachineType;
import dev.awaken.compat.Discovered;
import dev.awaken.core.api.CoreApi;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.request.RequestContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Awaken in JEI (SPEC §14): Awaken's own ways of making things as categories in the System's glass (crafting-table
 * recipes with their level road step, the Fabricator, the Frame Forge, tempering, Hearth upgrades, essence,
 * core fusing) and Codex pages for key items, all under the spoiler guard ({@link SpoilerGuard}). JEI finds this class
 * by its annotation, only when JEI is installed.
 */
@JeiPlugin
public final class AwakenJeiPlugin implements IModPlugin {
    private static final Identifier UID = Awaken.id("jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        IGuiHelper gui = r.getJeiHelpers().getGuiHelper();
        r.addRecipeCategories(
                new Categories.Crafting(gui.createDrawableItemLike(Blocks.CRAFTING_TABLE)),
                new Categories.Build(Categories.FABRICATOR, "fabricator", gui.createDrawableItemLike(AboveItems.FABRICATOR.get())),
                new Categories.Build(Categories.FRAME_FORGE, "frame_forge", gui.createDrawableItemLike(AboveItems.machine(MachineType.FRAME_FORGE))),
                new Categories.Temper(gui.createDrawableItemLike(HearthContent.FORGE_ITEM.get())),
                new Categories.BossDrops(gui.createDrawableItemLike(CoreApi.LORD_HEART.get())),
                new Categories.Blueprint(gui.createDrawableItemLike(dev.awaken.arms.ArmsItems.blueprintItem("daggers"))),
                new Categories.Upgrade(gui.createDrawableItemLike(HearthContent.HEARTH_ANCHOR_ITEM.get())),
                new Categories.Essence(gui.createDrawableItemLike(AboveItems.CELL.get())),
                new Categories.Fuse(gui.createDrawableItemLike(RequestContent.CORE_SHARD.get())),
                new Categories.Codex(gui.createDrawableItemLike(CoreApi.LORD_HEART.get())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        // the guard holds the very objects JEI shows, so every list is made once here
        SpoilerGuard.clearOwn();
        var machines = Recipes.machines();
        for (var b : machines) SpoilerGuard.own(Categories.FABRICATOR, b, b.output().getItem());
        var frames = Recipes.frames();
        for (var b : frames) SpoilerGuard.own(Categories.FRAME_FORGE, b, b.output().getItem());
        Item forge = HearthContent.FORGE_ITEM.get();
        var tempers = Recipes.tempers();
        for (var t : tempers) SpoilerGuard.own(Categories.TEMPER, t, forge);
        var bosses = Recipes.bosses();
        for (var bd : bosses) SpoilerGuard.own(Categories.BOSS, bd, CoreApi.LORD_HEART.get());
        var patterns = Recipes.patterns();
        for (var p : patterns) SpoilerGuard.own(Categories.BLUEPRINT, p, p.blueprint().getItem());
        var upgrades = Recipes.upgrades();
        for (var u : upgrades) SpoilerGuard.own(Categories.UPGRADE, u, u.station().getItem());
        var essence = Recipes.essence();
        for (var e : essence) SpoilerGuard.own(Categories.ESSENCE, e, e.output().getItem());
        var fuses = Recipes.fuses();
        for (var f : fuses) SpoilerGuard.own(Categories.FUSE, f, f.inputs().getFirst().getItem());
        var pages = Recipes.pages();
        for (var p : pages) SpoilerGuard.own(Categories.CODEX, p, p.item().getItem());

        r.addRecipes(Categories.FABRICATOR, machines);
        r.addRecipes(Categories.FRAME_FORGE, frames);
        r.addRecipes(Categories.TEMPER, tempers);
        r.addRecipes(Categories.BOSS, bosses);
        r.addRecipes(Categories.BLUEPRINT, patterns);
        r.addRecipes(Categories.UPGRADE, upgrades);
        r.addRecipes(Categories.ESSENCE, essence);
        r.addRecipes(Categories.FUSE, fuses);
        r.addRecipes(Categories.CODEX, pages);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addCraftingStation(Categories.CRAFTING, Blocks.CRAFTING_TABLE);
        r.addCraftingStation(Categories.FABRICATOR, AboveItems.FABRICATOR.get());
        r.addCraftingStation(Categories.FRAME_FORGE, AboveItems.machine(MachineType.FRAME_FORGE));
        r.addCraftingStation(Categories.TEMPER, HearthContent.FORGE_ITEM.get());
        r.addCraftingStation(Categories.BLUEPRINT, HearthContent.FORGE_ITEM.get());
        r.addCraftingStation(Categories.UPGRADE, HearthContent.HEARTH_ANCHOR_ITEM.get(), HearthContent.FORGE_ITEM.get());
        r.addCraftingStation(Categories.ESSENCE, AboveItems.CELL.get(), AboveItems.machine(MachineType.REACTOR));
        r.addCraftingStation(Categories.FUSE, RequestContent.CORE_SHARD.get());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration r) {
        // Awaken's crafting tab fills a crafting table's grid like JEI's own
        r.addRecipeTransferHandler(CraftingMenu.class, MenuType.CRAFTING, Categories.CRAFTING, 1, 9, 10, 36);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // Awaken's crafting-table recipes move from JEI's grey Crafting tab into Awaken's own
        var rm = runtime.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> ours = rm.createRecipeLookup(RecipeTypes.CRAFTING).includeHidden().get()
                .filter(h -> h.id().identifier().getNamespace().equals(Awaken.MODID)).toList();
        if (!ours.isEmpty()) {
            rm.addRecipes(Categories.CRAFTING, ours);
            rm.hideRecipes(RecipeTypes.CRAFTING, ours);
            for (var h : ours) {
                SpoilerGuard.moved(h);
                Item result = Discovered.resultOf(h.value().display().getFirst().result());
                if (result != null) SpoilerGuard.own(Categories.CRAFTING, h, result);
            }
        }
        SpoilerGuard.start(runtime);
        Awaken.LOG.info("[compat] JEI runtime ready: {} Awaken crafting recipes in Awaken's tab", ours.size());
    }

    @Override
    public void onRuntimeUnavailable() {
        SpoilerGuard.stop();
    }
}
