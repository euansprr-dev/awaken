package dev.awaken.powers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.powers.content.PowerDrone;
import dev.awaken.powers.content.ShadowSoldier;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Renderers for power summons. They reuse vanilla models and textures, tinted in the renderer. */
final class SummonRenderers {
    private SummonRenderers() {}

    /** Shadow soldiers: wither-skeleton bodies steeped in violet shadow. */
    static final class Soldier extends HumanoidMobRenderer<ShadowSoldier, SkeletonRenderState, SkeletonModel<SkeletonRenderState>> {
        private static final Identifier TEX = Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");

        Soldier(EntityRendererProvider.Context ctx) {
            super(ctx, new SkeletonModel<>(ctx.bakeLayer(ModelLayers.WITHER_SKELETON)), 0.5f);
        }

        @Override
        public SkeletonRenderState createRenderState() {
            return new SkeletonRenderState();
        }

        @Override
        public void extractRenderState(ShadowSoldier entity, SkeletonRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.isAggressive = entity.isAggressive();
        }

        @Override
        public Identifier getTextureLocation(SkeletonRenderState state) {
            return TEX;
        }

        @Override
        protected int getModelTint(SkeletonRenderState state) {
            return 0xFFB49BFF;
        }

        @Override
        protected int getBlockLightLevel(ShadowSoldier entity, BlockPos pos) {
            return Math.max(8, super.getBlockLightLevel(entity, pos));
        }
    }

    static final class DroneState extends VexRenderState {
        boolean turret;
        boolean overcharged;
    }

    /** Drones and turrets: a small vex frame tinted cyan (drone) or amber (turret). */
    static final class Drone extends MobRenderer<PowerDrone, DroneState, VexModel> {
        private static final Identifier TEX = Identifier.withDefaultNamespace("textures/entity/illager/vex.png");

        Drone(EntityRendererProvider.Context ctx) {
            super(ctx, new VexModel(ctx.bakeLayer(ModelLayers.VEX)), 0.25f);
        }

        @Override
        public DroneState createRenderState() {
            return new DroneState();
        }

        @Override
        public void extractRenderState(PowerDrone entity, DroneState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.turret = entity.isTurret();
            state.overcharged = entity.isCurrentlyGlowing();
        }

        @Override
        public Identifier getTextureLocation(DroneState state) {
            return TEX;
        }

        @Override
        protected int getModelTint(DroneState state) {
            if (state.overcharged) return 0xFFFFFFA0;
            return state.turret ? 0xFFFFC27A : 0xFF8AF0FF;
        }

        @Override
        protected int getBlockLightLevel(PowerDrone entity, BlockPos pos) {
            return 15;
        }

        @Override
        protected void scale(DroneState state, PoseStack poseStack) {
            float s = state.turret ? 1.0f : 0.7f;
            poseStack.scale(s, s, s);
        }
    }
}
