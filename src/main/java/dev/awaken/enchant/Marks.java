package dev.awaken.enchant;

import net.minecraft.util.Mth;

/**
 * Each enchantment's glyph (SPEC §45.15, "one script"): a frame for its family (wards a shield, banes a spear, spoils
 * a cup, carrying a gable, keeping a knot) with the enchantment's own sign inside it, drawn as straight cuts between the
 * points of a 7 by 7 grid, in the order a hand makes them. The same glyph is on its page, on the desk, in the trace, on
 * a Word Stone's face, in the Journal and in the air when the enchantment works. Common, so the server knows how many
 * strokes a trace has. A stroke is a polyline in the mark's own square (x along the piece, y across it, both -1..1),
 * sampled evenly along its length with every corner a sample.
 *
 * <p>The table below is written by {@code tools/art/specs/_enchant_glyphs.py java} (grid points, x right, y down, 0-6); it
 * replaced five generated family templates whose marks read as one within a family (three banes, one arrow).
 */
public final class Marks {
    private Marks() {}

    /** A sample about every this far along a stroke, in mark units (what the trace's reach, counted in samples, was tuned on). */
    static final float SPACING = 0.11F;
    /** A grid step in mark units: the grid's 0-6 spans a little inside the square. */
    static final float UNIT = 1 / 3.3F;

    private static final float[][][] GRID = {
            /*  1 Heat Ward */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {2F, 3.5F, 3F, 1.5F, 4F, 3.5F}},
            /*  2 Frost Ward */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {3F, 1F, 3F, 4F}, {1.5F, 2.5F, 4.5F, 2.5F}},
            /*  3 Charge Ward */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {4F, 1F, 2F, 2.5F, 4F, 2.5F, 2F, 4F}},
            /*  4 Gravity Ward */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {3F, 1F, 3F, 4F}, {2F, 3F, 3F, 4F, 4F, 3F}},
            /*  5 Spirit Ward */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {3F, 1F, 4.5F, 2.5F, 3F, 4F, 1.5F, 2.5F, 3F, 1F}},
            /*  6 Blast Protection */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {2F, 1.5F, 4F, 3.5F}, {4F, 1.5F, 2F, 3.5F}},
            /*  7 Projectile Protection */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {1.5F, 1.5F, 4.5F, 1.5F}, {3F, 1.5F, 3F, 4F}},
            /*  8 Feather Falling */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {2F, 1.2F, 3F, 2.2F, 4F, 1.2F}, {2F, 2.8F, 3F, 3.8F, 4F, 2.8F}},
            /*  9 Clear Blood */ {{0F, 0F, 6F, 0F, 6F, 3F, 3F, 6F, 0F, 3F, 0F, 0F}, {2F, 1.5F, 4F, 1.5F, 4F, 3.5F, 2F, 3.5F, 2F, 1.5F}},
            /* 10 Bane of the Dead */ {{0F, 3F, 6F, 3F}, {4F, 1F, 6F, 3F, 4F, 5F}, {1.5F, 0.5F, 1.5F, 5.5F}},
            /* 11 Bane of Crawlers */ {{0F, 3F, 6F, 3F}, {4F, 1F, 6F, 3F, 4F, 5F}, {0.5F, 1F, 1.5F, 3F, 0.5F, 5F}, {2F, 1.5F, 3F, 3F, 2F, 4.5F}},
            /* 12 Bane of Beasts */ {{0F, 3F, 6F, 3F}, {4F, 1F, 6F, 3F, 4F, 5F}, {0.5F, 0.5F, 0.5F, 2F}, {1.5F, 0.5F, 1.5F, 2F}, {2.5F, 0.5F, 2.5F, 2F}},
            /* 13 Bane of the Deep */ {{0F, 3F, 6F, 3F}, {4F, 1F, 6F, 3F, 4F, 5F}, {1F, 3F, 1F, 5.5F, 2.5F, 5.5F}},
            /* 14 Looting */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {3F, 1F, 4.5F, 2.5F, 3F, 4F, 1.5F, 2.5F, 3F, 1F}},
            /* 15 Fortune */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {3F, 4F, 3F, 1F}, {2F, 2F, 3F, 1F, 4F, 2F}},
            /* 16 Silk Touch */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {2F, 1.5F, 4F, 1.5F, 4F, 3.5F, 2F, 3.5F, 2F, 1.5F}},
            /* 17 Efficiency */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {1.5F, 1.5F, 2.5F, 2.5F, 1.5F, 3.5F}, {3.5F, 1.5F, 4.5F, 2.5F, 3.5F, 3.5F}},
            /* 18 Harvest */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {2F, 1F, 3F, 2.5F, 4F, 1F}, {3F, 2.5F, 3F, 4F}},
            /* 19 Insight */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {3F, 1F, 3F, 4F}, {1.5F, 2.5F, 4.5F, 2.5F}},
            /* 20 Respiration */ {{0F, 3F, 3F, 0F, 6F, 3F}, {3F, 3F, 4.5F, 4.5F, 3F, 6F, 1.5F, 4.5F, 3F, 3F}},
            /* 21 Aqua Affinity */ {{0F, 3F, 3F, 0F, 6F, 3F}, {1.5F, 3.5F, 4.5F, 3.5F}, {3F, 3.5F, 3F, 6F}},
            /* 22 Depth Strider */ {{0F, 3F, 3F, 0F, 6F, 3F}, {1.5F, 4F, 2.25F, 3.2F, 3F, 4F, 3.75F, 3.2F, 4.5F, 4F}, {1.5F, 5.6F, 2.25F, 4.8F, 3F, 5.6F, 3.75F, 4.8F, 4.5F, 5.6F}},
            /* 23 Swift Sneak */ {{0F, 3F, 3F, 0F, 6F, 3F}, {1.5F, 3.5F, 2.5F, 4.75F, 1.5F, 6F}, {3.5F, 3.5F, 4.5F, 4.75F, 3.5F, 6F}},
            /* 24 Unbreaking */ {{1F, 0F, 5F, 0F, 1F, 6F, 5F, 6F, 1F, 0F}, {0F, 3F, 6F, 3F}},
            /* 25 Mending */ {{1F, 0F, 5F, 0F, 1F, 6F, 5F, 6F, 1F, 0F}, {2F, 1.5F, 4F, 1.5F}, {2F, 4.5F, 4F, 4.5F}},
            /* 26 Infinity */ {{1F, 0F, 5F, 0F, 1F, 6F, 5F, 6F, 1F, 0F}, {3F, 2F, 4F, 3F, 3F, 4F, 2F, 3F, 3F, 2F}},
            /* 27 Loyalty */ {{0F, 3F, 3F, 0F, 6F, 3F}, {1.5F, 3.5F, 4.5F, 3.5F, 4.5F, 5.5F, 1.5F, 5.5F}},
            /* 28 Riptide */ {{0F, 3F, 3F, 0F, 6F, 3F}, {3F, 4.6F, 3.8F, 4.6F, 3.8F, 3.6F, 2.2F, 3.6F, 2.2F, 5.6F, 4.8F, 5.6F}},
            /* 29 Luck of the Sea */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {1.5F, 3F, 2.25F, 2F, 3F, 3F, 3.75F, 2F, 4.5F, 3F}},
            /* 30 Lure */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {3.5F, 1F, 3.5F, 3.5F, 2.5F, 4F, 2F, 3F}},
            /* 31 Lantern */ {{0F, 3F, 3F, 0F, 6F, 3F}, {3F, 3F, 3F, 6F}, {1.5F, 4.5F, 4.5F, 4.5F}, {2F, 3.5F, 4F, 5.5F}, {4F, 3.5F, 2F, 5.5F}},
            /* 32 Wayfarer */ {{0F, 3F, 3F, 0F, 6F, 3F}, {2.2F, 3F, 2.2F, 6F}, {3.8F, 3F, 3.8F, 6F}},
            /* 33 Gather */ {{0F, 0F, 0F, 4F, 2F, 6F, 4F, 6F, 6F, 4F, 6F, 0F}, {1.2F, 1.5F, 2.4F, 2.5F, 1.2F, 3.5F}, {4.8F, 1.5F, 3.6F, 2.5F, 4.8F, 3.5F}},
            /* 34 Regrowth */ {{1F, 0F, 5F, 0F, 1F, 6F, 5F, 6F, 1F, 0F}, {2.4F, 3F, 0.4F, 1.6F}, {3.6F, 3F, 5.6F, 1.6F}},
            /* 35 Remembrance */ {{1F, 0F, 5F, 0F, 1F, 6F, 5F, 6F, 1F, 0F}, {0F, 1.5F, 0F, 4.5F}, {6F, 1.5F, 6F, 4.5F}},
            /* 36 Long Arm */ {{0F, 3F, 3F, 0F, 6F, 3F}, {0.5F, 4.5F, 5.5F, 4.5F}, {5.5F, 3.5F, 5.5F, 5.5F}},
    };

    /** How many of each glyph's strokes are its family's frame; the rest are its own sign. */
    private static final int[] FRAME = {1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    private static final float[][][] CACHE = new float[GRID.length + 1][][];
    private static final float[][] PLAIN = {sample(new float[]{1, 0, 5, 0, 1, 6, 5, 6, 1, 0})};

    /** How many glyphs there are (numbered from 1; the Journal's numbers). */
    public static int count() {
        return GRID.length;
    }

    /** The glyph of the enchantment numbered {@code no}; a plain knot for anything else. */
    public static float[][] of(int no) {
        if (no < 1 || no > GRID.length) return PLAIN;
        float[][] m = CACHE[no];
        if (m == null) {
            float[][] g = GRID[no - 1];
            m = new float[g.length][];
            for (int i = 0; i < g.length; i++) m[i] = sample(g[i]);
            CACHE[no] = m;
        }
        return m;
    }

    public static int strokes(int no) {
        return of(no).length;
    }

    /** How many of the glyph's strokes are its family's frame (drawn first). */
    public static int frame(int no) {
        return no < 1 || no > FRAME.length ? 1 : FRAME[no - 1];
    }

    /** The glyph's corners as written (grid points, x right, y down, 0-6): for a 16 pixel icon, where samples are too many. */
    public static float[][] grid(int no) {
        return no < 1 || no > GRID.length ? new float[][]{{1, 0, 5, 0, 1, 6, 5, 6, 1, 0}} : GRID[no - 1];
    }

    /** A polyline through grid corners as samples in the mark's square: every corner one, the rest evenly between. */
    private static float[] sample(float[] c) {
        int corners = c.length / 2, total = 1;
        int[] steps = new int[corners];
        for (int i = 1; i < corners; i++) {
            float len = Mth.length(c[i * 2] - c[i * 2 - 2], c[i * 2 + 1] - c[i * 2 - 1]) * UNIT;
            steps[i] = Math.max(1, Math.round(len / SPACING));
            total += steps[i];
        }
        float[] p = new float[total * 2];
        int at = 0;
        put(p, at++, c[0], c[1]);
        for (int i = 1; i < corners; i++) {
            for (int k = 1; k <= steps[i]; k++) {
                float t = k / (float) steps[i];
                put(p, at++, Mth.lerp(t, c[i * 2 - 2], c[i * 2]), Mth.lerp(t, c[i * 2 - 1], c[i * 2 + 1]));
            }
        }
        return p;
    }

    private static void put(float[] p, int i, float gx, float gy) {
        p[i * 2] = (gx - 3) * UNIT;
        p[i * 2 + 1] = -(gy - 3) * UNIT;
    }
}
