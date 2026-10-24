package dev.awaken.compat.client.jei;

import dev.awaken.Awaken;
import dev.awaken.gear.HunterGearItem;
import dev.awaken.gear.SetBonus;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.LevelRoad;
import dev.awaken.progression.Rank;
import dev.awaken.progression.content.Defs;
import dev.awaken.signal.client.Glass;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Awaken's JEI categories, each drawn in the System's glass (see {@link JeiLook}). */
final class Categories {
    private Categories() {}

    static final IRecipeHolderType<CraftingRecipe> CRAFTING = IRecipeHolderType.create(Awaken.id("crafting"));
    static final IRecipeType<Recipes.Build> FABRICATOR = IRecipeType.create(Awaken.MODID, "fabricator", Recipes.Build.class);
    static final IRecipeType<Recipes.Build> FRAME_FORGE = IRecipeType.create(Awaken.MODID, "frame_forge", Recipes.Build.class);
    static final IRecipeType<Recipes.Temper> TEMPER = IRecipeType.create(Awaken.MODID, "tempering", Recipes.Temper.class);
    static final IRecipeType<Recipes.Upgrade> UPGRADE = IRecipeType.create(Awaken.MODID, "hearth_upgrade", Recipes.Upgrade.class);
    static final IRecipeType<Recipes.Duty> DUTY = IRecipeType.create(Awaken.MODID, "echo_duty", Recipes.Duty.class);
    static final IRecipeType<Recipes.Essence> ESSENCE = IRecipeType.create(Awaken.MODID, "essence", Recipes.Essence.class);
    static final IRecipeType<Recipes.Fuse> FUSE = IRecipeType.create(Awaken.MODID, "core_fusing", Recipes.Fuse.class);
    static final IRecipeType<Recipes.Page> CODEX = IRecipeType.create(Awaken.MODID, "codex", Recipes.Page.class);

    static final int W = 176;

    private static Component title(String key) {
        return Component.translatable("awaken.jei.category." + key);
    }

    // ---- crafting-table recipes, Awaken's ---------------------------------------------------------------------------

    /**
     * Every Awaken crafting-table recipe, moved here from JEI's grey Crafting tab: the grid, the result, and where the
     * recipe comes from (the level road step that teaches it; for Hunter gear its rank, set and set bonus).
     */
    static final class Crafting extends SystemCategory<RecipeHolder<CraftingRecipe>> {
        private final Map<Identifier, String> source = new HashMap<>();

        Crafting(IDrawable icon) {
            super(CRAFTING, title("crafting"), icon, W, 78);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, RecipeHolder<CraftingRecipe> r, IFocusGroup focuses) {
            // all nine inputs, in grid order (empty ones too), so the crafting table's transfer lines up
            IRecipeSlotBuilder[] grid = new IRecipeSlotBuilder[9];
            for (int i = 0; i < 9; i++) grid[i] = b.addInputSlot(8 + (i % 3) * 18, 8 + (i / 3) * 18);
            RecipeDisplay d = r.value().display().getFirst();
            SlotDisplay result = SlotDisplay.Empty.INSTANCE;
            if (d instanceof ShapedCraftingRecipeDisplay s) {
                for (int y = 0; y < s.height(); y++) for (int x = 0; x < s.width(); x++) {
                    SlotDisplay in = s.ingredients().get(y * s.width() + x);
                    if (!(in instanceof SlotDisplay.Empty)) grid[y * 3 + x].add(in);
                }
                result = s.result();
            } else if (d instanceof ShapelessCraftingRecipeDisplay s) {
                for (int i = 0; i < Math.min(9, s.ingredients().size()); i++) grid[i].add(s.ingredients().get(i));
                result = s.result();
                b.setShapeless(150, 6);
            }
            b.addOutputSlot(98, 24).add(result);
        }

        @Override
        int accent(RecipeHolder<CraftingRecipe> r) {
            HunterGearItem gear = gear(r);
            return gear != null ? gear.tier.rank.colour : Glass.EDGE;
        }

        @Override
        void drawRecipe(RecipeHolder<CraftingRecipe> r, GuiGraphicsExtractor g, double mx, double my) {
            for (int i = 0; i < 9; i++) JeiLook.slot(g, 8 + (i % 3) * 18, 8 + (i / 3) * 18);
            JeiLook.arrow(g, 66, 32, Glass.EDGE);
            JeiLook.resultSlot(g, 94, 20, accent(r));
            HunterGearItem gear = gear(r);
            if (gear != null) {
                JeiLook.chip(g, gear.tier.rank.label + "-RANK", 124, 9, gear.tier.rank.colour);
                JeiLook.fit(g, "Hunter", 124, 25, W - 128, Glass.LABEL);
                JeiLook.fit(g, "gear", 124, 35, W - 128, Glass.LABEL);
                if (gear.piece.isArmour() && !SetBonus.lines(gear.tier).isEmpty()) JeiLook.fit(g, "Set bonus", 124, 47, W - 128, Glass.EDGE);
            }
            JeiLook.rule(g, 6, W - 6, 63);
            JeiLook.fit(g, source(r), 8, 66, W - 16, Glass.LABEL);
        }

        @Override
        public void getTooltip(ITooltipBuilder t, RecipeHolder<CraftingRecipe> r, IRecipeSlotsView slots, double mx, double my) {
            HunterGearItem gear = gear(r);
            if (gear == null || !gear.piece.isArmour() || !JeiLook.inside(mx, my, 124, 6, 50, 52)) return;
            t.add(Component.literal(gear.tier.setName + " set").withColor(gear.tier.rank.colour));
            for (SetBonus.Line line : SetBonus.lines(gear.tier)) t.add(Component.literal(line.pieces() + " worn: " + line.text()).withColor(Glass.TEXT));
        }

        private static HunterGearItem gear(RecipeHolder<CraftingRecipe> r) {
            RecipeDisplay d = r.value().display().getFirst();
            var item = dev.awaken.compat.Discovered.resultOf(d.result());
            return item instanceof HunterGearItem h ? h : null;
        }

        /** "Level 13 · Beastbound gear · D", or how else the book learns it. */
        private String source(RecipeHolder<CraftingRecipe> r) {
            Identifier id = r.id().identifier();
            return source.computeIfAbsent(id, k -> {
                for (LevelRoad.Step s : LevelRoad.steps()) {
                    for (Defs.Grant gr : s.def().grants()) {
                        if (!gr.type().equals("recipe")) continue;
                        if (gr.ids().contains(k.toString()) || gr.id().map(k.toString()::equals).orElse(false)) {
                            return "Lv " + s.def().level() + " · " + s.def().title();
                        }
                    }
                }
                return "Learned from its ingredients";
            });
        }
    }

    // ---- the Fabricator and the Frame Forge -------------------------------------------------------------------------

    static final class Build extends SystemCategory<Recipes.Build> {
        Build(IRecipeType<Recipes.Build> type, String key, IDrawable icon) {
            super(type, title(key), icon, W, 100);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Build r, IFocusGroup focuses) {
            for (int i = 0; i < r.parts().size(); i++) b.addInputSlot(9 + 19 * i, 23).add(r.parts().get(i));
            b.addOutputSlot(141, 23).add(r.output());
        }

        @Override
        void drawRecipe(Recipes.Build r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.title(g, r.name(), 9, 8, Glass.TITLE);
            int cw = JeiLook.font().width(r.chip()) + 8;
            JeiLook.chip(g, r.chip(), W - 8 - cw, 6, Glass.EDGE);
            for (int i = 0; i < r.parts().size(); i++) JeiLook.slot(g, 9 + 19 * i, 23);
            JeiLook.arrow(g, 110, 31, Glass.EDGE);
            JeiLook.resultSlot(g, 137, 19, Glass.EDGE);
            if (r.essence() > 0) JeiLook.row(g, "Power", r.essence() + " essence", 9, 46, Glass.VIOLET);
            else JeiLook.row(g, "Power", "none needed", 9, 46, Glass.TEXT);
            JeiLook.wrap(g, r.text(), 9, 58, W - 18, Glass.LABEL, 4);
        }
    }

    // ---- the Hearth -------------------------------------------------------------------------------------------------

    static final class Temper extends SystemCategory<Recipes.Temper> {
        Temper(IDrawable icon) {
            super(TEMPER, title("tempering"), icon, W, 94);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Temper r, IFocusGroup focuses) {
            var in = b.addInputSlot(9, 23).addItemStacks(r.examples());
            for (int i = 0; i < r.price().size(); i++) b.addInputSlot(36 + 19 * i, 23).add(r.price().get(i));
            var out = b.addOutputSlot(141, 23).addItemStacks(r.tempered());
            b.createFocusLink(in, out);
        }

        @Override
        int accent(Recipes.Temper r) {
            return Glass.LANTERN;
        }

        @Override
        void drawRecipe(Recipes.Temper r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.title(g, "Temper " + Upgrades.roman(r.level()), 9, 8, Glass.TITLE);
            int cw = JeiLook.font().width("FORGE") + 8;
            JeiLook.chip(g, "FORGE", W - 8 - cw, 6, Glass.LANTERN);
            JeiLook.slot(g, 9, 23);
            JeiLook.text(g, "+", 28, 27, Glass.LABEL);
            for (int i = 0; i < r.price().size(); i++) JeiLook.slot(g, 36 + 19 * i, 23);
            JeiLook.arrow(g, 114, 31, Glass.LANTERN);
            JeiLook.resultSlot(g, 137, 19, Glass.LANTERN);
            JeiLook.wrap(g, "Weapons +1 damage a level; armour +0.5 armour and +0.25 toughness", 9, 46, W - 18, Glass.TEXT, 2);
            String where = r.level() == 1 ? "At any Forge, or by Oren the Blacksmith, who brings the shards"
                    : "In an Armory with a tier " + Upgrades.roman((r.level() + 1) / 2) + " Forge, or by Oren, who brings the shards";
            JeiLook.wrap(g, where, 9, 70, W - 18, Glass.LABEL, 2);
        }
    }

    static final class Upgrade extends SystemCategory<Recipes.Upgrade> {
        Upgrade(IDrawable icon) {
            super(UPGRADE, title("hearth_upgrade"), icon, W, 82);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Upgrade r, IFocusGroup focuses) {
            for (int i = 0; i < r.price().size(); i++) b.addInputSlot(9 + 19 * i, 23).add(r.price().get(i));
            b.addOutputSlot(141, 23).add(r.station());
        }

        @Override
        int accent(Recipes.Upgrade r) {
            return r.rank().colour;
        }

        @Override
        void drawRecipe(Recipes.Upgrade r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.title(g, r.what(), 9, 8, Glass.TITLE);
            String chip = r.rank().label + "-RANK";
            int cw = JeiLook.font().width(chip) + 8;
            JeiLook.chip(g, chip, W - 8 - cw, 6, r.rank().colour);
            for (int i = 0; i < r.price().size(); i++) JeiLook.slot(g, 9 + 19 * i, 23);
            JeiLook.arrow(g, 110, 31, r.rank().colour);
            JeiLook.resultSlot(g, 137, 19, r.rank().colour);
            boolean anchor = r.what().startsWith("Hearth");
            if (anchor) JeiLook.row(g, "Reach", HearthRecord.radiusForTier(r.target()) + " blocks", 9, 46, Glass.TEXT);
            else JeiLook.row(g, "Tempers to", Upgrades.roman(Math.min(Upgrades.MAX_TEMPER, r.target() * 2)) + " in an Armory", 9, 46, Glass.TEXT);
            JeiLook.wrap(g, "Needs " + r.rank().label + "-rank. 15% cheaper for every rank you hold above it", 9, 58, W - 18, Glass.LABEL, 2);
        }
    }

    static final class EchoDuty extends SystemCategory<Recipes.Duty> {
        EchoDuty(IDrawable icon) {
            super(DUTY, title("echo_duty"), icon, W, 80);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Duty r, IFocusGroup focuses) {
            b.addInputSlot(9, 23).add(r.pad());
            for (int i = 0; i < r.ores().size(); i++) b.addOutputSlot(37 + 19 * i, 23).add(r.ores().get(i));
        }

        @Override
        int accent(Recipes.Duty r) {
            return Glass.LANTERN;
        }

        @Override
        void drawRecipe(Recipes.Duty r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.title(g, "Mine Echo", 9, 8, Glass.TITLE);
            String chip = "ANCHOR " + Upgrades.roman(r.tier());
            int cw = JeiLook.font().width(chip) + 8;
            JeiLook.chip(g, chip, W - 8 - cw, 6, Glass.LANTERN);
            JeiLook.slot(g, 9, 23);
            JeiLook.text(g, "›", 29, 27, Glass.LABEL);
            for (int i = 0; i < r.ores().size(); i++) {
                JeiLook.slot(g, 37 + 19 * i, 23);
                String pct = Math.round(r.permille().get(i) / 10.0) + "%";
                int pw = JeiLook.font().width(pct);
                JeiLook.text(g, pct, 37 + 19 * i + 9 - pw / 2, 44, Glass.TEXT);
            }
            JeiLook.wrap(g, "One haul every " + dev.awaken.hearth.echo.Duty.MINE_TICKS / 1200 + " minutes, into a chest by the pad (a level "
                    + Recipes.DUTY_ECHO_LEVEL + " Echo)", 9, 56, W - 18, Glass.LABEL, 2);
        }
    }

    // ---- essence and cores ------------------------------------------------------------------------------------------

    static final class Essence extends SystemCategory<Recipes.Essence> {
        Essence(IDrawable icon) {
            super(ESSENCE, title("essence"), icon, W, 92);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Essence r, IFocusGroup focuses) {
            for (int i = 0; i < r.inputs().size(); i++) b.addInputSlot(9 + 19 * i, 23).add(r.inputs().get(i));
            b.addOutputSlot(141, 23).add(r.output());
        }

        @Override
        int accent(Recipes.Essence r) {
            return r.accent();
        }

        @Override
        void drawRecipe(Recipes.Essence r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.title(g, r.name(), 9, 8, Glass.TITLE);
            for (int i = 0; i < r.inputs().size(); i++) JeiLook.slot(g, 9 + 19 * i, 23);
            JeiLook.arrow(g, 110, 31, r.accent());
            JeiLook.resultSlot(g, 137, 19, r.accent());
            JeiLook.wrap(g, r.text(), 9, 49, W - 18, Glass.LABEL, 4);
        }
    }

    static final class Fuse extends SystemCategory<Recipes.Fuse> {
        Fuse(IDrawable icon) {
            super(FUSE, title("core_fusing"), icon, W, 82);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Fuse r, IFocusGroup focuses) {
            for (int i = 0; i < r.inputs().size(); i++) b.addInputSlot(9 + 19 * i, 23).add(r.inputs().get(i));
            b.addOutputSlot(141, 23).add(r.output());
        }

        @Override
        void drawRecipe(Recipes.Fuse r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.title(g, "Fuse a Power Core", 9, 8, Glass.TITLE);
            for (int i = 0; i < r.inputs().size(); i++) JeiLook.slot(g, 9 + 19 * i, 23);
            JeiLook.arrow(g, 110, 31, Glass.EDGE);
            JeiLook.resultSlot(g, 137, 19, Glass.EDGE);
            JeiLook.wrap(g, r.text(), 9, 49, W - 18, Glass.LABEL, 3);
        }
    }

    // ---- the Codex: info pages --------------------------------------------------------------------------------------

    static final class Codex extends SystemCategory<Recipes.Page> {
        Codex(IDrawable icon) {
            super(CODEX, title("codex"), icon, W, 124);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Recipes.Page r, IFocusGroup focuses) {
            b.addOutputSlot(9, 9).add(r.item());
        }

        @Override
        int accent(Recipes.Page r) {
            return r.accent();
        }

        @Override
        void drawRecipe(Recipes.Page r, GuiGraphicsExtractor g, double mx, double my) {
            JeiLook.slot(g, 9, 9);
            JeiLook.title(g, Component.translatable("awaken.jei.codex." + r.key() + ".title").getString(), 32, 9, Glass.TITLE);
            JeiLook.text(g, Component.translatable("awaken.jei.codex." + r.key() + ".kind"), 32, 19, r.accent());
            JeiLook.rule(g, 6, W - 6, 31);
            int y = JeiLook.wrap(g, Component.translatable("awaken.jei.codex." + r.key() + ".text"), 9, 36, W - 18, Glass.TEXT, r.extra().isEmpty() ? 8 : 4);
            for (String line : r.extra()) y = JeiLook.wrap(g, line, 9, y + 1, W - 18, Glass.LABEL, 2);
        }
    }
}
