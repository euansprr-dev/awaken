package dev.awaken.mobs.client.life;

import net.minecraft.util.Mth;

/**
 * The math of flight and swimming (SPEC §23, "Flight"), apart from the frame state so GameTests check it on the server:
 * the wingbeat's shape and pace, how the body banks, pitches and rides its stroke, when a flier glides, how a spine
 * curves into a turn. Model space: +x the body's left, +y down, -z forward; a positive pitch noses down, a positive
 * roll tips the top toward +x.
 */
public final class Flight {
    private Flight() {}

    /** The share of a beat the downstroke takes: the wings sweep down hard and come up slower. */
    public static final float DOWN = 0.4F;

    /** How far (radians) the wings' height above their mean counts toward the heave (a wing spreading is not a stroke). */
    static final float STROKE_MAX = 0.8F;

    /** The wings' height through a beat ({@code u} in cycles): 1 at the top, -1 at the bottom; down fast, up slow. */
    public static float stroke(float u) {
        u -= Mth.floor(u);
        return u < DOWN ? Mth.cos(Mth.PI * u / DOWN) : -Mth.cos(Mth.PI * (u - DOWN) / (1.0F - DOWN));
    }

    /** The beat's pace (times its own): quicker climbing and hovering, slower in a cruise. */
    public static float pace(float climb, float cruise) {
        return 1.15F - 0.35F * cruise + 0.4F * Mth.clamp(climb, 0.0F, 1.0F);
    }

    /** The path's climb (radians, + up) from its speed over the ground and up (blocks a second). */
    public static float climb(float ground, float up) {
        return Mth.clamp((float) Mth.atan2(up, Math.max(ground, 0.3F)), -1.2F, 1.2F);
    }

    /** Pitch (radians, + nose down): it noses into a cruise, up a climb and down a dive. */
    public static float pitch(float climb, float cruise, float k, float tilt) {
        return Mth.clamp(tilt * cruise - climb * k, -1.0F, 1.0F);
    }

    /** Roll (radians) into a turn: a turn to its right ({@code yawRate} > 0, radians a second) tips it right (-x). */
    public static float bank(float yawRate, float cruise, float k) {
        return Mth.clamp(-yawRate * cruise * k, -0.7F, 0.7F);
    }

    /**
     * The share of its stroke a flier keeps (1 a full beat): all of it climbing or hovering, the glide's share diving,
     * and in a cruise it beats and glides by turns ({@code lull} 0..1, a slow wave of its own).
     */
    public static float keep(float climb, float cruise, float glide, float lull) {
        float dive = Mth.clamp(-climb / 0.35F, 0.0F, 1.0F), rise = Mth.clamp(climb / 0.2F, 0.0F, 1.0F);
        float coast = Math.max(dive, Mth.clamp((cruise - 0.4F) / 0.4F, 0.0F, 1.0F) * lull) * (1.0F - rise);
        return Mth.lerp(coast, 1.0F, glide);
    }

    /** The body's rise (px, + down like a dip) with its wings {@code e} radians above their mean: wings low, body high. */
    public static float heave(float e, float k) {
        return Mth.clamp(e, -STROKE_MAX, STROKE_MAX) * k;
    }

    /** The spine's whole curve (radians of yaw, + tail toward +x) into a turn: the tail follows the path, inside it. */
    public static float bend(float yawRate, float k) {
        return Mth.clamp(-yawRate * k, -0.9F, 0.9F);
    }
}
