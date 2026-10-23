package dev.awaken.signal.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Geometry of the Status screen (K). Pure maths so GameTests can check every GUI size: the panel fits the screen, the
 * tabs fit the panel and never touch the content, and the content keeps a usable width. Narrow screens (under
 * {@link #SIDE_TABS_MIN_W} panel width, e.g. auto GUI scale on a laptop) get a row of tabs on top.
 */
public final class StatusLayout {
    private StatusLayout() {}

    public static final int MAX_W = 560, MAX_H = 360, OUTER = 8, SCROLLBAR = 6, SIDE_TABS_MIN_W = 440;
    public static final int TAB_H_TOP = 16, TAB_W_SIDE = 86, TAB_H_SIDE = 18;

    /** {@code close} is where "K to close" goes, or null when there's no room for it. */
    public record Frame(Rect panel, Rect content, List<Rect> tabs, Rect close, boolean topTabs) {}

    public static Frame of(int w, int h, List<String> tabLabels, String closeHint, ToIntFunction<String> measure) {
        int pw = Math.min(MAX_W, w - 2 * OUTER), ph = Math.min(MAX_H, h - 2 * OUTER);
        int px = (w - pw) / 2, py = (h - ph) / 2;
        Rect panel = new Rect(px, py, pw, ph);
        boolean top = pw < SIDE_TABS_MIN_W;
        List<Rect> tabs = new ArrayList<>();
        Rect content, close = null;
        if (top) {
            int x = px + 8;
            for (String l : tabLabels) {
                int tw = measure.applyAsInt(l) + 16;
                tabs.add(new Rect(x, py + 6, tw, TAB_H_TOP));
                x += tw + 4;
            }
            int cw = measure.applyAsInt(closeHint);
            if (x + 8 + cw <= px + pw - 8) close = new Rect(px + pw - 8 - cw, py + 10, cw, 9);
            content = new Rect(px + 10, py + 28, pw - 20 - SCROLLBAR, ph - 28 - 8);
        } else {
            for (int i = 0; i < tabLabels.size(); i++) tabs.add(new Rect(px + 8, py + 32 + i * 22, TAB_W_SIDE, TAB_H_SIDE));
            close = new Rect(px + 12, py + ph - 16, Math.min(84, measure.applyAsInt(closeHint)), 9);
            content = new Rect(px + 106, py + 12, pw - 106 - 10 - SCROLLBAR, ph - 24);
        }
        return new Frame(panel, content, tabs, close, top);
    }
}
