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

    static final int[] CELL = {96, 256}, EDGE = {18, 44}, RINGS = {4, 5};
    /** The chance a cell holds something where the land is at its usual liveliness. */
    static final double[] BASE = {0.62, 0.50};
    /** The furthest any scene reaches from its heart. */
    public static final int MAX_REACH = 48;
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
        RegionMap.Group group = a.map.landOf(r).group();
        long dx = x - a.spawnX, dz = z - a.spawnZ;
        boolean home = dx * dx + dz * dz < (long) HOME * HOME;
        for (int i = 0; i < w.length; i++) {
            Kind k = kinds.get(i);
            if (k.rank() > rank || !k.fits(group)) continue;
            double v = k.weight() * Math.pow(0.45, rank - k.rank());
            if (home && k.number() == 1) v *= 4; // the plainest of each set is the one you meet first
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
        int rings = RINGS[layer];
        for (int i = -rings; i <= rings; i++)
            for (int j = -rings; j <= rings; j++) {
                if (i == 0 && j == 0) continue;
                int other = basePick(a, layer, cx + i, cz + j);
                if (other < 0) continue;
                around.set(other);
                if (Long.compareUnsigned(cellHash(a.seed, layer, cx + i, cz + j), h) < 0) taken.set(other);
            }
        double[] w = weights(a, layer, x, z);
        List<Kind> kinds = Kinds.layer(layer);
        int choice = taken.get(base) ? -1 : base;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (choice < 0) {
                for (int i = around.nextSetBit(0); i >= 0; i = around.nextSetBit(i + 1)) if (i < w.length) w[i] = 0;
                choice = pick(w, mix(h, 0x23 + attempt));
                if (choice < 0) return NONE;
            }
            Kind k = kinds.get(choice);
            int[] at = Anchors.find(k, a.terrain, x, z, mix(h, 0x37));
            if (at != null && !a.terrain.structureNear(at[0], at[2], 1)) {
                RegionMap.Region r = a.map.regionAt(at[0], at[2]);
                return new Site(k, layer, at[0], at[1], at[2], mix(h, 0x41), at[3], a.map.landOf(r).group(),
                        Math.min(5, RegionMap.rankOf(a.map.levelAt(at[0], at[2]))));
            }
            taken.set(choice); // the ground doesn't suit it: deal again
            choice = -1;
        }
        return NONE;
    }

    private static @Nullable Site cell(Ask a, int layer, int cx, int cz) {
        Site s = SITES.computeIfAbsent(cellKey(layer, cx, cz), key -> make(a, layer, cx, cz));
        return s == NONE ? null : s;
    }

    // ---- asking -----------------------------------------------------------------------------------------------------

    /** Every site whose box (with {@code margin}) touches the rectangle. */
    public static List<Site> near(ServerLevel level, int x0, int z0, int x1, int z1, int margin) {
        Ask a = ask(level);
        if (a == null) return List.of();
        List<Site> out = new ArrayList<>(2);
        for (int layer = 0; layer < 2; layer++) {
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
        sc = build(s, s.layer() == 2 ? Terrain.flat(s.y()) : Terrain.of(level));
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
        int c = CELL[1], cx0 = Math.floorDiv(x, c), cz0 = Math.floorDiv(z, c);
        long here = (long) (x - spawnX) * (x - spawnX) + (long) (z - spawnZ) * (z - spawnZ);
        List<Kind> kinds = Kinds.layer(1);
        int checked = 0;
        for (int ring = 2; ring <= 12 && checked < 6; ring++)
            for (int i = -ring; i <= ring && checked < 6; i++)
                for (int j = -ring; j <= ring && checked < 6; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
                    int cx = cx0 + i, cz = cz0 + j;
                    long mx = (long) cx * c + c / 2 - spawnX, mz = (long) cz * c + c / 2 - spawnZ;
                    if (mx * mx + mz * mz <= here) continue;
                    int base = basePick(a, 1, cx, cz);
                    if (base < 0 || !want.test(kinds.get(base))) continue;
                    checked++;
                    Site s = cell(a, 1, cx, cz);
                    if (s != null && want.test(s.kind())) return s;
                }
        return null;
    }

    /** A site made by hand (the gallery, tests): the kind at a place, on a level pad. Layer 2 marks it as made by hand. */
    public static Site forced(Kind k, int x, int y, int z, int rot, long seed) {
        return new Site(k, 2, x, y, z, seed, rot, RegionMap.Group.PLAINS, k.rank());
    }
}
