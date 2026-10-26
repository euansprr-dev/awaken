package dev.awaken.holo.ui;

import dev.awaken.signal.ui.Rect;

/**
 * Geometry of the band's panel (the K menu, SPEC §30), in panel units: 288×228, a rail of the six pages down the wrist
 * edge (the panel's left: the side the beams come from), the K key at its foot, and the page's content beside it with a
 * scroll bar. Pure maths, so a GameTest checks that nothing overlaps.
 */
public final class BandLayout {
    private BandLayout() {}

    public static final int W = 288, H = 228;
    /** The rail's width and its page nodes: one every {@link #NODE_STEP} units from {@link #NODE_TOP}. */
    public static final int RAIL = 30, NODE_TOP = 9, NODE_STEP = 34, NODE_H = 31, PAGES = 6;
    public static final int CONTENT_X = 40, CONTENT_Y = 10, PAD = 10, SCROLLBAR = 6;

    /** Page {@code i}'s node on the rail. */
    public static Rect node(int i) {
        return new Rect(2, NODE_TOP + i * NODE_STEP, RAIL - 2, NODE_H);
    }

    /** The 12×12 icon inside page {@code i}'s node. */
    public static Rect icon(int i) {
        return new Rect(9, NODE_TOP + i * NODE_STEP + 4, 12, 12);
    }

    /** Where the K key sits, at the rail's foot. */
    public static Rect close() {
        return new Rect(9, H - 15, 12, 9);
    }

    /** The page's content (the scroll bar runs just right of it). */
    public static Rect content() {
        return new Rect(CONTENT_X, CONTENT_Y, W - CONTENT_X - PAD - SCROLLBAR, H - 2 * CONTENT_Y);
    }

    /**
     * Text scale for the rail's labels: two framebuffer pixels fewer per font pixel than the body text, so they stay on
     * whole pixels (3 at the MacBook's 5 px a unit, 4 at the Dell's 6), and never under 2.
     */
    public static float microScale(int ppu) {
        return ppu <= 0 ? 0.6f : Math.max(2, ppu - 2) / (float) Math.max(ppu, 2);
    }
}
