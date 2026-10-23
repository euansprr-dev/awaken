package dev.awaken.powers.client.fx;

import dev.awaken.powers.fx.FxStyle;

/**
 * One live mesh effect (ring, sphere, beam, trail, bolt, wave, column, tether, seams, glyph, disc). A fixed set of
 * these is allocated once and reused; fields are generic and their meaning depends on {@link #type}.
 */
final class Shape {
    static final int RING = 1;       // flat band: radius a->b, width c->d, normal n, centre moves by v
    static final int SPHERE = 2;     // shell: radius a->b, rim power c, fill d; flag HEMI for a ground dome
    static final int BEAM = 3;       // anchor -> end; width a
    static final int TRAIL = 4;      // ribbon through the anchor's recent positions; width a
    static final int LIGHTNING = 5;  // jagged arc anchor/origin -> end; width a
    static final int ARC = 6;        // rolling wave: radius a->b, half angle c (rad), height d, direction n
    static final int COLUMN = 7;     // vertical light column from y to y + a; width b
    static final int TETHER = 8;     // anchor -> end (target), wave amplitude b, width a
    static final int SEAMS = 9;      // glowing edges of the blocks in {@link #blocks}
    static final int GLYPH = 10;     // flat spinning sprite on the ground: radius a->b, spin c
    static final int DISC = 11;      // camera-facing sprite at the anchor: size a->b
    static final int SHELL = 12;     // sprites on a sphere around the anchor facing outward (hex skin): radius a
    static final int EMITTER = 13;   // draws nothing; only runs its emitter (auras, trails of sparks, fire paths)

    /** Draw flags. */
    static final int HEMI = 1;       // sphere: upper half only
    static final int DARK = 2;       // disc: alpha-blended dark core instead of glow
    static final int INSIDE = 4;     // computed: camera inside a sphere
    static final int PULSE = 8;      // brightness pulses
    static final int FLICKER = 16;   // random flicker (electric)
    static final int AIM = 32;       // beam end follows the local player's live aim (own channelled beams)
    static final int HOLD = 64;      // sphere/ring: grow over the first 8 ticks, then hold the size

    int type;
    boolean live;
    int age, life;
    int fadeIn = 2, fadeOut = 4;
    int flags;

    /** Origin (absolute world coordinates); for anchored shapes, the last known anchor position. */
    double x, y, z;
    /** End point or direction (meaning by type). */
    double ex, ey, ez;
    /** Per-tick motion of the origin. */
    double vx, vy, vz;
    /** Normal / direction for rings and arcs. */
    float nx, ny = 1, nz;
    float a, b, c, d;
    int c0, c1, c2;
    int sprite = -1, frame;
    FxStyle style = FxStyle.NEUTRAL;
    int seed;

    /** Entity whose anchor the origin follows (-1 none) and which anchor (see {@link Anchors}). */
    int follow = -1, anchor;
    /** Entity the end follows (-1 none). */
    int target = -1;
    /** Range for aim-following beams. */
    float range;

    /** Per-tick emitter (see {@link FxRecipes#emit}); 0 none. */
    int emitter;
    int emitParam;

    /** Interpolated draw positions, refreshed every frame by {@link FxWorld#extract}. */
    double rx, ry, rz, rex, rey, rez;

    /** Trail history (ring buffer). */
    final double[] hx = new double[20], hy = new double[20], hz = new double[20];
    int hn, hhead;

    /** Block positions for seams (BlockPos.asLong) and how many. */
    final long[] blocks = new long[96];
    int blockCount;

    void reset() {
        live = false;
        age = 0;
        life = 1;
        fadeIn = 2;
        fadeOut = 4;
        flags = 0;
        x = y = z = ex = ey = ez = vx = vy = vz = 0;
        nx = 0;
        ny = 1;
        nz = 0;
        a = b = c = d = 0;
        c0 = c1 = c2 = 0;
        sprite = -1;
        frame = 0;
        style = FxStyle.NEUTRAL;
        seed = 0;
        follow = -1;
        anchor = 0;
        target = -1;
        range = 0;
        emitter = 0;
        emitParam = 0;
        hn = 0;
        hhead = 0;
        blockCount = 0;
    }

    /** 0..1 through the life at a partial tick. */
    float t(float pt) {
        return Math.min(1f, (age + pt) / Math.max(1, life));
    }

    /** Fade envelope 0..1 at a partial tick. */
    float fade(float pt) {
        float t = age + pt;
        float in = fadeIn <= 0 ? 1f : Math.min(1f, t / fadeIn);
        float out = fadeOut <= 0 ? 1f : Math.min(1f, (life - t) / fadeOut);
        return Math.max(0f, Math.min(in, out));
    }

    void pushHistory(double px, double py, double pz) {
        hhead = (hhead + 1) % hx.length;
        hx[hhead] = px;
        hy[hhead] = py;
        hz[hhead] = pz;
        if (hn < hx.length) hn++;
    }
}
