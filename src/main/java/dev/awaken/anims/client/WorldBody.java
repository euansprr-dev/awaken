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
import net.minecraft.client.renderer.item.ItemStackRenderState;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
    /** How far a world arm's root follows its fist across (so it rises from under the fist, not the corner). */
    static final double RISE = 0.45;
    /** Where a fist is drawn from the eye at the least (vanilla's hands sit about this far). */
    static final double HAND_DISTANCE = 0.85;
    private static final FirstPerson.Limb[] ARMS = {FirstPerson.Limb.RIGHT_ARM, FirstPerson.Limb.LEFT_ARM},
            LEGS = {FirstPerson.Limb.RIGHT_LEG, FirstPerson.Limb.LEFT_LEG};
    /** The hand pass's field of view (vanilla's: 70°, whatever the player's own). */
    static final float HAND_FOV = 70F;

    /**
     * The hand pass draws at its own field of view: a point the world shows at some place on screen must be drawn there
     * with its view x and y scaled by this (the two fields of view's ratio; 1 at a 70° view with nothing narrowing it).
     */
    static float lens(Minecraft mc) {
        float world = mc.gameRenderer.mainCamera().getFov();
        if (world <= 1F) return 1F;
        float hand = dev.awaken.anims.client.station.StationClient.ownsCamera()
                ? mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov : HAND_FOV;
        return (float) (Math.tan(Math.toRadians(hand) / 2) / Math.tan(Math.toRadians(world) / 2));
    }

    /** A foot nearer the eye than this is drawn in the hand pass, pushed out to it. */
    static final double LEG_DISTANCE = 1.15;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final double[] D = new double[3], F = new double[3];

    private static EntityModelSet from;
    private static PlayerModel wide, slim;
    private static Model.Simple[] wideParts, slimParts;
    private static float pt;
    private static boolean broken;
    /** This frame's world body drew arms (vanilla's hands step aside in the hand pass that follows). */
    static boolean armsThisFrame, nearLegsThisFrame;
    /** The colour the next limb is drawn in (a fist's tint), white otherwise. */
    private static int rgb = 0xFFFFFF;

    static void onExtract(ExtractLevelRenderStateEvent e) {
        pt = e.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    static void frame() {
        armsThisFrame = false;
        nearLegsThisFrame = false;
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        armsThisFrame = false;
        nearLegsThisFrame = false;
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != mc.player && !dev.awaken.anims.client.station.StationClient.ownsCamera()) return;
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
        // your own render state's light where the game gathered it (a torch in your hand lights your feet too, SPEC §28.3)
        var avatar = e.getLevelRenderState().playerRenderState.avatarRenderState;
        int light = avatar != null ? avatar.lightCoords
                : LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, POS), mc.level.getBrightness(LightLayer.SKY, POS));
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        for (FirstPerson.Limb l : FirstPerson.Limb.values()) {
            int i = l.ordinal();
            float w = RIG.weight[i];
            if (w <= 0.01F || l == FirstPerson.Limb.RIGHT_ARM || l == FirstPerson.Limb.LEFT_ARM) continue;
            int j = i * 3;
            // a foot nearer than this (a slide's, out ahead under low eyes) would fill the frame: the hand pass draws it
            double tx = RIG.tip[j] - cam.x, ty = RIG.tip[j + 1] - cam.y, tz = RIG.tip[j + 2] - cam.z;
            if (l != FirstPerson.Limb.BODY && tx * tx + ty * ty + tz * tz < LEG_DISTANCE * LEG_DISTANCE) {
                nearLegsThisFrame = true;
                continue;
            }
            limb(ps, out, parts[i], part(model, l), type, light, RIG.root[j] - cam.x, RIG.root[j + 1] - cam.y, RIG.root[j + 2] - cam.z,
                    RIG.tip[j] - cam.x, RIG.tip[j + 1] - cam.y, RIG.tip[j + 2] - cam.z, RIG.front[j], RIG.front[j + 1], RIG.front[j + 2],
                    RIG.fx, RIG.fz, w);
        }
    }

    /**
     * The hand pass: this frame's near legs (a foot under {@link #LEG_DISTANCE} from the eye), each kept where the world
     * puts it on screen but pushed out along its line of sight to that distance, the way the arms are (drawn in the
     * world they would sink into the ground they lie on).
     */
    static void drawNearLegs(net.neoforged.neoforge.client.event.RenderHandEvent e) {
        if (!nearLegsThisFrame || broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            boolean slimSkin = mc.player.getSkin().model() == PlayerModelType.SLIM;
            Model.Simple[] parts = parts(mc, slimSkin);
            PlayerModel model = slimSkin ? slim : wide;
            model.rightPants.visible = mc.player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
            model.leftPants.visible = mc.player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
            RenderType type = RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath());
            var camera = mc.gameRenderer.mainCamera();
            Vec3 cam = camera.position();
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            float lens = lens(mc);
            ps.scale(lens, lens, 1F); // where the world puts it on screen, at the hand pass's field of view
            ps.rotate(VIEW.set(camera.rotation()).conjugate()); // world offsets to view space, as the hand pass sees them
            for (FirstPerson.Limb l : LEGS) {
                int i = l.ordinal(), j = i * 3;
                float w = RIG.weight[i];
                if (w <= 0.01F) continue;
                double tx = RIG.tip[j] - cam.x, ty = RIG.tip[j + 1] - cam.y, tz = RIG.tip[j + 2] - cam.z;
                double d = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (d >= LEG_DISTANCE || d < 1e-4) continue;
                double k = LEG_DISTANCE / d;
                limb(ps, e.getSubmitNodeCollector(), parts[i], part(model, l), type, e.getPackedLight(), (RIG.root[j] - cam.x) * k,
                        (RIG.root[j + 1] - cam.y) * k, (RIG.root[j + 2] - cam.z) * k, tx * k, ty * k, tz * k, RIG.front[j], RIG.front[j + 1],
                        RIG.front[j + 2], RIG.fx, RIG.fz, w);
            }
            ps.popPose();
        } catch (RuntimeException ex) {
            broken = true;
            Awaken.LOG.error("First-person legs failed and are off for this session", ex);
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
            BASE_INV.set(ps.last().pose()).invert(); // the fingers' place, back in this stack's own space
            for (FirstPerson.Limb l : ARMS) {
                int i = l.ordinal(), j = i * 3;
                float w = RIG.weight[i];
                if (w <= 0.01F) continue;
                // the fist: where the world has it on screen, at a hand's distance
                V.set((float) (RIG.tip[j] - cam.x), (float) (RIG.tip[j + 1] - cam.y), (float) (RIG.tip[j + 2] - cam.z)).rotate(VIEW);
                float d = V.length();
                if (d < 1e-4F) continue;
                V.mul((float) ((RIG.handDistance > 0 ? RIG.handDistance : HAND_DISTANCE) / d));
                float lens = lens(mc);
                V.x *= lens;
                V.y *= lens;
                // the arm comes up to it from under the frame's corner on its own side, its hand held as the source asks
                boolean right = l == FirstPerson.Limb.RIGHT_ARM;
                double side = right ? 1 : -1;
                int h = right ? 0 : 1;
                int light = e.getPackedLight();
                if (RIG.glow > 0) light = LightCoordsUtil.withBlock(light, Math.max(LightCoordsUtil.block(light), Math.round(15 * Math.min(1F, RIG.glow))));
                boolean holds = !RIG.held[h].isEmpty();
                if (holds) tipAcross = right ? (slimSkin ? -0.5F : -1F) : (slimSkin ? 0.5F : 1F); // the middle of the arm
                // the arm rises to the fist from below it, on its own side (from the frame's corner, a hand high in the
                // frame would drag its sleeve across it)
                viewArm(ps, e.getSubmitNodeCollector(), parts[i], part(model, l), type, light, V.x, V.y, V.z,
                        side * SHOULDER_X + RISE * V.x, SHOULDER_Y, SHOULDER_Z, FirstPerson.Rig.ARM_REACH, w);
                tipAcross = Float.NaN;
                if (holds) heldItem(mc, ps, e.getSubmitNodeCollector(), h, RIG.held[h], light);
            }
            ps.popPose();
        } catch (RuntimeException ex) {
            broken = true;
            armsThisFrame = false;
            Awaken.LOG.error("First-person world arms failed and are off for this session", ex);
        }
    }

    /** An arm in view space rising from (sx, sy, sz) toward its fist at (fx, fy, fz), its front up. */
    private static void viewArm(PoseStack ps, SubmitNodeCollector out, Model.Simple m, ModelPart part, RenderType type, int light,
                                double fx, double fy, double fz, double sx, double sy, double sz, double reach, float w) {
        double ax = fx - sx, ay = fy - sy, az = fz - sz, al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1e-4) return;
        // view space: the "body" faces into the screen
        limb(ps, out, m, part, type, light, fx - ax / al * reach, fy - ay / al * reach, fz - az / al * reach, fx, fy, fz, 0, 1, 0, 0, -1, w);
    }

    private static final ItemStackRenderState[] HELD = {new ItemStackRenderState(), new ItemStackRenderState()};
    /** The next limb drawn notes where its fingers are ({@link #TIP}) when this is a number: across the arm, pixels. */
    private static float tipAcross = Float.NaN;
    private static final org.joml.Vector3f TIP = new org.joml.Vector3f();
    private static final org.joml.Matrix4f BASE_INV = new org.joml.Matrix4f();

    /** What a hand holds, at its fingers (where the arm just drawn put them), upright and turned to you. */
    private static void heldItem(Minecraft mc, PoseStack ps, SubmitNodeCollector out, int h, ItemStack st, int light) {
        if (st.isEmpty() || mc.level == null) return;
        ItemStackRenderState r = HELD[h];
        mc.getItemModelResolver().updateForTopItem(r, st, ItemDisplayContext.GROUND, mc.level, null, h);
        BASE_INV.transformPosition(TIP);
        ps.pushPose();
        ps.translate(TIP.x, TIP.y - 0.02F, TIP.z);
        ps.scale(0.45F, 0.45F, 0.45F);
        r.submit(ps, out, light, OverlayTexture.NO_OVERLAY, 0);
        ps.popPose();
    }

    /** Where a fist clip's arm rises from: vanilla's own arm starts about here (x for the right arm). */
    static final double FIST_ANCHOR_X = 0.48, FIST_ANCHOR_Y = -0.84, FIST_ANCHOR_Z = -0.53, FIST_REACH = 0.6;

    /**
     * A view clip's fist (FirstPerson.View fists): the empty arm on that side drawn rising to (x, y, z) in view space, as
     * the hand pass sees it. Drawn in the main hand's event (the off hand's may not come when it holds nothing).
     */
    static void drawFist(net.neoforged.neoforge.client.event.RenderHandEvent e, boolean right, float x, float y, float z, int tint, float glow) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            boolean slimSkin = mc.player.getSkin().model() == PlayerModelType.SLIM;
            Model.Simple[] parts = parts(mc, slimSkin);
            PlayerModel model = slimSkin ? slim : wide;
            model.rightSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
            model.leftSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
            RenderType type = RenderTypes.entityTranslucent(mc.player.getSkin().body().texturePath());
            FirstPerson.Limb l = right ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM;
            double side = right ? 1 : -1;
            rgb = tint == 0 ? 0xFFFFFF : tint;
            // lit from within: the block light raised toward full by the glow
            int light = e.getPackedLight();
            if (glow > 0) light = LightCoordsUtil.withBlock(light, Math.max(LightCoordsUtil.block(light), Math.round(15 * Math.min(1F, glow))));
            viewArm(e.getPoseStack(), e.getSubmitNodeCollector(), parts[l.ordinal()], part(model, l), type, light, x, y, z,
                    side * FIST_ANCHOR_X, FIST_ANCHOR_Y, FIST_ANCHOR_Z, FIST_REACH, 1F);
            rgb = 0xFFFFFF;
        } catch (RuntimeException ex) {
            broken = true;
            Awaken.LOG.error("First-person fists failed and are off for this session", ex);
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
        int tint = (Mth.clamp((int) (w * 255), 0, 255) << 24) | rgb;
        out.submitModel(m, Unit.INSTANCE, ps, type, light, OverlayTexture.NO_OVERLAY, tint, null, 0);
        if (!Float.isNaN(tipAcross)) {
            // where the fingers are, in the space this is drawn in (an item held there is drawn at it)
            ps.last().pose().transformPosition((part.x + tipAcross) / 16F, (part.y + 10.5F) / 16F, (part.z - 1F) / 16F, TIP);
            tipAcross = Float.NaN;
        }
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

    /** This frame's world body shows that arm (the right, or the left). */
    static boolean armShown(boolean right) {
        return armsThisFrame && RIG.weight[(right ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM).ordinal()] > 0.01F;
    }

    static void reset() {
        armsThisFrame = false;
    }
}
