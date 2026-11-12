package dev.awaken.core.test;

import dev.awaken.core.api.LandFirst;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Land first never starves the work it holds (SPEC §12). A count the server thread made long ago holds nothing (it
 * stopped ticking: a GameTest's body, a paused world), and while the land keeps coming in (a flight over new ground)
 * work queued on one worker waits its span from when it was asked for: four searches asked at once all start within the
 * span, not a span after one another. The full suite of 12 Nov, run 43256513077: delve_horizon_flyover's sinkhole
 * searches, eight on one worker, waited a minute each behind a count frozen while the test's body ran.
 */
final class LandFirstTests {
    private LandFirstTests() {}

    private static final long MS = 1_000_000L;

    static void register() {
        // on its own: it says the land is coming in for the whole server, and no other test's work should wait on that
        AwakenTests.registerIsolated("core_land_first_never_starves", 200, LandFirstTests::neverStarves);
    }

    private static void neverStarves(GameTestHelper h) {
        ExecutorService one = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Awaken land-first test");
            t.setDaemon(true);
            return t;
        });
        try {
            LandFirst.testCount(true, 0);
            h.assertTrue(LandFirst.streaming(), "a count made just now: the land is coming in");
            LandFirst.testCount(true, LandFirst.STALE_NS + 1_000 * MS);
            h.assertFalse(LandFirst.streaming(), "a count from before the server thread stopped ticking: nothing is coming in");

            LandFirst.testCount(false, 0);
            long at = started(one, 1, LandFirst.MOST_NS)[0];
            h.assertTrue(at < 500 * MS, "nothing coming in: the work starts at once, not " + at / MS + " ms after its ask");

            // the count goes stale while the work waits on it: the wait ends there, not a minute on
            LandFirst.testCount(true, LandFirst.STALE_NS - 300 * MS);
            at = started(one, 1, LandFirst.MOST_NS)[0];
            h.assertTrue(at >= 200 * MS && at < 2_000 * MS, "a count gone stale ends the wait: the work started " + at / MS + " ms after its ask");

            // land that never stops coming: four asked at once on one worker, each waits its span from its ask
            LandFirst.testCount(true, 0);
            long span = 500 * MS;
            long[] four = started(one, 4, span);
            h.assertTrue(four[0] >= span - 60 * MS, "the first waited for the land: " + four[0] / MS + " ms");
            h.assertTrue(four[3] < span + 700 * MS, "the last started " + four[3] / MS + " ms after its ask, within its span of "
                    + span / MS + " ms (one span after another it would be " + 4 * span / MS + ")");
        } finally {
            one.shutdownNow();
            LandFirst.testCount(false, 0);
        }
        h.succeed();
    }

    /** {@code n} jobs asked for at once on the worker through {@link LandFirst#held}: when each started after the ask. */
    private static long[] started(ExecutorService one, int n, long span) {
        long[] at = new long[n];
        CountDownLatch done = new CountDownLatch(n);
        long asked = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int k = i;
            one.execute(LandFirst.held(span, () -> {
                at[k] = System.nanoTime() - asked;
                done.countDown();
            }));
        }
        try {
            if (!done.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("held work not done in 10 s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return at;
    }
}
