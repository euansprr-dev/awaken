package dev.awaken.signal.client;

import dev.awaken.core.audio.AudioSounds;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * The soft cue of a filed find (SPEC §26): no card, just a small diamond that blooms beside the crosshair with the
 * find's name in a whisper under it, gone in two seconds, and a quiet two-note chime. Several filed together share
 * one cue ("3 finds"). The Journal (K) holds them.
 */
final class FindCue {
    private FindCue() {}

    /** Ticks: bloom, hold, fade. */
    static final int IN = 5, HOLD = 34, OUT = 16, LIFE = IN + HOLD + OUT;
    /** Chimes this close together play once. */
    static final int CHIME_GAP = 60;

    private static String label = "";
    private static int colour = Glass.EDGE;
    private static int age = LIFE;
    private static long ticks, lastChime = -10_000;

    /** A find was filed (or {@code count} of them at once). */
    static void cue(String name, int accent, int count) {
        label = count > 1 ? count + " finds · Journal" : name;
        colour = accent;
        age = 0;
        if (ticks - lastChime >= CHIME_GAP) {
            lastChime = ticks;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(AudioSounds.UI_FIND.get(), 1.0F, 0.32F));
        }
    }

    static void tick() {
        ticks++;
        if (age < LIFE) age++;
    }

    static void clear() {
        age = LIFE;
        label = "";
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (age >= LIFE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        float t = age + dt.getGameTimeDeltaPartialTick(false);
        float in = Math.min(1f, t / IN);
        float a = t < IN + HOLD ? in : Math.max(0f, 1f - (t - IN - HOLD) / OUT);
        if (a <= 0.01f) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        // the glyph: a diamond 12 px right of the crosshair's centre, blooming out of a point with a ring that widens
        int gx = cx + 13, gy = cy - 1;
        float bloom = 1f - (1f - in) * (1f - in);
        int r = Math.max(1, Math.round(3 * bloom));
        diamond(g, gx, gy, r, Glass.a(colour, a));
        diamond(g, gx, gy, Math.max(0, r - 2), Glass.a(0xFFFFFF, a));
        float ring = Math.min(1f, t / (IN + 8f));
        if (ring < 1f) {
            int rr = 3 + Math.round(6 * ring);
            outline(g, gx, gy, rr, Glass.a(colour, (1f - ring) * 0.7f * a));
        }
        String s = SignalText.ellipsize(label, 120, font::width);
        int tx = gx + 7, ty = gy - 4;
        float slide = (1f - bloom) * 4f;
        g.pose().pushMatrix();
        g.pose().translate(slide, 0);
        HudLook.text(g, font, s, tx, ty, Glass.a(Glass.TEXT, 0.85f * a), true);
        g.pose().popMatrix();
    }

    private static void diamond(GuiGraphicsExtractor g, int x, int y, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(x - w, y + dy, x + w + 1, y + dy + 1, argb);
        }
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(x - w, y + dy, x - w + 1, y + dy + 1, argb);
            if (w > 0) g.fill(x + w, y + dy, x + w + 1, y + dy + 1, argb);
        }
    }
}
