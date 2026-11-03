package dev.awaken.lucidium.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.lucidium.net.LucidiumNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * A trial dream's own lines (SPEC §9.3), drawn by the dream, never the notice queue. Arriving: one line in the middle
 * of the screen ("You're dreaming. Press H to take control."), the goal faint at the top. In control: the goal line
 * at the top centre, the small line under it, and under that a thin crescent that empties: the clock, rose in its last
 * ten seconds. Lines the dream flashes (a sign's seconds, a catch) sit under the crosshair; the prize in the middle.
 */
final class TrialHud {
    private TrialHud() {}

    static final int PALE = 0xF2ECFA, LILAC = 0xC6A8E6, PEARL = 0xE8E4F0, GOLD = 0xF3D58A, ROSE = 0xF08AA8, PLUM = 0x140F22;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !ClientTrial.active()) return;
        LucidiumNet.TrialStart s = ClientTrial.start;
        LucidiumNet.TrialHud h = ClientTrial.hud;
        Font font = mc.font;
        int w = g.guiWidth(), cx = w / 2, cy = g.guiHeight() / 2;
        float partial = dt.getGameTimeDeltaPartialTick(false);
        float time = (mc.player.tickCount + partial) / 20f;
        float in = Mth.clamp((ClientTrial.phaseTicks + partial) / 12f, 0f, 1f);

        if (h.phase() == 0) {
            // the goal, faint, from the moment you arrive; the one thing to do, in the middle
            centred(g, font, s.goal(), cx, 12, PALE, 0.55f * Mth.clamp(ClientTrial.arrived / 20f, 0f, 1f), 1f);
            String key = AwakenKeys.REALITY_CHECK.getTranslatedKeyMessage().getString();
            String line = s.dreams() >= 3 ? "Press " + key + "." : "You're dreaming. Press " + key + " to take control.";
            float breathe = 0.82f + 0.18f * Mth.sin(time * 2.2f);
            float a = Mth.clamp(ClientTrial.arrived / 16f, 0f, 1f) * breathe;
            g.fillGradient(cx - font.width(line) / 2 - 14, cy + 14, cx + font.width(line) / 2 + 14, cy + 32,
                    LucidityHud.a(PLUM, 0.0f), LucidityHud.a(PLUM, 0.45f * a));
            centred(g, font, line, cx, cy + 19, PALE, a, 1f);
        } else if (h.phase() >= 1) {
            centred(g, font, s.goal(), cx, 12, PALE, in, 1f);
            int y = 24;
            if (!s.sub().isEmpty()) {
                centred(g, font, s.sub(), cx, y, LILAC, in * 0.9f, 0.75f);
                y += 9;
            }
            crescent(g, font, cx, y + 4, ClientTrial.left, h.total(), time, in, h.phase() == 2);
        }

        // a line the dream flashes: under the crosshair, or the prize in the middle
        if (ClientTrial.flashTicks > 0 && !ClientTrial.flash.isEmpty()) {
            int total = h.phase() == 2 ? 200 : ClientTrial.FLASH;
            int age = total - ClientTrial.flashTicks;
            float a = Mth.clamp(age / 6f, 0f, 1f) * Mth.clamp(ClientTrial.flashTicks / 14f, 0f, 1f);
            if (h.phase() == 2) centred(g, font, ClientTrial.flash, cx, cy - 34, GOLD, a, 1.5f);
            else centred(g, font, ClientTrial.flash, cx, cy + 22, PEARL, a, 1f);
        }
    }

    /**
     * The clock: a thin crescent, its horns up, that empties from its right horn; the seconds beside it. In the last ten
     * seconds it turns rose and pulses.
     */
    static void crescent(GuiGraphicsExtractor g, Font font, int cx, int top, int left, int total, float time, float alpha, boolean stopped) {
        float f = total <= 0 ? 0f : Mth.clamp(left / (float) total, 0f, 1f);
        boolean last = left <= 200 && !stopped;
        float pulse = last ? 0.7f + 0.3f * Mth.sin(time * 9f) : 1f;
        int full = last ? ROSE : stopped ? GOLD : PEARL;
        int half = 46, sag = 7;
        for (int dx = -half; dx <= half; dx++) {
            double t = dx / (double) half;
            int y = top + (int) Math.round(sag * (1 - t * t));
            // thickest in the middle, a hair at the horns
            int thick = 1 + (int) Math.round(1.6 * (1 - Math.abs(t)));
            boolean lit = (dx + half) / (2.0 * half) <= f;
            int colour = lit ? full : 0x6E6286;
            float a = alpha * (lit ? 0.95f * pulse : 0.35f);
            g.fill(cx + dx, y, cx + dx + 1, y + thick, LucidityHud.a(colour, a));
        }
        int secs = (left + 19) / 20;
        String text = secs / 60 + ":" + String.format("%02d", secs % 60);
        g.pose().pushMatrix();
        g.pose().translate(cx + half + 6, top + 1);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, text, 0, 0, LucidityHud.a(last ? ROSE : LILAC, alpha * 0.9f), true);
        g.pose().popMatrix();
    }

    private static void centred(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int colour, float alpha, float scale) {
        if (alpha <= 0.02f || s.isEmpty()) return;
        String shown = LucidityHud.scramble(s);
        if (scale == 1f) {
            g.text(font, shown, cx - font.width(shown) / 2, y, LucidityHud.a(colour, alpha), true);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        g.text(font, shown, -font.width(shown) / 2, 0, LucidityHud.a(colour, alpha), true);
        g.pose().popMatrix();
    }
}
