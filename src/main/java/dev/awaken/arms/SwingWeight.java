package dev.awaken.arms;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The weight a weapon shows in the hand (SPEC §25 "Weight in the Hand", stages 3 and 4): layers drawn over the baked
 * swings, never in place of them. Measured before they were built: at a player's pace the fast part of a cut is on
 * screen for about three frames, and the freeze and what follows for four times as long, in which the weapon held one
 * pose, came to a dead stop within 60 ms and then did not move at all. So:
 * <ul>
 * <li><b>the shake</b>: while a landed blow is frozen the weapon shudders along the cut and dies away by the freeze's end;</li>
 * <li><b>the settle</b>: where the blade stops after a cut it carries a little past and comes back once, with no bounce;</li>
 * <li><b>the living hold</b>: a held weapon breathes, a few millimetres, once in four and a half seconds;</li>
 * <li><b>the guard's ease</b>: up on a curve that seats with a small settle, down over three ticks;</li>
 * <li><b>the lowered rest</b>: a rested Katana lowers and rises on an ease from wherever it is, never in one frame;</li>
 * <li><b>the draw</b>: a changed weapon comes up on its own clock, by its weight, with a turn of the wrist.</li>
 * </ul>
 * Each is small on purpose and by a weapon's {@link ArmsRules.Heft}. A hand at rest is never moved by more than the
 * breath: the settle acts only where the hand was travelling. Plain maths, so a GameTest can hold every curve to its
 * rules; the hands in {@code ArmsHands} use exactly this. Nothing here keeps time: the callers pass the hands' own clock,
 * a double.
 */
public final class SwingWeight {
    private SwingWeight() {}

    /**
     * A weight class's numbers. {@code shake}: the shudder's first size (blocks at the hand; 0.008 is about 9 pixels at
     * 1080p). {@code settle}, {@code settleTurn}, {@code settleTicks}: how far past its stop the hand carries (blocks),
     * the weapon turns (degrees), and when that peaks (ticks; it is gone by about five times that). {@code lagReach},
     * {@code lagRate}: how far and how quickly the hands trail the view's turn, against the body's own (1, 1).
     * {@code drawTicks}: how long a changed weapon takes to come up.
     */
    public record Weight(float shake, float settle, float settleTurn, float settleTicks, float lagReach, float lagRate, float drawTicks) {}

    public static final Weight SNAP = new Weight(0.005F, 0.015F, 1.5F, 0.9F, 0.75F, 1.3F, 6F),
            BLADE = new Weight(0.008F, 0.025F, 2.5F, 1.4F, 1F, 1F, 8F),
            HEAVY = new Weight(0.012F, 0.040F, 4F, 2.2F, 1.45F, 0.65F, 10F);

    public static Weight of(@org.jspecify.annotations.Nullable String style) {
        return switch (ArmsRules.heft(style)) {
            case SNAP -> SNAP;
            case BLADE -> BLADE;
            case HEAVY -> HEAVY;
        };
    }

    // ---- the shake ----------------------------------------------------------------------------------------------------

    /** The shake's steps a second: it turns about every frame at 60, so it reads as impact and never as drift. */
    public static final long SHAKE_NANOS = 16_000_000L;

    /**
     * The shudder {@code u} of the way through a freeze (0..1) at {@code nanos} on the feel's clock: -1..1, hardest on the
     * hit, nothing by the end, its sign turning every {@link #SHAKE_NANOS}. Outside a freeze: 0.
     */
    public static float shake(float u, long nanos) {
        if (!(u >= 0F) || u >= 1F) return 0F;
        return (1F - u) * ((nanos / SHAKE_NANOS & 1L) == 0L ? 1F : -1F);
    }

    // ---- the settle ---------------------------------------------------------------------------------------------------

    /** Past this many peaks the settle is nothing (under half a percent of its size). */
    public static final float SETTLE_END = 8F;

    /**
     * The settle {@code since} ticks after the stop, for a weight whose settle peaks at {@code peak} ticks: 0..1, rising
     * to 1 at the peak and falling back to nothing with no second swing (a critically damped answer to the blade's own
     * speed: x e^(1-x)).
     */
    public static float settle(float since, float peak) {
        if (!(since > 0F) || peak <= 0F) return 0F;
        float x = since / peak;
        return x >= SETTLE_END ? 0F : (float) (x * Math.exp(1.0 - x));
    }

    /**
     * Where a baked move's hand comes to a stop after its hit, and the way it was going: the settle's place and its
     * direction. {@code keys}: a clip's keys ({@link FpSwings#STRIDE} floats each, {@code dt} ticks apart); {@code hit}:
     * the tick its blow lands. Into {@code way} the hand's travel into the stop (a unit, the hand's own space) and into
     * {@code axis} the axis it was turning about (a unit, or zero when it wasn't turning). Returns the stop's tick, or -1
     * when the move has none worth settling: it never slows to a stop before its end, or it stops where it rests (within
     * {@link #REST_GAP} of the pose it ends on), because a hand at rest must stay at rest.
     */
    public static float stop(float[] keys, float dt, float hit, Vector3f way, Vector3f axis) {
        int n = keys.length / FpSwings.STRIDE;
        if (n < 4 || dt <= 0) return -1F;
        float peak = 0F;
        for (int i = 1; i < n; i++) peak = Math.max(peak, speed(keys, i, dt));
        if (peak <= 1e-4F) return -1F;
        int from = Math.min(n - 1, Math.max(1, Math.round(hit / dt) + 1));
        for (int i = from; i < n - 1; i++) {
            if (speed(keys, i, dt) >= STOP_SHARE * peak) continue;
            if (gap(keys, i, n - 1) < REST_GAP) return -1F; // it stopped where it rests: nothing to settle
            // the way it came in: over the two keys before it slowed (the last key's own step is already almost nothing)
            int a = Math.max(0, i - 3), b = Math.max(1, i - 1);
            way.set(keys[b * FpSwings.STRIDE] - keys[a * FpSwings.STRIDE], keys[b * FpSwings.STRIDE + 1] - keys[a * FpSwings.STRIDE + 1],
                    keys[b * FpSwings.STRIDE + 2] - keys[a * FpSwings.STRIDE + 2]);
            if (way.lengthSquared() > 1e-10F) way.normalize();
            else way.zero();
            turn(keys, a, b, axis);
            return way.lengthSquared() == 0F && axis.lengthSquared() == 0F ? -1F : i * dt;
        }
        return -1F;
    }

    /** A hand under this share of its move's fastest is stopped; and a stop this near the move's last pose is the rest itself (blocks + radians). */
    static final float STOP_SHARE = 0.06F, REST_GAP = 0.05F;

    /** The hand's speed into key {@code i}: its travel plus its turn (a radian weighed as 0.4 blocks, about a forearm), a tick. */
    private static float speed(float[] k, int i, float dt) {
        int a = (i - 1) * FpSwings.STRIDE, b = i * FpSwings.STRIDE;
        float dx = k[b] - k[a], dy = k[b + 1] - k[a + 1], dz = k[b + 2] - k[a + 2];
        return ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.4F * angle(k, a, b)) / dt;
    }

    private static float gap(float[] k, int i, int j) {
        int a = i * FpSwings.STRIDE, b = j * FpSwings.STRIDE;
        float dx = k[b] - k[a], dy = k[b + 1] - k[a + 1], dz = k[b + 2] - k[a + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.4F * angle(k, a, b);
    }

    /** The angle between two keys' turns (radians), read from the turn's x, y, z (a w rounded short of a unit has no axis). */
    private static float angle(float[] k, int a, int b) {
        float dot = Math.abs(k[a + 3] * k[b + 3] + k[a + 4] * k[b + 4] + k[a + 5] * k[b + 5] + k[a + 6] * k[b + 6]);
        return dot >= 1F ? 0F : 2F * (float) Math.acos(dot);
    }

    private static final ThreadLocal<Quaternionf[]> Q = ThreadLocal.withInitial(() -> new Quaternionf[] {new Quaternionf(), new Quaternionf()});

    /** The axis the hand turned about from key {@code a} to key {@code b} (zero when it didn't). */
    private static void turn(float[] k, int a, int b, Vector3f axis) {
        Quaternionf[] q = Q.get();
        int i = a * FpSwings.STRIDE, j = b * FpSwings.STRIDE;
        q[0].set(k[i + 3], k[i + 4], k[i + 5], k[i + 6]).conjugate();
        q[1].set(k[j + 3], k[j + 4], k[j + 5], k[j + 6]).mul(q[0]); // b . a^-1
        if (q[1].w < 0) q[1].set(-q[1].x, -q[1].y, -q[1].z, -q[1].w);
        float s = (float) Math.sqrt(q[1].x * q[1].x + q[1].y * q[1].y + q[1].z * q[1].z);
        if (s > 1e-5F) axis.set(q[1].x / s, q[1].y / s, q[1].z / s);
        else axis.zero();
    }

    // ---- the living hold ----------------------------------------------------------------------------------------------

    /** The breath: how far the held weapon rises and sinks (blocks, 3 mm) and how long one breath is (ns, 4.5 s). */
    public static final float BREATH = 0.003F;
    public static final long BREATH_NANOS = 4_500_000_000L;

    /** The breath at {@code nanos} on the feel's clock: -BREATH..BREATH, a slow sine. The clock is taken modulo a breath first, in a long. */
    public static float breath(long nanos) {
        double phase = Math.floorMod(nanos, BREATH_NANOS) / (double) BREATH_NANOS;
        return BREATH * (float) Math.sin(phase * Math.PI * 2.0);
    }

    // ---- the guard's ease ---------------------------------------------------------------------------------------------

    /** Ticks the guard takes to come up (the server's own two: its deflect window opens with the raise) and to go down. */
    public static final float GUARD_RISE = 2F, GUARD_FALL = 3F;
    /** How far past its place the guard carries before it seats (a share of the whole raise). */
    static final float GUARD_OVER = 0.045F;

    /**
     * The guard {@code t} ticks into its raise, from {@code from} (0..1, where it was): out fast and seated with a small
     * settle. It is past nine tenths of the way by a tick and a half and in place by {@link #GUARD_RISE}, never more than
     * {@link #GUARD_OVER} past 1.
     */
    public static float guardUp(float t, float from) {
        float u = Math.clamp(t / GUARD_RISE, 0F, 1F), c = 1F - u;
        float eased = 1F - c * c * c + GUARD_OVER * 4F * u * c * (float) Math.sin(u * Math.PI); // an ease-out cubic, a little past and back
        return from + (1F - from) * eased;
    }

    /** The guard {@code t} ticks into its lowering, from {@code from}: eased both ends, down by {@link #GUARD_FALL}. */
    public static float guardDown(float t, float from) {
        float u = Math.clamp(t / GUARD_FALL, 0F, 1F);
        return from * (1F - u * u * (3F - 2F * u));
    }

    // ---- the lowered rest ---------------------------------------------------------------------------------------------

    /** Ticks a rested weapon takes to lower to its rest, and to come back up from it. */
    public static final float LOWER_TICKS = 6F;

    /**
     * How far a weapon is lowered {@code t} ticks after it turned to go {@code down} (or back up), from {@code from}
     * (0..1, where it was at the turn): eased both ends, there by {@link #LOWER_TICKS}, and at {@code t} 0 exactly where it
     * was, so no turn of the rest, however sudden, moves the hand in one frame.
     */
    public static float lowered(float t, float from, boolean down) {
        float u = Math.clamp(t / LOWER_TICKS, 0F, 1F);
        u = u * u * (3F - 2F * u);
        return down ? from + (1F - from) * u : from * (1F - u);
    }

    // ---- the draw -----------------------------------------------------------------------------------------------------

    /** How far below its place a weapon starts its draw (a share of vanilla's full sink) and how far its wrist is turned (degrees). */
    public static final float DRAW_SINK = 1F, DRAW_TURN = 22F;
    /** A strike begun while the weapon is still coming up brings it up in no more than this many ticks. */
    public static final float DRAW_HURRY = 2F;

    /**
     * How far below its place the weapon still is {@code t} ticks into a draw of {@code ticks}: 1 at first, 0 once up, on
     * an ease-out so it arrives quickly and seats gently; a hair above its place just before it seats (the settle).
     */
    public static float drawSink(float t, float ticks) {
        if (ticks <= 0F || t >= ticks) return 0F;
        float u = Math.max(0F, t) / ticks, c = 1F - u;
        return DRAW_SINK * (c * c * c - 0.06F * (float) Math.sin(u * Math.PI) * u * u);
    }

    /** The wrist's turn {@code t} ticks into the draw (degrees, + toward the off hand): turned in at first, square once up. */
    public static float drawTurn(float t, float ticks) {
        if (ticks <= 0F || t >= ticks) return 0F;
        float c = 1F - Math.max(0F, t) / ticks;
        return DRAW_TURN * c * c;
    }
}
