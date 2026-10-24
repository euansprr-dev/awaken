package dev.awaken.compat;

/**
 * The last minute of frame times for the Look switch's frame-time line. Recording a frame stores one int in a ring
 * (no allocation); percentiles are read from a histogram in 0.1 ms steps, filled from the ring only when asked (the
 * screen asks twice a second). Plain Java, so the GameTests check it without a client.
 */
public final class FrameStats {
    /** Histogram resolution: 0.1 ms; the last bin holds everything slower than {@link #BINS} / 10 ms. */
    private static final int BINS = 2000;

    private final int[] micros;
    private final int[] bins = new int[BINS + 1];
    private int head, count;

    public FrameStats(int capacity) {
        micros = new int[capacity];
    }

    /** One frame took this long. */
    public void record(long nanos) {
        micros[head] = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, nanos / 1000L));
        head = (head + 1) % micros.length;
        if (count < micros.length) count++;
    }

    public void clear() {
        head = 0;
        count = 0;
    }

    /**
     * p50 and p99 over the newest frames that together last at most {@code windowNanos}: {@code out[0]} p50 ms,
     * {@code out[1]} p99 ms, {@code out[2]} seconds covered, {@code out[3]} frames. False (out untouched) while fewer
     * than {@code minFrames} frames are in the window.
     */
    public boolean percentiles(long windowNanos, int minFrames, float[] out) {
        java.util.Arrays.fill(bins, 0);
        long budget = windowNanos / 1000L, covered = 0;
        int n = 0;
        for (int k = 0; k < count; k++) {
            int us = micros[Math.floorMod(head - 1 - k, micros.length)];
            if (covered + us > budget && n > 0) break;
            covered += us;
            bins[Math.min(BINS, us / 100)]++;
            n++;
        }
        if (n < Math.max(1, minFrames)) return false;
        out[0] = rank(n, 0.50);
        out[1] = rank(n, 0.99);
        out[2] = covered / 1.0e6f;
        out[3] = n;
        return true;
    }

    /** The value at quantile q (nearest rank), in ms: the middle of its 0.1 ms bin. */
    private float rank(int n, double q) {
        int target = Math.max(1, (int) Math.ceil(q * n)), seen = 0;
        for (int b = 0; b <= BINS; b++) {
            seen += bins[b];
            if (seen >= target) return b == BINS ? BINS / 10f : (b + 0.5f) / 10f;
        }
        return BINS / 10f;
    }
}
