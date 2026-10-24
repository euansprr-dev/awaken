package dev.awaken.holo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Per-frame view facts the holo module needs, read once from the level's camera state: the current field of view
 * (from the real projection, so sprint and cast punches are compensated), the framebuffer size, the camera rotation,
 * and the inverse of vanilla's view bob and hurt tilt (vanilla folds them into the level projection; panels undo them
 * so they never bob). Preallocated; {@link #update} is called once a frame before anything is placed.
 */
final class ViewMath {
    private ViewMath() {}

    /** tan of half the vertical and horizontal field of view of the level projection. */
    static float tanY = 0.7f, tanX = 1.2f;
    /** tan of half the vertical field of view of the first-person hand projection. */
    static float tanYHand = 0.7f;
    static int fbW = 1, fbH = 1;
    /** View space → camera-relative world (the camera rotation). */
    static final Matrix4f VIEW_TO_WORLD = new Matrix4f();
    /** The inverse of vanilla's bob and hurt tilt. */
    static final Matrix4f UNBOB = new Matrix4f();
    /** Camera-relative world → the panels' (unbobbed) view space: the inverse of VIEW_TO_WORLD · UNBOB. */
    static final Matrix4f WORLD_TO_PANEL_VIEW = new Matrix4f();
    private static final Matrix4f BOB = new Matrix4f();

    static void update(CameraRenderState cam) {
        Minecraft mc = Minecraft.getInstance();
        fbW = Math.max(1, mc.getWindow().getWidth());
        fbH = Math.max(1, mc.getWindow().getHeight());
        Matrix4f p = cam.projectionMatrix;
        if (Math.abs(p.m11()) > 1e-4f) tanY = 1f / p.m11();
        if (Math.abs(p.m00()) > 1e-4f) tanX = 1f / p.m00();
        tanYHand = (float) Math.tan(Math.toRadians(cam.hudFov) * 0.5);
        VIEW_TO_WORLD.rotation(cam.orientation);
        bob(cam, mc);
        BOB.invert(UNBOB);
        WORLD_TO_PANEL_VIEW.set(VIEW_TO_WORLD).mul(UNBOB).invert();
    }

    /** The same transform as GameRenderer.bobHurt + bobView (both private there). */
    private static void bob(CameraRenderState cam, Minecraft mc) {
        BOB.identity();
        var er = cam.entityRenderState;
        if (er.isLiving) {
            if (er.isDeadOrDying) {
                float duration = Math.min(er.deathTime, 20f);
                BOB.rotateZ((float) Math.toRadians(40f - 8000f / (duration + 200f)));
            }
            float hurt = er.hurtTime;
            if (hurt >= 0 && !er.preventFlinch && er.hurtDuration > 0) {
                hurt /= er.hurtDuration;
                hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
                float rr = er.hurtDir;
                BOB.rotateY((float) Math.toRadians(-rr));
                BOB.rotateZ((float) Math.toRadians(-hurt * 14.0 * mc.options.damageTiltStrength().get()));
                BOB.rotateY((float) Math.toRadians(rr));
            }
        }
        if (mc.options.bobView().get() && er.isPlayer) {
            float w = er.backwardsInterpolatedWalkDistance, b = er.bob;
            BOB.translate(Mth.sin(w * (float) Math.PI) * b * 0.5f, -Math.abs(Mth.cos(w * (float) Math.PI) * b), 0f);
            BOB.rotateZ((float) Math.toRadians(Mth.sin(w * (float) Math.PI) * b * 3f));
            BOB.rotateX((float) Math.toRadians(Math.abs(Mth.cos(w * (float) Math.PI - 0.2f) * b) * 5f));
        }
    }

    /** Metres per framebuffer pixel at distance {@code d} in front of the eye. */
    static float metresPerPixel(float d) {
        return 2f * tanY * d / fbH;
    }
}
