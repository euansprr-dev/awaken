package dev.awaken.veil.client;

import dev.awaken.Awaken;
import dev.awaken.veil.entity.VeilSpirit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** One of the dead: a translucent, softly glowing figure tinted by who they were (hunter, keeper, sleeper, ascendant). */
public final class VeilSpiritRenderer extends HumanoidMobRenderer<VeilSpirit, VeilSpiritRenderer.State, HumanoidModel<VeilSpiritRenderer.State>> {
    private static final Identifier TEXTURE = Awaken.id("textures/entity/veil_spirit.png");
    private static final int[] TINTS = {0xC3CBFF, 0xA7CDE3, 0xF6C6E6, 0xD6ECFF};

    public static final class State extends HumanoidRenderState {
        int faction;
        float bob;
    }

    public VeilSpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.0F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VeilSpirit e, State s, float pt) {
        super.extractRenderState(e, s, pt);
        s.faction = e.faction();
        s.bob = Mth.sin((e.tickCount + pt) * 0.05F) * 0.08F;
    }

    @Override
    public Identifier getTextureLocation(State s) {
        return TEXTURE;
    }

    @Override
    protected int getModelTint(State s) {
        float breath = 0.6F + 0.15F * Mth.sin(s.ageInTicks * 0.07F);
        int alpha = (int) (255 * breath);
        return (alpha << 24) | TINTS[Math.floorMod(s.faction, TINTS.length)];
    }

    @Override
    protected @Nullable RenderType getRenderType(State s, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        return RenderTypes.entityTranslucentEmissive(TEXTURE);
    }

    @Override
    protected void scale(State s, com.mojang.blaze3d.vertex.PoseStack pose) {
        pose.translate(0F, -s.bob, 0F);
    }

    @Override
    protected int getBlockLightLevel(VeilSpirit entity, BlockPos pos) {
        return 15;
    }
}
