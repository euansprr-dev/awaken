package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Trait;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Any non-humanoid model (vanilla or ours) drawing an AWAKEN creature: tint, translucency, skin and glow layers. */
class AwMobRenderer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends MobRenderer<AwakenMob, S, M> {
    protected final MobDef def;
    private final Identifier base;
    private final Supplier<S> factory;
    private final AwRender.@Nullable Filler<S> filler;
    private AwRender.@Nullable Scaler<S> scaler;
    private boolean fullBright;

    AwMobRenderer(EntityRendererProvider.Context ctx, MobDef def, M model, float shadow, Supplier<S> factory, AwRender.@Nullable Filler<S> filler) {
        super(ctx, model, shadow);
        this.def = def;
        this.base = AwRender.base(def);
        this.factory = factory;
        this.filler = filler;
        if (!def.model.ownTexture()) addLayer(new AwLayers.Skin<>(this, def));
        addLayer(new AwLayers.Glow<>(this, def));
    }

    AwMobRenderer<S, M> scaler(AwRender.Scaler<S> s) {
        this.scaler = s;
        return this;
    }

    AwMobRenderer<S, M> fullBright() {
        this.fullBright = true;
        return this;
    }

    @Override
    public S createRenderState() {
        return factory.get();
    }

    @Override
    public void extractRenderState(AwakenMob mob, S state, float partialTicks) {
        super.extractRenderState(mob, state, partialTicks);
        if (state instanceof HumanoidRenderState h) HumanoidMobRenderer.extractHumanoidRenderState(mob, h, partialTicks, itemModelResolver);
        else if (state instanceof ArmedEntityRenderState a) ArmedEntityRenderState.extractArmedEntityRenderState(mob, a, itemModelResolver, partialTicks);
        AwLook l = AwRender.extract(mob, state, partialTicks);
        if (filler != null) filler.fill(mob, state, partialTicks, l);
    }

    @Override
    public Identifier getTextureLocation(S state) {
        return base;
    }

    @Override
    protected int getModelTint(S state) {
        return AwRender.look(state).tint;
    }

    @Override
    protected @Nullable RenderType getRenderType(S state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (bodyVisible && AwRender.look(state).translucent) return RenderTypes.entityTranslucent(getTextureLocation(state));
        return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
    }

    @Override
    protected float getWhiteOverlayProgress(S state) {
        return AwRender.flash(AwRender.look(state), state.ageInTicks);
    }

    @Override
    public boolean isEntityUpsideDown(AwakenMob mob) {
        return mob.def().has(Trait.UPSIDE_DOWN) || super.isEntityUpsideDown(mob);
    }

    @Override
    protected int getBlockLightLevel(AwakenMob mob, BlockPos pos) {
        return fullBright ? 15 : super.getBlockLightLevel(mob, pos);
    }

    @Override
    protected void scale(S state, PoseStack pose) {
        super.scale(state, pose);
        if (scaler != null) scaler.scale(state, pose, AwRender.look(state));
    }
}
