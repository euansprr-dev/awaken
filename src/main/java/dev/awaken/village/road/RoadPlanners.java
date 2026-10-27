package dev.awaken.village.road;

import dev.awaken.Awaken;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * The road planners (SPEC §28.9, Cost): a few minimum-priority threads (half the cores, two to six) that make the
 * network's pieces before anyone waits for them, the most urgent job first: what world generation is waiting for, then
 * the towns' and wayhouses' sites the server thread's searches asked for, then what lies ahead of the players, nearest
 * first. A job only asks for a piece (a plan, a hub, a road, a lattice chunk's legs): each piece is still made once by
 * whichever thread asks first while the others wait for it, so the network is exactly what one thread would lay, and no
 * thread ever waits for a job still queued.
 */
final class RoadPlanners {
    private RoadPlanners() {}

    /** World generation is waiting; the server thread's searches asked; ahead of the players. */
    static final int URGENT = 0, SOON = 1, AHEAD = 2;

    /** A planner's thread (world generation's threads fan their work out to the planners; the planners don't). */
    static final class Planner extends Thread {
        Planner(Runnable r, int n) {
            super(r, "Awaken road planner " + n);
            setDaemon(true);
            setPriority(MIN_PRIORITY);
        }
    }

    private record Job(long rank, long seq, Runnable work) implements Runnable, Comparable<Job> {
        @Override
        public void run() {
            work.run();
        }

        @Override
        public int compareTo(Job o) {
            return rank != o.rank ? Long.compare(rank, o.rank) : Long.compare(seq, o.seq);
        }
    }

    private static @Nullable ThreadPoolExecutor pool;
    private static final AtomicLong SEQ = new AtomicLong();
    /** Each queued job's rank by its key: the same job is queued again only when it's become more urgent. */
    private static final ConcurrentHashMap<String, Long> PENDING = new ConcurrentHashMap<>();

    static int threads() {
        return Math.clamp(Integer.getInteger("awaken.roadPlanners", Math.max(2, Runtime.getRuntime().availableProcessors() / 2)), 1, 6);
    }

    private static synchronized ThreadPoolExecutor pool() {
        if (pool == null) {
            int n = threads();
            AtomicInteger k = new AtomicInteger();
            pool = new ThreadPoolExecutor(n, n, 0, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), r -> new Planner(r, k.incrementAndGet()));
        }
        return pool;
    }

    /** Queues a job unless the same one is queued already at least as urgently ({@code order} ranks it within its tier, lowest first). */
    static void job(String key, int tier, long order, Runnable work) {
        long rank = ((long) tier << 56) | Math.clamp(order, 0, (1L << 56) - 1);
        boolean[] mine = new boolean[1];
        PENDING.compute(key, (k, had) -> {
            if (had != null && had <= rank) return had;
            mine[0] = true;
            return rank;
        });
        if (!mine[0]) return;
        pool().execute(new Job(rank, SEQ.incrementAndGet(), () -> {
            PENDING.remove(key, rank);
            try {
                work.run();
            } catch (RuntimeException e) {
                Awaken.LOG.error("[road] planning {} failed", key, e);
            }
        }));
    }

    static boolean pending(String key) {
        return PENDING.containsKey(key);
    }

    static boolean on() {
        return Thread.currentThread() instanceof Planner;
    }

    /** Jobs queued (the timer's line). */
    static int queued() {
        ThreadPoolExecutor p = pool;
        return p == null ? 0 : p.getQueue().size();
    }

    /** Drops every queued job and waits for those under way (the bench, between its runs). */
    static void settle() {
        ThreadPoolExecutor p = pool;
        PENDING.clear();
        if (p == null) return;
        p.getQueue().clear();
        for (int quiet = 0; quiet < 3; ) {
            LockSupport.parkNanos(5_000_000L);
            quiet = p.getActiveCount() == 0 ? quiet + 1 : 0;
        }
    }

    /** The planners stop with the server (jobs under way finish into the cleared caches harmlessly). */
    static synchronized void stop() {
        if (pool != null) pool.shutdownNow();
        pool = null;
        PENDING.clear();
    }
}
