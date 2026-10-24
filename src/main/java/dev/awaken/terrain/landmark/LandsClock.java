package dev.awaken.terrain.landmark;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * What the second pair's worldgen really costs while a world generates (worker threads, Distant Horizons included):
 * each feature and landmark adds its calls and nanoseconds here; {@code /awaken terrain lands2cost} prints the averages
 * and the time per generated chunk. Two adders per name, no allocation per call once a name is known.
 */
public final class LandsClock {
    private LandsClock() {}

    private record Tally(LongAdder calls, LongAdder nanos) {}

    private static final Map<String, Tally> TALLIES = new ConcurrentHashMap<>();
    /** Chunks the fault painter looked at: one per generated chunk where a highland is near, the denominator for "per chunk". */
    public static final LongAdder CHUNKS = new LongAdder();

    public static void add(String what, long nanos) {
        Tally t = TALLIES.computeIfAbsent(what, k -> new Tally(new LongAdder(), new LongAdder()));
        t.calls().increment();
        t.nanos().add(nanos);
    }

    public static String report() {
        StringBuilder b = new StringBuilder();
        for (var e : new TreeMap<>(TALLIES).entrySet()) {
            long calls = e.getValue().calls().sum(), nanos = e.getValue().nanos().sum();
            if (calls == 0) continue;
            b.append(String.format(Locale.ROOT, " %s: %d calls, %.3f ms avg, %.1f ms total;", e.getKey(), calls, nanos / 1e6 / calls, nanos / 1e6));
        }
        return b.length() == 0 ? " nothing generated yet" : b.toString();
    }

    public static void reset() {
        TALLIES.clear();
    }
}
