package dev.awaken.core.client.skin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * A mob drawn like a player: the player mesh at the state's arm width, its skin, held items, head item and elytra.
 * <p>
 * Use this for every non-player body with a player-format skin, never AvatarRenderState: in 26.3
 * EntityRenderDispatcher.getRenderer(state) sends any AvatarRenderState to the vanilla player renderer (picked by
 * state.skin), so the entity's own renderer would extract the state and then never draw it.
 * <p>
 * Extends MobRenderer, not HumanoidMobRenderer: that one's AgeableMobRenderer parent resets the model in submit, and the
 * model here is picked per state (wide or slim). The three layers and the state extraction HumanoidMobRenderer adds are
 * repeated here.
 */
public abstract class SkinnedMobRenderer<T extends Mob, S extends SkinRenderState> extends MobRenderer<T, S, SkinModel<S>> {
    private final SkinModel<S> wide;
    private final SkinModel<S> slim;

    protected SkinnedMobRenderer(EntityRendererProvider.Context ctx, float shadow) {
        this(ctx, new SkinModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), new SkinModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true), shadow);
    }

    private SkinnedMobRenderer(EntityRendererProvider.Context ctx, SkinModel<S> wide, SkinModel<S> slim, float shadow) {
        super(ctx, wide, shadow);
        this.wide = wide;
        this.slim = slim;
        addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getPlayerSkinRenderCache()));
        addLayer(new WingsLayer<>(this, ctx.getModelSet(), ctx.getEquipmentRenderer()));
        addLayer(new ItemInHandLayer<>(this));
    }

    /** Worn armour. Wide and slim share the player armour meshes (LayerDefinitions puts the same set under both). */
    protected final void addArmorLayer(EntityRendererProvider.Context ctx) {
        addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), part -> new HumanoidModel<S>(part)),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public void extractRenderState(T entity, S state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, itemModelResolver);
        state.leftArmPose = getArmPose(entity, HumanoidArm.LEFT);
        state.rightArmPose = getArmPose(entity, HumanoidArm.RIGHT);
    }

    /** As HumanoidMobRenderer: a spear pose while stabbing, else empty (the model poses held items itself). */
    protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
        return HumanoidMobRenderer.usesSpearPose(mob.getItemHeldByArm(arm), arm, mob) ? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.EMPTY;
    }

    @Override
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        model = state.slim ? slim : wide; // per state, the way AgeableMobRenderer picks adult or baby
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(S state) {
        return state.skin;
    }

    @Override
    public Vec3 getRenderOffset(S state) {
        // a player-shaped body crouches like a player (AvatarRenderer lowers it two pixels)
        Vec3 offset = super.getRenderOffset(state);
        return state.isCrouching ? offset.add(0.0, state.scale * -2.0F / 16.0, 0.0) : offset;
    }
}
