package dev.awaken.powers.fx;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Cast points the server fires from, in the same numbers the poses are authored to (SPEC 32, "Charged Shot"), so what the
 * hands hold is where the shot leaves, on the server and on every client.
 */
public final class CastPoints {
    private CastPoints() {}

    /**
     * The ball held between both hands (cast profile origin "ball"): this far along the look and this far along the view's
     * up from the eyes, centred. The first-person fists (tools/art/specs/fp_gestures.py BALL) put their palms' midpoint here
     * exactly; the third-person hold (power_gestures.py lightning_ball) puts it here at a level look, and within about a sixth of
     * a block at 20 degrees up or down (its arms turn at the shoulders, not the eyes).
     */
    public static final double BALL_FORWARD = 0.58, BALL_UP = -0.17;

    /** The ball's point for an entity's current look. */
    public static Vec3 ball(Entity e) {
        return ball(e.getEyePosition(), e.getLookAngle(), e.getUpVector(1f));
    }

    public static Vec3 ball(Vec3 eye, Vec3 look, Vec3 up) {
        return eye.add(look.scale(BALL_FORWARD)).add(up.scale(BALL_UP));
    }
}
