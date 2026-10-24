package dev.awaken.signal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * AWAKEN's interface look: deep glass, a thin signal-blue edge, corner brackets, pale labels, amber for "not true yet".
 * Every colour is ARGB. Use {@link #a(int, float)} to fade a colour.
 */
public final class Glass {
    private Glass() {}

    public static final int GLASS = 0x0F1724;
    public static final int GLASS_TOP = 0x16233A;
    public static final int EDGE = 0x7FC3FF;
    public static final int TITLE = 0xFFFFFF;
    public static final int TEXT = 0xD9E4F2;
    public static final int LABEL = 0x8FA7C4;
    public static final int AMBER = 0xFFCF7A;
    public static final int WARN = 0xFF8A7A;
    public static final int GOOD = 0x8FF0C0;
    public static final int VIOLET = 0xB48CFF;
    public static final int LANTERN = 0xF2A541;

    /** Colour with alpha in [0,1]. */
    public static int a(int rgb, float alpha) {
        int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** A glass panel with an edge and corner brackets. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge, float alpha) {
        panel(GuiCanvas.of(g), x, y, w, h, edge, alpha);
    }

    public static void panel(UiCanvas c, int x, int y, int w, int h, int edge, float alpha) {
        c.gradient(x, y, x + w, y + h, a(GLASS_TOP, 0.92f * alpha), a(GLASS, 0.90f * alpha));
        c.outline(x, y, w, h, a(edge, 0.45f * alpha));
        int col = a(edge, alpha);
        int L = Math.min(10, Math.min(w, h) / 3);
        c.hLine(x - 1, x - 1 + L, y - 1, col);
        c.vLine(x - 1, y - 1, y - 1 + L, col);
        c.hLine(x + w - L, x + w, y + h, col);
        c.vLine(x + w, y + h - L, y + h, col);
        // faint inner glow line under the top edge
        c.hLine(x + 1, x + w - 2, y + 1, a(edge, 0.10f * alpha));
    }

    /** A compact System v2 card: glass, a faint edge and a solid accent bar on the left. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float alpha) {
        card(GuiCanvas.of(g), x, y, w, h, accent, alpha);
    }

    public static void card(UiCanvas c, int x, int y, int w, int h, int accent, float alpha) {
        c.gradient(x, y, x + w, y + h, a(GLASS_TOP, 0.90f * alpha), a(GLASS, 0.90f * alpha));
        c.outline(x, y, w, h, a(accent, 0.35f * alpha));
        c.fill(x, y, x + 2, y + h, a(accent, alpha));
    }

    /** Horizontal progress bar. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, int colour, float alpha) {
        bar(GuiCanvas.of(g), x, y, w, h, fraction, colour, alpha);
    }

    public static void bar(UiCanvas c, int x, int y, int w, int h, double fraction, int colour, float alpha) {
        c.fill(x, y, x + w, y + h, a(0x0A1020, 0.9f * alpha));
        int fw = (int) Math.round(Math.max(0, Math.min(1, fraction)) * w);
        if (fw > 0) c.gradient(x, y, x + fw, y + h, a(colour, alpha), a(darken(colour), alpha));
        c.outline(x, y, w, h, a(EDGE, 0.25f * alpha));
    }

    /** A small clickable button. Returns nothing; hit-test with {@link #inside}. */
    public static void button(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w, int h, boolean hover, boolean enabled, int accent) {
        button(GuiCanvas.of(g), font, label, x, y, w, h, hover, enabled, accent);
    }

    public static void button(UiCanvas c, Font font, String label, int x, int y, int w, int h, boolean hover, boolean enabled, int accent) {
        int bg = !enabled ? a(0x1A2232, 0.8f) : hover ? a(accent, 0.30f) : a(0x1B2A44, 0.85f);
        c.fill(x, y, x + w, y + h, bg);
        c.outline(x, y, w, h, a(enabled ? accent : LABEL, enabled ? (hover ? 1f : 0.6f) : 0.3f));
        int tw = font.width(label);
        c.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, a(enabled ? TITLE : LABEL, 1f), false);
    }

    /** A coloured chip (rank badge etc.). */
    public static int chip(GuiGraphicsExtractor g, Font font, String label, int x, int y, int colour) {
        return chip(GuiCanvas.of(g), font, label, x, y, colour);
    }

    public static int chip(UiCanvas c, Font font, String label, int x, int y, int colour) {
        int w = font.width(label) + 8;
        c.fill(x, y, x + w, y + 11, a(colour, 0.18f));
        c.outline(x, y, w, 11, a(colour, 0.9f));
        c.text(font, label, x + 4, y + 2, a(colour, 1f), false);
        return w;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static int darken(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r * 6 / 10) << 16) | ((gr * 6 / 10) << 8) | (b * 6 / 10);
    }
}
