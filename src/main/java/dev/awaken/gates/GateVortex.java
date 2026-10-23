package dev.awaken.gates;

import org.jspecify.annotations.Nullable;

/**
 * The surface gate's shape and look, as pure maths shared by the server (footprint, structure), the client renderer
 * ({@code gates.client.GateVortexRenderer}) and the offline preview (a GameTest exports frames of it for
 * {@code tools/art/specs/structures.py}). No Minecraft client classes here.
 *
 * <p>Local frame: origin at the vortex centre, x along the face, y up, z through the opening. The opening is an ellipse
 * with radii {@link Size#rx} x {@link Size#ry}; its lower edge hangs just above the scorched pad.
 *
 * <p>Every translucent layer is drawn from one texture atlas ({@code textures/entity/gate_vortex.png}, 512x512,
 * filtered) on one shared polar grid, so the game's per-quad sorting layers them the same way from either side.
 */
public final class GateVortex {
    private GateVortex() {}

    // ---- modes (synced on the anchor block entity) ----------------------------------------------------------------

    public static final int OPEN = 0, WARNING = 1, BROKEN = 2, CALM = 3, SPENT = 4, FORMING = 5;
    public static final String[] MODE_NAMES = {"open", "warning", "broken", "calm", "spent", "forming"};

    public static int modeByName(String name) {
        for (int i = 0; i < MODE_NAMES.length; i++) if (MODE_NAMES[i].equals(name)) return i;
        return -1;
    }

    // ---- size -------------------------------------------------------------------------------------------------------

    /** Radii of the opening (blocks). */
    public record Size(float rx, float ry) {
        /** Height of the vortex centre above the ground block the gate stands on. */
        public float centreY() {
            return 1.15F + ry;
        }

        /** Radius of the flat scorched pad (the rock spurs stand on it). */
        public float padRadius() {
            return rx + 3.0F;
        }

        /** Outer radius of the crater ring that follows the terrain. */
        public float ringRadius() {
            return padRadius() + 3.5F;
        }

        /** Highest block the structure uses above the ground (shards included). */
        public int top() {
            return (int) Math.ceil(centreY() + ry * 1.5F) + 1;
        }
    }

    private static final Size[] SIZES = {
            new Size(2.6F, 3.3F), new Size(3.2F, 4.0F), new Size(3.9F, 4.9F), new Size(4.6F, 5.8F),
            new Size(5.4F, 6.8F), new Size(6.5F, 8.2F), new Size(7.5F, 9.5F)};

    public static Size size(int rank) {
        return SIZES[clampRank(rank)];
    }

    public static int clampRank(int rank) {
        return Math.max(0, Math.min(6, rank));
    }

    /** Is the block (a along the face, y above the ground block) part of the opening? The two bottom rows are 3 wide. */
    public static boolean core(int rank, int a, int y) {
        Size s = size(rank);
        if (y < 1) return false;
        double dx = a / (s.rx * 0.94), dy = (y + 0.5 - s.centreY()) / (s.ry * 0.94);
        if (dx * dx + dy * dy <= 1.0) return true;
        return y <= 2 && Math.abs(a) <= 1;
    }

    /** Highest core row. */
    public static int coreTop(int rank) {
        Size s = size(rank);
        return (int) Math.floor(s.centreY() + s.ry * 0.94 - 0.5);
    }

    /** Half-width of the core rows (blocks). */
    public static int coreHalfWidth(int rank) {
        return (int) Math.floor(size(rank).rx * 0.94);
    }

    // ---- palette ----------------------------------------------------------------------------------------------------

    /** Colours of one look (ARGB, alpha 255): the dark heart, the arms, the bright filaments, the rim, the glow. */
    public record Palette(int heart, int arm, int filament, int rim, int glow, int mote, int crack) {}

    private static final Palette[] RANKS = {
            new Palette(0xFF070A14, 0xFF9CC4FF, 0xFFE6F1FF, 0xFFF4F6FB, 0xFF8FB8FF, 0xFFD6ECFF, 0xFFB8D6FF), // E: pale signal
            new Palette(0xFF04120C, 0xFF3FBF7F, 0xFFB6F5D2, 0xFFA8F7C8, 0xFF5FD08F, 0xFFC8FFE0, 0xFF5FD08F), // D: green
            new Palette(0xFF040A1A, 0xFF3C86FF, 0xFFB8DAFF, 0xFFC4E2FF, 0xFF6FB3FF, 0xFFD6ECFF, 0xFF6FB3FF), // C: blue
            new Palette(0xFF0B0518, 0xFF8A5CFF, 0xFFDCCBFF, 0xFFE4D4FF, 0xFFB48CFF, 0xFFEADFFF, 0xFFB48CFF), // B: violet
            new Palette(0xFF140904, 0xFFF08A1E, 0xFFFFD9A0, 0xFFFFE2A8, 0xFFF2A541, 0xFFFFE6B8, 0xFFF2A541), // A: amber
            new Palette(0xFF14030A, 0xFFFF2A44, 0xFFFFB0BC, 0xFFFFC6CE, 0xFFFF5A6A, 0xFFFFD0D6, 0xFFFF5A6A), // S: red
            new Palette(0xFF000000, 0xFFD4AF37, 0xFF140C02, 0xFFFFF4C8, 0xFFFFC83A, 0xFFFFF0C0, 0xFFD4AF37)};// Sovereign: gold on black
    private static final Palette BROKEN_PALETTE = new Palette(0xFF000000, 0xFFA0101E, 0xFFFF5A3A, 0xFFFF3A24, 0xFF8A0A14, 0xFFFF7A50, 0xFFFF2A1A);
    private static final int CALM_WHITE = 0xFFE4EEFF, WARN_RED = 0xFFFF3B2E, RED_GATE = 0xFFFF2A3A;

    public static Palette palette(int rank, int mode, boolean red) {
        if (mode == BROKEN) return BROKEN_PALETTE;
        Palette p = RANKS[clampRank(rank)];
        if (red) p = new Palette(p.heart, mix(p.arm, RED_GATE, 0.45F), p.filament, mix(p.rim, RED_GATE, 0.6F), mix(p.glow, RED_GATE, 0.6F), p.mote, RED_GATE);
        if (mode == CALM) {
            p = new Palette(mix(p.heart, 0xFF1A2236, 0.5F), mix(p.arm, CALM_WHITE, 0.8F), mix(p.filament, CALM_WHITE, 0.75F),
                    mix(p.rim, CALM_WHITE, 0.6F), mix(p.glow, CALM_WHITE, 0.6F), CALM_WHITE, p.crack);
        }
        return p;
    }

    /** Linear mix of two opaque ARGB colours. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // ---- look: everything one frame needs ---------------------------------------------------------------------------

    /**
     * One gate's look. {@code open} 0..1 grows the vortex while it forms; {@code warn} 0..1 how close the break is;
     * {@code fade} 0..1 an overall alpha (distance fade).
     */
    public record Look(int rank, int mode, boolean red, long seed, float open, float warn, float fade) {
        public Size size() {
            return GateVortex.size(rank);
        }
    }

    /** Where vertices go: four calls make one quad. Normals matter only for the rock shards. */
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz);
    }

    // ---- atlas layout (pixels of the 512x512 gate_vortex.png) ---------------------------------------------------------

    public static final float ATLAS = 512F;
    /** Bands that repeat across the whole width (u wraps): arms, filaments, rim crackle, ground crack. */
    public static final int ARMS_V0 = 4, ARMS_V1 = 124, FIL_V0 = 136, FIL_V1 = 256, RIM_V0 = 268, RIM_V1 = 332, CRACK_V0 = 344, CRACK_V1 = 360;
    /** Square sprites: x0, y0, size. */
    public static final int[] HALO = {0, 372, 128}, HEART = {136, 372, 128}, MOTE = {276, 372, 64}, SPARK = {276, 440, 64};

    // ---- the shared polar grid ----------------------------------------------------------------------------------------

    static final int SEGMENTS = 64;
    static final float[] RINGS = {0F, 0.05F, 0.1F, 0.16F, 0.23F, 0.31F, 0.4F, 0.5F, 0.6F, 0.7F, 0.79F, 0.88F, 0.94F, 1.0F, 1.08F, 1.16F,
            1.3F, 1.45F, 1.6F};
    private static final float[] COS = new float[SEGMENTS + 1], SIN = new float[SEGMENTS + 1];
    private static final float TAU = (float) (Math.PI * 2);

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    /** Quads the surface emits at most per frame (for budget checks). */
    public static int maxSurfaceQuads(int rank) {
        return SEGMENTS * (9 + 9 + 13 + 11 + 4) + moteCount(rank) + 24 + shardCount(rank);
    }

    // ---- motion -------------------------------------------------------------------------------------------------------

    /** Turns per second of the main arms (higher ranks turn faster; broken churns, calm drifts). */
    static float spin(Look l) {
        float s = 0.07F + 0.012F * clampRank(l.rank);
        return switch (l.mode) {
            case BROKEN -> s * 1.9F;
            case CALM -> s * 0.3F;
            case SPENT -> s * 0.5F;
            case WARNING -> s * (1.0F + 0.5F * l.warn);
            default -> s;
        };
    }

    /** Overall scale: forming grows (with a little overshoot), a warning gate breathes, a spent one shrinks. */
    static float scale(Look l, float t) {
        float s = switch (l.mode) {
            case SPENT -> 0.42F;
            case FORMING -> easeOutBack(l.open);
            default -> 1F;
        };
        if (l.mode == WARNING) s *= 1F + 0.045F * l.warn * pulse(t, 1.3F);
        return s;
    }

    static float pulse(float t, float period) {
        return 0.5F + 0.5F * (float) Math.sin(t * TAU / period);
    }

    static float easeOutBack(float x) {
        x = Math.max(0F, Math.min(1F, x));
        float c1 = 1.70158F, c3 = c1 + 1F;
        return 1F + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0F, Math.min(1F, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** A stable 0..1 hash of a seed and an index. */
    static float hash(long seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFFL) / (float) 0x1000000;
    }

    // ---- the surface ---------------------------------------------------------------------------------------------------

    /**
     * Emits every translucent layer of one frame: halo, dark heart, spiral arms, filaments, rim, motes, ground cracks.
     * {@code time} in seconds; {@code side} +1 or -1, the side of the plane the camera is on (layers stack toward it
     * and the funnel recedes away from it).
     */
    public static void emitSurface(Look l, float time, int side, Sink out) {
        emitSurface(l, time, side, out, out);
    }

    /** Only the dark heart (see {@link #emitSurface(Look, float, int, Sink, Sink)}). */
    public static void emitHeart(Look l, float time, int side, Sink heart) {
        emitSurface(l, time, side, null, heart);
    }

    /**
     * As above, with the dark heart sent to its own sink (null skips that part): the world renderer draws the heart
     * first and lets it write depth, so clouds and other late translucent layers stay behind the opening instead of
     * showing through it.
     */
    public static void emitSurface(Look l, float time, int side, @Nullable Sink out, @Nullable Sink heart) {
        Size sz = l.size();
        float sc = scale(l, time);
        if (sc <= 0.01F || l.fade <= 0.01F) return;
        float rx = sz.rx * sc, ry = sz.ry * sc;
        Palette p = palette(l.rank, l.mode, l.red);
        float fade = l.fade * (l.mode == SPENT ? 0.5F : l.mode == CALM ? 0.72F : 1F);
        float depth = side * 0.2F * sz.ry * sc;
        float warnPulse = l.mode == WARNING ? l.warn * pulse(time, 1.3F) : 0F;
        float turn = spin(l) * time;
        boolean broken = l.mode == BROKEN;
        // forming: the heart shows first, the arms and rim follow
        float openArms = l.mode == FORMING ? smooth(0.25F, 0.8F, l.open) : 1F;

        if (heart != null) {
            // 2. the dark heart
            radial(heart, rx, ry, 0, 10, -0.05F * side, depth, HEART, 0.79F, withAlpha(p.heart, 0.93F * fade), 1F, 1F);
        }
        if (out == null) return;
        // 1. glow halo behind everything (outer part of the grid)
        int glow = withAlpha(mix(p.glow, WARN_RED, 0.35F * warnPulse), (0.42F + 0.2F * warnPulse) * fade);
        radial(out, rx, ry, 9, RINGS.length - 1, -0.09F * side, depth, HALO, 1.6F, glow, 1F, 1F);
        // 3. spiral arms: a logarithmic spiral turning, so the arms flow inward
        float armsA = 0.95F * fade * openArms;
        band(out, rx, ry, 1, 13, 0F, depth, ARMS_V0, ARMS_V1, 1F, 0.62F, turn, broken ? 0.09F : 0F, time,
                withAlpha(p.arm, armsA), 0.06F, 0.3F, 0.86F, 1.0F);
        // 4. thin bright filaments: their own twist and speed, so the layers shear against each other
        band(out, rx, ry, 2, 13, 0.025F * side, depth, FIL_V0, FIL_V1, 2F, 1.05F, turn * 1.55F + 0.37F, broken ? 0.14F : 0.02F, time,
                withAlpha(p.filament, 0.8F * fade * openArms), 0.1F, 0.42F, 0.8F, 0.98F);
        // 5. the rim: crackling light in the rank colour, flickering red when the break is near
        float flick = 0.86F + 0.14F * (float) Math.sin(time * 9.1F + 1.7F * (float) Math.sin(time * 2.3F));
        int rim = withAlpha(mix(p.rim, WARN_RED, 0.45F * warnPulse), Math.min(1F, (0.95F + 0.25F * warnPulse) * flick * fade * openArms));
        rim(out, rx, ry, 0.05F * side, time, broken ? 0.5F : 0.22F, rim);
        emitShardGlows(l, time, side, out);
        // 6. motes pulled in from the land and the air
        motes(out, l, sz, rx, ry, time, 0.07F * side, p, fade * openArms);
        // 7. cracks over the pad: spreading while the break comes, red once broken
        if (l.mode == WARNING || broken) cracks(out, l, sz, time, p, fade);
    }

    /** A radial sprite (halo, heart) mapped onto rings [r0..r1] of the grid. */
    private static void radial(Sink out, float rx, float ry, int r0, int r1, float lift, float depth, int[] sprite, float extent,
                               int argb, float alphaIn, float alphaOut) {
        float cx = (sprite[0] + sprite[2] * 0.5F) / ATLAS, cy = (sprite[1] + sprite[2] * 0.5F) / ATLAS, rad = (sprite[2] * 0.5F - 1F) / ATLAS;
        for (int ri = r0; ri < r1; ri++) {
            float ra = RINGS[ri], rb = RINGS[ri + 1];
            float za = lift + funnel(ra, depth), zb = lift + funnel(rb, depth);
            for (int s = 0; s < SEGMENTS; s++) {
                int s1 = s + 1;
                float ua = cx + COS[s] * ra / extent * rad, va = cy + SIN[s] * ra / extent * rad;
                float ub = cx + COS[s1] * ra / extent * rad, vb = cy + SIN[s1] * ra / extent * rad;
                float uc = cx + COS[s1] * rb / extent * rad, vc = cy + SIN[s1] * rb / extent * rad;
                float ud = cx + COS[s] * rb / extent * rad, vd = cy + SIN[s] * rb / extent * rad;
                out.vertex(COS[s] * ra * rx, SIN[s] * ra * ry, za, ua, va, argb, 0, 0, 1);
                out.vertex(COS[s1] * ra * rx, SIN[s1] * ra * ry, za, ub, vb, argb, 0, 0, 1);
                out.vertex(COS[s1] * rb * rx, SIN[s1] * rb * ry, zb, uc, vc, argb, 0, 0, 1);
                out.vertex(COS[s] * rb * rx, SIN[s] * rb * ry, zb, ud, vd, argb, 0, 0, 1);
            }
        }
    }

    /** The funnel: the middle recedes from the viewer. */
    private static float funnel(float r, float depth) {
        float k = Math.max(0F, 1F - r);
        return -depth * k * k;
    }

    /**
     * A wrapping band (arms, filaments) on rings [r0..r1]: u follows a logarithmic spiral with {@code count} arms,
     * {@code twist} turns per e-fold of radius, turned by {@code turn}; v runs from the centre to the rim.
     */
    private static void band(Sink out, float rx, float ry, int r0, int r1, float lift, float depth, int v0, int v1, float count, float twist,
                             float turn, float jitter, float time, int argb, float in0, float in1, float out0, float out1) {
        float rMin = RINGS[r0], rMax = RINGS[r1];
        float a = (argb >>> 24) / 255F;
        int rgb = argb & 0xFFFFFF;
        for (int ri = r0; ri < r1; ri++) {
            float ra = RINGS[ri], rb = RINGS[ri + 1];
            float va = (v0 + (v1 - v0) * (ra - rMin) / (rMax - rMin)) / ATLAS, vb = (v0 + (v1 - v0) * (rb - rMin) / (rMax - rMin)) / ATLAS;
            float la = twist * (float) Math.log(Math.max(ra, 0.04F)), lb = twist * (float) Math.log(Math.max(rb, 0.04F));
            int ca = withAlpha(rgb, a * smooth(in0, in1, ra) * (1F - smooth(out0, out1, ra)));
            int cb = withAlpha(rgb, a * smooth(in0, in1, rb) * (1F - smooth(out0, out1, rb)));
            float za = lift + funnel(ra, depth), zb = lift + funnel(rb, depth);
            for (int s = 0; s < SEGMENTS; s++) {
                int s1 = s + 1;
                float ta = count * (s / (float) SEGMENTS - turn), tb = count * (s1 / (float) SEGMENTS - turn);
                float ja = 0F, jb = 0F, jc = 0F, jd = 0F;
                if (jitter > 0F) {
                    ja = jitter * (float) Math.sin(s * 0.59F + time * 2.3F + ra * 7F);
                    jb = jitter * (float) Math.sin(s1 * 0.59F + time * 2.3F + ra * 7F);
                    jc = jitter * (float) Math.sin(s1 * 0.59F + time * 2.3F + rb * 7F);
                    jd = jitter * (float) Math.sin(s * 0.59F + time * 2.3F + rb * 7F);
                }
                out.vertex(COS[s] * ra * rx, SIN[s] * ra * ry, za, ta + la + ja, va, ca, 0, 0, 1);
                out.vertex(COS[s1] * ra * rx, SIN[s1] * ra * ry, za, tb + la + jb, va, ca, 0, 0, 1);
                out.vertex(COS[s1] * rb * rx, SIN[s1] * rb * ry, zb, tb + lb + jc, vb, cb, 0, 0, 1);
                out.vertex(COS[s] * rb * rx, SIN[s] * rb * ry, zb, ta + lb + jd, vb, cb, 0, 0, 1);
            }
        }
    }

    /** The rim band on rings 0.88..1.16: crackle running round it in both directions. */
    private static void rim(Sink out, float rx, float ry, float lift, float time, float speed, int argb) {
        int r0 = 11, r1 = 15;
        float rMin = RINGS[r0], rMax = RINGS[r1];
        for (int ri = r0; ri < r1; ri++) {
            float ra = RINGS[ri], rb = RINGS[ri + 1];
            float va = (RIM_V0 + (RIM_V1 - RIM_V0) * (ra - rMin) / (rMax - rMin)) / ATLAS;
            float vb = (RIM_V0 + (RIM_V1 - RIM_V0) * (rb - rMin) / (rMax - rMin)) / ATLAS;
            for (int s = 0; s < SEGMENTS; s++) {
                int s1 = s + 1;
                float ua = 4F * s / SEGMENTS - speed * time, ub = 4F * s1 / SEGMENTS - speed * time;
                out.vertex(COS[s] * ra * rx, SIN[s] * ra * ry, lift, ua, va, argb, 0, 0, 1);
                out.vertex(COS[s1] * ra * rx, SIN[s1] * ra * ry, lift, ub, va, argb, 0, 0, 1);
                out.vertex(COS[s1] * rb * rx, SIN[s1] * rb * ry, lift, ub, vb, argb, 0, 0, 1);
                out.vertex(COS[s] * rb * rx, SIN[s] * rb * ry, lift, ua, vb, argb, 0, 0, 1);
            }
        }
    }

    public static int moteCount(int rank) {
        return 20 + 6 * clampRank(rank);
    }

    /**
     * Motes: each one lives a few seconds on a loop. Two in three spiral in from the edge of the glow; one in three
     * rises off the pad first and is pulled into the rim. Broken gates shed embers outward and up instead.
     */
    private static void motes(Sink out, Look l, Size sz, float rx, float ry, float time, float lift, Palette p, float fade) {
        int n = moteCount(l.rank);
        if (l.mode == CALM) n /= 3;
        if (l.mode == SPENT) return;
        float ground = -sz.centreY() + 1.05F;
        float speedUp = l.mode == WARNING ? 1F + 0.6F * l.warn : 1F;
        float mu0 = MOTE[0] / ATLAS, mv0 = MOTE[1] / ATLAS, mu1 = (MOTE[0] + MOTE[2]) / ATLAS, mv1 = (MOTE[1] + MOTE[2]) / ATLAS;
        for (int i = 0; i < n; i++) {
            float period = 2.6F + 2.6F * hash(l.seed, i * 3);
            float phase = time * speedUp / period + hash(l.seed, i * 3 + 1);
            float tau = phase - (float) Math.floor(phase);
            float th0 = hash(l.seed, i * 3 + 2) * TAU;
            float x, y, z;
            float size = (0.07F + 0.08F * hash(l.seed, i + 911)) * (1F + 0.06F * l.rank);
            float alpha = (float) Math.sin(Math.PI * tau);
            if (l.mode == BROKEN) {
                float r = 0.35F + 1.3F * tau;
                float th = th0 + 0.6F * tau;
                x = (float) Math.cos(th) * r * rx;
                y = (float) Math.sin(th) * r * ry + tau * 2.2F;
                z = lift;
            } else if (i % 3 == 0) {
                // off the pad, up to the rim, then in
                float gr = sz.padRadius() * (0.55F + 0.4F * hash(l.seed, i + 77));
                float gx = (float) Math.cos(th0) * gr, gz = (float) Math.sin(th0) * gr * 0.8F;
                float rimx = (float) Math.cos(th0) * rx * 1.05F, rimy = (float) Math.sin(th0 * 0.5F) * ry * 0.6F;
                if (tau < 0.55F) {
                    float k = tau / 0.55F, e = k * k * (3 - 2 * k);
                    x = gx + (rimx - gx) * e;
                    y = ground + (rimy - ground) * e + (float) Math.sin(k * Math.PI) * 1.2F;
                    z = gz * (1F - e) + lift * e;
                } else {
                    float k = (tau - 0.55F) / 0.45F;
                    float r = 1.05F * (1F - k * 0.92F);
                    float th = (float) Math.atan2(rimy / ry, rimx / rx) + k * 2.4F;
                    x = (float) Math.cos(th) * r * rx;
                    y = (float) Math.sin(th) * r * ry;
                    z = lift;
                }
            } else {
                float r = 0.06F + 1.45F * (float) Math.pow(1F - tau, 0.75F);
                float th = th0 + tau * (2.2F + hash(l.seed, i + 5) * 2F);
                x = (float) Math.cos(th) * r * rx;
                y = (float) Math.sin(th) * r * ry;
                z = lift;
            }
            int c = withAlpha(l.mode == BROKEN ? p.crack : p.mote, Math.min(1F, alpha * 0.9F * fade));
            out.vertex(x - size, y - size, z, mu0, mv1, c, 0, 0, 1);
            out.vertex(x + size, y - size, z, mu1, mv1, c, 0, 0, 1);
            out.vertex(x + size, y + size, z, mu1, mv0, c, 0, 0, 1);
            out.vertex(x - size, y + size, z, mu0, mv0, c, 0, 0, 1);
        }
    }

    /**
     * Cracks lying on the pad, radiating from the foot of the vortex: they lengthen as the break comes (warn 0..1) and
     * reach the edge of the pad once it has broken.
     */
    private static void cracks(Sink out, Look l, Size sz, float time, Palette p, float fade) {
        int n = 7 + clampRank(l.rank);
        float ground = -sz.centreY() + 1.02F;
        float reach = l.mode == BROKEN ? 1F : 0.35F + 0.65F * l.warn;
        float glowP = l.mode == BROKEN ? 0.85F : 0.55F + 0.45F * pulse(time, 1.3F);
        float v0 = CRACK_V0 / ATLAS, v1 = CRACK_V1 / ATLAS;
        int c = withAlpha(l.mode == BROKEN ? p.crack : mix(p.crack, WARN_RED, 0.5F), Math.min(1F, glowP * fade));
        for (int i = 0; i < n; i++) {
            float th = TAU * (i + 0.6F * hash(l.seed, 400 + i)) / n;
            float len = sz.padRadius() * reach * (0.6F + 0.4F * hash(l.seed, 500 + i));
            // creep: the tip advances and settles on a slow loop
            len *= 0.9F + 0.1F * pulse(time + i, 3.7F);
            float dx = (float) Math.cos(th), dz = (float) Math.sin(th);
            float w = 0.32F;
            float x0 = dx * 0.6F, z0 = dz * 0.6F, x1 = dx * len, z1 = dz * len;
            float px = -dz * w, pz = dx * w;
            float u1 = len / 2.5F + hash(l.seed, 600 + i);
            float u0 = hash(l.seed, 600 + i);
            out.vertex(x0 - px, ground, z0 - pz, u0, v1, c, 0, 1, 0);
            out.vertex(x1 - px, ground, z1 - pz, u1, v1, c, 0, 1, 0);
            out.vertex(x1 + px, ground, z1 + pz, u1, v0, c, 0, 1, 0);
            out.vertex(x0 + px, ground, z0 + pz, u0, v0, c, 0, 1, 0);
        }
    }

    // ---- rock shards ----------------------------------------------------------------------------------------------------

    public static int shardCount(int rank) {
        return 4 + 2 * clampRank(rank);
    }

    /**
     * One shard's place this frame, written into {@code o}: centre (0..2), half extents (3..5), rotation as yaw, pitch
     * and roll in radians (6..8). No allocation: the renderer calls this every frame.
     */
    public static void shard(Look l, int i, float time, float[] o) {
        Size sz = l.size();
        float sc = scale(l, time);
        float th = TAU * (i + 0.7F * hash(l.seed, 700 + i)) / shardCount(l.rank);
        float rho = 1.2F + 0.32F * hash(l.seed, 710 + i);
        float s = (0.2F + 0.32F * hash(l.seed, 720 + i)) * (1F + 0.1F * clampRank(l.rank));
        float elong = 1.2F + 1.1F * hash(l.seed, 730 + i);
        float zOff = (hash(l.seed, 740 + i) - 0.5F) * 1.4F;
        if (l.mode == BROKEN) {
            // fallen: lying on the pad round the foot of the gate
            float gr = sz.rx * (0.8F + 0.5F * hash(l.seed, 750 + i));
            float ground = -sz.centreY() + 1F;
            set(o, (float) Math.cos(th) * gr, ground + s * 0.5F, (float) Math.sin(th) * gr * 0.9F + zOff, s, s * elong * 0.6F, s,
                    th * 3F, 1.2F + hash(l.seed, 760 + i), 0.3F);
            return;
        }
        float motion = l.mode == CALM ? 0.15F : l.mode == WARNING ? 1F + 0.8F * l.warn : 1F;
        float orbit = th + 0.035F * time * motion * TAU * (hash(l.seed, 770 + i) < 0.5F ? 1F : 0.7F);
        float bob = 0.18F * (float) Math.sin(time * 0.8F * motion + i * 1.7F);
        float jitter = l.mode == WARNING ? 0.06F * l.warn * (float) Math.sin(time * 23F + i) : 0F;
        set(o, (float) Math.cos(orbit) * rho * sz.rx * sc + jitter, (float) Math.sin(orbit) * rho * sz.ry * sc + bob, zOff * sc,
                s * sc, s * elong * sc, s * 0.8F * sc,
                time * 0.25F * motion * (0.5F + hash(l.seed, 780 + i)) + i, 0.6F * (float) Math.sin(time * 0.3F * motion + i), th);
    }

    private static void set(float[] o, float x, float y, float z, float hx, float hy, float hz, float yaw, float pitch, float roll) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
        o[3] = hx;
        o[4] = hy;
        o[5] = hz;
        o[6] = yaw;
        o[7] = pitch;
        o[8] = roll;
    }

    /** Rock shards orbiting the rim (fallen when broken), as boxes with normals (texture gate_shard.png, 32x32). */
    public static void emitShards(Look l, float time, Sink out) {
        if (l.fade <= 0.01F || l.mode == SPENT) return;
        float[] s = new float[9], n = new float[3], w = new float[3];
        int count = shardCount(l.rank);
        for (int i = 0; i < count; i++) {
            shard(l, i, time, s);
            box(out, s, i, n, w);
        }
    }

    /** Glow behind each floating shard (part of the surface pass). */
    public static void emitShardGlows(Look l, float time, int side, Sink out) {
        if (l.fade <= 0.01F || l.mode == SPENT || l.mode == BROKEN) return;
        Palette p = palette(l.rank, l.mode, l.red);
        int c = withAlpha(p.glow, 0.5F * l.fade);
        float u0 = HALO[0] / ATLAS, v0 = HALO[1] / ATLAS, u1 = (HALO[0] + HALO[2]) / ATLAS, v1 = (HALO[1] + HALO[2]) / ATLAS;
        float[] s = new float[9];
        for (int i = 0; i < shardCount(l.rank); i++) {
            shard(l, i, time, s);
            float r = Math.max(s[3], s[4]) * 2.6F, z = s[2] - 0.3F * side;
            out.vertex(s[0] - r, s[1] - r, z, u0, v1, c, 0, 0, 1);
            out.vertex(s[0] + r, s[1] - r, z, u1, v1, c, 0, 0, 1);
            out.vertex(s[0] + r, s[1] + r, z, u1, v0, c, 0, 0, 1);
            out.vertex(s[0] - r, s[1] + r, z, u0, v0, c, 0, 0, 1);
        }
    }

    private static final float[][] FACES = {
            // normal, then four corners as signs of (x, y, z)
            {0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1},
            {0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
            {0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1},
            {0, 0, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1},
            {1, 0, 0, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1},
            {-1, 0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1}};
    private static final float[] FACE_UV = {0, 1, 1, 1, 1, 0, 0, 0};

    private static void box(Sink out, float[] s, int i, float[] n, float[] w) {
        float cy = (float) Math.cos(s[6]), sy = (float) Math.sin(s[6]);
        float cp = (float) Math.cos(s[7]), sp = (float) Math.sin(s[7]);
        float cr = (float) Math.cos(s[8]), sr = (float) Math.sin(s[8]);
        // texture: 32x32, four 16x16 tiles, one per shard in turn
        float tu = (i & 1) * 0.5F, tv = ((i >> 1) & 1) * 0.5F;
        for (float[] f : FACES) {
            rot(f[0], f[1], f[2], cy, sy, cp, sp, cr, sr, n);
            for (int k = 0; k < 4; k++) {
                rot(f[3 + k * 3] * s[3], f[4 + k * 3] * s[4], f[5 + k * 3] * s[5], cy, sy, cp, sp, cr, sr, w);
                out.vertex(s[0] + w[0], s[1] + w[1], s[2] + w[2], tu + FACE_UV[k * 2] * 0.5F, tv + FACE_UV[k * 2 + 1] * 0.5F, 0xFFFFFFFF,
                        n[0], n[1], n[2]);
            }
        }
    }

    /** Roll about z, then pitch about x, then yaw about y. */
    private static void rot(float x, float y, float z, float cy, float sy, float cp, float sp, float cr, float sr, float[] o) {
        float x1 = x * cr - y * sr, y1 = x * sr + y * cr;
        float y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;
        o[0] = x1 * cy + z2 * sy;
        o[1] = y2;
        o[2] = -x1 * sy + z2 * cy;
    }

    // ---- footprint of a look in local space (render bounds) -----------------------------------------------------------

    /** Half extents of everything drawn, around the vortex centre: along the face, up/down, through. */
    public static float[] bounds(int rank) {
        Size s = size(rank);
        float h = Math.max(s.centreY(), s.ry * 1.75F) + 1F;
        return new float[]{Math.max(s.rx * 1.75F, s.padRadius()) + 1F, h, s.padRadius() + 1F};
    }
}
