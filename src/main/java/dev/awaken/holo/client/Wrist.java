package dev.awaken.holo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The first-person wrist (docs/proposals/holo-ui.md §6). While a holo menu is open or animating, a HIGH-priority
 * {@link RenderHandEvent} listener cancels both vanilla hands (so CastAnims at LOW and Lucidium's reality check at
 * NORMAL don't run: NeoForge skips cancelled events for listeners that don't ask for them) and draws instead:
 * <ul>
 *   <li>the off-hand forearm coming in from the lower left (the elbow off-screen), the inside of the wrist turned to
 *       the eye, with the player's own skin and sleeve (wide or slim model, whatever the renderer is);</li>
 *   <li>the System's band, flush with the forearm: a thin dark strip, two emissive lines, segment marks with a slow
 *       travelling pulse and the System's diamond on the inside of the wrist; it flares as the panel leaves it;</li>
 *   <li>the main hand (or the item in it) sinking out of view the way vanilla lowers it on an item swap.</li>
 * </ul>
 * {@link #emitter} is where the panel starts its unfold.
 */
final class Wrist {
    private Wrist() {}

    // the raised forearm in hand-view space (metres, x right, y up, -z ahead), for the left arm; mirrored for the right:
    // the wrist in the lower left, the forearm running in from beyond the left edge toward the centre
    private static final float WX = -0.425f, WY = -0.30f, WZ = -0.62f;
    private static final Vector3f DIR = new Vector3f(0.80f, 0.34f, -0.50f).normalize(); // elbow → hand
    private static final Vector3f FACE = new Vector3f(0.12f, 0.50f, 0.86f);             // where the inside of the wrist looks
    /** A little smaller than vanilla's first-person arm, so the forearm reads as a forearm at this distance. */
    private static final float SCALE = 0.64f;
    /** The band along the arm (model units from the shoulder pivot; the hand ends at 10), and how far it sits off the
     *  skin (just over the sleeve layer, so it reads as flush). */
    private static final float BAND_Y0 = 7.15f, BAND_Y1 = 8.15f, PAD = 0.30f;

    private static final Matrix4f M = new Matrix4f(), CANCEL = new Matrix4f(), BASIS4 = new Matrix4f();
    private static final Matrix3f BASIS = new Matrix3f();
    private static final Vector3f X = new Vector3f(), Y = new Vector3f(), Z = new Vector3f(), T = new Vector3f(), TMP = new Vector3f();
    private static long drawnFrame = -1;
    /** Autopilot: draw the arm with this skin (wide or slim) instead of the player's. */
    static PlayerSkin forcedSkin;
    private static float glow, flare;
    private static float armX0, armX1; // the arm's cross-section in model units (wide or slim)
    private static int inner;          // the inner face's side in model x: -1 on the left arm, +1 on the right
    private static float seconds;      // for the idle pulse
    private static final SubmitNodeCollector.CustomGeometryRenderer STRAP = Wrist::strap, LIGHT = Wrist::light;

    /** The wrist's centre in hand-view space for a raise of {@code a} (0 down, 1 up) on {@code side} (-1 left, 1 right). */
    static void band(float a, int side, Vector3f out) {
        float down = 1 - a;
        out.set(side < 0 ? WX : -WX, WY - 0.55f * down, WZ + 0.10f * down);
    }

    /** On the inside of the wrist, where the beams start and the panel grows from (hand-view space). */
    static void emitter(float a, int side, Vector3f out) {
        band(a, side, out);
        out.add(side < 0 ? FACE.x * 0.115f : -FACE.x * 0.115f, FACE.y * 0.115f, FACE.z * 0.115f);
    }

    /**
     * Builds the forearm's transform into {@link #M}: blocks (what a ModelPart renders in; its cubes divide by 16
     * themselves) → hand-view metres, for a raise of {@code a}.
     */
    private static void armMatrix(float a, int side, boolean slim) {
        // basis: model +Y runs elbow → hand, the inner face (-X on the left arm, +X on the right) looks toward FACE
        Y.set(side < 0 ? DIR.x : -DIR.x, DIR.y, DIR.z);
        T.set(side < 0 ? FACE.x : -FACE.x, FACE.y, FACE.z);
        X.set(T).sub(TMP.set(Y).mul(T.dot(Y))).normalize().negate();
        if (side > 0) X.negate();
        Z.set(X).cross(Y);
        BASIS.set(X, Y, Z);
        float down = 1 - a;
        float cx = slim ? (side < 0 ? 0.5f : -0.5f) : (side < 0 ? 1.0f : -1.0f);
        band(a, side, T);
        M.identity().translate(T);
        M.rotate(Axis.XP.rotationDegrees(-38f * down)); // lowered: sunk below the view and tilted down
        M.mul(BASIS4.set(BASIS));
        M.scale(SCALE);
        M.translate(-cx / 16f, -(BAND_Y0 + BAND_Y1) / 32f, 0);
    }

    /** RenderHandEvent at HIGH: replaces both hands while a holo menu is open or animating. */
    static void onHand(RenderHandEvent e) {
        HoloPanel p = HoloClient.wristPanel();
        if (p == null || !p.timeline.active() || p.anchor != HoloPanel.Anchor.WRIST) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        long frame = HoloClient.frame();
        if (frame == drawnFrame) return; // both hands fire; draw once
        drawnFrame = frame;
        PlayerRenderState ps = e.getPlayerState();
        AvatarRenderState avatar = ps.avatarRenderState;
        if (avatar == null || avatar.isInvisible) return;
        if (forcedSkin != null) avatar.skin = forcedSkin;
        AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
        HoloTimeline t = p.timeline;
        float a = Math.max(0, Math.min(1.1f, t.arm()));
        HumanoidArm wristArm = avatar.mainArm.getOpposite();
        int side = wristArm == HumanoidArm.LEFT ? -1 : 1;
        boolean slim = avatar.skin.model() == PlayerModelType.SLIM;
        int light = e.getPackedLight();
        Identifier skin = avatar.skin.body().texturePath();
        PoseStack pose = e.getPoseStack();

        // the forearm
        armMatrix(a, side, slim);
        ModelPart arm = side < 0 ? renderer.getModel().leftArm : renderer.getModel().rightArm;
        PartPose ip = arm.getInitialPose();
        float zRot = side < 0 ? -0.1f : 0.1f; // what AvatarRenderer.renderHand sets
        CANCEL.identity().rotateZ(-zRot).translate(-ip.x() / 16f, -ip.y() / 16f, -ip.z() / 16f);
        pose.pushPose();
        pose.mulPose(M);
        pose.mulPose(CANCEL);
        if (side < 0) renderer.renderLeftHand(pose, e.getSubmitNodeCollector(), light, skin, avatar.showLeftSleeve);
        else renderer.renderRightHand(pose, e.getSubmitNodeCollector(), light, skin, avatar.showRightSleeve);
        pose.popPose();

        // the device, in the arm's model units; the strap after the arm, the light after the strap
        armX0 = slim ? (side < 0 ? -1f : -2f) : (side < 0 ? -1f : -3f);
        armX1 = slim ? (side < 0 ? 2f : 1f) : (side < 0 ? 3f : 1f);
        inner = side < 0 ? -1 : 1;
        seconds = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
        float b = t.band();
        glow = Math.min(1f, b) * Math.min(1f, a * 1.2f);
        flare = Math.max(0f, b - 1f) * Math.min(1f, a * 1.2f);
        pose.pushPose();
        pose.mulPose(M);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        e.getSubmitNodeCollector().order(1).submitCustomGeometry(pose, HoloPipelines.glass(), STRAP);
        if (glow + flare > 0.01f) e.getSubmitNodeCollector().order(2).submitCustomGeometry(pose, HoloPipelines.glow(), LIGHT);
        pose.popPose();

        // the main hand sinks out of view (vanilla's own transforms, at a lowered height)
        float drop = Math.min(1f, a);
        if (drop < 0.999f) mainHand(e, ps, avatar, renderer, -side, drop, light, skin);
    }

    private static void mainHand(RenderHandEvent e, PlayerRenderState ps, AvatarRenderState avatar, AvatarRenderer<?> renderer,
            int side, float drop, int light, Identifier skin) {
        PoseStack pose = e.getPoseStack();
        float invert = side > 0 ? 1f : -1f;
        pose.pushPose();
        if (ps.firstPersonHandsAndItems.mainHandItem.isEmpty()) {
            pose.translate(invert * 0.64000005F, -0.6F + drop * -0.6F, -0.71999997F);
            pose.rotateDegrees(Axis.YP, invert * 45.0F);
            pose.translate(invert * -1.0F, 3.6F, 3.5F);
            pose.rotateDegrees(Axis.ZP, invert * 120.0F);
            pose.rotateDegrees(Axis.XP, 200.0F);
            pose.rotateDegrees(Axis.YP, invert * -135.0F);
            pose.translate(invert * 5.6F, 0.0F, 0.0F);
            if (side > 0) renderer.renderRightHand(pose, e.getSubmitNodeCollector(), light, skin, avatar.showRightSleeve);
            else renderer.renderLeftHand(pose, e.getSubmitNodeCollector(), light, skin, avatar.showLeftSleeve);
        } else {
            pose.translate(invert * 0.56F, -0.52F + drop * -0.6F, -0.72F);
            ps.firstPersonHandsAndItems.mainHandRenderState.submit(pose, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }

    // ---- the band ---------------------------------------------------------------------------------------------------

    private static final Vector3f FN = new Vector3f(), FC = new Vector3f();

    /** Whether the face with this model-space normal and centre looks at the eye (the hand pass gives us no depth). */
    private static boolean facing(Matrix4f m, float nx, float nz, float cx, float cy, float cz) {
        m.transformDirection(FN.set(nx, 0, nz));
        m.transformPosition(FC.set(cx, cy, cz));
        return FN.dot(FC) < 0;
    }

    /**
     * The band's four faces, just over the skin: {@code (axis, k)} is the face's plane (axis 0: x = k, its width runs in
     * z; axis 2: z = k, its width runs in x), {@code a0..a1} its width, {@code n} the outward normal's sign.
     */
    private static float[] faces() {
        float x0 = armX0 - PAD, x1 = armX1 + PAD, z0 = -2f - PAD, z1 = 2f + PAD;
        float xi = inner < 0 ? x0 : x1, xo = inner < 0 ? x1 : x0;
        F[0] = 0; F[1] = xi; F[2] = z0; F[3] = z1; F[4] = inner;  // inside of the wrist
        F[5] = 0; F[6] = xo; F[7] = z0; F[8] = z1; F[9] = -inner; // back of the wrist
        F[10] = 2; F[11] = z0; F[12] = x0; F[13] = x1; F[14] = -1;
        F[15] = 2; F[16] = z1; F[17] = x0; F[18] = x1; F[19] = 1;
        return F;
    }

    private static final float[] F = new float[20];

    private static boolean visible(Matrix4f m, float[] f, int i, float ym) {
        int o = i * 5;
        float mid = (f[o + 2] + f[o + 3]) / 2;
        return f[o] == 0 ? facing(m, f[o + 4], 0, f[o + 1], ym, mid) : facing(m, 0, f[o + 4], mid, ym, f[o + 1]);
    }

    /** A quad on face {@code i} spanning width {@code a0..a1} and arm length {@code y0..y1}, lifted {@code lift} off it. */
    private static void onFace(VertexConsumer vc, Matrix4f m, float[] f, int i, float a0, float a1, float y0, float y1, float lift,
            float u0, float v0, float u1, float v1, int colour, int shape) {
        int o = i * 5;
        float k = f[o + 1] + f[o + 4] * lift;
        if (f[o] == 0) {
            vc.addVertex(m, k, y0, a0).setUv(u0, v0).setColor(colour).setUv2(shape, 0);
            vc.addVertex(m, k, y1, a0).setUv(u0, v1).setColor(colour).setUv2(shape, 0);
            vc.addVertex(m, k, y1, a1).setUv(u1, v1).setColor(colour).setUv2(shape, 0);
            vc.addVertex(m, k, y0, a1).setUv(u1, v0).setColor(colour).setUv2(shape, 0);
        } else {
            vc.addVertex(m, a0, y0, k).setUv(u0, v0).setColor(colour).setUv2(shape, 0);
            vc.addVertex(m, a0, y1, k).setUv(u0, v1).setColor(colour).setUv2(shape, 0);
            vc.addVertex(m, a1, y1, k).setUv(u1, v1).setColor(colour).setUv2(shape, 0);
            vc.addVertex(m, a1, y0, k).setUv(u1, v0).setColor(colour).setUv2(shape, 0);
        }
    }

    /** The band's body: a thin dark strip over the skin on the faces that show. */
    private static void strap(PoseStack.Pose pose, VertexConsumer vc) {
        Matrix4f m = pose.pose();
        float[] f = faces();
        float ym = (BAND_Y0 + BAND_Y1) / 2;
        for (int i = 0; i < 4; i++) {
            if (!visible(m, f, i, ym)) continue;
            int o = i * 5;
            onFace(vc, m, f, i, f[o + 2], f[o + 3], BAND_Y0, BAND_Y1, 0.02f, 0, 0, 0, 0, 0x7A081222, 0);
        }
    }

    /**
     * The band's light: two emissive lines along its edges, segment marks on the inside of the wrist with a pulse that
     * travels across them, the System's diamond between them, and a bloom while the panel leaves.
     */
    private static void light(PoseStack.Pose pose, VertexConsumer vc) {
        Matrix4f m = pose.pose();
        float[] f = faces();
        float y0 = BAND_Y0, y1 = BAND_Y1, ym = (y0 + y1) / 2;
        float breathe = 0.82f + 0.18f * (float) Math.sin(seconds * Math.PI * 2 / 2.6);
        float k = glow * breathe + flare * 0.8f;
        int line = Glass.a(Glass.EDGE, Math.min(1f, 0.75f * k)), hot = Glass.a(0xD8EEFF, Math.min(1f, 0.45f * k));
        float th = 0.12f;
        for (int i = 0; i < 4; i++) {
            if (!visible(m, f, i, ym)) continue;
            int o = i * 5;
            float a0 = f[o + 2], a1 = f[o + 3];
            onFace(vc, m, f, i, a0, a1, y0 - th, y0 + th, 0.05f, -0.8f, -1, 0.8f, 1, line, -2);
            onFace(vc, m, f, i, a0, a1, y1 - th, y1 + th, 0.05f, -0.8f, -1, 0.8f, 1, line, -2);
        }
        if (visible(m, f, 0, ym)) {
            float a0 = f[2], a1 = f[3], mid = (a0 + a1) / 2, half = (a1 - a0) / 2;
            // small segment nodes either side of the diamond, a pulse sweeping out across them
            int n = 3;
            float phase = (seconds * 1.4f) % 4.5f - 0.75f;
            for (int side = -1; side <= 1; side += 2) {
                for (int j = 0; j < n; j++) {
                    float c = mid + side * (0.95f + j * 0.62f) * (half / 2.4f);
                    float d = j - phase;
                    float pulse = 0.25f + 0.75f * (float) Math.exp(-d * d * 1.6f);
                    int col = Glass.a(Glass.EDGE, Math.min(1f, 0.95f * k * pulse));
                    onFace(vc, m, f, 0, c - 0.13f, c + 0.13f, ym - 0.13f, ym + 0.13f, 0.06f, -1, -1, 1, 1, col, -1);
                }
            }
            // the diamond: four short strokes
            float r = 0.28f, w = 0.06f;
            diamondStroke(vc, m, f, mid, ym, r, w, 1, 1, hot);
            diamondStroke(vc, m, f, mid, ym, r, w, 1, -1, hot);
            diamondStroke(vc, m, f, mid, ym, r, w, -1, 1, hot);
            diamondStroke(vc, m, f, mid, ym, r, w, -1, -1, hot);
            onFace(vc, m, f, 0, mid - 0.9f, mid + 0.9f, ym - 0.9f, ym + 0.9f, 0.07f, -1, -1, 1, 1,
                    Glass.a(Glass.EDGE, Math.min(1f, 0.16f * k)), -1);
            if (flare > 0.01f) {
                onFace(vc, m, f, 0, mid - 2.4f, mid + 2.4f, ym - 2.4f, ym + 2.4f, 0.08f, -1, -1, 1, 1,
                        Glass.a(Glass.EDGE, Math.min(1f, 0.16f * flare)), -1);
            }
        }
    }

    /** One stroke of the diamond on the inside face: from its centre's (sa, sy) quadrant edge midpoint, as a thin quad. */
    private static void diamondStroke(VertexConsumer vc, Matrix4f m, float[] f, float cx, float cy, float r, float w, int sa, int sy, int c) {
        // the stroke runs from (cx + sa·r, cy) to (cx, cy + sy·r); widen it along its normal in the face plane
        float ax = cx + sa * r, ay = cy, bx = cx, by = cy + sy * r;
        float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / len * w, ny = dx / len * w;
        float k = f[1] + f[4] * 0.07f;
        vc.addVertex(m, k, ay - ny, ax - nx).setUv(-0.8f, -1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, k, by - ny, bx - nx).setUv(0.8f, -1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, k, by + ny, bx + nx).setUv(0.8f, 1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, k, ay + ny, ax + nx).setUv(-0.8f, 1).setColor(c).setUv2(-2, 0);
    }
}
