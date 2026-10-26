package dev.awaken.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.core.client.skin.SkinModel;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.village.trouble.Rescuee;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Someone a village's trouble is about (SPEC §28.7), in their village skin, posed by what they're doing: bound in the
 * nest (arms tied behind, head down, tugging now and then), on your back (legs round your waist, arms round your neck,
 * leaning in on your shoulder: in first person only their hands show, crossed at your chest), walking behind you, sitting
 * hurt where they fell, in their parent's arms. What they say shows over their head, fading.
 */
final class RescueeRenderer extends SkinnedMobRenderer<Rescuee, RescueeRenderer.State> {
    static final class State extends SkinRenderState {
        int mode;
        float cut, age;
        boolean firstPersonCarrier;
        List<FormattedCharSequence> speech;
        float speechAlpha;
    }

    private static final Style SAY = Style.EMPTY.withColor(TextColor.fromRgb(0xFFF1D6));

    RescueeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER), false), new Model(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Rescuee r, State s, float partial) {
        super.extractRenderState(r, s, partial);
        String skin = r.skin();
        s.wear(skin.isEmpty() ? dev.awaken.Awaken.id("textures/entity/townsfolk/oren.png") : Identifier.parse(skin), r.slim());
        s.mode = r.mode();
        // on a back: drawn from the carrier's own position this frame, so it never trails your moves
        if (s.mode == Rescuee.CARRIED && r.carrier() >= 0 && r.level().getEntity(r.carrier()) instanceof net.minecraft.world.entity.LivingEntity c) {
            Vec3 at = c.getPosition(partial);
            float yaw = Mth.rotLerp(partial, c.yBodyRotO, c.yBodyRot);
            Vec3 on = Rescuee.onBack(at.x, at.y, at.z, yaw, r.child());
            s.x = on.x;
            s.y = on.y;
            s.z = on.z;
            s.bodyRot = yaw;
        }
        s.cut = r.cut();
        s.age = r.tickCount + partial;
        Minecraft mc = Minecraft.getInstance();
        s.firstPersonCarrier = s.mode == Rescuee.CARRIED && mc.getCameraEntity() != null && r.carrier() == mc.getCameraEntity().getId()
                && mc.options.getCameraType().isFirstPerson();
        s.isPassenger = false; // never vanilla's riding pose: the model has its own
        // what they say, fading in and out over 5 s
        s.speech = null;
        String line = r.line();
        float since = r.tickCount - r.sayStart + partial;
        if (!line.isEmpty() && since < 100 && s.distanceToCameraSq < 26 * 26 && !s.firstPersonCarrier) {
            s.speech = mc.font.split(Component.literal(line).withStyle(SAY), 150);
            s.speechAlpha = Mth.clamp(Math.min(since / 5f, (100 - since) / 12f), 0f, 1f);
        }
    }

    @Override
    protected void setupRotations(State s, PoseStack pose, float bodyRot, float scale) {
        super.setupRotations(s, pose, bodyRot, scale);
        switch (s.mode) {
            // on a back: leaning in on the shoulder
            case Rescuee.CARRIED -> pose.rotate(Axis.XP.rotationDegrees(22f));
            // sitting on the ground where they fell
            case Rescuee.LOST -> pose.translate(0f, -0.62f, 0f);
            // bound: slumped a little, swaying when they tug
            case Rescuee.BOUND -> pose.rotate(Axis.ZP.rotationDegrees(Mth.sin(s.age * 0.05f) * 2.5f + tug(s) * 6f));
            default -> {}
        }
    }

    /** A tug at the bonds every few seconds (0..1), stronger while being cut free. */
    static float tug(State s) {
        float t = (s.age % 90f) / 90f;
        float pulse = t > 0.84f ? Mth.sin((t - 0.84f) / 0.16f * Mth.PI) : 0f;
        return Math.max(pulse, s.cut > 0 ? 0.5f + 0.5f * Mth.sin(s.age * 0.9f) : 0f);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(s, pose, collector, camera);
        if (s.speech == null) return;
        Font font = getFont();
        pose.pushPose();
        pose.translate(0f, s.boundingBoxHeight + 0.45f, 0f);
        pose.rotate(camera.orientation);
        pose.scale(0.024f, -0.024f, 0.024f);
        int a = Math.max(4, Math.round(s.speechAlpha * 255f));
        float lh = font.lineHeight + 1, top = -s.speech.size() * lh, w = 0;
        for (var line : s.speech) w = Math.max(w, font.width(line));
        int bg = Math.round(s.speechAlpha * 150f) << 24 | 0x2A2018;
        collector.submitTextBackground(pose, -w / 2f - 3, top - 2, w / 2f + 3, 1, bg, Font.DisplayMode.NORMAL, s.lightCoords);
        for (int k = 0; k < s.speech.size(); k++) {
            var line = s.speech.get(k);
            float x = -font.width(line) / 2f, y = top + k * lh;
            collector.submitText(pose, x, y, line, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, (a / 3) << 24 | 0xFFFFFF, 0, 0);
            collector.submitText(pose, x, y, line, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, a << 24 | 0xFFFFFF, 0, 0);
        }
        pose.popPose();
    }

    /** The player mesh, posed after vanilla's own pose by what they're doing. */
    static final class Model extends SkinModel<State> {
        Model(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float t = s.age;
            switch (s.mode) {
                case Rescuee.BOUND -> {
                    // wrists tied behind the back, head down; a tug lifts the shoulders and the head
                    float tug = tug(s);
                    rightArm.xRot = 0.52f + tug * 0.2f;
                    leftArm.xRot = 0.52f + tug * 0.2f;
                    rightArm.yRot = 0f;
                    leftArm.yRot = 0f;
                    // the wrists drawn in together behind (a right arm closes with negative zRot, a left with positive)
                    rightArm.zRot = -0.12f + tug * 0.16f;
                    leftArm.zRot = 0.12f - tug * 0.16f;
                    head.xRot = 0.42f - tug * 0.5f + Mth.sin(t * 0.04f) * 0.04f;
                    head.yRot = Mth.sin(t * 0.021f) * 0.35f * (1 - tug);
                    rightLeg.xRot = 0.04f;
                    leftLeg.xRot = -0.04f;
                    rightLeg.zRot = 0.03f;
                    leftLeg.zRot = -0.03f;
                }
                case Rescuee.CARRIED -> {
                    // legs round the carrier's waist, arms round their neck, the head on their shoulder
                    rightLeg.xRot = -1.18f;
                    leftLeg.xRot = -1.18f;
                    rightLeg.yRot = 0.42f;
                    leftLeg.yRot = -0.42f;
                    rightLeg.zRot = 0.12f;
                    leftLeg.zRot = -0.12f;
                    rightArm.xRot = -1.62f + Mth.sin(t * 0.06f) * 0.03f;
                    leftArm.xRot = -1.62f + Mth.sin(t * 0.06f + 1f) * 0.03f;
                    rightArm.yRot = -0.46f;
                    leftArm.yRot = 0.46f;
                    rightArm.zRot = 0f;
                    leftArm.zRot = 0f;
                    head.xRot = 0.18f;
                    head.zRot = 0.22f;
                    // in first person only the hands show, crossed at your chest: the rest would fill the screen
                    boolean fp = s.firstPersonCarrier;
                    head.visible = !fp;
                    hat.visible = !fp && s.showHat;
                    body.visible = !fp;
                    rightLeg.visible = !fp;
                    leftLeg.visible = !fp;
                }
                case Rescuee.LOST -> {
                    // sat where they fell: legs out, one knee up, a hand on it, head low
                    rightLeg.xRot = -1.42f;
                    leftLeg.xRot = -1.05f;
                    rightLeg.yRot = 0.18f;
                    leftLeg.yRot = -0.12f;
                    rightArm.xRot = -0.55f;
                    leftArm.xRot = -0.95f;
                    leftArm.yRot = 0.3f;
                    rightArm.zRot = 0.18f;
                    head.xRot = 0.35f + Mth.sin(t * 0.03f) * 0.05f;
                }
                case Rescuee.WAIT -> {
                    // hands clasped in front, weight on one leg
                    rightArm.xRot = -0.62f;
                    leftArm.xRot = -0.62f;
                    rightArm.yRot = -0.72f;
                    leftArm.yRot = 0.72f;
                    leftLeg.zRot = -0.07f;
                }
                case Rescuee.EMBRACE -> {
                    rightArm.xRot = -1.43f;
                    leftArm.xRot = -1.43f;
                    rightArm.yRot = -0.66f;
                    leftArm.yRot = 0.66f;
                    head.xRot = 0.12f;
                    head.zRot = -0.09f + Mth.sin(t * 0.07f) * 0.04f;
                }
                default -> {
                    // walking home: a little stiff, a hand to the side now and then
                }
            }
            if (s.mode != Rescuee.CARRIED) {
                head.visible = true;
                body.visible = true;
                rightLeg.visible = true;
                leftLeg.visible = true;
            }
        }
    }
}
