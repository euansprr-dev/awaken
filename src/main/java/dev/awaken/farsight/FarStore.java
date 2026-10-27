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
    /**
     * 3 (2026-10-26): ice read as its textures' pale blue, not its lavender map colour (purple far ice); 4 (2026-10-27):
     * the ground under what floats, not a sky platform's deck (pillars from the ground up to it); 5: nor a tree's crown
     * in its islands' leaves (a trunk up to it). Older ones are re-learned.
     */
    public static final int SCHEMA = 5, REGION = 32, RESIDENT = 64;

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
        // most saves (block entities, random ticks) change no height, colour or crown: no patch rebuilds in seven rings
        FarChunk was = r.chunks.get(index(chunk.x(), chunk.z()));
        if (was != null && java.util.Arrays.equals(was.cell(), chunk.cell()) && java.util.Arrays.equals(was.colour(), chunk.colour())
                && java.util.Arrays.equals(was.crown(), chunk.crown())) return;
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
     * {@link FarColumns#patch} (its grid corners), each the average of the 2-block cells within half a cell of its
     * corner; the colour carries {@link FarColumns#GENERATED}, the crown's alpha 255 (the shape is the biome's). A corner
     * with less than a quarter of its area seen stays 0 (unknown), and the prediction shows instead. Returns
     * {@code min << 16 | max} of the known tops (whole blocks + {@link FarColumns#HEIGHT_BASE}), or -1 if none.
     */
    public int patch(int ring, int px, int pz, int[] height, int[] colour, int[] info, int[] crown) {
        int s = Rings.cellSize(ring), lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        Sum sum = new Sum();
        for (int j = 0; j < Rings.PATCH; j++) {
            for (int i = 0; i < Rings.PATCH; i++) {
                int k = j * Rings.PATCH + i;
                int x = (px * Rings.PATCH + i) * s, z = (pz * Rings.PATCH + j) * s;
                height[k] = colour[k] = info[k] = crown[k] = 0;
                if (!sum.reset().over(this, x - s / 2, z - s / 2, s)) continue;
                // built cells (the city) keep their highest where a corner spans a few blocks (rings 0-2): a wall or a spire
                // stands out of its corner, never averaged away. Coarser corners average: there one spire's top would be
                // taken by every corner round it and the mesh would slope between them into a cone over the city
                boolean keep = sum.built > 0 && s <= 8;
                float top = keep ? Math.max((float) sum.top / sum.n, sum.builtTop) : (float) sum.top / sum.n;
                int depth = sum.wet * 2 >= sum.n ? Math.clamp(sum.depth / Math.max(1, sum.wet), 1, FarColumns.MAX_DEPTH) : 0;
                int canopy = sum.trees == 0 ? 0 : sum.canopy / sum.trees;
                int density = sum.trees * 255 / sum.n;
                height[k] = FarColumns.packHeight(top);
                int rgb = keep && sum.builtTop > (float) sum.top / sum.n ? sum.builtRgb
                        : (int) (sum.r / sum.n) << 16 | (int) (sum.g / sum.n) << 8 | (int) (sum.b / sum.n);
                colour[k] = FarColumns.abgr(rgb, FarColumns.GENERATED);
                // coldness stays the prediction's (the shaders read it from there): the real colours already hold the snow
                info[k] = FarColumns.abgr(depth, Math.min(255, canopy * 4), density, 0);
                if (sum.trees > 0) crown[k] = FarColumns.abgr((int) (sum.cr / sum.trees) << 16 | (int) (sum.cg / sum.trees) << 8 | (int) (sum.cb / sum.trees), 255);
                int whole = (int) Math.floor(top) + FarColumns.HEIGHT_BASE;
                lo = Math.min(lo, whole);
                hi = Math.max(hi, whole + 1);
            }
        }
        return lo > hi ? -1 : lo << 16 | hi;
    }

    /** Running sums of the 2-block cells over one corner's area: ground, water by majority, trees, colours; the highest built cell. */
    private static final class Sum {
        long top, r, g, b, cr, cg, cb;
        int n, wet, depth, trees, canopy, built, builtTop, builtRgb;

        Sum reset() {
            top = r = g = b = cr = cg = cb = 0;
            n = wet = depth = trees = canopy = built = builtTop = builtRgb = 0;
            return this;
        }

        /** Sums the s×s blocks at (x0, z0), cell by cell (2-block cells up to 16, whole chunks past that); true once a quarter is seen. */
        boolean over(FarStore store, int x0, int z0, int s) {
            int area;
            if (s <= 16) {
                int n2 = Math.max(1, s / 2);
                area = n2 * n2;
                FarChunk c = null;
                int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
                for (int dz = 0; dz < n2; dz++) {
                    for (int dx = 0; dx < n2; dx++) {
                        int bx = x0 + dx * 2, bz = z0 + dz * 2;
                        if (bx >> 4 != cx || bz >> 4 != cz) {
                            cx = bx >> 4;
                            cz = bz >> 4;
                            c = store.get(cx, cz);
                        }
                        if (c != null) add(c, ((bz & 15) >> 1) * FarChunk.SIDE + ((bx & 15) >> 1));
                    }
                }
            } else {
                int m = s / 16;
                area = m * m * FarChunk.CELLS;
                for (int dz = 0; dz < m; dz++) {
                    for (int dx = 0; dx < m; dx++) {
                        FarChunk c = store.get((x0 >> 4) + dx, (z0 >> 4) + dz);
                        if (c != null) for (int i = 0; i < FarChunk.CELLS; i++) add(c, i);
                    }
                }
            }
            return n > 0 && n * 4 >= area;
        }

        void add(FarChunk c, int i) {
            int cell = c.cell()[i];
            if (cell == 0) return;
            int rgb = c.colour()[i];
            if (FarCity.underCitySight && FarCity.built(rgb)) {
                // City Sight draws the buildings: here only the ground, a block under the street
                var l = dev.awaken.city.City.layout();
                if (l != null) {
                    top += l.groundY(c.x() * 16 + (i % FarChunk.SIDE) * 2, c.z() * 16 + (i / FarChunk.SIDE) * 2);
                    r += rgb >> 16 & 255;
                    g += rgb >> 8 & 255;
                    b += rgb & 255;
                    n++;
                    return;
                }
            }
            top += FarChunk.top(cell);
            if (FarCity.built(rgb) && (built++ == 0 || FarChunk.top(cell) > builtTop)) {
                builtTop = FarChunk.top(cell);
                builtRgb = rgb & 0xFFFFFF;
            }
            r += rgb >> 16 & 255;
            g += rgb >> 8 & 255;
            b += rgb & 255;
            n++;
            int d = FarChunk.depth(cell);
            if (d > 0) {
                wet++;
                depth += d;
            }
            int h = FarChunk.canopy(cell), leaf = c.crown()[i];
            if (h > 0 && leaf != 0) {
                trees++;
                canopy += h;
                cr += leaf >> 16 & 255;
                cg += leaf >> 8 & 255;
                cb += leaf & 255;
            }
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
