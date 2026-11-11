package dev.awaken.body.ascent.client;

import dev.awaken.Awaken;
import dev.awaken.body.ascent.LessonFigure;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * The Lesson's figure, drawn as light (SPEC §71; the open item from The Way Up's page: the Gauntlet's ghost in this role
 * was a solid copy of the player's skin with a shadow). The player's own body shape (wide or slim arms) in one pale gold
 * with a slow brightening from feet to head, lit by nothing and lighting nothing: no shade on its faces, no shadow under
 * it, see-through. A shape, not a person.
 */
public final class LessonFigureRenderer extends SkinnedMobRenderer<LessonFigure, SkinRenderState> {
    /** A 64x64 player-layout texture of plain pale gold (tools/art: ascent_figure.py). */
    private static final Identifier LIGHT = Awaken.id("textures/entity/ascent/lesson_figure.png");
    /** ARGB over the texture: a little over two thirds there. */
    private static final int TINT = 0xB4FFFFFF;

    public LessonFigureRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0F);
    }

    @Override
    public SkinRenderState createRenderState() {
        return new SkinRenderState();
    }

    @Override
    public void extractRenderState(LessonFigure entity, SkinRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        var me = Minecraft.getInstance().player;
        state.wear(LIGHT, me != null && me.getSkin().model() == PlayerModelType.SLIM);
    }

    @Override
    protected int getModelTint(SkinRenderState state) {
        return TINT;
    }

    /** Lit by nothing around it. */
    @Override
    protected int getBlockLightLevel(LessonFigure entity, BlockPos pos) {
        return 15;
    }

    @Override
    protected @Nullable RenderType getRenderType(SkinRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        return RenderTypes.entityTranslucentEmissive(LIGHT);
    }

    @Override
    protected float getShadowRadius(SkinRenderState state) {
        return 0F;
    }
}
