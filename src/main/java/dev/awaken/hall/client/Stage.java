package dev.awaken.hall.client;

import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The Hall's page (SPEC §52): everything is laid out on one 1708 x 960 sheet, fitted to the window and letterboxed, so a
 * niche is the same niche at every GUI scale. Text is sized in sheet pixels to a font pixel and snapped to whole real ones.
 */
final class Stage {
    static final int W = 1708, H = 960;
    static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, GOOD = 0xFF9FE6D6, BAD = 0xFFFF8A7A;
    /** The quiet text colours, brighter under the game's High Contrast option. */
    static int LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299;
    /** E, D, C, B, A, S. */
    static final int[] RANK = {0xF4F6FB, 0x5FD08F, 0x6FB3FF, 0xB48CFF, 0xF2A541, 0xFF5A6A};
    static final Identifier GLOW = Awaken.id("textures/gui/hall/glow.png");
    private static final Style TITLE_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(Awaken.id("hall_title")));

    final Font font;
    GuiGraphicsExtractor g;
    float ox, oy, k = 1F, rps = 1F;

    Stage(Font font) {
        this.font = font;
    }

    /** The game's Distortion Effects slider turned down: things arrive rather than fly, and nothing turns by itself. */
    static boolean calm() {
        return Minecraft.getInstance().options.screenEffectScale().get() < .5D;
    }

    /** For the narrator, on a change only. */
    static void say(String words) {
        var narrator = Minecraft.getInstance().getNarrator();
        if (narrator.isActive()) narrator.saySystemNow(words);
    }

    void begin(GuiGraphicsExtractor g, int width, int height) {
        boolean bright = Minecraft.getInstance().options.highContrast().get();
        LABEL = bright ? 0xFFF0F4FA : 0xFFAFC2D8;
        DIM = bright ? 0xFFC8D4E2 : 0xFF6F8299;
        this.g = g;
        k = Math.min(width / (float) W, height / (float) H);
        ox = (width - W * k) / 2F;
        oy = (height - H * k) / 2F;
        rps = k * Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushMatrix().translate(ox, oy).scale(k, k);
    }

    void end(int width, int height) {
        g.pose().popMatrix();
        g.nextStratum();
        // anything zoomed past the sheet stops at its edge
        int top = (int) Math.floor(oy), left = (int) Math.floor(ox);
        if (top > 0) {
            g.fill(0, 0, width, top, 0xFF000000);
            g.fill(0, height - top, width, height, 0xFF000000);
        }
        if (left > 0) {
            g.fill(0, 0, left, height, 0xFF000000);
            g.fill(width - left, 0, width, height, 0xFF000000);
        }
    }

    /** The end of a page that has the world behind it: nothing is letterboxed. */
    void endBare() {
        g.pose().popMatrix();
    }

    float sx(double mouse) {
        return (float) ((mouse - ox) / k);
    }

    float sy(double mouse) {
        return (float) ((mouse - oy) / k);
    }

    /** Real pixels of texture a sheet pixel should get, held down for what's redrawn rarely and is big. */
    float real(float most) {
        return Math.min(most, rps);
    }

    float snap(float n) {
        return Math.max(1, (int) Math.floor(n * rps + .35F)) / rps; // down, but a hair under a whole step still counts: a window of 1.93 real pixels a sheet pixel keeps text at 2 x 1.93 = 4, not 3
    }

    private float grid(float v) {
        return Math.round(v * rps) / rps;
    }

    void fill(float x0, float y0, float x1, float y1, int colour) {
        g.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), colour);
    }

    void line(float x0, float y0, float x1, float y1, int colour) {
        fill(x0, y0, x1, y1, colour);
    }

    void outline(float x0, float y0, float x1, float y1, int t, int colour) {
        fill(x0, y0, x1, y0 + t, colour);
        fill(x0, y1 - t, x1, y1, colour);
        fill(x0, y0 + t, x0 + t, y1 - t, colour);
        fill(x1 - t, y0 + t, x1, y1 - t, colour);
    }

    /** A straight thin line between two sheet points, at any angle. */
    void ray(float x0, float y0, float x1, float y1, float t, int colour) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushMatrix().translate(x0, y0).rotate((float) Math.atan2(dy, dx));
        g.fill(0, Math.round(-t / 2F), Math.round(len), Math.max(1, Math.round(t / 2F)), colour);
        g.pose().popMatrix();
    }

    /** A soft round light: rgb only, its strength apart. */
    void glow(float cx, float cy, float rx, float ry, int rgb, float a) {
        int colour = Math.clamp(Math.round(a * 255F), 0, 255) << 24 | rgb & 0xFFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - rx), Math.round(cy - ry), 0F, 0F, Math.round(rx * 2F), Math.round(ry * 2F), 512, 512, 512, 512, colour);
    }

    void text(String s, float x, float y, float n, int colour) {
        text(s, x, y, n, colour, false);
    }

    void text(String s, float x, float y, float n, int colour, boolean shadow) {
        float size = snap(n);
        g.pose().pushMatrix().translate(grid(x), grid(y)).scale(size, size);
        g.text(font, s, 0, 0, colour, shadow);
        g.pose().popMatrix();
    }

    void text(FormattedCharSequence s, float x, float y, float n, int colour) {
        float size = snap(n);
        g.pose().pushMatrix().translate(grid(x), grid(y)).scale(size, size);
        g.text(font, s, 0, 0, colour, false);
        g.pose().popMatrix();
    }

    void right(String s, float x, float y, float n, int colour) {
        text(s, x - width(s, n), y, n, colour);
    }

    void centre(String s, float x, float y, float n, int colour) {
        text(s, x - width(s, n) / 2F, y, n, colour);
    }

    float width(String s, float n) {
        return font.width(s) * snap(n);
    }

    /** Lines of at most {@code width} sheet pixels at size n. */
    List<FormattedCharSequence> wrap(String s, float width, float n) {
        return font.split(Component.literal(s), Math.max(1, (int) (width / snap(n))));
    }

    String fit(String s, float width, float n) {
        if (width(s, n) <= width) return s;
        int room = (int) (width / snap(n)) - font.width("...");
        return font.plainSubstrByWidth(s, room).stripTrailing() + "...";
    }

    /** A line in the Hall's blackletter (the bitmap's 24-pixel design at n = 1). */
    static Component blackletter(String s) {
        return Component.literal(s).withStyle(TITLE_STYLE);
    }

    float titleWidth(Component c, float n) {
        return font.width(c) * snap(n);
    }

    /** Blackletter with the top of its capitals at y. */
    void title(Component c, float x, float y, float n, int colour) {
        float size = snap(n);
        g.pose().pushMatrix().translate(grid(x), grid(y + 11F * size)).scale(size, size);
        g.text(font, c, 0, 0, colour, false);
        g.pose().popMatrix();
    }

    void titleCentre(Component c, float x, float y, float n, int colour) {
        title(c, x - titleWidth(c, n) / 2F, y, n, colour);
    }

    /** An opaque-to-faded colour: rgb at a. */
    static int argb(int rgb, float a) {
        return Math.clamp(Math.round(a * 255F), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
}
