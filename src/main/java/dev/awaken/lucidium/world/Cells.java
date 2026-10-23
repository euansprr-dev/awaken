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
 * never touch). Dreams are thrown away on waking, so cell numbers are never reused within a server session and the
 * whole Lucidium folder is wiped when the server stops and again before it starts (crash recovery).
 */
public final class Cells {
    private Cells() {}

    public static final int PITCH = 1024, SIZE = 512, ROW = 64;
    private static final AtomicInteger NEXT = new AtomicInteger();

    public static int allocate() {
        return NEXT.getAndIncrement();
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
