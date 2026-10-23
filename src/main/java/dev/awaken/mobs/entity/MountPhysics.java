package dev.awaken.mobs.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The riding numbers as pure functions (the GameTests drive them directly; the rider's own client runs them when it
 * moves the mount, the server when nobody rides). Inputs are the rider's strafe and forward keys (-1..1); speeds are
 * blocks per tick before the move's friction.
 */
public final class MountPhysics {
    private MountPhysics() {}

    /** Hover height of a gliding mount above the ground under it (blocks). */
    public static final double HOVER = 1.1;
    /** A glider never sinks faster than this (blocks per tick), and drifts down at this rate once above its hover. */
    public static final double GLIDE_SINK = 0.06, GLIDE_DRIFT = 0.025;
    /** Ticks between two jumps or dashes. */
    public static final int JUMP_COOLDOWN = 24;

    /** Land: half speed sideways, a quarter backwards (the horse's rule). */
    public static Vec3 groundInput(float strafe, float forward) {
        return new Vec3(strafe * 0.5F, 0.0, forward <= 0.0F ? forward * 0.25F : forward);
    }

    /** In water you swim where you look: forward splits into forward and up by the rider's pitch (the nautilus rule). */
    public static Vec3 swimInput(float strafe, float forward, float pitchDegrees) {
        if (forward == 0.0F) return new Vec3(strafe, 0.0, 0.0);
        float f = Mth.cos(pitchDegrees * Mth.DEG_TO_RAD), up = -Mth.sin(pitchDegrees * Mth.DEG_TO_RAD);
        if (forward < 0.0F) {
            f *= -0.5F;
            up *= -0.5F;
        }
        return new Vec3(strafe, up, f);
    }

    /** Ridden speed: the creature's speed attribute times its kind's factor (water: the nautilus scale). */
    public static float rideSpeed(Fauna3Kinds.Style style, double attribute, float factor, boolean inWater) {
        return switch (style) {
            case GROUND -> (float) attribute * factor * (inWater ? 0.5F : 1.0F);
            case GLIDE -> (float) attribute * factor;
            case SWIM -> (float) attribute * factor * (inWater ? 0.03F : 0.012F);
        };
    }

    /**
     * A glider's vertical speed this tick: below its hover height it rises gently to it (it never touches the
     * ground), above it drifts down slowly whatever a jump gave it; never sinks faster than {@link #GLIDE_SINK}.
     */
    public static double glideVertical(double above, double vy) {
        if (above < HOVER) return Math.min(0.18, Math.max(vy, 0.0) * 0.6 + (HOVER - above) * 0.12);
        if (vy > 0.0) return vy - 0.035; // the end of a soar: lift bleeds away
        return Math.max(-GLIDE_SINK, Math.min(vy, -GLIDE_DRIFT) - 0.004);
    }

    /** The upward push of a charged jump (0..1 charge): a stag clears 3-4 blocks, a ray soars 6-8. */
    public static double jumpImpulse(Fauna3Kinds.Style style, float charge, float factor) {
        float c = Mth.clamp(charge, 0.0F, 1.0F);
        return switch (style) {
            case GROUND -> (0.42 + 0.48 * c) * factor;
            case GLIDE -> (0.45 + 0.75 * c) * factor;
            case SWIM -> 0.35 + 0.35 * c;
        };
    }

    /** A swimmer's dash along the look direction (blocks per tick added); at the surface it leaps out instead. */
    public static Vec3 dash(Vec3 look, float charge, double attribute, boolean inWater, boolean atSurface) {
        float c = Mth.clamp(charge, 0.0F, 1.0F);
        double push = (inWater ? 1.2 : 0.4) * c * attribute * 0.5;
        Vec3 d = look.normalize().scale(push);
        if (atSurface) d = new Vec3(d.x, Math.max(d.y, 0.0) + jumpImpulse(Fauna3Kinds.Style.SWIM, c, 1.0F), d.z);
        return d;
    }
}
