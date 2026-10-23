package dev.awaken.lucidium.client;

import dev.awaken.lucidium.dream.Lucidity;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamSign;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The lucidity meter, drawn only while dreaming: a slim column at the left edge that fills from rose (dreaming) to
 * signal blue (lucid, 50) to amber (mastery, 80), with the dream signs below it as diamonds (filled once noticed)
 * and the controls that are open to you right now. During a reality check every word on it scrambles.
 */
public final class LucidityHud {
    private LucidityHud() {}

    private static final int ROSE = 0xD088C2, LILAC = 0xA2569F, SIGNAL = 0x7FC3FF, AMBER = 0xFFD28A, CRIMSON = 0xFF5A6A;
    private static final int PLUM = 0x140F22, PALE = 0xE9DDF5, MUTED = 0xA898C0;
    private static final Random NOISE = new Random();

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !ClientDream.dreaming()) return;
        LucidiumNet.DreamHud hud = ClientDream.hud;
        Font font = mc.font;
        float time = (mc.player.tickCount + dt.getGameTimeDeltaPartialTick(false)) / 20f;
        boolean nightmare = hud.has(LucidiumNet.DreamHud.NIGHTMARE);

        int h = 84, w = 6;
        int x = 14, y = g.guiHeight() / 2 - h / 2 - 10;
        float value = Mth.clamp(ClientDream.shown, 0, 100);

        // backing glass
        g.fillGradient(x - 8, y - 26, x + 48, y + h + 50, a(0x1E1430, 0.55f), a(PLUM, 0.35f));

        // crescent moon
        int mx = x + 2, my = y - 15;
        for (int dy = -5; dy <= 5; dy++) {
            int half = (int) Math.round(Math.sqrt(25 - dy * dy));
            for (int dx = -half; dx <= half; dx++) {
                boolean inner = (dx - 2) * (dx - 2) + (dy + 1) * (dy + 1) < 16;
                if (!inner) g.fill(mx + dx, my + dy, mx + dx + 1, my + dy + 1, a(colourFor(value), 0.95f));
            }
        }
        g.text(font, scramble(Integer.toString(Math.round(value))), x + 11, y - 19, a(PALE, 1f), true);

        // the column
        int edge = nightmare ? CRIMSON : LILAC;
        float pulse = nightmare ? 0.6f + 0.4f * (float) Math.sin(time * 6) : 0.8f;
        g.fill(x, y, x + w, y + h, a(PLUM, 0.85f));
        int fill = Math.round(h * value / 100f);
        if (fill > 0) g.fillGradient(x, y + h - fill, x + w, y + h, a(colourFor(value), 1f), a(ROSE, 0.85f));
        g.outline(x - 1, y - 1, w + 2, h + 2, a(edge, pulse));
        notch(g, x, y, w, h, Lucidity.LUCID, SIGNAL);
        notch(g, x, y, w, h, Lucidity.MASTERY, AMBER);

        // state word and time left
        String state = nightmare ? Component.translatable("hud.awaken.nightmare").getString()
                : value >= Lucidity.LUCID ? Component.translatable("hud.awaken.lucid").getString()
                : Component.translatable("hud.awaken.dreaming").getString();
        small(g, font, scramble(state.toUpperCase()), x - 2, y + h + 5, nightmare ? CRIMSON : value >= Lucidity.LUCID ? SIGNAL : ROSE);
        float left = hud.total() > 0 ? Mth.clamp(ClientDream.ticksLeft / (float) hud.total(), 0, 1) : 0;
        g.fill(x - 2, y + h + 14, x + 34, y + h + 15, a(MUTED, 0.3f));
        g.fill(x - 2, y + h + 14, x - 2 + Math.round(36 * left), y + h + 15, a(PALE, 0.8f));

        // dream signs: diamonds, filled once you have noticed them
        int sx = x - 1;
        int sy = y + h + 22;
        for (DreamSign s : DreamSign.values()) {
            if ((hud.signs() & s.bit()) == 0) continue;
            boolean seen = (hud.spotted() & s.bit()) != 0;
            float flash = seen && ClientDream.signFlash > 0 ? ClientDream.signFlash / 40f : 0f;
            diamond(g, sx + 3, sy + 3, seen, seen ? AMBER : MUTED, flash);
            sx += 10;
        }

        // what you can do right now
        int hy = y + h + 34;
        hy = hint(g, font, "hud.awaken.hint_check", x - 2, hy, MUTED);
        if (hud.has(LucidiumNet.DreamHud.FLY)) hy = hint(g, font, "hud.awaken.hint_fly", x - 2, hy, SIGNAL);
        if (hud.has(LucidiumNet.DreamHud.SHAPE)) hy = hint(g, font, "hud.awaken.hint_shape", x - 2, hy, SIGNAL);
        if (hud.has(LucidiumNet.DreamHud.SUMMON)) hint(g, font, "hud.awaken.hint_summon", x - 2, hy, AMBER);

        // holding H: the shaping charge under the crosshair
        if (ClientDream.hold > 0 && (hud.has(LucidiumNet.DreamHud.SHAPE) || hud.has(LucidiumNet.DreamHud.SUMMON))) {
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2 + 10;
            float f = Mth.clamp(ClientDream.hold / (float) LucidiumClient.HOLD_TICKS, 0, 1);
            g.fill(cx - 12, cy, cx + 12, cy + 2, a(PLUM, 0.7f));
            g.fill(cx - 12, cy, cx - 12 + Math.round(24 * f), cy + 2, a(f >= 1 ? AMBER : SIGNAL, 0.95f));
        }
    }

    private static int colourFor(float value) {
        if (value >= Lucidity.MASTERY) return AMBER;
        if (value >= Lucidity.LUCID) return SIGNAL;
        return ROSE;
    }

    private static void notch(GuiGraphicsExtractor g, int x, int y, int w, int h, float at, int colour) {
        int ny = y + h - Math.round(h * at / 100f);
        g.fill(x - 3, ny, x, ny + 1, a(colour, 0.9f));
        g.fill(x + w, ny, x + w + 3, ny + 1, a(colour, 0.9f));
    }

    private static void diamond(GuiGraphicsExtractor g, int cx, int cy, boolean filled, int colour, float flash) {
        for (int dy = -3; dy <= 3; dy++) {
            int half = 3 - Math.abs(dy);
            if (filled) g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, a(colour, 0.9f));
            else {
                g.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, a(colour, 0.8f));
                g.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, a(colour, 0.8f));
            }
        }
        if (flash > 0) g.fill(cx - 5, cy - 5, cx + 6, cy + 6, a(0xFFFFFF, 0.35f * flash));
    }

    private static int hint(GuiGraphicsExtractor g, Font font, String key, int x, int y, int colour) {
        Component keyName = dev.awaken.core.client.AwakenKeys.REALITY_CHECK.getTranslatedKeyMessage();
        small(g, font, scramble(Component.translatable(key, keyName).getString()), x, y, colour);
        return y + 8;
    }

    private static void small(GuiGraphicsExtractor g, Font font, String s, int x, int y, int colour) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, s, 0, 0, a(colour, 1f), false);
        g.pose().popMatrix();
    }

    /** During a reality check, text won't hold still: that's how you know. */
    static String scramble(String s) {
        if (ClientDream.realityCheck <= 0) return s;
        char[] c = s.toCharArray();
        String glyphs = "ABCDEFGHJKLMNOPQRSTUVWXYZ0123456789#?%&@";
        for (int i = 0; i < c.length; i++) {
            if (c[i] != ' ' && NOISE.nextInt(3) != 0) c[i] = glyphs.charAt(NOISE.nextInt(glyphs.length()));
        }
        return new String(c);
    }

    static int a(int rgb, float alpha) {
        int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
}
