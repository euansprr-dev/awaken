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

    /** Smooth 3D value noise in [0,1] with the given cell size in blocks (world caves, worn stone). */
    public static double noise3(long seed, double x, double y, double z, double cell) {
        double fx = x / cell, fy = y / cell, fz = z / cell;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
        double tx = fx - ix, ty = fy - iy, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        tz = tz * tz * (3 - 2 * tz);
        double c000 = unit(of(seed, ix, iy, iz)), c100 = unit(of(seed, ix + 1, iy, iz));
        double c010 = unit(of(seed, ix, iy + 1, iz)), c110 = unit(of(seed, ix + 1, iy + 1, iz));
        double c001 = unit(of(seed, ix, iy, iz + 1)), c101 = unit(of(seed, ix + 1, iy, iz + 1));
        double c011 = unit(of(seed, ix, iy + 1, iz + 1)), c111 = unit(of(seed, ix + 1, iy + 1, iz + 1));
        double x00 = c000 + (c100 - c000) * tx, x10 = c010 + (c110 - c010) * tx;
        double x01 = c001 + (c101 - c001) * tx, x11 = c011 + (c111 - c011) * tx;
        double y0 = x00 + (x10 - x00) * ty, y1 = x01 + (x11 - x01) * ty;
        return y0 + (y1 - y0) * tz;
    }

    public static double fbm(long seed, double x, double z, double cell) {
        return noise(seed, x, z, cell) * 0.6 + noise(seed ^ 0x5DEECE66DL, x, z, cell / 2) * 0.3
                + noise(seed ^ 0xB5297A4DL, x, z, cell / 4) * 0.1;
    }
}
