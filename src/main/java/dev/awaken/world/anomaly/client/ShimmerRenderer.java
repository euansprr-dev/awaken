package dev.awaken.world.anomaly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.world.anomaly.GroveVariant;
import dev.awaken.world.anomaly.ShimmerEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * A shimmer grove: a ring of faint, wavering light curtains around its edge in the variant's colour, a soft column at
 * its heart, and (whisper groves) the spirit itself floating there. Brighter once someone has stepped in; it thins out
 * over a second and a half when the grove ends.
 */
final class ShimmerRenderer extends EntityRenderer<ShimmerEntity, ShimmerRenderer.State> {
    private static final Identifier CURTAIN = Awaken.id("textures/entity/anomaly_shimmer.png");
    private static final Identifier SPIRIT = Awaken.id("textures/entity/anomaly_spirit.png");
    /** The beam pipeline: soft translucent gradients with no alpha cut-off (back faces are culled, so panels draw both ways). */
    private static final RenderType CURTAIN_TYPE = RenderTypes.beaconBeam(CURTAIN, true);
    private static final RenderType SPIRIT_TYPE = RenderTypes.entityTranslucentEmissive(SPIRIT);
    static final int PANELS = 18;

    static final class State extends EntityRenderState {
        int variant;
        boolean active;
        float fade = 1.0F;
    }

    ShimmerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShimmerEntity e, State s, float partialTicks) {
        super.extractRenderState(e, s, partialTicks);
        s.variant = e.variant().ordinal();
        s.active = e.active();
        float in = Math.min(1.0F, (e.tickCount + partialTicks) / 30.0F);
        float out = e.fadingAge >= 0 ? Math.max(0.0F, 1.0F - (e.fadingAge + partialTicks) / 30.0F) : 1.0F;
        s.fade = in * out;
    }

    @Override
    protected int getBlockLightLevel(ShimmerEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    protected AABB getBoundingBoxForCulling(ShimmerEntity entity, float partialTicks) {
        double r = ShimmerEntity.radius() + 1.0;
        return entity.getInterpolatedBoundingBox(partialTicks).inflate(r, 0.0, r).expandTowards(0, 4.0, 0).expandTowards(0, -1.5, 0);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.fade > 0.01F) {
            GroveVariant v = GroveVariant.of(s.variant);
            int rgb = v.colour & 0xFFFFFF;
            float t = s.ageInTicks;
            float base = (s.active ? 0.40F : 0.24F) * s.fade;
            float r = (float) ShimmerEntity.radius();
            for (int i = 0; i < PANELS; i++) {
                double a0 = i * Math.PI * 2 / PANELS, a1 = (i + 1) * Math.PI * 2 / PANELS;
                float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                float alpha = base * (0.6F + 0.4F * Mth.sin(t * 0.07F + i * 1.3F));
                float h = 2.8F + 0.5F * Mth.sin(t * 0.045F + i * 0.9F);
                int argb = ((int) (Mth.clamp(alpha, 0F, 1F) * 255) << 24) | rgb;
                out.submitCustomGeometry(pose, CURTAIN_TYPE, (p, vc) -> panel(p, vc, x0, z0, x1, z1, -0.95F, h, argb));
            }
            // the heart: a soft column that turns to face you
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(180.0F - camera.yRot));
            int heart = ((int) (Mth.clamp(base * 1.3F, 0F, 1F) * 255) << 24) | rgb;
            out.submitCustomGeometry(pose, CURTAIN_TYPE, (p, vc) -> panel(p, vc, -0.7F, 0F, 0.7F, 0F, -0.95F, 3.6F, heart));
            pose.popPose();
            if (v == GroveVariant.WHISPER) {
                pose.pushPose();
                pose.translate(0.0F, 0.9F + 0.15F * Mth.sin(t * 0.08F), 0.0F);
                pose.rotate(camera.orientation);
                float size = 0.6F * s.fade;
                int spirit = ((int) (230 * s.fade) << 24) | 0xFFFFFF;
                out.submitCustomGeometry(pose, SPIRIT_TYPE, (p, vc) -> RiftRenderer.quad(p, vc, -size, -size, size, size * 1.4F, spirit));
                pose.popPose();
            }
        }
        super.submit(s, pose, out, camera);
    }

    /** A vertical quad from (x0, z0) to (x1, z1), bottom y0 to top y1, both windings; the texture fades upward. */
    static void panel(PoseStack.Pose pose, VertexConsumer vc, float x0, float z0, float x1, float z1, float y0, float y1, int argb) {
        vertex(vc, pose, x0, y0, z0, 0F, 1F, argb);
        vertex(vc, pose, x1, y0, z1, 1F, 1F, argb);
        vertex(vc, pose, x1, y1, z1, 1F, 0F, argb);
        vertex(vc, pose, x0, y1, z0, 0F, 0F, argb);
        vertex(vc, pose, x0, y1, z0, 0F, 0F, argb);
        vertex(vc, pose, x1, y1, z1, 1F, 0F, argb);
        vertex(vc, pose, x1, y0, z1, 1F, 1F, argb);
        vertex(vc, pose, x0, y0, z0, 0F, 1F, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }
}
