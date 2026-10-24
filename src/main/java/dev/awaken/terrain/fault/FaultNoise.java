package dev.awaken.terrain.fault;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

/**
 * Where the land is split (docs/modules/lands.md, "Shattered Highlands"). A fault runs along the zero line of the lands
 * pack's noise {@code awaken:lands/fault}; how far a column is from that line, in blocks, is the noise over the
 * steepness of its slope there ({@link #distance}), so a fault keeps its width wherever it runs (a plain threshold on
 * the noise would swell into blobs where the noise is flat). The fault is a band {@link #half} blocks either side of
 * the line, the seam {@link #SEAM_HALF} either side; where the trench noise {@code awaken:lands/fault_trench} rises the
 * band widens and opens into a trench. Everything reads the world's own noises through its {@link RandomState}, so a
 * line, a trench or a floating shard lines up wherever it is computed, without reading any neighbour chunk, and runs
 * across chunk borders whole.
 */
public final class FaultNoise {
    private FaultNoise() {}

    public static final ResourceKey<NormalNoise> FAULT = ResourceKey.create(Registries.NOISE, Awaken.id("lands/fault"));
    public static final ResourceKey<NormalNoise> TRENCH = ResourceKey.create(Registries.NOISE, Awaken.id("lands/fault_trench"));

    /** Half-width of a closed fault (faultstone), and of the seam down its middle, in blocks. */
    public static final double HALF = 1.6, SEAM_HALF = 0.55;
    /** How much wider a fault grows where it opens fully into a trench, in blocks either side. */
    public static final double WIDEN = 2.8;
    /** The trench opens where the trench noise passes this, fully by {@link #TRENCH_FROM} + 0.25, to {@link #TRENCH_DEPTH} deep. */
    public static final double TRENCH_FROM = 0.15;
    public static final int TRENCH_DEPTH = 9;
    /** No column further than this from a line is ever fault. */
    public static final double MAX_HALF = HALF + WIDEN;

    /** The world's fault noise, or null where the lands pack is not loaded (an old world: no faults anywhere). */
    public static @Nullable Noise fault(RandomState random) {
        return get(random, FAULT);
    }

    public static @Nullable Noise trench(RandomState random) {
        return get(random, TRENCH);
    }

    private static @Nullable Noise get(RandomState random, ResourceKey<NormalNoise> key) {
        try {
            return random.getOrCreateNoise(key);
        } catch (RuntimeException e) {
            return null; // no such noise in this world's registry
        }
    }

    /**
     * The fault field is the noise sampled on a {@link #CELL}-block lattice and blended bilinearly between (a quarter of
     * the noise work for the painter, and the same numbers wherever it is asked).
     */
    public static final int CELL = 2;

    /** Distance in blocks from column (x, z) to the nearest fault line: the field over its slope. */
    public static double distance(Noise fault, int x, int z) {
        int x0 = Math.floorDiv(x, CELL) * CELL, z0 = Math.floorDiv(z, CELL) * CELL;
        return distance(fault.get(x0, 0, z0), fault.get(x0 + CELL, 0, z0), fault.get(x0, 0, z0 + CELL), fault.get(x0 + CELL, 0, z0 + CELL),
                (x - x0) / (double) CELL, (z - z0) / (double) CELL);
    }

    /** The field's distance inside one lattice cell (corners a at x0 z0, b at x0+1, c at z0+1, d at both), at (fx, fz) in it. */
    public static double distance(double a, double b, double c, double d, double fx, double fz) {
        double n = (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
        double gx = ((b - a) * (1 - fz) + (d - c) * fz) / CELL;
        double gz = ((c - a) * (1 - fx) + (d - b) * fx) / CELL;
        return distance(n, gx, gz);
    }

    public static double distance(double n, double gx, double gz) {
        return Math.abs(n) / Math.max(1e-7, Math.sqrt(gx * gx + gz * gz));
    }

    /** 0..1: how far open the fault is (the trench noise past its threshold). */
    public static double open(double t) {
        return Math.max(0, Math.min(1, (t - TRENCH_FROM) / 0.25));
    }

    /** The fault's half-width where it is this far open. */
    public static double half(double open) {
        return HALF + WIDEN * open;
    }

    /** How deep the trench is at distance d from the line where the fault is this far open (0: a line in the ground). */
    public static int trenchDepth(double d, double open) {
        double h = half(open);
        if (d >= h || open <= 0) return 0;
        double a = d / h;
        // a V that rounds at the bottom: deepest on the line
        return (int) Math.round(TRENCH_DEPTH * open * (1 - a * a));
    }
}
