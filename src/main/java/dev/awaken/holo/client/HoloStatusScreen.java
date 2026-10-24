package dev.awaken.holo.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.QuietScreen;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.ui.Rect;
import dev.awaken.signal.ui.SignalText;
import dev.awaken.signal.ui.StatusLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The K menu on the wrist (docs/proposals/holo-ui.md §5): the same four tabs and sections as the flat
 * {@code StatusScreen}, laid out by the same {@link StatusLayout} inside a {@link HoloPanel} that unfolds from the
 * forearm band. Every section draws through {@link HoloGraphics}, the {@code Ui} sections and the ones written straight
 * against the vanilla graphics alike. The mouse is a ray into the panel ({@link HoloPanel#pick}); clicks land from
 * 250 ms on. Esc or K closes (K while it opens skips to open); Tab and 1–4 switch tabs. The world keeps running, and
 * toasts wait ({@link QuietScreen}).
 */
public final class HoloStatusScreen extends Screen implements QuietScreen {
    static final int W = 344, H = 214;
    private static StatusSections.Tab lastTab = StatusSections.Tab.STATUS;
    private static final int[] SCROLL = new int[StatusSections.Tab.values().length];
    private static final int SECTION_GAP = 12, SCROLLBAR = StatusLayout.SCROLLBAR;

    private final HoloPanel panel;
    private final HoloGraphics g = HoloGraphics.create();
    private StatusSections.Tab tab = lastTab;
    private StatusLayout.Frame frame;
    private Rect[] tabRects = new Rect[0];
    private int cx, cy, cw, ch;
    private int contentHeight;
    private final float[] hit = new float[2];
    private int hoverX = -10000, hoverY = -10000;

    public HoloStatusScreen() {
        this(lastTab);
    }

    public HoloStatusScreen(StatusSections.Tab open) {
        super(Component.translatable("screen.awaken.status"));
        tab = open;
        lastTab = open;
        panel = HoloClient.statusPanel();
    }

    @Override
    protected void init() {
        List<String> labels = new ArrayList<>();
        for (StatusSections.Tab t : StatusSections.Tab.values()) labels.add(label(t));
        // the flat layout for a panel exactly W×H (it insets by OUTER on every side)
        frame = StatusLayout.of(W + 2 * StatusLayout.OUTER, H + 2 * StatusLayout.OUTER, labels, closeHint(), font::width);
        tabRects = new Rect[frame.tabs().size()];
        for (int i = 0; i < tabRects.length; i++) {
            Rect r = frame.tabs().get(i);
            tabRects[i] = new Rect(r.x() - StatusLayout.OUTER, r.y() - StatusLayout.OUTER, r.w(), r.h());
        }
        Rect c = frame.content();
        cx = c.x() - StatusLayout.OUTER;
        cy = c.y() - StatusLayout.OUTER;
        cw = c.w();
        ch = c.h();
        HoloClient.openStatusPanel();
    }

    private static String closeHint() {
        return AwakenKeys.STATUS.getTranslatedKeyMessage().getString() + " to close";
    }

    private static String label(StatusSections.Tab t) {
        return switch (t) {
            case STATUS -> "Status";
            case TRUTHS -> "Truths";
            case CODEX -> "Codex";
            case GATE_MAP -> "Gate Map";
        };
    }

    private Rect tabRect(int i) {
        return tabRects[i];
    }

    private boolean overContent(double mx, double my) {
        return mx >= cx && my >= cy && mx < cx + cw && my < cy + ch;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // no blur or dim: the world stays in view behind the panel (Lens dims it through UiHooks.focus)
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor screen, int mouseX, int mouseY, float a) {
        super.extractRenderState(screen, mouseX, mouseY, a);
        boolean picked = panel.pick(mouseX, mouseY, hit);
        hoverX = picked ? Math.round(hit[0] - 0.5f) : -10000;
        hoverY = picked ? Math.round(hit[1] - 0.5f) : -10000;
        HoloCanvas canvas = panel.canvas;
        canvas.begin(W, H);
        canvas.tooltipsTo(screen, mouseX, mouseY);
        HoloGraphics gg = g.begin(canvas, W, H);
        draw(gg, hoverX, hoverY, a);
    }

    private void draw(HoloGraphics gg, int mouseX, int mouseY, float a) {
        StatusSections.Tab[] tabs = StatusSections.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            StatusSections.Tab t = tabs[i];
            Rect r = tabRect(i);
            boolean on = t == tab;
            boolean hover = r.contains(mouseX, mouseY);
            if (on) gg.fill(r.x(), r.y(), r.right(), r.bottom(), Glass.a(Glass.EDGE, 0.16f));
            else if (hover) gg.fill(r.x(), r.y(), r.right(), r.bottom(), Glass.a(Glass.EDGE, 0.07f));
            if (on && frame.topTabs()) gg.horizontalLine(r.x(), r.right() - 1, r.bottom() - 1, Glass.a(Glass.EDGE, 1f));
            if (on && !frame.topTabs()) gg.verticalLine(r.x(), r.y(), r.bottom() - 1, Glass.a(Glass.EDGE, 1f));
            gg.text(font, label(t), r.x() + 8, r.y() + (r.h() - 8) / 2, Glass.a(on ? Glass.TITLE : Glass.LABEL, 1f), false);
        }
        Rect close = frame.close();
        if (frame.topTabs()) {
            if (close != null) gg.text(font, closeHint(), close.x() - StatusLayout.OUTER, close.y() - StatusLayout.OUTER, Glass.a(Glass.LABEL, 0.7f), false);
            gg.horizontalLine(8, W - 8, 23, Glass.a(Glass.EDGE, 0.18f));
        } else {
            gg.text(font, "◇ SIGNAL", 12, 12, Glass.a(Glass.EDGE, 1f), false);
            gg.verticalLine(98, 8, H - 8, Glass.a(Glass.EDGE, 0.18f));
            gg.text(font, SignalText.ellipsize(closeHint(), close.w(), font::width), close.x() - StatusLayout.OUTER,
                    close.y() - StatusLayout.OUTER, Glass.a(Glass.LABEL, 0.7f), false);
        }

        boolean inside = overContent(mouseX, mouseY);
        int mx = inside ? mouseX : -10000, my = inside ? mouseY : -10000;
        gg.enableScissor(cx, cy, cx + cw + SCROLLBAR, cy + ch);
        int y = cy - SCROLL[tab.ordinal()];
        boolean any = false;
        for (StatusSections.Section s : StatusSections.of(tab)) {
            if (!s.visible()) continue;
            any = true;
            Ui title = Ui.draw((GuiGraphicsExtractor) gg, font, cx, y, cw, mx, my);
            title.text(s.title(), Glass.a(Glass.EDGE, 0.85f));
            y += title.height() + 2;
            int h = s.height(cw);
            if (y + h >= cy && y <= cy + ch) s.render(gg, cx, y, cw, mx, my, a);
            y += h + SECTION_GAP;
        }
        if (!any) {
            String empty = switch (tab) {
                case CODEX -> "Nothing yet. What you hear, see and dream will gather here.";
                case GATE_MAP -> "No gates detected.";
                default -> "";
            };
            Ui ui = Ui.draw((GuiGraphicsExtractor) gg, font, cx, y, cw, mx, my);
            ui.text(empty, Glass.a(Glass.LABEL, 1f));
            y += ui.height();
        }
        contentHeight = y + SCROLL[tab.ordinal()] - cy;
        gg.disableScissor();
        SCROLL[tab.ordinal()] = Math.min(SCROLL[tab.ordinal()], Math.max(0, contentHeight - ch));
        if (contentHeight > ch) {
            int barH = Math.max(20, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * SCROLL[tab.ordinal()] / Math.max(1, contentHeight - ch);
            gg.fill(cx + cw + 3, barY, cx + cw + 5, barY + barH, Glass.a(Glass.EDGE, 0.5f));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!panel.timeline.clickable()) return true;
        if (!panel.pick(event.x(), event.y(), hit)) return true;
        double mx = hit[0], my = hit[1];
        HoloClient.pressFlash(mx, my);
        StatusSections.Tab[] tabs = StatusSections.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (tabRect(i).contains(mx, my)) {
                selectTab(tabs[i]);
                return true;
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overContent(mx, my)) {
            int y = cy - SCROLL[tab.ordinal()];
            for (StatusSections.Section s : StatusSections.of(tab)) {
                if (!s.visible()) continue;
                Ui title = Ui.measure(font, cw);
                title.text(s.title(), 0);
                y += title.height() + 2;
                int h = s.height(cw);
                if (my >= y && my < y + h && s.click(mx, my, cx, y, cw)) return true;
                y += h + SECTION_GAP;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - ch);
        int i = tab.ordinal();
        SCROLL[i] = (int) Math.max(0, Math.min(max, SCROLL[i] - scrollY * 18));
        return true;
    }

    /** The open tab (the autopilot logs it after a click). */
    public StatusSections.Tab tab() {
        return tab;
    }

    /** Where tab {@code i}'s centre is on the screen, in GUI coordinates (for the autopilot's click). */
    public boolean tabCentreOnScreen(int i, float[] out) {
        if (i < 0 || i >= tabRects.length) return false;
        Rect r = tabRects[i];
        return panel.project(r.x() + r.w() / 2f, r.y() + r.h() / 2f, out);
    }

    private void selectTab(StatusSections.Tab t) {
        tab = t;
        lastTab = t;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.STATUS.matches(event)) {
            if (!panel.timeline.fullyOpen()) panel.timeline.skip();
            else onClose();
            return true;
        }
        StatusSections.Tab[] tabs = StatusSections.Tab.values();
        if (event.key() == InputConstants.KEY_TAB) {
            int n = (tab.ordinal() + (event.hasShiftDown() ? tabs.length - 1 : 1)) % tabs.length;
            selectTab(tabs[n]);
            return true;
        }
        if (event.key() >= InputConstants.KEY_1 && event.key() < InputConstants.KEY_1 + tabs.length) {
            selectTab(tabs[event.key() - InputConstants.KEY_1]);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        HoloClient.closeStatusPanel();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
