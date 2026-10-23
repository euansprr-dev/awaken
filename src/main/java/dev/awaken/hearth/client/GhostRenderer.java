package dev.awaken.hearth.client;

import dev.awaken.Awaken;
import dev.awaken.hearth.training.TrainingGhost;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The best-lap ghost: a pale, see-through runner. */
public final class GhostRenderer extends HumanoidMobRenderer<TrainingGhost, AvatarRenderState, PlayerModel> {
    private static final Identifier TEX = Awaken.id("textures/entity/training_ghost.png");

    public GhostRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0f);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(TrainingGhost ghost, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(ghost, state, partialTicks);
        state.showCape = false;
        state.showHat = false;
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return TEX;
    }

    @Override
    protected @Nullable RenderType getRenderType(AvatarRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        return RenderTypes.entityTranslucent(TEX);
    }

    @Override
    protected int getModelTint(AvatarRenderState state) {
        return 0x88CFEFFF;
    }

    @Override
    protected boolean shouldShowName(TrainingGhost entity, double distanceToCameraSq) {
        return distanceToCameraSq < 24 * 24;
    }
}
