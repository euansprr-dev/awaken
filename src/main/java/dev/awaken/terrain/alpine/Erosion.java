package dev.awaken.terrain.alpine;

/**
 * Alpine erosion (SPEC §15, Far Sight 2): gullies, spurs and ridgelines cut down every mountain slope, as a height
 * change per column. Each octave lays stripes that run straight downhill (their phase varies across the slope), blends
 * them over a grid of jittered cells so they curve and break like real drainage, and feeds its own slope into the next
 * octave, so finer gullies run down the sides of the coarser ridges and the pattern branches. Flats get none (no
 * downhill to follow), and the caller masks it to high ground.
 *
 * <p>Pure float maths with its own cosine and hash, so the chunk generator, Far Sight's predictor and its shaders
 * ({@code farsight_erosion.glsl}, the same formulas) agree, and every machine makes the same land. One call per column:
 * it never depends on y, and it allocates nothing (the caller's array holds the result and the scratch). The idea
 * follows Rune Skovbo Johansen's erosion filter (blog.runevision.com, 2026); this is our own implementation.
 */
public final class Erosion {
    private Erosion() {}

    /**
     * Octaves, the coarsest's cell (blocks) and height (blocks). Each next octave halves the cell and keeps
     * {@link #GAIN} of the height, so finer gullies are gentler than coarse ones (a stripe's steepest slope is
     * height × 2π / cell: about 0.98 for the first octave, 0.24 for the fifth).
     */
    public static final int OCTAVES = 5;
    public static final float CELL = 256f, HEIGHT = 40f, GAIN = 0.38f;
    /** How far each octave cuts below the slope it starts from: gullies are carved more than ridges are raised. */
    public static final float CARVE = 0.28f;
    /** Slopes (rise per block) over which an octave fades in: flats have no downhill to follow. */
    public static final float FLAT = 0.04f, STEEP = 0.45f;
    /** How much harder a stripe's low half is pulled down: narrow V gullies between rounded spurs. */
    public static final float GULLY = 0.6f;
    /** How much of the coarser octaves' slope a finer octave follows: all of it swirls, none of it never branches. */
    public static final float FOLLOW = 0.4f;
    /** The size {@link #erode}'s array must have: three results and three scratch values. */
    public static final int OUT = 6;

    /**
     * The height change at (x, z) for ground whose slope there is (gx, gz) (blocks of rise per block), times
     * {@code strength} (0 none, 1 full). Writes the change into {@code out[0]} and its gradient into {@code out[1..2]};
     * {@code out[3..5]} is scratch. Octaves finer than {@code finestCell} blocks are left out (a caller that can't show
     * them, such as a coarse far ring).
     */
    public static void erode(float x, float z, float gx, float gz, float strength, float finestCell, float[] out) {
        float dh = 0f, dx = 0f, dz = 0f;
        if (strength > 0f) {
            float cell = CELL, amp = HEIGHT * strength;
            for (int o = 0; o < OCTAVES && cell >= finestCell; o++, cell *= 0.5f, amp *= GAIN) {
                float sx = gx + FOLLOW * dx, sz = gz + FOLLOW * dz;
                float slope = (float) Math.sqrt(sx * sx + sz * sz);
                float fade = smooth(FLAT, STEEP, slope);
                if (fade <= 0f) continue;
                // across the slope: the stripe's phase changes along it, so lines of equal phase run downhill
                float px = -sz / slope, pz = sx / slope;
                octave(x / cell, z / cell, px, pz, o, out);
                float a = amp * fade, v = out[3];
                // profile: rounded spurs, narrow V gullies (the stripe's low half is pulled down harder)
                float k = v < 0f ? 1f - GULLY * v : 1f;
                dh += a * (v * k - CARVE);
                float slopeOfProfile = v < 0f ? 1f - 2f * GULLY * v : 1f;
                float d = a * slopeOfProfile / cell;
                dx += d * out[4];
                dz += d * out[5];
            }
        }
        out[0] = dh;
        out[1] = dx;
        out[2] = dz;
    }

    /**
     * One octave at cell coordinates (u, w), stripes across (px, pz): the blended stripe value (-1..1) into
     * {@code out[3]} and its gradient in cell units into {@code out[4..5]}. The 3×3 cells around the point each hold a
     * pivot (jittered within the middle half of the cell) and a phase; their stripes are blended with a smooth kernel of
     * radius {@code 1.25} cells, which always reaches a pivot and never one outside the 3×3 (no seams).
     */
    private static final float R2 = 1.5625f;

    static void octave(float u, float w, float px, float pz, int octave, float[] out) {
        int cu = floor(u), cw = floor(w);
        float sum = 0f, val = 0f, gu = 0f, gw = 0f;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                int ci = cu + i, cj = cw + j;
                int h = hash(ci, cj, octave);
                float pu = ci + 0.25f + 0.5f * unit(h), pw = cj + 0.25f + 0.5f * unit(h * 0x2C1B3C6D);
                float du = u - pu, dw = w - pw;
                float d2 = du * du + dw * dw;
                if (d2 >= R2) continue;
                float k = 1f - d2 / R2;
                k *= k * k;
                float phase = du * px + dw * pz;
                float c = cosTurns(phase), s = sinTurns(phase);
                sum += k;
                val += k * c;
                // the stripe's own slope (the kernel's is left out: it only bends the blend)
                float ds = -6.2831855f * s * k;
                gu += ds * px;
                gw += ds * pz;
            }
        }
        if (sum <= 1e-6f) {
            out[3] = out[4] = out[5] = 0f;
            return;
        }
        out[3] = val / sum;
        out[4] = gu / sum;
        out[5] = gw / sum;
    }

    /** cos(2πt), by a polynomial on a quarter turn: the same bits on every machine. */
    public static float cosTurns(float t) {
        float f = t - (float) Math.floor(t);           // 0..1
        float x = f < 0.5f ? f : 1f - f;               // cos is even about 0.5: 0..0.5
        boolean flip = x > 0.25f;
        if (flip) x = 0.5f - x;                        // 0..0.25, cos(2π(0.5-x)) = -cos(2πx)
        float a = x * 6.2831855f, a2 = a * a;
        float c = 1f + a2 * (-0.5f + a2 * (1f / 24f + a2 * (-1f / 720f + a2 * (1f / 40320f))));
        return flip ? -c : c;
    }

    public static float sinTurns(float t) {
        return cosTurns(t - 0.25f);
    }

    static int hash(int x, int z, int seed) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1 ^ seed * 0x5BD1E995;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        return h ^ h >>> 15;
    }

    /** A hash's low 24 bits as 0..1. */
    static float unit(int h) {
        return (h & 0xFFFFFF) / 16777216f;
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
