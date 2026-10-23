package dev.awaken.gates.world;

/** Stateless deterministic hashing and value noise. Safe on any thread; no RandomSource needed. */
public final class Hash {
    private Hash() {}

    public static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static long of(long seed, int a, int b) {
        return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL));
    }

    public static long of(long seed, int a, int b, int c) {
        return mix(of(seed, a, b) + c * 0x165667B19E3779F9L);
    }

    /** Uniform double in [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double unit(long seed, int a, int b) {
        return unit(of(seed, a, b));
    }

    public static int range(long seed, int a, int b, int n) {
        return (int) Math.floorMod(of(seed, a, b), (long) n);
    }

    /** Smooth 2D value noise in [0,1] with the given cell size in blocks. */
    public static double noise(long seed, double x, double z, double cell) {
        double fx = x / cell, fz = z / cell;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = unit(seed, ix, iz), b = unit(seed, ix + 1, iz);
        double c = unit(seed, ix, iz + 1), d = unit(seed, ix + 1, iz + 1);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    public static double fbm(long seed, double x, double z, double cell) {
        return noise(seed, x, z, cell) * 0.6 + noise(seed ^ 0x5DEECE66DL, x, z, cell / 2) * 0.3
                + noise(seed ^ 0xB5297A4DL, x, z, cell / 4) * 0.1;
    }
}
