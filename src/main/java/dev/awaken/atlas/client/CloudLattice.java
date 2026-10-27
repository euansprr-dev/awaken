package dev.awaken.atlas.client;

/**
 * Where the Atlas's cloud puffs sit at every zoom (SPEC §16.8). Each level is a fixed lattice of world cells (16, 32,
 * 64, 128, 256 blocks), and each cell holds a puff. The zoom is a continuous level between two lattices. As it moves from
 * one level to the next, every puff glides and grows toward its parent cell's puff, and only one puff in four is its
 * parent: the one in the cell whose coordinates are both even (its "heir"), which shares the parent's look. The other
 * three thin out as they merge into it. So the blanket never changes all at once: zooming out, puffs merge into bigger
 * ones; zooming in, they split. Plain maths, no client classes (a GameTest reads it).
 */
public final class CloudLattice {
    static final int LEVELS = SliceSampler.LODS;
    /** Screen pixels a block where the finest lattice is at its own size (the mesh's finest level begins there too). */
    static final float FINEST_PX = 3.6f;

    private CloudLattice() {}

    /** The zoom as a continuous level, 0 (16-block cells) to 4 (256), from screen pixels a block. */
    static float level(float physicalPxPerBlock) {
        if (!(physicalPxPerBlock > 0f)) return LEVELS - 1;
        float l = (float) (Math.log(FINEST_PX / physicalPxPerBlock) / Math.log(2));
        return Math.clamp(l, 0f, LEVELS - 1f);
    }

    static int cell(int k) {
        return 16 << k;
    }

    static boolean heir(int i, int j) {
        return (i & 1) == 0 && (j & 1) == 0;
    }

    /** The look of the puff in cell (i, j) at level k: an heir is its parent's puff, so it hashes as the parent. */
    static int hash(int i, int j, int k) {
        while (k < LEVELS - 1 && heir(i, j)) {
            i >>= 1;
            j >>= 1;
            k++;
        }
        return SliceSampler.hash(i * 7 + 3 + k * 101, j * 13 + 5);
    }

    /** Where the puff of cell (i, j) at level k rests, in world blocks, with the wind's offset: x, z and its scale. */
    static void rest(int i, int j, int k, double offX, double offZ, double[] out) {
        int h = hash(i, j, k);
        float r1 = (h & 255) / 255f, r2 = ((h >> 8) & 255) / 255f;
        double c = cell(k), s = c / 16.0;
        out[0] = (i + 0.5) * c + offX + (r1 - 0.5f) * 12.0 * s;
        out[1] = (j + 0.5) * c + offZ + (r2 - 0.5f) * 12.0 * s;
        out[2] = s;
    }

    /**
     * How opaque a puff is over cover {@code f} (0 known .. 1 unknown, blended between cell centres), the same at every
     * zoom: whole over unknown ground and its cells' edge, so the bank reaches over the last stretch of known land; a puff
     * in a known cell shows only as far as it leans toward the unknown.
     */
    static float opacity(float f) {
        float t = Math.clamp((f - 0.08f) / 0.42f, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static final ThreadLocal<double[]> PARENT = ThreadLocal.withInitial(() -> new double[3]);

    /**
     * The puff of cell (i, j) at level k, {@code t} (0..1) of the way to level k + 1: where it is and its scale (into
     * {@code out}: x, z, scale). Returns how much of it is left: 1 for an heir, fading to 0 for the other three.
     */
    static float place(int i, int j, int k, float t, double offX, double offZ, double[] out) {
        rest(i, j, k, offX, offZ, out);
        if (t <= 0f || k >= LEVELS - 1) return 1f;
        double[] p = PARENT.get();
        rest(i >> 1, j >> 1, k + 1, offX, offZ, p);
        float e = t * t * (3 - 2 * t); // eased, so a puff starts and ends its glide gently
        out[0] += (p[0] - out[0]) * e;
        out[1] += (p[1] - out[1]) * e;
        out[2] += (p[2] - out[2]) * e;
        return heir(i, j) ? 1f : 1f - e;
    }

    /**
     * The seam between two levels: an heir at the end of its step is its parent at the start of the next, in place, size
     * and look; the other three are gone; the level follows the zoom smoothly; the bank is whole over unknown ground at
     * every level. Null when it holds, else what failed.
     */
    public static String selfTest() {
        if (level(3.6f) != 0f || Math.abs(level(1.8f) - 1f) > 1e-5f || Math.abs(level(0.9f) - 2f) > 1e-5f || level(0.01f) != LEVELS - 1
                || !(level(2.5f) > 0f && level(2.5f) < 1f))
            return "the level follows screen pixels a block: 3.6 → 0, 1.8 → 1, 0.9 → 2, between them between";
        if (opacity(0f) != 0f || opacity(0.5f) < 0.999f || opacity(1f) != 1f || !(opacity(0.3f) > 0.3f && opacity(0.3f) < 1f))
            return "a puff is whole over unknown ground and the edge of its cell, and reaches part way over known land";
        double[] a = new double[3], b = new double[3];
        double ox = 123.4, oz = -56.7;
        for (int k = 0; k < LEVELS - 1; k++) {
            for (int i = -5; i <= 5; i++) {
                for (int j = -5; j <= 5; j++) {
                    float left = place(i, j, k, 1f, ox, oz, a);
                    if (heir(i, j)) {
                        float whole = place(i >> 1, j >> 1, k + 1, 0f, ox, oz, b);
                        if (left != 1f || whole != 1f || Math.abs(a[0] - b[0]) > 1e-6 || Math.abs(a[1] - b[1]) > 1e-6 || Math.abs(a[2] - b[2]) > 1e-9
                                || hash(i, j, k) != hash(i >> 1, j >> 1, k + 1))
                            return "cell (" + i + ", " + j + ") at level " + k + " becomes its parent exactly";
                    } else if (left != 0f) return "cell (" + i + ", " + j + ") at level " + k + " has merged away by the next level";
                    float start = place(i, j, k, 0f, ox, oz, a);
                    rest(i, j, k, ox, oz, b);
                    if (start != 1f || a[0] != b[0] || a[1] != b[1]) return "a puff starts its step at rest";
                }
            }
        }
        return null;
    }
}
