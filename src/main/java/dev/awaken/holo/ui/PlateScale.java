package dev.awaken.holo.ui;

/**
 * How big a world plate is ({@code holo.client.HoloWorld}, SPEC §12): its world size up close, growing with distance so
 * that a unit never drops under {@link #MIN_PX} framebuffer pixels at 1080 lines (scaled with the window). Text is 9
 * units tall, so a plate's words stay readable at 8, 16 or 40 blocks. Pure, so the rule is tested on the server.
 */
public final class PlateScale {
    private PlateScale() {}

    /** Metres per unit up close: a 9-unit line of text is about 0.19 blocks tall. */
    public static final float WORLD_UNIT = 0.021f;
    /** The fewest framebuffer pixels per unit at 1080 lines: 9-unit text never under 19 pixels. */
    public static final float MIN_PX = 2.1f;

    /**
     * Metres per unit at {@code dist} metres from the eye, for a projection with {@code tanY} (tan of half the vertical
     * field of view) on a framebuffer {@code fbH} pixels tall.
     */
    public static float unit(float dist, float tanY, int fbH) {
        float metresPerPixel = 2f * tanY * Math.max(0.5f, dist) / Math.max(1, fbH);
        return Math.max(WORLD_UNIT, MIN_PX * fbH / 1080f * metresPerPixel);
    }

    /** Framebuffer pixels per unit at that distance (what the eye gets). */
    public static float pixelsPerUnit(float dist, float tanY, int fbH) {
        return unit(dist, tanY, fbH) / (2f * tanY * Math.max(0.5f, dist) / Math.max(1, fbH));
    }
}
