package dev.awaken.atlas.client;

import dev.awaken.atlas.AtlasData;
import dev.awaken.atlas.AtlasNet;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A waypoint's look on the Atlas (SPEC §16.8): a thin stem standing on its place, a dark diamond head rimmed in its colour,
 * and its mark drawn small inside in the same colour. The eight marks (flag, pick, star, house, cross, cave, ring,
 * diamond) and where you fell's headstone are 7×7 pixel glyphs, so they stay crisp at every GUI scale.
 */
final class WaypointGlyphs {
    private WaypointGlyphs() {}

    static final int NAVY = 0xFF0B1626;
    /** Head radius and stem height (GUI px). */
    static final int HEAD = 7, STEM = 14;
    /** The marks' names, as the card's buttons read them out. */
    static final String[] NAMES = {"flag", "pick", "star", "house", "cross", "cave", "ring", "diamond"};

    // each glyph: seven rows, bit 6 the leftmost pixel
    private static final int[][] MARK = {
            {0b1111110, 0b1111100, 0b1111110, 0b1000000, 0b1000000, 0b1000000, 0b1000000}, // flag
            {0b0111110, 0b1001001, 0b0001000, 0b0001000, 0b0001000, 0b0001000, 0b0001000}, // pick
            {0b0001000, 0b0001000, 0b1111111, 0b0111110, 0b0011100, 0b0110110, 0b0100010}, // star
            {0b0001000, 0b0011100, 0b0111110, 0b1111111, 0b0111110, 0b0110110, 0b0110110}, // house
            {0b1100011, 0b0110110, 0b0011100, 0b0001000, 0b0011100, 0b0110110, 0b1100011}, // cross
            {0b0011100, 0b0111110, 0b1100011, 0b1100011, 0b1100011, 0b1100011, 0b1100011}, // cave
            {0b0011100, 0b0100010, 0b1000001, 0b1000001, 0b1000001, 0b0100010, 0b0011100}, // ring
            {0b0001000, 0b0011100, 0b0111110, 0b1111111, 0b0111110, 0b0011100, 0b0001000}, // diamond
    };
    private static final int[] FELL = {0b0011100, 0b0111110, 0b0110110, 0b0100010, 0b0110110, 0b0111110, 0b1111111};

    /** A glyph's pixels, its top left at (x, y). */
    static void glyph(GuiGraphicsExtractor g, int mark, boolean fell, int x, int y, int argb) {
        int[] rows = fell ? FELL : MARK[Math.floorMod(mark, AtlasData.MARKS)];
        for (int r = 0; r < 7; r++) {
            int bits = rows[r];
            for (int c = 0; c < 7; c++) {
                if ((bits & (1 << (6 - c))) == 0) continue;
                int run = 1; // runs of pixels as one fill
                while (c + run < 7 && (bits & (1 << (6 - c - run))) != 0) run++;
                g.fill(x + c, y + r, x + c + run, y + r + 1, argb);
                c += run - 1;
            }
        }
    }

    /**
     * The waypoint standing on (sx, sy): its foot's ring on the ground, the stem, the head with its mark. {@code bright}
     * lifts it (the one being changed, or hovered).
     */
    static void marker(GuiGraphicsExtractor g, int sx, int sy, int rgb, int mark, boolean fell, boolean bright) {
        int c = 0xFF000000 | rgb;
        foot(g, sx, sy, 6, Glass.a(rgb, bright ? 0.85f : 0.6f));
        g.fill(sx - 1, sy - STEM, sx + 2, sy + 1, NAVY);
        g.fill(sx, sy - STEM, sx + 1, sy, c);
        int hy = sy - STEM - HEAD; // the head's centre
        diamond(g, sx, hy, HEAD + 2, Glass.a(rgb, bright ? 0.40f : 0.22f)); // its glow
        diamond(g, sx, hy, HEAD + 1, NAVY);
        diamond(g, sx, hy, HEAD, c);
        diamond(g, sx, hy, HEAD - 1, NAVY);
        glyph(g, mark, fell, sx - 3, hy - 3, bright ? 0xFFFFFFFF : c);
    }

    /** Where the head's centre is above the foot, for labels and picking. */
    static int headY(int sy) {
        return sy - STEM - HEAD;
    }

    private static void diamond(GuiGraphicsExtractor g, int x, int y, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            g.fill(x - span, y + dy, x + span + 1, y + dy + 1, argb);
        }
    }

    /** A flat ring round the foot, as if lying on the land. */
    private static void foot(GuiGraphicsExtractor g, int x, int y, int r, int argb) {
        for (int k = 0; k < 32; k++) {
            double t = k * Math.PI * 2 / 32;
            int px = x + (int) Math.round(Math.cos(t) * r), py = y + (int) Math.round(Math.sin(t) * r * 0.5);
            g.fill(px, py, px + 1, py + 1, argb);
        }
    }

    /** A waypoint's colour as an index of the palette (-1: its owner's own colour, from a pin before waypoints). */
    static int colourIndex(AtlasNet.WaypointView w) {
        for (int i = 0; i < AtlasData.COLOURS.length; i++) if (AtlasData.COLOURS[i] == w.colour()) return i;
        return AtlasData.OWN_COLOUR;
    }
}
