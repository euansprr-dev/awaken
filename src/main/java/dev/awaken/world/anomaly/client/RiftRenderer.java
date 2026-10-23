package dev.awaken.world.anomaly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.world.anomaly.RiftEntity;
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
 * A rift tear: a tall full-bright crack that turns to face you, a faint halo behind it, and the core at its foot,
 * which shrinks as it takes hits and flashes when struck. It opens over a second and collapses over a second.
 */
final class RiftRenderer extends EntityRenderer<RiftEntity, RiftRenderer.State> {
    private static final Identifier TEAR = Awaken.id("textures/entity/rift_tear.png");
    private static final Identifier CORE = Awaken.id("textures/entity/rift_core.png");
    private static final RenderType TEAR_TYPE = RenderTypes.entityTranslucentEmissive(TEAR);
    private static final RenderType CORE_TYPE = RenderTypes.entityTranslucentEmissive(CORE);

    static final class State extends EntityRenderState {
        float open = 1.0F;
        float health = 1.0F;
        float flash;
    }

    RiftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RiftEntity e, State s, float partialTicks) {
        super.extractRenderState(e, s, partialTicks);
        float open = Math.min(1.0F, (e.tickCount + partialTicks) / 20.0F);
        if (e.closingAge >= 0) open = Math.max(0.0F, 1.0F - (e.closingAge + partialTicks) / 20.0F);
        s.open = open;
        s.health = e.hitsLeft() / (float) Math.max(1, e.maxHits());
        s.flash = e.flash > 0 ? Math.max(0.0F, (e.flash - partialTicks) / 6.0F) : 0.0F;
    }

    @Override
    protected int getBlockLightLevel(RiftEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    protected AABB getBoundingBoxForCulling(RiftEntity entity, float partialTicks) {
        return entity.getInterpolatedBoundingBox(partialTicks).expandTowards(0, 3.0, 0).inflate(1.0);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.open > 0.01F) {
            float t = s.ageInTicks;
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(180.0F - camera.yRot));
            float w = 0.8F * s.open * (1.0F + 0.07F * Mth.sin(t * 0.7F)) * (0.75F + 0.25F * s.health);
            float h = 3.6F * (0.35F + 0.65F * s.open);
            int alpha = (int) (215 + 40 * Mth.sin(t * 0.31F));
            int tear = (alpha << 24) | 0xFFFFFF;
            int halo = ((int) (70 + 30 * Mth.sin(t * 0.13F)) << 24) | 0xB8C4FF;
            float hw = w * 2.4F, top = h - 0.5F;
            out.submitCustomGeometry(pose, TEAR_TYPE, (p, vc) -> quad(p, vc, -hw, -0.9F, hw, top + 0.4F, halo));
            out.submitCustomGeometry(pose, TEAR_TYPE, (p, vc) -> quad(p, vc, -w, -0.5F, w, top, tear));
            pose.popPose();

            pose.pushPose();
            pose.translate(0.0F, 0.6F, 0.0F);
            pose.rotate(camera.orientation);
            float size = (0.3F + 0.25F * s.health) * s.open * (1.0F + 0.08F * Mth.sin(t * 0.25F)) * (1.0F + 0.35F * s.flash);
            int core = s.flash > 0 ? 0xFFFFFFFF : 0xF0FFFFFF;
            out.submitCustomGeometry(pose, CORE_TYPE, (p, vc) -> quad(p, vc, -size, -size, size, size, core));
            pose.popPose();
        }
        super.submit(s, pose, out, camera);
    }

    static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, int argb) {
        vertex(vc, pose, x0, y0, 0, 1, argb);
        vertex(vc, pose, x1, y0, 1, 1, argb);
        vertex(vc, pose, x1, y1, 1, 0, argb);
        vertex(vc, pose, x0, y1, 0, 0, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        vc.addVertex(pose, x, y, 0).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
    }
}
