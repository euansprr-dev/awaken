package dev.awaken.opening.client;

import net.minecraft.world.phys.Vec3;

/**
 * The sky shot's flight (SPEC §16.4), a designed path over Carillon's north-west half (tools/opening/skypath.py checks
 * the same keyframes against the painted city: clearance, the loaded circle round the Roost, the speed): above the
 * clouds at dawn looking south-east across the spire, down over the wall walk, along the river past the Quay cranes,
 * over the market's edge, up past the Choir's belfry, then back to the Runners' Roost, round your left shoulder and in
 * behind you. Keyframes are timed, not warped: a monotone cubic maps time to the spline (no stops, no overshoot), a
 * centripetal Catmull-Rom carries the eyes and a uniform one the look. Allocation-free after construction.
 */
final class SkyPath {
    /** Seconds from the first frame to the landing. */
    static final double SECONDS = 19.0;

    /** t (s); eye x, ry, z; look x, ry, z; in plan coordinates (blocks from Carillon's centre, heights from the street). */
    private static final double[][] KEYS = {
            {0.0, -268, 168, -246, 0, 50, -20},
            {3.2, -218, 140, -206, -10, 40, -30},
            {5.6, -150, 108, -182, -40, 15, -50},
            {7.6, -100, 62, -152, -72, 10, -60},
            {9.2, -86, 36, -100, -66, 18, -30},
            {10.6, -72, 38, -50, -40, 10, -8},
            {12.0, -47, 33, -13, 2, 3, 10},
            {13.4, -40, 44, -38, 0, 72, -46},
            {15.0, -80, 58, -80, -160, 46, -125},
            {16.3, -132, 52, -108, -170, 42, -130},
    };
    /** The last moves are the player's own frame: round the left shoulder (forward, left, up from the eyes). */
    private static final double[][] NEAR = {
            // t, eye forward/left/up, look forward/left/up
            {17.3, 6.5, 6.6, 4.4, 0, 0, 0},
            {18.0, -2.0, 5.2, 2.4, 16, 0, -1.6},
    };
    private static final double LAND_T = 18.8;

    private final int n;
    private final double[] t;
    private final double[][] p, k; // eyes and look targets (world), with phantom ends: n + 2
    private final double[] m;      // monotone slopes of u(t)
    final double endX, endY, endZ;
    final float endYaw, endPitch;

    /**
     * @param origin Carillon's centre at street height; eye the player's eyes on the Roost; yaw/pitch the view to land
     *               on; dist how far behind the eyes the landing sits (vanilla's third-person camera, clipped)
     */
    SkyPath(Vec3 origin, Vec3 eye, float yaw, float pitch, double dist) {
        n = KEYS.length + NEAR.length + 1;
        t = new double[n];
        p = new double[n + 2][3];
        k = new double[n + 2][3];
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr), fz = Math.cos(yr);   // forward (horizontal)
        double lx = Math.cos(yr), lz = Math.sin(yr);    // left
        double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr); // the view
        int i = 0;
        for (double[] key : KEYS) {
            t[i] = key[0];
            set(p[i + 1], origin.x + key[1], origin.y + key[2], origin.z + key[3]);
            set(k[i + 1], origin.x + key[4], origin.y + key[5], origin.z + key[6]);
            i++;
        }
        for (double[] key : NEAR) {
            t[i] = key[0];
            set(p[i + 1], eye.x + fx * key[1] + lx * key[2], eye.y + key[3], eye.z + fz * key[1] + lz * key[2]);
            set(k[i + 1], eye.x + fx * key[4] + lx * key[5], eye.y + key[6], eye.z + fz * key[4] + lz * key[5]);
            i++;
        }
        t[i] = LAND_T;
        endX = eye.x - dx * dist;
        endY = eye.y - dy * dist + (dist > 0.5 ? 0.25 : 0);
        endZ = eye.z - dz * dist;
        endYaw = yaw;
        endPitch = pitch;
        set(p[i + 1], endX, endY, endZ);
        set(k[i + 1], eye.x + dx * 200, eye.y + dy * 200, eye.z + dz * 200);
        for (int c = 0; c < 3; c++) {
            p[0][c] = 2 * p[1][c] - p[2][c];
            p[n + 1][c] = 2 * p[n][c] - p[n - 1][c];
            k[0][c] = 2 * k[1][c] - k[2][c];
            k[n + 1][c] = 2 * k[n][c] - k[n - 1][c];
        }
        // Fritsch-Carlson slopes for u(t) through (t[i], i): ease out of the start a little, settle fully at the end
        m = new double[n];
        double[] d = new double[n - 1];
        for (int j = 0; j < n - 1; j++) d[j] = 1.0 / (t[j + 1] - t[j]);
        m[0] = d[0] * 0.25;
        m[n - 1] = 0;
        for (int j = 1; j < n - 1; j++) m[j] = d[j - 1] * d[j] <= 0 ? 0 : 2.0 / (1.0 / d[j - 1] + 1.0 / d[j]);
    }

    private static void set(double[] a, double x, double y, double z) {
        a[0] = x;
        a[1] = y;
        a[2] = z;
    }

    /** The spline parameter at a time (seconds). */
    double u(double time) {
        if (time <= t[0]) return 0;
        if (time >= t[n - 1]) return n - 1;
        int i = 0;
        while (i < n - 2 && t[i + 1] <= time) i++;
        double h = t[i + 1] - t[i], s = (time - t[i]) / h;
        double s2 = s * s, s3 = s2 * s;
        return (2 * s3 - 3 * s2 + 1) * i + (s3 - 2 * s2 + s) * h * m[i] + (-2 * s3 + 3 * s2) * (i + 1) + (s3 - s2) * h * m[i + 1];
    }

    /** out[0..2] the eyes, out[3..5] the look target, at a time (seconds; clamped to the flight). */
    void sample(double time, double[] out) {
        double u = u(time);
        int seg = Math.min((int) u, n - 2);
        double lt = u - seg;
        catmull(p, seg, lt, out, 0, true);
        catmull(k, seg, lt, out, 3, false);
    }

    /** Segment seg (between keys seg and seg + 1) at local t; centripetal for the eyes, uniform for the look. */
    private static void catmull(double[][] pts, int seg, double t, double[] out, int off, boolean centripetal) {
        double[] p0 = pts[seg], p1 = pts[seg + 1], p2 = pts[seg + 2], p3 = pts[seg + 3];
        double t0 = 0, t1 = knot(t0, p0, p1, centripetal), t2 = knot(t1, p1, p2, centripetal), t3 = knot(t2, p2, p3, centripetal);
        double tt = t1 + (t2 - t1) * t;
        for (int c = 0; c < 3; c++) {
            double a1 = lerp(p0[c], p1[c], t0, t1, tt), a2 = lerp(p1[c], p2[c], t1, t2, tt), a3 = lerp(p2[c], p3[c], t2, t3, tt);
            double b1 = lerp(a1, a2, t0, t2, tt), b2 = lerp(a2, a3, t1, t3, tt);
            out[off + c] = lerp(b1, b2, t1, t2, tt);
        }
    }

    private static double knot(double ti, double[] a, double[] b, boolean centripetal) {
        if (!centripetal) return ti + 1;
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        return ti + Math.max(Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)), 1e-3);
    }

    private static double lerp(double a, double b, double ta, double tb, double t) {
        return a + (b - a) * (t - ta) / (tb - ta);
    }

    /** The first frames' eyes, for the warm-up behind the card: the path sampled at a fraction (0..1). */
    void sampleFraction(double f, double[] out) {
        sample(Math.clamp(f, 0, 1) * LAND_T, out);
    }
}
