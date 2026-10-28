package dev.awaken.delve;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where the lodes are, from the world seed alone (PLAN 7.3; SPEC §28.3 "Lodes"): the world is cut into 256 x 256
 * regions and each region holds {@link Ore#lodes} lodes of every ore at that ore's depth, each a seam walked out from its
 * heart. Pure and deterministic, so worldgen (per chunk, on any thread) and the Tuning Fork ask the same question and
 * get the same answer without scanning a block. Regions are cached; a plan is about a hundred longs per lode.
 */
public final class LodePlans {
    private LodePlans() {}

    public static final int REGION = 256;
    /** A lode reaches at most this far from its heart (its walk is cut there): the margin every query adds. */
    static final int REACH = 48;
    /** Lowest block a lode writes (above the bedrock rows). */
    static final int FLOOR_Y = -60;

    private record Key(long seed, int rx, int rz) {}

    /**
     * The plans, a future each: the first asker plans the region and anyone else asking waits on the future, not on a
     * lock the map holds (so clearing it never waits on a plan). The server thread never waits: it reads done plans only.
     */
    private static final ConcurrentHashMap<Key, CompletableFuture<List<Lode>>> CACHE = new ConcurrentHashMap<>();
    /** Lodes a test laid by hand: answered by every query, never written by worldgen. */
    static final List<Lode> EXTRA = new CopyOnWriteArrayList<>();
    /** Regions asked of the worker and not planned yet. */
    private static final java.util.Set<Key> ASKED = ConcurrentHashMap.newKeySet();
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "Awaken lode plans");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** The lodes planned for region (rx, rz), planned now if no one has (worldgen and workers; the server thread reads {@link #ready}). */
    public static List<Lode> region(long seed, int rx, int rz) {
        if (CACHE.size() > 4096) CACHE.clear();
        Key k = new Key(seed, rx, rz);
        CompletableFuture<List<Lode>> f = CACHE.get(k);
        if (f == null) {
            CompletableFuture<List<Lode>> mine = new CompletableFuture<>();
            f = CACHE.putIfAbsent(k, mine);
            if (f == null) {
                try {
                    mine.complete(plan(seed, rx, rz));
                } catch (Throwable t) {
                    CACHE.remove(k, mine);
                    mine.completeExceptionally(t);
                    throw t;
                }
                return mine.join();
            }
        }
        return f.join();
    }

    /**
     * The region's plan if it's done, else null (and the worker is asked for it): never plans, never waits, so the
     * server thread can ask it under a flying player. A region not planned yet has no lodes to whoever asks this way.
     */
    public static @Nullable List<Lode> ready(long seed, int rx, int rz) {
        Key k = new Key(seed, rx, rz);
        CompletableFuture<List<Lode>> f = CACHE.get(k);
        if (f != null && f.isDone() && !f.isCompletedExceptionally()) return f.join();
        if (f == null && ASKED.add(k)) {
            WORKER.execute(() -> {
                try {
                    region(k.seed, k.rx, k.rz);
                } catch (RuntimeException e) {
                    dev.awaken.Awaken.LOG.warn("[delve] lode region {} {} failed to plan: {}", k.rx, k.rz, e.toString());
                } finally {
                    ASKED.remove(k);
                }
            });
        }
        return null;
    }

    /** Drops the worker's waiting plans (the server is stopping; the one being planned finishes and is dropped with the cache). */
    public static void stopping() {
        WORKER.getQueue().clear();
        ASKED.clear();
    }

    /** Every lode whose box reaches the column box (x0, z0)-(x1, z1), test lodes included when {@code extra}. */
    static List<Lode> touching(long seed, int x0, int z0, int x1, int z1, boolean extra) {
        return touching(seed, x0, z0, x1, z1, extra, false);
    }

    /** As {@link #touching(long, int, int, int, int, boolean)}; with {@code ready}, only regions already planned (see {@link #ready}). */
    static List<Lode> touching(long seed, int x0, int z0, int x1, int z1, boolean extra, boolean ready) {
        List<Lode> out = new ArrayList<>();
        int rx0 = Math.floorDiv(x0 - REACH, REGION), rx1 = Math.floorDiv(x1 + REACH, REGION);
        int rz0 = Math.floorDiv(z0 - REACH, REGION), rz1 = Math.floorDiv(z1 + REACH, REGION);
        for (int rx = rx0; rx <= rx1; rx++) for (int rz = rz0; rz <= rz1; rz++) {
            List<Lode> plan = ready ? ready(seed, rx, rz) : region(seed, rx, rz);
            if (plan != null) for (Lode l : plan) if (l.touches(x0, z0, x1, z1)) out.add(l);
        }
        if (extra) for (Lode l : EXTRA) if (l.touches(x0, z0, x1, z1)) out.add(l);
        return out;
    }

    /** Whether every region a query of that box reads is planned (asking the worker for the rest). */
    static boolean planned(long seed, int x0, int z0, int x1, int z1) {
        boolean all = true;
        for (int rx = Math.floorDiv(x0 - REACH, REGION); rx <= Math.floorDiv(x1 + REACH, REGION); rx++) {
            for (int rz = Math.floorDiv(z0 - REACH, REGION); rz <= Math.floorDiv(z1 + REACH, REGION); rz++) {
                if (ready(seed, rx, rz) == null) all = false;
            }
        }
        return all;
    }

    /** The lode one of whose blocks is at {@code pos}, or null. */
    static Lode at(long seed, long pos) {
        int x = BlockPos.getX(pos), z = BlockPos.getZ(pos);
        for (Lode l : touching(seed, x, z, x, z, true)) if (l.holds(pos)) return l;
        return null;
    }

    // ---- the plan -----------------------------------------------------------------------------------------------------

    /** A region's plan made here and not kept (tests: what the worker will answer, without planning it for them). */
    static List<Lode> planNow(long seed, int rx, int rz) {
        return plan(seed, rx, rz);
    }

    private static List<Lode> plan(long seed, int rx, int rz) {
        if (Thread.currentThread() == serverThread) SERVER_PLANS.incrementAndGet();
        List<Lode> out = new ArrayList<>();
        Columns cols = columnsSeed == seed ? columns : null;
        if (cols != null) out.addAll(walls(seed, rx, rz, cols));
        for (Ore ore : Ore.values()) {
            for (int k = 0; k < ore.lodes; k++) {
                Rand r = new Rand(mix(seed, rx, rz, ore.ordinal() * 31 + k));
                if (k == ore.lodes - 1 && r.next() >= ore.lastChance) continue;
                int x = rx * REGION + 24 + r.nextInt(REGION - 48);
                int z = rz * REGION + 24 + r.nextInt(REGION - 48);
                int y = ore.minY + r.nextInt(ore.maxY - ore.minY + 1);
                int size = ore.minSize + r.nextInt(ore.maxSize - ore.minSize + 1);
                out.add(walk(ore, x, y, z, size, r));
            }
        }
        return List.copyOf(out);
    }

    /**
     * A seam: from the heart, two arms walk out in turn along a gently wandering heading (mostly level: seams follow the
     * strata), each step taking its cell and a cell beside it, so the lode reads as a band a block or two thick that a
     * cave wall cuts across. Stops at {@code size} blocks.
     */
    static Lode walk(Ore ore, int x, int y, int z, int size, Rand r) {
        double yaw = r.next() * Math.PI * 2, pitch = (r.next() - 0.5) * 0.5;
        return walk(ore, x, y, z, size, r, yaw, pitch);
    }

    static Lode walk(Ore ore, int x, int y, int z, int size, Rand r, double yaw, double pitch) {
        LongLinkedOpenHashSet cells = new LongLinkedOpenHashSet();
        long heart = BlockPos.asLong(x, y, z);
        cells.add(heart);
        double[][] arm = {{x + 0.5, y + 0.5, z + 0.5, yaw, pitch}, {x + 0.5, y + 0.5, z + 0.5, yaw + Math.PI, -pitch}};
        for (int step = 0; step < 240 && cells.size() < size; step++) {
            double[] a = arm[step & 1];
            a[3] += (r.next() - 0.5) * 0.35;
            a[4] = Math.max(-0.45, Math.min(0.45, a[4] + (r.next() - 0.5) * 0.16));
            double cp = Math.cos(a[4]);
            a[0] += Math.cos(a[3]) * cp;
            a[1] += Math.sin(a[4]);
            a[2] += Math.sin(a[3]) * cp;
            if (Math.abs(a[0] - x) > REACH - 2 || Math.abs(a[2] - z) > REACH - 2) continue;
            int cx = (int) Math.floor(a[0]), cy = (int) Math.floor(a[1]), cz = (int) Math.floor(a[2]);
            if (cy < FLOOR_Y) continue;
            cells.add(BlockPos.asLong(cx, cy, cz));
            // the band's width: a block beside the step (across the heading, or above or below it)
            if (cells.size() < size && r.next() < 0.85) {
                double side = r.next();
                int ox = 0, oy = 0, oz = 0;
                if (side < 0.5) {
                    double px = -Math.sin(a[3]), pz = Math.cos(a[3]);
                    int sign = r.next() < 0.5 ? 1 : -1;
                    ox = (int) Math.round(px * sign);
                    oz = (int) Math.round(pz * sign);
                } else {
                    oy = side < 0.75 ? 1 : -1;
                }
                if (cy + oy >= FLOOR_Y) cells.add(BlockPos.asLong(cx + ox, cy + oy, cz + oz));
            }
        }
        long[] arr = cells.toLongArray();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long c : arr) {
            x0 = Math.min(x0, BlockPos.getX(c));
            y0 = Math.min(y0, BlockPos.getY(c));
            z0 = Math.min(z0, BlockPos.getZ(c));
            x1 = Math.max(x1, BlockPos.getX(c));
            y1 = Math.max(y1, BlockPos.getY(c));
            z1 = Math.max(z1, BlockPos.getZ(c));
        }
        return new Lode(heart, ore, heart, arr, x0, y0, z0, x1, y1, z1);
    }

    // ---- wall lodes (SPEC §34.2): shallow seams that open onto cave air ---------------------------------------------------

    /** The base columns of the world being generated (its noise caves, before carvers), for the wall lodes. */
    @FunctionalInterface
    public interface Columns {
        net.minecraft.world.level.NoiseColumn at(int x, int z);
    }

    private static volatile Columns columns;
    private static volatile long columnsSeed;
    /** The thread that installed the columns (the server's), and the region plans made on it: each one a long tick. */
    private static volatile Thread serverThread;
    private static final AtomicInteger SERVER_PLANS = new AtomicInteger();

    /** Region plans made on the server thread so far (each samples up to a hundred noise columns: SPEC §28.9). */
    public static int serverPlans() {
        return SERVER_PLANS.get();
    }

    /** The overworld's columns (set when it loads; null when the server stops): plans made after include the wall lodes. */
    public static void columns(long seed, Columns c) {
        columns = c;
        columnsSeed = seed;
        serverThread = c == null ? null : Thread.currentThread();
        CACHE.clear();
    }

    /** Wall lodes a region holds, per ore, and their band (y, inclusive): the first trips' metal. */
    static final Ore[] WALL_ORES = {Ore.IRON, Ore.COPPER, Ore.COAL};
    static final int[] WALL_COUNT = {3, 2, 2}, WALL_MIN_Y = {-16, 0, 8}, WALL_MAX_Y = {60, 64, 72};
    /** Tries for a cave wall per lode (each a base column and up to four beside it). */
    static final int WALL_TRIES = 16;
    /** How far out from a standable spot a wall is looked for; a wall lode's most blocks (it follows one face). */
    static final int WALL_REACH = 10, WALL_MAX_SIZE = 56;

    /**
     * A region's wall lodes: each one's heart is the first cell of a cave wall at head height (found in the base columns,
     * a try at a time: the column's band searched for air you could stand in, nearest the rolled height first, then out
     * along a way to the wall; a lode with no cave at any try is not laid), and its seam follows the wall's face to either
     * side, so most of it faces the cave and glints in a lamp's light. On top of the region's other lodes, which stay
     * where they were.
     */
    static List<Lode> walls(long seed, int rx, int rz, Columns base) {
        List<Lode> out = new ArrayList<>();
        // a column is a full noise evaluation: each one is read once per region's plan
        java.util.Map<Long, net.minecraft.world.level.NoiseColumn> seen = new java.util.HashMap<>();
        Columns cols = (x, z) -> seen.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> base.at(x, z));
        for (int w = 0; w < WALL_ORES.length; w++) {
            Ore ore = WALL_ORES[w];
            for (int k = 0; k < WALL_COUNT[w]; k++) {
                Rand r = new Rand(mix(seed, rx, rz, 1000 + ore.ordinal() * 31 + k));
                int size = Math.min(WALL_MAX_SIZE, ore.minSize + r.nextInt(ore.maxSize - ore.minSize + 1));
                for (int t = 0; t < WALL_TRIES; t++) {
                    int x = rx * REGION + 24 + r.nextInt(REGION - 48);
                    int z = rz * REGION + 24 + r.nextInt(REGION - 48);
                    int y = WALL_MIN_Y[w] + r.nextInt(WALL_MAX_Y[w] - WALL_MIN_Y[w] + 1);
                    int[] wall = wallHeart(cols, x, y, z, r.nextInt(4), WALL_MIN_Y[w], WALL_MAX_Y[w]);
                    if (wall == null) continue;
                    out.add(wallSeam(ore, wall, size, r, cols));
                    break;
                }
            }
        }
        return out;
    }

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /**
     * A cave wall near the column at (x, z): air you could stand in between {@code lo} and {@code hi} (nearest y first,
     * up to four spots), then out from it the four ways from {@code dir}, up to {@value #WALL_REACH} cells, to the first
     * rock at head height with rock behind it. Returns {x, y, z, way} (way: the DIRS index pointing into the rock), or null.
     */
    private static int @org.jspecify.annotations.Nullable [] wallHeart(Columns cols, int x, int y, int z, int dir, int lo, int hi) {
        net.minecraft.world.level.NoiseColumn c = cols.at(x, z);
        lo = Math.max(lo, FLOOR_Y + 2);
        int span = Math.max(y - lo, hi - y), tried = 0;
        for (int dy = 0; dy <= span && tried < 4; dy++) {
            for (int k = 0; k < 2; k++) {
                int air = k == 0 ? y - dy : y + dy;
                if (k == 1 && dy == 0 || air < lo || air > hi || !open(c, air)) continue;
                tried++;
                int head = air + 1;
                for (int i = 0; i < 4; i++) {
                    int way = (dir + i) & 3;
                    int[] d = DIRS[way];
                    for (int j = 1; j <= WALL_REACH; j++) {
                        var at = cols.at(x + d[0] * j, z + d[1] * j);
                        if (!at.getBlock(head).isAir() || !at.getBlock(air).isAir()) {
                            // the first thing in the way: a wall when it is rock at head height with rock behind
                            if (rock(at, head) && rock(cols.at(x + d[0] * (j + 1), z + d[1] * (j + 1)), head))
                                return new int[]{x + d[0] * j, head, z + d[1] * j, way};
                            break;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * A seam along a cave wall: from the heart, two arms step sideways along the wall, each step settling on the wall's
     * face (the rock cell with air on its cave side, looked for up to two cells in or out, and a cell up or down now and
     * then), a cell behind the face here and there for depth. An arm ends where the wall does.
     */
    private static Lode wallSeam(Ore ore, int[] wall, int size, Rand r, Columns cols) {
        int[] in = DIRS[wall[3]];
        int tx = -in[1], tz = in[0];   // along the wall
        LongLinkedOpenHashSet cells = new LongLinkedOpenHashSet();
        long heart = BlockPos.asLong(wall[0], wall[1], wall[2]);
        cells.add(heart);
        int[][] arm = {{wall[0], wall[1], wall[2]}, {wall[0], wall[1], wall[2]}};
        boolean[] done = new boolean[2];
        for (int step = 0; step < size * 3 && cells.size() < size && !(done[0] && done[1]); step++) {
            int a = step & 1;
            if (done[a]) continue;
            int sign = a == 0 ? 1 : -1;
            int nx = arm[a][0] + tx * sign, ny = arm[a][1], nz = arm[a][2] + tz * sign;
            double roll = r.next();
            if (roll < 0.12) ny++;
            else if (roll < 0.24) ny--;
            if (ny < FLOOR_Y + 1 || Math.abs(nx - wall[0]) > REACH - 2 || Math.abs(nz - wall[2]) > REACH - 2) {
                done[a] = true;
                continue;
            }
            int[] face = null;
            for (int o : new int[]{0, 1, -1, 2, -2}) {
                int fx = nx + in[0] * o, fz = nz + in[1] * o;
                if (rock(cols.at(fx, fz), ny) && cols.at(fx - in[0], fz - in[1]).getBlock(ny).isAir()) {
                    face = new int[]{fx, ny, fz};
                    break;
                }
            }
            if (face == null) {
                done[a] = true;
                continue;
            }
            arm[a] = face;
            cells.add(BlockPos.asLong(face[0], face[1], face[2]));
            // depth: a cell behind the face, or the face above or below
            if (cells.size() < size && r.next() < 0.45) {
                if (r.next() < 0.5) {
                    if (rock(cols.at(face[0] + in[0], face[2] + in[1]), face[1])) cells.add(BlockPos.asLong(face[0] + in[0], face[1], face[2] + in[1]));
                } else {
                    int oy = r.next() < 0.5 ? 1 : -1;
                    if (face[1] + oy >= FLOOR_Y && rock(cols.at(face[0], face[2]), face[1] + oy)) cells.add(BlockPos.asLong(face[0], face[1] + oy, face[2]));
                }
            }
        }
        long[] arr = cells.toLongArray();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long c : arr) {
            x0 = Math.min(x0, BlockPos.getX(c));
            y0 = Math.min(y0, BlockPos.getY(c));
            z0 = Math.min(z0, BlockPos.getZ(c));
            x1 = Math.max(x1, BlockPos.getX(c));
            y1 = Math.max(y1, BlockPos.getY(c));
            z1 = Math.max(z1, BlockPos.getZ(c));
        }
        return new Lode(heart, ore, heart, arr, x0, y0, z0, x1, y1, z1);
    }

    /** Cave air you could stand in: two cells of air over rock, no water. */
    private static boolean open(net.minecraft.world.level.NoiseColumn c, int y) {
        return c.getBlock(y).isAir() && c.getBlock(y + 1).isAir() && rock(c, y - 1);
    }

    private static boolean rock(net.minecraft.world.level.NoiseColumn c, int y) {
        var s = c.getBlock(y);
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    /** A lode laid by hand along the given blocks, the first being its heart (tests and the tour's demo lode). */
    /** Lays a handmade lode for commands and tests (W5's staged places): answered by every query until the server stops. */
    public static void stage(Lode l) {
        EXTRA.removeIf(x -> x.heartPos().distManhattan(l.heartPos()) < 64);
        EXTRA.add(l);
    }

    public static Lode handmade(Ore ore, long[] cells) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long c : cells) {
            x0 = Math.min(x0, BlockPos.getX(c));
            y0 = Math.min(y0, BlockPos.getY(c));
            z0 = Math.min(z0, BlockPos.getZ(c));
            x1 = Math.max(x1, BlockPos.getX(c));
            y1 = Math.max(y1, BlockPos.getY(c));
            z1 = Math.max(z1, BlockPos.getZ(c));
        }
        return new Lode(cells[0], ore, cells[0], cells.clone(), x0, y0, z0, x1, y1, z1);
    }

    static long mix(long seed, int rx, int rz, int salt) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h = splitmix(h + rx * 0xBF58476D1CE4E5B9L);
        h = splitmix(h + rz * 0x94D049BB133111EBL);
        return splitmix(h + salt * 0x2545F4914F6CDD1DL);
    }

    static long splitmix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A small deterministic stream (SplitMix64), the same on every thread and every run. */
    static final class Rand {
        private long s;

        Rand(long seed) {
            s = seed;
        }

        double next() {
            s += 0x9E3779B97F4A7C15L;
            return (splitmix(s) >>> 11) * 0x1.0p-53;
        }

        int nextInt(int bound) {
            return (int) Math.floor(next() * bound);
        }
    }
}
