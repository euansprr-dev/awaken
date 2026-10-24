package dev.awaken.signal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The flat {@link UiCanvas}: every call goes to the same {@link GuiGraphicsExtractor} method the code called before
 * the canvas existed (lines and outlines included), so the 2D screens draw exactly the same pixels.
 */
public final class GuiCanvas implements UiCanvas {
    private static GuiCanvas last;
    private final GuiGraphicsExtractor g;

    public GuiCanvas(GuiGraphicsExtractor g) {
        this.g = g;
    }

    /** The canvas for {@code g}: the graphics itself when it is a canvas (a holo panel's), else a flat wrapper. */
    public static UiCanvas of(GuiGraphicsExtractor g) {
        if (g instanceof UiCanvas c) return c;
        GuiCanvas l = last;
        if (l == null || l.g != g) last = l = new GuiCanvas(g); // one per frame's graphics
        return l;
    }

    public GuiGraphicsExtractor graphics() {
        return g;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        g.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    @Override
    public void text(Font font, String s, int x, int y, int argb, boolean shadow) {
        g.text(font, s, x, y, argb, shadow);
    }

    @Override
    public void text(Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow) {
        g.text(font, s, x, y, argb, shadow);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        g.item(stack, x, y);
    }

    @Override
    public void itemSized(ItemStack stack, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    @Override
    public void itemTooltip(Font font, ItemStack stack, int x, int y) {
        g.setTooltipForNextFrame(font, stack, x, y);
    }

    @Override
    public void sprite(Identifier sprite, int x, int y, int w, int h, int argb) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, argb);
    }

    /** UVs go to vanilla's blit as texels of a notional R×R texture (it divides by the texture size). */
    private static final int R = 1 << 14;

    @Override
    public void texture(Identifier texture, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int argb) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u0 * R, v0 * R, w, h, Math.round((u1 - u0) * R), Math.round((v1 - v0) * R), R, R, argb);
    }

    @Override
    public void tooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        g.setTooltipForNextFrame(font, lines, x, y);
    }

    @Override
    public void hLine(int x0, int x1, int y, int argb) {
        g.horizontalLine(x0, x1, y, argb);
    }

    @Override
    public void vLine(int x, int y0, int y1, int argb) {
        g.verticalLine(x, y0, y1, argb);
    }

    @Override
    public void outline(int x, int y, int w, int h, int argb) {
        g.outline(x, y, w, h, argb);
    }
}
