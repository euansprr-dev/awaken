package dev.awaken.core.client.ui;

import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The one sheet every big AWAKEN screen is laid out on (SPEC §57, after the Hall's own, §52): 1708 x 960, fitted to the
 * window and letterboxed, so a thing is the same thing at every GUI scale. Text is sized in sheet pixels to a font pixel and
 * snapped to whole real ones, so it never blurs. Colours are {@link Glass}'s, by job. One per screen; {@link #begin} each
 * frame.
 */
public final class Sheet {
    public static final int W = 1708, H = 960;
    /** Type roles, in sheet pixels to one font pixel: a number, the place's name, a thing's name, a line, a label. */
    public static final float NUMERAL = 6F, DISPLAY = 5F, TITLE = 3F, BODY = 2F, LABEL = 1.5F;
    /** E, D, C, B, A, S. */
    public static final int[] RANK = {0xF4F6FB, 0x5FD08F, 0x6FB3FF, 0xB48CFF, 0xF2A541, 0xFF5A6A};

    public final Font font;
    public GuiGraphicsExtractor g;
    /** The sheet's place in GUI pixels and its scale; real pixels to a sheet pixel. */
    public float ox, oy, k = 1F, rps = 1F;
    /** The quiet text colours of this frame: brighter under the game's High Contrast option. */
    public int label = Glass.LABEL, dim = 0x6F829C;
    /** High Contrast: solid surfaces and bright rims. */
    public boolean bright;
    /** One step of the Band size setting: every text size goes a real pixel down or up. */
    public int sizeStep;

    public Sheet(Font font) {
        this.font = font;
    }

    /** The game's Distortion Effects slider turned down: things arrive rather than fly, and nothing turns by itself. */
    public static boolean calm() {
        return Minecraft.getInstance().options.screenEffectScale().get() < .5D;
    }

    /** For the narrator, on a change only. */
    public static void say(String words) {
        var narrator = Minecraft.getInstance().getNarrator();
        if (narrator.isActive()) narrator.saySystemNow(words);
    }

    /** The fit of a sheet in a window: its scale and corner. Pure, so a test can check it. */
    public static float[] fit(int width, int height) {
        float k = Math.min(width / (float) W, height / (float) H);
        return new float[] {k, (width - W * k) / 2F, (height - H * k) / 2F};
    }

    public void begin(GuiGraphicsExtractor g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        bright = mc.options.highContrast().get();
        label = bright ? 0xF0F4FA : Glass.LABEL;
        dim = bright ? 0xC8D4E2 : 0x6F829C;
        this.g = g;
        float[] f = fit(width, height);
        k = f[0];
        ox = f[1];
        oy = f[2];
        rps = k * mc.getWindow().getGuiScale();
        g.pose().pushMatrix().translate(ox, oy).scale(k, k);
    }

    public void end() {
        g.pose().popMatrix();
    }

    /** A GUI coordinate on the sheet. */
    public float sx(double gui) {
        return (float) ((gui - ox) / k);
    }

    public float sy(double gui) {
        return (float) ((gui - oy) / k);
    }

    /** A sheet coordinate in GUI pixels (what an entity's box and a tooltip take). */
    public int gx(float sheet) {
        return Math.round(ox + sheet * k);
    }

    public int gy(float sheet) {
        return Math.round(oy + sheet * k);
    }

    /** A text size snapped to whole real pixels: down, but a hair under a whole step still counts. */
    public float snap(float n) {
        return Math.max(1, (int) Math.floor(n * rps + .35F) + sizeStep) / rps;
    }

    private float grid(float v) {
        return Math.round(v * rps) / rps;
    }

    public static int argb(int rgb, float a) {
        return Math.clamp(Math.round(a * 255F), 0, 255) << 24 | rgb & 0xFFFFFF;
    }

    // ---- shapes ---------------------------------------------------------------------------------------------------

    public void fill(float x0, float y0, float x1, float y1, int colour) {
        g.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), colour);
    }

    public void gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        g.fillGradient(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), top, bottom);
    }

    public void outline(float x0, float y0, float x1, float y1, int t, int colour) {
        fill(x0, y0, x1, y0 + t, colour);
        fill(x0, y1 - t, x1, y1, colour);
        fill(x0, y0 + t, x0 + t, y1 - t, colour);
        fill(x1 - t, y0 + t, x1, y1 - t, colour);
    }

    /** A straight thin line between two sheet points, at any angle. */
    public void ray(float x0, float y0, float x1, float y1, float t, int colour) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushMatrix().translate(x0, y0).rotate((float) Math.atan2(dy, dx));
        g.fill(0, Math.round(-t / 2F), Math.round(len), Math.max(1, Math.round(t / 2F)), colour);
        g.pose().popMatrix();
    }

    /** A soft light round a rectangle, in the band's glass (nothing on the flat look). */
    public void glow(float x0, float y0, float x1, float y1, int rgb, float strength) {
        if (strength > .01F) HudLook.glow(g, Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0), rgb, strength);
    }

    // ---- text -----------------------------------------------------------------------------------------------------

    public void text(String s, float x, float y, float n, int colour) {
        float size = snap(n);
        g.pose().pushMatrix().translate(grid(x), grid(y)).scale(size, size);
        g.text(font, s, 0, 0, colour, false);
        g.pose().popMatrix();
    }

    public void text(FormattedCharSequence s, float x, float y, float n, int colour) {
        float size = snap(n);
        g.pose().pushMatrix().translate(grid(x), grid(y)).scale(size, size);
        g.text(font, s, 0, 0, colour, false);
        g.pose().popMatrix();
    }

    /** Text as light: the band's halo round it where the projected look is on. */
    public void lit(String s, float x, float y, float n, int colour) {
        float size = snap(n);
        g.pose().pushMatrix().translate(grid(x), grid(y)).scale(size, size);
        HudLook.text(g, font, s, 0, 0, colour, false);
        g.pose().popMatrix();
    }

    public void right(String s, float x, float y, float n, int colour) {
        text(s, x - width(s, n), y, n, colour);
    }

    public void centre(String s, float x, float y, float n, int colour) {
        text(s, x - width(s, n) / 2F, y, n, colour);
    }

    /** Spaced capitals: a caption under a number, a lane's name. */
    public void caps(String s, float x, float y, int colour) {
        text(s.toUpperCase(java.util.Locale.ROOT), x, y, LABEL, colour);
    }

    public float width(String s, float n) {
        return font.width(s) * snap(n);
    }

    /** The height of a line of text at size n (the font's nine pixels and one of air). */
    public float line(float n) {
        return 10F * snap(n);
    }

    /** Lines of at most {@code width} sheet pixels at size n. */
    public List<FormattedCharSequence> wrap(String s, float width, float n) {
        return font.split(Component.literal(s), Math.max(1, (int) (width / snap(n))));
    }

    /**
     * Wrapped lines from y down, every one of them: text on a sheet is never cut and never ends in dots (SPEC §57.6). What
     * holds it grows, or scrolls. Returns the y under the last line.
     */
    public float lines(String s, float x, float y, float width, float n, int colour) {
        float step = line(n);
        for (FormattedCharSequence l : wrap(s, width, n)) {
            text(l, x, y, n, colour);
            y += step;
        }
        return y;
    }

    /** As {@link #lines}, each line centred on cx. */
    public float linesCentred(String s, float cx, float y, float width, float n, int colour) {
        float step = line(n), size = snap(n);
        for (FormattedCharSequence l : wrap(s, width, n)) {
            text(l, cx - font.width(l) * size / 2F, y, n, colour);
            y += step;
        }
        return y;
    }

    /** As {@link #lines}, each line ending at x1. */
    public float linesRight(String s, float x1, float y, float width, float n, int colour) {
        float step = line(n), size = snap(n);
        for (FormattedCharSequence l : wrap(s, width, n)) {
            text(l, x1 - font.width(l) * size, y, n, colour);
            y += step;
        }
        return y;
    }

    /** How tall {@code s} is, wrapped to a width at size n. */
    public float tall(String s, float width, float n) {
        return s.isEmpty() ? 0F : wrap(s, width, n).size() * line(n);
    }

    // ---- things ---------------------------------------------------------------------------------------------------

    /** The item itself, as your inventory draws it: {@code size} sheet pixels across. */
    public void item(ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) return;
        float n = size / 16F;
        g.pose().pushMatrix().translate(x, y).scale(n, n);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    public static boolean in(float mx, float my, float x0, float y0, float x1, float y1) {
        return mx >= x0 && my >= y0 && mx < x1 && my < y1;
    }
}
