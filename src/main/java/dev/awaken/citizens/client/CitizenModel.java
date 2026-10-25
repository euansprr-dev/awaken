package dev.awaken.citizens.client;

import dev.awaken.core.client.skin.SkinModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import static dev.awaken.citizens.client.CitizenClips.*;

/**
 * The player mesh posed by a citizen's clip after vanilla's own pose (walk, look, held items): each channel the clip
 * sets replaces vanilla's value, crossfaded from the previous clip; channels it leaves alone keep vanilla's, so an
 * arm-only clip (a crate on the back, a halberd) plays over the walk.
 */
final class CitizenModel extends SkinModel<CitizenRenderState> {
    private static final float R = Mth.DEG_TO_RAD;

    CitizenModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(CitizenRenderState s) {
        super.setupAnim(s);
        if (!s.hasPose && !s.hasPrev) return;
        float w = s.hasPose ? s.blend : 0f;
        // the head keeps the look: the clip adds its tilt to it
        head.xRot += mix(s, HEAD_X, 0f, w) * R;
        head.zRot = mixAbs(s, HEAD_Z, head.zRot, w);
        body.yRot = mixAbs(s, TORSO_Y, body.yRot, w);
        arm(s, rightArm, RA_X, w, -5f);
        arm(s, leftArm, LA_X, w, 5f);
        leg(s, rightLeg, RL_X, w);
        leg(s, leftLeg, LL_X, w);
    }

    private static void arm(CitizenRenderState s, ModelPart p, int c, float w, float pivotX) {
        p.xRot = mixAbs(s, c, p.xRot, w);
        p.yRot = mixAbs(s, c + 1, p.yRot, w);
        p.zRot = mixAbs(s, c + 2, p.zRot, w);
        p.x += mix(s, c + 3, 0f, w);
        p.z += mix(s, c + 4, 0f, w);
    }

    private static void leg(CitizenRenderState s, ModelPart p, int c, float w) {
        p.xRot = mixAbs(s, c, p.xRot, w);
        p.yRot = mixAbs(s, c + 1, p.yRot, w);
        p.zRot = mixAbs(s, c + 2, p.zRot, w);
    }

    /** An angle channel (degrees in the clip) blended over the vanilla value (radians). */
    private static float mixAbs(CitizenRenderState s, int c, float vanilla, float w) {
        float cur = s.hasPose ? s.pose[c] : Float.NaN, prev = s.hasPrev ? s.prevPose[c] : Float.NaN;
        float a = Float.isNaN(prev) ? vanilla : prev * R, b = Float.isNaN(cur) ? vanilla : cur * R;
        return a + (b - a) * w;
    }

    /** An additive channel (pixels or degrees as given), 0 where unset. */
    static float mix(CitizenRenderState s, int c, float zero, float w) {
        float cur = s.hasPose ? s.pose[c] : Float.NaN, prev = s.hasPrev ? s.prevPose[c] : Float.NaN;
        float a = Float.isNaN(prev) ? zero : prev, b = Float.isNaN(cur) ? zero : cur;
        return a + (b - a) * w;
    }
}
