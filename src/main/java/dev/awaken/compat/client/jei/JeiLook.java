package dev.awaken.compat.client.jei;

import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The System's look inside JEI: every Awaken category draws its own glass panel ({@link Glass#panel}, the Status
 * screen's), dark glass slots with a faint signal-blue edge, a framed result slot in the recipe's accent and the
 * System's chips and rows. JEI's grey recipe border is switched off ({@code needsRecipeBorder}).
 */
final class JeiLook {
    private JeiLook() {}

    static final int SLOT_BG = 0x070C16;

    static Font font() {
        return Minecraft.getInstance().font;
    }

    /** The category's glass, one pixel in so the corner brackets fit inside JEI's area. */
    static void panel(GuiGraphicsExtractor g, int w, int h, int accent) {
        Glass.panel(g, 1, 1, w - 2, h - 2, accent, 1f);
    }

    /** A slot frame around a JEI slot placed at (x, y) (18×18 with its border). */
    static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, Glass.a(SLOT_BG, 0.92f));
        g.outline(x, y, 18, 18, Glass.a(Glass.EDGE, 0.28f));
    }

    /** The result's frame: 26×26 around an 18×18 slot at (x + 4, y + 4), edged in the accent with corner ticks. */
    static void resultSlot(GuiGraphicsExtractor g, int x, int y, int accent) {
        g.fill(x, y, x + 26, y + 26, Glass.a(SLOT_BG, 0.95f));
        g.outline(x, y, 26, 26, Glass.a(accent, 0.55f));
        int c = Glass.a(accent, 1f);
        g.fill(x - 1, y - 1, x + 5, y, c);
        g.fill(x - 1, y - 1, x, y + 5, c);
        g.fill(x + 21, y + 26, x + 27, y + 27, c);
        g.fill(x + 26, y + 21, x + 27, y + 27, c);
    }

    /** A thin signal arrow, 20 wide, centred on y. */
    static void arrow(GuiGraphicsExtractor g, int x, int y, int colour) {
        int c = Glass.a(colour, 0.85f);
        g.fill(x, y, x + 16, y + 1, c);
        for (int i = 0; i < 4; i++) {
            g.fill(x + 16 + i - 3, y - 3 + i, x + 17 + i - 3, y - 2 + i, c);
            g.fill(x + 16 + i - 3, y + 3 - i, x + 17 + i - 3, y + 4 - i, c);
        }
    }

    static void text(GuiGraphicsExtractor g, String s, int x, int y, int rgb) {
        g.text(font(), s, x, y, Glass.a(rgb, 1f), false);
    }

    static void text(GuiGraphicsExtractor g, Component s, int x, int y, int rgb) {
        g.text(font(), s, x, y, Glass.a(rgb, 1f), false);
    }

    /** One line cut to {@code maxWidth} with an ellipsis. */
    static void fit(GuiGraphicsExtractor g, String s, int x, int y, int maxWidth, int rgb) {
        Font f = font();
        if (f.width(s) > maxWidth) {
            while (!s.isEmpty() && f.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
            s = s.stripTrailing() + "…";
        }
        text(g, s, x, y, rgb);
    }

    /** Title text with the System's soft shadow. */
    static void title(GuiGraphicsExtractor g, String s, int x, int y, int rgb) {
        g.text(font(), s, x, y, Glass.a(rgb, 1f), true);
    }

    /** "Label  value" on one line; returns the width drawn. */
    static int row(GuiGraphicsExtractor g, String label, String value, int x, int y, int valueColour) {
        Font f = font();
        text(g, label, x, y, Glass.LABEL);
        int lx = x + f.width(label) + 4;
        text(g, value, lx, y, valueColour);
        return lx + f.width(value) - x;
    }

    /** Wrapped text, at most {@code maxLines}; returns the y below it. */
    static int wrap(GuiGraphicsExtractor g, Component s, int x, int y, int width, int rgb, int maxLines) {
        List<FormattedCharSequence> lines = font().split(s, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.text(font(), lines.get(i), x, y, Glass.a(rgb, 1f), false);
            y += 10;
        }
        return y;
    }

    static int wrap(GuiGraphicsExtractor g, String s, int x, int y, int width, int rgb, int maxLines) {
        return wrap(g, Component.literal(s), x, y, width, rgb, maxLines);
    }

    /** A System chip (rank badge, "BLUEPRINT"); returns its width. */
    static int chip(GuiGraphicsExtractor g, String label, int x, int y, int colour) {
        return Glass.chip(g, font(), label, x, y, colour);
    }

    /** A thin rule across the panel. */
    static void rule(GuiGraphicsExtractor g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, Glass.a(Glass.EDGE, 0.16f));
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return Glass.inside(mx, my, x, y, w, h);
    }
}
