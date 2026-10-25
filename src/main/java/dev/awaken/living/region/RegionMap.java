package dev.awaken.living.region;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The overworld as a patchwork of named regions (SPEC §21.2). Pure and deterministic from the seed and the Commons'
 * centre; the only outside input is a probe that names the land at a region's heart. Region records are made once per
 * grid cell and cached; a lookup allocates nothing once its cells are cached and is safe from any thread.
 *
 * <p>Hearts sit on a jittered grid ({@link #PITCH}); a point belongs to the nearest heart after a domain warp, so
 * borders wander. Everything within {@link #COMMONS_RADIUS} of the centre is the Commons (levels 1-8). Bands are dealt
 * per 3×3 block of cells from the odds for the block's distance, so every block holds a spread; the land at a heart
 * tilts its band; a region spans {@link #SPAN} levels, rising from its edge to its heart.
 */
public final class RegionMap {
    public static final int PITCH = 900;
    static final double JITTER = 0.35;
    static final double WARP_AMPLITUDE = 220, WARP_WAVELENGTH = 700;
    public static final int COMMONS_RADIUS = 700;
    /** Hearts this close to the centre are the Commons' neighbours: never above 20. */
    static final int NEIGHBOUR_RADIUS = 1600;
    /** Where the Commons' level starts rising (Carillon's wall). */
    static final int COMMONS_INNER = 240;
    public static final int SPAN = 6;
    public static final int MAX_LEVEL = 150;

    /** Level bands (inclusive). */
    public static final int[][] BANDS = {{1, 8}, {8, 20}, {20, 45}, {45, 80}, {80, MAX_LEVEL}};
    /** Odds of each band by heart distance (SPEC §21.2), in percent. */
    static final int[] NEIGHBOUR_ODDS = {40, 60, 0, 0, 0};
    static final int[] NEAR_ODDS = {25, 45, 25, 5, 0};    // under 2,000
    static final int[] MID_ODDS = {15, 30, 30, 20, 5};    // 2,000-5,000
    static final int[] FAR_ODDS = {10, 20, 25, 25, 20};   // beyond

    /** What the land at a heart is like: its name group and how it tilts the band. */
    public record Land(Group group, int tilt) {
        public static final Land PLAIN = new Land(Group.PLAINS, 0);
    }

    public enum Group { FOREST, PLAINS, SNOW, DESERT, SWAMP, MOUNTAIN, DARK, JUNGLE, OCEAN, MUSHROOM, BADLANDS }

    /** Names the land at a block position (the region's heart). Called once per region. */
    @FunctionalInterface
    public interface Probe {
        Land at(int x, int z);
    }

    /** One region. {@code band} indexes {@link #BANDS} (-1 for the Commons). */
    public record Region(long id, int cellX, int cellZ, int heartX, int heartZ, String name, int band, int low, int high, boolean commons) {}

    private final long seed;
    private final int cx, cz;
    private final Probe probe;
    private final Region commons;
    private final ConcurrentHashMap<Long, Region> cells = new ConcurrentHashMap<>();

    public RegionMap(long seed, int centreX, int centreZ, Probe probe) {
        this.seed = seed;
        this.cx = centreX;
        this.cz = centreZ;
        this.probe = probe;
        this.commons = new Region(Long.MIN_VALUE, 0, 0, centreX, centreZ, "The Commons", -1, BANDS[0][0], BANDS[0][1], true);
    }

    public int centreX() { return cx; }
    public int centreZ() { return cz; }
    public Region commons() { return commons; }

    // ---- lookups ----------------------------------------------------------------------------------------------------

    /** The region a block column belongs to. */
    public Region regionAt(int x, int z) {
        double wx = x + warpX(x, z), wz = z + warpZ(x, z);
        return regionAtWarped(wx, wz);
    }

    private Region regionAtWarped(double wx, double wz) {
        double dcx = wx - cx, dcz = wz - cz;
        if (dcx * dcx + dcz * dcz <= (double) COMMONS_RADIUS * COMMONS_RADIUS) return commons;
        return nearest(wx, wz);
    }

    /** The region's level at a column: low at the edge, high at the heart (the Commons rises from its wall outward). */
    public int levelAt(int x, int z) {
        double wx = x + warpX(x, z), wz = z + warpZ(x, z);
        Region r = regionAtWarped(wx, wz);
        if (r.commons) {
            double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
            double t = clamp((d - COMMONS_INNER) / (COMMONS_RADIUS - COMMONS_INNER), 0, 1);
            return r.low + (int) Math.round(t * (r.high - r.low));
        }
        double dx = wx - r.heartX, dz = wz - r.heartZ;
        double t = 1 - clamp(Math.sqrt(dx * dx + dz * dz) / (PITCH * 0.62), 0, 1);
        return r.low + (int) Math.round(t * (r.high - r.low));
    }

    private double warpX(int x, int z) {
        return warp(x, z, 0x9E3779B97F4A7C15L);
    }

    private double warpZ(int x, int z) {
        return warp(x, z, 0xC2B2AE3D27D4EB4FL);
    }

    private Region nearest(double wx, double wz) {
        int gx = (int) Math.floor(wx / PITCH), gz = (int) Math.floor(wz / PITCH);
        Region best = null;
        double bestD = Double.MAX_VALUE;
        for (int reach = 1; reach <= 3 && best == null; reach++) {
            for (int i = -reach; i <= reach; i++) {
                for (int j = -reach; j <= reach; j++) {
                    if (reach > 1 && Math.abs(i) < reach && Math.abs(j) < reach) continue; // ring only
                    int hx = heartX(gx + i, gz + j), hz = heartZ(gx + i, gz + j);
                    double dcx = hx - cx, dcz = hz - cz;
                    if (dcx * dcx + dcz * dcz <= (double) COMMONS_RADIUS * COMMONS_RADIUS) continue; // swallowed by the Commons
                    double dx = hx - wx, dz = hz - wz, d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = cell(gx + i, gz + j);
                    }
                }
            }
        }
        return best != null ? best : commons;
    }

    int heartX(int gx, int gz) {
        return (int) Math.round((gx + 0.5 + JITTER * 2 * (unit(hash(gx, gz, 11)) - 0.5)) * PITCH);
    }

    int heartZ(int gx, int gz) {
        return (int) Math.round((gz + 0.5 + JITTER * 2 * (unit(hash(gx, gz, 23)) - 0.5)) * PITCH);
    }

    /** The region record of a grid cell, made once. */
    Region cell(int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        Region r = cells.get(key);
        if (r != null) return r;
        r = make(gx, gz, key);
        if (cells.size() > 8192) cells.clear(); // a far traveller's trail; records are cheap to make again
        Region prev = cells.putIfAbsent(key, r);
        return prev != null ? prev : r;
    }

    private Region make(int gx, int gz, long key) {
        int hx = heartX(gx, gz), hz = heartZ(gx, gz);
        double dist = Math.sqrt((double) (hx - cx) * (hx - cx) + (double) (hz - cz) * (hz - cz));
        Land land = probe.at(hx, hz);
        int band;
        if (dist <= NEIGHBOUR_RADIUS) {
            band = pick(NEIGHBOUR_ODDS, unit(hash(gx, gz, 37)));
        } else {
            band = dealt(gx, gz);
        }
        // the land tilts it, 40% of the time
        if (land.tilt() != 0 && unit(hash(gx, gz, 41)) < 0.4) band += land.tilt();
        int cap = dist <= NEIGHBOUR_RADIUS ? 1 : BANDS.length - 1;
        band = Math.max(0, Math.min(cap, band));
        int lo = BANDS[band][0], hi = BANDS[band][1];
        int room = Math.max(0, hi - lo - SPAN);
        int low = lo + (int) Math.floor(unit(hash(gx, gz, 53)) * (room + 1));
        int high = Math.min(hi, low + SPAN);
        String name = Names.name(land.group(), band, hash(gx, gz, 67));
        return new Region(key, gx, gz, hx, hz, name, band, low, high, false);
    }

    /** The band dealt to a cell from its 3×3 block's deck (the odds for the block's distance, shuffled by the seed). */
    int dealt(int gx, int gz) {
        int bx = Math.floorDiv(gx, 3), bz = Math.floorDiv(gz, 3);
        double mx = (bx * 3 + 1.5) * PITCH, mz = (bz * 3 + 1.5) * PITCH;
        double d = Math.sqrt((mx - cx) * (mx - cx) + (mz - cz) * (mz - cz));
        int[] odds = d < 2000 ? NEAR_ODDS : d < 5000 ? MID_ODDS : FAR_ODDS;
        int[] deck = deck(odds);
        // seeded Fisher-Yates over the 9 slots, without allocating more than the deck
        long h = hash(bx, bz, 79);
        for (int i = 8; i > 0; i--) {
            h = mix(h + i);
            int j = (int) Math.floorMod(h, (long) (i + 1));
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        int slot = Math.floorMod(gx, 3) * 3 + Math.floorMod(gz, 3);
        return deck[slot];
    }

    /** Nine cards in proportion to the odds (largest remainders), so a block holds the spread. */
    static int[] deck(int[] odds) {
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

    static int pick(int[] odds, double u) {
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
