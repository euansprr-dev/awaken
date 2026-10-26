package dev.awaken.signal.client;

import dev.awaken.core.audio.AudioSounds;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.List;

/**
 * A discovery moment (SPEC §26): the first of its kind, a wonder, the first rift. One banner in the upper middle of
 * the screen, clear of the crosshair and the card slot: a small spaced label, a line of light that opens from the
 * centre, the name large, and one or two lines under it (the first find's lesson). Its own swell of a sound. It holds
 * for about five seconds (longer for more words) and fades; nothing else of the director's shows meanwhile.
 */
final class Moment {
    private Moment() {}

    static final int OPEN = 12, FADE = 16, BASE_HOLD = 90;
    private static final float TITLE_SCALE = 1.8f;

    private record Shown(String label, String title, List<String> lines, int colour, int hold) {}

    private static Shown shown;
    private static int age;
    private static int lastWidth = -1;
    private static String rawBody = "";

    /** Starts a moment now (the Herald decided it may). */
    static void show(String label, String title, String body, int colour) {
        rawBody = body == null ? "" : body;
        int words = rawBody.isBlank() ? 0 : rawBody.split("\\s+").length;
        shown = new Shown(label.toUpperCase(java.util.Locale.ROOT), title, List.of(), colour, BASE_HOLD + Math.min(120, words * 6));
        lastWidth = -1;
        age = 0;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(AudioSounds.UI_MOMENT.get(), 1.0F, 0.55F));
    }

    /** Whether a moment is on screen (the director waits). */
    static boolean active() {
        return shown != null;
    }

    static void tick() {
        if (shown == null) return;
        if (++age > OPEN + shown.hold + FADE) shown = null;
    }

    static void clear() {
        shown = null;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Shown s = shown;
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int maxW = Math.min(300, sw - 40);
        if (lastWidth != maxW) {
            lastWidth = maxW;
            s = shown = new Shown(s.label, s.title, SignalText.wrapAtMost(rawBody, maxW, 2, font::width), s.colour, s.hold);
        }
        float t = age + dt.getGameTimeDeltaPartialTick(false);
        float open = Math.min(1f, t / OPEN);
        float e = 1f - (1f - open) * (1f - open) * (1f - open);
        float a = t <= OPEN + s.hold ? e : Math.max(0f, 1f - (t - OPEN - s.hold) / FADE);
        if (a <= 0.01f) return;
        int cx = sw / 2;
        // the upper middle: above the crosshair by a clear margin, below the boss bars
        int top = Math.max(26, (int) (sh * 0.20));
        // the label, spaced out
        String label = spaced(s.label);
        HudLook.centered(g, font, label, cx, top, Glass.a(s.colour, 0.9f * a), true);
        // a line of light opening from the centre
        int half = Math.round(Math.min(maxW / 2f, 90) * e);
        int ly = top + 12;
        g.fill(cx - half, ly, cx + half, ly + 1, Glass.a(s.colour, 0.75f * a));
        g.fill(cx - half / 3, ly, cx + half / 3, ly + 1, Glass.a(0xFFFFFF, 0.6f * a));
        // the name, large, rising a little as it arrives
        String title = SignalText.ellipsize(s.title, (int) (maxW / TITLE_SCALE), font::width);
        float rise = (1f - e) * 4f;
        g.pose().pushMatrix();
        g.pose().translate(cx, ly + 6 + rise);
        g.pose().scale(TITLE_SCALE, TITLE_SCALE);
        HudLook.text(g, font, title, -font.width(title) / 2, 0, Glass.a(Glass.TITLE, a), true);
        g.pose().popMatrix();
        int by = ly + 6 + Math.round(9 * TITLE_SCALE) + 5;
        float la = Math.max(0f, Math.min(1f, (t - OPEN * 0.6f) / OPEN)) * a; // the words follow the name
        for (String line : s.lines) {
            HudLook.centered(g, font, line, cx, by, Glass.a(Glass.TEXT, 0.92f * la), true);
            by += 10;
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
