package dev.awaken.world.anomaly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.world.anomaly.FallingStarEntity;
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
import net.minecraft.world.phys.AABB;

/** A falling star: a white-hot point with a wide soft halo, always facing you. Its trail is particles. */
final class StarRenderer extends EntityRenderer<FallingStarEntity, EntityRenderState> {
    private static final Identifier TEX = Awaken.id("textures/entity/anomaly_star.png");
    private static final RenderType TYPE = RenderTypes.entityTranslucentEmissive(TEX);

    StarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    protected int getBlockLightLevel(FallingStarEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    protected AABB getBoundingBoxForCulling(FallingStarEntity entity, float partialTicks) {
        return entity.getInterpolatedBoundingBox(partialTicks).inflate(3.0);
    }

    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        float t = state.ageInTicks;
        pose.pushPose();
        pose.translate(0.0F, 0.25F, 0.0F);
        pose.rotate(camera.orientation);
        float core = 0.9F * (1.0F + 0.1F * Mth.sin(t * 1.3F));
        float halo = 3.2F;
        out.submitCustomGeometry(pose, TYPE, (p, vc) -> RiftRenderer.quad(p, vc, -halo, -halo, halo, halo, 0x66CFE4FF));
        out.submitCustomGeometry(pose, TYPE, (p, vc) -> RiftRenderer.quad(p, vc, -core, -core, core, core, 0xFFFFFFFF));
        pose.popPose();
        super.submit(state, pose, out, camera);
    }
}
