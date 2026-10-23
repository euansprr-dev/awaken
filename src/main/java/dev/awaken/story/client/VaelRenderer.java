package dev.awaken.story.client;

import dev.awaken.Awaken;
import dev.awaken.story.VaelEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Vael renders as a player-shaped hunter with a generated skin. */
public final class VaelRenderer extends HumanoidMobRenderer<VaelEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier SKIN = Awaken.id("textures/entity/vael.png");

    public VaelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return SKIN;
    }
}
