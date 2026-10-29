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
    /** In the wide world (regions of 1,600, SPEC §21.12) a little more often, so towns don't thin out as much as regions do. */
    static final double TOWN_ODDS_WIDE = 0.35;
    /** The grid's settlements keep this far from a town's region's heart. */
    public static final int TOWN_CLEAR = 360;
    /** A grid settlement anchored this near a region's heart is that region's hub. */
    static final int NEAR_HEART = 300;
    /** No town's region heart this near the first hamlet (its land stays the hamlet's). */
    static final int FIRST_CLEAR = 520;

    private static final ConcurrentHashMap<Long, CompletableFuture<Hub>> HUBS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<List<Road>>> ROADS = new ConcurrentHashMap<>();
    /**
     * The highroads' ways (by "T" key), Carillon's roads out's (by "P" key) and their pieces (by the road's key, "T...#k",
     * "P1#k"), each laid alone: SPEC §28.9, Cost.
     */
    private static final ConcurrentHashMap<String, CompletableFuture<Highroads.Way>> WAYS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<List<Highroads.Way>>> PORTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<Optional<Road>>> PIECES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CompletableFuture<SettlementGrid.Anchor[]>> SITES = new ConcurrentHashMap<>();
    static long planNanos, planned, reads;
    /** Time spent finding towns' and wayhouses' sites and the settlements near hearts, and bare crossroads (the bench). */
    static final java.util.concurrent.atomic.LongAdder SITE_NS = new java.util.concurrent.atomic.LongAdder(), DRY_NS = new java.util.concurrent.atomic.LongAdder();

    public static void clear() {
        HUBS.clear();
        ROADS.clear();
        WAYS.clear();
        PORTS.clear();
        PIECES.clear();
        SITES.clear();
        Holdfasts.clear();
        LEGS.clear();
        LATER.clear();
        BY_CHUNK.clear();
        planNanos = planned = reads = 0;
        SITE_NS.reset();
        DRY_NS.reset();
        RoadTimer.reset();
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
        return w.regions.roll(gx, gz, 0x70A7_5EEDL) < (w.regions.rules() >= 3 ? TOWN_ODDS_WIDE : TOWN_ODDS);
    }

    /** Whether a grid settlement anchored at (x, z) stands down for a town's region (within {@link #TOWN_CLEAR} of its heart). */
    public static boolean nearTown(int x, int z) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return false;
        int gx = Math.floorDiv(x, w.regions.pitch()), gz = Math.floorDiv(z, w.regions.pitch());
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
            long t0 = System.nanoTime();
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
            SITE_NS.add(System.nanoTime() - t0);
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
        int gx = Math.floorDiv(bx, w.regions.pitch()), gz = Math.floorDiv(bz, w.regions.pitch());
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

    /**
     * The town and wayhouse anchors within a radius of a point (for the settlement grid's searches). With {@code wait}
     * false (the server thread's own searches round the players) only the regions whose sites are already found answer;
     * the others are found on the road planner for a later search, so the server thread never plans.
     */
    public static List<SettlementGrid.Anchor> anchorsNear(int x, int z, int radius, boolean wait) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return List.of();
        long t0 = System.nanoTime();
        List<SettlementGrid.Anchor> out = new ArrayList<>();
        for (int i = Math.floorDiv(x - radius - 220, w.regions.pitch()); i <= Math.floorDiv(x + radius + 220, w.regions.pitch()); i++)
            for (int j = Math.floorDiv(z - radius - 220, w.regions.pitch()); j <= Math.floorDiv(z + radius + 220, w.regions.pitch()); j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.hypot(h[0] - x, h[1] - z) > radius + 220 || w.regions.swallowed(i, j)) continue;
                if (!wait && !sitesKnown(i, j)) {
                    sitesSoon(w, i, j);
                    continue;
                }
                for (SettlementGrid.Anchor a : sites(w, i, j)) if (a != null && Math.hypot(a.x() - x, a.z() - z) <= radius) out.add(a);
            }
        if (RoadTimer.onServer()) RoadTimer.server(System.nanoTime() - t0, "the towns' and wayhouses' sites within " + radius + " of " + x + "," + z);
        return out;
    }

    static boolean sitesKnown(int gx, int gz) {
        var f = SITES.get(cellKey(gx, gz));
        return f != null && f.isDone();
    }

    /** A region's sites, found by the road planners (nothing if another thread is already finding them). */
    static void sitesSoon(RoadWorld w, int gx, int gz) {
        long key = cellKey(gx, gz);
        if (!SITES.containsKey(key)) RoadPlanners.job("S" + gx + "," + gz, RoadPlanners.SOON, 0, () -> {
            if (!SITES.containsKey(key)) sites(w, gx, gz);
        });
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
        long t0 = System.nanoTime();
        SettlementGrid.Anchor near = nearHeart(w, gx, gz);
        SITE_NS.add(System.nanoTime() - t0);
        if (near != null) {
            VillagePlan p = Plans.of(near, t);
            if (p != null) return new Hub(gx, gz, HubKind.SETTLEMENT, p.ox, p.oz, near, region);
        }
        if (s[1] != null) {
            VillagePlan p = Plans.of(s[1], t);
            if (p != null) return new Hub(gx, gz, HubKind.WAYHOUSE, p.ox, p.oz, s[1], region);
        }
        // a bare crossroads on the driest gentle ground near the heart, or none (the heart's in the sea)
        long t1 = System.nanoTime();
        int[] dry = dryNear(t, h[0], h[1], 128);
        DRY_NS.add(System.nanoTime() - t1);
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
        List<Road> out = new ArrayList<>();
        pieces(w, way(w, gx, gz, dir), out);
        return out;
    }

    /** A way's pieces, laid. */
    private static void pieces(RoadWorld w, Highroads.Way way, List<Road> out) {
        for (int k = 0; k < way.pieces(); k++) {
            Road r = piece(w, way, k);
            if (r != null) out.add(r);
        }
    }

    /** A way's pieces that may come within {@code reach} each way of a point (the others aren't laid for it). */
    private static void piecesNear(RoadWorld w, Highroads.Way way, int x, int z, int reach, List<Road> out) {
        for (int k = 0; k < way.pieces(); k++) {
            if (!way.mayCome(k, x, z, reach)) continue;
            Road r = piece(w, way, k);
            if (r != null) out.add(r);
        }
    }

    /** A highroad's way (its hubs, the settlements it rides through and its pieces' ends), worked out once. */
    static Highroads.Way way(RoadWorld w, int gx, int gz, int dir) {
        return once(WAYS, "T" + gx + "," + gz + "," + dir, () -> Highroads.trunkWay(w, gx, gz, dir));
    }

    /** A highroad's piece, laid once (and filed by chunk), or null where no way is found. */
    static @Nullable Road piece(RoadWorld w, Highroads.Way way, int k) {
        return once(PIECES, way.key() + "#" + k, () -> {
            Road r = Highroads.piece(w, way, k);
            if (r != null) index(List.of(r));
            return Optional.ofNullable(r);
        }).orElse(null);
    }

    /** A highroad's piece if laid already (else null, as when no way was found). */
    static @Nullable Road laidPiece(Highroads.Way way, int k) {
        var f = PIECES.get(way.key() + "#" + k);
        return f != null && f.isDone() && !f.isCompletedExceptionally() ? f.join().orElse(null) : null;
    }

    /** Carillon's road out (0 north past the camp and the first hamlet, 1 east, 2 south) to the region beyond. */
    public static List<Road> port(RoadWorld w, int which) {
        List<Road> out = new ArrayList<>();
        for (Highroads.Way way : portWays(w, which)) pieces(w, way, out);
        return out;
    }

    /** Its first road (north, P0#0, or where no way was found for that the first on from the first hamlet), or null: none. */
    public static @Nullable Road portFirst(RoadWorld w, int which) {
        for (Highroads.Way way : portWays(w, which))
            for (int k = 0; k < way.pieces(); k++) {
                Road r = piece(w, way, k);
                if (r != null) return r;
            }
        return null;
    }

    /**
     * Its ways, worked out once. The region it heads for is the nearest whose way has a piece: its pieces are laid in
     * order until one is, and kept as its own; a way none of whose pieces is laid has nothing to keep.
     */
    static List<Highroads.Way> portWays(RoadWorld w, int which) {
        return once(PORTS, "P" + which, () -> Highroads.portWays(w, which, way -> {
            for (int k = 0; k < way.pieces(); k++) {
                Road r = Highroads.piece(w, way, k);
                if (r == null) continue;
                for (int j = 0; j < k; j++) PIECES.putIfAbsent(way.key() + "#" + j, CompletableFuture.completedFuture(Optional.empty()));
                PIECES.putIfAbsent(way.key() + "#" + k, CompletableFuture.completedFuture(Optional.of(r)));
                index(List.of(r));
                return true;
            }
            return false;
        }));
    }

    /** A settlement's lane to the nearest road, or nothing (it lies on a highroad, or no road is near). */
    public static List<Road> branch(RoadWorld w, SettlementGrid.Anchor a) {
        return once(ROADS, "B" + a.key(), () -> index(Highroads.branch(w, a)));
    }

    // ---- legs on the lattice -------------------------------------------------------------------------------------------

    private static final ConcurrentHashMap<Long, CompletableFuture<List<Road.Leg>>> LEGS = new ConcurrentHashMap<>();

    /** A leg's points all lie within this of its start chunk's middle (each way). */
    static final int LEG_REACH = 100;

    /** The legs whose structure starts in this chunk (none off the lattice). */
    public static List<Road.Leg> startsIn(int chunkX, int chunkZ) {
        if (Math.floorMod(chunkX, Road.LATTICE) != 0 || Math.floorMod(chunkZ, Road.LATTICE) != 0) return List.of();
        RoadWorld w = RoadWorld.get();
        if (w == null) return List.of();
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        var known = LEGS.get(key);
        if (known != null && known.isDone()) return known.join();
        // world generation is about to wait for what the planners haven't made yet: they make it beside it, first
        if (known == null && !RoadPlanners.on()) fanOut(w, chunkX, chunkZ, RoadPlanners.URGENT, 0);
        long t0 = System.nanoTime();
        List<Road.Leg> legs = once(LEGS, key, () -> {
            List<Road.Leg> out = new ArrayList<>();
            for (Road r : roadsAt(w, chunkX, chunkZ))
                for (Road.Leg l : r.legs()) if (l.startX() == chunkX && l.startZ() == chunkZ) out.add(l);
            return out;
        });
        RoadTimer.startsIn(chunkX, chunkZ, System.nanoTime() - t0, known == null);
        return legs;
    }

    /** Lattice chunks whose structure starts world generation set aside until an urgent planner has made their legs. */
    private static final ConcurrentHashMap<Long, CompletableFuture<Void>> LATER = new ConcurrentHashMap<>();

    /**
     * For world generation about to wait for a lattice chunk's legs (the Turbo backstop {@code RoadStarts}, SPEC §28.9):
     * null when it needn't (off the lattice, no roads, the legs known), else a future done once they are, laid on the
     * road starts thread exactly as world generation's executor laid them inline (its pieces fanned out to the planners
     * first), or by whoever is making them already (its pieces then asked for urgently too). The step that waited runs
     * once it's done and answers at once.
     */
    public static @Nullable CompletableFuture<?> legsLater(int chunkX, int chunkZ) {
        if (Math.floorMod(chunkX, Road.LATTICE) != 0 || Math.floorMod(chunkZ, Road.LATTICE) != 0) return null;
        RoadWorld w = RoadWorld.get();
        if (w == null) return null;
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        var known = LEGS.get(key);
        if (known != null) {
            if (known.isDone()) return null;
            RoadPlanners.job("F" + key, RoadPlanners.URGENT, 0, () -> fanOut(w, chunkX, chunkZ, RoadPlanners.URGENT, 0));
            return known;
        }
        return LATER.computeIfAbsent(key, k -> {
            CompletableFuture<Void> f = new CompletableFuture<>();
            RoadPlanners.forWorldgen(() -> {
                try {
                    startsIn(chunkX, chunkZ);
                    f.complete(null);
                } catch (RuntimeException e) {
                    Awaken.LOG.error("[road] laying lattice chunk {},{}'s legs for world generation failed", chunkX, chunkZ, e);
                    f.completeExceptionally(e);
                } finally {
                    LATER.remove(k, f);
                }
            });
            return f;
        });
    }

    /** Lattice chunks world generation has set aside for their legs (the stall watch's line). */
    static int setAside() {
        return LATER.size();
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

    /**
     * The roads that may start a leg in a lattice chunk: {@link #roadsNear}'s for its middle, but for the lanes that
     * provably can't reach it from what's laid so far ({@link Highroads#laneMayStart}), left until they're needed nearer
     * their settlements. A lane needs the highroads round its own settlement, which need their regions' hubs: the cascade
     * that made a chunk far out wait. The legs starting here are exactly roadsNear's.
     */
    static List<Road> roadsAt(RoadWorld w, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
        List<Road> out = highroadPiecesNear(w, x, z, LEG_REACH);
        for (SettlementGrid.Anchor a : SettlementGrid.gridNear(w.seed, x, z, Highroads.LANE_REACH + LEG_REACH, new int[]{w.cx, w.cz}))
            if (laneMayStart(w, a, chunkX, chunkZ)) out.addAll(branch(w, a));
        return out;
    }

    /** Whether a settlement's lane may start a leg in a lattice chunk (laid or being laid: yes; no plan: no lane). */
    private static boolean laneMayStart(RoadWorld w, SettlementGrid.Anchor a, int chunkX, int chunkZ) {
        if (ROADS.containsKey("B" + a.key())) return true;
        VillagePlan p = Plans.of(a, w.terrain());
        return p != null && Highroads.laneMayStart(w, p, chunkX, chunkZ);
    }

    /** A highroad's or Carillon's road out's pieces by its key ("T...", "P1") laid already, else null (its way unknown). */
    static @Nullable List<Road> laidAs(String key) {
        List<Highroads.Way> ways;
        if (key.startsWith("T")) {
            var f = WAYS.get(key);
            if (!done(f)) return null;
            ways = List.of(f.join());
        } else {
            var f = PORTS.get(key);
            if (!done(f)) return null;
            ways = f.join();
        }
        List<Road> out = new ArrayList<>();
        for (Highroads.Way way : ways)
            for (int k = 0; k < way.pieces(); k++) {
                Road r = laidPiece(way, k);
                if (r != null) out.add(r);
            }
        return out;
    }

    private static boolean done(@Nullable CompletableFuture<?> f) {
        return f != null && f.isDone() && !f.isCompletedExceptionally();
    }

    /** The highroads and Carillon's roads out that may pass within {@code reach} of a point (no lanes: those cascade). */
    public static List<Road> highroadsNear(RoadWorld w, int x, int z, int reach) {
        List<Road> out = new ArrayList<>();
        for (int[] c : trunksNear(w, x, z, reach)) out.addAll(trunk(w, c[0], c[1], c[2]));
        for (int p : portsNear(w, x, z, reach)) out.addAll(port(w, p));
        return out;
    }

    /** As {@link #highroadsNear}, but only the pieces that may come within {@code reach} each way of the point. */
    static List<Road> highroadPiecesNear(RoadWorld w, int x, int z, int reach) {
        List<Road> out = new ArrayList<>();
        for (int[] c : trunksNear(w, x, z, reach)) piecesNear(w, way(w, c[0], c[1], c[2]), x, z, reach, out);
        for (int p : portsNear(w, x, z, reach))
            for (Highroads.Way way : portWays(w, p)) piecesNear(w, way, x, z, reach, out);
        return out;
    }

    /** The highroads (region cell and way, {gx, gz, dir}) whose straight line between hearts comes near a point. */
    private static List<int[]> trunksNear(RoadWorld w, int x, int z, int reach) {
        List<int[]> out = new ArrayList<>();
        int pitch = w.regions.pitch();
        int gx = Math.floorDiv(x, pitch), gz = Math.floorDiv(z, pitch);
        for (int i = gx - 2; i <= gx + 1; i++)
            for (int j = gz - 2; j <= gz + 1; j++)
                for (int dir = 0; dir < 2; dir++) {
                    int ni = i + (dir == 0 ? 1 : 0), nj = j + (dir == 1 ? 1 : 0);
                    if (w.regions.swallowed(i, j) || w.regions.swallowed(ni, nj)) continue;
                    int[] a = w.regions.heart(i, j), b = w.regions.heart(ni, nj);
                    if (Highroads.mayPass(a[0], a[1], b[0], b[1], x, z, reach)) out.add(new int[]{i, j, dir});
                }
        return out;
    }

    /** Carillon's roads out whose ways come near a point. */
    private static List<Integer> portsNear(RoadWorld w, int x, int z, int reach) {
        List<Integer> out = new ArrayList<>();
        if (w.city) {
            for (int p = 0; p < 3; p++) {
                for (int[] seg : Highroads.portReach(w, p)) {
                    if (!Highroads.mayPass(seg[0], seg[1], seg[2], seg[3], x, z, reach)) continue;
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Queues what a lattice chunk's legs are made of as jobs the planners share: the plans of the settlements whose lanes
     * may reach it and the hubs of the regions whose highroads may pass it, then those highroads and Carillon's roads out,
     * then the lanes. A piece made or being made is left alone.
     */
    static void fanOut(RoadWorld w, int chunkX, int chunkZ, int tier, long order) {
        int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
        List<SettlementGrid.Anchor> grid = SettlementGrid.gridNear(w.seed, x, z, Highroads.LANE_REACH + LEG_REACH, new int[]{w.cx, w.cz});
        for (SettlementGrid.Anchor a : grid)
            if (!Plans.asked(a.key())) RoadPlanners.job("p" + a.key(), tier, order * 4, () -> {
                if (!Plans.asked(a.key())) Plans.of(a, w.terrain());
            });
        List<int[]> trunks = trunksNear(w, x, z, LEG_REACH);
        for (int[] c : trunks) {
            hubSoon(w, c[0], c[1], tier, order * 4);
            hubSoon(w, c[0] + (c[2] == 0 ? 1 : 0), c[1] + (c[2] == 1 ? 1 : 0), tier, order * 4);
        }
        for (int[] c : trunks) {
            var known = WAYS.get("T" + c[0] + "," + c[1] + "," + c[2]);
            if (done(known)) piecesSoon(w, known.join(), x, z, tier, order * 4 + 1);
            else RoadPlanners.job("T" + c[0] + "," + c[1] + "," + c[2] + "@" + chunkX + "," + chunkZ, tier, order * 4 + 1,
                    () -> piecesSoon(w, way(w, c[0], c[1], c[2]), x, z, tier, order * 4 + 1));
        }
        for (int p : portsNear(w, x, z, LEG_REACH)) {
            var known = PORTS.get("P" + p);
            if (done(known)) for (Highroads.Way way : known.join()) piecesSoon(w, way, x, z, tier, order * 4 + 1);
            else RoadPlanners.job("P" + p + "@" + chunkX + "," + chunkZ, tier, order * 4 + 1, () -> {
                for (Highroads.Way way : portWays(w, p)) piecesSoon(w, way, x, z, tier, order * 4 + 1);
            });
        }
        // a lane once the highroads near the chunk are laid (they may show it can't reach it)
        for (SettlementGrid.Anchor a : grid)
            if (!ROADS.containsKey("B" + a.key())) RoadPlanners.job("B" + a.key() + "@" + chunkX + "," + chunkZ, tier, order * 4 + 2, () -> {
                if (ROADS.containsKey("B" + a.key())) return;
                highroadPiecesNear(w, x, z, LEG_REACH);
                if (laneMayStart(w, a, chunkX, chunkZ)) branch(w, a);
            });
    }

    private static void hubSoon(RoadWorld w, int gx, int gz, int tier, long order) {
        long key = cellKey(gx, gz);
        if (!HUBS.containsKey(key)) RoadPlanners.job("H" + gx + "," + gz, tier, order, () -> {
            if (!HUBS.containsKey(key)) hub(w, gx, gz);
        });
    }

    /** Queues a way's pieces that may come near a lattice chunk's middle, each its own job. */
    private static void piecesSoon(RoadWorld w, Highroads.Way way, int x, int z, int tier, long order) {
        for (int k = 0; k < way.pieces(); k++) {
            int kk = k;
            if (way.mayCome(k, x, z, LEG_REACH) && !PIECES.containsKey(way.key() + "#" + k))
                RoadPlanners.job(way.key() + "#" + k, tier, order, () -> piece(w, way, kk));
        }
    }

    // ---- laying ahead of the players -------------------------------------------------------------------------------------

    /** How far ahead of each player the roads are laid (past the generation front), and lattice chunks queued a pass. */
    static final int AHEAD = 1100, PER_PASS = 16;

    /**
     * Every two seconds: the lattice chunks round each player whose legs aren't known yet go to the road planners, nearest
     * first, each as the pieces it's made of ({@link #fanOut}), so the roads, towns, wayhouses and settlements there are
     * laid before the land generates and world generation seldom waits for them (it asks the same cache: a chunk that needs
     * a road being laid waits for that one).
     */
    public static void prefetch(net.minecraft.server.MinecraftServer server) {
        RoadTimer.tick(server, RoadPlanners.queued(), unknown);
        if (server.getTickCount() % 40 != 21) return;
        RoadWorld w = RoadWorld.get();
        if (w == null) return;
        int behind = 0;
        for (net.minecraft.server.level.ServerPlayer p : server.overworld().players()) behind += ahead(w, p.getBlockX() >> 4, p.getBlockZ() >> 4, PER_PASS);
        unknown = behind;
    }

    /**
     * One pass ahead of a player at chunk (pcx, pcz): the nearest {@code most} lattice chunks within {@link #AHEAD} whose
     * legs aren't known or queued are handed to the planners (the caller only queues them: the server thread never fans
     * out or plans here); how many are left.
     */
    static int ahead(RoadWorld w, int pcx, int pcz, int most) {
        int reach = AHEAD / 16 / Road.LATTICE + 1;
        List<long[]> want = new ArrayList<>();
        int lx = Math.floorDiv(pcx, Road.LATTICE), lz = Math.floorDiv(pcz, Road.LATTICE);
        for (int i = lx - reach; i <= lx + reach; i++)
            for (int j = lz - reach; j <= lz + reach; j++) {
                int cx = i * Road.LATTICE, cz = j * Road.LATTICE;
                long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                if (LEGS.containsKey(key) || RoadPlanners.pending("L" + key) || RoadPlanners.pending("F" + key)) continue;
                long d = (long) (cx - pcx) * (cx - pcx) + (long) (cz - pcz) * (cz - pcz);
                if (d * 256 > (long) AHEAD * AHEAD) continue;
                want.add(new long[]{d, cx, cz, key});
            }
        want.sort((u, v) -> Long.compare(u[0], v[0]));
        for (int k = 0; k < Math.min(most, want.size()); k++) {
            long[] u = want.get(k);
            // the fan-out reads the region map and the grid (up to a few ms): a planner does it, just ahead of the pieces it queues
            RoadPlanners.job("F" + u[3], RoadPlanners.AHEAD, u[0] * 4, () -> fanOut(w, (int) u[1], (int) u[2], RoadPlanners.AHEAD, u[0]));
            RoadPlanners.job("L" + u[3], RoadPlanners.AHEAD, u[0] * 4 + 3, () -> startsIn((int) u[1], (int) u[2]));
        }
        return want.size();
    }

    /** Lattice chunks within reach of the players whose legs weren't known at the last pass (the timer's line). */
    private static int unknown;

    /** The planners stop with the server (a piece under way finishes into the cleared caches harmlessly). */
    public static void stopAhead() {
        RoadPlanners.stop();
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
        for (var f : PIECES.values()) if (f.isDone() && !f.isCompletedExceptionally()) f.join().ifPresent(out::add);
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

    /** A laid road's point, filed under its chunk. */
    private record Spot(Road road, int point) {}

    // Readers (the server thread: a creature's road distance, the Atlas) take no lock: each chunk's list is copy-on-write,
    // and a batch's points go into it in one copy (SPEC §28.9, Cost: the server never waits for planning).
    private static final ConcurrentHashMap<Long, List<Spot>> BY_CHUNK = new ConcurrentHashMap<>();

    /** Files a newly laid road's points by chunk. */
    static List<Road> index(List<Road> roads) {
        java.util.HashMap<Long, List<Spot>> batch = new java.util.HashMap<>();
        for (Road r : roads)
            for (int i = 0; i < r.points(); i++) {
                long k = net.minecraft.world.level.ChunkPos.pack((int) Math.floor(r.x()[i]) >> 4, (int) Math.floor(r.z()[i]) >> 4);
                batch.computeIfAbsent(k, kk -> new ArrayList<>()).add(new Spot(r, i));
            }
        batch.forEach((k, spots) -> BY_CHUNK.computeIfAbsent(k, kk -> new java.util.concurrent.CopyOnWriteArrayList<>()).addAll(spots));
        return roads;
    }

    /** The laid road point nearest (x, z) within {@code reach} (a few blocks: the chunk and its neighbours), or null. */
    public static @Nullable Nearest indexed(double x, double z, double reach) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        Nearest best = null;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                List<Spot> l = BY_CHUNK.get(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz));
                if (l == null) continue;
                for (Spot e : l) {
                    double d = Math.hypot(e.road().x()[e.point()] - x, e.road().z()[e.point()] - z);
                    if (d <= reach && (best == null || d < best.distance())) best = new Nearest(e.road(), e.point(), d);
                }
            }
        return best;
    }

    /** The laid road point nearest (x, z) within any {@code reach} (it scans the chunks round), or null. */
    public static @Nullable Nearest nearestWithin(double x, double z, double reach) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4, w = (int) Math.ceil(reach / 16) + 1;
        Nearest best = null;
        for (int dx = -w; dx <= w; dx++)
            for (int dz = -w; dz <= w; dz++) {
                List<Spot> l = BY_CHUNK.get(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz));
                if (l == null) continue;
                for (Spot e : l) {
                    double d = Math.hypot(e.road().x()[e.point()] - x, e.road().z()[e.point()] - z);
                    if (d <= reach && (best == null || d < best.distance())) best = new Nearest(e.road(), e.point(), d);
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
                List<Spot> l = BY_CHUNK.get(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz));
                if (l == null) continue;
                for (Spot e : l) best = Math.min(best, Math.hypot(e.road().x()[e.point()] - x, e.road().z()[e.point()] - z));
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
