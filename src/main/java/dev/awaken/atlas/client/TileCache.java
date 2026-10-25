package dev.awaken.atlas.client;

import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The Atlas's disk cache (SPEC §16.8): {@code <game>/awaken/atlas/<world>/<dim>/}, one deflated file per 512×512 tile
 * (height, colour, info per column), versioned. All file work runs on one daemon thread; tiles read back are handed to
 * the main thread through a queue. The whole cache stays under {@link #LIMIT}, evicting the oldest tiles first. The
 * cache is only ever a picture: what you have explored is the server's, never this.
 */
final class TileCache {
    static final int MAGIC = 0x41574154; // "AWAT"
    /** 1: heights, ARGB colours, info bytes (depth, class, lit), 512×512 each. */
    static final int VERSION = 1;
    static final long LIMIT = 64L << 20, AFTER_EVICT = 56L << 20;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Awaken Atlas cache");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    final Queue<ColumnStore.Tile> loaded = new ConcurrentLinkedQueue<>();
    private Path dir;

    static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("awaken").resolve("atlas");
    }

    /** The folder for this world and dimension: the save's folder name, or the server's address. */
    void open(String dimension) {
        Minecraft mc = Minecraft.getInstance();
        String world;
        var server = mc.getSingleplayerServer();
        if (server != null) world = "sp-" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        else if (mc.getCurrentServer() != null) world = "mp-" + mc.getCurrentServer().ip;
        else world = "unknown";
        dir = root().resolve(safe(world)).resolve(safe(dimension));
    }

    static String safe(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    /** Reads every tile within {@code tiles} of (tx, tz), nearest first, off the main thread. */
    void loadAround(int tx, int tz, int tiles, java.util.Set<Long> skip) {
        Path d = dir;
        if (d == null) return;
        IO.execute(() -> {
            List<int[]> want = new ArrayList<>();
            for (int z = tz - tiles; z <= tz + tiles; z++)
                for (int x = tx - tiles; x <= tx + tiles; x++)
                    if (!skip.contains(dev.awaken.atlas.Explored.key(x, z))) want.add(new int[]{x, z});
            want.sort(Comparator.comparingInt(a -> Math.abs(a[0] - tx) + Math.abs(a[1] - tz)));
            for (int[] w : want) {
                Path f = d.resolve(name(w[0], w[1]));
                if (!Files.isRegularFile(f)) continue;
                ColumnStore.Tile t = read(f, w[0], w[1]);
                if (t != null) loaded.add(t);
            }
        });
    }

    static String name(int tx, int tz) {
        return "t." + tx + "." + tz + ".bin";
    }

    private static ColumnStore.Tile read(Path f, int tx, int tz) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(f)), 1 << 16))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != tx || in.readInt() != tz) return null;
            ColumnStore.Tile t = new ColumnStore.Tile(tx, tz);
            int n = ColumnStore.TILE * ColumnStore.TILE;
            for (int i = 0; i < n; i++) t.height[i] = in.readShort();
            for (int i = 0; i < n; i++) t.color[i] = in.readInt();
            in.readFully(t.info);
            t.touched = Files.getLastModifiedTime(f).toMillis();
            return t;
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.debug("[atlas] cache tile {} unreadable, dropped: {}", f, e.toString());
            try {
                Files.deleteIfExists(f);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    /** Writes the tiles that changed since their last save, from copies taken now (main thread), then evicts. */
    void save(Iterable<ColumnStore.Tile> tiles) {
        Path d = dir;
        if (d == null) return;
        List<ColumnStore.Tile> copies = new ArrayList<>();
        for (ColumnStore.Tile t : tiles) {
            if (!t.dirty) continue;
            t.dirty = false;
            ColumnStore.Tile c = new ColumnStore.Tile(t.tx, t.tz);
            System.arraycopy(t.height, 0, c.height, 0, c.height.length);
            System.arraycopy(t.color, 0, c.color, 0, c.color.length);
            System.arraycopy(t.info, 0, c.info, 0, c.info.length);
            copies.add(c);
        }
        if (copies.isEmpty()) return;
        IO.execute(() -> {
            try {
                Files.createDirectories(d);
                for (ColumnStore.Tile c : copies) write(d, c);
                evict();
            } catch (IOException e) {
                Awaken.LOG.warn("[atlas] couldn't save the Atlas cache: {}", e.toString());
            }
        });
    }

    private static void write(Path d, ColumnStore.Tile t) throws IOException {
        Path f = d.resolve(name(t.tx, t.tz)), tmp = d.resolve(name(t.tx, t.tz) + ".tmp");
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (OutputStream raw = Files.newOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(raw, deflater), 1 << 16))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(t.tx);
            out.writeInt(t.tz);
            for (short h : t.height) out.writeShort(h);
            for (int c : t.color) out.writeInt(c);
            out.write(t.info);
        } finally {
            deflater.end();
        }
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Keeps the whole cache (every world) under the limit: oldest tiles go first. Cache thread. */
    static void evict() throws IOException {
        Path r = root();
        if (!Files.isDirectory(r)) return;
        List<Path> files;
        try (Stream<Path> s = Files.walk(r)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".bin")).toList();
        }
        long total = 0;
        List<long[]> meta = new ArrayList<>(files.size()); // [modified, size, index]
        for (int i = 0; i < files.size(); i++) {
            long size = Files.size(files.get(i));
            total += size;
            meta.add(new long[]{Files.getLastModifiedTime(files.get(i)).toMillis(), size, i});
        }
        if (total <= LIMIT) return;
        meta.sort(Comparator.comparingLong(m -> m[0]));
        for (long[] m : meta) {
            if (total <= AFTER_EVICT) break;
            Files.deleteIfExists(files.get((int) m[2]));
            total -= m[1];
        }
    }

    /** The size of the whole cache on disk, in bytes (cache thread result through the callback's own thread). */
    static long size() {
        Path r = root();
        if (!Files.isDirectory(r)) return 0;
        try (Stream<Path> s = Files.walk(r)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** "Clear Atlas cache": every world's tiles, on the cache thread. */
    static void clearAll() {
        IO.execute(() -> {
            Path r = root();
            if (!Files.isDirectory(r)) return;
            try (Stream<Path> s = Files.walk(r)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch (IOException e) {
                Awaken.LOG.warn("[atlas] couldn't clear the Atlas cache: {}", e.toString());
            }
        });
    }

    /** Waits for queued saves (leaving a world), at most a second. */
    static void drain() {
        var done = new java.util.concurrent.CountDownLatch(1);
        IO.execute(done::countDown);
        try {
            done.await(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
