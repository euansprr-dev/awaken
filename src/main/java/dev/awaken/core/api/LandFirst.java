package dev.awaken.core.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Land comes first (SPEC §12, §21.6, §28.9). Whether new land is still coming in round a player: a chunk inside some
 * player's view that isn't made yet, while chunks are still being made. Work of ours that can wait (roads, villages and
 * places planned ahead, the searches for a mouth, an adit, a wonder or a well, lode and sinkhole plans) waits for it
 * instead of taking cores from the generator: on Euan's Mac (12 Nov) five road planners used 4.6 cores while he flew
 * over new land, and our searches ran on the game's own worldgen threads ({@code Util.backgroundExecutor}).
 * <p>
 * The server thread counts the missing chunks twice a second (a hash lookup each: about a thousand for a view of 16);
 * any thread may ask. With no chunk made for {@link #STILL_NS} the land isn't coming in, whatever is missing (a chunk
 * waiting on something, a paused world), so nothing waits on it forever, unless the generator is waiting on work of
 * ours ({@link #atWork}: the roads of a lattice chunk it set aside). That is the land coming in too: Euan's new world of
 * 12 Nov stood 30 s at a time with no chunk made and 333 missing while it waited for its roads, and counted as still,
 * it let the road planners take up work kilometres away beside what it waited for.
 */
public final class LandFirst {
    private LandFirst() {}

    /** Ticks between counts. */
    static final int EVERY = 10;
    /** No chunk made for this long: the generator isn't busy, so nothing waits for it. */
    static final long STILL_NS = 5_000_000_000L;
    /** How often a waiting thread looks again. */
    static final long LOOK_NS = 50_000_000L;
    /** Optional work never waits longer than this: it still comes, only last. */
    public static final long MOST_NS = 60_000_000_000L;

    private static volatile boolean streaming;
    private static volatile int missing;
    private static volatile long lastMade;

    /** Whether new land is coming in round a player now (any thread). */
    public static boolean streaming() {
        return streaming;
    }

    /** The chunks inside players' views not made yet, at the last count. */
    public static int missing() {
        return missing;
    }

    private static volatile @Nullable Thread serverThread;
    private static final java.util.concurrent.atomic.LongAdder NOISE_ON_SERVER = new java.util.concurrent.atomic.LongAdder();
    private static volatile long noiseTold;
    /** Terrain noise on the server thread is told at most this often. */
    static final long NOISE_TELL_NS = 10_000_000_000L;

    /**
     * (The overworld's noise router: {@code CityBasinFunction}, which every column and chunk of the overworld's terrain
     * passes through) terrain noise sampled on this thread. On the server thread it's counted, and told with its caller
     * at most every ten seconds: a column there costs tens of milliseconds with Tectonic (each new noise chunk hashes
     * its whole density tree), and Euan's join of 12 Nov stood 32 s in one loop of them, its caller cut from the stall
     * watch's stack. Any other thread returns at once.
     */
    public static void noiseSampled() {
        if (Thread.currentThread() != serverThread) return;
        NOISE_ON_SERVER.increment();
        long now = System.nanoTime();
        if (now - noiseTold < NOISE_TELL_NS) return;
        noiseTold = now;
        long n = NOISE_ON_SERVER.sumThenReset();
        String who = StackWalker.getInstance().walk(s -> {
            var frames = s.limit(400).toList();
            StringBuilder b = new StringBuilder();
            int shown = 0;
            for (var f : frames) {
                String c = f.getClassName();
                if (!c.startsWith("dev.awaken.") || c.startsWith("dev.awaken.core.api.LandFirst") || c.startsWith("dev.awaken.terrain.city.CityBasinFunction")) continue;
                if (shown++ > 0) b.append(" < ");
                b.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(':').append(f.getLineNumber());
                if (shown == 8) break;
            }
            if (shown > 0) return b.toString();
            // none of ours: the frames nearest the tick (another mod's, or the game's own)
            int end = frames.size();
            for (int i = 0; i < frames.size(); i++)
                if (frames.get(i).getMethodName().equals("tickServer")) {
                    end = i;
                    break;
                }
            for (int i = Math.max(0, end - 10); i < end; i++) {
                var f = frames.get(i);
                String c = f.getClassName();
                if (b.length() > 0) b.append(" < ");
                b.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(':').append(f.getLineNumber());
            }
            return "none of ours: " + b;
        });
        dev.awaken.Awaken.LOG.warn("[core] terrain noise on the server thread ({} volumes since last told): {}", n, who);
    }

    private static final java.util.List<java.util.function.BooleanSupplier> AT_WORK = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * (Init) whether world generation is waiting on work of ours now (any thread): while it is and chunks in view are
     * missing, the land is coming in though no chunk is made.
     */
    public static void atWork(java.util.function.BooleanSupplier waiting) {
        AT_WORK.add(waiting);
    }

    private static boolean generatorWaits() {
        for (var w : AT_WORK) if (w.getAsBoolean()) return true;
        return false;
    }

    /** (Server tick, CoreModule) the count. */
    public static void tick(MinecraftServer server) {
        Thread self = Thread.currentThread();
        if (serverThread != self) serverThread = self; // a world opened again runs on a new server thread
        if (server.getTickCount() % EVERY != 0) return;
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.getChunkTrackingView() instanceof ChunkTrackingView.Positioned v)) continue;
            ServerChunkCache cache = p.level().getChunkSource();
            int cx = v.center().x(), cz = v.center().z(), r = v.viewDistance();
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) if (v.isInViewDistance(x, z) && cache.getChunkNow(x, z) == null) n++;
        }
        missing = n;
        streaming = n > 0 && (System.nanoTime() - lastMade < STILL_NS || generatorWaits());
    }

    /** (ChunkEvent.Load, CoreModule) a chunk was made or read: the generator is at work. */
    public static void chunkLoaded() {
        lastMade = System.nanoTime();
    }

    /** (Server stopping) nothing waits on a world that's gone. */
    public static void clear() {
        streaming = false;
        missing = 0;
        serverThread = null;
    }

    /**
     * Off the server thread: waits while new land is coming in, at most {@code mostNs}. Optional work calls this before it
     * starts. True if it waited.
     */
    public static boolean hold(long mostNs) {
        if (!streaming) return false;
        long t0 = System.nanoTime();
        while (streaming && System.nanoTime() - t0 < mostNs) LockSupport.parkNanos(LOOK_NS);
        return true;
    }

    private static @Nullable ThreadPoolExecutor later;

    /**
     * In place of {@code Util.backgroundExecutor()} for optional searches: that pool is the game's own, where world
     * generation runs, so a search there takes a generator's thread. Two threads of our own at the lowest priority, each
     * job held while land comes in (at most {@link #MOST_NS}).
     */
    public static synchronized ExecutorService later() {
        if (later == null) {
            AtomicInteger k = new AtomicInteger();
            later = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "Awaken land-first " + k.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }) {
                @Override
                protected void beforeExecute(Thread t, Runnable r) {
                    hold(MOST_NS);
                }
            };
        }
        return later;
    }
}
