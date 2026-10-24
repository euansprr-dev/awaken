package dev.awaken.signal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What {@link Ui} and {@link Glass} draw through. {@link GuiCanvas} is the flat screen (it forwards every call to the
 * same {@code GuiGraphicsExtractor} method the code used before, so the pixels don't change); the holo module records
 * the same calls onto a 3D panel. Coordinates are GUI pixels (panel units on a holo panel), colours ARGB.
 */
public interface UiCanvas {
    /** A filled rectangle; corners in any order, like {@code GuiGraphicsExtractor.fill}. */
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** A vertical gradient, top colour to bottom colour. */
    void gradient(int x0, int y0, int x1, int y1, int top, int bottom);

    /** One line of text. Alpha 0 draws nothing (as in vanilla). */
    void text(Font font, String s, int x, int y, int argb, boolean shadow);

    void text(Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow);

    /** A 16×16 item icon. */
    void item(ItemStack stack, int x, int y);

    /** An item icon {@code size} px square. */
    void itemSized(ItemStack stack, int x, int y, int size);

    /** An item's own tooltip for the next frame at the mouse. */
    void itemTooltip(Font font, ItemStack stack, int x, int y);

    /** A GUI-atlas sprite ({@code textures/gui/sprites/...}), tinted. */
    void sprite(Identifier sprite, int x, int y, int w, int h, int argb);

    /**
     * Part of a whole texture (an Identifier'd DynamicTexture, or a render target registered with the TextureManager),
     * tinted: one quad from (x, y) to (x + w, y + h) showing UVs (u0, v0)–(u1, v1) in 0..1.
     */
    void texture(Identifier texture, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int argb);

    /** A tooltip for the next frame at the mouse. */
    void tooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y);

    default void hLine(int x0, int x1, int y, int argb) {
        if (x1 < x0) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        fill(x0, y, x1 + 1, y + 1, argb);
    }

    default void vLine(int x, int y0, int y1, int argb) {
        if (y1 < y0) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        fill(x, y0 + 1, x + 1, y1, argb);
    }

    default void outline(int x, int y, int w, int h, int argb) {
        fill(x, y, x + w, y + 1, argb);
        fill(x, y + h - 1, x + w, y + h, argb);
        fill(x, y + 1, x + 1, y + h - 1, argb);
        fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }
}
