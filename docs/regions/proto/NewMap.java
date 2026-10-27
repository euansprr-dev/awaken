package dev.awaken.living.region;

/** Prototype of the proposed level field ("a ceiling by distance, a temper by region, soft borders"). Pure, no allocation per lookup but the 2 nearest. */
public final class NewMap {
    public static final int PITCH = 900;
    static final double JITTER = 0.35, WARP_AMPLITUDE = 220, WARP_WAVELENGTH = 700;
    final long seed; final int cx, cz;
    // tunables
    public double reachScale = 1.0;           // stretches every distance in the ceiling curve
    public double floorMin = 0.08;            // calmest a far land may be, as a share of the ceiling
    public double borderHalf = 300; public double marchTau = 240; public double quietShare = 0.125;           // soft border: half-width of the blend (distance-difference units)
    public double moodShare = 0.4;            // share of temper from the smooth mood field (vs the region's own deal)
    public double heartRise = 0.2;            // a region is this much worse at its heart than at its edge
    public double tiltStep = 0.15;            // harsh/gentle biome tilt on the temper
    public double calmStart = 700, calmEnd = 1300; public double contrast = 2.2; // the apron: no calm pockets inside calmStart; full variety by calmEnd
    static final double[][] REACH = {{240, 1}, {700, 8}, {1000, 12}, {1500, 20}, {2000, 30}, {3000, 52}, {4000, 74}, {5500, 105}, {8000, 150}, {20000, 150}};

    public NewMap(long seed, int cx, int cz) { this.seed = seed; this.cx = cx; this.cz = cz; }

    double reach(double d) {
        d /= reachScale;
        if (d <= REACH[0][0]) return 1;
        for (int i = 1; i < REACH.length; i++) if (d <= REACH[i][0]) {
            double t = (d - REACH[i-1][0]) / (REACH[i][0] - REACH[i-1][0]);
            return REACH[i-1][1] + t * (REACH[i][1] - REACH[i-1][1]);
        }
        return 150;
    }

    double fmin(double d) {
        double t = clamp((d - calmStart) / (calmEnd - calmStart), 0, 1);
        t = t * t * (3 - 2 * t);
        return 1 + (floorMin - 1) * t;
    }

    public int levelAt(int x, int z) {
        double wx = x + warp(x, z, 0x9E3779B97F4A7C15L), wz = z + warp(x, z, 0xC2B2AE3D27D4EB4FL);
        double d = Math.hypot(x - cx, z - cz);
        double ceil = reach(d);
        if (d <= 700) return (int) Math.round(ceil);       // the Commons: the ramp itself
        // every heart near the point votes on the temper, the nearer the louder: continuous everywhere, so a border is a slope
        int gx = (int) Math.floor(wx / PITCH), gz = (int) Math.floor(wz / PITCH);
        double dmin = Double.MAX_VALUE; double[] dist = new double[25]; double[] tq = new double[25]; int n = 0;
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
            int ccx = gx + i, ccz = gz + j; int hx = heartX(ccx, ccz), hz = heartZ(ccx, ccz);
            if (Math.hypot(hx - cx, hz - cz) <= 700) continue;
            double dd = Math.hypot(hx - wx, hz - wz); dist[n] = dd; tq[n] = temperCached(ccx, ccz); n++; dmin = Math.min(dmin, dd);
        }
        double sw = 0, sq = 0;
        for (int k = 0; k < n; k++) { double w = Math.exp(-(dist[k] - dmin) / marchTau); sw += w; sq += w * tq[k]; }
        double q = sq / sw; double d1 = dmin;
        // rising to the heart (a region is worse deeper in)
        double rise = 1 - heartRise * clamp(d1 / (PITCH * 0.62), 0, 1);
        q = clamp(q * (1 - heartRise * 0.0) , 0, 1) * rise / 1.0;
        q = q / (1 - heartRise * 0.5) ; // renormalise so the average region reaches its temper
        q = clamp(q, 0, 1);
        double fl = ceil * fmin(d);
        return (int) Math.max(1, Math.round(fl + (ceil - fl) * q));
    }

    final java.util.HashMap<Long, Double> tcache = new java.util.HashMap<>();
    double temperCached(int gx, int gz) { long k = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL); Double v = tcache.get(k); if (v == null) { v = temper(gx, gz); tcache.put(k, v); } return v; }

    /** A cell's temper in [0,1]: its stratified own deal and the smooth mood of the land, tilted by its biome. */
    double temper(int gx, int gz) {
        int bx = Math.floorDiv(gx, 3), bz = Math.floorDiv(gz, 3);
        int[] perm = new int[9]; for (int i = 0; i < 9; i++) perm[i] = i;
        long h = hash(bx, bz, 79);
        for (int i = 8; i > 0; i--) { h = mix(h + i); int j = (int) Math.floorMod(h, (long) (i + 1)); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
        int slot = Math.floorMod(gx, 3) * 3 + Math.floorMod(gz, 3);
        double own = (perm[slot] + 0.5) / 9.0;
        double mood = 0.5 + 0.5 * (0.7 * valueNoise(heartX(gx, gz) / 2400.0, heartZ(gx, gz) / 2400.0, 0xA5A5L) + 0.3 * valueNoise(heartX(gx, gz) / 1100.0, heartZ(gx, gz) / 1100.0, 0x5A5AL));
        double q = (1 - moodShare) * own + moodShare * clamp(mood, 0, 1);
        q = 0.5 + (q - 0.5) * contrast;
        // one region in eight beyond 2 km is a quiet vale: calm however far out
        if (quietShare > 0 && Math.hypot(heartX(gx, gz) - cx, heartZ(gx, gz) - cz) > 2000 && unit(hash(gx, gz, 131)) < quietShare) return 0;
        double r = unit(hash(gx, gz, 41));
        int tilt = tiltOf(gx, gz);
        if (tilt != 0 && r < 0.4) q += tilt * tiltStep;
        return clamp(q, 0, 1);
    }

    /** Matches the old model's biome tilt for the comparison: harsh 10% of cells, gentle 25%. */
    int tiltOf(int gx, int gz) { double u = unit(hash(gx, gz, 99)); return u < 0.10 ? 1 : u < 0.35 ? -1 : 0; }

    int heartX(int gx, int gz) { return (int) Math.round((gx + 0.5 + JITTER * 2 * (unit(hash(gx, gz, 11)) - 0.5)) * PITCH); }
    int heartZ(int gx, int gz) { return (int) Math.round((gz + 0.5 + JITTER * 2 * (unit(hash(gx, gz, 23)) - 0.5)) * PITCH); }

    double warp(int x, int z, long salt) {
        double f = 1.0 / WARP_WAVELENGTH;
        return WARP_AMPLITUDE * (0.7 * valueNoise(x * f, z * f, salt) + 0.3 * valueNoise(x * f * 2.3, z * f * 2.3, salt ^ 0x5DEECE66DL));
    }
    double valueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = signed(hash(x0, z0, salt)), b = signed(hash(x0 + 1, z0, salt)), c = signed(hash(x0, z0 + 1, salt)), d = signed(hash(x0 + 1, z0 + 1, salt));
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }
    long hash(long a, long b, long salt) { return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L)); }
    static long mix(long z) { z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; return z ^ (z >>> 31); }
    static double unit(long h) { return (h >>> 11) * 0x1.0p-53; }
    static double signed(long h) { return unit(h) * 2 - 1; }
    static double smooth(double t) { return t * t * (3 - 2 * t); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
}
