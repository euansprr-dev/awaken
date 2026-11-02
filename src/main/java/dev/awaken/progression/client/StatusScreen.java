package dev.awaken.progression.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.ui.Rect;
import dev.awaken.signal.ui.SignalText;
import dev.awaken.signal.ui.StatusLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The band, flat (K with {@code statusStyle = FLAT}, or when the holo shaders fail): the same six pages as the holo band
 * (SPEC §30: Status, Powers, Journal, Truths, Codex, Map), each a scrolling stack of {@link StatusSections.Section}s
 * contributed by modules. K always opens on Status unless a link names a page; the Map page (and M) opens the Atlas
 * table. On narrow screens (auto GUI scale on a laptop) the tabs move to a row on top so the content gets the width;
 * every section is clipped to the content area and gets the mouse only while it is over that area.
 */
public final class StatusScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final int[] SCROLL = new int[StatusSections.Tab.values().length];
    private static final int SECTION_GAP = 12, SCROLLBAR = StatusLayout.SCROLLBAR;

    private StatusSections.Tab tab = StatusSections.Tab.STATUS;
    private int px, py, pw, ph, cx, cy, cw, ch;
    private boolean topTabs;
    private StatusLayout.Frame frame;
    private int contentHeight;

    public StatusScreen() {
        super(Component.translatable("screen.awaken.status"));
    }

    /** Opens on a given page (a link, screenshots). */
    public StatusScreen(StatusSections.Tab open) {
        this();
        tab = open;
    }

    /** The first of these section titles that shows, scrolled to on the first frame (a link: the ward's "Show me"). */
    private String @org.jspecify.annotations.Nullable [] focus;

    /** Opens on a tab scrolled to the first of {@code titles} that shows there ("THE ASCENT", else "THE BODY"). */
    public StatusScreen(StatusSections.Tab open, String... titles) {
        this(open);
        focus = titles;
    }

    @Override
    protected void init() {
        List<String> labels = new java.util.ArrayList<>();
        for (StatusSections.Tab t : StatusSections.Tab.values()) labels.add(label(t));
        frame = StatusLayout.of(width, height, labels, closeHint(), font::width);
        Rect p = frame.panel(), c = frame.content();
        px = p.x();
        py = p.y();
        pw = p.w();
        ph = p.h();
        topTabs = frame.topTabs();
        cx = c.x();
        cy = c.y();
        cw = c.w();
        ch = c.h();
    }

    private static String closeHint() {
        return AwakenKeys.STATUS.getTranslatedKeyMessage().getString() + " to close";
    }

    private static String label(StatusSections.Tab t) {
        return t.label();
    }

    /** Where tab {@code i}'s button is. */
    private Rect tabRect(int i) {
        return frame.tabs().get(i);
    }

    private boolean overContent(double mx, double my) {
        return mx >= cx && my >= cy && mx < cx + cw && my < cy + ch;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xB0050810, 0xD0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Glass.panel(g, px, py, pw, ph, Glass.EDGE, 1f);

        // tabs
        StatusSections.Tab[] tabs = StatusSections.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            StatusSections.Tab t = tabs[i];
            Rect r = tabRect(i);
            boolean on = t == tab;
            boolean hover = r.contains(mouseX, mouseY);
            if (on) g.fill(r.x(), r.y(), r.right(), r.bottom(), Glass.a(Glass.EDGE, 0.16f));
            else if (hover) g.fill(r.x(), r.y(), r.right(), r.bottom(), Glass.a(Glass.EDGE, 0.07f));
            if (on && topTabs) g.horizontalLine(r.x(), r.right() - 1, r.bottom() - 1, Glass.a(Glass.EDGE, 1f));
            if (on && !topTabs) g.verticalLine(r.x(), r.y(), r.bottom() - 1, Glass.a(Glass.EDGE, 1f));
            g.text(font, label(t), r.x() + 8, r.y() + (r.h() - 8) / 2, Glass.a(on ? Glass.TITLE : Glass.LABEL, 1f), false);
        }
        Rect close = frame.close();
        if (topTabs) {
            if (close != null) g.text(font, closeHint(), close.x(), close.y(), Glass.a(Glass.LABEL, 0.7f), false);
            g.horizontalLine(px + 8, px + pw - 8, py + 23, Glass.a(Glass.EDGE, 0.18f));
        } else {
            g.text(font, "◇ SIGNAL", px + 12, py + 12, Glass.a(Glass.EDGE, 1f), false);
            g.verticalLine(px + 98, py + 8, py + ph - 8, Glass.a(Glass.EDGE, 0.18f));
            g.text(font, SignalText.ellipsize(closeHint(), close.w(), font::width), close.x(), close.y(), Glass.a(Glass.LABEL, 0.7f), false);
        }

        // content
        StatusSections.Tab asked = StatusSections.takeRequest();
        if (asked != null) select(asked);
        StatusSections.drawingAt(0);
        boolean inside = overContent(mouseX, mouseY);
        int mx = inside ? mouseX : -10000, my = inside ? mouseY : -10000;
        g.enableScissor(cx, cy, cx + cw + SCROLLBAR, cy + ch);
        int y = cy - SCROLL[tab.ordinal()];
        boolean any = false;
        int[] focusAt = focus == null ? null : new int[focus.length];
        if (focusAt != null) java.util.Arrays.fill(focusAt, -1);
        for (StatusSections.Section s : StatusSections.of(tab)) {
            if (!StatusSections.shows(tab, s)) continue;
            any = true;
            if (focusAt != null) for (int i = 0; i < focus.length; i++) if (focusAt[i] < 0 && focus[i].equals(s.title())) focusAt[i] = y + SCROLL[tab.ordinal()] - cy;
            y += BandHeader.draw(g, font, s, cx, y, cw);
            int h = s.height(cw);
            if (y + h >= cy && y <= cy + ch) s.render(g, cx, y, cw, mx, my, a);
            y += h + SECTION_GAP;
        }
        if (!any) {
            String empty = switch (tab) {
                case TASKS -> "Nothing to do this minute. Look around: what you find brings the next step.";
                case JOURNAL -> "Nothing yet. What you find, and what the System tells you, will be kept here.";
                case POWERS -> "No powers yet. Your first one comes soon after you awaken.";
                default -> "";
            };
            Ui ui = Ui.draw(g, font, cx, y, cw, mx, my);
            ui.text(empty, Glass.a(Glass.LABEL, 1f));
            y += ui.height();
        }
        contentHeight = y + SCROLL[tab.ordinal()] - cy;
        if (focusAt != null) {
            // the link lands on its section: the first named that shows, else where the tab was
            for (int at : focusAt) {
                if (at < 0) continue;
                SCROLL[tab.ordinal()] = Math.max(0, at - 4);
                break;
            }
            focus = null;
        }
        g.disableScissor();
        // content can shrink (a decision answered, a screen resize): never stay scrolled past the end
        SCROLL[tab.ordinal()] = Math.min(SCROLL[tab.ordinal()], Math.max(0, contentHeight - ch));

        // scrollbar
        if (contentHeight > ch) {
            int barH = Math.max(20, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * SCROLL[tab.ordinal()] / Math.max(1, contentHeight - ch);
            g.fill(cx + cw + 3, barY, cx + cw + 5, barY + barH, Glass.a(Glass.EDGE, 0.5f));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        StatusSections.Tab[] tabs = StatusSections.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (tabRect(i).contains(mx, my)) {
                select(tabs[i]);
                return true;
            }
        }
        if (event.button() == 1 && overContent(mx, my)) { // left button (26.3 buttons are 1-based)
            int y = cy - SCROLL[tab.ordinal()];
            List<StatusSections.Section> sections = StatusSections.of(tab);
            for (StatusSections.Section s : sections) {
                if (!StatusSections.shows(tab, s)) continue;
                if (s.notePage() != null && BandHeader.onNote(font, s, cx, y, cw, mx, my)) {
                    select(StatusSections.follow(s));
                    return true;
                }
                y += BandHeader.height(s);
                int h = s.height(cw);
                if (my >= y && my < y + h && s.click(mx, my, cx, y, cw)) return true;
                y += h + SECTION_GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - ch);
        int i = tab.ordinal();
        SCROLL[i] = (int) Math.max(0, Math.min(max, SCROLL[i] - scrollY * 18));
        return true;
    }

    /** Shows a page; one that opens something of its own (the Map: the Atlas) closes the band and opens it. */
    private void select(StatusSections.Tab t) {
        StatusSections.PageAction action = StatusSections.action(t);
        if (action != null) {
            minecraft.gui.setScreen(null);
            action.open().run();
            return;
        }
        if (t != tab) StatusSections.narrate(t.label() + " page");
        tab = t;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.STATUS.matches(event)) {
            onClose();
            return true;
        }
        for (StatusSections.Section s : StatusSections.of(tab)) if (StatusSections.shows(tab, s) && s.key(event)) return true;
        StatusSections.Tab keyed = StatusSections.pageForKey(event);
        if (keyed != null) {
            select(keyed);
            return true;
        }
        StatusSections.Tab[] tabs = StatusSections.Tab.values();
        if (event.key() >= com.mojang.blaze3d.platform.InputConstants.KEY_1 && event.key() < com.mojang.blaze3d.platform.InputConstants.KEY_1 + tabs.length) {
            select(tabs[event.key() - com.mojang.blaze3d.platform.InputConstants.KEY_1]);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
