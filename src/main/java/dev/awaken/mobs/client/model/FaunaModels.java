package dev.awaken.mobs.client.model;

import dev.awaken.Awaken;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.FaunaMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Bodies of the Awakened layer's overworld creatures: the Lumen Moth, the stag (Frost Stag, the Old Antler), the
 * Mossback tortoise, the Dune Ray and the Glass Scarab. Texture layouts are mirrored box for box in
 * tools/art/specs/fauna.py (keep them in sync). Poses come from {@link AwLook#fauna} ({@link FaunaMob}'s bits).
 */
public final class FaunaModels {
    private FaunaModels() {}

    public static final ModelLayerLocation MOTH = layer("moth");
    public static final ModelLayerLocation STAG = layer("stag");
    public static final ModelLayerLocation TORTOISE = layer("tortoise");
    public static final ModelLayerLocation RAY = layer("ray");
    public static final ModelLayerLocation SCARAB = layer("scarab");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Awaken.id("fauna_" + name), "main");
    }

    private static AwLook look(LivingEntityRenderState s) {
        AwLook l = s.getRenderData(AwModels.LOOK);
        return l != null ? l : NONE;
    }

    private static final AwLook NONE = new AwLook();

    private static boolean has(LivingEntityRenderState s, int bit) {
        return (look(s).fauna & bit) != 0;
    }

    private static float walk(LivingEntityRenderState s, float rate, float amp) {
        return Mth.cos(s.walkAnimationPos * 0.6662F * rate) * amp * s.walkAnimationSpeed;
    }

    // ---- Lumen Moth --------------------------------------------------------------------------------------------------

    public static final class Moth extends EntityModel<LivingEntityRenderState> {
        private final ModelPart body, head, antL, antR, wingL, wingR, hindL, hindR;

        public Moth(ModelPart root) {
            super(root);
            body = root.getChild("body");
            head = body.getChild("head");
            antL = head.getChild("antenna_left");
            antR = head.getChild("antenna_right");
            wingL = body.getChild("wing_left");
            wingR = body.getChild("wing_right");
            hindL = body.getChild("hind_left");
            hindR = body.getChild("hind_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3)
                    .texOffs(0, 6).addBox(-1.5F, -1.0F, 1.5F, 3, 3, 5), PartPose.offset(0, 18, 0));
            PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 0).addBox(-1, -1, -2, 2, 2, 2), PartPose.offset(0, -0.5F, -1.5F));
            head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(24, 0).addBox(0, -3, 0, 3, 3, 0), PartPose.offsetAndRotation(0.3F, -0.8F, -1.5F, -0.5F, -0.3F, 0.35F));
            head.addOrReplaceChild("antenna_right", CubeListBuilder.create().mirror().texOffs(24, 0).addBox(-3, -3, 0, 3, 3, 0), PartPose.offsetAndRotation(-0.3F, -0.8F, -1.5F, -0.5F, 0.3F, -0.35F));
            body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 14).addBox(0, 0, -2, 10, 0, 7), PartPose.offset(1.5F, -1.0F, -0.5F));
            body.addOrReplaceChild("wing_right", CubeListBuilder.create().mirror().texOffs(0, 14).addBox(-10, 0, -2, 10, 0, 7), PartPose.offset(-1.5F, -1.0F, -0.5F));
            body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(0, 22).addBox(0, 0, 0, 7, 0, 6), PartPose.offset(1.5F, -0.6F, 1.5F));
            body.addOrReplaceChild("hind_right", CubeListBuilder.create().mirror().texOffs(0, 22).addBox(-7, 0, 0, 7, 0, 6), PartPose.offset(-1.5F, -0.6F, 1.5F));
            return LayerDefinition.create(mesh, 64, 32);
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float flap = Mth.sin(age * 1.45F);
            float hind = Mth.sin(age * 1.45F - 0.6F);
            wingL.zRot = -0.15F - flap * 0.75F;
            wingR.zRot = 0.15F + flap * 0.75F;
            hindL.zRot = -0.1F - hind * 0.6F;
            hindR.zRot = 0.1F + hind * 0.6F;
            wingL.yRot = -0.12F;
            wingR.yRot = 0.12F;
            hindL.yRot = -0.4F;
            hindR.yRot = 0.4F;
            body.y = 18 + Mth.sin(age * 0.17F) * 0.9F;
            body.xRot = 0.12F + Mth.sin(age * 0.11F) * 0.05F;
            head.yRot = Motion.yaw(s, 0.8F);
            head.xRot = Motion.pitch(s, 0.5F);
            antL.zRot = 0.35F + Mth.sin(age * 0.3F) * 0.1F;
            antR.zRot = -0.35F - Mth.sin(age * 0.3F + 1) * 0.1F;
            // breathing: the abdomen swells a little; struck: the wings snap up and it reels
            body.yScale = Motion.breathe(s, 0.2F, 0.05F);
            float fl = Motion.flinch(s);
            wingL.zRot -= fl * 0.7F;
            wingR.zRot += fl * 0.7F;
            body.zRot = Motion.shudder(s, 0.4F);
            body.xRot -= fl * 0.35F;
        }
    }

    // ---- Stag (Frost Stag, the Old Antler) -----------------------------------------------------------------------------

    public static final class Stag extends EntityModel<LivingEntityRenderState> {
        private final ModelPart neck, head, tail, legFL, legFR, legHL, legHR;

        public Stag(ModelPart root) {
            super(root);
            neck = root.getChild("neck");
            head = neck.getChild("head");
            tail = root.getChild("tail");
            legFL = root.getChild("leg_front_left");
            legFR = root.getChild("leg_front_right");
            legHL = root.getChild("leg_hind_left");
            legHR = root.getChild("leg_hind_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -8, 8, 8, 16), PartPose.offset(0, 9, 0));
            PartDefinition neck = root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(48, 0).addBox(-2, -9, -2, 4, 10, 4), PartPose.offsetAndRotation(0, 7, -6.5F, 0.45F, 0, 0));
            PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 24).addBox(-2.5F, -3, -5, 5, 5, 6)
                    .texOffs(22, 24).addBox(-1.5F, -1, -8, 3, 3, 3), PartPose.offsetAndRotation(0, -9, 0, -0.45F, 0, 0));
            head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(34, 24).addBox(0, -0.5F, -0.5F, 3, 2, 1), PartPose.offsetAndRotation(2.5F, -2, 0, 0, 0, -0.35F));
            head.addOrReplaceChild("ear_right", CubeListBuilder.create().mirror().texOffs(34, 24).addBox(-3, -0.5F, -0.5F, 3, 2, 1), PartPose.offsetAndRotation(-2.5F, -2, 0, 0, 0, 0.35F));
            antler(head, "left", 1);
            antler(head, "right", -1);
            CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 40).addBox(-1.5F, 0, -1.5F, 3, 12, 3);
            CubeListBuilder legM = CubeListBuilder.create().mirror().texOffs(0, 40).addBox(-1.5F, 0, -1.5F, 3, 12, 3);
            root.addOrReplaceChild("leg_front_left", leg, PartPose.offset(2.5F, 12, -6));
            root.addOrReplaceChild("leg_front_right", legM, PartPose.offset(-2.5F, 12, -6));
            root.addOrReplaceChild("leg_hind_left", leg, PartPose.offset(2.5F, 12, 6));
            root.addOrReplaceChild("leg_hind_right", legM, PartPose.offset(-2.5F, 12, 6));
            root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 40).addBox(-1.5F, 0, 0, 3, 4, 2), PartPose.offsetAndRotation(0, 6, 7.5F, 0.6F, 0, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }

        /** A main beam with three tines and a crown point; side = 1 left, -1 right. */
        private static void antler(PartDefinition head, String name, int side) {
            CubeListBuilder beamC = side > 0 ? CubeListBuilder.create().texOffs(44, 24) : CubeListBuilder.create().mirror().texOffs(44, 24);
            PartDefinition beam = head.addOrReplaceChild("antler_" + name, beamC.addBox(-0.5F, -9, -0.5F, 1, 9, 1),
                    PartPose.offsetAndRotation(side * 1.5F, -3, -1, -0.3F, 0, -side * 0.55F));
            CubeListBuilder tineC = side > 0 ? CubeListBuilder.create().texOffs(48, 24) : CubeListBuilder.create().mirror().texOffs(48, 24);
            beam.addOrReplaceChild("tine_a_" + name, tineC.addBox(-0.5F, -5, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(0, -3, 0, -1.0F, 0, side * 0.25F));
            CubeListBuilder tineB = side > 0 ? CubeListBuilder.create().texOffs(48, 24) : CubeListBuilder.create().mirror().texOffs(48, 24);
            beam.addOrReplaceChild("tine_b_" + name, tineB.addBox(-0.5F, -5, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(0, -6, 0, -0.55F, 0, -side * 0.55F));
            CubeListBuilder tineC2 = side > 0 ? CubeListBuilder.create().texOffs(48, 24) : CubeListBuilder.create().mirror().texOffs(48, 24);
            beam.addOrReplaceChild("tine_c_" + name, tineC2.addBox(-0.5F, -5, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(0, -8, 0, 0.55F, 0, side * 0.4F));
            CubeListBuilder crown = side > 0 ? CubeListBuilder.create().texOffs(52, 24) : CubeListBuilder.create().mirror().texOffs(52, 24);
            beam.addOrReplaceChild("crown_" + name, crown.addBox(-0.5F, -4, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(0, -9, 0, 0.1F, 0, side * 0.7F));
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float w = walk(s, 1.0F, 1.3F);
            legFL.xRot = w;
            legHR.xRot = w;
            legFR.xRot = -w;
            legHL.xRot = -w;
            float tele = look(s).telegraph;
            float neckX = 0.45F;
            float headX = -0.45F + s.xRot * Mth.DEG_TO_RAD * 0.5F;
            if (has(s, FaunaMob.P_GRAZE)) {
                neckX = 1.75F;
                headX = 0.35F + Mth.sin(s.ageInTicks * 0.25F) * 0.08F;
            } else if (has(s, FaunaMob.P_ANGRY) || tele > 0) {
                // Antlers forward.
                neckX = 1.0F + tele * 0.3F;
                headX = -0.1F;
            }
            neck.xRot = neckX;
            neck.yRot = Motion.yaw(s, 0.5F);
            head.yRot = Motion.yaw(s, 0.3F);
            head.xRot = headX;
            tail.xRot = 0.6F + Mth.sin(s.ageInTicks * 0.2F) * 0.1F + s.walkAnimationSpeed * 0.3F;
            // struck: the head flies up and the body shies sideways
            float fl = Motion.flinch(s);
            neck.xRot -= fl * 0.6F;
            neck.zRot = Motion.shudder(s, 0.25F);
        }
    }

    // ---- Mossback (tortoise) -------------------------------------------------------------------------------------------

    public static final class Tortoise extends EntityModel<LivingEntityRenderState> {
        private final ModelPart shell, head, legFL, legFR, legHL, legHR, tail, plantA, plantB;

        public Tortoise(ModelPart root) {
            super(root);
            shell = root.getChild("shell");
            plantA = shell.getChild("plant_a");
            plantB = shell.getChild("plant_b");
            head = root.getChild("head");
            tail = root.getChild("tail");
            legFL = root.getChild("leg_front_left");
            legFR = root.getChild("leg_front_right");
            legHL = root.getChild("leg_hind_left");
            legHR = root.getChild("leg_hind_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition shell = root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -5, -8, 14, 5, 16)
                    .texOffs(0, 21).addBox(-5, -8, -6, 10, 3, 12)
                    .texOffs(44, 27).addBox(-4, -9, -3, 4, 1, 4).addBox(1, -9, 0, 4, 1, 4), PartPose.offset(0, 20, 0));
            shell.addOrReplaceChild("plant_a", CubeListBuilder.create().texOffs(44, 21).addBox(-3, -6, 0, 6, 6, 0), PartPose.offsetAndRotation(1, -8, -1, 0, 0.785F, 0));
            shell.addOrReplaceChild("plant_b", CubeListBuilder.create().texOffs(44, 21).addBox(-3, -6, 0, 6, 6, 0), PartPose.offsetAndRotation(1, -8, -1, 0, -0.785F, 0));
            root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 36).addBox(-2, -2, -5, 4, 4, 5), PartPose.offset(0, 17, -8));
            CubeListBuilder leg = CubeListBuilder.create().texOffs(18, 36).addBox(-2, 0, -2, 4, 5, 4);
            CubeListBuilder legM = CubeListBuilder.create().mirror().texOffs(18, 36).addBox(-2, 0, -2, 4, 5, 4);
            root.addOrReplaceChild("leg_front_left", leg, PartPose.offset(5, 19, -5.5F));
            root.addOrReplaceChild("leg_front_right", legM, PartPose.offset(-5, 19, -5.5F));
            root.addOrReplaceChild("leg_hind_left", leg, PartPose.offset(5, 19, 5.5F));
            root.addOrReplaceChild("leg_hind_right", legM, PartPose.offset(-5, 19, 5.5F));
            root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(34, 36).addBox(-1, 0, 0, 2, 2, 3), PartPose.offset(0, 18, 7.5F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            boolean tucked = has(s, FaunaMob.P_GUARD) || has(s, FaunaMob.P_REST);
            float w = tucked ? 0 : walk(s, 0.7F, 0.9F);
            legFL.xRot = w;
            legHR.xRot = w;
            legFR.xRot = -w;
            legHL.xRot = -w;
            float legY = tucked ? 21.5F : 19;
            legFL.y = legY;
            legFR.y = legY;
            legHL.y = legY;
            legHR.y = legY;
            // struck: the head snaps back into the shell for a moment (the flinch of a tortoise)
            float fl = Motion.flinch(s);
            head.z = tucked ? -5 : -8 + fl * 3.0F + Mth.sin(s.ageInTicks * 0.08F) * 0.3F;
            head.y = tucked ? 18 : 17;
            head.yRot = tucked ? 0 : Motion.yaw(s, 0.8F);
            head.xRot = has(s, FaunaMob.P_GRAZE) ? 0.75F + Mth.sin(s.ageInTicks * 0.3F) * 0.1F : Motion.pitch(s, 0.6F);
            shell.y = tucked ? 22 : 20 + Mth.sin(s.walkAnimationPos * 0.45F) * 0.25F * s.walkAnimationSpeed;
            shell.yScale = Motion.breathe(s, 0.07F, 0.02F);
            shell.zRot = Motion.shudder(s, 0.08F);
            tail.y = tucked ? 21 : 18;
            plantA.zRot = Mth.sin(s.ageInTicks * 0.05F) * 0.06F;
            plantB.zRot = -Mth.sin(s.ageInTicks * 0.05F + 1) * 0.06F;
        }
    }

    // ---- Dune Ray --------------------------------------------------------------------------------------------------

    public static final class Ray extends EntityModel<LivingEntityRenderState> {
        private final ModelPart body, wingL, wingR, tipL, tipR, tail1, tail2;

        public Ray(ModelPart root) {
            super(root);
            body = root.getChild("body");
            wingL = body.getChild("wing_left");
            wingR = body.getChild("wing_right");
            tipL = wingL.getChild("tip_left");
            tipR = wingR.getChild("tip_right");
            tail1 = body.getChild("tail");
            tail2 = tail1.getChild("tail_end");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -1, -6, 8, 2, 11)
                    .texOffs(38, 0).addBox(-3.5F, -0.5F, -9, 2, 1, 3).mirror().addBox(1.5F, -0.5F, -9, 2, 1, 3), PartPose.offset(0, 21, 0));
            PartDefinition wl = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 13).addBox(0, -0.5F, -4, 8, 1, 9), PartPose.offset(4, 0, -1));
            wl.addOrReplaceChild("tip_left", CubeListBuilder.create().texOffs(34, 13).addBox(0, -0.5F, -2.5F, 6, 1, 5), PartPose.offset(8, 0, 0));
            PartDefinition wr = body.addOrReplaceChild("wing_right", CubeListBuilder.create().mirror().texOffs(0, 13).addBox(-8, -0.5F, -4, 8, 1, 9), PartPose.offset(-4, 0, -1));
            wr.addOrReplaceChild("tip_right", CubeListBuilder.create().mirror().texOffs(34, 13).addBox(-6, -0.5F, -2.5F, 6, 1, 5), PartPose.offset(-8, 0, 0));
            PartDefinition t1 = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(38, 4).addBox(-0.5F, -0.5F, 0, 1, 1, 8), PartPose.offset(0, 0, 5));
            t1.addOrReplaceChild("tail_end", CubeListBuilder.create().texOffs(0, 23).addBox(-0.5F, -0.5F, 0, 1, 1, 8)
                    .texOffs(18, 23).addBox(0, -2, 1, 0, 3, 4), PartPose.offset(0, 0, 8));
            return LayerDefinition.create(mesh, 64, 32);
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            float speed = has(s, FaunaMob.P_ANGRY) ? 0.42F : 0.24F;
            float a = Mth.sin(age * speed);
            float b = Mth.sin(age * speed - 0.9F);
            wingL.zRot = a * 0.42F;
            wingR.zRot = -a * 0.42F;
            tipL.zRot = b * 0.5F;
            tipR.zRot = -b * 0.5F;
            tail1.yRot = Mth.sin(age * 0.15F) * 0.25F;
            tail2.yRot = Mth.sin(age * 0.15F - 0.8F) * 0.35F;
            tail1.xRot = -0.08F + look(s).telegraph * 0.9F;
            body.y = 21 + Mth.sin(age * 0.12F) * 0.6F;
            body.xRot = Motion.pitch(s, 0.3F);
            // it turns its whole flat body to face you; struck: it rolls away and the wing tips curl
            body.yRot = Motion.yaw(s, 0.25F);
            body.yScale = Motion.breathe(s, 0.12F, 0.08F);
            float fl = Motion.flinch(s);
            body.zRot = Motion.shudder(s, 0.3F) + fl * 0.25F;
            tipL.zRot -= fl * 0.8F;
            tipR.zRot += fl * 0.8F;
            tail1.yRot += Motion.shudder(s, 0.5F);
        }
    }

    // ---- Glass Scarab --------------------------------------------------------------------------------------------------

    public static final class Scarab extends EntityModel<LivingEntityRenderState> {
        private final ModelPart body, head;
        private final ModelPart[] legs = new ModelPart[6];

        public Scarab(ModelPart root) {
            super(root);
            body = root.getChild("body");
            head = body.getChild("head");
            for (int i = 0; i < 6; i++) legs[i] = body.getChild("leg" + i);
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            // A low body under a high domed shell that comes well down the sides: a beetle, not a table.
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -3.5F, 6, 2, 7)
                    .texOffs(0, 10).addBox(-3.5F, -4.5F, -3, 7, 3, 7), PartPose.offset(0, 22.5F, 0));
            PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-2, -2, -2, 4, 3, 2), PartPose.offset(0, -1, -3.5F));
            head.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(12, 20).addBox(-0.5F, -3, -1, 1, 3, 1), PartPose.offsetAndRotation(0, -1, -1, -0.5F, 0, 0));
            for (int i = 0; i < 3; i++) {
                float z = -2 + i * 2;
                body.addOrReplaceChild("leg" + (i * 2), CubeListBuilder.create().texOffs(16, 20).addBox(0, 0, -0.5F, 3, 1, 1),
                        PartPose.offsetAndRotation(2.5F, -0.5F, z, 0, (i - 1) * -0.5F, 0.8F));
                body.addOrReplaceChild("leg" + (i * 2 + 1), CubeListBuilder.create().mirror().texOffs(16, 20).addBox(-3, 0, -0.5F, 3, 1, 1),
                        PartPose.offsetAndRotation(-2.5F, -0.5F, z, 0, (i - 1) * 0.5F, -0.8F));
            }
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float burrow = look(s).faunaAnim;
            float scuttle = s.walkAnimationPos * 1.8F;
            float amp = 0.45F * Math.min(1.0F, s.walkAnimationSpeed * 1.5F) + burrow * 0.4F;
            for (int i = 0; i < 6; i++) {
                int pair = i / 2;
                float phase = (i % 2 == 0 ? 0 : Mth.PI) + pair * 2.1F;
                legs[i].yRot = (pair - 1) * (i % 2 == 0 ? -0.5F : 0.5F) + Mth.sin(scuttle + phase + (burrow > 0 ? s.ageInTicks * 1.4F : 0)) * amp;
            }
            head.yRot = Motion.yaw(s, 0.7F);
            head.xRot = Motion.pitch(s, 0.4F);
            // Digging in: the beetle sinks out of sight.
            body.y = 22.5F + burrow * 7.0F;
            body.xRot = burrow * 0.35F;
            // breathing under the shell; struck: it clamps down and the legs splay
            float fl = Motion.flinch(s);
            body.yScale = Motion.breathe(s, 0.16F, 0.03F) - fl * 0.12F;
            body.zRot = Motion.shudder(s, 0.2F);
            for (int i = 0; i < 6; i++) legs[i].zRot = (i % 2 == 0 ? 0.8F : -0.8F) * (1.0F - fl * 0.6F);
        }
    }
}
