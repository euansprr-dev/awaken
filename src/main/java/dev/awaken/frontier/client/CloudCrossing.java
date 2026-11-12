package dev.awaken.frontier.client;

import dev.awaken.Awaken;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The cloud the skiff flies through (SPEC §67), drawn like the gates' crossing: the cloud thickens until the painting of
 * where you're going fills the screen and drifts, the name surfaces letter by letter from its middle, a gold rule draws
 * out under it with how far and which way, and a hair of light in the bottom bar fills as the land arrives. When the film
 * is ready the cloud parts over the real place. It stays in the overworld, so it's an overlay, not a loading screen.
 */
final class CloudCrossing {
    private CloudCrossing() {}

    private static final int GOLD = 0xE8C98A, PALE = 0xFFF6E4;
    /** The cloud closes over this long (a quick flight: at once). */
    static final long CLOSE_MS = 1100, FAST_MS = 300;

    private static @Nullable String title, line;
    private static boolean home;
    private static long coverFrom = -1, coverMs = CLOSE_MS, partFrom = -1, partMs = 1;
    private static @Nullable Identifier art;
    private static boolean artChecked;

    static void begin(String t, String l, boolean toCity) {
        title = t.toUpperCase(Locale.ROOT);
        line = l;
        home = toCity;
        coverFrom = -1;
        partFrom = -1;
        artChecked = false;
        art = null;
    }

    /** Starts closing (once). */
    static void cover() {
        if (coverFrom < 0) {
            coverFrom = Util.getMillis();
            coverMs = CLOSE_MS;
        }
    }

    /** Closes fast (the server moved us before the lift was over). */
    static void coverNow() {
        long now = Util.getMillis();
        if (coverFrom < 0 || now - coverFrom < FAST_MS) {
            coverFrom = now - Math.round(cover(now) * FAST_MS);
            coverMs = FAST_MS;
        }
    }

    /** Parts over {@code ticks} ticks, onto the real place. */
    static void part(int ticks) {
        partFrom = Util.getMillis();
        partMs = ticks * 50L;
    }

    static void end() {
        title = null;
        coverFrom = -1;
        partFrom = -1;
    }

    /** How closed the cloud is now, 0 to 1. */
    private static float cover(long now) {
        if (coverFrom < 0) return 0;
        float k = Mth.clamp((now - coverFrom) / (float) coverMs, 0, 1);
        float a = k * k * (3 - 2 * k);
        if (partFrom >= 0) {
            float p = Mth.clamp((now - partFrom) / (float) partMs, 0, 1);
            a *= 1 - p * p * (3 - 2 * p);
        }
        return a;
    }

    private static @Nullable Identifier art() {
        if (!artChecked) {
            artChecked = true;
            Identifier id = Awaken.id("textures/gui/frontier/crossing_" + (home ? "home" : "out") + ".png");
            art = Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? id : null;
        }
        return art;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (title == null || coverFrom < 0) return;
        long now = Util.getMillis();
        float a = cover(now);
        if (a <= 0.003F) return;
        int w = g.guiWidth(), h = g.guiHeight();
        long t = now - coverFrom;
        // the painting, drifting slowly as if past the bow; a sky if it's missing
        Identifier id = art();
        if (id != null) {
            float drift = (t % 60000L) / 60000F;
            float art = 16F / 9F, screen = w / (float) h;
            float us = (screen > art ? 1 : screen / art) * 0.94F, vs = (screen > art ? art / screen : 1) * 0.94F;
            float u0 = (1 - us) / 2 + (float) Math.sin(drift * Math.PI * 2) * 0.02F, v0 = (1 - vs) / 2 + 0.01F;
            int color = Math.max(1, Math.round(a * 255)) << 24 | 0xFFFFFF;
            g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u0 * 1024, v0 * 1024, w, h, (int) (us * 1024), (int) (vs * 1024), 1024, 1024, color);
        } else {
            g.fillGradient(0, 0, w, h, argb(a, home ? 0x5A6E9A : 0x4F7FB8), argb(a, home ? 0xE9C9A0 : 0xEDE4D2));
        }
        // the cloud's own veil at the edges, thickest as it closes and as it parts
        float veil = Mth.clamp(1.6F * a * (1 - a) * 2, 0, 1);
        if (veil > 0) g.fill(0, 0, w, h, argb(veil * 0.85F, 0xF4F1EA));
        float words = Mth.clamp((a - 0.75F) / 0.25F, 0, 1);
        if (words <= 0) return;
        Font font = Minecraft.getInstance().font;
        int cx = w / 2, y = (int) (h * 0.665F);
        float scale = Mth.clamp(w / 230F, 1.6F, 3.6F);
        String kicker = home ? "HOME ON THE WIND" : "OUT ON THE WIND";
        float kickerIn = Mth.clamp((t - 620) / 420F, 0, 1) * words;
        if (kickerIn > 0) {
            g.fillGradient(0, y - 44, w, y - 8, 0, (int) (0x70 * kickerIn) << 24);
            g.fill(0, y - 8, w, y + (int) (9 * scale) + 4, (int) (0x70 * kickerIn) << 24);
            spaced(g, font, kicker, cx, y - 15, 2, argb(kickerIn, PALE));
        }
        // the name, surfacing from its middle
        String name = title;
        float surf = (t - 780) / 900F;
        int spacing = 3, total = 0, n = name.length();
        for (int i = 0; i < n; i++) total += font.width(String.valueOf(name.charAt(i))) + spacing;
        float px = -(total - spacing) / 2F, mid = (n - 1) / 2F;
        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(name.charAt(i));
            float dist = mid < 0.5F ? 0 : Math.abs(i - mid) / mid;
            float la = Mth.clamp((surf - dist * 0.55F) / 0.45F, 0, 1) * words;
            if (la > 0) {
                g.pose().pushMatrix();
                g.pose().translate(cx + px * scale, y + (1 - la) * 7);
                g.pose().scale(scale, scale);
                g.text(font, ch, 0, 0, argb(la, PALE), true);
                g.pose().popMatrix();
            }
            px += font.width(ch) + spacing;
        }
        int ruleY = (int) (y + 9 * scale + 7);
        float draw = Mth.clamp((t - 1200) / 700F, 0, 1);
        int half = (int) ((1 - (1 - draw) * (1 - draw)) * Math.min(w * 0.3F, total * scale * 0.68F));
        if (half > 0) {
            g.fillGradient(cx - half, ruleY, cx, ruleY + 1, argb(0, GOLD), argb(0.9F * words, GOLD));
            g.fillGradient(cx, ruleY, cx + half, ruleY + 1, argb(0.9F * words, GOLD), argb(0, GOLD));
        }
        float lineIn = Mth.clamp((t - 1500) / 620F, 0, 1) * words;
        if (line != null && !line.isEmpty() && lineIn > 0) g.centeredText(font, line, cx, ruleY + 8, argb(lineIn * 0.9F, PALE));
        // the land arriving: a hair of light in the bottom bar
        int barY = h - (int) (h * 0.039F), reach = (int) (w * 0.16F);
        // it creeps while the far land is made (that can take a while in new land), then fills as the land reaches you
        float shown;
        if (FlightFilm.cur != null && FlightFilm.cur.descendAt >= 0) shown = 1;
        else if (FlightFilm.cur != null && FlightFilm.cur.arrivedAt >= 0) shown = 0.85F + 0.15F * FlightFilm.landArrived(Minecraft.getInstance());
        else shown = 0.85F * (1 - (float) Math.exp(-t / 9000.0));
        int lit = (int) (reach * shown);
        float barIn = Mth.clamp((t - 500) / 400F, 0, 1) * words;
        g.fill(cx - reach, barY, cx + reach, barY + 1, argb(0.16F * barIn, PALE));
        g.fill(cx - lit, barY, cx + lit, barY + 1, argb(0.9F * barIn, GOLD));
    }

    private static void spaced(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int spacing, int argb) {
        int total = 0;
        for (int i = 0; i < s.length(); i++) total += font.width(String.valueOf(s.charAt(i))) + spacing;
        int x = cx - (total - spacing) / 2;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.text(font, ch, x, y, argb, true);
            x += font.width(ch) + spacing;
        }
    }

    private static int argb(float a, int rgb) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (rgb & 0xFFFFFF);
    }
}
