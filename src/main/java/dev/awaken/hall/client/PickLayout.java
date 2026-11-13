package dev.awaken.hall.client;

import dev.awaken.hall.HallNet;
import org.jspecify.annotations.Nullable;

/**
 * The Picks wall's niches on the sheet (SPEC §52): the four forms across, each its own bay, on the Arms wall's six rows, so
 * the tide, the ledges and the rank letters line up with it. tools/art/hall/backdrops.py paints the same arithmetic.
 */
final class PickLayout {
    private PickLayout() {}

    static final int COLS = HallNet.PICK_COLS;
    private static final float WIDE = 980F, GAP = 46F;
    /** Each form's middle on the bottom row, for the names under the wall. */
    static final float[] COL_X = new float[COLS];
    private static final float[] LEFT = new float[HallNet.ROWS];
    private static final HallLayout.Cell[] CELLS = build();

    private static HallLayout.Cell[] build() {
        HallLayout.Cell[] out = new HallLayout.Cell[COLS * HallNet.ROWS];
        for (int tier = 0; tier < HallNet.ROWS; tier++) {
            float p = HallLayout.P[tier], tw = WIDE * p, fg = GAP * p, cw = (tw - fg * (COLS - 1)) / COLS, x = 854F - tw / 2F;
            LEFT[tier] = x;
            for (int col = 0; col < COLS; col++) {
                out[col * HallNet.ROWS + tier] = new HallLayout.Cell(col, tier, x + cw / 2F, HallLayout.ROW_TOP[tier], HallLayout.ROW_BOTTOM[tier], cw);
                if (tier == 0) COL_X[col] = x + cw / 2F;
                x += cw + fg;
            }
        }
        return out;
    }

    static HallLayout.Cell cell(int col, int tier) {
        return CELLS[col * HallNet.ROWS + tier];
    }

    static HallLayout.@Nullable Cell at(float x, float y) {
        for (HallLayout.Cell c : CELLS) if (x >= c.x() - c.w() / 2F && x < c.x() + c.w() / 2F && y >= c.y0() && y < c.y1()) return c;
        return null;
    }

    /** The row's left edge, where its rank letter stands off. */
    static float left(int tier) {
        return LEFT[tier];
    }

    /** A pick stood up in a niche: its height fitted to the niche's, and its head kept inside the niche's width. */
    static float portrait(Meshes.Up up, HallLayout.Cell c) {
        return Math.min(c.height() * .8F / up.length(), c.w() * .78F / Math.max(.01F, up.width()));
    }
}
