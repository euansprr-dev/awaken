package dev.awaken.signal.client;

import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * A System toast, managed by vanilla's ToastManager so it stacks with advancement and recipe toasts: 160 wide, top
 * right, a title and one detail line (32 tall), or two when the detail needs them (43 tall), on screen for 5 s × the
 * player's notification-time option. With the projected look ({@link HudLook}) it keeps that footprint and slot but
 * doesn't slide: it scans in where it sits and glitches out, over vanilla's own 600 ms show and hide.
 */
final class SignalToast implements Toast {
    static final long BASE_MS = 5000L;

    private String title;
    private String detail;
    private final java.util.List<String> hints;
    /** The detail wrapped to the toast's width: one or two lines, worked out when the text changes, not per frame. */
    private String line1 = "", line2 = "";
    /** Vanilla reserves a toast's slots once, when it's added: a short toast that later grows stays one line. */
    private final boolean mayWrap;
    private int colour;
    private long shownFrom;
    private boolean reset;
    private Visibility wanted = Visibility.SHOW;
    /** Vanilla's slide (0 hidden, 1 in place), which the projected look turns into its scan-in and glitch-out. */
    private float portion = 1f;
    private final Runnable onDone;

    SignalToast(String title, String detail, int colour, Runnable onDone) {
        this(title, detail, colour, "", onDone);
    }

    SignalToast(String title, String detail, int colour, String hint, Runnable onDone) {
        this.hints = hint.isBlank() ? java.util.List.of() : SignalText.wrap(hint, SignalLayout.TOAST_W - 16, net.minecraft.client.Minecraft.getInstance().font::width);
        this.title = title;
        this.detail = detail;
        this.colour = colour;
        this.onDone = onDone;
        layout(true);
        this.mayWrap = !line2.isEmpty();
    }

    /** Replaces the text and restarts the timer (a merged toast that grew). */
    void update(String title, String detail, int colour) {
        this.title = title;
        this.detail = detail;
        this.colour = colour;
        this.reset = true;
        layout(mayWrap);
    }

    private static final int TWO_LINE_H = 43;

    private void layout(boolean twoLines) {
        line1 = line2 = "";
        if (detail == null || detail.isBlank()) return;
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int textW = SignalLayout.TOAST_W - 16;
        java.util.List<String> lines = SignalText.wrap(detail, textW, font::width);
        if (lines.isEmpty()) return;
        if (!twoLines) {
            line1 = SignalText.ellipsize(detail, textW, font::width);
            return;
        }
        line1 = lines.get(0);
        if (lines.size() > 1) {
            String rest = String.join(" ", lines.subList(1, lines.size()));
            line2 = SignalText.ellipsize(rest, textW, font::width);
        }
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
        boolean quiet = net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof QuietScreen;
        wanted = !quiet && fullyVisibleForMs - shownFrom < ms ? Visibility.SHOW : Visibility.HIDE;
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
        int textW = w - 16;
        String t = SignalText.ellipsize(title, textW, font::width);
        if (line1.isEmpty()) {
            g.text(font, t, 9, (h - 8) / 2, Glass.a(Glass.TITLE, 1f), false);
        } else {
            g.text(font, t, 9, 7, Glass.a(Glass.TITLE, 1f), false);
            g.text(font, line1, 9, 18, Glass.a(Glass.TEXT, 0.85f), false);
            if (!line2.isEmpty()) g.text(font, line2, 9, 29, Glass.a(Glass.TEXT, 0.85f), false);
        }
        for (int i = 0; i < hints.size(); i++) g.text(font, hints.get(i), 9, h - 12 - (hints.size() - 1 - i) * 11, Glass.a(Glass.EDGE, 0.9f), false);
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
        int textW = w - 16;
        String t = SignalText.ellipsize(title, textW, font::width);
        if (line1.isEmpty()) {
            HudLook.text(g, font, t, 9, (h - 8) / 2, Glass.a(Glass.TITLE, a), false);
        } else {
            HudLook.text(g, font, t, 9, 7, Glass.a(Glass.TITLE, a), false);
            HudLook.text(g, font, line1, 9, 18, Glass.a(Glass.TEXT, 0.9f * a), false);
            if (!line2.isEmpty()) HudLook.text(g, font, line2, 9, 29, Glass.a(Glass.TEXT, 0.9f * a), false);
        }
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
        return (line2.isEmpty() ? SignalLayout.TOAST_H : TWO_LINE_H) + (hints.size() * 11);
    }

    @Override
    public void onFinishedRendering() {
        onDone.run();
    }
}
