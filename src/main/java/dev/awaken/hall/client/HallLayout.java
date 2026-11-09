package dev.awaken.hall.client;

import dev.awaken.hall.HallNet;
import org.jspecify.annotations.Nullable;

/** The wall's niches on the sheet: six ranks receding upward, sixteen kinds in five bays. */
final class HallLayout {
    private HallLayout() {}

    private static final int[] ROW_H = {184, 156, 130, 108, 90, 74};
    private static final int[] BAY = {4, 2, 4, 3, 3};
    private static final int GAP = 9, BOTTOM = 908;
    static final float[] P = {1F, .935F, .87F, .805F, .74F, .68F};
    static final String[] FAMILY = {"Blades", "Heavy", "Reach", "Close", "Strange"};
    static final int[] ROW_TOP = new int[HallNet.ROWS], ROW_BOTTOM = new int[HallNet.ROWS];
    /** Each bay's middle on the bottom row. */
    static final float[] BAY_X = new float[BAY.length];

    record Cell(int col, int tier, float x, float y0, float y1, float w) {
        float height() {
            return (y1 - y0) * 1.02F;
        }

        /** The middle of the weapon's picture, which stands slightly proud of the niche's base. */
        float cy() {
            float h = height();
            return y1 - h + h * .015F + h / 2F;
        }
    }

    private static final Cell[] CELLS = build();

    private static Cell[] build() {
        Cell[] out = new Cell[HallNet.COLS * HallNet.ROWS];
        int y = BOTTOM;
        for (int tier = 0; tier < HallNet.ROWS; tier++) {
            int top = y - ROW_H[tier];
            ROW_TOP[tier] = top;
            ROW_BOTTOM[tier] = y;
            float tw = 1640F * P[tier], fg = 26F * P[tier], cw = (tw - fg * 4F) / HallNet.COLS, x = 854F - tw / 2F;
            int col = 0;
            for (int bay = 0; bay < BAY.length; bay++) {
                float start = x;
                for (int i = 0; i < BAY[bay]; i++) {
                    out[col * HallNet.ROWS + tier] = new Cell(col, tier, x + cw / 2F, top, y, cw);
                    col++;
                    x += cw;
                }
                if (tier == 0) BAY_X[bay] = (start + x) / 2F;
                x += fg;
            }
            y = top - GAP;
        }
        return out;
    }

    static Cell cell(int col, int tier) {
        return CELLS[col * HallNet.ROWS + tier];
    }

    static @Nullable Cell at(float x, float y) {
        for (Cell c : CELLS) if (x >= c.x - c.w / 2F && x < c.x + c.w / 2F && y >= c.y0 && y < c.y1) return c;
        return null;
    }

    /** The picture's scale in a box of side h: short weapons are drawn smaller, so a dagger reads as a dagger beside a greatblade. */
    static float portrait(Meshes.Up up, float h) {
        float f = .6F + .4F * Math.min(1F, up.length() / 3F);
        return 236F * f / up.length() * (h / 256F);
    }

    static int styleColumn(String style) {
        for (int i = 0; i < HallNet.COLUMNS.length; i++) if (HallNet.COLUMNS[i].equals(style)) return i;
        return -1;
    }
}
