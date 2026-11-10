package dev.awaken.holo.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.progression.client.BandHeader;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.progression.client.StatusSections.Tab;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A band page whose module hasn't moved to the stage yet (SPEC §57): its old sections, exactly as they were written, laid
 * out on the stage in columns of the old band's width (232 units), a unit two sheet pixels (the stage's body text size).
 * Each section goes to the shortest column, so a page that scrolled for five screens mostly fits on one; the wheel moves
 * what doesn't. A page loses this the day its module gives the band a place of its own.
 */
final class LegacyPlace implements BandPlaces.Place {
    private static final int COL = 232, GAP = 14, SECTION_GAP = 12;

    private record Laid(StatusSections.Section s, int x, int y, int h) {}

    private final Tab tab;
    private final List<Laid> laid = new ArrayList<>();
    private int scroll, tall, viewH;
    private float unit = 2F;
    private String[] focus;

    LegacyPlace(Tab tab) {
        this.tab = tab;
    }

    @Override
    public void opened(String... focus) {
        this.focus = focus.length == 0 ? null : focus;
    }

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        GuiGraphicsExtractor g = s.g;
        unit = s.snap(Sheet.BODY);
        float w = (BandPlaces.X1 - BandPlaces.X0) / unit;
        viewH = (int) ((BandPlaces.Y1 - BandPlaces.Y0) / unit);
        int cols = Math.max(1, (int) ((w + GAP) / (COL + GAP)));
        int[] heights = new int[cols];
        laid.clear();
        StatusSections.drawingAt(Math.max(1, Math.round(unit * s.rps)), HoloConfig.reducedMotion);
        for (StatusSections.Section sec : StatusSections.of(tab)) {
            if (!StatusSections.shows(tab, sec)) continue;
            int c = 0;
            for (int i = 1; i < cols; i++) if (heights[i] < heights[c]) c = i;
            int h = BandHeader.height(sec) + sec.height(COL);
            laid.add(new Laid(sec, c * (COL + GAP), heights[c], h));
            heights[c] += h + SECTION_GAP;
        }
        tall = 0;
        for (int h : heights) tall = Math.max(tall, h);
        if (focus != null) {
            // a link lands on its section: the first named that shows here
            out:
            for (String title : focus) for (Laid l : laid) if (l.s.title().equals(title)) {
                scroll = Math.max(0, l.y - 4);
                break out;
            }
            focus = null;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, tall - viewH)));
        // the sections' own coordinates: a unit is one of their pixels
        float umx = (mx - BandPlaces.X0) / unit, umy = (my - BandPlaces.Y0) / unit;
        boolean inside = umx >= 0 && umy >= 0 && umx < w && umy < viewH;
        int imx = inside ? Math.round(umx) : -10000, imy = inside ? Math.round(umy) : -10000;
        g.enableScissor(Math.round(BandPlaces.X0), Math.round(BandPlaces.Y0), Math.round(BandPlaces.X1) + 12, Math.round(BandPlaces.Y1));
        g.pose().pushMatrix().translate(BandPlaces.X0, BandPlaces.Y0).scale(unit, unit);
        GuiCanvas.tooltipsAt(s.gx(mx), s.gy(my));
        try {
            if (laid.isEmpty()) {
                Ui ui = Ui.draw(g, s.font, 0, 0, COL * 2, imx, imy);
                ui.text(HoloStatusScreen.empty(tab), Glass.a(Glass.LABEL, 1F));
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
            float track = BandPlaces.Y1 - BandPlaces.Y0, bar = Math.max(40F, track * viewH / tall);
            float by = BandPlaces.Y0 + (track - bar) * scroll / Math.max(1, tall - viewH);
            s.fill(BandPlaces.X1 + 6, by, BandPlaces.X1 + 10, by + bar, Glass.a(Glass.EDGE, .5F));
        }
        if (inside) hot.add(BandPlaces.X0, BandPlaces.Y0, BandPlaces.X1, BandPlaces.Y1, () -> click(imx, imy));
    }

    /** A left click at a point in the sections' own units, already scrolled into view. */
    private void click(int mx, int my) {
        for (Laid l : laid) {
            int y = l.y - scroll;
            if (l.s.notePage() != null && BandHeader.onNote(net.minecraft.client.Minecraft.getInstance().font, l.s, l.x, y, COL, mx, my)) {
                Tab to = StatusSections.follow(l.s);
                if (to != null) StatusSections.go(to);
                return;
            }
            int top = y + BandHeader.height(l.s);
            if (mx >= l.x && mx < l.x + COL && my >= top && my < y + l.h && l.s.click(mx, my, l.x, top, COL)) return;
        }
    }

    @Override
    public boolean key(KeyEvent event) {
        // Tab, Q and E mean the same on every place (SPEC §57): an old section's own use of them waits for its move
        int k = event.key();
        if (k == InputConstants.KEY_TAB || k == InputConstants.KEY_Q || k == InputConstants.KEY_E || k == InputConstants.KEY_ESCAPE) return false;
        for (StatusSections.Section sec : StatusSections.of(tab)) if (StatusSections.shows(tab, sec) && sec.key(event)) return true;
        return false;
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, tall - viewH), scroll - amount * 18));
        return true;
    }

    @Override
    public void hints(List<Kit.Hint> out) {
        if (tall > viewH) out.add(Kit.Hint.of("More", "Scroll"));
    }
}
