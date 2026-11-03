package dev.awaken.signal.client;

import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * A System toast, managed by vanilla's ToastManager so it stacks with advancement and recipe toasts: 160 wide, top
 * right, a title and one detail line (32 tall), growing 11 a line so nothing is cut: the title takes up to two lines and
 * the detail up to four (Euan 2026-11-03: they were almost always cut). On screen for 5 s × the player's
 * notification-time option. With the projected look ({@link HudLook}) it keeps that footprint and slot but doesn't
 * slide: it scans in where it sits and glitches out, over vanilla's own 600 ms show and hide.
 */
final class SignalToast implements Toast {
    static final long BASE_MS = 5000L;

    static final int TITLE_LINES = 2, DETAIL_LINES = 4;

    /** Title and detail wrapped to the toast's width once, here: vanilla reserves a toast's slots when it's added. */
    private final java.util.List<String> titles, details, hints;
    private final int colour;
    private Visibility wanted = Visibility.SHOW;
    /** Vanilla's slide (0 hidden, 1 in place), which the projected look turns into its scan-in and glitch-out. */
    private float portion = 1f;
    private final Runnable onDone;

    SignalToast(String title, String detail, int colour, Runnable onDone) {
        this(title, detail, colour, "", onDone);
    }

    SignalToast(String title, String detail, int colour, String hint, Runnable onDone) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int textW = SignalLayout.TOAST_W - 16;
        this.titles = SignalText.wrapAtMost(title == null ? "" : title, textW, TITLE_LINES, font::width);
        this.details = detail == null || detail.isBlank() ? java.util.List.of() : SignalText.wrapAtMost(detail, textW, DETAIL_LINES, font::width);
        this.hints = hint.isBlank() ? java.util.List.of() : SignalText.wrap(hint, textW, font::width);
        this.colour = colour;
        this.onDone = onDone;
    }

    /** Vanilla's 32-tall slots this toast takes. */
    int slots() {
        return occcupiedSlotCount();
    }

    @Override
    public Visibility getWantedVisibility() {
        return wanted;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        double ms = BASE_MS * manager.getNotificationDisplayTimeMultiplier();
        boolean quiet = net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof QuietScreen;
        wanted = !quiet && fullyVisibleForMs < ms ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public float xPos(int screenWidth, float visiblePortion) {
        portion = visiblePortion;
        return HudLook.holo() ? screenWidth - width() : Toast.super.xPos(screenWidth, visiblePortion);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
        if (HudLook.holo()) {
            projected(g, font);
            return;
        }
        int w = width(), h = height();
        g.fillGradient(0, 0, w, h, Glass.a(Glass.GLASS_TOP, 0.94f), Glass.a(Glass.GLASS, 0.94f));
        g.outline(0, 0, w, h, Glass.a(colour, 0.45f));
        g.fill(0, 0, 2, h, Glass.a(colour, 1f));
        int y = top();
        for (String t : titles) { g.text(font, t, 9, y, Glass.a(Glass.TITLE, 1f), false); y += 11; }
        for (String d : details) { g.text(font, d, 9, y, Glass.a(Glass.TEXT, 0.85f), false); y += 11; }
        for (int i = 0; i < hints.size(); i++) g.text(font, hints.get(i), 9, h - 12 - (hints.size() - 1 - i) * 11, Glass.a(Glass.EDGE, 0.9f), false);
    }

    /** The first line's y: a lone title line sits in the middle of the first slot. */
    private int top() {
        return details.isEmpty() && titles.size() == 1 ? (SignalLayout.TOAST_H - 8) / 2 : 7;
    }

    private void projected(GuiGraphicsExtractor g, Font font) {
        int w = width(), h = height();
        boolean closing = wanted == Visibility.HIDE;
        float reveal = closing ? 1f : Math.min(1f, portion / 0.45f);
        float glitch = closing ? Math.min(1f, (1f - portion) / 0.5f) : 0f;
        HudLook.card(g, 0, 0, w, h, colour, 1f, reveal, glitch, HudLook.TOAST);
        if (glitch >= 1f) return;
        float a = 1f - glitch * glitch;
        float e = 1f - (1f - reveal) * (1f - reveal) * (1f - reveal); // the card's own ease
        g.pose().pushMatrix();
        g.pose().translate(HudLook.shake(glitch), 0);
        g.enableScissor(0, 0, w, (int) Math.ceil(h * e)); // text appears as the scan passes it
        int y = top();
        for (String t : titles) { HudLook.text(g, font, t, 9, y, Glass.a(Glass.TITLE, a), false); y += 11; }
        for (String d : details) { HudLook.text(g, font, d, 9, y, Glass.a(Glass.TEXT, 0.9f * a), false); y += 11; }
        for (int i = 0; i < hints.size(); i++) HudLook.text(g, font, hints.get(i), 9, h - 12 - (hints.size() - 1 - i) * 11, Glass.a(Glass.EDGE, a), false);
        g.disableScissor();
        g.pose().popMatrix();
    }

    /** Slots are 32 tall whatever this toast's height (vanilla's default multiplies by the height). */
    @Override
    public float yPos(int firstSlotIndex) {
        return firstSlotIndex * 32.0F;
    }

    @Override
    public int width() {
        return SignalLayout.TOAST_W;
    }

    @Override
    public int height() {
        int lines = titles.size() + details.size();
        return (lines <= 2 ? SignalLayout.TOAST_H : 11 * lines + 10) + hints.size() * 11;
    }

    @Override
    public void onFinishedRendering() {
        onDone.run();
    }
}
