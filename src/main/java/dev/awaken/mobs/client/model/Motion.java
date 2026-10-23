package dev.awaken.mobs.client.model;

import dev.awaken.mobs.entity.AwLook;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The small shared motions of our hand-built bodies (lizard, snail, mushroom, marmot, moth, tortoise, scarab, ray,
 * serpent): a breathing idle, a flinch when struck, and head look toward what the creature watches. Gaits stay in each
 * model, driven by walkAnimationPos (distance travelled), so a stride covers ground at any speed.
 */
final class Motion {
    private Motion() {}

    private static final AwLook NONE = new AwLook();

    static AwLook look(LivingEntityRenderState s) {
        AwLook l = s.getRenderData(AwModels.LOOK);
        return l != null ? l : NONE;
    }

    /**
     * 0..1 flinch: sharp the moment it is hit, gone in half a second. Overworld creatures report their hurt time; for
     * the rest (gate serpents) a short shudder while the hurt tint shows.
     */
    static float flinch(LivingEntityRenderState s) {
        if (s.deathTime > 0) return 0.0F;
        float h = look(s).hurt;
        if (h > 0) return h * h;
        return s.hasRedOverlay ? 0.35F : 0.0F;
    }

    /** A fast side-to-side shake scaled by the flinch (in radians). */
    static float shudder(LivingEntityRenderState s, float amp) {
        float f = flinch(s);
        return f <= 0 ? 0.0F : Mth.sin(s.ageInTicks * 2.6F) * amp * f;
    }

    /** A breathing scale factor around 1, slowed and deepened while the creature is still. */
    static float breathe(LivingEntityRenderState s, float rate, float amp) {
        float still = 1.0F - Math.min(1.0F, s.walkAnimationSpeed * 2.0F);
        return 1.0F + Mth.sin(s.ageInTicks * rate) * amp * (0.5F + 0.5F * still);
    }

    /** Head yaw toward what it watches, in radians, weighted. */
    static float yaw(LivingEntityRenderState s, float w) {
        return Mth.clamp(s.yRot, -80.0F, 80.0F) * Mth.DEG_TO_RAD * w;
    }

    static float pitch(LivingEntityRenderState s, float w) {
        return Mth.clamp(s.xRot, -45.0F, 45.0F) * Mth.DEG_TO_RAD * w;
    }

    /** Gait amplitude 0..1 from the walk speed (eased in so slow shuffles stay small). */
    static float stride(LivingEntityRenderState s, float gain) {
        return Mth.clamp(s.walkAnimationSpeed * gain, 0.0F, 1.0F);
    }
}
