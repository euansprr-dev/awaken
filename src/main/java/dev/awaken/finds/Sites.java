package dev.awaken.finds;

import dev.awaken.core.api.CityHooks;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the finds stand (SPEC §58.4). The world is cut into cells on two layers (curios 96 blocks, finds 256); each cell
 * may hold one site, at a place inside it picked from the seed. Whether it holds one, and what, comes from:
 * <ul>
 *   <li><b>Richness</b>: a slow field of quiet and lively land. In quiet land a curio cell is always empty and a find
 *   cell nearly always. This is what keeps the world from feeling like a fairground: long stretches hold nothing.</li>
 *   <li><b>The deal</b>: a hash pick among the kinds that fit the land's rank and group, thinning for kinds from nearer
 *   home, moved on when a cell within a few rings would pick the same (no twins close together).</li>
 *   <li><b>The ground</b>: {@link Anchors} has to find what the kind needs. No fitting ground, no find.</li>
 * </ul>
 * All of it is a pure function of the seed, so any chunk can ask and the server gets the same answer later.
 */
public final class Sites {
    private Sites() {}

    static final int[] CELL = {96, 256, 384}, EDGE = {18, 44, 80}, RINGS = {4, 4, 2};
    /** The layer number of a site stood by hand (the gallery, a test): it stands on a level pad, not on the world's ground. */
    public static final int HAND = 3;
    /** The chance a cell holds something where the land is at its usual liveliness. */
    // The world deals places only (Euan 10 Nov 2026: "actual places, not just single small things"). The small kinds
    // stay in the code as furnishing and for their tests; their layers are off.
    // Layer 1 is the remains (SPEC §62.6): small, and only where a monument stands near.
    static final double[] BASE = {0, 0.8, 1.0};
    /** The furthest any scene reaches from its heart. */
    public static final int MAX_REACH = 64;
    /** Below this the land is quiet. About three parts in ten of the world. */
    static final double QUIET = 0.42;
    /** Round spawn nothing is quiet, so the first hour meets each verb. */
    static final int HOME = 420;

    private static final Site NONE = new Site(null, 0, 0, 0, 0, 0, 0, RegionMap.Group.PLAINS, 0);
    private static final ConcurrentHashMap<Long, Site> SITES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> PICKS = new ConcurrentHashMap<>();
    private static final Map<String, Scene> SCENES = java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Scene> e) {
            return size() > 384;
        }
    });

    public static void reset() {
        SITES.clear();
        PICKS.clear();
        SCENES.clear();
    }

    static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    private static long cellHash(long seed, int layer, int cx, int cz) {
        return mix(mix(seed, layer * 31L + 7), mix(cx, cz));
    }

    private static long cellKey(int layer, int cx, int cz) {
        return ((long) layer << 60) ^ ((cx & 0x3FFFFFFFL) << 30) ^ (cz & 0x3FFFFFFFL);
    }

    // ---- richness ---------------------------------------------------------------------------------------------------

    private static double lattice(long seed, int ix, int iz) {
        return (mix(mix(seed, ix), iz) >>> 11) / (double) (1L << 53);
    }

    private static double value(long seed, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = lattice(seed, ix, iz), b = lattice(seed, ix + 1, iz), c = lattice(seed, ix, iz + 1), d = lattice(seed, ix + 1, iz + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    /** The slow field, 0..1: low is quiet land, high is where things gather. */
    public static double field(long seed, int x, int z) {
        return 0.62 * value(seed ^ 0x51ED270BL, x / 1100.0, z / 1100.0) + 0.38 * value(seed ^ 0x2545F491L, x / 420.0, z / 420.0);
    }

    /** How much a cell's chance is scaled at a place: 0 in quiet land for curios, up to about 1.6 where things gather. */
    public static double richness(long seed, int x, int z, int layer, int spawnX, int spawnZ) {
        double n = field(seed, x, z);
        long dx = x - spawnX, dz = z - spawnZ;
        boolean home = dx * dx + dz * dz < (long) HOME * HOME;
        if (n < QUIET) return home ? 0.9 : layer == 0 ? 0 : 0.12;
        double t = (n - QUIET) / (1 - QUIET);
        t = t * t * (3 - 2 * t);
        double m = 0.45 + 1.9 * t;
        return home ? Math.max(m, 0.9) : m;
    }

    // ---- the deal ---------------------------------------------------------------------------------------------------

    private record Ask(ServerLevel level, long seed, RegionMap map, Terrain terrain, int spawnX, int spawnZ) {}

    private static @Nullable Ask ask(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || !FindsModule.on()) return null;
        RegionMap map = RegionService.mapIfReady(level.getServer());
        if (map == null) return null;
        BlockPos spawn = level.getRespawnData().pos();
        return new Ask(level, level.getSeed(), map, Terrain.of(level), spawn.getX(), spawn.getZ());
    }

    private static int cellX(long h, int layer, int cx) {
        return cx * CELL[layer] + EDGE[layer] + (int) Math.floorMod(h >>> 8, (long) CELL[layer] - 2 * EDGE[layer]);
    }

    private static int cellZ(long h, int layer, int cz) {
        return cz * CELL[layer] + EDGE[layer] + (int) Math.floorMod(h >>> 30, (long) CELL[layer] - 2 * EDGE[layer]);
    }

    /** The weights of every kind of a layer at a place: 0 where it doesn't fit. */
    private static double[] weights(Ask a, int layer, int x, int z) {
        List<Kind> kinds = Kinds.layer(layer);
        double[] w = new double[kinds.size()];
        RegionMap.Region r = a.map.regionAt(x, z);
        int rank = Math.min(5, RegionMap.rankOf(a.map.levelAt(x, z)));
        // a place goes by the biome where it stands, not by its region's name: a region is named for its heart, and the first
        // tours had a desert caravanserai in a jungle
        RegionMap.Group group = layer == 2 ? landAt(a, x, z) : a.map.landOf(r).group();
        long dx = x - a.spawnX, dz = z - a.spawnZ;
        boolean home = dx * dx + dz * dz < (long) HOME * HOME;
        for (int i = 0; i < w.length; i++) {
            Kind k = kinds.get(i);
            if (!k.fits(group)) continue;
            if (layer == 2 && k.set() != Kind.FSet.MONUMENTS) continue; // the thirty everyday places are not dealt (rejected, SPEC §61.1)
            if (layer == 1 && k.set() != Kind.FSet.REMAINS) continue;   // nor the small finds: only the remains (SPEC §62.6)
            // Bands (SPEC §62.2): nothing stands nearer home than its own rank, so each band out holds monuments the
            // last did not. At the land's rank and one under it a kind is at full weight; older ones thin by half a rank.
            if (k.rank() > rank) continue;
            double v = layer == 2 ? k.weight() * Math.pow(0.5, Math.max(0, rank - k.rank() - 1)) : k.weight();
            if (home && (layer == 2 ? k.tier() == 1 : k.number() == 1)) v *= 4; // the plainest is the one you meet first
            w[i] = v;
        }
        return w;
    }

    private static int pick(double[] w, long h) {
        double sum = 0;
        for (double v : w) sum += v;
        if (sum <= 0) return -1;
        double roll = (h >>> 11) / (double) (1L << 53) * sum;
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0 && w[i] > 0) return i;
        }
        return -1;
    }

    /** What a cell would hold before it looks at its neighbours or the ground: a kind's index on its layer, or -1. */
    private static int basePick(Ask a, int layer, int cx, int cz) {
        return PICKS.computeIfAbsent(cellKey(layer, cx, cz), key -> {
            long h = cellHash(a.seed, layer, cx, cz);
            int x = cellX(h, layer, cx), z = cellZ(h, layer, cz);
            double chance = BASE[layer] * richness(a.seed, x, z, layer, a.spawnX, a.spawnZ);
            if (((h >>> 44) & 0xFFFF) / 65536.0 >= chance) return -1;
            return pick(weights(a, layer, x, z), mix(h, 0x11));
        });
    }

    private static Site make(Ask a, int layer, int cx, int cz) {
        int base = basePick(a, layer, cx, cz);
        if (base < 0) return NONE;
        long h = cellHash(a.seed, layer, cx, cz);
        int x = cellX(h, layer, cx), z = cellZ(h, layer, cz);
        long sx = x - a.spawnX, sz = z - a.spawnZ;
        if (sx * sx + sz * sz < 40 * 40) return NONE; // never on the spawn point itself
        if (CityHooks.inCity(a.level, new BlockPos(x, a.level.getSeaLevel(), z))) return NONE;
        // kinds a nearer cell with first say would pick: not here
        // and if this cell has to choose again, it takes nothing any cell round it would pick, whoever has first say:
        // the neighbour that keeps its first choice mustn't find it here too
        java.util.BitSet taken = new java.util.BitSet(), around = new java.util.BitSet();
        // and no family twice in cells that touch (SPEC §62.2): two cells on is something that is not a dish at all
        java.util.EnumSet<Family> famTaken = java.util.EnumSet.noneOf(Family.class), famAround = java.util.EnumSet.noneOf(Family.class);
        List<Kind> kinds = Kinds.layer(layer);
        int rings = RINGS[layer];
        for (int i = -rings; i <= rings; i++)
            for (int j = -rings; j <= rings; j++) {
                if (i == 0 && j == 0) continue;
                int other = basePick(a, layer, cx + i, cz + j);
                if (other < 0) continue;
                around.set(other);
                boolean first = Long.compareUnsigned(cellHash(a.seed, layer, cx + i, cz + j), h) < 0;
                if (first) taken.set(other);
                Family f = kinds.get(other).family();
                if (f != null && Math.abs(i) <= 1 && Math.abs(j) <= 1) {
                    famAround.add(f);
                    if (first) famTaken.add(f);
                }
            }
        double[] w = weights(a, layer, x, z);
        int choice = taken.get(base) || (kinds.get(base).family() != null && famTaken.contains(kinds.get(base).family())) ? -1 : base;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (choice < 0) {
                for (int i = around.nextSetBit(0); i >= 0; i = around.nextSetBit(i + 1)) if (i < w.length) w[i] = 0;
                for (int i = 0; i < w.length; i++) if (kinds.get(i).family() != null && famAround.contains(kinds.get(i).family())) w[i] = 0;
                choice = pick(w, mix(h, 0x23 + attempt));
                if (choice < 0) return NONE;
            }
            Kind k = kinds.get(choice);
            int[] at = Anchors.find(k, a.terrain, x, z, mix(h, 0x37));
            // a place keeps clear of villages and the city by its own width and more (the first tours had the quarry
            // against Carillon's wall and the temple in a village's garden)
            boolean place = k.size() == Kind.Size.PLACE;
            if (at != null && place && !k.fits(landAt(a, at[0], at[2]))) at = null;   // the ground it found is in another country
            if (at == null) LOST[0]++;
            else if (a.terrain.structureNear(at[0], at[2], place ? 2 : 1)) LOST[1]++;
            else if (place && a.terrain.settlementNear(at[0], at[2], (k.reach() + 72) >> 4)) LOST[2]++;
            else if (place && cityNear(a, at[0], at[2], k.reach() + 56)) LOST[3]++;
            if (at != null && !a.terrain.structureNear(at[0], at[2], place ? 2 : 1)
                    && !(place && (a.terrain.settlementNear(at[0], at[2], (k.reach() + 72) >> 4) || cityNear(a, at[0], at[2], k.reach() + 56)))) {
                RegionMap.Region r = a.map.regionAt(at[0], at[2]);
                if (k.set() == Kind.FSet.REMAINS) {
                    // a remain is the edge of something big: it stands only where a monument does, and faces it
                    Site to = nearestMonument(a, at[0], at[2], REMAINS_REACH, true);
                    if (to == null) return NONE;
                    double bestDot = -1e18;
                    for (int d = 0; d < 4; d++) {
                        double dot = (to.x() - at[0]) * (double) RX[d] + (to.z() - at[2]) * (double) RZ[d];
                        if (dot > bestDot) {
                            bestDot = dot;
                            at[3] = d;
                        }
                    }
                }
                return new Site(k, layer, at[0], at[1], at[2], mix(h, 0x41), at[3], place ? landAt(a, at[0], at[2]) : a.map.landOf(r).group(),
                        Math.min(5, RegionMap.rankOf(a.map.levelAt(at[0], at[2]))));
            }
            taken.set(choice); // the ground doesn't suit it: deal again
            choice = -1;
        }
        return NONE;
    }

    /** Why deals failed since the last census: no ground, a structure, a settlement, the city. A check's counters, nothing reads them in play. */
    private static final int[] LOST = new int[4];

    /** The country at a spot, from the biome there (the same naming the regions use for their hearts). */
    private static RegionMap.Group landAt(Ask a, int x, int z) {
        var b = a.level.getUncachedNoiseBiome(x >> 2, 63 >> 2, z >> 2);
        var arch = dev.awaken.terrain.api.BiomeTable.archetype(b);
        String path = arch != null ? arch.identifier().getPath() : b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        return dev.awaken.living.region.RegionService.land(path).group();
    }

    private static boolean cityNear(Ask a, int x, int z, int r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y = a.level.getSeaLevel();
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI / 8, rr = i % 2 == 0 ? r : r * 0.5;
            if (CityHooks.inCity(a.level, m.set(x + (int) (Math.cos(ang) * rr), y, z + (int) (Math.sin(ang) * rr)))) return true;
        }
        return false;
    }

    private static @Nullable Site cell(Ask a, int layer, int cx, int cz) {
        // not computeIfAbsent: a remain's cell asks the monuments' cells while it is being made, and a map must not be
        // changed from inside its own computation. Two threads may make the same cell; they make the same site.
        long key = cellKey(layer, cx, cz);
        Site s = SITES.get(key);
        if (s == null) {
            s = make(a, layer, cx, cz);
            Site old = SITES.putIfAbsent(key, s);
            if (old != null) s = old;
        }
        return s == NONE ? null : s;
    }

    /** South, west, north, east: where a kind's own south points after 0..3 quarter turns. */
    private static final int[] RX = {0, -1, 0, 1}, RZ = {1, 0, -1, 0};
    /** How far a remain may stand from the monument it belongs to. */
    public static final int REMAINS_REACH = 1000;

    /**
     * The nearest monument that stands within {@code max} blocks of a point, or null. Cells are asked ring by ring from
     * the point outward and the search stops as soon as nothing further out could be nearer, so it asks for the ground
     * (the slow part) of only the few cells round the answer; cells that chose nothing are never asked at all.
     * {@code remain}: for a remain's own placing, which may not stand inside a monument's ground; otherwise the monument
     * you are standing in is passed over.
     */
    private static @Nullable Site nearestMonument(Ask a, int x, int z, int max, boolean remain) {
        int c = CELL[2], cx0 = Math.floorDiv(x, c), cz0 = Math.floorDiv(z, c), rings = Math.min(7, max / c + 1);
        Site best = null;
        double bestD = max;
        for (int ring = 0; ring <= rings; ring++) {
            for (int i = -ring; i <= ring; i++)
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring || basePick(a, 2, cx0 + i, cz0 + j) < 0) continue;
                    Site s = cell(a, 2, cx0 + i, cz0 + j);
                    if (s == null) continue;
                    double d = Math.hypot(s.x() - x, s.z() - z);
                    if (d <= s.kind().reach() + 30) {
                        if (remain) return null;                    // inside a monument's own ground: nothing else stands there
                        continue;                                   // the one you are standing in is not news
                    }
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
            if (best != null && bestD < (ring - 0.2) * (double) c) break;   // nothing in a further ring can be nearer
        }
        return best;
    }

    /** The nearest monument that stands within {@code max} blocks, for a remain's carving and the Listening Stone. */
    public static @Nullable Site monumentFrom(ServerLevel level, int x, int z, int max) {
        Ask a = ask(level);
        return a == null ? null : nearestMonument(a, x, z, max, false);
    }

    // ---- asking -----------------------------------------------------------------------------------------------------

    /** Every site whose box (with {@code margin}) touches the rectangle. */
    public static List<Site> near(ServerLevel level, int x0, int z0, int x1, int z1, int margin) {
        Ask a = ask(level);
        if (a == null) return List.of();
        List<Site> out = new ArrayList<>(2);
        for (int layer = 0; layer < CELL.length; layer++) {
            if (BASE[layer] <= 0) continue;
            int c = CELL[layer], pad = MAX_REACH + margin + 24; // an anchor may move a site a little out of its cell
            for (int cx = Math.floorDiv(x0 - pad, c); cx <= Math.floorDiv(x1 + pad, c); cx++)
                for (int cz = Math.floorDiv(z0 - pad, c); cz <= Math.floorDiv(z1 + pad, c); cz++) {
                    Site s = cell(a, layer, cx, cz);
                    if (s == null) continue;
                    int r = s.kind().reach() + margin;
                    if (s.x() + r < x0 || s.x() - r > x1 || s.z() + r < z0 || s.z() - r > z1) continue;
                    out.add(s);
                }
        }
        return out;
    }

    /** The site whose box holds a position, or null. Where two overlap, the one whose heart is nearer. */
    public static @Nullable Site at(ServerLevel level, BlockPos pos) {
        Site best = null;
        long bestD = Long.MAX_VALUE;
        for (Site s : near(level, pos.getX(), pos.getZ(), pos.getX(), pos.getZ(), 0)) {
            if (!s.holds(pos, 0)) continue;
            long dx = pos.getX() - s.x(), dz = pos.getZ() - s.z(), d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** The site whose heart block is exactly here, or null. */
    public static @Nullable Site ofHeart(ServerLevel level, BlockPos heart) {
        for (Site s : near(level, heart.getX(), heart.getZ(), heart.getX(), heart.getZ(), 0)) if (s.heart().equals(heart)) return s;
        return null;
    }

    /** What a site builds. Cached; the same from any chunk and from the server. */
    public static Scene scene(ServerLevel level, Site s) {
        Scene sc = SCENES.get(s.key());
        if (sc != null) return sc;
        sc = build(s, s.layer() == HAND ? Terrain.flat(s.y()) : Terrain.of(level));
        SCENES.put(s.key(), sc);
        return sc;
    }

    static Scene build(Site s, Terrain t) {
        Scene sc = new Scene(s, t);
        s.kind().maker().build(sc);
        return sc;
    }

    /**
     * The nearest find (layer 1) that passes {@code want} and lies further from spawn than (x, z), for a lead. Looks at
     * what cells would hold before the ground is asked, nearest first, and only then checks the few best for real.
     */
    public static @Nullable Site lead(ServerLevel level, int x, int z, int spawnX, int spawnZ, java.util.function.Predicate<Kind> want) {
        Ask a = ask(level);
        if (a == null) return null;
        int c = CELL[2], cx0 = Math.floorDiv(x, c), cz0 = Math.floorDiv(z, c);
        long here = (long) (x - spawnX) * (x - spawnX) + (long) (z - spawnZ) * (z - spawnZ);
        List<Kind> kinds = Kinds.layer(2);
        int checked = 0;
        for (int ring = 1; ring <= 8 && checked < 6; ring++)
            for (int i = -ring; i <= ring && checked < 6; i++)
                for (int j = -ring; j <= ring && checked < 6; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
                    int cx = cx0 + i, cz = cz0 + j;
                    long mx = (long) cx * c + c / 2 - spawnX, mz = (long) cz * c + c / 2 - spawnZ;
                    if (mx * mx + mz * mz <= here) continue;
                    int base = basePick(a, 2, cx, cz);
                    if (base < 0 || !want.test(kinds.get(base))) continue;
                    checked++;
                    Site s = cell(a, 2, cx, cz);
                    if (s != null && want.test(s.kind())) return s;
                }
        return null;
    }

    /**
     * The nearest place of one kind to a point, looking out {@code rings} cells. Only cells that would hold that kind
     * are asked for their ground, so it is quick: the ground is the slow part.
     */
    public static @Nullable Site nearestOf(ServerLevel level, Kind kind, int x, int z, int rings) {
        Ask a = ask(level);
        if (a == null) return null;
        int c = CELL[2], cx0 = Math.floorDiv(x, c), cz0 = Math.floorDiv(z, c), want = Kinds.layer(2).indexOf(kind);
        if (want < 0) return null;
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= rings; ring++) {
            for (int i = -ring; i <= ring; i++)
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring || basePick(a, 2, cx0 + i, cz0 + j) != want) continue;
                    Site s = cell(a, 2, cx0 + i, cz0 + j);
                    if (s == null || s.kind() != kind) continue;
                    double d = Math.hypot(s.x() - x, s.z() - z);
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
            if (best != null && bestD < (ring - 1) * (double) c) break; // nothing in a further ring can be nearer
        }
        return best;
    }

    /**
     * A count of the places round the spawn, for checking that every kind can be met: for each kind, the cells that
     * first chose it and the sites that stand; and how many cells fall in each land. Slow (it asks every cell for its
     * ground): a check command's, never the game's.
     */
    public static List<String> census(ServerLevel level, int rings) {
        Ask a = ask(level);
        if (a == null) return List.of("finds are off here");
        List<Kind> kinds = Kinds.layer(2);
        int[] chose = new int[kinds.size()], stand = new int[kinds.size()], nearest = new int[kinds.size()];
        java.util.Arrays.fill(nearest, Integer.MAX_VALUE);
        int[] lands = new int[RegionMap.Group.values().length], ranks = new int[6];
        int c = CELL[2], cx0 = Math.floorDiv(a.spawnX, c), cz0 = Math.floorDiv(a.spawnZ, c), cells = 0, any = 0, none = 0;
        for (int i = -rings; i <= rings; i++)
            for (int j = -rings; j <= rings; j++) {
                cells++;
                long h = cellHash(a.seed, 2, cx0 + i, cz0 + j);
                int x = cellX(h, 2, cx0 + i), z = cellZ(h, 2, cz0 + j);
                lands[landAt(a, x, z).ordinal()]++;
                ranks[Math.min(5, RegionMap.rankOf(a.map.levelAt(x, z)))]++;
                int base = basePick(a, 2, cx0 + i, cz0 + j);
                if (base < 0) continue;
                any++;
                chose[base]++;
                Site s = cell(a, 2, cx0 + i, cz0 + j);
                if (s == null) {
                    none++;
                    continue;
                }
                int k = kinds.indexOf(s.kind());
                stand[k]++;
                nearest[k] = Math.min(nearest[k], (int) Math.hypot(s.x() - a.spawnX, s.z() - a.spawnZ));
            }
        List<String> out = new ArrayList<>();
        StringBuilder land = new StringBuilder("lands of " + cells + " cells:");
        for (RegionMap.Group g : RegionMap.Group.values()) land.append(' ').append(g).append(' ').append(lands[g.ordinal()]);
        out.add(land.toString());
        out.add("lost since the last count: no ground " + LOST[0] + ", a structure " + LOST[1] + ", a settlement " + LOST[2] + ", the city " + LOST[3]);
        java.util.Arrays.fill(LOST, 0);
        out.add("ranks: E " + ranks[0] + " D " + ranks[1] + " C " + ranks[2] + " B " + ranks[3] + " A " + ranks[4] + " S " + ranks[5] + "; cells that chose " + any + ", of those with no ground " + none);
        for (int k = 0; k < kinds.size(); k++)
            out.add(String.format("%-22s rank %d chose %3d stands %3d nearest %s", kinds.get(k).id(), kinds.get(k).rank(), chose[k], stand[k],
                    nearest[k] == Integer.MAX_VALUE ? "none" : nearest[k] + ""));
        return out;
    }

    /** A site made by hand (the gallery, tests): the kind at a place, on a level pad. Layer 2 marks it as made by hand. */
    public static Site forced(Kind k, int x, int y, int z, int rot, long seed) {
        // a place stood by hand is in the first of its own lands, so what it plants round itself is what would grow there
        return new Site(k, HAND, x, y, z, seed, rot, k.lands().isEmpty() ? RegionMap.Group.PLAINS : k.lands().iterator().next(), k.rank());
    }
}
