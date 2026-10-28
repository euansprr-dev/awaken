package dev.awaken.living.wild;

/**
 * The wildness field's arithmetic (SPEC §41.1), pure and thread-safe: how a tamer's touch fades with distance, and how the
 * land's share untouched, its rank and two scales of patchy noise make one value. Tuned on the planning prototype
 * (docs/wild-lands/proto/wild.py) over Euan's world.
 */
public final class WildField {
    private WildField() {}

    /** The fade's shape: a soft shoulder, then a long tail (1 is a plain exponential, 2 a bell). */
    public static final double SHAPE = 1.6;
    /** Each rank (E 0 to S 5) leaves untouched land this much wilder. */
    public static final double RANK_STEP = 0.035;
    /** The broad patches (a forest-sized swing) and the small ones (a copse-sized swing): cell sizes and strengths. */
    public static final int BROAD = 1100, SMALL = 260;
    public static final double BROAD_SWING = 0.32, SMALL_SWING = 0.14;
    /** A Hearth: fully tamed to 48 blocks, its touch fading over about 140 more, leaving at most a fifth of the wild. */
    public static final int HEARTH_RADIUS = 48, HEARTH_REACH = 140;
    public static final double HEARTH_STRENGTH = 0.8;
    /** Past this many reaches beyond its edge a tamer's touch is about one percent: far enough to stop looking. */
    public static final double REACHES = 2.5;

    /**
     * The share of the land a tamer leaves untouched at distance {@code d} from its centre: none of it (times
     * {@code strength}) inside {@code radius}, fading out over {@code reach} beyond.
     */
    public static double touch(double d, double radius, double reach, double strength) {
        double t = Math.max(0, d - radius) / reach;
        return 1 - strength * Math.exp(-Math.pow(t, SHAPE));
    }

    /** As {@link #touch} at full strength. */
    public static double touch(double d, double radius, double reach) {
        return touch(d, radius, reach, 1.0);
    }

    /**
     * The land's wildness from its untouched share {@code keep} (0..1), its rank (0..5) and the world's patches: tamed land
     * stays tamed (the noise scales with the root of {@code keep}), open land swings by forests and copses.
     */
    public static double combine(double keep, int rank, long seed, int x, int z) {
        double root = Math.sqrt(Math.max(0, keep));
        double w = keep + RANK_STEP * rank * keep
                + (noise(seed ^ 0x57_11D0L, x, z, BROAD) - 0.5) * BROAD_SWING * root
                + (noise(seed ^ 0x57_11D1L, x, z, SMALL) - 0.5) * SMALL_SWING * root;
        return Math.clamp(w, 0.0, 1.0);
    }

    /** Smooth value noise in 0..1 on a lattice of {@code cell} blocks. */
    public static double noise(long seed, int x, int z, int cell) {
        int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
        double fx = smooth((x - (double) gx * cell) / cell), fz = smooth((z - (double) gz * cell) / cell);
        double a = lattice(seed, gx, gz), b = lattice(seed, gx + 1, gz);
        double c = lattice(seed, gx, gz + 1), d = lattice(seed, gx + 1, gz + 1);
        double top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lattice(long seed, int gx, int gz) {
        long h = mix(seed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL));
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
