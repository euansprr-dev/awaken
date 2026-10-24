package dev.awaken.signal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/**
 * How the System's cards look: toasts, choice and gate cards, story lines, the Now line and the first-power choice
 * (SPEC §12). Flat {@link Glass} cards unless the holo module installs its projected-glass look (docs/modules/holo.md
 * "System cards"). The look never moves, resizes or retimes anything: positions, sizes and timing stay the callers',
 * so the System v2 rules (nothing over the crosshair, card heights, arming, folding, quiet screens) hold either way.
 * Every flat path makes exactly the calls it made before the hook existed.
 */
public final class HudLook {
    private HudLook() {}

    /** Card styles for the projected look (ignored flat). */
    public static final int FRAME = 1, DRIFT = 2, EMITTER = 4;
    /** A System notice: the K panel's frame (edge glow, corner brackets) and a slow interference band. */
    public static final int NOTICE = FRAME | DRIFT;
    /** A toast: a notice projected from its edge. */
    public static final int TOAST = NOTICE | EMITTER;

    /** A projected look for the System's cards (installed by the holo module). */
    public interface Look {
        /** Whether it draws right now (its style chosen and its shaders compiled). */
        boolean enabled();

        /**
         * A glass card. {@code reveal} 0..1 scans it in from the top (1: whole); {@code glitch} 0..1 takes it out
         * (0: whole). {@code alpha} is the caller's own fade. {@code style}: {@link #FRAME} the K panel's edge glow and
         * corner brackets, {@link #DRIFT} a slow interference band (still with reduced motion), {@link #EMITTER} a
         * projection edge on the left that flares as the card arrives.
         */
        void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float alpha, float reveal, float glitch, int style);

        /** A soft light around a rectangle (a hovered tile). */
        void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float strength);

        /** Text as light: no drop shadow, a faint halo. */
        void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int argb);

        /** How far content shifts sideways while a card glitches out (0 with reduced motion). */
        float shake(float glitch);
    }

    private static Look look;

    public static void install(Look l) {
        look = l;
    }

    /** Whether the projected look is drawing (callers keep their flat code for when it isn't). */
    public static boolean holo() {
        return look != null && look.enabled();
    }

    /** A System notice card ({@link #NOTICE}): the projected one, or {@link Glass#card} as before. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float alpha, float reveal, float glitch) {
        card(g, x, y, w, h, accent, alpha, reveal, glitch, NOTICE);
    }

    /** A card in a given style: the projected one, or {@link Glass#card} as before. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float alpha, float reveal, float glitch, int style) {
        if (holo()) look.card(g, x, y, w, h, accent, alpha, reveal, glitch, style);
        else Glass.card(g, x, y, w, h, accent, alpha);
    }

    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float strength) {
        if (holo()) look.glow(g, x, y, w, h, accent, strength);
    }

    /** Text: as light, or vanilla's with the shadow the caller asked for. */
    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int argb, boolean flatShadow) {
        if (s == null) return;
        if (holo()) look.text(g, font, net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.of(s)), x, y, argb);
        else g.text(font, s, x, y, argb, flatShadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int argb, boolean flatShadow) {
        if (holo()) look.text(g, font, s, x, y, argb);
        else g.text(font, s, x, y, argb, flatShadow);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int argb, boolean flatShadow) {
        text(g, font, s, cx - font.width(s) / 2, y, argb, flatShadow);
    }

    public static float shake(float glitch) {
        return holo() && glitch > 0 ? look.shake(glitch) : 0f;
    }
}
