package dev.awaken.compat.client.jei;

import dev.awaken.signal.client.Glass;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** An Awaken JEI category: the System's glass under every recipe, no JEI border. Subclasses draw their own content. */
abstract class SystemCategory<T> implements IRecipeCategory<T> {
    private final IRecipeType<T> type;
    private final Component title;
    private final IDrawable icon;
    private final int width, height;

    SystemCategory(IRecipeType<T> type, Component title, IDrawable icon, int width, int height) {
        this.type = type;
        this.title = title;
        this.icon = icon;
        this.width = width;
        this.height = height;
    }

    @Override
    public final IRecipeType<T> getRecipeType() {
        return type;
    }

    @Override
    public final Component getTitle() {
        return title;
    }

    @Override
    public final int getWidth() {
        return width;
    }

    @Override
    public final int getHeight() {
        return height;
    }

    @Override
    public final IDrawable getIcon() {
        return icon;
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    /** The recipe's accent: the panel's brackets and its result frame. */
    int accent(T recipe) {
        return Glass.EDGE;
    }

    @Override
    public final void draw(T recipe, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        JeiLook.panel(g, width, height, accent(recipe));
        drawRecipe(recipe, g, mouseX, mouseY);
    }

    abstract void drawRecipe(T recipe, GuiGraphicsExtractor g, double mouseX, double mouseY);
}
