package dev.awaken.anims.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * The world layer (core.api.FirstPerson): your own body in first person, its limbs where a source put them in the world
 * (hands on a pipe, feet on the wall). Each limb is a rigid part of a private copy of the player model (vanilla's own is
 * posed by every player rendered in the frame, and parts are drawn later than they're submitted, so ours is never
 * touched by anyone else), turned about its root so it points at its tip, with its front where the source asked. Skin,
 * sleeves and pants as you wear them. Never in third person (the clips draw you there).
 * <p>
 * The legs (and a torso) are drawn in the world with real depth: they're far enough from the eye to look their size.
 * The arms are not: a hand on a pipe is a third of a block from your eye and would fill half the frame. They're drawn
 * in the hand pass like vanilla's hands (the viewmodel way): each arm keeps the direction the world gives it, so its
 * fist sits on the pipe on screen, but is pushed out along that direction to a hand's usual distance, where it reads
 * at a hand's usual size.
 */
final class WorldBody {
    private WorldBody() {}

    private static final FirstPerson.Rig RIG = new FirstPerson.Rig();
    private static final Matrix3f A = new Matrix3f(), B = new Matrix3f();
    private static final Quaternionf Q = new Quaternionf(), VIEW = new Quaternionf();
    private static final org.joml.Vector3f V = new org.joml.Vector3f();
    /** In view space, the point under the frame's corner an arm rises from (x for the right arm; z toward you). */
    static final double SHOULDER_X = 0.5, SHOULDER_Y = -0.85, SHOULDER_Z = -0.1;
    /** Where a fist is drawn from the eye at the least (vanilla's hands sit about this far). */
    static final double HAND_DISTANCE = 0.85;
    private static final FirstPerson.Limb[] ARMS = {FirstPerson.Limb.RIGHT_ARM, FirstPerson.Limb.LEFT_ARM};
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final double[] D = new double[3], F = new double[3];

    private static EntityModelSet from;
    private static PlayerModel wide, slim;
    private static Model.Simple[] wideParts, slimParts;
    private static float pt;
    private static boolean broken;
    /** This frame's world body drew arms (vanilla's hands step aside in the hand pass that follows). */
    static boolean armsThisFrame;

    static void onExtract(ExtractLevelRenderStateEvent e) {
        pt = e.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    static void frame() {
        armsThisFrame = false;
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        armsThisFrame = false;
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != mc.player) return;
        try {
            RIG.clear();
            List<FirstPerson.WorldSource> sources = FirstPerson.worldSources();
            boolean posed = false;
            for (int i = 0; i < sources.size() && !posed; i++) posed = sources.get(i).pose().pose(pt, RIG);
            if (!posed || !RIG.any()) return;
            armsThisFrame = RIG.arms();
            submit(mc, e);
        } catch (RuntimeException ex) {
            broken = true;
            armsThisFrame = false;
            Awaken.LOG.error("First-person world body failed and is off for this session", ex);
        }
    }

    private static void submit(Minecraft mc, SubmitCustomGeometryEvent e) {
        Model.Simple[] parts = parts(mc, mc.player.getSkin().model() == PlayerModelType.SLIM);
        PlayerModel model = mc.player.getSkin().model() == PlayerModelType.SLIM ? slim : wide;
        model.rightSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.leftSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightPants.visible = mc.player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftPants.visible = mc.player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.jacket.visible = mc.player.isModelPartShown(PlayerModelPart.JACKET);
        RenderType type = RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath());
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        POS.set(Mth.floor(cam.x), Mth.floor(cam.y), Mth.floor(cam.z));
        int light = LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, POS), mc.level.getBrightness(LightLayer.SKY, POS));
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        for (FirstPerson.Limb l : FirstPerson.Limb.values()) {
            int i = l.ordinal();
            float w = RIG.weight[i];
            if (w <= 0.01F || l == FirstPerson.Limb.RIGHT_ARM || l == FirstPerson.Limb.LEFT_ARM) continue;
            int j = i * 3;
            limb(ps, out, parts[i], part(model, l), type, light, RIG.root[j] - cam.x, RIG.root[j + 1] - cam.y, RIG.root[j + 2] - cam.z,
                    RIG.tip[j] - cam.x, RIG.tip[j + 1] - cam.y, RIG.tip[j + 2] - cam.z, RIG.front[j], RIG.front[j + 1], RIG.front[j + 2],
                    RIG.fx, RIG.fz, w);
        }
    }

    /** The hand pass: this frame's arms, in view space, pushed out to a hand's distance (see the class note). */
    static void drawArms(net.neoforged.neoforge.client.event.RenderHandEvent e) {
        if (!armsThisFrame || broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            boolean slimSkin = mc.player.getSkin().model() == PlayerModelType.SLIM;
            Model.Simple[] parts = parts(mc, slimSkin);
            PlayerModel model = slimSkin ? slim : wide;
            model.rightSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
            model.leftSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
            RenderType type = RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath());
            var camera = mc.gameRenderer.mainCamera();
            Vec3 cam = camera.position();
            VIEW.set(camera.rotation()).conjugate(); // world offsets to view space, as the hand pass sees them
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            for (FirstPerson.Limb l : ARMS) {
                int i = l.ordinal(), j = i * 3;
                float w = RIG.weight[i];
                if (w <= 0.01F) continue;
                // the fist: where the world has it on screen, at a hand's distance
                V.set((float) (RIG.tip[j] - cam.x), (float) (RIG.tip[j + 1] - cam.y), (float) (RIG.tip[j + 2] - cam.z)).rotate(VIEW);
                float d = V.length();
                if (d < 1e-4F) continue;
                V.mul((float) (HAND_DISTANCE / d));
                // the arm comes up to it from under the frame's corner on its own side
                double side = l == FirstPerson.Limb.RIGHT_ARM ? 1 : -1;
                double sx = side * SHOULDER_X, sy = SHOULDER_Y, sz = SHOULDER_Z;
                double ax = V.x - sx, ay = V.y - sy, az = V.z - sz, al = Math.sqrt(ax * ax + ay * ay + az * az);
                double reach = FirstPerson.Rig.ARM_REACH;
                double rx = V.x - ax / al * reach, ry = V.y - ay / al * reach, rz = V.z - az / al * reach;
                // view space: the "body" faces into the screen, the arm's front faces up
                limb(ps, e.getSubmitNodeCollector(), parts[i], part(model, l), type, e.getPackedLight(), rx, ry, rz, V.x, V.y, V.z,
                        0, 1, 0, 0, -1, w);
            }
            ps.popPose();
        } catch (RuntimeException ex) {
            broken = true;
            armsThisFrame = false;
            Awaken.LOG.error("First-person world arms failed and are off for this session", ex);
        }
    }

    /**
     * One limb: its root at the rig's root, its length along root to tip, its front turned to the rig's front. The
     * standing body's transform (vanilla's: turned to the facing, flipped into model space) is turned by the rotation
     * that takes a hanging limb (straight down, front forward) to this one.
     */
    private static void limb(PoseStack ps, SubmitNodeCollector out, Model.Simple m, ModelPart part, RenderType type, int light,
                             double rx, double ry, double rz, double tx, double ty, double tz, double frx, double fry, double frz,
                             double facingX, double facingZ, float w) {
        float yaw = (float) (Mth.atan2(-facingX, facingZ) * Mth.RAD_TO_DEG);
        D[0] = tx - rx;
        D[1] = ty - ry;
        D[2] = tz - rz;
        double len = Math.sqrt(D[0] * D[0] + D[1] * D[1] + D[2] * D[2]);
        if (len < 1e-4) return;
        D[0] /= len;
        D[1] /= len;
        D[2] /= len;
        // the front, square to the limb (or the body's forward, when the asked front lies along it)
        double dot = frx * D[0] + fry * D[1] + frz * D[2];
        F[0] = frx - D[0] * dot;
        F[1] = fry - D[1] * dot;
        F[2] = frz - D[2] * dot;
        double fl = Math.sqrt(F[0] * F[0] + F[1] * F[1] + F[2] * F[2]);
        if (fl < 1e-3) {
            F[0] = facingX;
            F[1] = 0;
            F[2] = facingZ;
            dot = F[0] * D[0] + F[2] * D[2];
            F[0] -= D[0] * dot;
            F[1] -= D[1] * dot;
            F[2] -= D[2] * dot;
            fl = Math.sqrt(F[0] * F[0] + F[1] * F[1] + F[2] * F[2]);
            if (fl < 1e-3) return;
        }
        F[0] /= fl;
        F[1] /= fl;
        F[2] /= fl;
        // from (down, forward, down × forward) to (limb, front, limb × front)
        float bx = (float) facingX, bz = (float) facingZ;
        A.set(0, -1, 0, bx, 0, bz, -bz, 0, bx);
        float dx = (float) D[0], dy = (float) D[1], dz = (float) D[2], fx = (float) F[0], fy = (float) F[1], fz = (float) F[2];
        B.set(dx, dy, dz, fx, fy, fz, dy * fz - dz * fy, dz * fx - dx * fz, dx * fy - dy * fx);
        B.mul(A.transpose());
        Q.setFromNormalized(B);
        ps.pushPose();
        ps.translate(rx, ry, rz);
        ps.rotate(Q);
        ps.rotateDegrees(Axis.YP, 180F - yaw);
        ps.scale(-1F, -1F, 1F);
        ps.translate(-part.x / 16F, -part.y / 16F, -part.z / 16F);
        int tint = w >= 0.99F ? -1 : (Mth.clamp((int) (w * 255), 0, 255) << 24) | 0xFFFFFF;
        out.submitModel(m, Unit.INSTANCE, ps, type, light, OverlayTexture.NO_OVERLAY, tint, null, 0);
        ps.popPose();
    }

    private static ModelPart part(PlayerModel m, FirstPerson.Limb l) {
        return switch (l) {
            case RIGHT_ARM -> m.rightArm;
            case LEFT_ARM -> m.leftArm;
            case RIGHT_LEG -> m.rightLeg;
            case LEFT_LEG -> m.leftLeg;
            case BODY -> m.body;
        };
    }

    /** Our own models (baked again when the game's model set is), one drawable per limb. */
    private static Model.Simple[] parts(Minecraft mc, boolean isSlim) {
        EntityModelSet set = mc.getEntityModels();
        if (set != from || wide == null) {
            from = set;
            wide = new PlayerModel(set.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel(set.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            wideParts = drawables(wide);
            slimParts = drawables(slim);
        }
        return isSlim ? slimParts : wideParts;
    }

    private static Model.Simple[] drawables(PlayerModel m) {
        Model.Simple[] out = new Model.Simple[FirstPerson.Rig.N];
        for (FirstPerson.Limb l : FirstPerson.Limb.values()) out[l.ordinal()] = new Model.Simple(part(m, l), RenderTypes::entityTranslucent);
        return out;
    }

    static void reset() {
        armsThisFrame = false;
    }
}
