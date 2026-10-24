package dev.awaken.holo.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A {@link GuiGraphicsExtractor} that draws onto a holo panel. Status sections written straight against the vanilla
 * graphics (the Gate Map, Powers, the Descent ledger) and every {@link dev.awaken.signal.client.Ui} section run
 * unchanged on a panel through it: the primitives they use (fills, gradients, lines, outlines, text, GUI sprites,
 * items and their decorations, scissors, tooltips, pose translate and scale) are recorded into a {@link HoloCanvas}.
 * Anything else falls through to a private render state that is never drawn and is cleared every frame, so an
 * unsupported call is invisible on the panel instead of breaking it. It is also a {@link UiCanvas}, so {@code Ui}
 * draws through it without the detour.
 */
public final class HoloGraphics extends GuiGraphicsExtractor implements UiCanvas {
    private final GuiRenderState sink;
    private HoloCanvas canvas;
    private int width, height;
    private final Vector2f a = new Vector2f(), b = new Vector2f();

    private HoloGraphics(GuiRenderState sink) {
        super(Minecraft.getInstance(), sink, -10000, -10000);
        this.sink = sink;
    }

    public static HoloGraphics create() {
        return new HoloGraphics(new GuiRenderState());
    }

    /** Points at the canvas for this frame. */
    public HoloGraphics begin(HoloCanvas canvas, int width, int height) {
        this.canvas = canvas;
        this.width = width;
        this.height = height;
        sink.reset();
        pose().clear();
        return this;
    }

    public HoloCanvas canvas() {
        return canvas;
    }

    @Override
    public int guiWidth() {
        return width;
    }

    @Override
    public int guiHeight() {
        return height;
    }

    private Vector2f at(Vector2f out, float x, float y) {
        return pose().transformPosition(x, y, out);
    }

    private float scale() {
        Matrix3x2f m = pose();
        return (float) Math.sqrt(m.m00 * m.m00 + m.m01 * m.m01);
    }

    // ---- shapes ---------------------------------------------------------------------------------------------------

    @Override
    public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col) {
        at(a, x0, y0);
        at(b, x1, y1);
        canvas.fillF(a.x, a.y, b.x, b.y, col, col);
    }

    @Override
    public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2) {
        at(a, x0, y0);
        at(b, x1, y1);
        canvas.fillF(a.x, a.y, b.x, b.y, col1, col2);
    }

    @Override
    public void enableScissor(int x0, int y0, int x1, int y1) {
        // the canvas clips; vanilla's scissor stack (which allocates) is never drawn here
        at(a, x0, y0);
        at(b, x1, y1);
        canvas.pushClip(a.x, a.y, b.x, b.y);
    }

    @Override
    public void disableScissor() {
        canvas.popClip();
    }

    // ---- text -----------------------------------------------------------------------------------------------------

    @Override
    public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
        at(a, x, y);
        canvas.textF(str, a.x, a.y, color, dropShadow, scale());
    }

    @Override
    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int xo, int yo, @Nullable Identifier style) {
        canvas.tooltip(font, lines, xo, yo);
    }

    // ---- sprites and items ----------------------------------------------------------------------------------------

    @Override
    public void blitSprite(RenderPipeline renderPipeline, Identifier location, int x, int y, int w, int h, int color) {
        at(a, x, y);
        float s = scale();
        canvas.spriteF(location, a.x, a.y, w * s, h * s, color);
    }

    @Override
    public void blitSprite(RenderPipeline renderPipeline, TextureAtlasSprite sprite, int x, int y, int w, int h, int color) {
        at(a, x, y);
        float s = scale();
        canvas.spriteF(sprite.contents().name(), a.x, a.y, w * s, h * s, color);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        at(a, x, y);
        canvas.itemF(stack, a.x, a.y);
    }

    @Override
    public void item(ItemStack stack, int x, int y, int seed) {
        item(stack, x, y);
    }

    @Override
    public void item(LivingEntity owner, ItemStack stack, int x, int y, int seed) {
        item(stack, x, y);
    }

    @Override
    public void fakeItem(ItemStack stack, int x, int y) {
        item(stack, x, y);
    }

    @Override
    public void fakeItem(ItemStack stack, int x, int y, int seed) {
        item(stack, x, y);
    }

    // ---- UiCanvas (Ui draws here directly) ------------------------------------------------------------------------

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        fill(null, x0, y0, x1, y1, argb);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        fillGradient(x0, y0, x1, y1, top, bottom);
    }

    @Override
    public void text(Font font, String s, int x, int y, int argb, boolean shadow) {
        at(a, x, y);
        canvas.textF(s, a.x, a.y, argb, shadow, scale());
    }

    @Override
    public void sprite(Identifier sprite, int x, int y, int w, int h, int argb) {
        blitSprite(null, sprite, x, y, w, h, argb);
    }

    @Override
    public void tooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        canvas.tooltip(font, lines, x, y);
    }

    @Override
    public void hLine(int x0, int x1, int y, int argb) {
        horizontalLine(x0, x1, y, argb);
    }

    @Override
    public void vLine(int x, int y0, int y1, int argb) {
        verticalLine(x, y0, y1, argb);
    }
}
