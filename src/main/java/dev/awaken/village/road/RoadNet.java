package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.living.region.RegionMap;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The world's roads (SPEC §28.9, PLAN 9): planned per pair of neighbouring regions from the region map. Each region away
 * from the Commons has a <b>hub</b> where its roads meet: a walled town (about one region in five), else the settlement
 * nearest its heart, else a wayhouse (an inn with a stable on a crossroads, about a day's walk from the next), else a
 * bare crossroads. A <b>highroad</b> joins the hubs of every two neighbouring regions and runs through the settlements
 * that lie on its way (in at one end of the main street, out at the other); Carillon's three roads out (north past the
 * Wayfarer camp and the first hamlet, east and south) each run to the nearest region's hub that way; every other
 * settlement has a <b>lane</b> to the nearest road. Everything is pure from the seed and the land, made on whichever
 * thread asks first and kept for the session; a road's legs start on a lattice of chunks, so a chunk off the lattice
 * answers at once.
 */
public final class RoadNet {
    private RoadNet() {}

    // ---- hubs --------------------------------------------------------------------------------------------------------

    public enum HubKind { NONE, TOWN, SETTLEMENT, WAYHOUSE, CROSSROADS }

    /**
     * Where a region's roads meet: its kind, the meeting point (world), the settlement there (a town's, a settlement's or a
     * wayhouse's anchor) and the region's name.
     */
    public record Hub(int gx, int gz, HubKind kind, int x, int z, SettlementGrid.@Nullable Anchor anchor, String region) {}

    /** About one region in five holds a town (its site found near the heart; none fits and it's a crossroads). */
    static final double TOWN_ODDS = 0.22;
    /** The grid's settlements keep this far from a town's region's heart. */
    public static final int TOWN_CLEAR = 360;
    /** A grid settlement anchored this near a region's heart is that region's hub. */
    static final int NEAR_HEART = 300;
    /** No town's region heart this near the first hamlet (its land stays the hamlet's). */
    static final int FIRST_CLEAR = 520;

    private static final ConcurrentHashMap<Long, CompletableFuture<Hub>> HUBS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<List<Road>>> ROADS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CompletableFuture<SettlementGrid.Anchor[]>> SITES = new ConcurrentHashMap<>();
    static long planNanos, planned, reads;

    public static void clear() {
        HUBS.clear();
        ROADS.clear();
        SITES.clear();
        LEGS.clear();
        synchronized (ALL) {
            ALL.clear();
            BY_CHUNK.clear();
        }
        planNanos = planned = reads = 0;
    }

    static long cellKey(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xffffffffL);
    }

    /** Made once by the first thread to ask; others wait for it (outside the map's lock). */
    private static <K, V> V once(ConcurrentHashMap<K, CompletableFuture<V>> map, K key, Supplier<V> make) {
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> had = map.putIfAbsent(key, mine);
        if (had != null) return had.join();
        try {
            V v = make.get();
            mine.complete(v);
            return v;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            map.remove(key, mine);
            throw e;
        }
    }

    /** Whether region cell (gx, gz) is meant to hold a town (a pure roll, never in the Commons or by the first hamlet). */
    public static boolean townCell(RoadWorld w, int gx, int gz) {
        if (w.regions.swallowed(gx, gz)) return false;
        if (FirstTown.is(w, gx, gz)) return true;
        int[] h = w.regions.heart(gx, gz);
        SettlementGrid.Anchor f = SettlementGrid.first(w.seed, new int[]{w.cx, w.cz});
        if (Math.hypot(h[0] - f.x(), h[1] - f.z()) < FIRST_CLEAR) return false;
        return w.regions.roll(gx, gz, 0x70A7_5EEDL) < TOWN_ODDS;
    }

    /** Whether a grid settlement anchored at (x, z) stands down for a town's region (within {@link #TOWN_CLEAR} of its heart). */
    public static boolean nearTown(int x, int z) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return false;
        int gx = Math.floorDiv(x, RegionMap.PITCH), gz = Math.floorDiv(z, RegionMap.PITCH);
        for (int i = gx - 1; i <= gx + 1; i++)
            for (int j = gz - 1; j <= gz + 1; j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.hypot(h[0] - x, h[1] - z) < TOWN_CLEAR && townCell(w, i, j)) return true;
            }
        return false;
    }

    /**
     * The anchors a region cell adds to the settlement grid: its town (at the site its land offers) and its wayhouse, each
     * or null. Pure, made once.
     */
    static SettlementGrid.Anchor[] sites(RoadWorld w, int gx, int gz) {
        return once(SITES, cellKey(gx, gz), () -> {
            SettlementGrid.Anchor town = null, way = null;
            if (!w.regions.swallowed(gx, gz)) {
                int[] h = w.regions.heart(gx, gz);
                Terrain t = w.terrain();
                long seed = SettlementGridMix.mix(w.seed ^ cellKey(gx, gz) * 0x9E3779B97F4A7C15L ^ 0x7031L);
                if (townCell(w, gx, gz)) {
                    int[] s = dev.awaken.village.plan.TownPlanner.site(t, h[0], h[1], (x, z) -> dev.awaken.terrain.api.CityBasin.reservedWithin(x, z, 140));
                    if (s != null) town = new SettlementGrid.Anchor("t" + gx + "_" + gz, s[0], s[1], VillagePlan.Kind.TOWN, false, seed);
                }
                if (town == null && nearHeart(w, gx, gz) == null) {
                    int[] s = dev.awaken.village.plan.WayhousePlanner.site(t, h[0], h[1], (x, z) -> dev.awaken.terrain.api.CityBasin.reservedWithin(x, z, 60));
                    if (s != null) way = new SettlementGrid.Anchor("w" + gx + "_" + gz, s[0], s[1], VillagePlan.Kind.WAYHOUSE, false, SettlementGridMix.mix(seed + 1));
                }
            }
            return new SettlementGrid.Anchor[]{town, way};
        });
    }

    /** The grid settlement anchored nearest a region's heart within {@link #NEAR_HEART}, whose land has a site; or null. */
    static SettlementGrid.@Nullable Anchor nearHeart(RoadWorld w, int gx, int gz) {
        int[] h = w.regions.heart(gx, gz);
        for (SettlementGrid.Anchor a : SettlementGrid.gridNear(w.seed, h[0], h[1], NEAR_HEART, new int[]{w.cx, w.cz})) {
            if (VillagePlanner.hasSite(Plans.request(a), w.terrain())) return a;
        }
        return null;
    }

    /** The town and wayhouse anchors whose start is in a chunk (for the settlement grid's placement). */
    public static List<SettlementGrid.Anchor> anchorsInChunk(int chunkX, int chunkZ) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return List.of();
        int bx = (chunkX << 4) + 8, bz = (chunkZ << 4) + 8;
        List<SettlementGrid.Anchor> out = new ArrayList<>(1);
        int gx = Math.floorDiv(bx, RegionMap.PITCH), gz = Math.floorDiv(bz, RegionMap.PITCH);
        for (int i = gx - 1; i <= gx + 1; i++)
            for (int j = gz - 1; j <= gz + 1; j++) {
                int[] h = w.regions.heart(i, j);
                // the site search reaches 192 from the heart (a town) or 96 (a wayhouse): nothing further can start here
                if (Math.abs(h[0] - bx) > 216 || Math.abs(h[1] - bz) > 216 || w.regions.swallowed(i, j)) continue;
                for (SettlementGrid.Anchor a : sites(w, i, j))
                    if (a != null && a.x() >> 4 == chunkX && a.z() >> 4 == chunkZ) out.add(a);
            }
        return out;
    }

    /** The town and wayhouse anchors within a radius of a point (for the settlement grid's searches). */
    public static List<SettlementGrid.Anchor> anchorsNear(int x, int z, int radius) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return List.of();
        List<SettlementGrid.Anchor> out = new ArrayList<>();
        for (int i = Math.floorDiv(x - radius - 220, RegionMap.PITCH); i <= Math.floorDiv(x + radius + 220, RegionMap.PITCH); i++)
            for (int j = Math.floorDiv(z - radius - 220, RegionMap.PITCH); j <= Math.floorDiv(z + radius + 220, RegionMap.PITCH); j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.hypot(h[0] - x, h[1] - z) > radius + 220 || w.regions.swallowed(i, j)) continue;
                for (SettlementGrid.Anchor a : sites(w, i, j)) if (a != null && Math.hypot(a.x() - x, a.z() - z) <= radius) out.add(a);
            }
        return out;
    }

    /** The first town a player meets: its anchor, or null (no town in reach of the road north, or no city). */
    public static SettlementGrid.@Nullable Anchor firstTown() {
        RoadWorld w = RoadWorld.get();
        if (w == null || w.firstTown == null) return null;
        return sites(w, w.firstTown[0], w.firstTown[1])[0];
    }

    /** A region's hub (made once). */
    public static Hub hub(RoadWorld w, int gx, int gz) {
        return once(HUBS, cellKey(gx, gz), () -> makeHub(w, gx, gz));
    }

    private static Hub makeHub(RoadWorld w, int gx, int gz) {
        if (w.regions.swallowed(gx, gz)) return new Hub(gx, gz, HubKind.NONE, 0, 0, null, "The Commons");
        int[] h = w.regions.heart(gx, gz);
        String region = w.regions.cellRegion(gx, gz).name();
        SettlementGrid.Anchor[] s = sites(w, gx, gz);
        Terrain t = w.terrain();
        if (s[0] != null) {
            VillagePlan p = Plans.of(s[0], t);
            if (p != null) return new Hub(gx, gz, HubKind.TOWN, p.ox, p.oz, s[0], region);
        }
        SettlementGrid.Anchor near = nearHeart(w, gx, gz);
        if (near != null) {
            VillagePlan p = Plans.of(near, t);
            if (p != null) return new Hub(gx, gz, HubKind.SETTLEMENT, p.ox, p.oz, near, region);
        }
        if (s[1] != null) {
            VillagePlan p = Plans.of(s[1], t);
            if (p != null) return new Hub(gx, gz, HubKind.WAYHOUSE, p.ox, p.oz, s[1], region);
        }
        // a bare crossroads on the driest gentle ground near the heart, or none (the heart's in the sea)
        int[] dry = dryNear(t, h[0], h[1], 128);
        return dry == null ? new Hub(gx, gz, HubKind.NONE, h[0], h[1], null, region) : new Hub(gx, gz, HubKind.CROSSROADS, dry[0], dry[1], null, region);
    }

    /** The nearest dry, gentle column to a point within a reach (a 16-block lattice), or null. */
    static int @Nullable [] dryNear(Terrain t, int x, int z, int reach) {
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int dz = -reach; dz <= reach; dz += 16)
            for (int dx = -reach; dx <= reach; dx += 16) {
                double d = Math.hypot(dx, dz);
                if (d >= bd || d > reach) continue;
                int px = x + dx, pz = z + dz;
                if (t.water(px, pz) > 0) continue;
                int h0 = t.top(px, pz);
                if (Math.abs(t.top(px + 8, pz) - h0) > 3 || Math.abs(t.top(px, pz + 8) - h0) > 3) continue;
                // real land round it, not an islet in the shallows
                int dry = 0;
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    if (t.water(px + (int) Math.round(Math.cos(a) * 40), pz + (int) Math.round(Math.sin(a) * 40)) == 0) dry++;
                }
                if (dry < 6) continue;
                best = new int[]{px, pz};
                bd = d;
            }
        return best;
    }

    // ---- where a road meets a settlement ---------------------------------------------------------------------------------

    /**
     * A road's end: just outside a settlement's street end (the way it leaves, the street's height there), a port, a
     * junction on another road or a bare crossroads ({@code kind}, Road.ENTRY..); {@code place} is the settlement's kind
     * (VillagePlan.Kind's ordinal) or -1.
     */
    public record Entry(int x, int z, int y, float dx, float dz, String name, String key, int kind, int place) {}

    /** Every street end a road can leave a settlement from (a town's gates, a wayhouse's four ways, a village's main street). */
    static List<Entry> entries(VillagePlan p) {
        List<Entry> out = new ArrayList<>();
        for (VillagePlan.Street st : p.streets) {
            if (!p.exits(st)) continue;
            int n = st.points();
            if (n < 2) continue;
            for (int end = 0; end < 2; end++) {
                if (!p.exitAt(st, end)) continue;
                int a = end == 0 ? 0 : n - 1, b = end == 0 ? 1 : n - 2;
                float ex = st.x()[a], ez = st.z()[a];
                float dx = ex - st.x()[b], dz = ez - st.z()[b];
                float l = (float) Math.hypot(dx, dz);
                if (l < 1e-3f) continue;
                dx /= l;
                dz /= l;
                // step out past the settlement's own paving (its street, its gate) onto the land
                float ox = ex, oz = ez;
                int lift = st.lift()[a];
                for (int k = 0; k < 24; k++) {
                    byte kk = p.kind(Math.round(ox), Math.round(oz));
                    if (kk == CityPlan.VOID || kk == CityPlan.FREE) break;
                    ox += dx;
                    oz += dz;
                }
                ox += dx;
                oz += dz;
                out.add(new Entry(p.ox + Math.round(ox), p.oz + Math.round(oz), p.baseY + lift, dx, dz, p.name, p.key, Road.ENTRY, p.kind.ordinal()));
            }
        }
        return out;
    }

    /** The entry nearest a target point (by distance, a little by the way it leaves). */
    static @Nullable Entry entryToward(VillagePlan p, double tx, double tz) {
        Entry best = null;
        double bs = Double.MAX_VALUE;
        for (Entry e : entries(p)) {
            double d = Math.hypot(tx - e.x(), tz - e.z());
            double dot = d < 1 ? 1 : ((tx - e.x()) * e.dx() + (tz - e.z()) * e.dz()) / d;
            double score = d * (1.6 - 0.6 * dot);
            if (score < bs) {
                bs = score;
                best = e;
            }
        }
        return best;
    }

    // ---- the roads -----------------------------------------------------------------------------------------------------

    /** The highroad from region (gx, gz) east (dir 0) or south (dir 1): its pieces between the settlements on its way. */
    public static List<Road> trunk(RoadWorld w, int gx, int gz, int dir) {
        return once(ROADS, "T" + gx + "," + gz + "," + dir, () -> index(Highroads.trunk(w, gx, gz, dir)));
    }

    /** Carillon's road out (0 north past the camp and the first hamlet, 1 east, 2 south) to the region beyond. */
    public static List<Road> port(RoadWorld w, int which) {
        return once(ROADS, "P" + which, () -> index(Highroads.port(w, which)));
    }

    /** A settlement's lane to the nearest road, or nothing (it lies on a highroad, or no road is near). */
    public static List<Road> branch(RoadWorld w, SettlementGrid.Anchor a) {
        return once(ROADS, "B" + a.key(), () -> index(Highroads.branch(w, a)));
    }

    // ---- legs on the lattice -------------------------------------------------------------------------------------------

    private static final ConcurrentHashMap<Long, CompletableFuture<List<Road.Leg>>> LEGS = new ConcurrentHashMap<>();

    /** The legs whose structure starts in this chunk (none off the lattice). */
    public static List<Road.Leg> startsIn(int chunkX, int chunkZ) {
        if (Math.floorMod(chunkX, Road.LATTICE) != 0 || Math.floorMod(chunkZ, Road.LATTICE) != 0) return List.of();
        RoadWorld w = RoadWorld.get();
        if (w == null) return List.of();
        return once(LEGS, ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL), () -> {
            List<Road.Leg> out = new ArrayList<>();
            for (Road r : roadsNear(w, (chunkX << 4) + 8, (chunkZ << 4) + 8, 100))
                for (Road.Leg l : r.legs()) if (l.startX() == chunkX && l.startZ() == chunkZ) out.add(l);
            return out;
        });
    }

    /** Whether a lattice chunk's legs are known yet (the planner ahead of the players asks this). */
    public static boolean known(int chunkX, int chunkZ) {
        var f = LEGS.get(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL));
        return f != null && f.isDone();
    }

    /**
     * Every road that may pass within {@code reach} of a point: the highroads whose straight line between region hearts
     * comes near it (a road keeps within its search's ellipse of that line), Carillon's roads out, and the lanes of
     * settlements within a lane's reach.
     */
    public static List<Road> roadsNear(RoadWorld w, int x, int z, int reach) {
        List<Road> out = highroadsNear(w, x, z, reach);
        for (SettlementGrid.Anchor a : SettlementGrid.gridNear(w.seed, x, z, Highroads.LANE_REACH + reach, new int[]{w.cx, w.cz}))
            out.addAll(branch(w, a));
        return out;
    }

    /** The highroads and Carillon's roads out that may pass within {@code reach} of a point (no lanes: those cascade). */
    public static List<Road> highroadsNear(RoadWorld w, int x, int z, int reach) {
        List<Road> out = new ArrayList<>();
        int pitch = RegionMap.PITCH;
        int gx = Math.floorDiv(x, pitch), gz = Math.floorDiv(z, pitch);
        for (int i = gx - 2; i <= gx + 1; i++)
            for (int j = gz - 2; j <= gz + 1; j++)
                for (int dir = 0; dir < 2; dir++) {
                    int ni = i + (dir == 0 ? 1 : 0), nj = j + (dir == 1 ? 1 : 0);
                    if (w.regions.swallowed(i, j) || w.regions.swallowed(ni, nj)) continue;
                    int[] a = w.regions.heart(i, j), b = w.regions.heart(ni, nj);
                    if (!Highroads.mayPass(a[0], a[1], b[0], b[1], x, z, reach)) continue;
                    out.addAll(trunk(w, i, j, dir));
                }
        if (w.city) {
            for (int p = 0; p < 3; p++) {
                for (int[] seg : Highroads.portReach(w, p)) {
                    if (!Highroads.mayPass(seg[0], seg[1], seg[2], seg[3], x, z, reach)) continue;
                    out.addAll(port(w, p));
                    break;
                }
            }
        }
        return out;
    }

    // ---- laying ahead of the players -------------------------------------------------------------------------------------

    private static java.util.concurrent.@Nullable ExecutorService ahead;
    private static final java.util.Set<Long> QUEUED = ConcurrentHashMap.newKeySet();
    /** How far ahead of each player the roads are laid (past the generation front), and lattice chunks queued a pass. */
    static final int AHEAD = 1100, PER_PASS = 8;

    /**
     * Every two seconds: the lattice chunks round each player whose legs aren't known yet go to a planner thread of their
     * own, nearest first, so the roads, towns, wayhouses and settlements there are laid before the land generates and world
     * generation seldom waits for them (it asks the same cache: a chunk that needs a road being laid waits for that one).
     */
    public static void prefetch(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 40 != 21) return;
        RoadWorld w = RoadWorld.get();
        if (w == null) return;
        if (ahead == null) ahead = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Awaken road planner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        for (net.minecraft.server.level.ServerPlayer p : server.overworld().players()) {
            int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
            int reach = AHEAD / 16 / Road.LATTICE + 1;
            List<long[]> want = new ArrayList<>();
            int lx = Math.floorDiv(pcx, Road.LATTICE), lz = Math.floorDiv(pcz, Road.LATTICE);
            for (int i = lx - reach; i <= lx + reach; i++)
                for (int j = lz - reach; j <= lz + reach; j++) {
                    int cx = i * Road.LATTICE, cz = j * Road.LATTICE;
                    long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                    if (known(cx, cz) || QUEUED.contains(key)) continue;
                    long d = (long) (cx - pcx) * (cx - pcx) + (long) (cz - pcz) * (cz - pcz);
                    if (d * 256 > (long) AHEAD * AHEAD) continue;
                    want.add(new long[]{d, cx, cz, key});
                }
            want.sort((u, v) -> Long.compare(u[0], v[0]));
            for (int k = 0; k < Math.min(PER_PASS, want.size()); k++) {
                long[] u = want.get(k);
                QUEUED.add(u[3]);
                ahead.submit(() -> {
                    try {
                        startsIn((int) u[1], (int) u[2]);
                    } catch (RuntimeException e) {
                        Awaken.LOG.error("[road] laying ahead at chunk {} {} failed", u[1], u[2], e);
                    } finally {
                        QUEUED.remove(u[3]);
                    }
                });
            }
        }
    }

    /** The planner thread stops with the server (a pass under way finishes into the cleared caches harmlessly). */
    public static void stopAhead() {
        if (ahead != null) ahead.shutdownNow();
        ahead = null;
        QUEUED.clear();
    }

    // ---- what the network looks like, for commands and the survey -------------------------------------------------------

    public static String stats() {
        return String.format(Locale.ROOT, "%d roads laid, %.0f ms each on average, %d land reads", planned,
                planned == 0 ? 0 : planNanos / 1e6 / planned, reads);
    }

    /** Every road already laid this session (commands, the survey). */
    public static List<Road> laid() {
        List<Road> out = new ArrayList<>();
        for (var f : ROADS.values()) if (f.isDone() && !f.isCompletedExceptionally()) out.addAll(f.join());
        out.sort(Comparator.comparing(Road::key));
        return out;
    }

    /** The laid road nearest a point within a reach, with the point index: for commands and caravans. */
    public static Optional<Nearest> nearest(double x, double z, double reach) {
        Nearest best = null;
        for (Road r : laid()) {
            for (int i = 0; i < r.points(); i++) {
                double d = Math.hypot(r.x()[i] - x, r.z()[i] - z);
                if (d <= reach && (best == null || d < best.distance())) best = new Nearest(r, i, d);
            }
        }
        return Optional.ofNullable(best);
    }

    public record Nearest(Road road, int point, double distance) {}

    // ---- the laid roads by chunk (who walks where, for the Atlas) ---------------------------------------------------------

    private static final List<Road> ALL = new ArrayList<>();
    private static final ConcurrentHashMap<Long, List<int[]>> BY_CHUNK = new ConcurrentHashMap<>();

    /** Files a newly laid road's points by chunk. */
    static List<Road> index(List<Road> roads) {
        synchronized (ALL) {
            for (Road r : roads) {
                int id = ALL.size();
                ALL.add(r);
                for (int i = 0; i < r.points(); i++) {
                    long k = net.minecraft.world.level.ChunkPos.pack((int) Math.floor(r.x()[i]) >> 4, (int) Math.floor(r.z()[i]) >> 4);
                    BY_CHUNK.computeIfAbsent(k, kk -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(new int[]{id, i});
                }
            }
        }
        return roads;
    }

    /** The laid road point nearest (x, z) within {@code reach} (a few blocks: the chunk and its neighbours), or null. */
    public static @Nullable Nearest indexed(double x, double z, double reach) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        Nearest best = null;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                List<int[]> l = BY_CHUNK.get(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz));
                if (l == null) continue;
                for (int[] e : l) {
                    Road r;
                    synchronized (ALL) {
                        r = ALL.get(e[0]);
                    }
                    double d = Math.hypot(r.x()[e[1]] - x, r.z()[e[1]] - z);
                    if (d <= reach && (best == null || d < best.distance())) best = new Nearest(r, e[1], d);
                }
            }
        return best;
    }

    /** The distance to the laid road point nearest (x, z) if one is within {@code reach} (any reach: it scans the chunks round), else -1. */
    public static double distanceWithin(double x, double z, double reach) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4, w = (int) Math.ceil(reach / 16) + 1;
        double best = Double.MAX_VALUE;
        for (int dx = -w; dx <= w; dx++)
            for (int dz = -w; dz <= w; dz++) {
                List<int[]> l = BY_CHUNK.get(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz));
                if (l == null) continue;
                for (int[] e : l) {
                    Road r;
                    synchronized (ALL) {
                        r = ALL.get(e[0]);
                    }
                    best = Math.min(best, Math.hypot(r.x()[e[1]] - x, r.z()[e[1]] - z));
                }
            }
        return best <= reach ? best : -1;
    }

    /** The seed mixer the grid uses (a stable hash). */
    static final class SettlementGridMix {
        private SettlementGridMix() {}

        static long mix(long z) {
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }
    }

    static void counted(long nanos, int landReads) {
        synchronized (RoadNet.class) {
            planNanos += nanos;
            planned++;
            reads += landReads;
        }
    }

    static void log(String fmt, Object... args) {
        Awaken.LOG.info("[road] " + fmt, args);
    }
}
