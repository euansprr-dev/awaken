package dev.awaken.descent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.descent.entity.ShadowSelf;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Your shadow: your own skin, drawn dark and see-through. */
final class ShadowSelfRenderer extends HumanoidMobRenderer<ShadowSelf, AvatarRenderState, PlayerModel> {
    private final EntityRendererProvider.Context ctx;

    ShadowSelfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.ctx = ctx;
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(ShadowSelf entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.skin = ctx.getPlayerSkinRenderCache().getOrDefault(entity.profile()).playerSkin();
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }

    @Override
    protected int getModelTint(AvatarRenderState state) {
        return 0xD8221A30;
    }

    @Override
    protected RenderType getRenderType(AvatarRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return RenderTypes.entityTranslucent(getTextureLocation(state));
    }

    @Override
    protected void scale(AvatarRenderState state, PoseStack pose) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
