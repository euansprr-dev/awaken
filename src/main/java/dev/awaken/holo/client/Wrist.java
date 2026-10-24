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
 *   <li>the off-hand forearm raised in from the lower left, foreshortened, the back of the wrist turned up toward the
 *       eye, with the player's own skin and sleeve (wide or slim model, whatever the renderer is);</li>
 *   <li>the System's wrist device: a dark strap whose top is a tiny glass face (the panel shader: hairline edge,
 *       brackets), glowing seams, an emitter that flares as the panel lifts off it;</li>
 *   <li>the main hand (or the item in it) sinking out of view the way vanilla lowers it on an item swap.</li>
 * </ul>
 * {@link #emitter} is where the panel starts its unfold.
 */
final class Wrist {
    private Wrist() {}

    // the raised forearm in hand-view space (metres, x right, y up, -z ahead), for the left arm; mirrored for the right
    private static final float WX = -0.19f, WY = -0.335f, WZ = -0.66f;
    private static final Vector3f DIR = new Vector3f(0.60f, 0.24f, -0.76f).normalize(); // elbow → hand
    private static final Vector3f FACE = new Vector3f(0.05f, 0.80f, 0.60f);            // where the back of the wrist looks
    /** A little smaller than vanilla's first-person arm, so the forearm reads as a forearm at this distance. */
    private static final float SCALE = 0.80f;
    /** The device along the arm (model units from the shoulder pivot; the hand ends at 10). */
    private static final float BAND_Y0 = 6.9f, BAND_Y1 = 8.4f, PAD = 0.32f;

    private static final Matrix4f M = new Matrix4f(), CANCEL = new Matrix4f(), BASIS4 = new Matrix4f();
    private static final Matrix3f BASIS = new Matrix3f();
    private static final Vector3f X = new Vector3f(), Y = new Vector3f(), Z = new Vector3f(), T = new Vector3f(), TMP = new Vector3f();
    private static long drawnFrame = -1;
    /** Autopilot: draw the arm with this skin (wide or slim) instead of the player's. */
    static PlayerSkin forcedSkin;
    private static float glow, flare;
    private static float armX0, armX1; // the arm's cross-section in model units (wide or slim)
    private static int outer;          // the outer face's side in model x: +1 on the left arm, -1 on the right
    private static final SubmitNodeCollector.CustomGeometryRenderer STRAP = Wrist::strap, LIGHT = Wrist::light;

    /** The wrist's centre in hand-view space for a raise of {@code a} (0 down, 1 up) on {@code side} (-1 left, 1 right). */
    static void band(float a, int side, Vector3f out) {
        float down = 1 - a;
        out.set(side < 0 ? WX : -WX, WY - 0.55f * down, WZ + 0.10f * down);
    }

    /** Just above the device's face, where the panel lifts off (hand-view space). */
    static void emitter(float a, int side, Vector3f out) {
        band(a, side, out);
        out.add(side < 0 ? FACE.x * 0.16f : -FACE.x * 0.16f, FACE.y * 0.16f, FACE.z * 0.16f);
    }

    /**
     * Builds the forearm's transform into {@link #M}: blocks (what a ModelPart renders in; its cubes divide by 16
     * themselves) → hand-view metres, for a raise of {@code a}.
     */
    private static void armMatrix(float a, int side, boolean slim) {
        // basis: model +Y runs elbow → hand, the outer face (+X on the left arm, -X on the right) looks toward FACE
        Y.set(side < 0 ? DIR.x : -DIR.x, DIR.y, DIR.z);
        T.set(side < 0 ? FACE.x : -FACE.x, FACE.y, FACE.z);
        X.set(T).sub(TMP.set(Y).mul(T.dot(Y))).normalize();
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
        outer = side < 0 ? 1 : -1;
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

    // ---- the device -------------------------------------------------------------------------------------------------

    private static final Vector3f FN = new Vector3f(), FC = new Vector3f();

    /** Whether the face with this model-space normal and centre looks at the eye (the hand pass has no depth for us). */
    private static boolean facing(Matrix4f m, float nx, float nz, float cx, float cy, float cz) {
        m.transformDirection(FN.set(nx, 0, nz));
        m.transformPosition(FC.set(cx, cy, cz));
        return FN.dot(FC) < 0;
    }

    /** The strap: dark sides, and on the back of the wrist a small glass face drawn by the panel shader. */
    private static void strap(PoseStack.Pose pose, VertexConsumer vc) {
        Matrix4f m = pose.pose();
        float x0 = armX0 - PAD, x1 = armX1 + PAD, z0 = -2f - PAD, z1 = 2f + PAD, y0 = BAND_Y0, y1 = BAND_Y1, ym = (y0 + y1) / 2;
        int dark = 0xF00A111C, darker = 0xF0060A12;
        float xo = outer > 0 ? x1 : x0, xi = outer > 0 ? x0 : x1;
        // the face: UV in quarter units so the shader's integer size keeps its proportions
        if (facing(m, outer, 0, xo, ym, 0)) {
            int w = Math.round((z1 - z0) * 4), h = Math.round((y1 - y0) * 4);
            int c = 0xFF000000 | (Glass.EDGE & 0xFFFFFF);
            vc.addVertex(m, xo, y0, z0).setUv(0, 0).setColor(c).setUv2(w, h);
            vc.addVertex(m, xo, y1, z0).setUv(0, h).setColor(c).setUv2(w, h);
            vc.addVertex(m, xo, y1, z1).setUv(w, h).setColor(c).setUv2(w, h);
            vc.addVertex(m, xo, y0, z1).setUv(w, 0).setColor(c).setUv2(w, h);
        }
        side(vc, m, xi, z0, xi, z1, y0, y1, -outer, 0, darker);
        side(vc, m, x0, z0, x1, z0, y0, y1, 0, -1, dark);
        side(vc, m, x0, z1, x1, z1, y0, y1, 0, 1, dark);
    }

    private static void side(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float y0, float y1, float nx, float nz, int colour) {
        if (!facing(m, nx, nz, (ax + bx) / 2, (y0 + y1) / 2, (az + bz) / 2)) return;
        vc.addVertex(m, ax, y0, az).setUv(0, 0).setColor(colour).setUv2(0, 0);
        vc.addVertex(m, ax, y1, az).setUv(0, 0).setColor(colour).setUv2(0, 0);
        vc.addVertex(m, bx, y1, bz).setUv(0, 0).setColor(colour).setUv2(0, 0);
        vc.addVertex(m, bx, y0, bz).setUv(0, 0).setColor(colour).setUv2(0, 0);
    }

    /** Seams of light along the strap's edges, the emitter on its face, and the flare as the panel lifts off. */
    private static void light(PoseStack.Pose pose, VertexConsumer vc) {
        Matrix4f m = pose.pose();
        float x0 = armX0 - PAD, x1 = armX1 + PAD, z0 = -2f - PAD, z1 = 2f + PAD, y0 = BAND_Y0, y1 = BAND_Y1, ym = (y0 + y1) / 2;
        float xo = outer > 0 ? x1 + 0.03f : x0 - 0.03f;
        int seam = Glass.a(Glass.EDGE, Math.min(1f, 0.55f * glow + 0.3f * flare));
        float th = 0.22f;
        // seams on the visible faces (outer, front, back)
        if (facing(m, outer, 0, xo, ym, 0)) {
            seamX(vc, m, xo, z0, z1, y0, th, seam);
            seamX(vc, m, xo, z0, z1, y1, th, seam);
        }
        if (facing(m, 0, -1, (x0 + x1) / 2, ym, z0)) {
            seamZ(vc, m, x0, x1, z0 - 0.03f, y0, th, seam);
            seamZ(vc, m, x0, x1, z0 - 0.03f, y1, th, seam);
        }
        if (facing(m, 0, 1, (x0 + x1) / 2, ym, z1)) {
            seamZ(vc, m, x0, x1, z1 + 0.03f, y0, th, seam);
            seamZ(vc, m, x0, x1, z1 + 0.03f, y1, th, seam);
        }
        // the emitter: a bright node on the face, a soft bloom, and the flare
        float ox = xo + outer * 0.05f;
        blob(vc, m, ox, ym, 0, 0.55f, Glass.a(0xE6F4FF, Math.min(1f, 0.85f * glow + 0.4f * flare)));
        blob(vc, m, ox, ym, 0, 2.4f, Glass.a(Glass.EDGE, Math.min(1f, 0.16f * glow + 0.22f * flare)));
        if (flare > 0.01f) blob(vc, m, ox + outer * 0.2f, ym, 0, 6.5f, Glass.a(Glass.EDGE, Math.min(1f, 0.16f * flare)));
    }

    /** A seam along the outer face (it runs in z at height y). */
    private static void seamX(VertexConsumer vc, Matrix4f m, float x, float z0, float z1, float y, float th, int c) {
        vc.addVertex(m, x, y - th, z0).setUv(-0.8f, -1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, x, y - th, z1).setUv(0.8f, -1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, x, y + th, z1).setUv(0.8f, 1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, x, y + th, z0).setUv(-0.8f, 1).setColor(c).setUv2(-2, 0);
    }

    /** A seam along a front or back face (it runs in x at height y). */
    private static void seamZ(VertexConsumer vc, Matrix4f m, float x0, float x1, float z, float y, float th, int c) {
        vc.addVertex(m, x0, y - th, z).setUv(-0.8f, -1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, x1, y - th, z).setUv(0.8f, -1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, x1, y + th, z).setUv(0.8f, 1).setColor(c).setUv2(-2, 0);
        vc.addVertex(m, x0, y + th, z).setUv(-0.8f, 1).setColor(c).setUv2(-2, 0);
    }

    /** A round light lying on the outer face (the y–z plane), centred at (x, y, z). */
    private static void blob(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, int c) {
        vc.addVertex(m, x, y - r, z - r).setUv(-1, -1).setColor(c).setUv2(-1, 0);
        vc.addVertex(m, x, y + r, z - r).setUv(-1, 1).setColor(c).setUv2(-1, 0);
        vc.addVertex(m, x, y + r, z + r).setUv(1, 1).setColor(c).setUv2(-1, 0);
        vc.addVertex(m, x, y - r, z + r).setUv(1, -1).setColor(c).setUv2(-1, 0);
    }
}
