package dev.awaken.ground.client;

import dev.awaken.ground.wild.WildLawn;
import dev.awaken.living.wild.WildRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The lawn's colour by wildness on the client (SPEC §41.3): each chunk's wildness record ({@code awaken:wild}, synced with the
 * chunk) copied on the main thread into a small table the meshing threads may read, then read between cell centres (four blocks
 * apart, across chunk edges) so no cell or chunk shows a line. A chunk whose record comes after it was drawn is redrawn
 * round its surface. Overworld only; nothing allocated on a read.
 */
final class WildTint {
    private WildTint() {}

    private record Entry(long key, byte[] cells) {}

    private static final int SIZE = 128, MASK = SIZE - 1;
    private static final AtomicReferenceArray<Entry> TABLE = new AtomicReferenceArray<>(SIZE * SIZE);
    private static Level seen;
    private static int scan;

    static void init() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof ClientLevel level && e.getChunk() instanceof LevelChunk chunk) take(level, chunk, false);
        });
        NeoForge.EVENT_BUS.addListener(WildTint::tick);
    }

    private static int slot(int cx, int cz) {
        return (cz & MASK) * SIZE + (cx & MASK);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** Copies a chunk's record into the table; true when it was new there. */
    private static boolean take(ClientLevel level, LevelChunk chunk, boolean redraw) {
        if (level.dimension() != Level.OVERWORLD) return false;
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        Entry e = TABLE.get(slot(cx, cz));
        if (e != null && e.key == key(cx, cz)) return false;
        WildRecord.Cells c = WildRecord.existing(chunk);
        if (c == null) return false;
        byte[] cells = new byte[16];
        for (int i = 0; i < 16; i++) cells[i] = (byte) Math.round(c.cell(i) * 255);
        TABLE.set(slot(cx, cz), new Entry(key(cx, cz), cells));
        if (redraw) {
            int sy = SectionPos.blockToSectionCoord(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8));
            level.setSectionRangeDirty(cx - 1, sy - 1, cz - 1, cx + 1, sy + 1, cz + 1);
        }
        return true;
    }

    /** A sweep of a slice of the loaded chunks each tick, picking up records that came after their chunk. */
    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != seen) {
            seen = level;
            for (int i = 0; i < TABLE.length(); i++) TABLE.set(i, null);
        }
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD) return;
        int r = mc.options.getEffectiveRenderDistance() + 1, side = 2 * r + 1;
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        // one row of the square round the player a tick
        int row = scan++ % side;
        for (int i = 0; i < side; i++) {
            int cx = pcx - r + i, cz = pcz - r + row;
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk != null) take(level, chunk, true);
        }
    }

    /** The lawn's wildness at a column (-1 where no record is known yet). */
    static double at(int x, int z) {
        // cell centres sit at 2, 6, 10, 14 within a chunk: read between the four round (x, z)
        int gx = Math.floorDiv(x - 2, 4), gz = Math.floorDiv(z - 2, 4);
        float fx = (x - 2 - gx * 4) / 4F, fz = (z - 2 - gz * 4) / 4F;
        float a = cell(gx, gz), b = cell(gx + 1, gz), c = cell(gx, gz + 1), d = cell(gx + 1, gz + 1);
        // a missing neighbour takes the value across from it, so an edge waits for its record without a step
        if (a < 0) a = b >= 0 ? b : c >= 0 ? c : d;
        if (a < 0) return -1;
        if (b < 0) b = a;
        if (c < 0) c = a;
        if (d < 0) d = c;
        float top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    /** A cell by its global index (four blocks a cell), or -1 when its chunk has no record here. */
    private static float cell(int gx, int gz) {
        int cx = gx >> 2, cz = gz >> 2;
        Entry e = TABLE.get(slot(cx, cz));
        if (e == null || e.key != key(cx, cz)) return -1F;
        return (e.cells[(gz & 3) * 4 + (gx & 3)] & 0xFF) / 255F;
    }

    static int apply(int rgb, int x, int z) {
        double w = at(x, z);
        return w < 0 ? rgb : WildLawn.shift(rgb, w);
    }
}
