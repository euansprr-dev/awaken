package dev.awaken.progression.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.progression.client.StatusSections.Section;
import dev.awaken.progression.client.StatusSections.Tab;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The old band's sections on the stage (SPEC §57): exactly as they were written, in columns of the old band's width (232
 * units, a unit two sheet pixels: the stage's body text), each section to the shortest column, inside a rectangle of the
 * sheet. The wheel moves what doesn't fit. A page (or the part of one) that hasn't moved to the stage yet shows through this.
 */
public final class SectionHost {
    public static final int COL = 232, GAP = 14, SECTION_GAP = 12;

    private record Laid(Section s, int x, int y, int h) {}

    private final List<Laid> laid = new ArrayList<>();
    private int scroll, tall, viewH;
    private float unit = 2F;
    private String[] focus;

    /** Scrolls to the first of these section titles that shows, the next time it draws (a link). */
    public void focus(String... titles) {
        focus = titles.length == 0 ? null : titles;
    }

    public boolean more() {
        return tall > viewH;
    }

    public boolean empty() {
        return laid.isEmpty();
    }

    /** Draws the page's sections that pass {@code which}; {@code empty} is said when none shows ("" for nothing). */
    public void draw(Sheet s, Hot hot, Tab tab, Predicate<Section> which, float x0, float y0, float x1, float y1, float mx, float my,
                     String empty, boolean reducedMotion) {
        GuiGraphicsExtractor g = s.g;
        unit = s.snap(Sheet.BODY);
        float w = (x1 - x0) / unit;
        viewH = (int) ((y1 - y0) / unit);
        int cols = Math.max(1, (int) ((w + GAP) / (COL + GAP)));
        int[] heights = new int[cols];
        laid.clear();
        StatusSections.drawingAt(Math.max(1, Math.round(unit * s.rps)), reducedMotion);
        for (Section sec : StatusSections.of(tab)) {
            if (!StatusSections.shows(tab, sec) || !which.test(sec)) continue;
            int c = 0;
            for (int i = 1; i < cols; i++) if (heights[i] < heights[c]) c = i;
            int h = BandHeader.height(sec) + sec.height(COL);
            if (h <= 0) continue;
            laid.add(new Laid(sec, c * (COL + GAP), heights[c], h));
            heights[c] += h + SECTION_GAP;
        }
        tall = 0;
        for (int h : heights) tall = Math.max(tall, h);
        if (focus != null) {
            out:
            for (String title : focus) for (Laid l : laid) if (l.s.title().equals(title)) {
                scroll = Math.max(0, l.y - 4);
                break out;
            }
            focus = null;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, tall - viewH)));
        // the sections' own coordinates: a unit is one of their pixels
        float umx = (mx - x0) / unit, umy = (my - y0) / unit;
        boolean inside = umx >= 0 && umy >= 0 && umx < w && umy < viewH;
        int imx = inside ? Math.round(umx) : -10000, imy = inside ? Math.round(umy) : -10000;
        g.enableScissor(Math.round(x0), Math.round(y0), Math.round(x1) + 12, Math.round(y1));
        g.pose().pushMatrix().translate(x0, y0).scale(unit, unit);
        GuiCanvas.tooltipsAt(s.gx(mx), s.gy(my));
        try {
            if (laid.isEmpty() && !empty.isEmpty()) {
                Ui ui = Ui.draw(g, s.font, 0, 0, Math.min((int) w, COL * 2), imx, imy);
                ui.text(empty, Glass.a(Glass.LABEL, 1F));
            }
            for (Laid l : laid) {
                int y = l.y - scroll;
                if (y + l.h < 0 || y > viewH) continue;
                int hh = BandHeader.draw(g, s.font, l.s, l.x, y, COL);
                l.s.render(g, l.x, y + hh, COL, imx, imy, 0F);
            }
        } finally {
            GuiCanvas.tooltipsWhereAsked();
            g.pose().popMatrix();
            g.disableScissor();
        }
        if (tall > viewH) {
            float track = y1 - y0, bar = Math.max(40F, track * viewH / tall);
            float by = y0 + (track - bar) * scroll / Math.max(1, tall - viewH);
            s.fill(x1 + 6, by, x1 + 10, by + bar, Glass.a(Glass.EDGE, .5F));
        }
        if (inside) hot.add(x0, y0, x1, y1, () -> click(imx, imy));
    }

    /**
     * Sections laid from a point down with no scroll or clip of their own (a shelf on the Journal's wall, which scrolls
     * as one): columns as in {@link #draw}, drawn where they cross {@code viewY0..viewY1}. Returns the y under the last.
     */
    public static float flat(Sheet s, Hot hot, List<Section> secs, float x0, float y0, float x1, float viewY0, float viewY1, float mx, float my,
                             boolean reducedMotion) {
        if (secs.isEmpty()) return y0;
        GuiGraphicsExtractor g = s.g;
        float unit = s.snap(Sheet.BODY), w = (x1 - x0) / unit;
        int cols = Math.max(1, (int) ((w + GAP) / (COL + GAP)));
        int[] heights = new int[cols];
        List<Laid> laid = new ArrayList<>(secs.size());
        StatusSections.drawingAt(Math.max(1, Math.round(unit * s.rps)), reducedMotion);
        for (Section sec : secs) {
            int c = 0;
            for (int i = 1; i < cols; i++) if (heights[i] < heights[c]) c = i;
            int h = BandHeader.height(sec) + sec.height(COL);
            if (h <= 0) continue;
            laid.add(new Laid(sec, c * (COL + GAP), heights[c], h));
            heights[c] += h + SECTION_GAP;
        }
        int tall = 0;
        for (int h : heights) tall = Math.max(tall, h);
        float umx = (mx - x0) / unit, umy = (my - y0) / unit;
        boolean inside = umx >= 0 && umy >= 0 && umx < w && umy < tall && my >= viewY0 && my < viewY1;
        int imx = inside ? Math.round(umx) : -10000, imy = inside ? Math.round(umy) : -10000;
        g.pose().pushMatrix().translate(x0, y0).scale(unit, unit);
        GuiCanvas.tooltipsAt(s.gx(mx), s.gy(my));
        try {
            for (Laid l : laid) {
                float top = y0 + l.y * unit;
                if (top + l.h * unit < viewY0 || top > viewY1) continue;
                int hh = BandHeader.draw(g, s.font, l.s, l.x, l.y, COL);
                l.s.render(g, l.x, l.y + hh, COL, imx, imy, 0F);
            }
        } finally {
            GuiCanvas.tooltipsWhereAsked();
            g.pose().popMatrix();
        }
        if (inside) hot.add(x0, y0, x1, y0 + tall * unit, () -> {
            for (Laid l : laid) {
                int top = l.y + BandHeader.height(l.s);
                if (imx >= l.x && imx < l.x + COL && imy >= top && imy < l.y + l.h && l.s.click(imx, imy, l.x, top, COL)) return;
            }
        });
        return y0 + tall * unit;
    }

    /** A left click at a point in the sections' own units, already scrolled into view. */
    private void click(int mx, int my) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        for (Laid l : laid) {
            int y = l.y - scroll;
            if (l.s.notePage() != null && BandHeader.onNote(font, l.s, l.x, y, COL, mx, my)) {
                Tab to = StatusSections.follow(l.s);
                if (to != null) StatusSections.go(to);
                return;
            }
            int top = y + BandHeader.height(l.s);
            if (mx >= l.x && mx < l.x + COL && my >= top && my < y + l.h && l.s.click(mx, my, l.x, top, COL)) return;
        }
    }

    /** A key for the sections that show. Tab, Q, E and Esc mean the same on every place, so no old section gets them. */
    public boolean key(KeyEvent event, Tab tab, Predicate<Section> which) {
        int k = event.key();
        if (k == InputConstants.KEY_TAB || k == InputConstants.KEY_Q || k == InputConstants.KEY_E || k == InputConstants.KEY_ESCAPE) return false;
        for (Section sec : StatusSections.of(tab)) if (StatusSections.shows(tab, sec) && which.test(sec) && sec.key(event)) return true;
        return false;
    }

    public boolean scroll(double amount) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, tall - viewH), scroll - amount * 18));
        return true;
    }
}
