package dev.awaken.descent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.descent.entity.ShadowSelf;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Your shadow: your own skin, drawn dark and see-through. */
final class ShadowSelfRenderer extends SkinnedMobRenderer<ShadowSelf, SkinRenderState> {
    private final PlayerSkinRenderCache skins;

    ShadowSelfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0.5F);
        this.skins = ctx.getPlayerSkinRenderCache();
    }

    @Override
    public SkinRenderState createRenderState() {
        return new SkinRenderState();
    }

    @Override
    public void extractRenderState(ShadowSelf entity, SkinRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.wear(skins.getOrDefault(entity.profile()).playerSkin());
    }

    @Override
    protected int getModelTint(SkinRenderState state) {
        return 0xD8221A30;
    }

    @Override
    protected RenderType getRenderType(SkinRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return RenderTypes.entityTranslucent(getTextureLocation(state));
    }

    @Override
    protected void scale(SkinRenderState state, PoseStack pose) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
