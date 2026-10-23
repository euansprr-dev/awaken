package dev.awaken.world.anomaly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.world.anomaly.AnomalyMote;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** A procession light: a small warm glow that always faces you and breathes. */
final class MoteRenderer extends EntityRenderer<AnomalyMote, EntityRenderState> {
    private static final Identifier TEX = Awaken.id("textures/entity/anomaly_mote.png");
    private static final RenderType TYPE = RenderTypes.entityTranslucentEmissive(TEX);

    MoteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    protected int getBlockLightLevel(AnomalyMote entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        float t = state.ageInTicks;
        pose.pushPose();
        pose.translate(0.0F, 0.15F, 0.0F);
        pose.rotate(camera.orientation);
        float size = 0.32F * (1.0F + 0.12F * Mth.sin(t * 0.21F)) * Math.min(1.0F, t / 15.0F);
        int alpha = (int) (200 + 50 * Mth.sin(t * 0.17F));
        int colour = (alpha << 24) | 0xFFFFFF;
        out.submitCustomGeometry(pose, TYPE, (p, vc) -> RiftRenderer.quad(p, vc, -size, -size, size, size, colour));
        float glow = size * 2.6F;
        out.submitCustomGeometry(pose, TYPE, (p, vc) -> RiftRenderer.quad(p, vc, -glow, -glow, glow, glow, 0x38FFE6B0));
        pose.popPose();
        super.submit(state, pose, out, camera);
    }
}
