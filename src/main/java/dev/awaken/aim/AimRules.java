package dev.awaken.aim;

import dev.awaken.core.api.Aim;
import net.minecraft.util.Mth;

/**
 * Lock-on's rules (SPEC §40), side-agnostic and pure so GameTests can hold them: which creature the view means, how
 * fast a lock may turn the view, how much of the mouse it keeps, what counts as a flick, and how far a power's aim may
 * bend. The numbers the server checks presses with live in {@link Aim}.
 */
public final class AimRules {
    private AimRules() {}

    /** The mark: the creature nearest the view's middle within this cone and range, seen. */
    public static final float MARK_CONE = 25F, MARK_RANGE = 24F;
    /** The marked creature keeps the mark against one this much nearer the middle (no flicker between two). */
    public static final float STICKY = 8F;
    /** Nearer creatures win a close call: degrees a block of distance is worth. */
    public static final float NEAR_WEIGHT = 0.35F;

    /** A lock lets go beyond this range or after this long unseen (1.5 s). */
    public static final float LOCK_RANGE = 40F;
    public static final int LOST_TICKS = 30;
    /** A kill moves the lock on to the next creature within this cone and range, or lets go. */
    public static final float NEXT_CONE = 60F, NEXT_RANGE = 12F;
    /** A flick or the wheel picks among creatures this far from the view's middle. */
    public static final float SWITCH_CONE = 80F;

    /** The view turns toward a locked creature at most this fast (degrees a second), easing in. */
    public static final float TURN_MAX = 300F;
    /** The share of the gap the view closes each second (an exponential ease: fast when far, gentle when near). */
    public static final float TURN_EASE = 10F;
    /** Inside this many degrees the view stops chasing (the body's own sway, the creature's bob). */
    public static final float TURN_DEAD = 0.6F;
    /** While locked, the mouse keeps this share of its turn (to lead, to peek, to flick). */
    public static final float MOUSE_KEEP = 0.25F;
    /** A flick: the mouse's running sum (window pixels, fading over {@link #FLICK_MS}) reaches this. */
    public static final float FLICK_PX = 60F;
    public static final int FLICK_MS = 100;
    /** After a switch, no other for this long. */
    public static final int SWITCH_GAP_MS = 220;

    /** A power's bolt bends toward the locked or marked creature by at most this much. A bow never bends. */
    public static final float BEND_POWER = 10F;
    /** Tells from creatures off the screen within this range show as arcs at the screen's edge. */
    public static final float THREAT_RANGE = 12F;

    /**
     * How well a creature fits the mark: lower is better, NaN when it can't be marked. {@code off} is degrees from the
     * view's middle to the creature's box edge, {@code dist} blocks, {@code current} whether it holds the mark now.
     */
    public static float score(float off, double dist, boolean current, float cone, float range) {
        if (off > cone + (current ? STICKY : 0F) || dist > range) return Float.NaN;
        return off + (float) dist * NEAR_WEIGHT - (current ? STICKY : 0F);
    }

    /** Whether {@code a} beats {@code best} (NaN never wins; anything beats NaN). */
    public static boolean better(float a, float best) {
        return !Float.isNaN(a) && (Float.isNaN(best) || a < best);
    }

    /**
     * The view's turn this frame toward a creature {@code gap} degrees away (signed, wrapped to -180..180): eased, and
     * never more than {@link #TURN_MAX} a second, so a lock can't spin you round in one frame.
     */
    public static float turnStep(float gap, float seconds) {
        gap = Mth.wrapDegrees(gap);
        if (Math.abs(gap) < TURN_DEAD || seconds <= 0) return 0F;
        float eased = gap * (1F - (float) Math.exp(-TURN_EASE * seconds));
        float cap = TURN_MAX * seconds;
        return Mth.clamp(eased, -cap, cap);
    }

    /**
     * The mouse sensitivity setting that turns the view {@code keep} as far as {@code sens} does (vanilla cubes it:
     * {@code (s * 0.6 + 0.2)^3}).
     */
    public static double keepSensitivity(double sens, float keep) {
        double ss = sens * 0.6 + 0.2;
        return (ss * Math.cbrt(keep) - 0.2) / 0.6;
    }

    /** A flick's running sum: the old sum fades over {@link #FLICK_MS}, the new movement adds. */
    public static float flickSum(float sum, float dx, float ms) {
        return sum * (float) Math.exp(-ms / FLICK_MS) + dx;
    }

    /**
     * Which way a power's aim may bend toward a creature: the yaw and pitch (degrees) to send, the look turned toward
     * ({@code toYaw}, {@code toPitch}) by at most {@code cap}; unchanged when the creature lies further than {@code cap}
     * from the look (out: {@code [yaw, pitch]}).
     */
    public static void bend(float yaw, float pitch, float toYaw, float toPitch, float cap, float[] out) {
        float dy = Mth.wrapDegrees(toYaw - yaw), dp = toPitch - pitch;
        // the angle between the two looks, small-angle form is fine at these caps (the cos(pitch) squeeze on yaw kept)
        float cos = Mth.cos(pitch * Mth.DEG_TO_RAD);
        float off = (float) Math.sqrt(dy * cos * dy * cos + dp * dp);
        if (off > cap) {
            out[0] = yaw;
            out[1] = pitch;
            return;
        }
        out[0] = yaw + dy;
        out[1] = Mth.clamp(pitch + dp, -90F, 90F);
    }

    /**
     * The flick's pick (or the wheel's): among creatures at screen offsets ({@code sx[i]}, {@code sy[i]}) from the locked
     * one, the nearest whose offset points along ({@code dirX}, {@code dirY}) (within 60°). -1 when none does.
     */
    public static int pickSide(float[] sx, float[] sy, int n, float dirX, float dirY) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1.0E-4) return -1;
        dirX /= len;
        dirY /= len;
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float d = (float) Math.sqrt(sx[i] * sx[i] + sy[i] * sy[i]);
            if (d < 1.0E-3) continue;
            if ((sx[i] * dirX + sy[i] * dirY) / d < 0.5F) continue;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
