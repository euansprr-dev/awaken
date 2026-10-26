package dev.awaken.delve;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private static final ConcurrentHashMap<Key, List<Lode>> CACHE = new ConcurrentHashMap<>();
    /** Lodes a test laid by hand: answered by every query, never written by worldgen. */
    static final List<Lode> EXTRA = new CopyOnWriteArrayList<>();

    /** The lodes planned for region (rx, rz). */
    public static List<Lode> region(long seed, int rx, int rz) {
        if (CACHE.size() > 4096) CACHE.clear();
        return CACHE.computeIfAbsent(new Key(seed, rx, rz), k -> plan(k.seed, k.rx, k.rz));
    }

    /** Every lode whose box reaches the column box (x0, z0)-(x1, z1), test lodes included when {@code extra}. */
    static List<Lode> touching(long seed, int x0, int z0, int x1, int z1, boolean extra) {
        List<Lode> out = new ArrayList<>();
        int rx0 = Math.floorDiv(x0 - REACH, REGION), rx1 = Math.floorDiv(x1 + REACH, REGION);
        int rz0 = Math.floorDiv(z0 - REACH, REGION), rz1 = Math.floorDiv(z1 + REACH, REGION);
        for (int rx = rx0; rx <= rx1; rx++) for (int rz = rz0; rz <= rz1; rz++) {
            for (Lode l : region(seed, rx, rz)) if (l.touches(x0, z0, x1, z1)) out.add(l);
        }
        if (extra) for (Lode l : EXTRA) if (l.touches(x0, z0, x1, z1)) out.add(l);
        return out;
    }

    /** The lode one of whose blocks is at {@code pos}, or null. */
    static Lode at(long seed, long pos) {
        int x = BlockPos.getX(pos), z = BlockPos.getZ(pos);
        for (Lode l : touching(seed, x, z, x, z, true)) if (l.holds(pos)) return l;
        return null;
    }

    // ---- the plan -----------------------------------------------------------------------------------------------------

    private static List<Lode> plan(long seed, int rx, int rz) {
        List<Lode> out = new ArrayList<>();
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
        LongLinkedOpenHashSet cells = new LongLinkedOpenHashSet();
        long heart = BlockPos.asLong(x, y, z);
        cells.add(heart);
        double yaw = r.next() * Math.PI * 2, pitch = (r.next() - 0.5) * 0.5;
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

    /** A lode laid by hand along the given blocks, the first being its heart (tests and the tour's demo lode). */
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
