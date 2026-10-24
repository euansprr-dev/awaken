package dev.awaken.city;

import java.util.Arrays;
import java.util.Locale;

/** Per-chunk paint and write timings (nanoseconds), safe to add to from worldgen threads. */
final class CityStats {
    synchronized void clear() {
        n = 0;
        blocks = 0;
    }

    private long[] paint = new long[4096], write = new long[4096];
    private int n;
    private long blocks;

    synchronized void add(long paintNs, long writeNs, int written) {
        if (n == paint.length) {
            paint = Arrays.copyOf(paint, n * 2);
            write = Arrays.copyOf(write, n * 2);
        }
        paint[n] = paintNs;
        write[n] = writeNs;
        n++;
        blocks += written;
    }

    synchronized int count() {
        return n;
    }

    synchronized String summary() {
        if (n == 0) return "no chunks";
        return String.format(Locale.ROOT, "chunks=%d paint[%s] write[%s] blocks=%d (%.0f/chunk)", n, line(paint), line(write),
                blocks, blocks / (double) n);
    }

    private String line(long[] src) {
        long[] v = Arrays.copyOf(src, n);
        Arrays.sort(v);
        long sum = 0;
        for (long x : v) sum += x;
        return String.format(Locale.ROOT, "p50=%.3fms p95=%.3fms max=%.3fms mean=%.3fms sum=%.1fms", v[n / 2] / 1e6,
                v[Math.min(n - 1, (int) (n * 0.95))] / 1e6, v[n - 1] / 1e6, sum / 1e6 / n, sum / 1e6);
    }
}
