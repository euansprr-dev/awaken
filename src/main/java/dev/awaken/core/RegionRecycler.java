package dev.awaken.core;

import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Erases chunks from a level's region files while the server runs, so cell dimensions (gates, dreams) can reuse a cell
 * without a restart (SPEC "Together", dedicated server). Only when it is safe: every chunk of the batch must be out of
 * memory (neither loaded nor waiting to unload) on two checks at least ten seconds apart; then each chunk is written
 * as "no data" through the level's own chunk IO worker, the same path vanilla uses, so a later load finds nothing and
 * generates afresh. Entities and points of interest live in other files: callers make stale entities leave on load.
 * Server thread only; forgotten on server stop (the callers' own stop-time cleanup still runs).
 */
public final class RegionRecycler {
    private RegionRecycler() {}

    /** Checks run this often; a batch is erased after two clean checks this far apart. */
    static final int CHECK_EVERY = 100, CLEAN_FOR = 200;

    private static final class Batch {
        final ResourceKey<Level> level;
        final long[] chunks;
        final Runnable onDone;
        long cleanSince = -1;

        Batch(ResourceKey<Level> level, long[] chunks, Runnable onDone) {
            this.level = level;
            this.chunks = chunks;
            this.onDone = onDone;
        }
    }

    private static final List<Batch> QUEUE = new ArrayList<>();

    /** Erases these chunks (packed {@link ChunkPos#pack} longs) once none is in memory, then runs {@code onDone}. */
    public static void eraseWhenUnloaded(ServerLevel level, long[] chunks, Runnable onDone) {
        QUEUE.add(new Batch(level.dimension(), chunks.clone(), onDone));
    }

    /** Every chunk of a square of {@code size} chunks whose corner chunk is ({@code cx}, {@code cz}). */
    public static long[] square(int cx, int cz, int size) {
        LongArrayList out = new LongArrayList(size * size);
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) out.add(ChunkPos.pack(cx + x, cz + z));
        return out.toLongArray();
    }

    /** Batches still waiting (tests). */
    public static int pending() {
        return QUEUE.size();
    }

    static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty() || server.getTickCount() % CHECK_EVERY != 0) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Batch> it = QUEUE.iterator(); it.hasNext(); ) {
            Batch b = it.next();
            ServerLevel level = server.getLevel(b.level);
            if (level == null) {
                it.remove();
                continue;
            }
            ChunkMap map = level.getChunkSource().chunkMap;
            if (!allOut(map, b.chunks)) {
                b.cleanSince = -1;
                continue;
            }
            if (b.cleanSince < 0) {
                b.cleanSince = now;
                continue;
            }
            if (now - b.cleanSince < CLEAN_FOR) continue;
            for (long c : b.chunks) map.write(ChunkPos.unpack(c), () -> null);
            it.remove();
            try {
                b.onDone.run();
            } catch (RuntimeException e) {
                Awaken.LOG.warn("[region] recycle callback failed", e);
            }
        }
    }

    /** No chunk of the batch is loaded, generating or waiting to unload. */
    static boolean allOut(ChunkMap map, long[] chunks) {
        for (long c : chunks) if (map.getUpdatingChunkIfPresent(c) != null || map.getVisibleChunkIfPresent(c) != null) return false;
        return true;
    }

    static void clear() {
        QUEUE.clear();
    }
}
