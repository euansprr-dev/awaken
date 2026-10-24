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
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The first-person wrist (docs/proposals/holo-ui.md §6). While a holo menu is open or animating, a HIGH-priority
 * {@link RenderHandEvent} listener cancels both vanilla hands (so CastAnims at LOW and Lucidium's reality check at
 * NORMAL don't run: NeoForge skips cancelled events for listeners that don't ask for them) and draws instead:
 * <ul>
 *   <li>the off-hand forearm raised across the view, the back of the wrist turned toward the eye, with the player's
 *       own skin and sleeve (wide or slim model, whatever the renderer is);</li>
 *   <li>a thin band of System light around the wrist in the additive glow pipeline, flaring as the panel leaves it;</li>
 *   <li>the main hand (or the item in it) sinking out of view the way vanilla lowers it on an item swap.</li>
 * </ul>
 * The band's centre ({@link #band}) is also where the panel starts its unfold.
 */
final class Wrist {
    private Wrist() {}

    // the raised forearm in hand-view space (metres, x right, y up, -z ahead), for the left arm; mirrored for the right
    private static final float WX = -0.165f, WY = -0.285f, WZ = -0.56f;
    private static final Vector3f DIR = new Vector3f(0.80f, 0.30f, -0.44f).normalize(); // elbow → hand
    private static final Vector3f FACE = new Vector3f(0.05f, 0.62f, 0.78f);           // where the back of the wrist looks
    /** Along the arm (model units from the shoulder pivot): the band and the hand's end. */
    private static final float BAND_Y0 = 7.0f, BAND_Y1 = 8.3f;

    private static final Matrix4f M = new Matrix4f(), CANCEL = new Matrix4f();
    private static final Matrix3f BASIS = new Matrix3f();
    private static final Vector3f X = new Vector3f(), Y = new Vector3f(), Z = new Vector3f(), T = new Vector3f();
    private static long drawnFrame = -1;
    /** Autopilot: draw the arm with this skin (wide or slim) instead of the player's. */
    static net.minecraft.world.entity.player.PlayerSkin forcedSkin;
    private static float bandGlow;
    private static float armX0, armX1; // the arm's cross-section in model units (wide or slim)
    private static final SubmitNodeCollector.CustomGeometryRenderer BAND = Wrist::band;

    /** The band's centre in hand-view space for a raise of {@code a} (0 down, 1 up) on {@code side} (-1 left, 1 right). */
    static void band(float a, int side, Vector3f out) {
        float down = 1 - a;
        out.set(side < 0 ? WX : -WX, WY - 0.55f * down, WZ + 0.10f * down);
    }

    private static final Vector3f TMP = new Vector3f();
    private static final Matrix4f BASIS4 = new Matrix4f();

    /** Builds the forearm's transform (model units → hand-view metres) for a raise of {@code a} into {@link #M}. */
    private static void armMatrix(float a, int side, boolean slim) {
        // basis: model +Y runs elbow → hand, the outer face (+X on the left arm, -X on the right) looks at FACE
        Y.set(side < 0 ? DIR.x : -DIR.x, DIR.y, DIR.z);
        T.set(side < 0 ? FACE.x : -FACE.x, FACE.y, FACE.z);
        X.set(T).sub(TMP.set(Y).mul(T.dot(Y))).normalize(); // the outer face's direction, square to the arm
        if (side > 0) X.negate();                            // the right arm's outer side is model -X
        Z.set(X).cross(Y);
        BASIS.set(X, Y, Z);
        // lowered: the same arm sunk below the view and tilted down
        float down = 1 - a;
        float cx = slim ? (side < 0 ? 0.5f : -0.5f) : (side < 0 ? 1.0f : -1.0f);
        band(a, side, T);
        M.identity().translate(T);
        M.rotate(Axis.XP.rotationDegrees(-38f * down));
        M.mul(BASIS4.set(BASIS));
        M.scale(1 / 16f);
        M.translate(-cx, -(BAND_Y0 + BAND_Y1) / 2f, 0);
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

        // the band, in the arm's own space
        armX0 = slim ? (side < 0 ? -1f : -2f) : (side < 0 ? -1f : -3f);
        armX1 = slim ? (side < 0 ? 2f : 1f) : (side < 0 ? 3f : 1f);
        bandGlow = t.band() * Math.min(1f, a * 1.2f);
        if (bandGlow > 0.01f) {
            pose.pushPose();
            pose.mulPose(M);
            e.getSubmitNodeCollector().submitCustomGeometry(pose, HoloPipelines.glow(), BAND);
            pose.popPose();
        }

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

    /** Draws the ring's faces that look at the eye (no depth test: back faces would show through the arm). */
    private static void band(PoseStack.Pose pose, VertexConsumer vc) {
        float g = bandGlow;
        int core = Glass.a(Glass.EDGE, Math.min(1f, 0.55f * g));
        int hot = Glass.a(0xCFEAFF, Math.min(1f, 0.30f * g));
        float pad = 0.42f;
        float x0 = armX0 - pad, x1 = armX1 + pad, z0 = -2f - pad, z1 = 2f + pad;
        float y0 = BAND_Y0, y1 = BAND_Y1, ym = (y0 + y1) / 2f;
        Matrix4f m = pose.pose();
        // four faces: outer (+x), inner (-x), front (-z), back (+z); each a line shape across the band's height
        face(vc, m, x1, z0, x1, z1, y0, y1, 1, 0, 0, core);
        face(vc, m, x0, z1, x0, z0, y0, y1, -1, 0, 0, core);
        face(vc, m, x0, z0, x1, z0, y0, y1, 0, 0, -1, core);
        face(vc, m, x1, z1, x0, z1, y0, y1, 0, 0, 1, core);
        // a soft bloom on whichever side faces the eye most, and a hot core line on it
        float best = -1;
        int bx = 0;
        for (int i = 0; i < 4; i++) {
            float nx = i == 0 ? 1 : i == 1 ? -1 : 0, nz = i == 2 ? -1 : i == 3 ? 1 : 0;
            m.transformDirection(FN.set(nx, 0, nz)).normalize();
            m.transformPosition(FC.set(i == 0 ? x1 : i == 1 ? x0 : (x0 + x1) / 2, ym, i == 2 ? z0 : i == 3 ? z1 : 0));
            float facing = -FN.dot(FC.normalize());
            if (facing > best) {
                best = facing;
                bx = i;
            }
        }
        float nx = bx == 0 ? 1 : bx == 1 ? -1 : 0, nz = bx == 2 ? -1 : bx == 3 ? 1 : 0;
        float fx = bx == 0 ? x1 + 0.05f : bx == 1 ? x0 - 0.05f : (x0 + x1) / 2, fz = bx == 2 ? z0 - 0.05f : bx == 3 ? z1 + 0.05f : 0;
        // tangent along the face
        float tx = nz != 0 ? 1 : 0, tz = nx != 0 ? 1 : 0;
        float half = nz != 0 ? (x1 - x0) / 2 : (z1 - z0) / 2;
        blob(vc, m, fx, ym, fz, tx, tz, half * 1.6f, 2.6f, Glass.a(Glass.EDGE, Math.min(1f, 0.22f * g)));
        line(vc, m, fx, ym, fz, tx, tz, half, 0.42f, hot);
    }

    private static void face(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float y0, float y1,
            float nx, float ny, float nz, int colour) {
        m.transformDirection(FN.set(nx, ny, nz));
        m.transformPosition(FC.set((ax + bx) / 2, (y0 + y1) / 2, (az + bz) / 2));
        if (FN.dot(FC) >= 0) return; // faces away from the eye
        // u stays inside ±0.8 so the line shape doesn't fade at the ring's corners
        vertex(vc, m, ax, y0, az, -0.8f, -1, colour, -2);
        vertex(vc, m, bx, y0, bz, 0.8f, -1, colour, -2);
        vertex(vc, m, bx, y1, bz, 0.8f, 1, colour, -2);
        vertex(vc, m, ax, y1, az, -0.8f, 1, colour, -2);
    }

    private static void blob(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float tx, float tz, float halfW, float halfH, int colour) {
        vertex(vc, m, cx - tx * halfW, cy - halfH, cz - tz * halfW, -1, -1, colour, -1);
        vertex(vc, m, cx + tx * halfW, cy - halfH, cz + tz * halfW, 1, -1, colour, -1);
        vertex(vc, m, cx + tx * halfW, cy + halfH, cz + tz * halfW, 1, 1, colour, -1);
        vertex(vc, m, cx - tx * halfW, cy + halfH, cz - tz * halfW, -1, 1, colour, -1);
    }

    private static void line(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float tx, float tz, float halfW, float halfH, int colour) {
        vertex(vc, m, cx - tx * halfW, cy - halfH, cz - tz * halfW, -1, -1, colour, -2);
        vertex(vc, m, cx + tx * halfW, cy - halfH, cz + tz * halfW, 1, -1, colour, -2);
        vertex(vc, m, cx + tx * halfW, cy + halfH, cz + tz * halfW, 1, 1, colour, -2);
        vertex(vc, m, cx - tx * halfW, cy + halfH, cz - tz * halfW, -1, 1, colour, -2);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int colour, int shape) {
        vc.addVertex(m, x, y, z).setUv(u, v).setColor(colour).setUv2(shape, 0);
    }
}
