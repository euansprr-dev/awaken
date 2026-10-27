package dev.awaken.ground;

/**
 * The lawn's colour drift (docs/terrain-immersion/PLAN.md, P0): the biome's grass colour multiplied by a slow noise
 * of the world column, so a meadow has patches (deeper, warmer) instead of one flat carpet. Pure maths of (x, z), the
 * same on every client, with the hash Far Sight's shaders use ({@code farsight.glsl} {@code farHash}), so far ground
 * can lay the same patches. The amplitude is small on purpose: patches 20-30 blocks across, a few per cent of colour.
 */
public final class LawnDrift {
    private LawnDrift() {}

    /** The patches' cell size (blocks) and the texture's, and how far the colour moves at the noise's ends. */
    public static final float PATCH = 26f, GRAIN = 7f, DEEP = 0.07f, WARM = 0.09f;

    /** The noise at a column, -1 (deep, cool) to 1 (sunny, warm): patches with a finer grain on them. */
    public static float noise(int x, int z) {
        float n = 0.64f * value(x + 0.5f, z + 0.5f, PATCH, 3) + 0.36f * value(x + 0.5f, z + 0.5f, GRAIN, 8);
        return Math.clamp((n - 0.5f) * 2.4f, -1f, 1f);
    }

    /** An 0xRRGGBB colour moved by the drift at (x, z): brighter and yellower where the noise is high, deeper and bluer where low. */
    public static int apply(int rgb, int x, int z) {
        float t = noise(x, z);
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        r = clamp(r * (1f + WARM * t));
        g = clamp(g * (1f + DEEP * 0.45f * t));
        b = clamp(b * (1f - DEEP * t));
        return (rgb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Whether a colour is a lawn's green (what Far Sight's ground colours are checked by: its other grounds are left alone). */
    public static boolean greenish(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return g >= r * 1.05f && g >= b * 1.25f;
    }

    private static int clamp(float v) {
        return Math.clamp(Math.round(v), 0, 255);
    }

    /** Smooth value noise in 0..1 on a lattice of {@code cell} blocks. */
    static float value(float x, float z, float cell, int seed) {
        float u = x / cell, v = z / cell;
        int iu = (int) Math.floor(u), iv = (int) Math.floor(v);
        float fu = u - iu, fv = v - iv;
        fu = fu * fu * (3f - 2f * fu);
        fv = fv * fv * (3f - 2f * fv);
        float a = hash(iu, iv, seed), b = hash(iu + 1, iv, seed), c = hash(iu, iv + 1, seed), d = hash(iu + 1, iv + 1, seed);
        float top = a + (b - a) * fu, bottom = c + (d - c) * fu;
        return top + (bottom - top) * fv;
    }

    /** farsight.glsl's farHash: two ints to 0..1. */
    static float hash(int x, int z, int seed) {
        int h = (x * 3 + seed) * 374761393 + (z * 3 + seed) * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return Integer.toUnsignedLong(h ^ (h >>> 16)) / 4294967296f;
    }
}
