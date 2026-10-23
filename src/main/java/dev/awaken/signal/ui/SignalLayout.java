package dev.awaken.signal.ui;

/**
 * Where System v2 puts things, in GUI pixels. Pure maths (no client classes) so GameTests can check every GUI size.
 *
 * <pre>
 *  ┌ tracker (top left)                      toasts (top right, vanilla slots) ┐
 *  │                                                                          │
 *  │                           + crosshair (kept clear)                       │
 *  │                                              card slot: choice or panel  │
 *  │                story line (above the hotbar)                             │
 *  └──────────────────────────── hotbar ───────────────────── power bar ──────┘
 * </pre>
 * The card slot sits on the right, just below the middle, so its top edge is always below the crosshair box; every
 * card is at most {@link #MAX_HEIGHT} of the screen tall. Text that doesn't fit is shortened or moved to the Status
 * screen, never clipped.
 */
public final class SignalLayout {
    private SignalLayout() {}

    public static final int MARGIN = 4;
    public static final int LINE = 10;
    public static final int CROSSHAIR = 16;
    public static final int CROSSHAIR_GAP = 4;
    public static final double MAX_HEIGHT = 0.20;
    public static final double MIN_SCALE = 0.75, MAX_SCALE = 1.5;

    /** Vanilla toast geometry (Toast.DEFAULT_WIDTH / SLOT_HEIGHT; ToastManager has 5 slots). */
    public static final int TOAST_W = 160, TOAST_H = 32, TOAST_SLOTS = 5;
    /** The System never has more than this many of its own toasts on screen. */
    public static final int MAX_TOASTS = 3;

    public static final int CARD_W = 170;
    public static final int PANEL_W = 200;
    public static final int STORY_MAX_W = 300;
    public static final int STORY_PAD = 3;
    /** Story lines end above vanilla's action-bar line (H - 68) and the held item's name (H - 59). */
    public static final int STORY_BOTTOM = 72;
    public static final int TRACKER_MAX_W = 220;
    /** A card never takes so much width that a story line beside it would have less than this. */
    public static final int STORY_MIN_SPAN = 150;
    /** Inside the Descent the Descent HUD owns the top-left corner; the tracker moves below it. */
    public static final int TRACKER_LOWERED_Y = 58;

    public static double clampScale(double s) {
        if (Double.isNaN(s)) return 1.0;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }

    /** The 16×16 box around the crosshair that nothing may cover. */
    public static Rect crosshair(int w, int h) {
        return new Rect(w / 2 - CROSSHAIR / 2, h / 2 - CROSSHAIR / 2, CROSSHAIR, CROSSHAIR);
    }

    public static int maxHeight(int h) {
        return (int) Math.floor(h * MAX_HEIGHT);
    }

    /** Top of the card slot: just below the crosshair box. */
    public static int slotTop(int h) {
        return h / 2 + CROSSHAIR / 2 + CROSSHAIR_GAP;
    }

    // ---- toasts -------------------------------------------------------------------------------------------------

    /** A toast in vanilla slot {@code slot} (0 = top). Toasts are always vanilla-sized. */
    public static Rect toast(int w, int slot) {
        return new Rect(w - TOAST_W, slot * TOAST_H, TOAST_W, TOAST_H);
    }

    // ---- choice card ----------------------------------------------------------------------------------------------

    /** Unscaled height of a choice card: question, optional body line, then "[Y] …" and "[N] …". */
    public static int choiceHeight(int questionLines, boolean bodyLine) {
        return 4 + questionLines * LINE + (bodyLine ? LINE : 0) + 2 + 2 * LINE + 3;
    }

    public static final int CHOICE_MIN_H = choiceHeight(1, false);

    /** A laid-out card: its screen rect, the scale to draw at, and how many lines of each kind fit. */
    public record Card(Rect rect, double scale, int lines, boolean extra) {
        /** Width available for text inside the card, in unscaled px. */
        public int textWidth() {
            return (int) Math.floor(rect.w() / scale) - 12;
        }
    }

    /**
     * The choice card. {@code wantQuestionLines} (1–2) and {@code wantBody} say what the text needs; the card keeps the
     * question first and drops the body line when the screen is short.
     */
    public static Card choice(int w, int h, double scale, int wantQuestionLines, boolean wantBody) {
        int maxH = maxHeight(h);
        double s = Math.min(clampScale(scale), maxH / (double) CHOICE_MIN_H);
        s = Math.min(s, (w - 2.0 * MARGIN - STORY_MIN_SPAN) / CARD_W);
        int budget = (int) Math.floor(maxH / s + 1e-6);
        int q = Math.max(1, Math.min(2, wantQuestionLines));
        boolean b = wantBody;
        while (choiceHeight(q, b) > budget) {
            if (b) b = false;
            else if (q > 1) q--;
            else break;
        }
        int cw = (int) Math.round(CARD_W * s);
        int ch = Math.min(maxH, (int) Math.ceil(choiceHeight(q, b) * s));
        return new Card(inSlot(w, h, cw, ch), s, q, b);
    }

    // ---- panel (gate card) ----------------------------------------------------------------------------------------

    /** Unscaled height of a panel: title, content lines (body, rows, "+N more"), optional footer. */
    public static int panelHeight(int contentLines, boolean footer) {
        return 4 + LINE + 1 + contentLines * LINE + (footer ? LINE : 0) + 3;
    }

    /**
     * The panel. {@code wantLines} = body lines + rows; the result's {@code lines} is how many fit (the renderer uses the
     * last one for "+N more · K" when it had to cut). The footer goes first when space is short.
     */
    public static Card panel(int w, int h, double scale, int wantLines, boolean wantFooter) {
        int maxH = maxHeight(h);
        double s = Math.min(clampScale(scale), maxH / (double) panelHeight(1, false));
        s = Math.min(s, (w - 2.0 * MARGIN - STORY_MIN_SPAN) / PANEL_W);
        int budget = (int) Math.floor(maxH / s + 1e-6);
        int n = Math.max(0, wantLines);
        boolean f = wantFooter;
        while (panelHeight(n, f) > budget) {
            if (f) f = false;
            else if (n > 1) n--;
            else break;
        }
        int pw = (int) Math.round(PANEL_W * s);
        int ph = Math.min(maxH, (int) Math.ceil(panelHeight(n, f) * s));
        return new Card(inSlot(w, h, pw, ph), s, n, f);
    }

    private static Rect inSlot(int w, int h, int cw, int ch) {
        int x = w - MARGIN - cw;
        int y = slotTop(h);
        if (y + ch > h - MARGIN) y = Math.max(0, h - MARGIN - ch);
        return new Rect(Math.max(MARGIN, x), y, Math.min(cw, w - 2 * MARGIN), ch);
    }

    // ---- story line -----------------------------------------------------------------------------------------------

    public static int storyHeight(int lines) {
        return lines * LINE + 2 * STORY_PAD;
    }

    /**
     * The story subtitle, centred above the hotbar. When a card is up and they would touch, the line moves into the free
     * span left of the card instead of going under it.
     */
    public static Card story(int w, int h, double scale, int lines, Rect avoid) {
        double s = clampScale(scale);
        int rh = (int) Math.ceil(storyHeight(Math.max(1, lines)) * s);
        int top = h - STORY_BOTTOM - rh;
        int minTop = h / 2 + CROSSHAIR / 2 + CROSSHAIR_GAP;
        if (top < minTop) top = minTop;
        int rw = (int) Math.min(Math.round(STORY_MAX_W * s), w - 2 * MARGIN - 40);
        Rect r = new Rect((w - rw) / 2, top, rw, rh);
        if (avoid != null && r.intersects(avoid)) {
            int span = Math.max(40, avoid.x() - 2 * MARGIN);
            rw = Math.min(rw, span);
            r = new Rect(MARGIN + (span - rw) / 2, top, rw, rh);
        }
        return new Card(r, s, lines, false);
    }

    /** Width for story text in unscaled px. */
    public static int storyTextWidth(Card story) {
        return (int) Math.floor(story.rect().w() / story.scale()) - 2 * STORY_PAD - 4;
    }

    // ---- Now tracker ----------------------------------------------------------------------------------------------

    /** Widest text the tracker may draw, in unscaled px. */
    public static int trackerTextWidth(int w, double scale) {
        double s = clampScale(scale);
        return (int) Math.min(TRACKER_MAX_W, Math.floor(w * 0.45 / s)) - 8;
    }

    /** The tracker box: {@code textW} is the widest line (unscaled), {@code lines} 1–2. */
    public static Rect tracker(int w, int h, double scale, int textW, int lines, boolean lowered) {
        double s = clampScale(scale);
        int bw = Math.min(textW, trackerTextWidth(w, s)) + 8;
        int bh = Math.max(1, lines) * LINE + 4;
        return new Rect(MARGIN, lowered ? TRACKER_LOWERED_Y : MARGIN, (int) Math.round(bw * s), (int) Math.round(bh * s));
    }
}
