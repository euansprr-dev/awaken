package dev.awaken.living.region;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The overworld as a patchwork of named regions under a ceiling that grows with distance (SPEC §21.10, plan
 * docs/regions/PLAN.md). Pure and deterministic from the seed and the Commons' centre; the only outside input is a probe
 * that names the land at a region's heart. Cells are made once and cached; a lookup allocates nothing and is safe from
 * any thread.
 *
 * <p>Hearts sit on a jittered grid ({@link #PITCH}); a point belongs to the nearest heart after a domain warp, so borders
 * wander. A land's level is made of three things: a <b>ceiling</b> by distance from the centre (the highest a land can be
 * there; inside {@link #COMMONS_RADIUS} it is the Commons' own ramp, 1 to 8), a <b>temper</b> per region (0 calm to 1 savage:
 * its own roll dealt per 3×3 block of cells, a smooth "mood" of the wider land, the biome's tilt) that places the land
 * between a calm floor and the ceiling, and <b>soft borders</b> (every near heart votes on the temper, the nearer the
 * louder, so a border is a slope). The numbers are in {@code data/awaken/awaken/regions/reach.json}.
 *
 * <p>{@code rules} 1 is a world made before this: its levels follow the field like any, but its regions keep naming
 * themselves by the old deal's band (SPEC §21.2 as built 2026-10-25), so every name the player discovered stays.
 */
public final class RegionMap {
    public static final int PITCH = 900;
    static final double JITTER = 0.35;
    static final double WARP_AMPLITUDE = 220, WARP_WAVELENGTH = 700;
    public static final int COMMONS_RADIUS = 700;
    public static final int MAX_LEVEL = 150;
    /** The rules new worlds are made under (SPEC §21.10). */
    public static final int RULES = 2;

    /** The lowest level of each land rank E, D, C, B, A, S (the plate's bands, {@code Regions.bandOf}). */
    public static final int[] RANK_FROM = {1, 10, 25, 45, 70, 100};

    public static int rankOf(int level) {
        int r = 0;
        for (int i = 1; i < RANK_FROM.length; i++) if (level >= RANK_FROM[i]) r = i;
        return r;
    }

    // ---- the old deal, kept for naming in worlds made before (rules 1) -----------------------------------------------------
    private static final int NEIGHBOUR_RADIUS = 1600;
    private static final int[][] LEGACY_BANDS = {{1, 8}, {8, 20}, {20, 45}, {45, 80}, {80, MAX_LEVEL}};
    private static final int[] NEIGHBOUR_ODDS = {40, 60, 0, 0, 0};
    private static final int[] NEAR_ODDS = {25, 45, 25, 5, 0};
    private static final int[] MID_ODDS = {15, 30, 30, 20, 5};
    private static final int[] FAR_ODDS = {10, 20, 25, 25, 20};

    /** What the land at a heart is like: its name group and how it tilts the temper. */
    public record Land(Group group, int tilt) {
        public static final Land PLAIN = new Land(Group.PLAINS, 0);
    }

    public enum Group { FOREST, PLAINS, SNOW, DESERT, SWAMP, MOUNTAIN, DARK, JUNGLE, OCEAN, MUSHROOM, BADLANDS }

    /** Names the land at a block position (the region's heart). Called once per region. */
    @FunctionalInterface
    public interface Probe {
        Land at(int x, int z);
    }

    /**
     * One region. {@code band} is its land rank index (0 E to 5 S: the heart's level; -1 for the Commons), {@code low} and
     * {@code high} the levels its land really spans (its edge nearest home to its heart).
     */
    public record Region(long id, int cellX, int cellZ, int heartX, int heartZ, String name, int band, int low, int high, boolean commons) {}

    /** What each cell is made of once: its land from the probe and its temper. */
    private record Cell(Land land, double temper) {}

    // ---- the numbers (data) -------------------------------------------------------------------------------------------------

    /** The tunables of the level field, read once from {@code reach.json} (built-in defaults when it is missing). */
    static final class Reach {
        static final Reach INSTANCE = load();
        final double[][] points;
        final double floorShare, floorFrom, floorTo, march, mood, contrast, heartWorse, tilt, quietShare, quietFrom;

        private Reach(double[][] points, JsonObject o) {
            this.points = points;
            floorShare = num(o, "floor", 0.08);
            floorFrom = num(o, "floorFrom", 700);
            floorTo = num(o, "floorTo", 1300);
            march = num(o, "march", 240);
            mood = num(o, "mood", 0.4);
            contrast = num(o, "contrast", 2.2);
            heartWorse = num(o, "heartWorse", 0.2);
            tilt = num(o, "tilt", 0.15);
            quietShare = num(o, "quietShare", 0.125);
            quietFrom = num(o, "quietFrom", 2000);
        }

        private static double num(JsonObject o, String k, double dflt) {
            return o.has(k) ? o.get(k).getAsDouble() : dflt;
        }

        private static Reach load() {
            double[][] dflt = {{240, 1}, {700, 8}, {1000, 12}, {1500, 20}, {2000, 30}, {3000, 52}, {4000, 74}, {5500, 105}, {8000, 150}};
            try (var in = RegionMap.class.getResourceAsStream("/data/awaken/awaken/regions/reach.json")) {
                if (in == null) return new Reach(dflt, new JsonObject());
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray a = o.getAsJsonArray("points");
                double[][] pts = new double[a.size()][2];
                for (int i = 0; i < a.size(); i++) {
                    pts[i][0] = a.get(i).getAsJsonArray().get(0).getAsDouble();
                    pts[i][1] = a.get(i).getAsJsonArray().get(1).getAsDouble();
                }
                return new Reach(pts, o);
            } catch (Exception e) {
                return new Reach(dflt, new JsonObject());
            }
        }

        /** The ceiling at a distance: straight lines between the points, flat past the last. */
        double ceiling(double d) {
            if (d <= points[0][0]) return points[0][1];
            for (int i = 1; i < points.length; i++) {
                if (d <= points[i][0]) {
                    double t = (d - points[i - 1][0]) / (points[i][0] - points[i - 1][0]);
                    return points[i - 1][1] + t * (points[i][1] - points[i - 1][1]);
                }
            }
            return points[points.length - 1][1];
        }

        /** How much of the ceiling the calmest land keeps at a distance: all of it up to the Commons' edge, fading to the floor. */
        double keep(double d) {
            double t = clamp((d - floorFrom) / (floorTo - floorFrom), 0, 1);
            t = t * t * (3 - 2 * t);
            return 1 + (floorShare - 1) * t;
        }
    }

    private final long seed;
    private final int cx, cz, rules;
    private final Probe probe;
    private final Region commons;
    private final ConcurrentHashMap<Long, Region> regions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();

    public RegionMap(long seed, int centreX, int centreZ, Probe probe) {
        this(seed, centreX, centreZ, probe, RULES);
    }

    public RegionMap(long seed, int centreX, int centreZ, Probe probe, int rules) {
        this.seed = seed;
        this.cx = centreX;
        this.cz = centreZ;
        this.probe = probe;
        this.rules = rules;
        this.commons = new Region(Long.MIN_VALUE, 0, 0, centreX, centreZ, "The Commons", -1, 1, 8, true);
    }

    public int centreX() { return cx; }
    public int centreZ() { return cz; }
    public int rules() { return rules; }
    public Region commons() { return commons; }

    // ---- lookups ----------------------------------------------------------------------------------------------------

    /** The region a block column belongs to. */
    public Region regionAt(int x, int z) {
        double wx = x + warpX(x, z), wz = z + warpZ(x, z);
        double dcx = wx - cx, dcz = wz - cz;
        if (dcx * dcx + dcz * dcz <= (double) COMMONS_RADIUS * COMMONS_RADIUS) return commons;
        return nearest(wx, wz);
    }

    /**
     * The region of grid cell (gx, gz) (its heart, name and levels), or the Commons when the Commons swallowed its heart.
     * Roads are planned between neighbouring cells (SPEC §28.9).
     */
    public Region cellRegion(int gx, int gz) {
        return swallowed(gx, gz) ? commons : region(gx, gz);
    }

    /** Whether grid cell (gx, gz)'s heart lies in the Commons (the cell has no region of its own). */
    public boolean swallowed(int gx, int gz) {
        double dcx = heartX(gx, gz) - cx, dcz = heartZ(gx, gz) - cz;
        return dcx * dcx + dcz * dcz <= (double) COMMONS_RADIUS * COMMONS_RADIUS;
    }

    /** The heart of grid cell (gx, gz), without naming it (no probe): {x, z}. */
    public int[] heart(int gx, int gz) {
        return new int[]{heartX(gx, gz), heartZ(gx, gz)};
    }

    /** A grid cell's own roll in [0, 1) for a salt (other modules' choices per region, e.g. which regions hold a town). */
    public double roll(int gx, int gz, long salt) {
        return unit(hash(gx, gz, salt));
    }

    /** The highest a land can be at a distance from the centre (the ceiling; the Commons' ramp inside its wall). */
    public int ceilingAt(double distance) {
        return (int) Math.round(Reach.INSTANCE.ceiling(distance));
    }

    /**
     * The level of the land at a column: the ceiling for its distance, brought down by its temper (SPEC §21.10). Soft
     * everywhere: no 100 blocks change by more than about 15 levels inside 5,000 of the centre.
     */
    public int levelAt(int x, int z) {
        Reach r = Reach.INSTANCE;
        double d = Math.hypot(x - cx, z - cz);
        double ceil = r.ceiling(d);
        double keep = r.keep(d);
        if (keep >= 1) return Math.max(1, (int) Math.round(ceil)); // the Commons and its apron: the ceiling itself
        double wx = x + warpX(x, z), wz = z + warpZ(x, z);
        int gx = (int) Math.floor(wx / PITCH), gz = (int) Math.floor(wz / PITCH);
        double dmin = Double.MAX_VALUE;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (swallowed(gx + i, gz + j)) continue;
                double dd = Math.hypot(heartX(gx + i, gz + j) - wx, heartZ(gx + i, gz + j) - wz);
                if (dd < dmin) dmin = dd;
            }
        }
        // every near heart votes on the temper, the nearer the louder: a border is a slope, never a step
        double sw = 0, sq = 0;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (swallowed(gx + i, gz + j)) continue;
                double dd = Math.hypot(heartX(gx + i, gz + j) - wx, heartZ(gx + i, gz + j) - wz);
                double w = Math.exp(-(dd - dmin) / r.march);
                if (w < 1e-3) continue;
                sw += w;
                sq += w * cell(gx + i, gz + j).temper;
            }
        }
        double q = sq / sw;
        // worse toward the heart than at the edge
        double rise = 1 - r.heartWorse * clamp(dmin / (PITCH * 0.62), 0, 1);
        q = clamp(q * rise / (1 - r.heartWorse * 0.5), 0, 1);
        double floor = ceil * keep;
        return Math.max(1, (int) Math.round(floor + (ceil - floor) * q));
    }

    private double warpX(int x, int z) {
        return warp(x, z, 0x9E3779B97F4A7C15L);
    }

    private double warpZ(int x, int z) {
        return warp(x, z, 0xC2B2AE3D27D4EB4FL);
    }

    private Region nearest(double wx, double wz) {
        int gx = (int) Math.floor(wx / PITCH), gz = (int) Math.floor(wz / PITCH);
        int bestI = 0, bestJ = 0;
        boolean found = false;
        double bestD = Double.MAX_VALUE;
        for (int reach = 1; reach <= 3 && !found; reach++) {
            for (int i = -reach; i <= reach; i++) {
                for (int j = -reach; j <= reach; j++) {
                    if (reach > 1 && Math.abs(i) < reach && Math.abs(j) < reach) continue; // ring only
                    if (swallowed(gx + i, gz + j)) continue; // swallowed by the Commons
                    double dx = heartX(gx + i, gz + j) - wx, dz = heartZ(gx + i, gz + j) - wz, d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        bestI = i;
                        bestJ = j;
                        found = true;
                    }
                }
            }
        }
        return found ? region(gx + bestI, gz + bestJ) : commons;
    }

    int heartX(int gx, int gz) {
        return (int) Math.round((gx + 0.5 + JITTER * 2 * (unit(hash(gx, gz, 11)) - 0.5)) * PITCH);
    }

    int heartZ(int gx, int gz) {
        return (int) Math.round((gz + 0.5 + JITTER * 2 * (unit(hash(gx, gz, 23)) - 0.5)) * PITCH);
    }

    // ---- cells and regions -----------------------------------------------------------------------------------------------

    private static long key(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    /** A cell's land and temper, made once (the probe is called once per cell, from any thread). */
    private Cell cell(int gx, int gz) {
        long key = key(gx, gz);
        Cell c = cells.get(key);
        if (c != null) return c;
        Land land = probe.at(heartX(gx, gz), heartZ(gx, gz));
        c = new Cell(land, makeTemper(gx, gz, land));
        if (cells.size() > 16384) cells.clear(); // a far traveller's trail; cells are cheap to make again
        Cell prev = cells.putIfAbsent(key, c);
        return prev != null ? prev : c;
    }

    /**
     * A cell's temper in [0, 1]: its own roll (nine strata dealt in a shuffle per 3×3 block, so a block holds the whole
     * spread), the smooth mood of the wider land, the biome's tilt 40% of the time; one cell in eight beyond the quiet
     * line is calm whatever its neighbours are.
     */
    private double makeTemper(int gx, int gz, Land land) {
        Reach r = Reach.INSTANCE;
        int hx = heartX(gx, gz), hz = heartZ(gx, gz);
        if (quiet(gx, gz)) return 0;
        int bx = Math.floorDiv(gx, 3), bz = Math.floorDiv(gz, 3);
        int slot = Math.floorMod(gx, 3) * 3 + Math.floorMod(gz, 3);
        // seeded Fisher-Yates over the nine strata, keeping only where our slot ends up
        int[] deck = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        long h = hash(bx, bz, 79);
        for (int i = 8; i > 0; i--) {
            h = mix(h + i);
            int j = (int) Math.floorMod(h, (long) (i + 1));
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        double own = (deck[slot] + 0.5) / 9.0;
        double mood = 0.5 + 0.5 * (0.7 * valueNoise(hx / 2400.0, hz / 2400.0, 0xA5A5L) + 0.3 * valueNoise(hx / 1100.0, hz / 1100.0, 0x5A5AL));
        double q = (1 - r.mood) * own + r.mood * clamp(mood, 0, 1);
        q = 0.5 + (q - 0.5) * r.contrast;
        if (land != null && land.tilt() != 0 && unit(hash(gx, gz, 41)) < 0.4) q += land.tilt() * r.tilt;
        return clamp(q, 0, 1);
    }

    /** Whether a cell is a quiet vale (SPEC §21.10): one in eight beyond 2,000 is calm however far out. */
    boolean quiet(int gx, int gz) {
        Reach r = Reach.INSTANCE;
        return r.quietShare > 0 && Math.hypot(heartX(gx, gz) - cx, heartZ(gx, gz) - cz) > r.quietFrom && unit(hash(gx, gz, 131)) < r.quietShare;
    }

    /** The region record of a grid cell, made once. */
    private Region region(int gx, int gz) {
        long key = key(gx, gz);
        Region r = regions.get(key);
        if (r != null) return r;
        r = make(gx, gz, key);
        if (regions.size() > 8192) regions.clear();
        Region prev = regions.putIfAbsent(key, r);
        return prev != null ? prev : r;
    }

    private Region make(int gx, int gz, long key) {
        int hx = heartX(gx, gz), hz = heartZ(gx, gz);
        Land land = cell(gx, gz).land;
        // its span: the heart and eight points round it half a pitch out (the edge nearest home is the lowest)
        int heart = levelAt(hx, hz), low = heart, high = heart;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int lv = levelAt(hx + (int) Math.round(Math.cos(a) * PITCH * 0.5), hz + (int) Math.round(Math.sin(a) * PITCH * 0.5));
            low = Math.min(low, lv);
            high = Math.max(high, lv);
        }
        int band = rules >= 2 ? rankOf(heart) : legacyBand(gx, gz, land, Math.hypot(hx - cx, hz - cz));
        // a quiet vale is named like one ("Willow Rest"); worlds made before keep every name they had
        String name = rules >= 2 && quiet(gx, gz) ? Names.quiet(land.group(), hash(gx, gz, 67)) : Names.name(land.group(), band, hash(gx, gz, 67));
        return new Region(key(gx, gz), gx, gz, hx, hz, name, band, low, high, false);
    }

    // ---- the old deal (names in worlds made before SPEC §21.10) ----------------------------------------------------------

    private int legacyBand(int gx, int gz, Land land, double dist) {
        int band = dist <= NEIGHBOUR_RADIUS ? pick(NEIGHBOUR_ODDS, unit(hash(gx, gz, 37))) : dealt(gx, gz);
        if (land.tilt() != 0 && unit(hash(gx, gz, 41)) < 0.4) band += land.tilt();
        int cap = dist <= NEIGHBOUR_RADIUS ? 1 : LEGACY_BANDS.length - 1;
        return Math.max(0, Math.min(cap, band));
    }

    /** The band dealt to a cell from its 3×3 block's deck. */
    private int dealt(int gx, int gz) {
        int bx = Math.floorDiv(gx, 3), bz = Math.floorDiv(gz, 3);
        double mx = (bx * 3 + 1.5) * PITCH, mz = (bz * 3 + 1.5) * PITCH;
        double d = Math.sqrt((mx - cx) * (mx - cx) + (mz - cz) * (mz - cz));
        int[] odds = d < 2000 ? NEAR_ODDS : d < 5000 ? MID_ODDS : FAR_ODDS;
        int[] deck = deck(odds);
        long h = hash(bx, bz, 79);
        for (int i = 8; i > 0; i--) {
            h = mix(h + i);
            int j = (int) Math.floorMod(h, (long) (i + 1));
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        return deck[Math.floorMod(gx, 3) * 3 + Math.floorMod(gz, 3)];
    }

    /** Nine cards in proportion to the odds (largest remainders). */
    private static int[] deck(int[] odds) {
        int[] count = new int[odds.length];
        double[] rem = new double[odds.length];
        int dealt = 0;
        for (int b = 0; b < odds.length; b++) {
            double exact = odds[b] * 9 / 100.0;
            count[b] = (int) Math.floor(exact);
            rem[b] = exact - count[b];
            dealt += count[b];
        }
        while (dealt < 9) {
            int best = 0;
            for (int b = 1; b < odds.length; b++) if (rem[b] > rem[best]) best = b;
            count[best]++;
            rem[best] = -1;
            dealt++;
        }
        int[] deck = new int[9];
        int k = 0;
        for (int b = 0; b < odds.length; b++) for (int n = 0; n < count[b]; n++) deck[k++] = b;
        return deck;
    }

    private static int pick(int[] odds, double u) {
        double acc = 0;
        for (int b = 0; b < odds.length; b++) {
            acc += odds[b] / 100.0;
            if (u < acc) return b;
        }
        return odds.length - 1;
    }

    // ---- noise and hashing ------------------------------------------------------------------------------------------

    /** A smooth offset in [-amplitude, amplitude] (value noise, two octaves). */
    private double warp(int x, int z, long salt) {
        double f = 1.0 / WARP_WAVELENGTH;
        return WARP_AMPLITUDE * (0.7 * valueNoise(x * f, z * f, salt) + 0.3 * valueNoise(x * f * 2.3, z * f * 2.3, salt ^ 0x5DEECE66DL));
    }

    private double valueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = signed(hash(x0, z0, salt)), b = signed(hash(x0 + 1, z0, salt));
        double c = signed(hash(x0, z0 + 1, salt)), d = signed(hash(x0 + 1, z0 + 1, salt));
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    long hash(long a, long b, long salt) {
        return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L));
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double signed(long h) {
        return unit(h) * 2 - 1;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
