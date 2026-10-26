package dev.awaken.village.plan;

import dev.awaken.city.CityPlan;
import dev.awaken.city.VillageStyles;
import dev.awaken.village.plan.VillagePlan.Kind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;

import static dev.awaken.city.CityPlan.*;

/**
 * The settlement planner (SPEC §28.6, PLAN 5.3): from a seed and an anchor, a site on gentle ground near water, a road
 * skeleton that follows the contours (a main street through a green with the well, the bell and the notice board,
 * lanes off it), plots along the frontages with buildings chosen by size and land (cottages, two-storey houses, the
 * inn, the smithy with its forge, the mill on water, barns and the granary), strip fields and pens beyond, a dock where
 * there's water, lamps along the streets, and the households who live there with their jobs. Pure and deterministic:
 * the same request over the same land always makes the same plan, and it reads nothing but {@link Terrain} (the
 * generator's own estimate: no chunk loads). Once per settlement; the result is cached by {@code Plans}.
 */
public final class VillagePlanner {
    private VillagePlanner() {}

    /**
     * What to plan: a stable key, the seed, where to look (the anchor; the site moves up to {@link #SEARCH} from it), the
     * kind, whether this is the first hamlet (placed on purpose up the camp road: always modest, never skipped for a
     * merely fair site), and the world window the plan must stay inside (the structure's reach), plus a keep-out test
     * (the city's reserve) over world columns.
     */
    public record Request(String key, long seed, int anchorX, int anchorZ, Kind kind, boolean first, int minX, int minZ, int maxX, int maxZ,
                          BiPredicate<Integer, Integer> reserved) {
        /** The same request for another kind (a village that has no room plans as a hamlet). */
        public Request as(Kind k) {
            return new Request(key, seed, anchorX, anchorZ, k, first, minX, minZ, maxX, maxZ, reserved);
        }
    }

    public static final int SEARCH = 32, FIRST_SEARCH = 64;
    static final int STEP = 6;

    /** Whether a site near the anchor takes a settlement (the site test alone, no plan: cheap). */
    public static boolean hasSite(Request q, Terrain t) {
        return new Work(q, t).site();
    }

    /** Why the last plan on this thread found no site (studio and survey diagnostics). */
    public static final ThreadLocal<String> WHY = ThreadLocal.withInitial(() -> "");

    /**
     * The plan, or null when no site near the anchor suits (sea, a cliff, a lake, the city's reserve). A village with no
     * room for itself becomes a hamlet on the same anchor.
     */
    public static @Nullable VillagePlan plan(Request q, Terrain t) {
        // (SPEC §28.9) a region's town, a crossroads' wayhouse, a mining town (where no mountain is near, a village)
        switch (q.kind()) {
            case TOWN -> {
                return new TownPlanner.TownWork(q, t).run();
            }
            case WAYHOUSE -> {
                return new WayhousePlanner.WayWork(q, t).run();
            }
            case MINING -> {
                VillagePlan m = MinePlanner.plan(q, t);
                if (m != null) return m;
                q = q.as(Kind.VILLAGE);
            }
            default -> {
            }
        }
        VillagePlan p = new Work(q, t).run();
        if (p == null && q.kind() == Kind.VILLAGE) {
            String why = WHY.get();
            p = new Work(q.as(Kind.HAMLET), t).run();
            if (p == null) WHY.set("village: " + why + "; hamlet: " + WHY.get());
        }
        return p;
    }

    // ---- the work of one plan -------------------------------------------------------------------------------------

    static class Work {
        final Request q;
        final Terrain t;
        final Random rnd;
        final boolean village;
        int cx, cz, r, baseY, gx0, gz0, gn;
        float[] top;
        byte[] wet;
        VillagePlan plan;
        /** Street cells' distance to their centre line (to let the nearest segment set a cell's height). */
        float[] streetDist;

        Work(Request q, Terrain t) {
            this.q = q;
            this.t = t;
            this.rnd = new Random(q.seed() ^ 0x77A1_1A6EL);
            this.village = q.kind() == Kind.VILLAGE || q.kind() == Kind.MINING || q.kind() == Kind.TOWN;
        }

        /** The land at a column, read once ({top, water}). */
        private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap memo = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

        private long land(int x, int z) {
            long k = ((long) x << 32) ^ (z & 0xffffffffL);
            long v = memo.getOrDefault(k, Long.MIN_VALUE);
            if (v == Long.MIN_VALUE) {
                v = t.both(x, z);
                memo.put(k, v);
            }
            return v;
        }

        int topAt(int x, int z) {
            return (int) (land(x, z) >> 32);
        }

        int waterAt(int x, int z) {
            return (int) land(x, z);
        }

        @Nullable VillagePlan run() {
            if (!site()) {
                if (WHY.get().isEmpty()) WHY.set("no site");
                return null;
            }
            sample();
            int fam = family();
            int wealth = q.first() ? VillageStyles.MODEST : village ? (rnd.nextInt(100) < 55 ? VillageStyles.MODEST : VillageStyles.PROSPEROUS)
                    : (rnd.nextInt(100) < 55 ? VillageStyles.POOR : VillageStyles.MODEST);
            String name = Names.settlement(fam, q.seed());
            plan = new VillagePlan(q.key(), name, q.kind(), fam, wealth, q.seed(), cx, cz, baseY, r);
            streetDist = new float[plan.n * plan.n];
            Arrays.fill(streetDist, Float.MAX_VALUE);
            ground();
            green();
            streets();
            keepExits();
            buildings();
            if (plan.plots.size() < (village ? 6 : q.first() ? 2 : 3)) {
                WHY.set("only " + plan.plots.size() + " buildings fit");
                return null;
            }
            mill();
            dock();
            gardens();
            fields();
            pens();
            trees();
            lines();
            lamps();
            props();
            people();
            plan.index();
            return plan;
        }

        // ---- the site: gentle ground near water -------------------------------------------------------------------

        boolean site() {
            WHY.set("");
            int core = village ? 36 : 26;
            double best = Double.NEGATIVE_INFINITY;
            int bx = 0, bz = 0;
            int search = q.first() ? FIRST_SEARCH : SEARCH;
            for (int dz = -search; dz <= search; dz += 16) {
                for (int dx = -search; dx <= search; dx += 16) {
                    int x = q.anchorX() + dx, z = q.anchorZ() + dz;
                    double s = score(x, z, core) - 0.02 * (dx * dx + dz * dz) / 16.0;
                    if (s > best) {
                        best = s;
                        bx = x;
                        bz = z;
                    }
                }
            }
            if (best == Double.NEGATIVE_INFINITY || best < (q.first() ? -80 : -34)) {
                WHY.set(best == Double.NEGATIVE_INFINITY ? "no dry, gentle ground" : String.format(java.util.Locale.ROOT, "best ground too rough (%.0f)", best));
                return false;
            }
            cx = bx;
            cz = bz;
            int want = village ? 88 : 58;
            int room = Math.min(Math.min(cx - q.minX(), q.maxX() - cx), Math.min(cz - q.minZ(), q.maxZ() - cz)) - 2;
            r = Math.min(want, room);
            if (r < (village ? 56 : 38)) WHY.set("no room in the structure's reach");
            return r >= (village ? 56 : 38);
        }

        /** How well a centre suits: flat dry ground at its core, water near but not in it, not too high or too low. */
        double score(int x, int z, int core) {
            int n = 0, wetN = 0;
            double sum = 0, sum2 = 0;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int dz = -core; dz <= core; dz += 8) {
                for (int dx = -core; dx <= core; dx += 8) {
                    if (dx * dx + dz * dz > core * core) continue;
                    if (q.reserved().test(x + dx, z + dz)) return Double.NEGATIVE_INFINITY;
                    int h = topAt(x + dx, z + dz);
                    if (waterAt(x + dx, z + dz) > 0) {
                        wetN++;
                        continue;
                    }
                    n++;
                    sum += h;
                    sum2 += (double) h * h;
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
            }
            if (n < 8 || wetN > (n + wetN) * (q.first() ? 0.3 : 0.15)) return Double.NEGATIVE_INFINITY;
            double mean = sum / n, sd = Math.sqrt(Math.max(0, sum2 / n - mean * mean));
            int range = hi - lo;
            if (range > (village ? 18 : q.first() ? 22 : 16) || mean < t.seaLevel() + 1 || mean > 150) return Double.NEGATIVE_INFINITY;
            // water near: a river, a lake or the sea within forty blocks of the core's edge
            int near = 0;
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8;
                for (int d = core + 8; d <= core + 40; d += 16) {
                    // on the same eight-block lattice as the core's reads (each column is read once)
                    int wx = x + 8 * (int) Math.round(Math.cos(ang) * d / 8), wz = z + 8 * (int) Math.round(Math.sin(ang) * d / 8);
                    if (waterAt(wx, wz) > 0) {
                        near++;
                        break;
                    }
                }
            }
            return -sd * 2.5 - range * 0.6 - wetN * 4 + Math.min(near, 4) * 2.5 - Math.abs(mean - 75) * 0.04;
        }

        /** The heights and water on a 4-block grid over the plan (the generator's estimate, read once). */
        void sample() {
            gx0 = cx - r - STEP;
            gz0 = cz - r - STEP;
            gn = (2 * r + 2 * STEP) / STEP + 3;
            top = new float[gn * gn];
            wet = new byte[gn * gn];
            for (int j = 0; j < gn; j++) {
                for (int i = 0; i < gn; i++) {
                    int x = gx0 + i * STEP, z = gz0 + j * STEP;
                    top[j * gn + i] = topAt(x, z);
                    wet[j * gn + i] = (byte) Math.min(100, waterAt(x, z));
                }
            }
            // the street level: the dry core's median
            List<Integer> core = new ArrayList<>();
            for (int dz = -12; dz <= 12; dz += 4)
                for (int dx = -12; dx <= 12; dx += 4) if (water(dx, dz) < 0.5f) core.add(Math.round(height(dx, dz)));
            core.sort(null);
            baseY = core.isEmpty() ? Math.round(height(0, 0)) : core.get(core.size() / 2);
        }

        /** The land's top at a plan cell (bilinear over the grid). */
        float height(int px, int pz) {
            return bilinear(top, px, pz);
        }

        float water(int px, int pz) {
            return bilinear(wet, px, pz);
        }

        private float bilinear(float[] a, int px, int pz) {
            float fx = (cx + px - gx0) / (float) STEP, fz = (cz + pz - gz0) / (float) STEP;
            int i = Math.clamp((int) Math.floor(fx), 0, gn - 2), j = Math.clamp((int) Math.floor(fz), 0, gn - 2);
            float u = Math.clamp(fx - i, 0f, 1f), v = Math.clamp(fz - j, 0f, 1f);
            return a[j * gn + i] * (1 - u) * (1 - v) + a[j * gn + i + 1] * u * (1 - v) + a[(j + 1) * gn + i] * (1 - u) * v + a[(j + 1) * gn + i + 1] * u * v;
        }

        private float bilinear(byte[] a, int px, int pz) {
            float fx = (cx + px - gx0) / (float) STEP, fz = (cz + pz - gz0) / (float) STEP;
            int i = Math.clamp((int) Math.floor(fx), 0, gn - 2), j = Math.clamp((int) Math.floor(fz), 0, gn - 2);
            float u = Math.clamp(fx - i, 0f, 1f), v = Math.clamp(fz - j, 0f, 1f);
            return a[j * gn + i] * (1 - u) * (1 - v) + a[j * gn + i + 1] * u * (1 - v) + a[(j + 1) * gn + i] * (1 - u) * v + a[(j + 1) * gn + i + 1] * u * v;
        }

        int family() {
            int[] votes = new int[VillageStyles.FAMILIES.length];
            for (int dz = -16; dz <= 16; dz += 16) for (int dx = -16; dx <= 16; dx += 16) votes[Math.clamp(t.family(cx + dx, cz + dz), 0, votes.length - 1)]++;
            int fam = 0;
            for (int k = 1; k < votes.length; k++) if (votes[k] > votes[fam]) fam = k;
            // the sea within reach turns a temperate place into a fishing village
            if (fam == VillageStyles.TEMPERATE || fam == VillageStyles.HIGHLAND) {
                int sea = 0;
                for (int j = 0; j < gn; j += 2)
                    for (int i = 0; i < gn; i += 2) if (wet[j * gn + i] >= 3 && top[j * gn + i] <= t.seaLevel() + 1) sea++;
                if (sea > gn * gn / 40) fam = VillageStyles.COAST;
            }
            return fam;
        }

        // ---- the ground ---------------------------------------------------------------------------------------------

        void ground() {
            for (int z = -r; z <= r; z++) {
                for (int x = -r; x <= r; x++) {
                    if (x * x + z * z > r * r) continue;
                    boolean wetCell = water(x, z) >= 0.5f;
                    plan.set(x, z, wetCell ? VillagePlan.WATER : FREE, Math.round(height(x, z)) - baseY);
                }
            }
        }

        boolean free(int x, int z) {
            return plan.kind(x, z) == FREE && !kept(x, z);
        }

        /** (SPEC §28.9) The ground kept clear beyond each street end roads leave by: no field, pen, garden, tree or house there. */
        boolean[] kept;

        boolean kept(int x, int z) {
            return kept != null && plan.inRaster(x, z) && kept[plan.idx(x, z)];
        }

        /** Keeps a way fourteen wide clear from each exit's street end out to the raster's edge, for the road. */
        void keepExits() {
            kept = new boolean[plan.n * plan.n];
            for (VillagePlan.Street st : plan.streets) {
                if (!plan.exits(st) || st.points() < 2) continue;
                for (int end = 0; end < 2; end++) {
                    if (!plan.exitAt(st, end)) continue;
                    int a = end == 0 ? 0 : st.points() - 1, b = end == 0 ? 1 : st.points() - 2;
                    float dx = st.x()[a] - st.x()[b], dz = st.z()[a] - st.z()[b];
                    float l = (float) Math.hypot(dx, dz);
                    if (l < 1e-3f) continue;
                    dx /= l;
                    dz /= l;
                    for (int k = 0; k <= 2 * r; k++) {
                        float px = st.x()[a] + dx * k, pz = st.z()[a] + dz * k;
                        if (!plan.inRaster(Math.round(px), Math.round(pz))) break;
                        for (int w = -7; w <= 7; w++) {
                            int cxk = Math.round(px - dz * w), czk = Math.round(pz + dx * w);
                            if (plan.inRaster(cxk, czk)) kept[plan.idx(cxk, czk)] = true;
                        }
                    }
                }
            }
        }

        boolean dry(int x, int z) {
            byte k = plan.kind(x, z);
            return k != VillagePlan.WATER && k != VOID;
        }

        // ---- the green and the streets ------------------------------------------------------------------------------

        void green() {
            int g = village ? 7 : 5;
            plan.greenR = g;
            for (int z = -g - 1; z <= g + 1; z++) {
                for (int x = -g - 1; x <= g + 1; x++) {
                    double e = Math.pow(Math.abs(x) / (g + 0.5), 3) + Math.pow(Math.abs(z) / (g + 0.5), 3);
                    if (e <= 1 && dry(x, z)) plan.set(x, z, SQUARE, 0);
                }
            }
        }

        void streets() {
            // the main street runs along the contour through the green
            float gxh = height(12, 0) - height(-12, 0), gzh = height(0, 12) - height(0, -12);
            double heading;
            if (Math.hypot(gxh, gzh) < 1.5) heading = rnd.nextDouble() * Math.PI;
            else heading = Math.atan2(gzh, gxh) + Math.PI / 2;
            int len = village ? 40 + rnd.nextInt(26) : 24 + rnd.nextInt(14);
            List<float[]> a = walk(0, 0, heading, len, 1.0), b = walk(0, 0, heading + Math.PI, len, 1.0);
            List<float[]> main = new ArrayList<>();
            for (int k = b.size() - 1; k >= 1; k--) main.add(b.get(k));
            main.addAll(a);
            int centre = b.size() - 1;
            addStreet(0, village ? 4.2f : 3.4f, main, centre);
            // the Watch's posts at both ends
            float[] e0 = main.getFirst(), e1 = main.getLast();
            plan.posts.add(new int[]{Math.round(e0[0]), Math.round(e0[1]), plan.streets.getFirst().lift()[0]});
            int[] ml = plan.streets.getFirst().lift();
            plan.posts.add(new int[]{Math.round(e1[0]), Math.round(e1[1]), ml[ml.length - 1]});
            // lanes branch off it, alternating sides, never in the green
            int lanes = village ? 2 + rnd.nextInt(3) : q.first() || rnd.nextInt(100) < 50 ? 1 : 0;
            VillagePlan.Street st = plan.streets.getFirst();
            int made = 0;
            List<Integer> starts = new ArrayList<>();
            for (int k = 2; k < st.points() - 2; k++) {
                if (Math.hypot(st.x()[k], st.z()[k]) < plan.greenR + 6) continue;
                starts.add(k);
            }
            java.util.Collections.shuffle(starts, rnd);
            List<Integer> used = new ArrayList<>();
            for (int k : starts) {
                if (made >= lanes) break;
                boolean close = false;
                for (int u : used) close |= Math.abs(u - k) < 5;
                if (close) continue;
                float dx = st.x()[k + 1] - st.x()[k - 1], dz = st.z()[k + 1] - st.z()[k - 1];
                double along = Math.atan2(dz, dx);
                double side = (made % 2 == 0 ? 1 : -1) * Math.PI / 2;
                List<float[]> lane = walk(st.x()[k], st.z()[k], along + side, village ? 18 + rnd.nextInt(18) : 12 + rnd.nextInt(8), 1.5);
                if (lane.size() < 4) lane = walk(st.x()[k], st.z()[k], along - side, village ? 18 + rnd.nextInt(18) : 12 + rnd.nextInt(8), 1.5);
                if (lane.size() < 4) continue;
                addStreet(1, 3.2f, lane, 0, st.lift()[k]);
                used.add(k);
                made++;
            }
        }

        /** Where a street may run (a town keeps its lanes inside its walls). */
        boolean walkable(int x, int z) {
            return x * x + z * z <= (r - 12) * (r - 12);
        }

        /** Where a building, garden or tree may stand. */
        boolean within(int x, int z, int margin) {
            return x * x + z * z <= (r - margin) * (r - margin);
        }

        /** How far out from the green fields and pens lie: {nearest, furthest}. */
        int[] fieldBand() {
            return new int[]{plan.greenR + 10, plan.greenR + 10 + Math.max(1, Math.min(r - plan.greenR - 24, 48))};
        }

        /** A street's centre line from (x, z) toward a heading, step by step along the easiest ground; stops at water, cliffs, the edge. */
        List<float[]> walk(float x0, float z0, double heading, int length, double climb) {
            List<float[]> pts = new ArrayList<>();
            pts.add(new float[]{x0, z0});
            float x = x0, z = z0;
            double h = heading;
            int steps = length / STEP;
            for (int s = 0; s < steps; s++) {
                double bestCost = Double.MAX_VALUE, bestH = 0;
                float bx = 0, bz = 0;
                for (int turn = -2; turn <= 2; turn++) {
                    double th = h + turn * Math.toRadians(12);
                    float nx = x + (float) (Math.cos(th) * STEP), nz = z + (float) (Math.sin(th) * STEP);
                    int ix = Math.round(nx), iz = Math.round(nz);
                    if (!walkable(ix, iz) || !dry(ix, iz) || water(ix, iz) > 0.2f) continue;
                    if (q.reserved().test(cx + ix, cz + iz)) continue;
                    byte k = plan.kind(ix, iz);
                    if ((k == STREET || k == LANE) && s > 1 && !joins()) continue; // never back across a street (a town's lane joins it)
                    float dh = Math.abs(height(ix, iz) - height(Math.round(x), Math.round(z)));
                    double cost = dh * 3 + Math.abs(turn) * 0.7 + Math.abs(height(ix, iz) - baseY) * 0.05;
                    if (dh > 3 * climb) continue;
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestH = th;
                        bx = nx;
                        bz = nz;
                    }
                }
                if (bestCost == Double.MAX_VALUE) break;
                // (a town's grid) a lane that meets another street stops there: a T-junction, never a crossing
                if (joins() && s > 0) {
                    float[] hit = meets(x, z, bx, bz);
                    if (hit != null) {
                        pts.add(hit);
                        break;
                    }
                }
                x = bx;
                z = bz;
                h = bestH;
                pts.add(new float[]{x, z});
            }
            return pts;
        }

        /** Whether lanes end where they meet another street (a town's grid; a village's lanes keep W3's ways). */
        boolean joins() {
            return false;
        }

        /** The first street cell on the step from (x, z) to (bx, bz), a block at a time, or null. */
        float @Nullable [] meets(float x, float z, float bx, float bz) {
            int n = (int) Math.ceil(Math.hypot(bx - x, bz - z));
            for (int k = 1; k <= n; k++) {
                float px = x + (bx - x) * k / n, pz = z + (bz - z) * k / n;
                byte kk = plan.kind(Math.round(px), Math.round(pz));
                if (kk == STREET || kk == LANE) return new float[]{px, pz};
            }
            return null;
        }

        void addStreet(int rank, float width, List<float[]> pts, int fixedIndex) {
            addStreet(rank, width, pts, fixedIndex, 0);
        }

        /** Smooths a centre line's heights (never more than one up or down every four blocks, held at the fixed point), then paves it. */
        void addStreet(int rank, float width, List<float[]> pts, int fixed, int fixedLift) {
            int n = pts.size();
            float[] xs = new float[n], zs = new float[n];
            float[] raw = new float[n];
            for (int k = 0; k < n; k++) {
                xs[k] = pts.get(k)[0];
                zs[k] = pts.get(k)[1];
                raw[k] = height(Math.round(xs[k]), Math.round(zs[k])) - baseY;
            }
            float[] sm = raw.clone();
            for (int pass = 0; pass < 2; pass++) {
                float[] nx = sm.clone();
                for (int k = 1; k < n - 1; k++) nx[k] = (sm[k - 1] + sm[k] + sm[k + 1]) / 3f;
                sm = nx;
            }
            int[] lift = new int[n];
            for (int k = 0; k < n; k++) lift[k] = Math.round(sm[k]);
            lift[fixed] = fixedLift;
            for (int k = fixed + 1; k < n; k++) lift[k] = Math.clamp(lift[k], lift[k - 1] - 1, lift[k - 1] + 1);
            for (int k = fixed - 1; k >= 0; k--) lift[k] = Math.clamp(lift[k], lift[k + 1] - 1, lift[k + 1] + 1);
            // in the green the paving is the green's
            for (int k = 0; k < n; k++) if (Math.hypot(xs[k], zs[k]) <= plan.greenR + 1) lift[k] = 0;
            for (int k = 1; k < n; k++) lift[k] = Math.clamp(lift[k], lift[k - 1] - 1, lift[k - 1] + 1);
            VillagePlan.Street st = new VillagePlan.Street(rank, width, xs, zs, lift);
            plan.streets.add(st);
            pave(st);
        }

        void pave(VillagePlan.Street st) {
            float hw = st.width() / 2f;
            byte kind = st.rank() == 0 ? STREET : LANE;
            for (int k = 0; k + 1 < st.points(); k++) {
                float ax = st.x()[k], az = st.z()[k], bx = st.x()[k + 1], bz = st.z()[k + 1];
                int x0 = (int) Math.floor(Math.min(ax, bx) - hw - 1), x1 = (int) Math.ceil(Math.max(ax, bx) + hw + 1);
                int z0 = (int) Math.floor(Math.min(az, bz) - hw - 1), z1 = (int) Math.ceil(Math.max(az, bz) + hw + 1);
                float dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        if (!plan.inRaster(x, z) || !dry(x, z)) continue;
                        float tt = l2 == 0 ? 0 : Math.clamp(((x - ax) * dx + (z - az) * dz) / l2, 0f, 1f);
                        float px = ax + tt * dx, pz = az + tt * dz;
                        float d = (float) Math.hypot(x - px, z - pz);
                        if (d > hw) continue;
                        byte was = plan.kind(x, z);
                        if (was == SQUARE) continue;
                        if (was == STREET && kind == LANE) continue;
                        int i = plan.idx(x, z);
                        if (was == kind && streetDist[i] <= d) continue;
                        streetDist[i] = d;
                        plan.set(x, z, kind, Math.round(st.lift()[k] + (st.lift()[k + 1] - st.lift()[k]) * tt));
                    }
                }
            }
        }

        boolean street(byte k) {
            return k == STREET || k == LANE || k == SQUARE;
        }

        // ---- buildings along the frontages --------------------------------------------------------------------------

        /** A building to place: its use, footprint (along the street, deep), storeys, setback. */
        record Want(String use, int w, int d, int storeys, int setback) {}

        void buildings() {
            List<Want> wants = new ArrayList<>();
            boolean two = village || rnd.nextInt(100) < 25;
            if (village) {
                wants.add(new Want("inn", 11 + rnd.nextInt(2) * 2, 9, 2, 3));
                wants.add(new Want("smithy", 9, 7, 1, 2));
                wants.add(new Want("house", 9, 9, 2, 1)); // the elder's
                if (rnd.nextInt(100) < 70) wants.add(new Want("granary", 7, 7, 1, 2));
                if (rnd.nextInt(100) < 60) wants.add(new Want("barn", 11, 9, 1, 2));
            } else if (rnd.nextInt(100) < 45) {
                wants.add(new Want("smithy", 9, 7, 1, 2));
            }
            int homes = village ? 8 + rnd.nextInt(10) : 3 + rnd.nextInt(4);
            if (q.first()) homes = 4 + rnd.nextInt(2);
            for (int k = 0; k < homes; k++) {
                int storeys = two && rnd.nextInt(100) < (plan.wealth == VillageStyles.PROSPEROUS ? 60 : plan.wealth == VillageStyles.MODEST ? 35 : 15) ? 2 : 1;
                boolean longhouse = plan.family == VillageStyles.FROST && rnd.nextInt(100) < 60;
                int w = longhouse ? 11 + rnd.nextInt(2) * 2 : storeys == 2 ? 9 : 7 + rnd.nextInt(2) * 2;
                int d = longhouse ? 7 : storeys == 2 ? 7 + rnd.nextInt(2) * 2 : 7;
                wants.add(new Want("house", w, d, storeys, 1 + rnd.nextInt(2)));
            }
            if (!village && rnd.nextInt(100) < 35) wants.add(new Want("barn", 9, 7, 1, 2));
            // frontage slots: every street side, from the green outward
            List<int[]> slots = new ArrayList<>();
            for (int s = 0; s < plan.streets.size(); s++) {
                VillagePlan.Street st = plan.streets.get(s);
                for (int k = 0; k < st.points(); k++) {
                    for (int side = -1; side <= 1; side += 2) slots.add(new int[]{s, k, side});
                }
            }
            slots.sort((u, v) -> Double.compare(slotDist(u), slotDist(v)));
            for (Want w : wants) {
                boolean placed = false;
                for (int[] sl : slots) {
                    if (place(w, sl[0], sl[1], sl[2])) {
                        placed = true;
                        break;
                    }
                }
                if (!placed && w.use().equals("house") && w.w() > 7) place(new Want("house", 7, 7, 1, 1), slots);
            }
        }

        void place(Want w, List<int[]> slots) {
            for (int[] sl : slots) if (place(w, sl[0], sl[1], sl[2])) return;
        }

        double slotDist(int[] sl) {
            VillagePlan.Street st = plan.streets.get(sl[0]);
            double d = Math.hypot(st.x()[sl[1]], st.z()[sl[1]]);
            // each side of a street fills house by house; lanes a little after the main street
            return d + st.rank() * 6 + (sl[2] > 0 ? 0.5 : 0);
        }

        /** Tries a building beside street s at point k on one side: front to the street, a yard round it, level with the paving. */
        boolean place(Want w, int s, int k, int side) {
            VillagePlan.Street st = plan.streets.get(s);
            int n = st.points();
            float tx = st.x()[Math.min(n - 1, k + 1)] - st.x()[Math.max(0, k - 1)], tz = st.z()[Math.min(n - 1, k + 1)] - st.z()[Math.max(0, k - 1)];
            float tl = (float) Math.hypot(tx, tz);
            if (tl < 1e-3) return false;
            tx /= tl;
            tz /= tl;
            float nx = -tz * side, nz = tx * side;
            // the house faces back along the normal: its front is the side nearest the street
            int front;
            boolean alongX;
            if (Math.abs(nx) >= Math.abs(nz)) {
                front = nx > 0 ? W : E;
                alongX = false;
            } else {
                front = nz > 0 ? N : S;
                alongX = true;
            }
            int wx = alongX ? w.w() : w.d(), wz = alongX ? w.d() : w.w();
            float off = st.width() / 2f + w.setback() + 0.5f + (alongX ? wz : wx) / 2f;
            float hx = st.x()[k] + nx * off, hz = st.z()[k] + nz * off;
            int x0 = Math.round(hx - (wx - 1) / 2f), z0 = Math.round(hz - (wz - 1) / 2f), x1 = x0 + wx - 1, z1 = z0 + wz - 1;
            // all of it inside, on free dry ground, a free ring round it
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    if (!within(x, z, 4) || kept(x, z)) return false;
                    byte kk = plan.kind(x, z);
                    boolean ring = x < x0 || x > x1 || z < z0 || z > z1;
                    if (ring ? kk != FREE && kk != YARD && kk != GARDEN : kk != FREE) return false;
                    if (q.reserved().test(cx + x, cz + z)) return false;
                }
            }
            // the door in the middle of the front, and its path straight out to the street
            int len = front == N || front == S ? wx : wz;
            int doorT = (len - 1) / 2 + (len >= 9 && rnd.nextBoolean() ? 1 : 0);
            int doorX, doorZ;
            switch (front) {
                case N -> { doorX = x0 + doorT; doorZ = z0; }
                case S -> { doorX = x1 - doorT; doorZ = z1; }
                case E -> { doorX = x1; doorZ = z0 + doorT; }
                default -> { doorX = x0; doorZ = z1 - doorT; }
            }
            int sx = STEP_X[front], sz = STEP_Z[front];
            int px = doorX + sx, pz = doorZ + sz;
            List<int[]> path = new ArrayList<>();
            int reachLift = Integer.MIN_VALUE;
            for (int stp = 0; stp < w.setback() + 4; stp++) {
                byte kk = plan.kind(px, pz);
                if (street(kk)) {
                    reachLift = plan.lift(px, pz);
                    break;
                }
                if (kk != FREE && kk != YARD && kk != GARDEN) return false;
                path.add(new int[]{px, pz});
                px += sx;
                pz += sz;
            }
            if (reachLift == Integer.MIN_VALUE) return false;
            int lift = reachLift;
            // the land under it: never more than a few blocks of cut or fill
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, sum = 0;
            int cells = 0;
            for (int z = z0; z <= z1; z += 2)
                for (int x = x0; x <= x1; x += 2) {
                    float h = height(x, z) - baseY;
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                    sum += h;
                    cells++;
                }
            if (hi - lo > 7 || Math.abs(sum / cells - lift) > 4.5f) return false;
            int id = plan.plots.size();
            int wealth = Math.clamp(plan.wealth + (w.use().equals("inn") ? 1 : 0) + (rnd.nextInt(100) < 20 ? (rnd.nextBoolean() ? 1 : -1) : 0), 0, 2);
            boolean steep = plan.family == VillageStyles.FROST || plan.family == VillageStyles.HIGHLAND || plan.family == VillageStyles.ELDER
                    || rnd.nextInt(100) < 25;
            Roof roof = plan.family == VillageStyles.DESERT ? Roof.FLAT
                    : w.use().equals("granary") ? Roof.HIP : rnd.nextInt(100) < 18 && plan.family != VillageStyles.FROST ? Roof.HIP : Roof.GABLE;
            boolean ridgeX = wx > wz || wx == wz && (front == N || front == S);
            int eave = 5 + 4 * (w.storeys() - 1);
            int span = ridgeX ? wz : wx;
            int topRow = eave + (roof == Roof.FLAT ? 1 : rise(steep, (span + 1) / 2) + 3);
            plan.plots.add(new Plot(id, VillageStyles.district(plan.family, wealth), x0, z0, x1, z1, front, w.storeys(), roof, ridgeX, steep, eave,
                    topRow, lift, doorX, doorZ, w.use(), true, rnd.nextInt(1000)));
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    boolean in = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                    if (in) {
                        plan.set(x, z, PLOT, lift);
                        plan.setPlot(x, z, id);
                    } else if (plan.kind(x, z) == FREE) plan.set(x, z, YARD, lift);
                }
            }
            for (int[] c : path) plan.set(c[0], c[1], LANE, lift);
            // the point on the street the path joins
            int bestK = k;
            double bd = Double.MAX_VALUE;
            for (int kk = 0; kk < n; kk++) {
                double d = Math.hypot(st.x()[kk] - (doorX + sx), st.z()[kk] - (doorZ + sz));
                if (d < bd) {
                    bd = d;
                    bestK = kk;
                }
            }
            plan.buildings.add(new VillagePlan.Building(id, w.use(), doorX, doorZ, doorX + sx, doorZ + sz, s, bestK));
            if (w.use().equals("smithy")) forge(id);
            return true;
        }

        static final int[] STEP_X = {0, 1, 0, -1}, STEP_Z = {-1, 0, 1, 0};

        static int rise(boolean steep, int d) {
            if (d <= 0) return 0;
            if (!steep) return d;
            return d <= 3 ? 2 * d : 6 + (d - 3);
        }

        /** The forge: an open shed against the smithy's side, its anvil under the roof, the smith's spot beside it. */
        void forge(int id) {
            Plot p = plan.plots.get(id);
            // the side to the left of the front, or the right, whichever has room
            for (int turn : new int[]{1, 3}) {
                int sd = (p.front() + turn) & 3;
                int x0, z0, x1, z1;
                switch (sd) {
                    case N -> { x0 = p.x0() + 1; x1 = p.x0() + 4; z1 = p.z0() - 1; z0 = z1 - 2; }
                    case S -> { x0 = p.x1() - 4; x1 = p.x1() - 1; z0 = p.z1() + 1; z1 = z0 + 2; }
                    case E -> { z0 = p.z0() + 1; z1 = p.z0() + 4; x0 = p.x1() + 1; x1 = x0 + 2; }
                    default -> { z0 = p.z1() - 4; z1 = p.z1() - 1; x1 = p.x0() - 1; x0 = x1 - 2; }
                }
                boolean ok = true;
                for (int z = z0; z <= z1 && ok; z++)
                    for (int x = x0; x <= x1 && ok; x++) {
                        byte k = plan.kind(x, z);
                        ok = k == FREE || k == YARD;
                    }
                if (!ok) continue;
                for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, YARD, p.lift());
                int ax = (x0 + x1) / 2, az = (z0 + z1) / 2;
                int smx = ax + (sd == E ? 1 : sd == W ? -1 : 0), smz = az + (sd == S ? 1 : sd == N ? -1 : 0);
                if (smx < x0 || smx > x1 || smz < z0 || smz > z1) {
                    smx = Math.clamp(smx, x0, x1);
                    smz = Math.clamp(smz, z0, z1);
                }
                plan.forge = new VillagePlan.Forge(id, x0, z0, x1, z1, p.lift(), ax, az, sd == N || sd == S ? ax + 1 : smx, sd == N || sd == S ? smz : az + 1);
                return;
            }
        }

        // ---- water: the mill and the dock --------------------------------------------------------------------------

        void mill() {
            if (!village || plan.family == VillageStyles.DESERT) return;
            // the nearest water to the green that a 9x9 mill can stand beside, its wheel in the water
            int best = Integer.MAX_VALUE, bx = 0, bz = 0, bside = -1;
            for (int z = -r + 8; z <= r - 8; z += 2) {
                for (int x = -r + 8; x <= r - 8; x += 2) {
                    if (plan.kind(x, z) != VillagePlan.WATER) continue;
                    int d = x * x + z * z;
                    if (d >= best || d < (plan.greenR + 14) * (plan.greenR + 14)) continue;
                    // land to one side
                    for (int sd = 0; sd < 4; sd++) {
                        int lx = x - STEP_X[sd] * 6, lz = z - STEP_Z[sd] * 6;
                        if (!free(lx, lz)) continue;
                        best = d;
                        bx = x;
                        bz = z;
                        bside = sd;
                        break;
                    }
                }
            }
            if (bside < 0) return;
            // the mill stands on the land side, its wall on the bank, the wheel in the water
            int sd = bside; // from the mill toward the water
            int mx = bx - STEP_X[sd] * 6, mz = bz - STEP_Z[sd] * 6;
            int x0 = mx - 4, z0 = mz - 4, x1 = mx + 4, z1 = mz + 4;
            for (int z = z0 - 1; z <= z1 + 1; z++)
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    byte k = plan.kind(x, z);
                    boolean in = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                    if (in ? k != FREE : k != FREE && k != YARD && k != VillagePlan.WATER) return;
                }
            // a path from its front (away from the water) to the nearest street point
            int front = (sd + 2) & 3;
            int doorX = front == N || front == S ? mx : front == E ? x1 : x0, doorZ = front == N ? z0 : front == S ? z1 : mz;
            int ox = doorX + STEP_X[front], oz = doorZ + STEP_Z[front];
            int[] near = nearestPoint(ox, oz);
            if (near == null || Math.hypot(plan.streets.get(near[0]).x()[near[1]] - ox, plan.streets.get(near[0]).z()[near[1]] - oz) > 26) return;
            VillagePlan.Street st = plan.streets.get(near[0]);
            int lift = st.lift()[near[1]];
            float sum = 0;
            int c = 0;
            for (int z = z0; z <= z1; z += 2)
                for (int x = x0; x <= x1; x += 2) {
                    sum += height(x, z) - baseY;
                    c++;
                }
            int ml = Math.round(sum / c);
            if (Math.abs(ml - lift) > 4) return;
            int id = plan.plots.size();
            plan.plots.add(new Plot(id, VillageStyles.district(plan.family, plan.wealth), x0, z0, x1, z1, front, 2, Roof.GABLE,
                    sd == N || sd == S, plan.family == VillageStyles.FROST, 9, 9 + 4 + 3, ml, doorX, doorZ, "mill", true, rnd.nextInt(1000)));
            for (int z = z0 - 1; z <= z1 + 1; z++)
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                        plan.set(x, z, PLOT, ml);
                        plan.setPlot(x, z, id);
                    } else if (plan.kind(x, z) == FREE) plan.set(x, z, YARD, ml);
                }
            // the path, sloping from the mill's floor to the street
            path(ox, oz, Math.round(st.x()[near[1]]), Math.round(st.z()[near[1]]), ml, lift);
            plan.buildings.add(new VillagePlan.Building(id, "mill", doorX, doorZ, ox, oz, near[0], near[1]));
            // the wheel: centred on the water-side wall, in the water beyond it
            int wcx = sd == N || sd == S ? mx : sd == E ? x1 + 2 : x0 - 2, wcz = sd == E || sd == W ? mz : sd == S ? z1 + 2 : z0 - 2;
            plan.wheel = new VillagePlan.Wheel(id, wcx, wcz, sd, ml + 2);
        }

        /** The nearest street point to a cell: {street, point}. */
        int @Nullable [] nearestPoint(int x, int z) {
            int[] best = null;
            double bd = Double.MAX_VALUE;
            for (int s = 0; s < plan.streets.size(); s++) {
                VillagePlan.Street st = plan.streets.get(s);
                for (int k = 0; k < st.points(); k++) {
                    double d = Math.hypot(st.x()[k] - x, st.z()[k] - z);
                    if (d < bd) {
                        bd = d;
                        best = new int[]{s, k};
                    }
                }
            }
            return best;
        }

        /** A footpath (lane cells, one wide) from a to b, its height running from la to lb; only over free ground. */
        void path(int ax, int az, int bx, int bz, int la, int lb) {
            int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
            for (int s = 0; s <= steps; s++) {
                float tt = steps == 0 ? 0 : s / (float) steps;
                int x = Math.round(ax + (bx - ax) * tt), z = Math.round(az + (bz - az) * tt);
                byte k = plan.kind(x, z);
                if (street(k) || k == PLOT) continue;
                if (k == FREE || k == YARD || k == GARDEN) plan.set(x, z, LANE, Math.round(la + (lb - la) * tt));
            }
        }

        void dock() {
            // open water two deep or more, a short walk from a street: the pier runs out from the bank
            int best = Integer.MAX_VALUE, bx = 0, bz = 0, bsd = -1;
            for (int z = -r + 6; z <= r - 6; z += 2) {
                for (int x = -r + 6; x <= r - 6; x += 2) {
                    if (!free(x, z) && plan.kind(x, z) != YARD) continue;
                    for (int sd = 0; sd < 4; sd++) {
                        int wx = x + STEP_X[sd], wz = z + STEP_Z[sd];
                        if (plan.kind(wx, wz) != VillagePlan.WATER) continue;
                        // deep enough a few blocks out
                        int fx = x + STEP_X[sd] * 5, fz = z + STEP_Z[sd] * 5;
                        if (plan.kind(fx, fz) != VillagePlan.WATER || water(fx, fz) < 1.5f) continue;
                        int[] near = nearestPoint(x, z);
                        if (near == null) continue;
                        VillagePlan.Street st = plan.streets.get(near[0]);
                        int d = (int) Math.hypot(st.x()[near[1]] - x, st.z()[near[1]] - z);
                        if (d < best && d < 28) {
                            best = d;
                            bx = x;
                            bz = z;
                            bsd = sd;
                        }
                    }
                }
            }
            if (bsd < 0) return;
            int len = 0;
            int surf = Math.round(height(bx + STEP_X[bsd] * 2, bz + STEP_Z[bsd] * 2)) - baseY; // the water's surface
            while (len < 13) {
                int x = bx + STEP_X[bsd] * (len + 1), z = bz + STEP_Z[bsd] * (len + 1);
                if (plan.kind(x, z) != VillagePlan.WATER || !plan.inRaster(x + STEP_X[bsd] * 2, z + STEP_Z[bsd] * 2)) break;
                len++;
            }
            if (len < 5) return;
            int ex = bx + STEP_X[bsd] * len, ez = bz + STEP_Z[bsd] * len;
            int px = STEP_Z[bsd] != 0 ? 1 : 0, pz = STEP_X[bsd] != 0 ? 1 : 0;
            int x0 = Math.min(bx + STEP_X[bsd], ex) - px, x1 = Math.max(bx + STEP_X[bsd], ex) + px;
            int z0 = Math.min(bz + STEP_Z[bsd], ez) - pz, z1 = Math.max(bz + STEP_Z[bsd], ez) + pz;
            int deck = surf + 1;
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, VillagePlan.DOCK, deck);
            plan.dock = new VillagePlan.Dock(x0, z0, x1, z1, deck, ex, ez, bsd);
            int[] near = nearestPoint(bx, bz);
            VillagePlan.Street st = plan.streets.get(near[0]);
            path(bx, bz, Math.round(st.x()[near[1]]), Math.round(st.z()[near[1]]), Math.max(deck, Math.round(height(bx, bz)) - baseY), st.lift()[near[1]]);
        }

        // ---- the fields and the pens --------------------------------------------------------------------------------

        static final String[][] CROPS = {
                {"wheat", "wheat", "carrots", "potatoes", "beetroots"},   // temperate
                {"potatoes", "potatoes", "beetroots", "wheat"},            // frost
                {"wheat", "melons", "pumpkins", "wheat"},                  // desert (by water)
                {"wheat", "wheat", "melons", "carrots"},                   // savanna
                {"potatoes", "carrots", "wheat"},                          // mire
                {"potatoes", "wheat", "beetroots"},                        // highland
                {"wheat", "carrots", "pumpkins"},                          // elder
                {"wheat", "carrots", "potatoes", "beetroots"},             // coast
                {"melons", "carrots", "wheat"},                            // jungle
                {"wheat", "beetroots", "carrots"}};                        // cherry

        /**
         * Kitchen gardens behind or beside half the homes: a fenced patch of vegetables, its gate toward the house (the
         * village feeds itself from its back doors as much as its fields).
         */
        void gardens() {
            String[] crops = CROPS[Math.clamp(plan.family, 0, CROPS.length - 1)];
            for (VillagePlan.Building b : new ArrayList<>(plan.buildings)) {
                if (!b.use().equals("house") || rnd.nextInt(100) >= (plan.family == VillageStyles.DESERT ? 25 : 55)) continue;
                Plot p = plan.plots.get(b.plot());
                // the back first, then either side
                int back = (p.front() + 2) & 3;
                for (int sd : new int[]{back, (p.front() + 1) & 3, (p.front() + 3) & 3}) {
                    int gw = 5 + rnd.nextInt(2), gd = 4 + rnd.nextInt(2);
                    int x0, z0, x1 = 0, z1 = 0;
                    boolean along = sd == N || sd == S;
                    int w = along ? gw : gd, d = along ? gd : gw;
                    int mx = (p.x0() + p.x1()) / 2, mz = (p.z0() + p.z1()) / 2;
                    switch (sd) {
                        case N -> { x0 = mx - w / 2; z1 = p.z0() - 2; z0 = z1 - d + 1; }
                        case S -> { x0 = mx - w / 2; z0 = p.z1() + 2; z1 = z0 + d - 1; }
                        case E -> { z0 = mz - d / 2; x0 = p.x1() + 2; x1 = x0 + w - 1; }
                        default -> { z0 = mz - d / 2; x1 = p.x0() - 2; x0 = x1 - w + 1; }
                    }
                    if (along) x1 = x0 + w - 1;
                    else z1 = z0 + d - 1;
                    if (!gardenFits(x0, z0, x1, z1)) continue;
                    int lift = p.lift();
                    for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, VillagePlan.KITCHEN, lift);
                    // the gate faces the house
                    int gx = sd == E ? x0 : sd == W ? x1 : (x0 + x1) / 2, gz = sd == S ? z0 : sd == N ? z1 : (z0 + z1) / 2;
                    plan.gardens.add(new VillagePlan.Garden(x0, z0, x1, z1, lift, crops[rnd.nextInt(crops.length)], gx, gz, (sd + 2) & 3));
                    break;
                }
            }
        }

        /** Free yard or open ground, gentle enough to level, that touches no street, plot or field. */
        boolean gardenFits(int x0, int z0, int x1, int z1) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (!within(x, z, 3) || kept(x, z)) return false;
                    byte k = plan.kind(x, z);
                    if (k != FREE && k != YARD) return false;
                    if (q.reserved().test(cx + x, cz + z)) return false;
                    float h = height(x, z);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
            }
            return hi - lo <= 3;
        }

        /** A few trees in the village: on the green's edge and in yards, of the land's own wood. */
        void trees() {
            int want = village ? 4 + rnd.nextInt(4) : 2 + rnd.nextInt(2);
            if (plan.family == VillageStyles.DESERT) want = village ? 2 : 1;
            int made = 0;
            for (int tries = 0; tries < 300 && made < want; tries++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                int dist = made == 0 ? plan.greenR + 3 : plan.greenR + 6 + rnd.nextInt(Math.max(1, r / 2));
                int x = (int) Math.round(Math.cos(ang) * dist), z = (int) Math.round(Math.sin(ang) * dist);
                boolean ok = true;
                for (int dz = -2; dz <= 2 && ok; dz++)
                    for (int dx = -2; dx <= 2 && ok; dx++) {
                        byte k = plan.kind(x + dx, z + dz);
                        ok = (k == FREE || k == YARD) && !kept(x + dx, z + dz) && (Math.abs(dx) == 2 || Math.abs(dz) == 2 || k != YARD || !nextToPlot(x + dx, z + dz));
                    }
                // the canopy keeps clear of every wall
                for (int dz = -3; dz <= 3 && ok; dz++) for (int dx = -3; dx <= 3 && ok; dx++) ok = plan.kind(x + dx, z + dz) != PLOT;
                if (!ok || plan.treeCells.contains(cellKey(x, z))) continue;
                int lift = Math.round(height(x, z)) - baseY;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) plan.set(x + dx, z + dz, YARD, lift);
                plan.trees.add(new int[]{x, z, lift, rnd.nextInt(1000)});
                plan.treeCells.add(cellKey(x, z));
                made++;
            }
        }

        boolean nextToPlot(int x, int z) {
            return plan.kind(x + 1, z) == PLOT || plan.kind(x - 1, z) == PLOT || plan.kind(x, z + 1) == PLOT || plan.kind(x, z - 1) == PLOT;
        }

        static long cellKey(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        /** Washing lines along a third of the homes' side yards, two out from the wall, a pole at each end. */
        void lines() {
            for (VillagePlan.Building b : plan.buildings) {
                if (!b.use().equals("house") || rnd.nextInt(100) >= 32) continue;
                Plot p = plan.plots.get(b.plot());
                int sd = rnd.nextBoolean() ? (p.front() + 1) & 3 : (p.front() + 3) & 3;
                boolean alongX = sd == N || sd == S;
                int len = (alongX ? p.x1() - p.x0() : p.z1() - p.z0()) - 1;
                if (len < 4) continue;
                int x0, z0;
                if (alongX) {
                    x0 = p.x0() + 1;
                    z0 = sd == N ? p.z0() - 2 : p.z1() + 2;
                } else {
                    z0 = p.z0() + 1;
                    x0 = sd == W ? p.x0() - 2 : p.x1() + 2;
                }
                int x1 = alongX ? x0 + len - 1 : x0, z1 = alongX ? z0 : z0 + len - 1;
                boolean ok = true;
                for (int z = z0; z <= z1 && ok; z++) for (int x = x0; x <= x1 && ok; x++) ok = plan.kind(x, z) == YARD || plan.kind(x, z) == FREE;
                if (!ok || plan.treeCells.contains(cellKey(x0, z0))) continue;
                for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, YARD, p.lift());
                plan.props.add(new Prop("line", x0, z0, x1, z1, p.lift() + 3, "ab", (alongX ? 0 : 4) + rnd.nextInt(4), "laundry"));
            }
        }

        void fields() {
            int want = village ? 4 + rnd.nextInt(4) : 2 + rnd.nextInt(2);
            if (q.first()) want = 3;
            String[] crops = CROPS[Math.clamp(plan.family, 0, CROPS.length - 1)];
            int made = 0;
            for (int tries = 0; tries < 420 && made < want; tries++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                int[] band = fieldBand();
                int dist = band[0] + rnd.nextInt(Math.max(1, band[1] - band[0]));
                int fcx = (int) Math.round(Math.cos(ang) * dist), fcz = (int) Math.round(Math.sin(ang) * dist);
                boolean rowsX = rnd.nextBoolean();
                int fw = 9 + rnd.nextInt(4) * 2, fd = 7 + rnd.nextInt(2) * 2;
                int wx = rowsX ? fw : fd, wz = rowsX ? fd : fw;
                int x0 = fcx - wx / 2, z0 = fcz - wz / 2, x1 = x0 + wx - 1, z1 = z0 + wz - 1;
                if (!rect(x0, z0, x1, z1, 1, 4)) continue;
                if (!nearVillage(x0, z0, x1, z1, 8)) continue;
                int lift = meanLift(x0, z0, x1, z1);
                for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, VillagePlan.FIELD, lift);
                int[] near = nearestPoint((x0 + x1) / 2, (z0 + z1) / 2);
                int wkx = Math.clamp(Math.round(plan.streets.get(near[0]).x()[near[1]]), x0, x1), wkz = Math.clamp(Math.round(plan.streets.get(near[0]).z()[near[1]]), z0, z1);
                plan.fields.add(new VillagePlan.Field(x0, z0, x1, z1, lift, crops[rnd.nextInt(crops.length)], rowsX, wkx, wkz));
                made++;
            }
        }

        void pens() {
            int want = village ? 1 + rnd.nextInt(2) : rnd.nextInt(100) < 70 ? 1 : 0;
            if (q.first()) want = 1;
            String[] beasts = switch (plan.family) {
                case VillageStyles.FROST, VillageStyles.HIGHLAND -> new String[]{"sheep", "sheep", "goat"};
                case VillageStyles.DESERT, VillageStyles.SAVANNA -> new String[]{"goat", "chicken", "cow"};
                case VillageStyles.MIRE -> new String[]{"pig", "chicken"};
                case VillageStyles.COAST -> new String[]{"chicken", "sheep"};
                default -> new String[]{"sheep", "pig", "cow", "chicken", "sheep"};
            };
            int made = 0;
            for (int tries = 0; tries < 200 && made < want; tries++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                int[] band = fieldBand();
                int dist = band[0] + rnd.nextInt(Math.max(1, band[1] - band[0]));
                int pcx = (int) Math.round(Math.cos(ang) * dist), pcz = (int) Math.round(Math.sin(ang) * dist);
                int wx = 7 + rnd.nextInt(2), wz = 6 + rnd.nextInt(2);
                int x0 = pcx - wx / 2, z0 = pcz - wz / 2, x1 = x0 + wx - 1, z1 = z0 + wz - 1;
                if (!rect(x0, z0, x1, z1, 1, 3)) continue;
                if (!nearVillage(x0, z0, x1, z1, 5)) continue;
                int lift = meanLift(x0, z0, x1, z1);
                // the gate on the side nearest a street
                int[] near = nearestPoint((x0 + x1) / 2, (z0 + z1) / 2);
                float sx = plan.streets.get(near[0]).x()[near[1]], sz = plan.streets.get(near[0]).z()[near[1]];
                int gside, gx, gz;
                float ddx = sx - (x0 + x1) / 2f, ddz = sz - (z0 + z1) / 2f;
                if (Math.abs(ddx) > Math.abs(ddz)) {
                    gside = ddx > 0 ? E : W;
                    gx = ddx > 0 ? x1 : x0;
                    gz = (z0 + z1) / 2;
                } else {
                    gside = ddz > 0 ? S : N;
                    gz = ddz > 0 ? z1 : z0;
                    gx = (x0 + x1) / 2;
                }
                for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, VillagePlan.PEN, lift);
                plan.pens.add(new VillagePlan.Pen(x0, z0, x1, z1, lift, beasts[rnd.nextInt(beasts.length)], gx, gz, gside));
                made++;
            }
        }

        /** Free, dry, gentle ground over a box (and a free ring round it). */
        boolean rect(int x0, int z0, int x1, int z1, int ring, int maxRange) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int z = z0 - ring; z <= z1 + ring; z++) {
                for (int x = x0 - ring; x <= x1 + ring; x++) {
                    if (x * x + z * z > (r - 3) * (r - 3)) return false;
                    if (!free(x, z)) return false;
                    if (q.reserved().test(cx + x, cz + z)) return false;
                    if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                        float h = height(x, z);
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                    }
                }
            }
            return hi - lo <= maxRange;
        }

        boolean nearVillage(int x0, int z0, int x1, int z1, int reach) {
            for (int z = z0 - reach; z <= z1 + reach; z += 2)
                for (int x = x0 - reach; x <= x1 + reach; x += 2) {
                    byte k = plan.kind(x, z);
                    if (street(k) || k == YARD || k == PLOT) return true;
                }
            return false;
        }

        int meanLift(int x0, int z0, int x1, int z1) {
            float sum = 0;
            int c = 0;
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) {
                    sum += height(x, z) - baseY;
                    c++;
                }
            return Math.round(sum / c);
        }

        // ---- lamps, the bell, the well and the street's things -----------------------------------------------------

        void lamps() {
            int max = maxLamps();
            // round the green first: four at its corners, then along the streets every fourteen blocks or so
            int g = plan.greenR;
            int[][] corners = {{g - 1, g - 1}, {-g + 1, g - 1}, {g - 1, -g + 1}, {-g + 1, -g + 1}};
            for (int[] c : corners) if (plan.kind(c[0], c[1]) == SQUARE && plan.lamps.size() < (village ? 4 : 2)) lamp(c[0], c[1]);
            for (int s = 0; s < plan.streets.size() && plan.lamps.size() < max; s++) {
                VillagePlan.Street st = plan.streets.get(s);
                int side = 1;
                for (int k = 3; k < st.points() && plan.lamps.size() < max; k += 3 + (st.rank() == 0 ? 0 : 1)) {
                    if (Math.hypot(st.x()[k], st.z()[k]) < g + 3) continue;
                    float tx = st.x()[Math.min(st.points() - 1, k + 1)] - st.x()[k - 1], tz = st.z()[Math.min(st.points() - 1, k + 1)] - st.z()[k - 1];
                    float tl = (float) Math.hypot(tx, tz);
                    float nx = -tz / tl * side, nz = tx / tl * side;
                    int lx = Math.round(st.x()[k] + nx * (st.width() / 2f + 0.6f)), lz = Math.round(st.z()[k] + nz * (st.width() / 2f + 0.6f));
                    byte kk = plan.kind(lx, lz);
                    if ((kk == FREE || kk == YARD) && !plan.lampNear(lx, lz)) {
                        plan.set(lx, lz, YARD, st.lift()[k]);
                        plan.lamps.add(new VillagePlan.Lamp(lx, lz, st.lift()[k], s, k));
                        side = -side;
                    }
                }
            }
        }

        int maxLamps() {
            return village ? 14 : 5;
        }

        void lamp(int x, int z) {
            int[] near = nearestPoint(x, z);
            if (near == null) return;
            plan.lamps.add(new VillagePlan.Lamp(x, z, 0, near[0], near[1]));
        }

        void props() {
            civic();
            around();
        }

        /** The green's things: the well, the bell, the notice board, benches round the well, a village's stall. */
        void civic() {
            int g = plan.greenR;
            // the well a little off the green's middle (the street runs through it), the bell across from it
            VillagePlan.Street main = plan.streets.getFirst();
            int c0 = 0;
            double cd = Double.MAX_VALUE;
            for (int k = 0; k < main.points(); k++) {
                double d = Math.hypot(main.x()[k], main.z()[k]);
                if (d < cd) {
                    cd = d;
                    c0 = k;
                }
            }
            float tx = main.x()[Math.min(main.points() - 1, c0 + 1)] - main.x()[Math.max(0, c0 - 1)], tz = main.z()[Math.min(main.points() - 1, c0 + 1)] - main.z()[Math.max(0, c0 - 1)];
            float tl = Math.max(1e-3f, (float) Math.hypot(tx, tz));
            float nx = -tz / tl, nz = tx / tl;
            int off = Math.max(2, g - 3);
            int wx = Math.round(nx * off), wz = Math.round(nz * off);
            plan.well = new int[]{wx, wz};
            plan.props.add(new Prop("well", wx - 1, wz - 1, wx + 1, wz + 1, 0, "", rnd.nextInt(4), ""));
            // the bell across the street: a post in a hamlet, a tower in a village
            int bx = Math.round(-nx * off), bz = Math.round(-nz * off);
            if (village) {
                plan.bell = new VillagePlan.Bell(bx - 1, bz - 1, bx + 1, bz + 1, 0, true, bx, 8, bz, bx + (nx > 0 ? -2 : 2), bz);
            } else {
                boolean alongX = Math.abs(nx) < Math.abs(nz);
                plan.bell = new VillagePlan.Bell(bx - (alongX ? 1 : 0), bz - (alongX ? 0 : 1), bx + (alongX ? 1 : 0), bz + (alongX ? 0 : 1), 0, false,
                        bx, 3, bz, bx + (alongX ? 0 : 1), bz + (alongX ? 1 : 0));
            }
            VillagePlan.Bell b = plan.bell;
            for (int z = b.z0(); z <= b.z1(); z++) for (int x = b.x0(); x <= b.x1(); x++) if (plan.kind(x, z) == SQUARE) plan.set(x, z, LANDMARK, 0);
            // the notice board by the well, facing the street
            int side = Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? W : E) : (nz > 0 ? N : S);
            int ntx = wx + Math.round(tx / tl * 3), ntz = wz + Math.round(tz / tl * 3);
            plan.notice = new int[]{ntx, ntz, side};
            plan.props.add(new Prop("notice", ntx, ntz, ntx, ntz, 0, "NESW".substring(side, side + 1), 0, ""));
            // benches round the well, facing it
            List<Integer> bs = new ArrayList<>();
            int[][] around = {{wx, wz - 3, S}, {wx, wz + 3, N}, {wx - 3, wz, E}, {wx + 3, wz, W}};
            for (int[] a : around) {
                if (plan.kind(a[0], a[1]) != SQUARE || Math.abs(a[0] - ntx) + Math.abs(a[1] - ntz) < 2) continue;
                plan.props.add(new Prop("bench", a[0], a[1], a[0], a[1], 0, "NESW".substring(a[2], a[2] + 1), 0, ""));
                bs.add(a[0]);
                bs.add(a[1]);
                bs.add(a[2]);
            }
            plan.benches = bs.stream().mapToInt(Integer::intValue).toArray();
            // a market stall on a village's green
            if (village) {
                int sx = wx + Math.round(-tx / tl * 4) - 1, sz = wz + Math.round(-tz / tl * 4);
                boolean ok = true;
                for (int z = sz; z <= sz + 1 && ok; z++) for (int x = sx; x <= sx + 3 && ok; x++) ok = plan.kind(x, z) == SQUARE;
                if (ok) plan.props.add(new Prop("stall", sx, sz, sx + 3, sz + 1, 0, "S", rnd.nextInt(6), ""));
            }
        }

        /** Round the buildings and the edge: carts, stacks, planters, the woodcutter's log stack, nets by the dock. */
        void around() {
            // round the buildings: a cart by the inn and the barn, crates by the smithy and the granary, a planter by doors
            for (VillagePlan.Building bd : plan.buildings) {
                Plot p = plan.plots.get(bd.plot());
                switch (bd.use()) {
                    case "inn" -> {
                        int f = p.front(), sx = STEP_X[f], sz = STEP_Z[f];
                        // a table and benches out front, beside the door
                        int lat = f == N || f == S ? 1 : 0, latZ = 1 - lat;
                        int ax = bd.outX() + lat * 2, az = bd.outZ() + latZ * 2;
                        if (cellsFree(ax, az, ax + sx * 2, az + sz * 2)) {
                            plan.props.add(new Prop("tavern_table", Math.min(ax, ax + sx * 2), Math.min(az, az + sz * 2), Math.max(ax, ax + sx * 2),
                                    Math.max(az, az + sz * 2), p.lift(), "NESW".substring(f, f + 1), 0, ""));
                            mark(ax, az, ax + sx * 2, az + sz * 2, p.lift());
                        }
                        cart(p, "casks");
                    }
                    case "barn", "granary" -> {
                        cart(p, bd.use().equals("barn") ? "produce" : "sacks");
                        stack(p, "sacks");
                    }
                    case "smithy" -> stack(p, "crates2");
                    case "mill" -> stack(p, "sacks");
                    default -> {
                        if (rnd.nextInt(100) < 45) {
                            int f = p.front(), lat = f == N || f == S ? 1 : 0;
                            int ax = bd.outX() + lat * (rnd.nextBoolean() ? 1 : -1), az = bd.outZ() + (1 - lat) * (rnd.nextBoolean() ? 1 : -1);
                            if (plan.kind(ax, az) == YARD) {
                                plan.props.add(new Prop("planter", ax, az, ax, az, p.lift(), "", rnd.nextInt(7), plan.wealth >= 1 ? "stone" : "wood"));
                                plan.setKind(ax, az, GARDEN);
                            }
                        }
                        if (rnd.nextInt(100) < 25) stack(p, rnd.nextBoolean() ? "casks" : "crates");
                    }
                }
            }
            // the woodcutter's log stack at the edge, on the side with the most free ground
            if (plan.family == VillageStyles.ELDER || plan.family == VillageStyles.FROST || plan.family == VillageStyles.TEMPERATE || village) {
                for (int tries = 0; tries < 60; tries++) {
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    int d = r - 18 - rnd.nextInt(12);
                    int x = (int) Math.round(Math.cos(ang) * d), z = (int) Math.round(Math.sin(ang) * d);
                    if (!rect(x, z, x + 1, z + 1, 1, 1) || !nearVillage(x, z, x + 1, z + 1, 6)) continue;
                    int lift = meanLift(x, z, x + 1, z + 1);
                    plan.props.add(new Prop("heap", x, z, x + 1, z + 1, lift, "", rnd.nextInt(4), "timber"));
                    for (int zz = z; zz <= z + 1; zz++) for (int xx = x; xx <= x + 1; xx++) plan.set(xx, zz, YARD, lift);
                    plan.posts.add(new int[]{x - 1, z, lift}); // where the woodcutter works: index posts.size()-1, see people()
                    woods = plan.posts.size() - 1;
                    break;
                }
            }
            // nets on the bank by the dock
            if (plan.dock != null) {
                VillagePlan.Dock dk = plan.dock;
                int bxk = dk.side() == E ? dk.x0() - 1 : dk.side() == W ? dk.x1() + 1 : dk.x0() - 3;
                int bzk = dk.side() == S ? dk.z0() - 1 : dk.side() == N ? dk.z1() + 1 : dk.z0() - 3;
                boolean alongX = dk.side() == N || dk.side() == S;
                int x1 = alongX ? bxk + 2 : bxk, z1 = alongX ? bzk : bzk + 2;
                if (cellsFree(bxk, bzk, x1, z1)) {
                    int lift = Math.round(height(bxk, bzk)) - baseY;
                    plan.props.add(new Prop("net", bxk, bzk, x1, z1, lift, "", 0, ""));
                    mark(bxk, bzk, x1, z1, lift);
                }
            }
        }

        int woods = -1;

        boolean cellsFree(int ax, int az, int bx, int bz) {
            for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++)
                for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
                    byte k = plan.kind(x, z);
                    if (k != YARD && k != FREE) return false;
                }
            return true;
        }

        void mark(int ax, int az, int bx, int bz, int lift) {
            for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++)
                for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) plan.set(x, z, GARDEN, lift);
        }

        /** A cart in the yard beside a building (two cells, shafts out). */
        void cart(Plot p, String load) {
            int[][] sides = {{p.x1() + 2, (p.z0() + p.z1()) / 2, 0}, {p.x0() - 3, (p.z0() + p.z1()) / 2, 0}, {(p.x0() + p.x1()) / 2, p.z1() + 2, 1}};
            for (int[] s : sides) {
                int x0 = s[0], z0 = s[1], x1 = s[2] == 0 ? x0 + 1 : x0, z1 = s[2] == 0 ? z0 : z0 + 1;
                if (!cellsFree(x0, z0, x1, z1)) continue;
                plan.props.add(new Prop("cart", x0, z0, x1, z1, p.lift(), s[2] == 0 ? (x0 > p.x1() ? "E" : "W") : "S", 0, load));
                mark(x0, z0, x1, z1, p.lift());
                return;
            }
        }

        /** A stack of goods against a building's side wall. */
        void stack(Plot p, String what) {
            int[][] cells = {{p.x0() - 1, p.z0() + 1}, {p.x1() + 1, p.z1() - 1}, {p.x1() - 1, p.z0() - 1}, {p.x0() + 1, p.z1() + 1}};
            for (int[] c : cells) {
                if (plan.kind(c[0], c[1]) != YARD) continue;
                if (Math.abs(c[0] - p.doorX()) + Math.abs(c[1] - p.doorZ()) < 3) continue;
                plan.props.add(new Prop("stack", c[0], c[1], c[0], c[1], p.lift(), "", rnd.nextInt(4), what));
                plan.setKind(c[0], c[1], GARDEN);
                return;
            }
        }

        // ---- the people ---------------------------------------------------------------------------------------------

        int[] peopleBounds() {
            return village ? new int[]{20, 50} : new int[]{8, 16};
        }

        /** Jobs a bigger place adds to the list (the town's Watch at its gates, its stallkeepers, its scribe). */
        void moreJobs(List<String[]> jobs) {}

        void people() {
            Random pr = new Random(q.seed() ^ 0x9E0B1E5L);
            // the jobs the place has work for, in the order they're handed out to the households' adults
            List<String[]> jobs = new ArrayList<>(); // job, work kind, work index
            for (int k = 0; k < plan.buildings.size(); k++) {
                switch (plan.buildings.get(k).use()) {
                    case "smithy" -> jobs.add(new String[]{"smith", "anvil", String.valueOf(k)});
                    case "inn" -> jobs.add(new String[]{"tavernkeeper", "inn", String.valueOf(k)});
                    case "mill" -> jobs.add(new String[]{"miller", "mill", String.valueOf(k)});
                    default -> {
                    }
                }
            }
            for (int f = 0; f < plan.fields.size(); f++) jobs.add(new String[]{"farmer", "field", String.valueOf(f)});
            for (int p = 0; p < plan.pens.size(); p++) jobs.add(new String[]{"shepherd", "pen", String.valueOf(p)});
            if (plan.dock != null) jobs.add(new String[]{"fisher", "dock", "0"});
            if (woods >= 0) jobs.add(new String[]{"woodcutter", "post", String.valueOf(woods)});
            jobs.add(new String[]{"hunter", "post", "0"});
            jobs.add(new String[]{"washer", "well", "0"});
            if (village) {
                jobs.add(new String[]{"elder", "green", "0"});
                jobs.add(new String[]{"bellringer", "bell", "0"});
                jobs.add(new String[]{"lamplighter", "lamps", "0"});
                jobs.add(new String[]{"watch", "post", "0"});
                jobs.add(new String[]{"watch", "post", "1"});
                jobs.add(new String[]{"scribe", "green", "1"});
                jobs.add(new String[]{"healer", "green", "2"});
                jobs.add(new String[]{"carter", "green", "3"});
                if (plan.fields.size() > 3) jobs.add(new String[]{"farmer", "field", "0"});
                if (plan.family == VillageStyles.HIGHLAND) jobs.add(new String[]{"miner", "post", "0"});
                moreJobs(jobs);
            } else {
                jobs.add(new String[]{"elder", "green", "0"});
                jobs.add(new String[]{"lamplighter", "lamps", "0"});
                jobs.add(new String[]{"bellringer", "bell", "0"});
            }
            int next = 0;
            // households: one per house and the inn; the smithy's smith lives in the house nearest it
            for (int k = 0; k < plan.buildings.size(); k++) {
                VillagePlan.Building b = plan.buildings.get(k);
                if (!b.use().equals("house") && !b.use().equals("inn") && !b.use().equals("scribe") && !b.use().equals("hall") && !b.use().equals("assay")) continue;
                int hid = plan.households.size();
                String fam = Names.family(pr);
                plan.households.add(new VillagePlan.Household(hid, k, fam));
                int adults = 1 + (pr.nextInt(100) < 85 ? 1 : 0) + (pr.nextInt(100) < 22 ? 1 : 0);
                int children = pr.nextInt(100) < 70 ? 1 + pr.nextInt(village ? 2 : 3) : 0;
                for (int a = 0; a < adults; a++) {
                    boolean female = a == 1 || a == 0 && pr.nextBoolean() && adults == 1;
                    int age = a == 2 ? 3 : pr.nextInt(100) < 18 ? 1 : 2;
                    String job = "resident", work = "";
                    int wi = 0;
                    if (b.use().equals("inn") && a == 0) {
                        job = "tavernkeeper";
                        work = "inn";
                        wi = k;
                        for (int j = 0; j < jobs.size(); j++)
                            if (jobs.get(j)[0].equals("tavernkeeper")) {
                                jobs.remove(j);
                                break;
                            }
                    } else if (b.use().equals("scribe") && a == 0) {
                        // (SPEC §28.9) the town's scribe keeps the shop they live over
                        job = "scribe";
                        work = "scribe";
                        wi = k;
                        jobs.removeIf(j -> j[0].equals("scribe"));
                    } else if (b.use().equals("assay") && a == 0) {
                        // (SPEC §28.9) a mining town's assayer keeps the store of the mine's ore
                        job = "assayer";
                        work = "assay";
                        wi = k;
                    } else if (b.use().equals("hall") && a == 0) {
                        job = "elder";
                        work = "green";
                        jobs.removeIf(j -> j[0].equals("elder"));
                    } else if (age < 3 && next < jobs.size()) {
                        String[] j = jobs.get(next++);
                        job = j[0];
                        work = j[1];
                        wi = Integer.parseInt(j[2]);
                    } else if (age == 3 && next < jobs.size() && jobs.get(next)[0].equals("elder")) {
                        String[] j = jobs.get(next++);
                        job = j[0];
                        work = j[1];
                    }
                    plan.people.add(new VillagePlan.Person(Names.given(pr, female), fam, job, age, hid, female ? 1 : pr.nextInt(2), work, wi));
                }
                for (int c = 0; c < children; c++) {
                    boolean female = pr.nextBoolean();
                    plan.people.add(new VillagePlan.Person(Names.given(pr, female), fam, "child", 0, hid, 1, "green", 0));
                }
            }
            // a hamlet is eight to sixteen people, a village twenty to fifty: children fill a short count, the youngest
            // leave a long one (a lodger or a grandchild, never a worker)
            int[] bounds = peopleBounds();
            int min = bounds[0], max = bounds[1];
            for (int k = 0; plan.people.size() < min && !plan.households.isEmpty() && k < 64; k++) {
                VillagePlan.Household h = plan.households.get(k % plan.households.size());
                boolean female = pr.nextBoolean();
                plan.people.add(new VillagePlan.Person(Names.given(pr, female), h.family(), "child", 0, h.id(), 1, "green", 0));
            }
            for (int k = plan.people.size() - 1; k >= 0 && plan.people.size() > max; k--)
                if (plan.people.get(k).job().equals("child") || plan.people.get(k).job().equals("resident")) plan.people.remove(k);
        }
    }
}
