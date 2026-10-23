package dev.awaken.signal.client;

import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * A System toast, managed by vanilla's ToastManager so it stacks with advancement and recipe toasts: 160×32, top
 * right, a title and one detail line, on screen for 5 s × the player's notification-time option.
 */
final class SignalToast implements Toast {
    static final long BASE_MS = 5000L;

    private String title;
    private String detail;
    private int colour;
    private long shownFrom;
    private boolean reset;
    private Visibility wanted = Visibility.SHOW;
    private final Runnable onDone;

    SignalToast(String title, String detail, int colour, Runnable onDone) {
        this.title = title;
        this.detail = detail;
        this.colour = colour;
        this.onDone = onDone;
    }

    /** Replaces the text and restarts the timer (a merged toast that grew). */
    void update(String title, String detail, int colour) {
        this.title = title;
        this.detail = detail;
        this.colour = colour;
        this.reset = true;
    }

    @Override
    public Visibility getWantedVisibility() {
        return wanted;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (reset) {
            shownFrom = fullyVisibleForMs;
            reset = false;
        }
        double ms = BASE_MS * manager.getNotificationDisplayTimeMultiplier();
        wanted = fullyVisibleForMs - shownFrom < ms ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
        int w = width(), h = height();
        g.fillGradient(0, 0, w, h, Glass.a(Glass.GLASS_TOP, 0.94f), Glass.a(Glass.GLASS, 0.94f));
        g.outline(0, 0, w, h, Glass.a(colour, 0.45f));
        g.fill(0, 0, 2, h, Glass.a(colour, 1f));
        int textW = w - 16;
        String t = SignalText.ellipsize(title, textW, font::width);
        if (detail == null || detail.isBlank()) {
            g.text(font, t, 9, (h - 8) / 2, Glass.a(Glass.TITLE, 1f), false);
        } else {
            g.text(font, t, 9, 7, Glass.a(Glass.TITLE, 1f), false);
            g.text(font, SignalText.ellipsize(detail, textW, font::width), 9, 18, Glass.a(Glass.TEXT, 0.85f), false);
        }
    }

    @Override
    public int width() {
        return SignalLayout.TOAST_W;
    }

    @Override
    public int height() {
        return SignalLayout.TOAST_H;
    }

    @Override
    public void onFinishedRendering() {
        onDone.run();
    }
}
