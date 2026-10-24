package dev.awaken.lucidium.world;

import dev.awaken.Awaken;
import dev.awaken.lucidium.LucidiumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Lucidium cells: one dream per 512x512 cell, aligned to region files like the gates
 * (cell i has its origin at ((i mod 64) * 1024, (i div 64) * 1024), so each cell is exactly one region file and cells
 * never touch). Dreams are thrown away on waking. A cell whose dream ended is reused once the chunks it generated are
 * out of memory and erased from disk ({@link dev.awaken.core.RegionRecycler}), so a server that runs for weeks keeps
 * using the same few cells; the whole Lucidium folder is still wiped when the server stops and before it starts.
 */
public final class Cells {
    private Cells() {}

    public static final int PITCH = 1024, SIZE = 512, ROW = 64;
    private static final AtomicInteger NEXT = new AtomicInteger();
    /** Cells whose old dream is erased and that can be dreamed in again. */
    private static final java.util.ArrayDeque<Integer> FREE = new java.util.ArrayDeque<>();
    /** Chunks each cell generated this session (chunk generation runs on worker threads). */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.Set<Long>> GENERATED = new java.util.concurrent.ConcurrentHashMap<>();
    /** Cells waiting for their chunks to be erased. */
    private static final java.util.Set<Integer> RECYCLING = new java.util.HashSet<>();

    /** A cell for a new dream: a recycled one if any, else a fresh number. Server thread. */
    public static int allocate() {
        Integer free = FREE.poll();
        return free != null ? free : NEXT.getAndIncrement();
    }

    /** The dream in this cell is over: erase what it generated once it is out of memory, then reuse the cell. */
    public static void release(MinecraftServer server, int cell) {
        if (cell < 0 || !RECYCLING.add(cell)) return;
        java.util.Set<Long> chunks = GENERATED.remove(cell);
        net.minecraft.server.level.ServerLevel level = server.getLevel(LucidiumContent.LUCIDIUM);
        if (chunks == null || chunks.isEmpty() || level == null) {
            RECYCLING.remove(cell);
            FREE.add(cell);
            return;
        }
        long[] packed = chunks.stream().mapToLong(Long::longValue).toArray();
        dev.awaken.core.RegionRecycler.eraseWhenUnloaded(level, packed, () -> {
            if (RECYCLING.remove(cell)) FREE.add(cell);
        });
    }

    /** Called by the generator for every chunk it fills inside a cell. */
    static void generated(int cell, long chunk) {
        GENERATED.computeIfAbsent(cell, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(chunk);
    }

    /** Cells handed out so far (fresh numbers, not reuses): tests watch it stay small. */
    public static int issued() {
        return NEXT.get();
    }

    public static int originX(int cell) {
        return Math.floorMod(cell, ROW) * PITCH;
    }

    public static int originZ(int cell) {
        return Math.floorDiv(cell, ROW) * PITCH;
    }

    public static BlockPos center(int cell, int y) {
        return new BlockPos(originX(cell) + SIZE / 2, y, originZ(cell) + SIZE / 2);
    }

    /** The cell containing a block column, or -1 for the gaps between cells. */
    public static int cellAt(int blockX, int blockZ) {
        int cx = Math.floorDiv(blockX, PITCH), cz = Math.floorDiv(blockZ, PITCH);
        int lx = blockX - cx * PITCH, lz = blockZ - cz * PITCH;
        if (cx < 0 || cx >= ROW || cz < 0 || lx >= SIZE || lz >= SIZE) return -1;
        return cx + ROW * cz;
    }

    /** Deletes every generated Lucidium chunk. Only call while the Lucidium level is closed. */
    public static void wipe(MinecraftServer server) {
        NEXT.set(0);
        FREE.clear();
        GENERATED.clear();
        RECYCLING.clear();
        DreamChunkGenerator.clear();
        Path dim = DimensionType.getStorageFolder(LucidiumContent.LUCIDIUM, server.getWorldPath(LevelResource.ROOT));
        for (String sub : new String[]{"region", "entities", "poi"}) {
            Path dir = dim.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(dir)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        Awaken.LOG.warn("Lucidium: could not delete {}", p, e);
                    }
                });
            } catch (IOException e) {
                Awaken.LOG.warn("Lucidium: could not clean {}", dir, e);
            }
        }
    }
}
