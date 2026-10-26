package dev.awaken.world.anomaly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.world.anomaly.RiftEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;

/**
 * A rift tear's entity renderer. The tear itself, its halo, pool, column, motes and the core you strike are light,
 * drawn by {@link RiftGlow}'s additive pass (so they glow by day and feed Lens's bloom); this only keeps the entity
 * full-bright for anything vanilla still draws for it.
 */
final class RiftRenderer extends EntityRenderer<RiftEntity, EntityRenderState> {
    RiftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    protected int getBlockLightLevel(RiftEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void submit(EntityRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        super.submit(s, pose, out, camera);
    }

    /** A camera-space quad, full-bright (the mote, star and shimmer renderers draw theirs with it). */
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
