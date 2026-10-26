package dev.awaken.farsight.voxel;

import dev.awaken.city.City;
import dev.awaken.city.CityLayout;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * City Sight's capture (SPEC §20): the city's chunks, snapshotted on the server tick within the far collector's budget
 * (nearest the players first, a held chunk read as it is, one not loaded any more read back from disk once a tick),
 * and again two seconds after a block in it changes (the game's neighbour update, which every placed, broken or
 * exploded block sends; saves are no sign: the city's hold saves every chunk as it lets it go, and taking them all
 * again showed as the city loading twice); the snapshots wait in {@link #SNAPSHOTS} for the client's workers.
 * Runs only while the client draws City Sight ({@link #wanted}).
 */
public final class VoxelCapture {
    private VoxelCapture() {}

    /** Set by the client while City Sight draws. */
    public static volatile boolean wanted;
    /** Taken and not yet picked up by the client. */
    public static final ConcurrentLinkedQueue<VoxelSnapshot> SNAPSHOTS = new ConcurrentLinkedQueue<>();
    /** Snapshots handed over, waiting (bounded: a snapshot holds its sections' copies). */
    static final int MAX_WAITING = 96;
    private static final AtomicInteger waiting = new AtomicInteger();
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    private static final LongOpenHashSet CITY = new LongOpenHashSet();
    /** City chunks whose blocks changed, by the tick of the last change (taken again once it has settled). */
    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap DIRTY = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    static final int SETTLE_TICKS = 40;
    private static boolean started;
    private static int captured;

    /** The client took a snapshot from {@link #SNAPSHOTS}. */
    public static void took() {
        waiting.decrementAndGet();
    }

    /** Whether everything asked for has been snapshotted and taken. */
    public static boolean idle() {
        synchronized (PENDING) {
            return started && PENDING.isEmpty() && SNAPSHOTS.isEmpty();
        }
    }

    public static int captured() {
        return captured;
    }

    /** A block changed at (x, z) in the overworld (server thread): if City Sight draws its chunk, take it again soon. */
    public static void blockChanged(ServerLevel level, int x, int z) {
        if (!wanted) return;
        int tick = level.getServer().getTickCount();
        synchronized (PENDING) {
            if (CITY.isEmpty()) return;
            // the chunk, and its neighbour where the block sits on the edge (its face there may now show or hide)
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int bx = x + dx, bz = z + dz;
                    if ((dx != 0 || dz != 0) && (bx >> 4) == (x >> 4) && (bz >> 4) == (z >> 4)) continue;
                    long key = ChunkPos.pack(bx >> 4, bz >> 4);
                    if (CITY.contains(key)) DIRTY.put(key, tick);
                }
            }
        }
    }

    public static void reset() {
        synchronized (PENDING) {
            PENDING.clear();
            CITY.clear();
            DIRTY.clear();
            started = false;
            captured = 0;
        }
        SNAPSHOTS.clear();
        waiting.set(0);
    }

    /** Takes snapshots until {@code deadline} (System.nanoTime) on the server thread. */
    public static void tick(ServerLevel overworld, long deadline) {
        if (!wanted) {
            if (started) reset();
            return;
        }
        synchronized (PENDING) {
            if (!started) {
                started = true;
                CityLayout city = City.layout();
                if (city != null) {
                    long[] keys = city.chunks.clone();
                    var players = overworld.players();
                    int px = players.isEmpty() ? city.cx >> 4 : players.getFirst().blockPosition().getX() >> 4;
                    int pz = players.isEmpty() ? city.cz >> 4 : players.getFirst().blockPosition().getZ() >> 4;
                    it.unimi.dsi.fastutil.longs.LongArrays.quickSort(keys, (a, b) -> Long.compare(dist(a, px, pz), dist(b, px, pz)));
                    for (long k : keys) {
                        CITY.add(k);
                        PENDING.add(k);
                    }
                }
            }
        }
        synchronized (PENDING) {
            if (!DIRTY.isEmpty()) {
                int now = overworld.getServer().getTickCount();
                var it = DIRTY.long2IntEntrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    if (now - e.getIntValue() < SETTLE_TICKS) continue;
                    PENDING.addAndMoveToFirst(e.getLongKey());
                    it.remove();
                }
            }
        }
        boolean loadedOne = false;
        while (System.nanoTime() < deadline && waiting.get() < MAX_WAITING) {
            long key;
            synchronized (PENDING) {
                if (PENDING.isEmpty()) return;
                key = PENDING.removeFirstLong();
            }
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            LevelChunk chunk = overworld.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                // the city's hold let it go: read it back, once a tick (a disk read)
                if (loadedOne) {
                    synchronized (PENDING) {
                        PENDING.add(key);
                    }
                    return;
                }
                loadedOne = true;
                chunk = overworld.getChunk(cx, cz);
            }
            VoxelSnapshot s = VoxelSnapshot.take(overworld, chunk);
            captured++;
            if (s == null) continue;
            waiting.incrementAndGet();
            SNAPSHOTS.add(s);
        }
    }

    private static long dist(long key, int cx, int cz) {
        long dx = ChunkPos.getX(key) - cx, dz = ChunkPos.getZ(key) - cz;
        return dx * dx + dz * dz;
    }
}
