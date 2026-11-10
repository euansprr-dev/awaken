package dev.awaken.holo.ui;

/**
 * Geometry of the band's stage (the K menu, SPEC §57), in sheet pixels (1708 x 960): you and the six places down the wrist
 * edge, the plate of glass, the place's name, the stage, the panel that speaks about the thing in focus, and the bar of
 * keys. Pure maths, so a GameTest checks that nothing overlaps or leaves the sheet.
 */
public final class StageLayout {
    private StageLayout() {}

    public static final int W = 1708, H = 960, PLACES = 6;
    /** The rail: six nodes down the wrist edge. */
    public static final float RAIL_X = 26F, RAIL_Y = 124F, NODE_W = 104F, NODE_H = 96F, NODE_STEP = 102F;
    /** The plate of the band's glass. */
    public static final float PLATE_X0 = 152F, PLATE_Y0 = 40F, PLATE_X1 = 1680F, PLATE_Y1 = 884F;
    /** The stage inside it: under the place's name. The same numbers as {@code BandPlaces}'. */
    public static final float STAGE_X0 = 192F, STAGE_Y0 = 144F, STAGE_X1 = 1640F, STAGE_Y1 = 856F;
    /** The panel of words: the stage's right 440. */
    public static final float PANEL_X0 = STAGE_X1 - 440F;
    /** The bar of keys, under the plate. */
    public static final float HINT_Y = 904F, HINT_H = 28F;
    /** Sheet pixels to one unit of the glass shader (its rim, brackets and scan lines are drawn in units). */
    public static final float GLASS_UNIT = 3F;

    public static float nodeY(int i) {
        return RAIL_Y + i * NODE_STEP;
    }

    /** The plate's rectangle in GUI pixels for a window: x, y, width, height (what the 3D panel lands on). */
    public static int[] plateOnScreen(int width, int height) {
        float k = Math.min(width / (float) W, height / (float) H), ox = (width - W * k) / 2F, oy = (height - H * k) / 2F;
        return new int[] {Math.round(ox + PLATE_X0 * k), Math.round(oy + PLATE_Y0 * k), Math.round((PLATE_X1 - PLATE_X0) * k), Math.round((PLATE_Y1 - PLATE_Y0) * k)};
    }
}
