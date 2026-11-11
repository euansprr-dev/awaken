package dev.awaken.core.api;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Where the real game starts in a Far Landing world (SPEC §67): one landing per world, chosen from the seed when the world
 * is made, on fair ground {@link #MIN} to {@link #MAX} blocks from the city. Pure: the same seed and city give the same
 * landing on every thread and in every session (the roads find it before any chunk is made, the region map saves it).
 * A spot scores on its land (start land: forest, plains, meadow, birch, flowers, cherry; never sea, river, swamp,
 * desert, badlands, peaks, jungle, mushroom or ice), how flat it is, and water within {@link #WATER}.
 */
public final class FarLanding {
    private FarLanding() {}

    /** How far from the city the landing may be, and how near water counts. */
    public static final int MIN = 5000, MAX = 7000, WATER = 300;
    /** Bearings tried round the city, and the radii on each. */
    static final int BEARINGS = 48;
    static final int[] RADII = {5000, 5500, 6000, 6500, 7000};
    /** How far apart the flatness points are, and how many of the best by land are measured for it. */
    static final int FLAT_STEP = 48, MEASURED = 28;

    /** What the finder reads of the land: the biome's vanilla name at a column, the ground's top, the sea's level. */
    public interface Sampler {
        String biome(int x, int z);

        int top(int x, int z);

        int seaLevel();
    }

    private record Found(long key, int x, int z) {}

    private static volatile @Nullable Found cached;

    /** The landing for this seed and city, worked out once a session (the same answer every time). */
    public static int[] of(long seed, int cityX, int cityZ, Sampler s) {
        long key = key(seed, cityX, cityZ);
        Found f = cached;
        if (f != null && f.key == key) return new int[]{f.x, f.z};
        synchronized (FarLanding.class) {
            f = cached;
            if (f != null && f.key == key) return new int[]{f.x, f.z};
            int[] at = find(seed, cityX, cityZ, s);
            cached = new Found(key, at[0], at[1]);
            return at;
        }
    }

    /** The landing already worked out this session for this seed and city, or null. */
    public static int @Nullable [] cached(long seed, int cityX, int cityZ) {
        Found f = cached;
        return f != null && f.key == key(seed, cityX, cityZ) ? new int[]{f.x, f.z} : null;
    }

    /** Tests: forget the session's landing. */
    public static void forget() {
        cached = null;
    }

    private static long key(long seed, int x, int z) {
        return mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL);
    }

    /** The landing: the best-scored spot of the ring (ties go to the seed's own order). */
    public static int[] find(long seed, int cityX, int cityZ, Sampler s) {
        int n = BEARINGS * RADII.length;
        double[] land = new double[n];
        int[] xs = new int[n], zs = new int[n];
        double turn = unit(mix(seed ^ 0x1A2D_1D6EL)) * Math.PI * 2;
        for (int b = 0; b < BEARINGS; b++) {
            double a = turn + b * Math.PI * 2 / BEARINGS;
            for (int r = 0; r < RADII.length; r++) {
                int i = b * RADII.length + r;
                xs[i] = cityX + (int) Math.round(Math.sin(a) * RADII[r]);
                zs[i] = cityZ - (int) Math.round(Math.cos(a) * RADII[r]);
                land[i] = landScore(s.biome(xs[i], zs[i]));
            }
        }
        // the best by land first; flatness and water are measured only for those
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (u, v) -> {
            int c = Double.compare(land[v], land[u]);
            return c != 0 ? c : Long.compare(mix(seed + u), mix(seed + v));
        });
        int best = order[0];
        double top = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < Math.min(MEASURED, n); k++) {
            int i = order[k];
            if (land[i] < 0 && top > Double.NEGATIVE_INFINITY) break;
            double score = land[i] + flatScore(s, xs[i], zs[i]) + waterScore(s, xs[i], zs[i]) + 0.01 * unit(mix(seed ^ i));
            if (score > top) {
                top = score;
                best = i;
            }
        }
        return new int[]{xs[best], zs[best]};
    }

    /** A start's land: 3 for the gentlest (plains, meadow, flowers, cherry, birch), 2 for forest, below zero for land nobody starts on. */
    static double landScore(String biome) {
        String b = biome.toLowerCase(Locale.ROOT);
        if (b.contains("ocean") || b.contains("river") || b.contains("beach") || b.contains("shore") || b.contains("swamp")
                || b.contains("mangrove") || b.contains("desert") || b.contains("badlands") || b.contains("peaks")
                || b.contains("jungle") || b.contains("bamboo") || b.contains("mushroom") || b.contains("ice") || b.contains("frozen")
                || b.contains("deep_dark") || b.contains("dripstone") || b.contains("lush")) return -5;
        if (b.contains("snowy") || b.contains("slopes") || b.contains("windswept") || b.contains("stony") || b.contains("grove")
                || b.contains("dark_forest") || b.contains("pale_garden")) return -2;
        if (b.equals("plains") || b.equals("sunflower_plains") || b.equals("meadow") || b.equals("flower_forest")
                || b.equals("cherry_grove") || b.contains("birch")) return 3;
        if (b.contains("forest") || b.contains("taiga") || b.contains("savanna") || b.contains("plains")) return 2;
        return 0;
    }

    /** Flat ground scores up to 2: the spread of the ground over a cross of nine points, and never under the sea. */
    static double flatScore(Sampler s, int x, int z) {
        int sea = s.seaLevel();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int y = s.top(x + dx * FLAT_STEP, z + dz * FLAT_STEP);
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        if (lo <= sea) return -3;
        return 2 - Math.min(4, (hi - lo) / 6.0);
    }

    /** A river or lake within {@link #WATER} blocks: 1. */
    static double waterScore(Sampler s, int x, int z) {
        for (int r = 150; r <= WATER; r += 150)
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                String b = s.biome(x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r));
                if (b.contains("river") || b.contains("lake")) return 1;
            }
        return 0;
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
