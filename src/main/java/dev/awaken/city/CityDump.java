package dev.awaken.city;

import dev.awaken.Awaken;
import net.minecraft.world.level.block.state.BlockState;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Dev studio: paints the whole city into memory (no world needed) and writes it as voxels for
 * {@code tools/city/render.py}, which draws it from the sky with the blocks' texture colours. Lets the look be judged
 * between tours. Format (gzip): "CITYDUMP", int version 2, int YMIN, int H, int palette size, palette strings (UTF),
 * int chunk count, then per chunk: int dx, int dz (chunk offsets from the centre's chunk), 16*16*H shorts of palette
 * indices (0 = untouched, ordered y, z, x).
 */
final class CityDump {
    private CityDump() {}

    static int write(Path out, CityLayout layout, long seed) throws IOException {
        Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
        List<String> palette = new ArrayList<>();
        palette.add("none");
        CityBuffer buf = new CityBuffer(16, 16);
        int ccx = layout.cx >> 4, ccz = layout.cz >> 4;
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".chunks");
        int n = 0;
        try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)), 1 << 16))) {
            short[] cells = new short[16 * 16 * CityBuffer.H];
            for (long key : layout.chunks) {
                int x = net.minecraft.world.level.ChunkPos.unpack(key).x(), z = net.minecraft.world.level.ChunkPos.unpack(key).z();
                CityPainter.paintChunk(layout, x, z, seed, buf);
                for (int i = 0; i < cells.length; i++) {
                    BlockState s = buf.cells[i];
                    if (s == null) {
                        cells[i] = 0;
                        continue;
                    }
                    int id = ids.getOrDefault(s, -1);
                    if (id < 0) {
                        id = palette.size();
                        palette.add(s.toString());
                        ids.put(s, id);
                    }
                    cells[i] = (short) id;
                }
                d.writeInt(x - ccx);
                d.writeInt(z - ccz);
                for (short v : cells) d.writeShort(v);
                n++;
            }
        }
        try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(out)), 1 << 16))) {
            d.writeBytes("CITYDUMP");
            d.writeInt(2);
            d.writeInt(CityBuffer.YMIN);
            d.writeInt(CityBuffer.H);
            d.writeInt(palette.size());
            for (String s : palette) d.writeUTF(s);
            d.writeInt(n);
            d.writeInt(layout.cx - (ccx << 4));
            d.writeInt(layout.cz - (ccz << 4));
        }
        Awaken.LOG.info("[city] dump: {} chunks, {} block states -> {}", n, palette.size(), out.toAbsolutePath());
        return n;
    }
}
