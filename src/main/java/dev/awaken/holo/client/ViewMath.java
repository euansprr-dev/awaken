package dev.awaken.holo.client;

import dev.awaken.lens.client.WorldProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/**
 * Per-frame view facts the holo module needs, read once from the level's camera state: the current field of view
 * (from the real projection, so sprint and cast punches are compensated), the framebuffer size, the camera rotation,
 * and the inverse of the view bob and hurt tilt (vanilla folds them into the level projection; panels undo them so they
 * never bob), with the roll a camera mod adds to them (Camera Overhaul's, as the world's own projection held it a frame
 * or two ago: WorldProjection). Preallocated; {@link #update} is called once a frame before anything is placed.
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
    /** The inverse of the bob and hurt tilt (vanilla's, and a camera mod's roll before them). */
    static final Matrix4f UNBOB = new Matrix4f();
    /** Camera-relative world → the panels' (unbobbed) view space: the inverse of VIEW_TO_WORLD · UNBOB. */
    static final Matrix4f WORLD_TO_PANEL_VIEW = new Matrix4f();
    private static final Matrix4f BOB = new Matrix4f(), VANILLA = new Matrix4f();

    static void update(CameraRenderState cam) {
        Minecraft mc = Minecraft.getInstance();
        fbW = Math.max(1, mc.getWindow().getWidth());
        fbH = Math.max(1, mc.getWindow().getHeight());
        Matrix4f p = cam.projectionMatrix;
        if (Math.abs(p.m11()) > 1e-4f) tanY = 1f / p.m11();
        if (Math.abs(p.m00()) > 1e-4f) tanX = 1f / p.m00();
        tanYHand = (float) Math.tan(Math.toRadians(cam.hudFov) * 0.5);
        VIEW_TO_WORLD.rotation(cam.orientation);
        // a camera mod puts its roll at the start of the bob stack (Camera Overhaul: at the head of bobHurt); it's
        // measured only while a panel shows
        for (HoloRender.Draw d : HoloClient.draws()) {
            if (HoloRender.visible(d.p)) {
                WorldProjection.wantRoll();
                break;
            }
        }
        BOB.rotationZ(WorldProjection.roll()).mul(WorldProjection.vanillaBob(cam, VANILLA));
        BOB.invert(UNBOB);
        WORLD_TO_PANEL_VIEW.set(VIEW_TO_WORLD).mul(UNBOB).invert();
    }

    /** Metres per framebuffer pixel at distance {@code d} in front of the eye. */
    static float metresPerPixel(float d) {
        return 2f * tanY * d / fbH;
    }
}
