package dev.awaken.descent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.descent.entity.GraveEcho;
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

/** A Grave Echo: a full-bright ghost billboard that bobs and breathes. */
final class GraveEchoRenderer extends EntityRenderer<GraveEcho, EntityRenderState> {
    private static final Identifier TEX = Awaken.id("textures/entity/grave_echo.png");
    private static final RenderType TYPE = RenderTypes.entityTranslucentEmissive(TEX);

    GraveEchoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    protected int getBlockLightLevel(GraveEcho entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        float t = state.ageInTicks;
        pose.translate(0, 0.9 + Mth.sin(t * 0.08F) * 0.08, 0);
        pose.rotate(Axis.YP.rotationDegrees(180 - camera.yRot));
        float breathe = 1.0F + Mth.sin(t * 0.05F) * 0.04F;
        pose.scale(1.8F * breathe, 1.8F * breathe, 1.8F);
        int alpha = (int) (200 + 40 * Mth.sin(t * 0.11F));
        int colour = (alpha << 24) | 0xFFFFFF;
        out.submitCustomGeometry(pose, TYPE, (p, vc) -> quad(p, vc, colour));
        pose.popPose();
        super.submit(state, pose, out, camera);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int colour) {
        vertex(vc, pose, -0.5F, -0.5F, 0, 1, colour);
        vertex(vc, pose, 0.5F, -0.5F, 1, 1, colour);
        vertex(vc, pose, 0.5F, 0.5F, 1, 0, colour);
        vertex(vc, pose, -0.5F, 0.5F, 0, 0, colour);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int colour) {
        vc.addVertex(pose, x, y, 0).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }
}
