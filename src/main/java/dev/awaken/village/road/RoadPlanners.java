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
 * The road planners (SPEC §28.9, Cost): a few minimum-priority threads (half the cores, one to six, always two cores
 * left for the game) that make the network's pieces before anyone waits for them, the most urgent job first: what world
 * generation is waiting for, then the towns' and wayhouses' sites the server thread's searches asked for, then what lies
 * ahead of the players, nearest first (held back while the server is late with a tick). While new land is coming in
 * round a player ({@code LandFirst}) only the jobs for that land run, on every planner (world generation's own and the
 * lattice chunks it's about to reach): the rest are held until it's in, a minute from their ask at most. Then a search
 * the server thread asked for (a region's sites) runs whatever the land does, and a piece ahead of the players is let
 * go: nobody waits on it, and the next pass asks for it again when it's wanted, as the land's own once a player nears.
 * A job only asks for a piece (a plan, a hub, a road, a lattice chunk's legs): each piece is still made once by
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
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.clamp(Integer.getInteger("awaken.roadPlanners", Math.min(cores / 2, cores - 2)), 1, 6);
    }

    /** The longest a job ahead of the players holds back for a late tick (it runs then anyway: the queue keeps moving). */
    static final long HOLD_NS = 1_000_000_000L;

    private static synchronized ThreadPoolExecutor pool() {
        if (pool == null) {
            int n = threads();
            AtomicInteger k = new AtomicInteger();
            pool = new ThreadPoolExecutor(n, n, 0, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), r -> new Planner(r, k.incrementAndGet()));
        }
        return pool;
    }

    /**
     * Queues a job unless the same one is queued already at least as urgently ({@code order} ranks it within its tier,
     * lowest first; ahead of the players it's the lattice chunk's squared distance in chunks, times four); whether it was.
     */
    static boolean job(String key, int tier, long order, Runnable work) {
        long rank = ((long) tier << 56) | Math.clamp(order, 0, (1L << 56) - 1);
        boolean[] mine = new boolean[1];
        PENDING.compute(key, (k, had) -> {
            if (had != null && had <= rank) return had;
            mine[0] = true;
            return rank;
        });
        if (!mine[0]) return false;
        // what world generation waits for, or a piece of a lattice chunk the land coming in will reach in a moment
        boolean forLand = tier == URGENT || tier == AHEAD && order / 4 <= front;
        long seq = SEQ.incrementAndGet(), asked = System.nanoTime();
        Job[] self = new Job[1];
        boolean[] due = {false};
        self[0] = new Job(rank, seq, () -> {
            // a more urgent copy of this job was queued since (a player came nearer) or has run: that one does the work
            Long now = PENDING.get(key);
            if (now == null || now != rank) return;
            // land comes first (SPEC §28.9, Cost): while new land is coming in round a player, a job that isn't for that
            // land waits until it's in (Euan's Mac, 12 Nov: five planners at 4.6 cores while he flew), and the ones that
            // are have every planner: world generation waits for them (holding the near ones left 15 s in 30 of road
            // starts laying legs itself, run 43209363904; two at a time, Euan's new world waited almost two minutes)
            // held a minute from its ask at most (release): a planner that takes it up later runs it
            if (!forLand && !due[0] && dev.awaken.core.api.LandFirst.streaming() && System.nanoTime() - asked < dev.awaken.core.api.LandFirst.MOST_NS) {
                DEFERRED.incrementAndGet();
                HELD.add(new Held(self[0], key, rank, asked, tier == SOON, due));
                return;
            }
            PENDING.remove(key, rank);
            // ahead of the players, a job starts between the server's ticks or within a late one's first 40 ms
            if (tier == AHEAD) for (long t0 = System.nanoTime(); RoadTimer.serverLate() && System.nanoTime() - t0 < HOLD_NS; ) LockSupport.parkNanos(5_000_000L);
            try {
                work.run();
            } catch (RuntimeException e) {
                Awaken.LOG.error("[road] planning {} failed", key, e);
            }
        });
        pool().execute(self[0]);
        return true;
    }

    /** Jobs held while land came in (the timer's line). */
    static final AtomicLong DEFERRED = new AtomicLong();
    /** Jobs held a minute while land came in, then let go (the timer's line): pieces ahead of the players nobody waited on. */
    static final AtomicLong LET_GO = new AtomicLong();

    /** A job held while land comes in: its key and rank (to let it go), when it was asked for, whether it's a search. */
    private record Held(Job job, String key, long rank, long asked, boolean search, boolean[] due) implements Comparable<Held> {
        @Override
        public int compareTo(Held o) {
            return Long.compare(asked, o.asked);
        }
    }

    /** Jobs held while land comes in, the longest asked for first ({@link #release}). */
    private static final PriorityBlockingQueue<Held> HELD = new PriorityBlockingQueue<>();
    /**
     * How near a player (squared, in chunks) a lattice chunk is for the land coming in: the ring world generation makes
     * structure starts in round the view distance, and one lattice spacing past it (set each pass, {@link #front}).
     */
    private static volatile long front = (12L + net.minecraft.server.level.ChunkLevel.RADIUS_AROUND_FULL_CHUNK + Road.LATTICE)
            * (12L + net.minecraft.server.level.ChunkLevel.RADIUS_AROUND_FULL_CHUNK + Road.LATTICE);

    /** The server's view distance, for the land coming in round a player. */
    static void front(int viewDistance) {
        long r = viewDistance + net.minecraft.server.level.ChunkLevel.RADIUS_AROUND_FULL_CHUNK + Road.LATTICE;
        front = r * r;
    }

    /** (Each server tick; nothing to do most) the held jobs whose time has come. */
    static void release() {
        if (!HELD.isEmpty()) release(System.nanoTime());
    }

    /**
     * Once the land is in, every job held while it came is queued again. While it still comes, the ones asked for a minute
     * ago or more: a search the server thread asked for runs then whatever the land does (SPEC §12: nothing waits on land
     * first forever), and a piece ahead of the players is let go, to be asked for again when it's wanted. {@code now} is
     * {@code System.nanoTime()} (the test passes a minute on).
     */
    static void release(long now) {
        boolean coming = dev.awaken.core.api.LandFirst.streaming();
        ThreadPoolExecutor p = pool;
        for (Held h; (h = HELD.peek()) != null && (!coming || now - h.asked() >= dev.awaken.core.api.LandFirst.MOST_NS); ) {
            Held x = HELD.poll(); // the head, or one asked for still earlier
            if (x == null) return;
            if (coming && !x.search()) {
                PENDING.remove(x.key(), x.rank());
                LET_GO.incrementAndGet();
                continue;
            }
            x.due()[0] = coming;
            if (p == null || p.isShutdown()) continue; // the server stopped: they go with it
            try {
                p.execute(x.job());
            } catch (java.util.concurrent.RejectedExecutionException e) {
                HELD.clear();
                return;
            }
        }
    }

    /**
     * World generation's own waits, set aside (the Turbo backstop {@code RoadStarts}): one thread, as the level's worldgen
     * executor is one, laying a lattice chunk's legs as that executor did inline (fanning its pieces out to the planners
     * first), so the roads come as fast as without it while the executor generates the other chunks.
     */
    private static @Nullable ThreadPoolExecutor starts;

    static synchronized void forWorldgen(Runnable work) {
        if (starts == null) starts = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Awaken road starts");
            t.setDaemon(true);
            return t;
        });
        starts.execute(work);
    }

    static boolean pending(String key) {
        return PENDING.containsKey(key);
    }

    /** Whether a job is held while land comes in (the test). */
    static boolean held(String key) {
        for (Held x : HELD) if (x.key().equals(key)) return true;
        return false;
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
        HELD.clear();
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
        if (starts != null) starts.shutdownNow();
        starts = null;
        PENDING.clear();
        HELD.clear();
    }
}
