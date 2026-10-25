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
        public final int[] height = new int[Rings.PATCH * Rings.PATCH], colour = new int[Rings.PATCH * Rings.PATCH],
                info = new int[Rings.PATCH * Rings.PATCH], crown = new int[Rings.PATCH * Rings.PATCH];
        /** The patch's trees ({@link FarTrees}), rings 1-4. */
        public final int[] trees = new int[FarTrees.MAX * FarTrees.INTS];
        public int treeCount;
    }

    /** Whether a patch is still in its window for that epoch. Called on the workers, so it must be cheap and thread-safe. */
    @FunctionalInterface
    public interface Wanted {
        boolean wanted(int ring, int px, int pz, int epoch);
    }

    private record Job(ServerLevel level, int ring, int px, int pz, int epoch, float priority, Wanted still, boolean generated) {}

    private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>(512, Comparator.comparingDouble(Job::priority));
    private final ConcurrentLinkedQueue<Patch> done = new ConcurrentLinkedQueue<>(), free = new ConcurrentLinkedQueue<>();
    /** Columns predicted, the time it took and the density probes it made, for the status line. */
    public final AtomicLong columnCount = new AtomicLong(), columnNanos = new AtomicLong(), probes = new AtomicLong();
    private final int threads;
    private boolean started;

    public FarWorkers(int threads) {
        this.threads = threads;
    }

    /**
     * A quarter of the cores, at most three (two on an M4): the integrated server's chunk generation and the game keep
     * the rest. macOS ignores Java thread priorities, so these compete with the render thread as equals: fewer of them
     * keep frames steady while a new world fills in (Far Sight 2's tours).
     */
    public static int defaultThreads() {
        return Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 3);
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
            Patch p = free.poll();
            if (p == null) p = new Patch();
            p.ring = job.ring();
            p.px = job.px();
            p.pz = job.pz();
            p.epoch = job.epoch();
            p.generated = job.generated();
            try {
                p.treeCount = 0;
                if (job.level() != bound) {
                    bound = job.level();
                    columns = FarColumns.of(bound);
                }
                if (job.generated()) {
                    FarStore store = FarStore.peek(job.level());
                    p.range = store == null ? -1 : store.patch(job.ring(), job.px(), job.pz(), p.height, p.colour, p.info, p.crown);
                    if (p.range >= 0 && FarTrees.plants(job.ring())) {
                        // the real chunks' trees, shaped as their biome grows them
                        int s = Rings.cellSize(job.ring());
                        for (int k = 0; k < p.crown.length; k++) {
                            if (p.height[k] == 0) continue;
                            int x = (job.px() * Rings.PATCH + (k & 15)) * s, z = (job.pz() * Rings.PATCH + (k >> 4)) * s;
                            int shape = FarPalette.look(columns.biome(x, (int) FarColumns.unpackHeight(p.height[k]), z)).shape();
                            p.crown[k] = p.crown[k] & 0xFFFFFF | shape << 24;
                        }
                        p.treeCount = FarTrees.plant(job.ring(), job.px(), job.pz(), p.height, p.colour, p.info, p.crown, columns.seaLevel(), p.trees);
                    }
                } else {
                    long t0 = System.nanoTime(), p0 = columns.probes;
                    p.range = columns.patch(job.ring(), job.px(), job.pz(), p.height, p.colour, p.info, p.crown);
                    p.treeCount = FarTrees.plant(job.ring(), job.px(), job.pz(), p.height, p.colour, p.info, p.crown, columns.seaLevel(), p.trees);
                    columnNanos.addAndGet(System.nanoTime() - t0);
                    columnCount.addAndGet(Rings.PATCH * Rings.PATCH);
                    probes.addAndGet(columns.probes - p0);
                }
            } catch (RuntimeException e) {
                // a generator that can't be sampled off-thread: hand the patch back as failed, so its slot is released
                // (asked again later) and the worker stays alive
                Awaken.LOG.warn("[farsight] patch {} {},{} failed: {}", job.ring(), job.px(), job.pz(), e.toString());
                p.range = -2;
            }
            done.add(p);
        }
    }
}
