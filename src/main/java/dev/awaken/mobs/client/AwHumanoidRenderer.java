package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Trait;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/** Zombie, skeleton, piglin and player-shaped creatures: held items, armour, our skin and glow layers. */
class AwHumanoidRenderer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends HumanoidMobRenderer<AwakenMob, S, M> {
    protected final MobDef def;
    private final Function<S, Identifier> base;
    private final Supplier<S> factory;
    private final AwRender.@Nullable Filler<S> filler;

    AwHumanoidRenderer(EntityRendererProvider.Context ctx, MobDef def, M model, @Nullable ArmorModelSet<M> armor, Supplier<S> factory,
                       Function<S, Identifier> base, AwRender.@Nullable Filler<S> filler) {
        super(ctx, model, 0.5F);
        this.def = def;
        this.base = base;
        this.factory = factory;
        this.filler = filler;
        if (armor != null) addLayer(new HumanoidArmorLayer<>(this, armor, ctx.getEquipmentRenderer()));
        if (!def.model.ownTexture()) addLayer(new AwLayers.Skin<>(this, def));
        addLayer(new AwLayers.Glow<>(this, def));
    }

    @Override
    public S createRenderState() {
        return factory.get();
    }

    @Override
    public void extractRenderState(AwakenMob mob, S state, float partialTicks) {
        super.extractRenderState(mob, state, partialTicks);
        AwLook l = AwRender.extract(mob, state, partialTicks);
        if (filler != null) filler.fill(mob, state, partialTicks, l);
    }

    @Override
    public Identifier getTextureLocation(S state) {
        return base.apply(state);
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
    protected void scale(S state, PoseStack pose) {
        super.scale(state, pose);
    }
}
