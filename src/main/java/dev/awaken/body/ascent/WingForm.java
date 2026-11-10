package dev.awaken.body.ascent;

import net.minecraft.util.Mth;

/**
 * The feathered wings' form and motion (SPEC §62), as plain numbers: the port, one to one, of the model Euan approved
 * on the plan's page (docs/movement/src/wings.js; its numbers are these numbers). A wing is three bones in its own
 * plane and, hung from them, 22 flight feathers (5 tertials, 8 secondaries, 9 primaries) under two rows of coverts (13
 * and 11). A pose is twelve numbers ({@link #FOLD} ... {@link #FLARE}); the beat is one smooth cycle ({@link #beat}).
 * Nothing here draws (the client's WingFeathers does) and nothing allocates, so the tests can hold the form to its word.
 */
public final class WingForm {
    private WingForm() {}

    // ---- a pose: the bones' angles in the wing's plane, the flap about the body's long axis at each joint, the fan ----
    public static final int A0 = 0, A1 = 1, A2 = 2, P0 = 3, P1 = 4, P2 = 5, FAN_IN = 6, FAN_OUT = 7, SEC = 8, TER = 9, CASC = 10, BOB = 11, N = 12;
    public static final float[] FOLD = {92, 38, -101, 16, 10, 8, -92, -89, -90, -92, 0, 0};
    public static final float[] OPEN = {66, 16, -18, 8, 0, 0, -72, -8, -84, -97, 1, 0};
    public static final float[] GLIDE = {50, 6, -15, 6, 5, 7, -70, -13, -86, -95, 1, 0};
    public static final float[] DIVE = {26, -128, -64, 22, 2, 4, -84, -70, -100, -98, 1, 0};
    public static final float[] FLARE = {64, 18, -6, -34, -12, -10, -78, 2, -84, -96, 1, 0};
    /** A hover's resting pose: a third of the way from open to the glide. */
    public static final float[] HOVER = new float[N];

    static {
        mix(OPEN, GLIDE, 0.3F, HOVER);
    }

    /** The beats' lengths in seconds: in flight, and the slow whole beats of a hover. */
    public static final float FLY_PERIOD = 0.95F, HOVER_PERIOD = 1.6F;
    /** The same in ticks (the beat's clock is whole ticks since it began, never a float of time). */
    public static final int FLY_TICKS = 19, HOVER_TICKS = 32;
    /** The share of a beat that is the downstroke. */
    public static final float DOWN = 0.42F;

    public static void mix(float[] a, float[] b, float t, float[] out) {
        for (int i = 0; i < N; i++) out[i] = a[i] + (b[i] - a[i]) * t;
    }

    public static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float backOut(float x) {
        x = Mth.clamp(x, 0, 1);
        float c = 1.9F, y = x - 1;
        return 1 + (c + 1) * y * y * y + c * y * y;
    }

    /**
     * A beat: one smooth cycle of {@code period} seconds. Down takes 42% of it and up the rest, each eased in and out,
     * and every term comes back to where it began, so nothing jumps where the cycle loops. The shoulder moves little
     * (the inner wing stays over the back and never cuts the body); the bend runs out along the wing a little late at
     * each joint, so the tip trails; on the way up the wrist folds and the long feathers close, on the way down they
     * spread. {@code lift}: how far the wing also rises and falls in its own plane (an upright hover's beat);
     * {@code amp} scales the stroke. (Euan, 10 Nov 2026, on the first beat: "it clips a bit and it just doesn't look
     * professional": that one jumped where it looped, turned each feather through its neighbours and hinged the whole
     * wing at the shoulder.)
     */
    public static void beat(float[] base, float t, float period, float amp, float lift, float[] out) {
        float u = (t % period) / period;
        if (u < 0) u += 1;
        float th = u < DOWN ? Mth.PI * u / DOWN : Mth.PI * (1 + (u - DOWN) / (1 - DOWN));
        float sn = Mth.sin(th), up = th > Mth.PI ? sn * sn : 0, dn = th > Mth.PI ? 0 : sn * sn;
        System.arraycopy(base, 0, out, 0, N);
        out[P0] = base[P0] + amp * 9 * Mth.cos(th);
        out[P1] = base[P1] + amp * 25 * Mth.cos(th - 0.55F);
        out[P2] = base[P2] + amp * 15 * Mth.cos(th - 1.15F);
        out[A0] = base[A0] + lift * 9 * Mth.cos(th);
        out[A1] = base[A1] + lift * 7 * Mth.cos(th - 0.5F) - amp * 8 * up;
        out[A2] = base[A2] - amp * 18 * up;
        out[FAN_OUT] = base[FAN_OUT] - amp * 9 * up + amp * 5 * dn;
        out[FAN_IN] = base[FAN_IN] - amp * 3 * up;
        out[BOB] = -1.3F * amp * Mth.cos(th - 0.7F);
    }

    /** How long the opening takes (seconds): a gather, a spread that runs out feather by feather, a settle. */
    public static final float UNFOLD = 0.9F;
    public static final int UNFOLD_TICKS = 18;

    /** The opening, {@code t} seconds in: the folded wings lift and draw back, spread past open, and settle. */
    public static void unfold(float t, float[] out) {
        if (t < 0.22F) {
            gather(ease(t / 0.22F), out);
            return;
        }
        float k = backOut((t - 0.22F) / 0.42F);
        mix(FOLD, OPEN, k, out);
        out[CASC] = Math.min(1, (t - 0.22F) / 0.5F * 1.25F);
        out[P0] = Mth.lerp(k, FOLD[P0] + 18, OPEN[P0]);
        if (t > 0.64F) {
            float s = (float) (Math.exp(-(t - 0.64F) * 6) * Math.sin((t - 0.64F) * 14));
            out[FAN_OUT] += 7 * s;
            out[P2] += 6 * s;
            out[CASC] = 1;
        }
    }

    /** The gather alone, held: the folded wings lifted and drawn back by {@code k} (the opening's first beat, a takeoff's crouch). */
    public static void gather(float k, float[] out) {
        System.arraycopy(FOLD, 0, out, 0, N);
        out[A0] = FOLD[A0] + 6 * k;
        out[P0] = FOLD[P0] + 18 * k;
        out[A1] = FOLD[A1] + 14 * k;
    }

    /**
     * The glide: flat, the tips trembling in the air ({@code tremble} 0 for reduced motion), and swept back a little
     * toward the dive as speed builds ({@code sweep} 0..1).
     */
    public static void glide(float t, float sweep, float tremble, float[] out) {
        System.arraycopy(GLIDE, 0, out, 0, N);
        float w = (Mth.sin(t * 9) * 0.6F + Mth.sin(t * 23) * 0.4F) * tremble;
        out[P2] += w * 1.6F;
        out[FAN_OUT] += w * 1.2F;
        out[P0] += Mth.sin(t * 1.3F) * 1.5F * tremble;
        float s = sweep * SWEEP;
        out[A1] += (DIVE[A1] - GLIDE[A1]) * s;
        out[A2] += (DIVE[A2] - GLIDE[A2]) * s;
        out[FAN_OUT] += (DIVE[FAN_OUT] - GLIDE[FAN_OUT]) * s;
    }

    /** How far toward the dive's tuck full speed sweeps a glide. */
    public static final float SWEEP = 0.12F;

    /** The dive: tucked into a W, shivering. */
    public static void dive(float t, float tremble, float[] out) {
        System.arraycopy(DIVE, 0, out, 0, N);
        float w = (Mth.sin(t * 31) * 0.5F + Mth.sin(t * 47) * 0.5F) * tremble;
        out[FAN_OUT] += w * 1.5F;
        out[P2] += w;
    }

    /** How long the flare holds before the wings fold (seconds, ticks). */
    public static final float LAND = 0.9F;
    public static final int LAND_TICKS = 18;

    /** The flare (a landing): cupped forward, the feathers spread, a settle. */
    public static void land(float t, float[] out) {
        mix(GLIDE, FLARE, ease(t / 0.3F), out);
        if (t > 0.3F) {
            float s = (float) (Math.exp(-(t - 0.3F) * 4) * Math.sin((t - 0.3F) * 11));
            out[P0] += 8 * s;
            out[FAN_OUT] += 5 * s;
        }
    }

    /** The flap at a place on the span (0 the shoulder, 1 the elbow, 2 the wrist, 3 the tip), eased in across each joint: degrees. */
    public static float flap(float p0, float p1, float p2, float sp) {
        return p0 + p1 * ease((sp - 0.7F) / 0.7F) + p2 * ease((sp - 1.7F) / 0.7F);
    }

    // ---- the feathers -----------------------------------------------------------------------------------------------

    /** The bones' lengths (texels). */
    public static final float[] BONE = {9, 13, 14};
    /** The flight feathers' lengths along the span: tertials on the first bone, secondaries on the second, primaries on the third. */
    public static final int[] TERS = {10, 12, 13, 14, 15}, SECS = {15, 16, 17, 18, 19, 19, 20, 20}, PRIM = {21, 24, 26, 28, 28, 27, 25, 22, 18};
    /** The two rows of coverts over them. */
    public static final int COV = 13, COV2 = 11;

    public static int covert(int row, int i) {
        return row == 0 ? (i < 4 ? 6 : i < 9 ? 8 : 9) : (i < 4 ? 4 : 5);
    }

    /** The groups a wing's feathers come in. */
    public static final int G_TER = 0, G_SEC = 1, G_PRIM = 2;

    /** Far forms: 0 everything; 1 (past 48 blocks) every other feather, no coverts, no shafts; 2 (past 96) the three bones and six broad slabs. */
    public static int lod(double distanceSq) {
        return distanceSq > 96 * 96 ? 2 : distanceSq > 48 * 48 ? 1 : 0;
    }

    /** The first feather of a group drawn at this distance, and the step to the next. */
    public static int first(int group, int lod) {
        return lod < 2 ? 0 : group == G_TER ? 2 : 1;
    }

    public static int step(int group, int lod) {
        return lod == 0 ? 1 : lod == 1 ? 2 : group == G_TER ? 9 : group == G_SEC ? 4 : 3;
    }

    /** A feather's width at this distance (fewer feathers are broader, so the wing stays whole). */
    public static float width(int group, int lod) {
        return lod == 0 ? 3 : lod == 1 ? 4 : group == G_TER ? 9 : group == G_SEC ? 8 : 6;
    }

    /** The boxes of one feather: root, mid, the outer step if it is long enough, the tip; and its shaft up close. */
    public static int featherBoxes(int len, int lod) {
        int a = Math.max(2, Math.round(len * 0.42F)), b = Math.max(a + 1, len - 4);
        return 3 + (len - 1 > b ? 1 : 0) + (lod == 0 ? 1 : 0);
    }

    /** The boxes of one wing at this distance (what the client draws; the budget the plan gave is about 230 up close). */
    public static int boxes(int lod) {
        int n = 3 + (lod == 0 ? 2 : 0); // the bones, and their knuckles up close
        for (int i = first(G_TER, lod); i < TERS.length; i += step(G_TER, lod)) n += featherBoxes(TERS[i], lod);
        for (int i = first(G_SEC, lod); i < SECS.length; i += step(G_SEC, lod)) n += featherBoxes(SECS[i], lod);
        for (int i = first(G_PRIM, lod); i < PRIM.length; i += step(G_PRIM, lod)) n += featherBoxes(PRIM[i], lod);
        if (lod == 0) {
            for (int i = 0; i < COV; i++) n += featherBoxes(covert(0, i), 0);
            for (int i = 0; i < COV2; i++) n += featherBoxes(covert(1, i), 0);
        }
        return n;
    }

    // ---- the rungs --------------------------------------------------------------------------------------------------

    /**
     * Each rung's wings: Wings one pair; Skyborn a larger pair and a smaller one under it; the Sovereign's form three
     * pairs. Per pair: its size, its roll about the body's front-to-back axis (degrees; negative lifts it), how late it
     * follows the pair before it (seconds).
     */
    public static final float[][] PAIRS_WINGS = {{0.94F, 0, 0}}, PAIRS_SKYBORN = {{1.18F, -6, 0}, {0.6F, 44, 0.12F}},
            PAIRS_SOVEREIGN = {{0.82F, -50, 0}, {1.26F, -2, 0.1F}, {0.86F, 40, 0.2F}};
}
