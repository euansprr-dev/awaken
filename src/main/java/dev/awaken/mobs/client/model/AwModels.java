package dev.awaken.mobs.client.model;

import dev.awaken.Awaken;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import dev.awaken.mobs.entity.AwLook;

/**
 * Our own creature models (texture layouts mirrored in tools/art/specs/mobs.py, keep them in sync): the Serpent
 * (Glass Serpent, Sky Leviathan, eels, Cord-Eaters), the Listener (blind, all ears), the Bell (the Bell Warden), the
 * Lantern (the Lantern Broker) and the Shade (the Hollow Herald, the Seed Warden, the Signal's Shadow).
 */
public final class AwModels {
    private AwModels() {}

    public static final ModelLayerLocation SERPENT = layer("serpent");
    public static final ModelLayerLocation LISTENER = layer("listener");
    public static final ModelLayerLocation BELL = layer("bell");
    public static final ModelLayerLocation LANTERN = layer("lantern");
    public static final ModelLayerLocation SHADE = layer("shade");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Awaken.id("mobs_" + name), "main");
    }

    /** The per-frame look, put on the render state by our renderers (models are shared, so read it from the state). */
    public static final ContextKey<AwLook> LOOK = new ContextKey<>(Awaken.id("mobs_look"));

    /** Telegraph intensity 0..1 of the creature being drawn. */
    static float tele(LivingEntityRenderState s) {
        AwLook l = s.getRenderData(LOOK);
        return l == null ? 0.0F : l.telegraph;
    }

    private static float walk(LivingEntityRenderState s, float mul) {
        return Mth.cos(s.walkAnimationPos * 0.6662F) * 1.2F * s.walkAnimationSpeed * mul;
    }

    // ---- Serpent ------------------------------------------------------------------------------------------------------

    public static final class Serpent extends EntityModel<LivingEntityRenderState> {
        private final ModelPart head, jaw;
        private final ModelPart[] segments = new ModelPart[6];

        public Serpent(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            head = root.getChild("head");
            jaw = head.getChild("jaw");
            ModelPart p = root.getChild("seg0");
            segments[0] = p;
            for (int i = 1; i < 6; i++) {
                p = p.getChild("seg" + i);
                segments[i] = p;
            }
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -3, -10, 8, 6, 10)
                    .texOffs(36, 0).addBox(-0.5F, -7, -8, 1, 4, 8), PartPose.offset(0, 16, 0));
            head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 16).addBox(-3.5F, 0, -8, 7, 2, 8), PartPose.offset(0, 2.5F, -1));
            PartDefinition s0 = root.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 26).addBox(-3, -3, 0, 6, 6, 8), PartPose.offset(0, 16, 0));
            PartDefinition s1 = s0.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 26).addBox(-3, -3, 0, 6, 6, 8), PartPose.offset(0, 0, 8));
            PartDefinition s2 = s1.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(28, 26).addBox(-2.5F, -2.5F, 0, 5, 5, 8), PartPose.offset(0, 0, 8));
            PartDefinition s3 = s2.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(28, 26).addBox(-2.5F, -2.5F, 0, 5, 5, 8), PartPose.offset(0, 0, 8));
            PartDefinition s4 = s3.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(0, 42).addBox(-2, -2, 0, 4, 4, 8), PartPose.offset(0, 0, 8));
            s4.addOrReplaceChild("seg5", CubeListBuilder.create().texOffs(26, 42).addBox(-1, -1, 0, 2, 2, 8)
                    .texOffs(46, 42).addBox(-0.5F, -3, 3, 1, 6, 6), PartPose.offset(0, 0, 8));
            return LayerDefinition.create(mesh, 64, 64);
        }


        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float tele = tele(s);
            head.yRot = s.yRot * Mth.DEG_TO_RAD * 0.6F;
            head.xRot = s.xRot * Mth.DEG_TO_RAD * 0.5F;
            jaw.xRot = 0.15F + 0.25F * (0.5F + 0.5F * Mth.sin(age * 0.2F)) + tele * 0.6F;
            float speed = 0.25F + s.walkAnimationSpeed * 0.4F;
            for (int i = 0; i < segments.length; i++) {
                segments[i].yRot = Mth.sin(age * speed - i * 0.9F) * 0.32F;
                segments[i].xRot = Mth.cos(age * speed * 0.7F - i * 0.7F) * 0.08F;
            }
            head.y = 16 + Mth.sin(age * 0.1F) * 0.8F;
        }
    }

    // ---- Listener -----------------------------------------------------------------------------------------------------

    public static final class Listener extends EntityModel<LivingEntityRenderState> {
        private final ModelPart head, earL, earR, armL, armR, legL, legR, body;

        public Listener(ModelPart root) {
            super(root);
            body = root.getChild("body");
            head = root.getChild("head");
            earL = head.getChild("ear_left");
            earR = head.getChild("ear_right");
            armL = root.getChild("arm_left");
            armR = root.getChild("arm_right");
            legL = root.getChild("leg_left");
            legR = root.getChild("leg_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4, -14, -2, 8, 14, 4), PartPose.offset(0, 8, 0));
            PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -6, 0));
            head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(32, 0).addBox(0, -10, -3, 1, 12, 10), PartPose.offset(4, -2, 0));
            head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(32, 22).addBox(-1, -10, -3, 1, 12, 10), PartPose.offset(-4, -2, 0));
            root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(0, 36).addBox(-3, -1, -1.5F, 3, 20, 3), PartPose.offset(-4, -5, 0));
            root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(12, 36).addBox(0, -1, -1.5F, 3, 20, 3), PartPose.offset(4, -5, 0));
            root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(24, 44).addBox(-1.5F, 0, -1.5F, 3, 16, 3), PartPose.offset(-2, 8, 0));
            root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(36, 44).addBox(-1.5F, 0, -1.5F, 3, 16, 3), PartPose.offset(2, 8, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }


        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float tele = tele(s);
            head.yRot = s.yRot * Mth.DEG_TO_RAD;
            head.zRot = Mth.sin(age * 0.07F) * 0.25F;
            head.xRot = 0.15F;
            body.xRot = 0.18F;
            earL.yRot = -0.2F - Mth.sin(age * 0.3F) * 0.12F - tele * 0.5F;
            earR.yRot = 0.2F + Mth.sin(age * 0.3F + 1) * 0.12F + tele * 0.5F;
            float w = walk(s, 1.0F);
            legR.xRot = w;
            legL.xRot = -w;
            armR.xRot = -w * 0.6F - tele * 2.2F;
            armL.xRot = w * 0.6F - tele * 2.2F;
            armR.zRot = 0.08F + Mth.sin(age * 0.09F) * 0.05F;
            armL.zRot = -0.08F - Mth.sin(age * 0.09F) * 0.05F;
        }
    }

    // ---- Bell (the Bell Warden) ----------------------------------------------------------------------------------------

    public static final class Bell extends EntityModel<LivingEntityRenderState> {
        private final ModelPart bell, clapper, armL, armR, legL, legR;

        public Bell(ModelPart root) {
            super(root);
            bell = root.getChild("bell");
            clapper = bell.getChild("clapper");
            armL = root.getChild("arm_left");
            armR = root.getChild("arm_right");
            legL = root.getChild("leg_left");
            legR = root.getChild("leg_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 64).addBox(-3, 0, -3, 6, 14, 6), PartPose.offset(-4, 10, 0));
            root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(24, 64).addBox(-3, 0, -3, 6, 14, 6), PartPose.offset(4, 10, 0));
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 32).addBox(-8, -16, -5, 16, 16, 10), PartPose.offset(0, 10, 0));
            root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(48, 64).addBox(-5, -2, -2.5F, 5, 20, 5), PartPose.offset(-8, -4, 0));
            root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(68, 64).addBox(0, -2, -2.5F, 5, 20, 5), PartPose.offset(8, -4, 0));
            PartDefinition bell = root.addOrReplaceChild("bell", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -14, -7, 14, 13, 14)
                    .texOffs(56, 0).addBox(-9, -3, -9, 18, 3, 18)
                    .texOffs(100, 32).addBox(-1.5F, -18, -1.5F, 3, 4, 3), PartPose.offset(0, -6, 0));
            bell.addOrReplaceChild("clapper", CubeListBuilder.create().texOffs(0, 90).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, -4, 0));
            return LayerDefinition.create(mesh, 128, 128);
        }


        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float tele = tele(s);
            bell.yRot = s.yRot * Mth.DEG_TO_RAD * 0.5F;
            bell.zRot = Mth.sin(age * 0.08F) * 0.06F + Mth.sin(age * 1.2F) * 0.08F * tele;
            clapper.zRot = Mth.sin(age * 0.25F) * 0.4F;
            clapper.xRot = Mth.cos(age * 0.21F) * 0.3F;
            float w = walk(s, 0.7F);
            legR.xRot = w;
            legL.xRot = -w;
            armR.xRot = -w * 0.5F - tele * 2.6F;
            armL.xRot = w * 0.5F - tele * 2.6F;
        }
    }

    // ---- Lantern (the Lantern Broker) ----------------------------------------------------------------------------------

    public static final class Lantern extends EntityModel<LivingEntityRenderState> {
        private final ModelPart head, core, armL, armR, robe;

        public Lantern(ModelPart root) {
            super(root);
            robe = root.getChild("robe");
            head = root.getChild("head");
            core = head.getChild("core");
            armL = root.getChild("arm_left");
            armR = root.getChild("arm_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 20).addBox(-5, -18, -3, 10, 18, 6), PartPose.offset(0, 24, 0));
            root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(32, 20).addBox(-3, -1, -1.5F, 3, 12, 3), PartPose.offset(-5, 7, 0));
            root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(44, 20).addBox(0, -1, -1.5F, 3, 12, 3), PartPose.offset(5, 7, 0));
            PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8)
                    .texOffs(48, 0).addBox(-1, -13, -1, 2, 3, 2), PartPose.offset(0, 6, 0));
            head.addOrReplaceChild("core", CubeListBuilder.create().texOffs(32, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.offset(0, -5, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }


        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float tele = tele(s);
            head.yRot = s.yRot * Mth.DEG_TO_RAD;
            head.y = 6 + Mth.sin(age * 0.12F) * 0.5F;
            core.yRot = age * 0.08F;
            core.xRot = age * 0.05F;
            robe.zRot = Mth.sin(age * 0.06F) * 0.03F;
            float w = walk(s, 0.5F);
            armR.xRot = -0.4F - w - tele * 1.8F;
            armL.xRot = -0.4F + w - tele * 1.8F;
            armR.zRot = 0.1F;
            armL.zRot = -0.1F;
        }
    }

    // ---- Shade (the Herald, the Seed Warden, the Signal's Shadow) ------------------------------------------------------

    public static final class Shade extends EntityModel<LivingEntityRenderState> {
        private final ModelPart head, halo, armL, armR, torso, robe;

        public Shade(ModelPart root) {
            super(root);
            torso = root.getChild("torso");
            robe = root.getChild("robe");
            head = root.getChild("head");
            halo = root.getChild("halo");
            armL = root.getChild("arm_left");
            armR = root.getChild("arm_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 40).addBox(-4, 0, -2, 8, 12, 4), PartPose.offset(0, 4, 0));
            root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 20).addBox(-5, -14, -3, 10, 14, 6), PartPose.offset(0, 4, 0));
            root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8), PartPose.offset(0, -10, 0));
            root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(32, 20).addBox(-3, -1, -1.5F, 3, 18, 3), PartPose.offset(-5, -9, 0));
            root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(44, 20).addBox(0, -1, -1.5F, 3, 18, 3), PartPose.offset(5, -9, 0));
            root.addOrReplaceChild("halo", CubeListBuilder.create()
                    .texOffs(32, 0).addBox(-6, -0.5F, -6.5F, 12, 1, 1).addBox(-6, -0.5F, 5.5F, 12, 1, 1)
                    .texOffs(32, 4).addBox(-6.5F, -0.5F, -6, 1, 1, 12).addBox(5.5F, -0.5F, -6, 1, 1, 12), PartPose.offset(0, -23, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }


        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float tele = tele(s);
            float bob = Mth.sin(age * 0.08F) * 1.2F;
            torso.y = 4 + bob;
            robe.y = 4 + bob;
            robe.xRot = 0.15F + Mth.sin(age * 0.05F) * 0.05F;
            head.y = -10 + bob;
            head.yRot = s.yRot * Mth.DEG_TO_RAD;
            head.xRot = s.xRot * Mth.DEG_TO_RAD * 0.5F;
            halo.y = -23 + bob;
            halo.yRot = age * (0.04F + tele * 0.3F);
            armR.y = -9 + bob;
            armL.y = -9 + bob;
            armR.xRot = -0.2F + Mth.sin(age * 0.07F) * 0.1F - tele * 2.0F;
            armL.xRot = -0.2F - Mth.sin(age * 0.07F) * 0.1F - tele * 2.0F;
            armR.zRot = 0.25F + tele * 0.4F;
            armL.zRot = -0.25F - tele * 0.4F;
        }
    }
}
