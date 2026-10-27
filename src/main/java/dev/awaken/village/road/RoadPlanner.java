package dev.awaken.village.road;

import dev.awaken.village.plan.Terrain;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * One road's line over the land (SPEC §28.9): pure and deterministic from its two ends and the generator's estimate of
 * the land (never a chunk). A coarse search over a lattice inside an ellipse round the two ends finds the easiest way
 * (gentle grades, round hills rather than over them, no cliffs, water crossed where it's narrow, low passes over
 * ridges), its corners are cut, and the line is laid every {@link #STEP} blocks with a profile: the land's own height
 * smoothed, never climbing more than a block a point (two where the land itself is steep), held to the ends' heights
 * and to bridge decks over water, so cuttings and banks stay small.
 */
public final class RoadPlanner {
    private RoadPlanner() {}

    /** Blocks between the line's points. */
    public static final int STEP = 4;
    /** Point flags: a bridge deck over water, a boardwalk over a shallow mire, a causeway over a brook. */
    public static final byte BRIDGE = 1, BOARDWALK = 2, CAUSEWAY = 4;
    /** A dry stretch held high over a valley (a viaduct), and the points its piers stand on (8 and 16 are the painter's). */
    public static final byte VIADUCT = 32, PIER = 64;
    /** How far over the land a point stands before the road crosses on a viaduct instead of a bank. */
    static final int VIADUCT_RISE = 8;

    /** A column the road may not cross (another settlement's ground, the city's reserve, the camp). */
    @FunctionalInterface
    public interface Keep {
        boolean out(int x, int z);
    }

    /**
     * What to lay: the two ends (world x, z; the height a settlement's street meets the road at, or
     * {@link Integer#MIN_VALUE} for the land's own), the direction each end leaves in (unit x, z, or 0, 0: any), what it
     * keeps out of, whether the mire's shallows take boardwalks (the land's family at a column, see {@link Terrain#family}),
     * and whether it may bridge {@link #WIDE} water (the road north out of the camp, where no narrower way is found).
     */
    public record Request(int ax, int az, int ay, float adx, float adz, int bx, int bz, int by, float bdx, float bdz, Keep keep, boolean wide) {
        public Request(int ax, int az, int ay, float adx, float adz, int bx, int bz, int by, float bdx, float bdz, Keep keep) {
            this(ax, az, ay, adx, adz, bx, bz, by, bdx, bdz, keep, false);
        }
    }

    /** A wide crossing has land this near on both sides (a lake's narrows, a broad river): bridges to about twice it. */
    static final int WIDE = 48;

    /** The laid line: points every {@link #STEP} blocks with the road's surface height and flags; what it cost. */
    public record Path(float[] x, float[] z, int[] y, byte[] flags, int reads, long nanos) {
        public int points() {
            return x.length;
        }

        public double length() {
            return (x.length - 1) * (double) STEP;
        }
    }

    /** Why the last plan on this thread failed (studio). */
    public static final ThreadLocal<String> WHY = ThreadLocal.withInitial(() -> "");

    /** How much longer than the straight line the search may wander (the ellipse's sum of distances over the ends'). */
    static final double STRETCH = 1.45;
    static final int MAX_NODES = 40_000;

    public static @Nullable Path plan(Request q, Terrain t) {
        long t0 = System.nanoTime();
        Work w = new Work(q, t);
        Path p = w.run();
        if (p == null) return null;
        return new Path(p.x, p.z, p.y, p.flags, w.memo.size(), System.nanoTime() - t0);
    }

    private static final class Work {
        final Request q;
        final Terrain t;
        final Long2LongOpenHashMap memo = new Long2LongOpenHashMap();
        final int sea;
        /** Why steps were refused: outside the ellipse, kept ground, a cliff, open water, the sea's deeps. */
        final int[] refused = new int[5];

        Work(Request q, Terrain t) {
            this.q = q;
            this.t = t;
            this.sea = t.seaLevel();
            memo.defaultReturnValue(Long.MIN_VALUE);
        }

        long land(int x, int z) {
            long k = ((long) x << 32) ^ (z & 0xffffffffL);
            long v = memo.get(k);
            if (v == Long.MIN_VALUE) {
                v = t.both(x, z);
                memo.put(k, v);
            }
            return v;
        }

        int top(int x, int z) {
            return (int) (land(x, z) >> 32);
        }

        int water(int x, int z) {
            return (int) land(x, z);
        }

        @Nullable Path run() {
            WHY.set("");
            // the search's ends: a little out from each end the way it leaves (a street end runs on before it turns)
            float sx = q.ax() + q.adx() * 12, sz = q.az() + q.adz() * 12, ex = q.bx() + q.bdx() * 12, ez = q.bz() + q.bdz() * 12;
            double d = Math.hypot(ex - sx, ez - sz);
            int c = (int) Math.clamp(Math.round(d / 40.0), 12, 32);
            List<float[]> coarse = d < 2 * c ? new ArrayList<>(List.of(new float[]{sx, sz}, new float[]{ex, ez})) : search(sx, sz, ex, ez, c, d);
            if (coarse == null) return null;
            // the whole line: the ends themselves, out along their ways, the search's way between, its corners cut
            List<float[]> line = new ArrayList<>();
            line.add(new float[]{q.ax(), q.az()});
            if (q.adx() != 0 || q.adz() != 0) line.add(new float[]{q.ax() + q.adx() * 6, q.az() + q.adz() * 6});
            line.addAll(coarse);
            if (q.bdx() != 0 || q.bdz() != 0) line.add(new float[]{q.bx() + q.bdx() * 6, q.bz() + q.bdz() * 6});
            line.add(new float[]{q.bx(), q.bz()});
            line = chaikin(chaikin(line));
            return lay(line);
        }

        // ---- the coarse search ----------------------------------------------------------------------------------------

        /** A* over the c-block lattice inside the ellipse, from the lattice point nearest s to the one nearest e. */
        @Nullable List<float[]> search(float sx, float sz, float ex, float ez, int c, double d) {
            int si = Math.round(sx / c), sj = Math.round(sz / c), gi = Math.round(ex / c), gj = Math.round(ez / c);
            double reach = d * STRETCH + 2.0 * c;
            Long2DoubleOpenHashMap g = new Long2DoubleOpenHashMap();
            g.defaultReturnValue(Double.MAX_VALUE);
            Long2LongOpenHashMap from = new Long2LongOpenHashMap();
            PriorityQueue<double[]> open = new PriorityQueue<>((u, v) -> Double.compare(u[0], v[0]));
            long start = key(si, sj), goal = key(gi, gj);
            g.put(start, 0);
            open.add(new double[]{Math.hypot(gi - si, gj - sj) * c, start});
            int expanded = 0;
            it.unimi.dsi.fastutil.longs.LongOpenHashSet closed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            while (!open.isEmpty()) {
                double[] top = open.poll();
                long k = (long) top[1];
                if (!closed.add(k)) continue;
                if (k == goal) break;
                if (++expanded > MAX_NODES) {
                    WHY.set("search too long");
                    return null;
                }
                int i = (int) (k >> 32), j = (int) k;
                int x = i * c, z = j * c;
                long l1 = land(x, z);
                int h1 = (int) (l1 >> 32), w1 = (int) l1;
                double gk = g.get(k);
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if (di == 0 && dj == 0) continue;
                        int ni = i + di, nj = j + dj;
                        long nk = key(ni, nj);
                        if (closed.contains(nk)) continue;
                        int nx = ni * c, nz = nj * c;
                        if (Math.hypot(nx - sx, nz - sz) + Math.hypot(nx - ex, nz - ez) > reach) {
                            refused[0]++;
                            continue;
                        }
                        boolean end = nk == goal || nk == start;
                        // the ends' own neighbourhood is never kept (a street's end stands at its settlement's edge)
                        boolean nearEnd = Math.hypot(nx - sx, nz - sz) < 1.6 * c || Math.hypot(nx - ex, nz - ez) < 1.6 * c;
                        if (!end && !nearEnd && q.keep().out(nx, nz)) {
                            refused[1]++;
                            continue;
                        }
                        double cost = step(h1, w1, nx, nz, di != 0 && dj != 0 ? c * Math.sqrt(2) : c, end, c);
                        if (cost == Double.MAX_VALUE) continue;
                        double ng = gk + cost;
                        if (ng < g.get(nk)) {
                            g.put(nk, ng);
                            from.put(nk, k);
                            open.add(new double[]{ng + Math.hypot(gi - ni, gj - nj) * c, nk});
                        }
                    }
                }
            }
            if (!closed.contains(goal)) {
                if (WHY.get().isEmpty()) WHY.set(String.format(java.util.Locale.ROOT,
                        "no way round: %d nodes, refused %d outside, %d kept, %d cliffs, %d open water, %d deep sea", closed.size(), refused[0], refused[1],
                        refused[2], refused[3], refused[4]));
                return null;
            }
            List<float[]> out = new ArrayList<>();
            for (long k = goal; ; k = from.get(k)) {
                out.addFirst(new float[]{(int) (k >> 32) * c, (int) k * c});
                if (k == start) break;
            }
            return out;
        }

        static long key(int i, int j) {
            return ((long) i << 32) ^ (j & 0xffffffffL);
        }

        /** The cost of a step of length {@code len} onto (nx, nz) from ground at h1 with water w1 over it. */
        double step(int h1, int w1, int nx, int nz, double len, boolean end, int c) {
            long l2 = land(nx, nz);
            int h2 = (int) (l2 >> 32), w2 = (int) l2;
            // water is crossed only where it's narrow: land on both sides within a step (a river, a strait), never along a
            // shore or out over a lake or the sea
            if (w2 > 0 && !end && !narrow(nx, nz, c)) {
                refused[3]++;
                return Double.MAX_VALUE;
            }
            int dh = Math.abs(h2 - h1);
            double grade = dh / len;
            boolean dry = w1 == 0 && w2 == 0;
            if (dry && grade > 0.6 && !end) { // a cliff
                refused[2]++;
                return Double.MAX_VALUE;
            }
            if (w2 > 0 && h2 - w2 < sea - 12 && !end) { // the open sea's deeps
                refused[4]++;
                return Double.MAX_VALUE;
            }
            double cost = len;
            cost += len * grade * grade * 40;
            if (grade > 0.3) cost += len * (grade - 0.3) * 30;
            if (w2 > 0) cost += len * (w2 > 4 ? 12 : 6) + (w1 == 0 ? 60 : 0);
            cost += len * Math.max(0, h2 - 100) * 0.04; // keep low: passes, not peaks
            return cost;
        }

        /** Land a step away on two opposite sides (either axis or diagonal). */
        boolean narrow(int x, int z, int c) {
            int[][] pairs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
            for (int[] d : pairs) {
                if (water(x + d[0] * c, z + d[1] * c) == 0 && water(x - d[0] * c, z - d[1] * c) == 0) return true;
                // (a wide crossing) land further out on both sides, a step at a time
                for (int m = 2; q.wide() && m * c <= WIDE; m++)
                    if (shore(x, z, d[0] * c, d[1] * c, m) && shore(x, z, -d[0] * c, -d[1] * c, m)) return true;
            }
            return false;
        }

        /** Land within m steps of (x, z) one way. */
        boolean shore(int x, int z, int sx, int sz, int m) {
            for (int k = 1; k <= m; k++) if (water(x + sx * k, z + sz * k) == 0) return true;
            return false;
        }

        static List<float[]> chaikin(List<float[]> pts) {
            if (pts.size() < 3) return pts;
            List<float[]> out = new ArrayList<>();
            out.add(pts.getFirst());
            for (int k = 0; k + 1 < pts.size(); k++) {
                float[] a = pts.get(k), b = pts.get(k + 1);
                if (k > 0) out.add(new float[]{a[0] * 0.75f + b[0] * 0.25f, a[1] * 0.75f + b[1] * 0.25f});
                if (k + 2 < pts.size()) out.add(new float[]{a[0] * 0.25f + b[0] * 0.75f, a[1] * 0.25f + b[1] * 0.75f});
            }
            out.add(pts.getLast());
            return out;
        }

        // ---- the line and its profile ------------------------------------------------------------------------------------

        /** The line every STEP blocks, its heights and its bridges. */
        @Nullable Path lay(List<float[]> line) {
            List<float[]> pts = new ArrayList<>();
            pts.add(line.getFirst());
            double carry = 0;
            for (int k = 0; k + 1 < line.size(); k++) {
                float[] a = line.get(k), b = line.get(k + 1);
                double seg = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double at = STEP - carry;
                while (at <= seg) {
                    double f = at / seg;
                    pts.add(new float[]{(float) (a[0] + (b[0] - a[0]) * f), (float) (a[1] + (b[1] - a[1]) * f)});
                    at += STEP;
                }
                carry = seg - (at - STEP);
            }
            float[] last = line.getLast();
            if (Math.hypot(pts.getLast()[0] - last[0], pts.getLast()[1] - last[1]) > 0.5) pts.add(last);
            int n = pts.size();
            if (n < 2) return null;
            float[] xs = new float[n], zs = new float[n];
            int[] top = new int[n], wet = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = pts.get(i)[0];
                zs[i] = pts.get(i)[1];
                long l = land(Math.round(xs[i]), Math.round(zs[i]));
                top[i] = (int) (l >> 32);
                wet[i] = (int) l;
            }
            // the ends stand on land: a street's end or a crossroads
            wet[0] = 0;
            wet[n - 1] = 0;
            byte[] flags = new byte[n];
            boolean[] fixed = new boolean[n];
            int[] y = new int[n];
            // water: brooks are walked on a causeway, the mire's shallows on boardwalks, the rest bridged level over the span
            for (int i = 0; i < n; ) {
                if (wet[i] == 0) {
                    i++;
                    continue;
                }
                int j = i;
                int deepest = 0, surface = Integer.MIN_VALUE;
                while (j < n && wet[j] > 0) {
                    deepest = Math.max(deepest, wet[j]);
                    surface = Math.max(surface, top[j]);
                    j++;
                }
                int span = j - i;
                boolean mire = deepest <= 2 && t.family(Math.round(xs[(i + j) / 2]), Math.round(zs[(i + j) / 2])) == dev.awaken.city.VillageStyles.MIRE;
                byte kind = span <= 2 && deepest <= 1 ? CAUSEWAY : mire ? BOARDWALK : BRIDGE;
                int deck = surface + (kind == BRIDGE ? 2 : 1);
                for (int k = i; k < j; k++) {
                    flags[k] = kind;
                    y[k] = deck;
                    fixed[k] = true;
                }
                // a bridge's ends rest on the banks at its deck's height
                if (kind == BRIDGE) {
                    if (i > 0) {
                        flags[i - 1] = BRIDGE;
                        y[i - 1] = deck;
                        fixed[i - 1] = true;
                    }
                    if (j < n) {
                        flags[j] = BRIDGE;
                        y[j] = deck;
                        fixed[j] = true;
                    }
                }
                i = j + 1;
            }
            // the land's own height, smoothed over a dozen blocks either way (water left out)
            float[] s = new float[n];
            for (int i = 0; i < n; i++) {
                float sum = 0;
                int cnt = 0;
                for (int k = Math.max(0, i - 3); k <= Math.min(n - 1, i + 3); k++) {
                    if (wet[k] > 0) continue;
                    sum += top[k];
                    cnt++;
                }
                s[i] = cnt == 0 ? top[i] : sum / cnt;
            }
            for (int i = 0; i < n; i++) if (!fixed[i]) y[i] = Math.round(s[i]);
            if (q.ay() != Integer.MIN_VALUE) {
                y[0] = q.ay();
                fixed[0] = true;
            }
            if (q.by() != Integer.MIN_VALUE) {
                y[n - 1] = q.by();
                fixed[n - 1] = true;
            }
            // a block a point (a slab's half step every two blocks at most), two where the land itself climbs that fast
            int[] g = new int[n];
            for (int i = 0; i < n; i++) {
                int lo = Math.max(0, i - 3), hi = Math.min(n - 1, i + 3);
                float steep = Math.abs(s[hi] - s[lo]) / Math.max(1, hi - lo);
                g[i] = steep > 1.2f ? 2 : 1;
            }
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 1; i < n; i++) if (!fixed[i]) y[i] = Math.clamp(y[i], y[i - 1] - g[i], y[i - 1] + g[i]);
                for (int i = n - 2; i >= 0; i--) if (!fixed[i]) y[i] = Math.clamp(y[i], y[i + 1] - g[i + 1], y[i + 1] + g[i + 1]);
            }
            viaducts(y, top, flags);
            return new Path(xs, zs, y, flags, 0, 0);
        }
    }

    /**
     * Where the road holds its grade {@link #VIADUCT_RISE} or more over dry land it crosses on a viaduct (SPEC §28.9): the
     * run and a point either side (the abutments, on the land) take VIADUCT, and piers stand on the abutments and evenly
     * between, two to four points apart (about every 12 blocks), so every span is one arch.
     */
    static void viaducts(int[] y, int[] top, byte[] flags) {
        int n = y.length;
        for (int i = 1; i + 1 < n; ) {
            if (flags[i] != 0 || y[i] - top[i] < VIADUCT_RISE) {
                i++;
                continue;
            }
            int j = i;
            while (j + 2 < n && flags[j + 1] == 0 && y[j + 1] - top[j + 1] >= VIADUCT_RISE) j++;
            int a = (flags[i - 1] & (BRIDGE | BOARDWALK | CAUSEWAY)) == 0 ? i - 1 : i, b = (flags[j + 1] & (BRIDGE | BOARDWALK | CAUSEWAY)) == 0 ? j + 1 : j;
            if (b - a >= 2) {
                for (int k = a; k <= b; k++) flags[k] |= VIADUCT;
                int m = Math.max(1, Math.round((b - a) / 3f));
                for (int q = 0; q <= m; q++) flags[a + Math.round(q * (b - a) / (float) m)] |= PIER;
            }
            i = j + 1;
        }
    }
}
