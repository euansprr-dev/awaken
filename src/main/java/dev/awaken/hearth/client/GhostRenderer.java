package dev.awaken.hearth.client;

import dev.awaken.Awaken;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.hearth.training.TrainingGhost;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The best-lap ghost: a pale, see-through runner. */
public final class GhostRenderer extends SkinnedMobRenderer<TrainingGhost, SkinRenderState> {
    private static final Identifier TEX = Awaken.id("textures/entity/training_ghost.png");

    public GhostRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0f);
    }

    @Override
    public SkinRenderState createRenderState() {
        return new SkinRenderState();
    }

    @Override
    public void extractRenderState(TrainingGhost ghost, SkinRenderState state, float partialTicks) {
        super.extractRenderState(ghost, state, partialTicks);
        state.wear(TEX, false);
        state.showHat = false;
    }

    @Override
    protected @Nullable RenderType getRenderType(SkinRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        return RenderTypes.entityTranslucent(TEX);
    }

    @Override
    protected int getModelTint(SkinRenderState state) {
        return 0x88CFEFFF;
    }

    @Override
    protected boolean shouldShowName(TrainingGhost entity, double distanceToCameraSq) {
        return distanceToCameraSq < 24 * 24;
    }
}
