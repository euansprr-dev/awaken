package dev.awaken.citizens;

import java.util.Arrays;
import java.util.Locale;

/**
 * Per-tick timings of the ledger, the bodies (every body's tick plus the embodiment pass) and the whole
 * server tick. A rolling minute for the log line, and a labelled window the tour opens and closes around a scene.
 * Everything is preallocated; only the summaries format strings.
 */
final class CrowdStats {
    static final int RING = 1200, WINDOW = 32000;

    private final long[] rLedger = new long[RING], rBodies = new long[RING], rManager = new long[RING], rMspt = new long[RING];
    private final int[] rN = new int[RING];
    private final long[] rPath = new long[RING];
    private final boolean[] rGc = new boolean[RING];
    private int rPos, rFill;

    private final long[] wLedger = new long[WINDOW], wBodies = new long[WINDOW], wManager = new long[WINDOW], wMspt = new long[WINDOW];
    private final int[] wN = new int[WINDOW];
    private final long[] wPath = new long[WINDOW];
    private final boolean[] wGc = new boolean[WINDOW];
    private int wFill;
    String windowLabel;

    private final long[] sort = new long[WINDOW];
    private final long[] clean = new long[WINDOW];

    void record(long ledger, long bodies, long manager, long mspt, int n, long path, boolean gc) {
        rPath[rPos] = path;
        rGc[rPos] = gc;
        rLedger[rPos] = ledger;
        rBodies[rPos] = bodies;
        rManager[rPos] = manager;
        rMspt[rPos] = mspt;
        rN[rPos] = n;
        rPos = (rPos + 1) % RING;
        if (rFill < RING) rFill++;
        if (windowLabel != null && wFill < WINDOW) {
            wLedger[wFill] = ledger;
            wBodies[wFill] = bodies;
            wManager[wFill] = manager;
            wMspt[wFill] = mspt;
            wN[wFill] = n;
            wPath[wFill] = path;
            wGc[wFill] = gc;
            wFill++;
        }
    }

    void startWindow(String label) {
        windowLabel = label;
        wFill = 0;
    }

    String endWindow() {
        String s = windowSummary();
        windowLabel = null;
        return s;
    }

    String ringSummary() {
        return summary("last " + rFill + "t", rLedger, rBodies, rManager, rMspt, rN, rPath, rGc, rFill);
    }

    String windowSummary() {
        return summary("window " + windowLabel + " " + wFill + "t", wLedger, wBodies, wManager, wMspt, wN, wPath, wGc, wFill);
    }

    private String summary(String what, long[] ledger, long[] bodies, long[] manager, long[] mspt, int[] n, long[] path, boolean[] gc, int len) {
        if (len == 0) return what + " (empty)";
        long nSum = 0;
        int nMax = 0, gcTicks = 0, pathTicks = 0, cleanLen = 0;
        for (int i = 0; i < len; i++) {
            nSum += n[i];
            nMax = Math.max(nMax, n[i]);
            if (gc[i]) gcTicks++;
            if (path[i] > 0) pathTicks++;
            // bodies on ticks with no GC pause and no vanilla path request: the steady cost of walking
            if (!gc[i] && path[i] == 0) clean[cleanLen++] = bodies[i];
        }
        return String.format(Locale.ROOT, "%s | load=%.1f | ledger %s | bodies n=%.1f max=%d %s | bodies clean(%d t) %s | gcTicks=%d pathTicks=%d path %s | manager %s | mspt %s", what,
                java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),
                pct(ledger, len), nSum / (double) len, nMax, pct(bodies, len), cleanLen, cleanLen == 0 ? "-" : pct(clean, cleanLen),
                gcTicks, pathTicks, pct(path, len), pct(manager, len), pct(mspt, len));
    }

    private String pct(long[] a, int len) {
        System.arraycopy(a, 0, sort, 0, len);
        Arrays.sort(sort, 0, len);
        long sum = 0;
        for (int i = 0; i < len; i++) sum += sort[i];
        return String.format(Locale.ROOT, "p50=%.3f p95=%.3f p99=%.3f max=%.3f avg=%.3fms", sort[len / 2] / 1e6,
                sort[Math.min(len - 1, (int) (len * 0.95))] / 1e6, sort[Math.min(len - 1, (int) (len * 0.99))] / 1e6,
                sort[len - 1] / 1e6, sum / (double) len / 1e6);
    }
}
