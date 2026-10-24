package dev.awaken.farsight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

/**
 * The generated layer's memory (SPEC §20): every real chunk far terrain has seen in one level, as {@link FarChunk}s,
 * grouped in regions of 32×32 chunks. Regions live in memory while used (at most {@link #RESIDENT}) and on disk in
 * {@code <world>/awaken/farsight/<namespace>/<dimension>/r.<x>.<z>.dat} (compressed NBT, versioned), so a return trip
 * shows what was there before any chunk loads. Written on the server thread, read by the far-terrain workers.
 * Chunks that changed queue up in {@link #pollChanged} for whoever draws them.
 */
public final class FarStore {
    public static final int SCHEMA = 1, REGION = 32, RESIDENT = 64;

    private record RegionFile(int schemaVersion, List<FarChunk> chunks) {
        static final Codec<RegionFile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(RegionFile::schemaVersion),
                FarChunk.CODEC.listOf().fieldOf("chunks").forGetter(RegionFile::chunks)
        ).apply(i, RegionFile::new));
    }

    private static final class Region {
        final AtomicReferenceArray<FarChunk> chunks = new AtomicReferenceArray<>(REGION * REGION);
        volatile boolean dirty;
        volatile long used;
    }

    private static final Map<ResourceKey<Level>, FarStore> STORES = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Awaken Far Sight IO");
        t.setDaemon(true);
        return t;
    });

    /** Set while something draws far terrain from this server (the singleplayer client today; a server config later). */
    public static volatile boolean collecting;

    private final Path dir;
    private final Map<Long, Region> regions = new ConcurrentHashMap<>();
    /** Regions that exist on disk or in memory: a missing one is never looked for twice. */
    private final Set<Long> known = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Long> changed = new ConcurrentLinkedQueue<>();

    private FarStore(Path dir) {
        this.dir = dir;
        try (Stream<Path> files = Files.isDirectory(dir) ? Files.list(dir) : Stream.empty()) {
            files.forEach(f -> {
                String[] p = f.getFileName().toString().split("\\.");
                if (p.length == 4 && p[0].equals("r") && p[3].equals("dat")) {
                    try {
                        known.add(ChunkPos.pack(Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                    } catch (NumberFormatException ignored) {
                        // not one of ours
                    }
                }
            });
        } catch (IOException e) {
            Awaken.LOG.warn("[farsight] can't list {}: {}", dir, e.toString());
        }
    }

    public static FarStore of(ServerLevel level) {
        return STORES.computeIfAbsent(level.dimension(), k -> new FarStore(level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("awaken").resolve("farsight").resolve(k.identifier().getNamespace()).resolve(k.identifier().getPath())));
    }

    /** The store for a level if one exists, without making it. */
    public static @Nullable FarStore peek(ServerLevel level) {
        return STORES.get(level.dimension());
    }

    public void put(FarChunk chunk) {
        Region r = region(Math.floorDiv(chunk.x(), REGION), Math.floorDiv(chunk.z(), REGION), true);
        r.chunks.set(index(chunk.x(), chunk.z()), chunk);
        r.dirty = true;
        changed.add(ChunkPos.pack(chunk.x(), chunk.z()));
    }

    /** The chunk's summary, or null if far terrain has never seen it. May read a region from disk (workers only). */
    public @Nullable FarChunk get(int chunkX, int chunkZ) {
        Region r = region(Math.floorDiv(chunkX, REGION), Math.floorDiv(chunkZ, REGION), false);
        return r == null ? null : r.chunks.get(index(chunkX, chunkZ));
    }

    /** Whether any chunk in this block rectangle may be known (a cheap check before asking for a patch). */
    public boolean mayHave(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int rb = REGION * 16;
        for (int rz = Math.floorDiv(minBlockZ, rb); rz <= Math.floorDiv(maxBlockZ, rb); rz++) {
            for (int rx = Math.floorDiv(minBlockX, rb); rx <= Math.floorDiv(maxBlockX, rb); rx++) {
                if (known.contains(ChunkPos.pack(rx, rz))) return true;
            }
        }
        return false;
    }

    /**
     * Fills one patch of ring {@code ring} from the real chunks seen there, as texels laid out like
     * {@link FarColumns#patch}; a cell with too little seen stays 0 (unknown), and the prediction shows instead. A
     * cell up to 16 blocks wide sums the 2-block cells inside one chunk; a wider one the chunks it covers, once at
     * least a quarter of them are known. Returns {@code min << 16 | max} of the known tops (offset), or -1 if none.
     */
    public int patch(int ring, int px, int pz, int[] height, int[] colour) {
        int s = Rings.cellSize(ring), lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        Sum sum = new Sum();
        for (int j = 0; j < Rings.PATCH; j++) {
            for (int i = 0; i < Rings.PATCH; i++) {
                int k = j * Rings.PATCH + i;
                int x0 = (px * Rings.PATCH + i) * s, z0 = (pz * Rings.PATCH + j) * s;
                long v = s <= 16 ? within(sum.reset(), x0, z0, s) : across(sum.reset(), x0, z0, s);
                height[k] = 0;
                colour[k] = 0;
                if (v == Long.MIN_VALUE) continue;
                int top = (int) (v >> 40), depth = (int) (v >> 32) & 255, rgb = (int) v & 0xFFFFFF;
                int h = Math.clamp(top + FarColumns.HEIGHT_OFFSET, 0, 65535);
                height[k] = 255 << 24 | depth << 16 | (h & 255) << 8 | h >> 8;
                colour[k] = 255 << 24 | (rgb & 255) << 16 | (rgb >> 8 & 255) << 8 | rgb >> 16 & 255;
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        return lo > hi ? -1 : lo << 16 | hi;
    }

    // top << 40 | depth << 32 | rgb, or MIN_VALUE: the s×s blocks at (x0, z0), all inside one chunk
    private long within(Sum sum, int x0, int z0, int s) {
        FarChunk c = get(x0 >> 4, z0 >> 4);
        if (c == null) return Long.MIN_VALUE;
        int n = s / 2, sx0 = (x0 & 15) / 2, sz0 = (z0 & 15) / 2;
        for (int dz = 0; dz < n; dz++) for (int dx = 0; dx < n; dx++) sum.add(c, (sz0 + dz) * FarChunk.SIDE + sx0 + dx);
        return sum.result();
    }

    // the same over the chunks an s×s cell covers (s a multiple of 16), known once a quarter of them are
    private long across(Sum sum, int x0, int z0, int s) {
        int m = s / 16, seen = 0;
        for (int dz = 0; dz < m; dz++) {
            for (int dx = 0; dx < m; dx++) {
                FarChunk c = get((x0 >> 4) + dx, (z0 >> 4) + dz);
                if (c == null) continue;
                seen++;
                for (int i = 0; i < FarChunk.CELLS; i++) sum.add(c, i);
            }
        }
        return seen * 4 < m * m ? Long.MIN_VALUE : sum.result();
    }

    /** Running averages of 2-block cells: top, colour, and water by majority. */
    private static final class Sum {
        long top, r, g, b;
        int n, wet, depth;

        Sum reset() {
            top = r = g = b = 0;
            n = wet = depth = 0;
            return this;
        }

        void add(FarChunk c, int i) {
            int cell = c.cell()[i];
            if (cell == 0) return;
            top += FarChunk.top(cell);
            int rgb = c.colour()[i];
            r += rgb >> 16 & 255;
            g += rgb >> 8 & 255;
            b += rgb & 255;
            n++;
            int d = FarChunk.depth(cell);
            if (d > 0) {
                wet++;
                depth += d;
            }
        }

        long result() {
            if (n == 0) return Long.MIN_VALUE;
            int d = wet * 2 >= n ? Math.clamp(depth / Math.max(1, wet), 1, FarColumns.MAX_DEPTH) : 0;
            long rgb = (r / n) << 16 | (g / n) << 8 | b / n;
            return (top / n) << 40 | (long) d << 32 | rgb;
        }
    }

    /** The next chunk that changed ({@link ChunkPos#pack}), or {@code Long.MIN_VALUE} when none. */
    public long pollChanged() {
        Long k = changed.poll();
        return k == null ? Long.MIN_VALUE : k;
    }

    private static int index(int chunkX, int chunkZ) {
        return Math.floorMod(chunkZ, REGION) * REGION + Math.floorMod(chunkX, REGION);
    }

    private @Nullable Region region(int rx, int rz, boolean create) {
        long key = ChunkPos.pack(rx, rz);
        Region r = regions.get(key);
        if (r == null) {
            if (!create && !known.contains(key)) return null;
            r = regions.computeIfAbsent(key, k -> load(rx, rz));
            known.add(key);
            if (regions.size() > RESIDENT + 16) IO.execute(this::trim);
        }
        r.used = System.nanoTime();
        return r;
    }

    private Region load(int rx, int rz) {
        Region r = new Region();
        Path f = file(rx, rz);
        if (!Files.isRegularFile(f)) return r;
        try {
            CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.uncompressedQuota());
            if (tag.getIntOr("schemaVersion", 0) != SCHEMA) return r; // an older layout: re-learned as chunks load
            RegionFile.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(e -> Awaken.LOG.warn("[farsight] region {}: {}", f, e))
                    .ifPresent(file -> {
                        for (FarChunk c : file.chunks()) if (c.valid()) r.chunks.set(index(c.x(), c.z()), c);
                    });
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.warn("[farsight] can't read {}: {}", f, e.toString());
        }
        return r;
    }

    private Path file(int rx, int rz) {
        return dir.resolve("r." + rx + "." + rz + ".dat");
    }

    private void write(long key, Region r) {
        if (!r.dirty) return;
        r.dirty = false;
        List<FarChunk> list = new ArrayList<>();
        for (int i = 0; i < REGION * REGION; i++) {
            FarChunk c = r.chunks.get(i);
            if (c != null) list.add(c);
        }
        int rx = ChunkPos.getX(key), rz = ChunkPos.getZ(key);
        try {
            Files.createDirectories(dir);
            Tag tag = RegionFile.CODEC.encodeStart(NbtOps.INSTANCE, new RegionFile(SCHEMA, list)).getOrThrow();
            Path f = file(rx, rz), tmp = dir.resolve("r." + rx + "." + rz + ".tmp");
            NbtIo.writeCompressed((CompoundTag) tag, tmp);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            r.dirty = true;
            Awaken.LOG.warn("[farsight] can't write region {},{}: {}", rx, rz, e.toString());
        }
    }

    /** Writes the least recently used regions out and lets them go, down to {@link #RESIDENT}. IO thread. */
    private void trim() {
        if (regions.size() <= RESIDENT) return;
        List<Map.Entry<Long, Region>> all = new ArrayList<>(regions.entrySet());
        all.sort(Comparator.comparingLong(e -> e.getValue().used));
        for (int i = 0; i < all.size() - RESIDENT; i++) {
            var e = all.get(i);
            write(e.getKey(), e.getValue());
            regions.remove(e.getKey(), e.getValue());
        }
    }

    /** Writes every changed region: in the background on autosave, waiting for it when the server stops. */
    public static void flushAll(boolean wait) {
        for (FarStore s : STORES.values()) IO.execute(() -> s.regions.forEach(s::write));
        if (!wait) return;
        try {
            IO.submit(() -> {}).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Awaken.LOG.warn("[farsight] saving the far terrain cache took too long: {}", e.toString());
        }
    }

    /** The server stopped: everything written, nothing kept for the next world. */
    public static void closeAll() {
        flushAll(true);
        STORES.clear();
    }

    public int resident() {
        return regions.size();
    }
}
