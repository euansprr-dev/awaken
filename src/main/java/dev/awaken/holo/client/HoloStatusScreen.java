package dev.awaken.holo.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.holo.ui.BandLayout;
import dev.awaken.holo.ui.HoverTick;
import dev.awaken.progression.Progression;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.progression.client.StatusSections.Tab;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Journal;
import dev.awaken.signal.client.QuietScreen;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.ui.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The band on the wrist (docs/proposals/holo-ui.md §5; its pages, SPEC §30): a rail of six pages down the panel's wrist
 * edge (Status, Powers, Journal, Truths, Codex, Map) and the open page's sections beside it, inside a {@link HoloPanel}
 * that unfolds from the forearm band. Every section draws through {@link HoloGraphics}. The mouse is a ray into the
 * panel ({@link HoloPanel#pick}); clicks land from 250 ms on. K always opens on Status unless a link names a page. Esc
 * or K closes (K while it opens skips to open); 1–6 open a page, Tab and Shift+Tab step through them, and a page's own
 * key (M for the Map) opens it. The Map page is the Atlas table: picking it closes the band and opens the table. The
 * world keeps running, and toasts wait ({@link QuietScreen}).
 */
public final class HoloStatusScreen extends Screen implements QuietScreen {
    /** Panel units: 5 framebuffer pixels per unit at about half of a 2880- or 3024-wide screen (6 on a 4K one). */
    static final int W = BandLayout.W, H = BandLayout.H;
    private static final int[] SCROLL = new int[Tab.values().length];
    private static final int SECTION_GAP = 12;
    /** The light of the active page (the System's blue, brighter). */
    private static final int HOT = 0xC4E6FF;

    /** The pages' icons, 12×12, one panel unit a pixel. */
    private static final String[][] ICONS = {
            {".....##.....", "....####....", "....####....", "....####....", ".....##.....", "............", "...######...", "..########..", ".##########.", ".##########.", ".##########.", "............"},
            {".......###..", "......###...", ".....###....", "....###.....", "...#######..", ".......##...", "......##....", ".....##.....", "....##......", "...##.......", "..##........", "............"},
            {"............", ".####..####.", "#....##....#", "#.##.##.##.#", "#....##....#", "#.##.##.##.#", "#....##....#", "#.##.##.##.#", "#....##....#", ".####..####.", ".....##.....", "............"},
            {".....##.....", ".....##.....", "....####....", "....####....", "..########..", "############", "############", "..########..", "....####....", "....####....", ".....##.....", ".....##....."},
            {"#####..#####", "#...#..#...#", "#.#.#..#.#.#", "#...#..#...#", "#####..#####", "............", "#####..#####", "#...#..#...#", "#.#.#..#...#", "#...#..#...#", "#####..#####", "............"},
            {"....####....", "...######...", "..###..###..", "..##....##..", "..###..###..", "...######...", "....####....", ".....##.....", ".....##.....", "............", ".##########.", "............"},
    };

    private final HoloPanel panel;
    private final HoloGraphics g = HoloGraphics.create();
    private Tab tab;
    private String @Nullable [] focus;
    private final Rect content = BandLayout.content();
    private int contentHeight;
    private final float[] hit = new float[2];
    private int hoverX = -10000, hoverY = -10000;
    private final HoverTick hover = new HoverTick();
    private long lastNanos;

    public HoloStatusScreen() {
        this(Tab.STATUS);
    }

    /** Opens on a page, scrolled to the first of {@code titles} that shows there (a link: the ward's "Show me"). */
    public HoloStatusScreen(Tab open, String... titles) {
        super(Component.translatable("screen.awaken.status"));
        tab = open;
        focus = titles.length == 0 ? null : titles;
        panel = HoloClient.statusPanel();
    }

    @Override
    protected void init() {
        HoloClient.openStatusPanel();
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
        tickRail();
        HoloCanvas canvas = panel.canvas;
        canvas.begin(W, H);
        canvas.tooltipsTo(screen, mouseX, mouseY);
        HoloGraphics gg = g.begin(canvas, W, H);
        draw(gg, hoverX, hoverY, a);
    }

    /** A glass tick as the cursor comes onto a page's node (once the panel takes clicks). */
    private void tickRail() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : (now - lastNanos) / 1e6f;
        lastNanos = now;
        int target = HoverTick.NONE;
        for (int i = 0; i < BandLayout.PAGES; i++) if (BandLayout.node(i).contains(hoverX, hoverY)) target = i;
        if (hover.update(target, dt, !panel.timeline.clickable())) HoloAudio.hover();
    }

    private void draw(HoloGraphics gg, int mouseX, int mouseY, float a) {
        rail(gg, mouseX, mouseY);
        Rect c = content;
        int cx = c.x(), cy = c.y(), cw = c.w(), ch = c.h();
        boolean inside = c.contains(mouseX, mouseY);
        int mx = inside ? mouseX : -10000, my = inside ? mouseY : -10000;
        gg.enableScissor(cx, cy, cx + cw + BandLayout.SCROLLBAR, cy + ch);
        int i = tab.ordinal();
        int y = cy - SCROLL[i];
        boolean any = false;
        int[] focusAt = focus == null ? null : new int[focus.length];
        if (focusAt != null) java.util.Arrays.fill(focusAt, -1);
        for (StatusSections.Section s : StatusSections.of(tab)) {
            if (!s.visible()) continue;
            any = true;
            if (focusAt != null) for (int k = 0; k < focus.length; k++) if (focusAt[k] < 0 && focus[k].equals(s.title())) focusAt[k] = y + SCROLL[i] - cy;
            Ui title = Ui.draw((GuiGraphicsExtractor) gg, font, cx, y, cw, mx, my);
            title.text(s.title(), Glass.a(Glass.EDGE, 0.85f));
            y += title.height() + 2;
            int h = s.height(cw);
            if (y + h >= cy && y <= cy + ch) s.render(gg, cx, y, cw, mx, my, a);
            y += h + SECTION_GAP;
        }
        if (!any) {
            Ui ui = Ui.draw((GuiGraphicsExtractor) gg, font, cx, y, cw, mx, my);
            ui.text(empty(tab), Glass.a(Glass.LABEL, 1f));
            y += ui.height();
        }
        contentHeight = y + SCROLL[i] - cy;
        if (focusAt != null) {
            // the link lands on its section: the first named that shows, else where the page was
            for (int at : focusAt) {
                if (at < 0) continue;
                SCROLL[i] = Math.max(0, at - 4);
                break;
            }
            focus = null;
        }
        gg.disableScissor();
        SCROLL[i] = Math.min(SCROLL[i], Math.max(0, contentHeight - ch));
        if (contentHeight > ch) {
            int barH = Math.max(20, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * SCROLL[i] / Math.max(1, contentHeight - ch);
            gg.fill(cx + cw + 3, barY, cx + cw + 5, barY + barH, Glass.a(Glass.EDGE, 0.5f));
        }
    }

    /** What an empty page says. */
    static String empty(Tab t) {
        return switch (t) {
            case CODEX -> "Nothing yet. What you hear, see and dream will gather here.";
            case JOURNAL -> "Nothing yet. What you take on, and what the System tells you, will be kept here.";
            case POWERS -> "No powers yet. Your first one comes soon after you awaken.";
            default -> "";
        };
    }

    /** The rail: the six pages down the wrist edge, the open one lit like the band's emitter; the K key at its foot. */
    private void rail(HoloGraphics gg, int mouseX, int mouseY) {
        int r = BandLayout.RAIL;
        gg.fillGradient(0, 0, r, H, Glass.a(0x05080F, 0.30f), Glass.a(0x05080F, 0.30f));
        gg.fill(r, 6, r + 1, H - 6, Glass.a(Glass.EDGE, 0.30f));
        float micro = BandLayout.microScale(panel.ppu);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            Rect n = BandLayout.node(i);
            boolean on = t == tab, over = n.contains(mouseX, mouseY);
            if (on) {
                gg.fill(n.x(), n.y(), n.right(), n.bottom(), Glass.a(Glass.EDGE, 0.15f));
                gg.fill(0, n.y() + 1, 2, n.bottom() - 1, Glass.a(HOT, 0.95f));
            } else if (over) {
                gg.fill(n.x(), n.y(), n.right(), n.bottom(), Glass.a(Glass.EDGE, 0.07f));
            }
            Rect ic = BandLayout.icon(i);
            int col = on ? Glass.a(HOT, 1f) : Glass.a(Glass.LABEL, over ? 1f : 0.85f);
            String[] rows = ICONS[i];
            for (int y = 0; y < rows.length; y++)
                for (int x = 0; x < rows[y].length(); x++)
                    if (rows[y].charAt(x) == '#') gg.fill(ic.x() + x, ic.y() + y, ic.x() + x + 1, ic.y() + y + 1, col);
            String label = t.label().toUpperCase(java.util.Locale.ROOT);
            float lw = font.width(label) * micro;
            gg.pose().pushMatrix();
            gg.pose().translate(r / 2f - lw / 2f, n.y() + 20);
            gg.pose().scale(micro, micro);
            gg.text(font, label, 0, 0, on ? Glass.a(Glass.TITLE, 1f) : Glass.a(Glass.LABEL, 0.95f), false);
            gg.pose().popMatrix();
            badge(gg, t, n, micro);
        }
        Rect k = BandLayout.close();
        String key = AwakenKeys.STATUS.getTranslatedKeyMessage().getString();
        gg.fill(k.x(), k.y(), k.right(), k.bottom(), Glass.a(0x05080F, 0.6f));
        gg.outline(k.x(), k.y(), k.w(), k.h(), Glass.a(Glass.LABEL, 0.55f));
        float kw = font.width(key) * micro;
        gg.pose().pushMatrix();
        gg.pose().translate(k.x() + k.w() / 2f - kw / 2f, k.y() + 2);
        gg.pose().scale(micro, micro);
        gg.text(font, key, 0, 0, Glass.a(Glass.TEXT, 1f), false);
        gg.pose().popMatrix();
    }

    /** A count on a page that waits on you: points to spend on Status (amber), new Journal lines (light). */
    private void badge(HoloGraphics gg, Tab t, Rect n, float micro) {
        var mc = minecraft;
        int count = 0, colour = 0;
        if (t == Tab.STATUS && mc != null && mc.player != null) {
            count = Progression.profile(mc.player).unspent();
            colour = Glass.AMBER;
        } else if (t == Tab.JOURNAL) {
            count = Journal.unread();
            colour = HOT;
        }
        if (count <= 0) return;
        String s = count > 9 ? "9+" : String.valueOf(count);
        int w = Math.max(7, Math.round(font.width(s) * micro) + 3);
        int x = n.x() + 17, y = n.y() + 1;
        gg.fill(x, y, x + w, y + 8, Glass.a(colour, 0.95f));
        gg.pose().pushMatrix();
        gg.pose().translate(x + 2, y + 1.5f);
        gg.pose().scale(micro, micro);
        gg.text(font, s, 0, 0, Glass.a(0x05080F, 1f), false);
        gg.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!panel.timeline.clickable()) return true;
        if (!panel.pick(event.x(), event.y(), hit)) return true;
        double mx = hit[0], my = hit[1];
        HoloClient.pressFlash(mx, my);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (BandLayout.node(i).contains(mx, my)) {
                selectTab(tabs[i]);
                return true;
            }
        }
        Rect c = content;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && c.contains(mx, my)) {
            int y = c.y() - SCROLL[tab.ordinal()];
            for (StatusSections.Section s : StatusSections.of(tab)) {
                if (!s.visible()) continue;
                Ui title = Ui.measure(font, c.w());
                title.text(s.title(), 0);
                y += title.height() + 2;
                int h = s.height(c.w());
                if (my >= y && my < y + h && s.click(mx, my, c.x(), y, c.w())) return true;
                y += h + SECTION_GAP;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - content.h());
        int i = tab.ordinal();
        SCROLL[i] = (int) Math.max(0, Math.min(max, SCROLL[i] - scrollY * 18));
        return true;
    }

    /** The open page (the autopilot logs it after a click). */
    public Tab tab() {
        return tab;
    }

    /** Where page {@code i}'s node is on the screen, in GUI coordinates (for the autopilot's click). */
    public boolean tabCentreOnScreen(int i, float[] out) {
        if (i < 0 || i >= BandLayout.PAGES) return false;
        Rect r = BandLayout.node(i);
        return panel.project(r.x() + r.w() / 2f, r.y() + r.h() / 2f, out);
    }

    /** Shows a page; a page that opens something of its own (the Map: the Atlas) closes the band and opens it. */
    private void selectTab(Tab t) {
        StatusSections.PageAction action = StatusSections.action(t);
        if (action != null) {
            if (minecraft != null) minecraft.gui.setScreen(null);
            action.open().run();
            return;
        }
        tab = t;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.STATUS.matches(event)) {
            if (!panel.timeline.fullyOpen()) panel.timeline.skip();
            else onClose();
            return true;
        }
        Tab keyed = StatusSections.pageForKey(event);
        if (keyed != null) {
            selectTab(keyed);
            return true;
        }
        Tab[] tabs = Tab.values();
        if (event.key() == InputConstants.KEY_TAB) {
            // Tab steps through the pages that show here; the Map has its own key and its own screen
            int n = tab.ordinal();
            do n = (n + (event.hasShiftDown() ? tabs.length - 1 : 1)) % tabs.length;
            while (StatusSections.action(tabs[n]) != null && n != tab.ordinal());
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
