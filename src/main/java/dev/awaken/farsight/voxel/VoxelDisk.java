package dev.awaken.farsight.voxel;

import dev.awaken.Awaken;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * City Sight's memory on disk (SPEC §20): every chunk it has captured, as the snapshot it meshes from (the sections in
 * the game's own network form and their light, deflated), in regions of 32×32 chunks under
 * {@code <world>/awaken/farsight/voxels/overworld/r.<x>.<z>.bin} (versioned). A later session draws the places once
 * seen at once, before any chunk there loads. Written and read on its own thread; regions stay in memory once read.
 */
public final class VoxelDisk {
    static final int SCHEMA = 2, REGION = 32, MAGIC = 0x41575658; // "AWVX"; 2: the chunk's lowest ground

    private static final class Region {
        final Long2ObjectOpenHashMap<byte[]> blobs = new Long2ObjectOpenHashMap<>();
        boolean dirty;
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Awaken City Sight IO");
        t.setDaemon(true);
        return t;
    });
    private static @Nullable VoxelDisk open;

    private final Path dir;
    private final PalettedContainerFactory factory;
    private final int levelMinY;
    private final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    private VoxelDisk(ServerLevel level) {
        dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("awaken").resolve("farsight").resolve("voxels").resolve("overworld");
        factory = PalettedContainerFactory.create(level.registryAccess());
        levelMinY = level.getMinY();
    }

    /** The overworld's store (server thread), made on first use. */
    public static synchronized VoxelDisk of(ServerLevel overworld) {
        if (open == null) open = new VoxelDisk(overworld);
        return open;
    }

    /** Writes what changed and forgets the store (the server stops): waits for the write, at most 10 s (its thread dies with the game). */
    public static synchronized void close() {
        VoxelDisk d = open;
        open = null;
        if (d == null) return;
        try {
            IO.submit(d::flushNow).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            Awaken.LOG.warn("[farsight] city sight: the last write didn't finish: {}", e.toString());
        }
    }

    /** Writes what changed, on the IO thread (a level save). */
    public static synchronized void flush() {
        VoxelDisk d = open;
        if (d != null) IO.execute(d::flushNow);
    }

    /** Keeps a snapshot (serialized on the IO thread). */
    public void put(VoxelSnapshot s) {
        IO.execute(() -> {
            try {
                byte[] blob = encode(s);
                Region r = region(Math.floorDiv(s.chunkX(), REGION), Math.floorDiv(s.chunkZ(), REGION));
                r.blobs.put(ChunkPos.pack(s.chunkX(), s.chunkZ()), blob);
                r.dirty = true;
            } catch (RuntimeException e) {
                Awaken.LOG.warn("[farsight] city sight: can't keep chunk {},{}: {}", s.chunkX(), s.chunkZ(), e.toString());
            }
        });
    }

    /**
     * Reads, on the IO thread, every kept chunk within {@code radius} blocks of (x, z) that {@code skip} doesn't
     * already hold, and hands each over as a snapshot ({@code into}), nearest first.
     */
    public void around(double x, double z, double radius, java.util.function.LongPredicate skip, java.util.function.Consumer<VoxelSnapshot> into) {
        IO.execute(() -> {
            int rb = REGION * 16;
            int rx0 = Math.floorDiv((int) (x - radius), rb), rx1 = Math.floorDiv((int) (x + radius), rb);
            int rz0 = Math.floorDiv((int) (z - radius), rb), rz1 = Math.floorDiv((int) (z + radius), rb);
            LongArrayList keys = new LongArrayList();
            for (int rz = rz0; rz <= rz1; rz++) {
                for (int rx = rx0; rx <= rx1; rx++) {
                    Region r = region(rx, rz);
                    for (long key : r.blobs.keySet()) {
                        double dx = ChunkPos.getX(key) * 16 + 8 - x, dz = ChunkPos.getZ(key) * 16 + 8 - z;
                        if (dx * dx + dz * dz <= radius * radius && !skip.test(key)) keys.add(key);
                    }
                }
            }
            keys.sort((a, b) -> Double.compare(dist(a, x, z), dist(b, x, z)));
            for (long key : keys) {
                Region r = region(Math.floorDiv(ChunkPos.getX(key), REGION), Math.floorDiv(ChunkPos.getZ(key), REGION));
                byte[] blob = r.blobs.get(key);
                if (blob == null) continue;
                try {
                    into.accept(decode(ChunkPos.getX(key), ChunkPos.getZ(key), blob));
                } catch (RuntimeException | IOException e) {
                    r.blobs.remove(key);
                }
            }
        });
    }

    private static double dist(long key, double x, double z) {
        double dx = ChunkPos.getX(key) * 16 + 8 - x, dz = ChunkPos.getZ(key) * 16 + 8 - z;
        return dx * dx + dz * dz;
    }

    // ---- regions (IO thread) ----------------------------------------------------------------------------------------

    private Path file(int rx, int rz) {
        return dir.resolve("r." + rx + "." + rz + ".bin");
    }

    private Region region(int rx, int rz) {
        long key = ChunkPos.pack(rx, rz);
        Region r = regions.get(key);
        if (r != null) return r;
        r = new Region();
        regions.put(key, r);
        Path f = file(rx, rz);
        if (Files.isRegularFile(f)) {
            try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(f)))) {
                if (in.readInt() == MAGIC && in.readInt() == SCHEMA) {
                    int n = in.readInt();
                    for (int i = 0; i < n; i++) {
                        int cx = in.readInt(), cz = in.readInt(), len = in.readInt();
                        byte[] b = new byte[len];
                        in.readFully(b);
                        r.blobs.put(ChunkPos.pack(cx, cz), b);
                    }
                }
            } catch (IOException e) {
                Awaken.LOG.warn("[farsight] city sight: can't read {}: {}", f, e.toString());
            }
        }
        return r;
    }

    private void flushNow() {
        for (var e : regions.long2ObjectEntrySet()) {
            Region r = e.getValue();
            if (!r.dirty) continue;
            r.dirty = false;
            Path f = file(ChunkPos.getX(e.getLongKey()), ChunkPos.getZ(e.getLongKey())), tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try {
                Files.createDirectories(dir);
                try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(tmp)))) {
                    out.writeInt(MAGIC);
                    out.writeInt(SCHEMA);
                    out.writeInt(r.blobs.size());
                    for (var b : r.blobs.long2ObjectEntrySet()) {
                        out.writeInt(ChunkPos.getX(b.getLongKey()));
                        out.writeInt(ChunkPos.getZ(b.getLongKey()));
                        out.writeInt(b.getValue().length);
                        out.write(b.getValue());
                    }
                }
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Awaken.LOG.warn("[farsight] city sight: can't write {}: {}", f, ex.toString());
            }
        }
    }

    // ---- one chunk --------------------------------------------------------------------------------------------------

    /** A snapshot in its kept form (public for the test). */
    public static byte[] encode(VoxelSnapshot s) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(16384));
        buf.writeInt(s.fromSection());
        buf.writeInt(s.ground());
        buf.writeInt(s.sections().length);
        for (int i = 0; i < s.sections().length; i++) {
            LevelChunkSection sec = s.sections()[i];
            DataLayer sky = s.sky()[i], block = s.block()[i];
            buf.writeByte((sec != null ? 1 : 0) | (sky != null ? 2 : 0) | (block != null ? 4 : 0));
            if (sec != null) sec.write(buf);
            if (sky != null) buf.writeBytes(sky.getData());
            if (block != null) buf.writeBytes(block.getData());
        }
        byte[] raw = new byte[buf.readableBytes()];
        buf.readBytes(raw);
        buf.release();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length / 3 + 64);
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes, new Deflater(Deflater.BEST_SPEED))) {
            out.write(raw);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** A kept snapshot read back (public for the test). */
    public VoxelSnapshot decode(int cx, int cz, byte[] blob) throws IOException {
        byte[] raw;
        try (InflaterInputStream in = new InflaterInputStream(new java.io.ByteArrayInputStream(blob))) {
            raw = in.readAllBytes();
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(raw));
        int from = buf.readInt(), ground = buf.readInt(), n = buf.readInt();
        LevelChunkSection[] sections = new LevelChunkSection[n];
        DataLayer[] sky = new DataLayer[n], block = new DataLayer[n];
        for (int i = 0; i < n; i++) {
            int flags = buf.readByte();
            if ((flags & 1) != 0) {
                LevelChunkSection sec = new LevelChunkSection(factory);
                sec.read(buf);
                sections[i] = sec;
            }
            if ((flags & 2) != 0) {
                byte[] d = new byte[2048];
                buf.readBytes(d);
                sky[i] = new DataLayer(d);
            }
            if ((flags & 4) != 0) {
                byte[] d = new byte[2048];
                buf.readBytes(d);
                block[i] = new DataLayer(d);
            }
        }
        return new VoxelSnapshot(cx, cz, levelMinY, from, ground, sections, sky, block);
    }
}
