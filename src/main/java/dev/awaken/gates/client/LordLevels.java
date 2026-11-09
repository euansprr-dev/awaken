package dev.awaken.gates.client;

import dev.awaken.core.api.Notices;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The Lord's payout on screen (SPEC §50.4): one number in the middle of your view, climbing a level with every bell. Each
 * beat the old number rises away, the new one lands a little too big and settles, and a rule flares out from it.
 */
final class LordLevels {
    private LordLevels() {}

    private static final long HOLD_MS = 2600, OUT_MS = 700, IN_MS = 180, BEAT_MS = 460;
    private static final int GOLD = 0xE8C98A, PALE = 0xFFF4DC;
    private static final Object HOLD = new Object();

    private static int level, was, beats, points;
    private static long from = -1, beatAt, lastAt = -1;

    static void beat(GatePayloads.LordLevel m) {
        long now = Util.getMillis();
        if (from < 0) {
            from = now;
            Notices.client().hold(HOLD, true); // nothing else talks over it
            was = m.level() - 1;
        } else was = level;
        level = m.level();
        beats = m.beats();
        points = m.points();
        beatAt = now;
        lastAt = m.last() ? now : -1;
    }

    static void reset() {
        if (from >= 0) Notices.client().hold(HOLD, false);
        from = lastAt = -1;
    }

    static void tick() {
        if (from >= 0 && Util.getMillis() - beatAt > HOLD_MS + OUT_MS) reset(); // a lost last beat can't hold the screen
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (from < 0 || mc.gui.hud.isHidden()) return;
        long now = Util.getMillis();
        float in = Mth.clamp((now - from) / (float) IN_MS, 0, 1);
        float out = Mth.clamp((now - beatAt - HOLD_MS) / (float) OUT_MS, 0, 1);
        float a = in * (1 - out * out);
        if (a <= 0) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = (int) (h * 0.33F);
        float k = Mth.clamp((now - beatAt) / (float) BEAT_MS, 0, 1);
        float ease = 1 - (1 - k) * (1 - k) * (1 - k);
        float scale = Mth.clamp(w / 110F, 3.5F, 6F);

        // the room dims a little behind the number, so it reads over a bright arena
        g.fillGradient(0, (int) (cy - 46 * scale / 4), w, cy, 0, argb(0.34F * a, 0x06050A));
        g.fillGradient(0, cy, w, (int) (cy + 58 * scale / 4), argb(0.34F * a, 0x06050A), 0);

        int half = (int) (w * (0.09F + 0.2F * ease));
        int ruleY = (int) (cy + 7 * scale + 6);
        g.fill(cx - half, ruleY, cx + half, ruleY + 1, argb(a * (0.9F - 0.55F * ease), GOLD));
        if (k < 1) HudLook.glow(g, cx - (int) (34 * scale / 4), cy - (int) (8 * scale), (int) (68 * scale / 4), (int) (16 * scale), GOLD, (1 - ease) * 0.9F * a);

        // the old number leaves upward while the new one lands
        if (k < 1 && was != level) number(g, font, was, cx, cy - 12 * ease * scale / 4, scale * 0.82F, (1 - ease) * a * 0.55F, GOLD);
        float land = 1 + 0.28F * (1 - Mth.clamp(k * 2.6F, 0, 1)) * (1 - Mth.clamp(k * 2.6F, 0, 1));
        number(g, font, level, cx, cy, scale * land, a, PALE);

        HudLook.centered(g, font, "L E V E L", cx, cy - (int) (5 * scale) - 11, argb(0.85F * a, GOLD), false);
        HudLook.centered(g, font, "+" + points + " stat points", cx, ruleY + 6, argb(0.9F * a, PALE), false);

        // one mark per level gained, the newest brightest
        int shown = Math.min(beats, 24), gap = 7, x0 = cx - (shown - 1) * gap / 2, my = ruleY + 20;
        for (int i = 0; i < shown; i++) {
            boolean newest = i == shown - 1;
            int c = argb(a * (newest ? 1 : 0.5F), newest ? PALE : GOLD), r = newest && k < 0.5F ? 2 : 1;
            g.fill(x0 + i * gap - r, my - r, x0 + i * gap + r + 1, my + r + 1, c);
        }
    }

    private static void number(GuiGraphicsExtractor g, Font font, int n, int cx, float y, float scale, float a, int rgb) {
        String s = Integer.toString(n);
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        HudLook.text(g, font, s, -font.width(s) / 2, -4, argb(a, rgb), false);
        g.pose().popMatrix();
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
