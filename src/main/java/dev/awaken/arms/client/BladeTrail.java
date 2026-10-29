package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The blade's own trail (SPEC §25 "Trails"): a ribbon laid down by the blade itself, sampled every frame from where it
 * is drawn (its outer third: a root two thirds up the blade, its tip), so it is a crescent on the tip's path while a blow is in the air, kept in the world so a turn of the
 * head leaves it behind, and drawn as a band that is brightest along the tip's path and fades to nothing toward the
 * root and with age, its light on a darker shade of its colour so it reads by day as well as by night. Between samples it runs on a smooth curve (Catmull-Rom through the samples), and its head is
 * carried on to where the blade is this frame, so it never lags the steel. In first person the samples come from the
 * hand pass, carried out from the hands' 70° to the world's field of view; in third person from our sculpted weapons'
 * own draw (a vanilla sword seen from outside keeps the hand-arc crescent, ArmsFx). Fixed pools: nothing allocated.
 */
final class BladeTrail {
    private BladeTrail() {}

    /** Wielders traced at once, samples kept each, how long a sample lives (ns). */
    private static final int RINGS = 8, N = 48;
    private static final long LIFE = 150_000_000L;
    /** Never two samples closer than this (the same blade drawn twice in a frame: shadows, a mirror). */
    private static final long MIN_GAP = 3_000_000L;

    private static final int[] ENTITY = new int[RINGS], HEAD = new int[RINGS], COUNT = new int[RINGS], ARGB = new int[RINGS];
    /**
     * Which of the wielder's hands a ring traces (0 the main, 1 the off hand's twin), and its light (round 14): each blade
     * of a pair lays its own, so a strike passing from one hand to the other never joins them in a band across the view.
     */
    private static final int[] SIDE = new int[RINGS];
    private static final float[] LIGHT = new float[RINGS];
    /** The blow each ring traces: it samples until this (ns); 0 for none. */
    private static final long[] UNTIL = new long[RINGS];
    /** A kill's flare on the ring (SPEC §25 "Fight Feel"): when it flared (ns); it burns toward white and fades. */
    private static final long[] FLARE = new long[RINGS];
    private static final long FLARE_LIFE = 160_000_000L;
    private static final double[] ROOT = new double[RINGS * N * 3], TIP = new double[RINGS * N * 3];
    private static final long[] AT = new long[RINGS * N];

    static {
        java.util.Arrays.fill(ENTITY, Integer.MIN_VALUE);
    }

    /** A blow on {@code entity} is in the air from now for {@code ms}: its blade traces while it lasts, in {@code argb}. */
    static void open(int entity, int ms, int argb) {
        open(entity, 0, ms, argb, 1F);
    }

    /** The same for one hand's blade ({@code side} 1: the off hand's twin), its light scaled by {@code light}. */
    static void open(int entity, int side, int ms, int argb, float light) {
        int r = ring(entity, side, true);
        if (r < 0) return;
        UNTIL[r] = System.nanoTime() + ms * 1_000_000L;
        ARGB[r] = argb;
        LIGHT[r] = light;
    }

    /** A killing blow: {@code entity}'s ribbon flares once. */
    static void flare(int entity) {
        long now = System.nanoTime();
        for (int r = 0; r < RINGS; r++) if (ENTITY[r] == entity) FLARE[r] = now;
    }

    /** Whether {@code entity}'s blade is tracing now (the renderers sample only then). */
    static boolean open(int entity) {
        return open(entity, 0);
    }

    /** Whether that hand's blade of {@code entity} is tracing now. */
    static boolean open(int entity, int side) {
        int r = ring(entity, side, false);
        return r >= 0 && System.nanoTime() < UNTIL[r];
    }

    /**
     * Whether this wielder's blade lays its own ribbon, so the crescent stands aside: always for a player (the hand
     * pass in first person, the trace layer or our weapon's own draw from outside) and for our weapons in any hand.
     */
    static boolean ownRibbon(net.minecraft.world.entity.Entity wielder, boolean sculpted) {
        return sculpted || wielder instanceof net.minecraft.world.entity.player.Player;
    }

    private static final Vector3f A = new Vector3f(), B = new Vector3f();

    /**
     * Where the blade is drawn now: its root and tip in the pose's space ({@code pose} the matrix it is drawn by;
     * {@code hand}: the hands' pass of first person). Called by the renderers each frame while {@link #open(int)}.
     */
    static void sample(int entity, Matrix4f pose, float rx, float ry, float rz, float tx, float ty, float tz, boolean hand) {
        sample(entity, 0, pose, rx, ry, rz, tx, ty, tz, hand);
    }

    /** The same for one hand's blade. */
    static void sample(int entity, int side, Matrix4f pose, float rx, float ry, float rz, float tx, float ty, float tz, boolean hand) {
        int r = ring(entity, side, false);
        if (r < 0) return;
        long now = System.nanoTime();
        if (now >= UNTIL[r]) return;
        int last = (HEAD[r] - 1 + N) % N;
        if (COUNT[r] > 0 && now - AT[r * N + last] < MIN_GAP) return;
        // a blow after a pause is a new ribbon: joined to the last one's end it drew a stray wedge across the view
        if (COUNT[r] > 0 && now - AT[r * N + last] > LIFE) COUNT[r] = 0;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        pose.transformPosition(rx, ry, rz, A);
        pose.transformPosition(tx, ty, tz, B);
        if (hand) {
            WeaponMotes.toWorld(camera, A);
            WeaponMotes.toWorld(camera, B);
        }
        Vec3 c = camera.position();
        int i = r * N + HEAD[r];
        ROOT[i * 3] = c.x + A.x;
        ROOT[i * 3 + 1] = c.y + A.y;
        ROOT[i * 3 + 2] = c.z + A.z;
        TIP[i * 3] = c.x + B.x;
        TIP[i * 3 + 1] = c.y + B.y;
        TIP[i * 3 + 2] = c.z + B.z;
        AT[i] = now;
        lastSampleAt = now;
        if (debug > 0) {
            debug--;
            dev.awaken.Awaken.LOG.info("[arms ribbon] e={} hand={} root=({}, {}, {}) tip=({}, {}, {}) from the eye; pose t=({}, {}, {})", entity, hand,
                    fmt(A.x), fmt(A.y), fmt(A.z), fmt(B.x), fmt(B.y), fmt(B.z), fmt(pose.m30()), fmt(pose.m31()), fmt(pose.m32()));
        }
        HEAD[r] = (HEAD[r] + 1) % N;
        COUNT[r] = Math.min(N, COUNT[r] + 1);
    }

    /** Autopilot only: each ring's samples and ages now, on the clock ribbons age by. */
    static String state() {
        StringBuilder b = new StringBuilder("frozen=" + frozen);
        long now = clock(), real = System.nanoTime();
        b.append(" clockLag=").append((real - now) / 1_000_000).append("ms");
        for (int r = 0; r < RINGS; r++) {
            if (ENTITY[r] == Integer.MIN_VALUE) continue;
            int n = COUNT[r];
            long newest = n > 0 ? AT[r * N + (HEAD[r] - 1 + N) % N] : 0, oldest = n > 0 ? AT[r * N + (HEAD[r] - n + N) % N] : 0;
            b.append(" | e=").append(ENTITY[r]).append(" side=").append(SIDE[r]).append(" n=").append(n).append(" newestAge=").append((now - newest) / 1_000_000)
                    .append("ms oldestAge=").append((now - oldest) / 1_000_000).append("ms until=").append((UNTIL[r] - real) / 1_000_000).append("ms");
        }
        return b.toString();
    }

    /** Autopilot only: 0 as built, 1 without the fades of age, 2 loud (solid red geometry). */
    static int mode;

    /** Autopilot only: log this many samples. */
    static int debug;

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static int ring(int entity, int side, boolean claim) {
        int free = -1, oldest = -1;
        long oldestAt = Long.MAX_VALUE;
        for (int r = 0; r < RINGS; r++) {
            if (ENTITY[r] == entity && SIDE[r] == side) return r;
            if (ENTITY[r] == Integer.MIN_VALUE && free < 0) free = r;
            if (UNTIL[r] < oldestAt) {
                oldestAt = UNTIL[r];
                oldest = r;
            }
        }
        if (!claim) return -1;
        int r = free >= 0 ? free : oldest;
        ENTITY[r] = entity;
        SIDE[r] = side;
        COUNT[r] = HEAD[r] = 0;
        return r;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    /** Subdivisions of each gap between samples (the curve), and where the band's strips run across it (root to tip). */
    private static final int SUB = 4;
    private static final float[] ACROSS = {0F, 0.45F, 0.8F, 0.93F, 1F};
    private static final double[] PR = new double[3], PT = new double[3];
    private static final float[] RX = new float[N * SUB + 2], RY = new float[N * SUB + 2], RZ = new float[N * SUB + 2],
            TX = new float[N * SUB + 2], TY = new float[N * SUB + 2], TZ = new float[N * SUB + 2], AGE = new float[N * SUB + 2];
    private static double camX, camY, camZ;
    /** Autopilot only: ribbons stop ageing once their blade stops laying them, so a still frame shows the whole ribbon. */
    static boolean frozen;
    private static long lastSampleAt;

    /** The clock ribbons age by: real time, or (frozen) held just after the last sample. */
    private static long clock() {
        long now = System.nanoTime();
        return frozen ? Math.min(now, lastSampleAt + 15_000_000L) : now;
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        boolean any = false;
        long now = clock();
        for (int r = 0; r < RINGS; r++) {
            if (COUNT[r] < 2) continue;
            if (now - AT[r * N + (HEAD[r] - 1 + N) % N] > LIFE) {
                COUNT[r] = 0;
                if (now > UNTIL[r]) ENTITY[r] = Integer.MIN_VALUE;
                continue;
            }
            any = true;
        }
        if (!any || !WeaponPipelines.ok()) return;
        Vec3 c = e.getLevelRenderState().cameraRenderState.pos;
        camX = c.x;
        camY = c.y;
        camZ = c.z;
        PoseStack ps = e.getPoseStack();
        e.getSubmitNodeCollector().submitCustomGeometry(ps, WeaponPipelines.shade(), BladeTrail::drawShade);
        e.getSubmitNodeCollector().order(1).submitCustomGeometry(ps, WeaponPipelines.glow(), BladeTrail::drawAll);
    }

    /** Whether this pass is the shade (darker, alpha-blended) under the light. */
    private static boolean shading;

    private static void drawShade(PoseStack.Pose pose, VertexConsumer buffer) {
        shading = true;
        try {
            drawAll(pose, buffer);
        } finally {
            shading = false;
        }
    }

    private static void drawAll(PoseStack.Pose pose, VertexConsumer buffer) {
        long t = clock();
        for (int r = 0; r < RINGS; r++) if (COUNT[r] >= 2) draw(buffer, r, t, camX, camY, camZ);
    }

    /** One ring: the band through its live samples, oldest to newest, then on to the blade's place now. */
    private static void draw(VertexConsumer buffer, int r, long now, double cx, double cy, double cz) {
        int n = COUNT[r], base = r * N, start = (HEAD[r] - n + N) % N;
        int m = 0;
        for (int k = 0; k < n - 1; k++) {
            int i0 = base + (start + Math.max(0, k - 1)) % N, i1 = base + (start + k) % N, i2 = base + (start + k + 1) % N,
                    i3 = base + (start + Math.min(n - 1, k + 2)) % N;
            if (now - AT[i2] > LIFE) continue;
            for (int s = 0; s < SUB; s++) {
                float u = s / (float) SUB;
                spline(ROOT, i0, i1, i2, i3, u, PR);
                spline(TIP, i0, i1, i2, i3, u, PT);
                put(m++, cx, cy, cz, (now - (AT[i1] + (long) ((AT[i2] - AT[i1]) * u))) / (float) LIFE);
            }
        }
        int newest = base + (start + n - 1) % N;
        System.arraycopy(ROOT, newest * 3, PR, 0, 3);
        System.arraycopy(TIP, newest * 3, PT, 0, 3);
        put(m++, cx, cy, cz, (now - AT[newest]) / (float) LIFE);
        // the head carried on along the blade's last motion to this frame (a frame behind otherwise: a gap at speed)
        int prev = base + (start + n - 2) % N;
        long gap = AT[newest] - AT[prev];
        if (gap > 0 && now < UNTIL[r] + 20_000_000L) {
            float k = Math.min(1.5F, (now - AT[newest]) / (float) gap);
            for (int j = 0; j < 3; j++) {
                PR[j] = ROOT[newest * 3 + j] + (ROOT[newest * 3 + j] - ROOT[prev * 3 + j]) * k;
                PT[j] = TIP[newest * 3 + j] + (TIP[newest * 3 + j] - TIP[prev * 3 + j]) * k;
            }
            put(m++, cx, cy, cz, 0F);
        }
        int argb = ARGB[r];
        int cr = argb >> 16 & 255, cg = argb >> 8 & 255, cb = argb & 255;
        // strong enough to read on a noon sky: the colour's own alpha (the rhythm's brightness) lifted, never faint
        float alpha = Math.min(0.95F, 0.55F + 0.5F * ((argb >>> 24) / 255F)) * LIGHT[r];
        long since = System.nanoTime() - FLARE[r];
        if (FLARE[r] != 0 && since >= 0 && since < FLARE_LIFE) {
            float f = 1F - since / (float) FLARE_LIFE;
            cr += Math.round((255 - cr) * 0.6F * f);
            cg += Math.round((255 - cg) * 0.6F * f);
            cb += Math.round((255 - cb) * 0.6F * f);
            alpha = Math.min(1F, alpha + 0.25F * f);
        }
        for (int a = 0; a + 1 < m; a++) band(buffer, a, a + 1, cr, cg, cb, alpha);
    }

    private static void put(int m, double cx, double cy, double cz, float age) {
        RX[m] = (float) (PR[0] - cx);
        RY[m] = (float) (PR[1] - cy);
        RZ[m] = (float) (PR[2] - cz);
        TX[m] = (float) (PT[0] - cx);
        TY[m] = (float) (PT[1] - cy);
        TZ[m] = (float) (PT[2] - cz);
        AGE[m] = Math.max(0F, Math.min(1F, age));
    }

    /**
     * The band between two cross-lines, in strips from the root (nothing) to the tip (the brightest line, whitened at
     * the edge itself), each corner faded by its age.
     */
    private static void band(VertexConsumer buffer, int a, int b, int cr, int cg, int cb, float alpha) {
        float fa = fade(AGE[a]) * alpha, fb = fade(AGE[b]) * alpha;
        if (mode == 1) {
            fa = alpha;
            fb = alpha;
        }
        if (fa <= 0.002F && fb <= 0.002F && mode == 0) return;
        for (int s = 0; s + 1 < ACROSS.length; s++) {
            float v0 = ACROSS[s], v1 = ACROSS[s + 1];
            float w0 = across(v0), w1 = across(v1);
            corner(buffer, a, v0, cr, cg, cb, fa * w0, v0);
            corner(buffer, a, v1, cr, cg, cb, fa * w1, v1);
            corner(buffer, b, v1, cr, cg, cb, fb * w1, v1);
            corner(buffer, a, v0, cr, cg, cb, fa * w0, v0);
            corner(buffer, b, v1, cr, cg, cb, fb * w1, v1);
            corner(buffer, b, v0, cr, cg, cb, fb * w0, v0);
        }
    }

    /** Across the band: nothing at the root, a soft rise (the inner edge never shows), full along the tip's crisp edge. */
    private static float across(float v) {
        if (v >= 0.93F) return 1F;
        float u = Math.min(1F, v / 0.93F);
        return 0.85F * u * u * (3 - 2 * u);
    }

    /** With age: full for the first fifth, then out on a curve. */
    private static float fade(float age) {
        if (age >= 1) return 0;
        float k = age < 0.2F ? 1F : 1F - (age - 0.2F) / 0.8F;
        return k * k;
    }

    private static void corner(VertexConsumer buffer, int m, float v, int cr, int cg, int cb, float a, float whiten) {
        int r, g, b;
        if (mode == 2) {
            // autopilot's loud mode: the geometry alone, solid red
            buffer.addVertex(RX[m] + (TX[m] - RX[m]) * v, RY[m] + (TY[m] - RY[m]) * v, RZ[m] + (TZ[m] - RZ[m]) * v).setColor(255, 0, 0, 255);
            return;
        }
        if (shading) {
            // the shade: the colour taken well down, so the light above it has something to stand on by day
            r = cr * 2 / 5;
            g = cg * 2 / 5;
            b = cb * 2 / 5;
            a *= 0.6F;
        } else {
            float w = whiten >= 0.93F ? 0.65F : 0F; // the tip's edge strip burns towards white
            r = Math.round(cr + (255 - cr) * w);
            g = Math.round(cg + (255 - cg) * w);
            b = Math.round(cb + (255 - cb) * w);
        }
        buffer.addVertex(RX[m] + (TX[m] - RX[m]) * v, RY[m] + (TY[m] - RY[m]) * v, RZ[m] + (TZ[m] - RZ[m]) * v)
                .setColor(r, g, b, Math.max(0, Math.min(255, Math.round(a * 255))));
    }

    /** Catmull-Rom between samples i1 and i2 (i0 and i3 their neighbours) at u, into out. */
    private static void spline(double[] p, int i0, int i1, int i2, int i3, float u, double[] out) {
        float u2 = u * u, u3 = u2 * u;
        for (int j = 0; j < 3; j++) {
            double a = p[i0 * 3 + j], b = p[i1 * 3 + j], c = p[i2 * 3 + j], d = p[i3 * 3 + j];
            out[j] = 0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
        }
    }

    static void reset() {
        java.util.Arrays.fill(ENTITY, Integer.MIN_VALUE);
        java.util.Arrays.fill(COUNT, 0);
        java.util.Arrays.fill(UNTIL, 0);
    }
}
