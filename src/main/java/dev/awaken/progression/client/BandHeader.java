package dev.awaken.progression.client;

import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A section's label on the band (SPEC §30.4): small capitals in the labels' colour, a hairline to the edge, and a note
 * on the right ("ALL 23 ›"), instead of a loud title. A section with an empty title draws its own (the Status sheet).
 */
public final class BandHeader {
    private BandHeader() {}

    /** The label row's height, 0 for a section without one. */
    public static final int H = 10;

    public static int height(StatusSections.Section s) {
        return s.title().isEmpty() ? 0 : H;
    }

    /** Draws the row at (x, y) across w; returns its height. */
    public static int draw(GuiGraphicsExtractor g, Font f, StatusSections.Section s, int x, int y, int w) {
        String title = s.title();
        if (title.isEmpty()) return 0;
        float m = StatusSections.micro();
        float tw = text(g, f, title, x, y + 2, m, Glass.LABEL);
        float right = x + w;
        String note = s.note();
        if (!note.isEmpty()) {
            float nw = f.width(note) * m;
            text(g, f, note, right - nw, y + 2, m, s.noteColour() & 0xFFFFFF);
            right -= nw + 5;
        }
        if (right > x + tw + 8) g.fill(Math.round(x + tw + 4), y + 4, Math.round(right), y + 5, Glass.a(Glass.EDGE, 0.22f));
        return H;
    }

    /** Whether (mx, my) is on the row's note (a click there opens the note's page). */
    public static boolean onNote(Font f, StatusSections.Section s, int x, int y, int w, double mx, double my) {
        String note = s.note();
        if (note.isEmpty() || s.title().isEmpty()) return false;
        float nw = f.width(note) * StatusSections.micro();
        return my >= y && my < y + H && mx >= x + w - nw - 2 && mx < x + w + 2;
    }

    private static float text(GuiGraphicsExtractor g, Font f, String s, float x, float y, float scale, int rgb) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(f, s, 0, 0, Glass.a(rgb, 1f), false);
        g.pose().popMatrix();
        return f.width(s) * scale;
    }
}
