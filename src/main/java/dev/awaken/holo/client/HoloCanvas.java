package dev.awaken.holo.client;

import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A holo panel's display list. {@link dev.awaken.signal.client.Ui} (and anything drawing through {@link HoloGraphics})
 * records its fills, text, items and sprites here in panel units during the screen's extract pass; {@link HoloRender}
 * replays them onto the panel's 3D quad in the same frame. Clipping happens while recording: fills are cut to the
 * clip rectangle, text and items fade out across its top and bottom edges (a scrolling list dissolves at the edge
 * instead of popping). Every array is preallocated; recording a frame allocates nothing.
 */
public final class HoloCanvas implements UiCanvas {
    static final int MAX_FILLS = 4096, MAX_TEXT = 1024, MAX_ITEMS = 96, MAX_SPRITES = 128, MAX_CLIP = 16;
    /** Text and items fade over this many units at a clip edge. */
    private static final float FADE = 7f;

    final float[] fill = new float[MAX_FILLS * 4];
    final int[] fillTop = new int[MAX_FILLS], fillBottom = new int[MAX_FILLS];
    int fills;

    final Object[] text = new Object[MAX_TEXT]; // String or FormattedCharSequence
    final float[] textXY = new float[MAX_TEXT * 2];
    final int[] textColour = new int[MAX_TEXT];
    final boolean[] textShadow = new boolean[MAX_TEXT];
    final float[] textScale = new float[MAX_TEXT];
    int texts;

    final ItemStack[] items = new ItemStack[MAX_ITEMS];
    final float[] itemXY = new float[MAX_ITEMS * 2];
    final float[] itemAlpha = new float[MAX_ITEMS];
    int itemCount;

    final Identifier[] sprites = new Identifier[MAX_SPRITES];
    final float[] spriteRect = new float[MAX_SPRITES * 4];
    final int[] spriteColour = new int[MAX_SPRITES];
    int spriteCount;

    private final float[] clips = new float[MAX_CLIP * 4];
    private int clipDepth;
    private float cx0, cy0, cx1, cy1;

    /** Where a tooltip asked for by the layout goes: the screen's real graphics, at the real mouse. */
    private GuiGraphicsExtractor tooltipTarget;
    private int tooltipX, tooltipY;
    private boolean tooltipShown;

    /** Starts a new frame on a panel {@code w}×{@code h} units. */
    public void begin(int w, int h) {
        for (int i = 0; i < texts; i++) text[i] = null;
        for (int i = 0; i < itemCount; i++) items[i] = null;
        for (int i = 0; i < spriteCount; i++) sprites[i] = null;
        fills = texts = itemCount = spriteCount = 0;
        clipDepth = 0;
        cx0 = 0;
        cy0 = 0;
        cx1 = w;
        cy1 = h;
        tooltipTarget = null;
        tooltipShown = false;
    }

    /** Tooltips go to {@code g} at the given screen position (null: tooltips are dropped). */
    public void tooltipsTo(GuiGraphicsExtractor g, int screenX, int screenY) {
        tooltipTarget = g;
        tooltipX = screenX;
        tooltipY = screenY;
    }

    public boolean isEmpty() {
        return fills == 0 && texts == 0 && itemCount == 0 && spriteCount == 0;
    }

    // ---- clipping -------------------------------------------------------------------------------------------------

    public void pushClip(float x0, float y0, float x1, float y1) {
        if (clipDepth < MAX_CLIP) {
            int o = clipDepth * 4;
            clips[o] = cx0;
            clips[o + 1] = cy0;
            clips[o + 2] = cx1;
            clips[o + 3] = cy1;
        }
        clipDepth++;
        cx0 = Math.max(cx0, Math.min(x0, x1));
        cy0 = Math.max(cy0, Math.min(y0, y1));
        cx1 = Math.min(cx1, Math.max(x0, x1));
        cy1 = Math.min(cy1, Math.max(y0, y1));
    }

    public void popClip() {
        if (clipDepth == 0) return;
        clipDepth--;
        if (clipDepth < MAX_CLIP) {
            int o = clipDepth * 4;
            cx0 = clips[o];
            cy0 = clips[o + 1];
            cx1 = clips[o + 2];
            cy1 = clips[o + 3];
        }
    }

    /** 0..1: how much of a row from {@code y0} to {@code y1} shows inside the clip (soft at the edges). */
    private float rowAlpha(float y0, float y1) {
        if (y1 <= cy0 || y0 >= cy1) return 0f;
        float top = (y1 - cy0) / FADE, bottom = (cy1 - y0) / FADE;
        return Math.max(0f, Math.min(1f, Math.min(top, bottom)));
    }

    // ---- recording (float coordinates; the UiCanvas methods below call these) ---------------------------------------

    public void fillF(float x0, float y0, float x1, float y1, int top, int bottom) {
        if (fills >= MAX_FILLS) return;
        if ((top >>> 24) == 0 && (bottom >>> 24) == 0) return;
        float ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        float h = by - ay;
        float cay = Math.max(ay, cy0), cby = Math.min(by, cy1);
        ax = Math.max(ax, cx0);
        bx = Math.min(bx, cx1);
        if (ax >= bx || cay >= cby) return;
        if (top != bottom && h > 0) { // keep the gradient true after cutting
            int t = top;
            top = lerpColour(t, bottom, (cay - ay) / h);
            bottom = lerpColour(t, bottom, (cby - ay) / h);
        }
        int o = fills * 4;
        fill[o] = ax;
        fill[o + 1] = cay;
        fill[o + 2] = bx;
        fill[o + 3] = cby;
        fillTop[fills] = top;
        fillBottom[fills] = bottom;
        fills++;
    }

    public void textF(Object s, float x, float y, int argb, boolean shadow) {
        textF(s, x, y, argb, shadow, 1f);
    }

    public void textF(Object s, float x, float y, int argb, boolean shadow, float scale) {
        if (texts >= MAX_TEXT || s == null) return;
        if ((argb >>> 24) == 0) return;
        float a = rowAlpha(y, y + 9 * scale);
        if (a <= 0.02f) return;
        if (a < 1f) argb = ((Math.round((argb >>> 24) * a)) << 24) | (argb & 0xFFFFFF);
        text[texts] = s;
        textXY[texts * 2] = x;
        textXY[texts * 2 + 1] = y;
        textColour[texts] = argb;
        textShadow[texts] = shadow;
        textScale[texts] = scale;
        texts++;
    }

    public void itemF(ItemStack stack, float x, float y) {
        if (itemCount >= MAX_ITEMS || stack == null || stack.isEmpty()) return;
        float a = rowAlpha(y, y + 16);
        if (a <= 0.5f || x + 16 <= cx0 || x >= cx1) return; // items can't fade: show only when mostly inside
        items[itemCount] = stack;
        itemXY[itemCount * 2] = x;
        itemXY[itemCount * 2 + 1] = y;
        itemAlpha[itemCount] = a;
        itemCount++;
    }

    public void spriteF(Identifier sprite, float x, float y, float w, float h, int argb) {
        if (spriteCount >= MAX_SPRITES || sprite == null) return;
        float a = rowAlpha(y, y + h);
        if (a <= 0.02f || x + w <= cx0 || x >= cx1) return;
        if (a < 1f) argb = ((Math.round((argb >>> 24) * a)) << 24) | (argb & 0xFFFFFF);
        sprites[spriteCount] = sprite;
        int o = spriteCount * 4;
        spriteRect[o] = x;
        spriteRect[o + 1] = y;
        spriteRect[o + 2] = w;
        spriteRect[o + 3] = h;
        spriteColour[spriteCount] = argb;
        spriteCount++;
    }

    // ---- UiCanvas -------------------------------------------------------------------------------------------------

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        fillF(x0, y0, x1, y1, argb, argb);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        fillF(x0, y0, x1, y1, top, bottom);
    }

    @Override
    public void text(Font font, String s, int x, int y, int argb, boolean shadow) {
        textF(s, x, y, argb, shadow);
    }

    @Override
    public void text(Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow) {
        textF(s, x, y, argb, shadow);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        itemF(stack, x, y);
    }

    @Override
    public void sprite(Identifier sprite, int x, int y, int w, int h, int argb) {
        spriteF(sprite, x, y, w, h, argb);
    }

    @Override
    public void tooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        if (tooltipTarget == null || tooltipShown) return;
        tooltipShown = true;
        tooltipTarget.setTooltipForNextFrame(font, lines, tooltipX, tooltipY);
    }

    static int lerpColour(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
