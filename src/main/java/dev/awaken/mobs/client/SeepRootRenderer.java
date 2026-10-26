package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.model.HeroModel;
import dev.awaken.mobs.client.model.WildModel;
import dev.awaken.mobs.entity.SeepRoot;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws a burst of the seep heart's roots ({@link SeepRoot}): the "seep_root" hero rig playing its one clip, "erupt"
 * (they punch up out of the floor with the stones they broke, hold, and sink back), with its emissive veins on top,
 * bright as they come up and fading as they sink.
 */
public final class SeepRootRenderer extends EntityRenderer<SeepRoot, SeepRootRenderer.State> {
    private static final Identifier TEXTURE = Awaken.id("textures/entity/mobs/seep_root.png");
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(Awaken.id("textures/entity/mobs/seep_root_glow.png"));
    private final @Nullable Model model;

    public SeepRootRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        HeroRig rig = HeroRig.get("seep_root");
        this.model = rig == null ? null : new Model(ctx.bakeLayer(HeroModel.layer(rig.id)), rig);
        this.shadowRadius = 0.0F;
    }

    public static final class State extends EntityRenderState {
        float age, yaw, size;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SeepRoot root, State s, float partialTicks) {
        super.extractRenderState(root, s, partialTicks);
        s.age = (root.tickCount + partialTicks) / 20.0F;
        s.yaw = root.getYRot();
        s.size = root.size();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (model == null) return;
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, 180.0F - s.yaw);
        pose.scale(s.size, s.size, s.size);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.setupAnim(s);
        collector.submitModel(model, s, pose, RenderTypes.entityCutout(TEXTURE), s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        float glow = Mth.clamp(1.0F - Math.max(0.0F, s.age - 0.7F) / 0.6F, 0.0F, 1.0F);
        if (glow > 0.01F) {
            collector.order(1).submitModel(model, s, pose, GLOW, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ARGB.white(glow), null, s.outlineColor);
        }
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }

    /** The rig as a model, posed by its "erupt" clip at the burst's age. */
    static final class Model extends EntityModel<State> {
        private final @Nullable KeyframeAnimation erupt;

        Model(ModelPart root, HeroRig rig) {
            super(root, RenderTypes::entityCutout);
            HeroRig.Clip c = rig.clip("erupt");
            this.erupt = c == null ? null : WildModel.definition(c).bake(root);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            if (erupt != null) erupt.apply((long) (Math.min(s.age, 1.4F) * 1000.0F), 1.0F);
        }
    }
}
