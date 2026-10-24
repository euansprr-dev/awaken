package dev.awaken.farsight;

import dev.awaken.Awaken;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The worker threads that predict far terrain (SPEC §20): a few low-priority daemon threads, never the server tick or
 * the render thread. Jobs are patches, served in priority order (the caller's number: coarsest ring first, then
 * nearest), and a job whose patch has left its window by the time a worker reaches it is dropped unsampled.
 * Finished patches wait in {@link #done} for the owner to drain; their buffers are pooled.
 */
public final class FarWorkers {
    /** One patch's texels and where they go: predicted, or from the real chunks seen there ({@code generated}). */
    public static final class Patch {
        public int ring, px, pz, epoch, range;
        public boolean generated;
        public final int[] height = new int[Rings.PATCH * Rings.PATCH], colour = new int[Rings.PATCH * Rings.PATCH];
    }

    /** Whether a patch is still in its window for that epoch. Called on the workers, so it must be cheap and thread-safe. */
    @FunctionalInterface
    public interface Wanted {
        boolean wanted(int ring, int px, int pz, int epoch);
    }

    private record Job(ServerLevel level, int ring, int px, int pz, int epoch, float priority, Wanted still, boolean generated) {}

    private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>(512, Comparator.comparingDouble(Job::priority));
    private final ConcurrentLinkedQueue<Patch> done = new ConcurrentLinkedQueue<>(), free = new ConcurrentLinkedQueue<>();
    /** Columns sampled and the time it took, per kind (estimate, exact), for the log. */
    public final AtomicLong estimateColumns = new AtomicLong(), estimateNanos = new AtomicLong(), exactColumns = new AtomicLong(),
            exactNanos = new AtomicLong();
    private final int threads;
    private boolean started;

    public FarWorkers(int threads) {
        this.threads = threads;
    }

    /** About a third of the cores, at most three: the integrated server and the game keep the rest. */
    public static int defaultThreads() {
        return Math.clamp(Runtime.getRuntime().availableProcessors() / 3, 1, 3);
    }

    /** Asks for patch (px, pz) of ring {@code ring}; lower {@code priority} first. */
    public void submit(ServerLevel level, int ring, int px, int pz, int epoch, float priority, Wanted still) {
        if (!started) start();
        queue.add(new Job(level, ring, px, pz, epoch, priority, still, false));
    }

    /** Asks for the generated layer of patch (px, pz): what the real chunks seen there show (a {@link FarStore} read). */
    public void submitGenerated(ServerLevel level, int ring, int px, int pz, int epoch, float priority, Wanted still) {
        if (!started) start();
        queue.add(new Job(level, ring, px, pz, epoch, priority, still, true));
    }

    /** Drops every queued job (a new world, a new dimension, far view turned off). */
    public void clear() {
        queue.clear();
        Patch p;
        while ((p = done.poll()) != null) free.add(p);
    }

    public int queued() {
        return queue.size();
    }

    /** The next finished patch, or null. Hand it back with {@link #recycle} once uploaded. */
    public Patch poll() {
        return done.poll();
    }

    public void recycle(Patch p) {
        free.add(p);
    }

    private synchronized void start() {
        if (started) return;
        started = true;
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::run, "Awaken Far Sight #" + i);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        Awaken.LOG.info("[farsight] {} worker threads", threads);
    }

    private void run() {
        ServerLevel bound = null;
        FarColumns columns = null;
        while (true) {
            Job job;
            try {
                job = queue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (job == null) {
                // idle: let go of the level so a closed world can be collected
                bound = null;
                columns = null;
                continue;
            }
            if (!job.still().wanted(job.ring(), job.px(), job.pz(), job.epoch())) continue;
            try {
                Patch p = free.poll();
                if (p == null) p = new Patch();
                p.ring = job.ring();
                p.px = job.px();
                p.pz = job.pz();
                p.epoch = job.epoch();
                p.generated = job.generated();
                if (job.generated()) {
                    FarStore store = FarStore.peek(job.level());
                    p.range = store == null ? -1 : store.patch(job.ring(), job.px(), job.pz(), p.height, p.colour);
                    done.add(p);
                    continue;
                }
                if (job.level() != bound) {
                    bound = job.level();
                    columns = FarColumns.of(bound);
                }
                long t0 = System.nanoTime();
                p.range = columns.patch(job.ring(), job.px(), job.pz(), p.height, p.colour);
                long dt = System.nanoTime() - t0;
                boolean exact = job.ring() < Rings.EXACT_RINGS;
                (exact ? exactColumns : estimateColumns).addAndGet(Rings.PATCH * Rings.PATCH);
                (exact ? exactNanos : estimateNanos).addAndGet(dt);
                done.add(p);
            } catch (RuntimeException e) {
                // a generator that can't be sampled off-thread: leave the patch unknown, keep the worker alive
                Awaken.LOG.warn("[farsight] patch {} {},{} failed: {}", job.ring(), job.px(), job.pz(), e.toString());
            }
        }
    }
}
