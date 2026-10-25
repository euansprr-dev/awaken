package dev.awaken.city;

import dev.awaken.Awaken;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

/**
 * Generates every city chunk plus a margin while the world is being created (SPEC §16.2: ≤ 30 s on a quiet machine),
 * behind the game's own loading screen, before anyone can enter. Runs on the server thread inside world creation,
 * driving the chunk system by hand: tickets for a window of chunks (nearest the centre first), each scheduled to FULL,
 * the chunk cache's main-thread queue pumped until they are done. Distant Horizons waits until it ends.
 * Afterwards generator changes can never touch this city: its chunks are on disk.
 *
 * <p>A finished chunk swaps its generation ticket for a timed hold ({@link CityModule#HOLD_TICKET}): with no spawn-chunk
 * ticket in 26.3, dropping the tickets outright unloaded and saved all ~1,700 chunks in the idle ticks before the player
 * joined, and the player's view then read them all back (docs/perf/CITY-FPS.md). Timeouts count game ticks only, so the
 * hold outlasts the pregen and runs out about a minute into play, after the player's own tickets have taken over.
 */
final class CityPregen {
    private CityPregen() {}

    /** How many chunks are asked for at once (the worldgen threads stay busy; memory stays bounded). */
    static final int WINDOW = 320;
    /** Give up waiting after this long (the world still opens; the rest generates as players walk). */
    static final long DEADLINE_NS = 300_000_000_000L;
    /** Beyond the city's reach, this many chunks more. */
    static final int MARGIN_CHUNKS = 2;
    /** How long a finished chunk stays held, in game ticks (the player joins in seconds). */
    static final long HOLD_TICKS = 1200L;

    record Result(int chunks, int ok, int failed, double seconds, String paint) {}

    private static final class Pending {
        final long key;
        CompletableFuture<ChunkResult<ChunkAccess>> future;

        Pending(long key) {
            this.key = key;
        }
    }

    static Result run(ServerLevel level, CityLayout layout) {
        return run(level, order(layout), new ChunkPos(layout.cx >> 4, layout.cz >> 4));
    }

    /** Generates these chunks to FULL, in this order, before returning. */
    static Result run(ServerLevel level, long[] order, ChunkPos focus) {
        ServerChunkCache cache = level.getChunkSource();
        TicketType ticket = NeoForgeMod.GENERATE_FORCED_TICKET.value();
        TicketType hold = CityModule.HOLD_TICKET.value();
        LevelLoadListener listener = level.getServer().getLevelLoadListener();
        listener.start(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, order.length);
        listener.updateFocus(level.dimension(), focus);
        boolean dh = CityDh.hold();
        City.STATS.clear();
        Awaken.LOG.info("[city] pregen start: {} chunks round chunk {} {}{}", order.length, focus.x(), focus.z(),
                dh ? " (Distant Horizons holds its world generation)" : "");
        long t0 = System.nanoTime();
        List<Pending> pending = new ArrayList<>();
        int next = 0, done = 0, failed = 0;
        long lastReport = t0;
        try {
            while (done < order.length) {
                boolean added = false;
                while (pending.size() < WINDOW && next < order.length) {
                    long key = order[next++];
                    cache.addTicketWithRadius(ticket, ChunkPos.unpack(key), 0);
                    pending.add(new Pending(key));
                    added = true;
                }
                if (added) for (int i = 0; i < 64 && cache.pollTask(); i++) { }
                for (Pending p : pending) {
                    if (p.future != null) continue;
                    ChunkHolder h = cache.chunkMap.getVisibleChunkIfPresent(p.key);
                    if (h != null) p.future = h.scheduleChunkGenerationTask(ChunkStatus.FULL, cache.chunkMap);
                }
                for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                    Pending p = it.next();
                    if (p.future == null || !p.future.isDone()) continue;
                    ChunkResult<ChunkAccess> r = p.future.getNow(null);
                    if (r == null || !r.isSuccess()) failed++;
                    else cache.addTicketWithRadius(hold, ChunkPos.unpack(p.key), 0);
                    cache.removeTicketWithRadius(ticket, ChunkPos.unpack(p.key), 0);
                    it.remove();
                    done++;
                }
                long now = System.nanoTime();
                if (now - lastReport > 250_000_000L) {
                    listener.update(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, done, order.length);
                    lastReport = now;
                }
                if (now - t0 > DEADLINE_NS) {
                    Awaken.LOG.warn("[city] pregen gave up after {} s with {} of {} chunks done", (now - t0) / 1_000_000_000L, done, order.length);
                    for (Pending p : pending) cache.removeTicketWithRadius(ticket, ChunkPos.unpack(p.key), 0);
                    break;
                }
                if (!cache.pollTask()) LockSupport.parkNanos(250_000L);
            }
        } finally {
            if (dh) CityDh.release();
            listener.finish(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN);
        }
        double s = (System.nanoTime() - t0) / 1e9;
        Result res = new Result(order.length, done - failed, failed, s, City.STATS.summary());
        Awaken.LOG.info(String.format(Locale.ROOT, "[city] pregen done: %d chunks (%d failed) in %.2f s (%.2f ms/chunk wall); city %s",
                order.length, failed, s, s * 1000 / Math.max(1, order.length), res.paint()));
        return res;
    }

    /** Every chunk the city writes into plus a margin, nearest the centre first. */
    static long[] order(CityLayout l) {
        int r = l.reach + MARGIN_CHUNKS * 16 + 16;
        int ccx = l.cx >> 4, ccz = l.cz >> 4;
        List<long[]> out = new ArrayList<>();
        for (int x = (l.cx - r) >> 4; x <= (l.cx + r) >> 4; x++) {
            for (int z = (l.cz - r) >> 4; z <= (l.cz + r) >> 4; z++) {
                long dx = (x << 4) + 8 - l.cx, dz = (z << 4) + 8 - l.cz;
                if (dx * dx + dz * dz > (long) r * r) continue;
                out.add(new long[]{ChunkPos.pack(x, z), dx * dx + dz * dz});
            }
        }
        out.sort(Comparator.comparingLong(a -> a[1]));
        return out.stream().mapToLong(a -> a[0]).toArray();
    }

    static String describe(long[] chunks) {
        return chunks.length + " chunks " + Arrays.toString(Arrays.copyOf(chunks, Math.min(3, chunks.length)));
    }
}
