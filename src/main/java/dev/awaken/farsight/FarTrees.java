package dev.awaken.farsight;

import dev.awaken.terrain.alpine.AlpineSurface;
import dev.awaken.terrain.alpine.Erosion;

/**
 * Far forests (SPEC §20, Far Sight 2): the trees a far patch stands, as instances the client draws as opaque voxel trees
 * (a trunk and a crown, shaped by kind). Rings 1 to 4 (cells of 4 to 32 blocks, half a kilometre to four) plant them;
 * nearer the real chunks have their own, farther a tree is under a pixel and the ground's canopy shading carries the
 * forest. Each cell holds a few candidate spots, jittered by a hash of the world position, so the same place grows the
 * same trees whichever ring asks; a spot takes a tree as often as the forest is dense there, and never on water, on
 * slopes too steep for soil, or above the treeline (a little under the alpine snow line).
 *
 * <p>Works from a patch's corner texels ({@link FarColumns#patch}, or the real chunks' {@link FarStore#patch}), so the
 * prediction and the generated layer plant alike. Pure, allocation-free; worker threads.
 */
public final class FarTrees {
    private FarTrees() {}

    /** Trees one patch may hold, and the ints per tree. */
    public static final int MAX = 512, INTS = 4;
    /** The rings that plant trees. */
    public static final int FIRST_RING = 1, LAST_RING = 4;
    /** Candidate spots per cell, by ring (more per cell as cells grow, so forests keep their look). */
    private static final int[] SPOTS = {0, 1, 2, 3, 2};

    public static boolean plants(int ring) {
        return ring >= FIRST_RING && ring <= LAST_RING;
    }

    /**
     * Plants the patch's trees into {@code out} ({@link #INTS} ints each) and returns how many:
     * <ol>
     *   <li>x and z in the patch, 1/16 block, 16 bits each;</li>
     *   <li>the ground under it (a height texel), the canopy's height × 4 (8 bits), the shape (2 bits), a seed (6 bits);</li>
     *   <li>the crown's colour (RGB) and its width × 8 (8 bits);</li>
     *   <li>unused.</li>
     * </ol>
     * The crown texel's alpha is the shape (the generated layer's is filled in from the biome before this runs).
     */
    public static int plant(int ring, int px, int pz, int[] height, int[] colour, int[] info, int[] crown, int seaLevel, int[] out) {
        if (!plants(ring)) return 0;
        int s = Rings.cellSize(ring), spots = SPOTS[ring], n = 0;
        for (int j = 0; j < Rings.PATCH - 1; j++) {
            for (int i = 0; i < Rings.PATCH - 1; i++) {
                int k = j * Rings.PATCH + i;
                float density = 0f;
                boolean known = true;
                for (int q = 0; q < 4; q++) {
                    int c = k + (q & 1) + (q >> 1) * Rings.PATCH;
                    if (height[c] == 0) known = false;
                    density = Math.max(density, (info[c] >> 16 & 255) / 255f);
                    // water anywhere in the cell: no trees on it
                    if ((info[c] & 255) > 0) known = false;
                }
                if (!known || density <= 0f) continue;
                float h00 = FarColumns.unpackHeight(height[k]), h10 = FarColumns.unpackHeight(height[k + 1]);
                float h01 = FarColumns.unpackHeight(height[k + Rings.PATCH]), h11 = FarColumns.unpackHeight(height[k + Rings.PATCH + 1]);
                // the cell's slope (rise per block): too steep for soil, no trees
                float gx = ((h10 - h00) + (h11 - h01)) * 0.5f / s, gz = ((h01 - h00) + (h11 - h10)) * 0.5f / s;
                float slope = (float) Math.sqrt(gx * gx + gz * gz);
                if (slope > AlpineSurface.TREE_SLOPE) continue;
                int flags = colour[k] >>> 24;
                int wx0 = (px * Rings.PATCH + i) * s, wz0 = (pz * Rings.PATCH + j) * s;
                for (int t = 0; t < spots; t++) {
                    if (n >= MAX) return n;
                    int hash = Erosion.hash(wx0 + t * 7919, wz0, 0x7EE5 + ring);
                    float u = 0.12f + 0.76f * Erosion.unit(hash), v = 0.12f + 0.76f * Erosion.unit(hash * 0x2C1B3C6D);
                    // a spot takes a tree as often as the forest is dense (the densest forests: about two in three)
                    if (Erosion.unit(hash * 0x297A2D39) >= density * 0.72f) continue;
                    float ground = h00 * (1 - u) * (1 - v) + h10 * u * (1 - v) + h01 * (1 - u) * v + h11 * u * v;
                    float wx = wx0 + u * s, wz = wz0 + v * s;
                    // the treeline, as the chunks' own trees keep it (Awakened Alpine's awaken:treeline)
                    if ((flags & FarColumns.ALPINE) != 0 && !AlpineSurface.treeAllowed(wx, ground, wz, gx, gz)) continue;
                    int at = n * INTS;
                    int lx = Math.round((i + u) * s * 16), lz = Math.round((j + v) * s * 16);
                    int c = crown[k];
                    int canopy = info[k] >> 8 & 255;
                    // each tree a little taller or shorter than the forest's mean
                    float tall = 0.75f + 0.5f * Erosion.unit(hash >>> 3);
                    int canopyQuarter = Math.clamp(Math.round(Math.max(canopy, 16) * tall), 8, 255);
                    int shape = c >>> 24 & 3;
                    int width = shape == FarPalette.CONIFER ? 4 : shape == FarPalette.BROADLEAF ? 5 : 7;
                    float wide = 0.8f + 0.4f * Erosion.unit(hash >>> 7);
                    out[at] = lx & 0xFFFF | (lz & 0xFFFF) << 16;
                    out[at + 1] = FarColumns.packHeight(ground) | canopyQuarter << 16 | shape << 24 | (hash >>> 26) << 26;
                    out[at + 2] = (c & 0xFFFFFF) | Math.clamp(Math.round(width * wide * 8), 8, 255) << 24;
                    out[at + 3] = 0;
                    n++;
                }
            }
        }
        return n;
    }
}
