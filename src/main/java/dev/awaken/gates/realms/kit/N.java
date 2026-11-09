package dev.awaken.gates.realms.kit;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** The numbers every Realm is drawn with (SPEC §50.7): noise, curves, a repeatable random, a hex lattice. */
public final class N {
    private N() {}

    public static SimplexNoise noise(long seed) {
        return new SimplexNoise(new XoroshiroRandomSource(seed));
    }

    public static double fbm(SimplexNoise n, double x, double z, double f, int octaves) {
        double a = 1, s = 0, t = 0;
        for (int i = 0; i < octaves; i++) {
            s += a * n.get(x * f, z * f);
            t += a;
            a *= 0.5;
            f *= 2;
        }
        return s / t;
    }

    public static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double sq(double a) {
        return a * a;
    }

    public static long hash(long a, long b, long salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ h >>> 32;
    }

    /** 0..1, the same every time for the same three numbers. */
    public static double rnd(long a, long b, long salt) {
        return (hash(a, b, salt) >>> 12) * 0x1p-52;
    }

    private static final double S3 = Math.sqrt(3);

    public record Hex(int q, int r, double cx, double cz, double edge) {}

    /** The cell of a pointy-top hex lattice of circumradius {@code s}; edge is 0 at its middle and 1 on its side. */
    public static Hex hex(double x, double z, double s) {
        double fq = (S3 / 3 * x - z / 3) / s, fr = (2.0 / 3 * z) / s, fs = -fq - fr;
        long q = Math.round(fq), r = Math.round(fr), k = Math.round(fs);
        double dq = Math.abs(q - fq), dr = Math.abs(r - fr), ds = Math.abs(k - fs);
        if (dq > dr && dq > ds) q = -r - k;
        else if (dr > ds) r = -q - k;
        double cx = s * S3 * (q + r / 2.0), cz = s * 1.5 * r;
        double dx = x - cx, dz = z - cz;
        double d = Math.max(Math.abs(dx), Math.max(Math.abs(0.5 * dx + 0.866 * dz), Math.abs(-0.5 * dx + 0.866 * dz)));
        return new Hex((int) q, (int) r, cx, cz, d / (s * 0.866));
    }

    /** 0 at the middle, 1 on a flat side of a hexagon of apothem 1. */
    public static double hexMetric(double dx, double dz) {
        return Math.max(Math.abs(dz), Math.max(Math.abs(0.866 * dx + 0.5 * dz), Math.abs(-0.866 * dx + 0.5 * dz)));
    }
}
