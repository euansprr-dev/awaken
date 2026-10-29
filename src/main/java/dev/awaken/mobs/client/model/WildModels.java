package dev.awaken.mobs.client.model;

import dev.awaken.Awaken;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.life.LifeModel;
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
 * The P2 creatures' own bodies, where a vanilla one would look wrong: the lizard (Mudskipper, Crested Basilisk), the
 * snail (Gloam Snail, Sporeback Snail), the walking mushroom (Shroomlet, the Mycelord) and the marmot (Scree Marmot).
 * Texture layouts are mirrored box for box in tools/art/specs/fauna2.py (keep them in sync). Poses come from
 * {@link AwLook#fauna} ({@link FaunaMob}'s bits).
 */
public final class WildModels {
    private WildModels() {}

    public static final ModelLayerLocation LIZARD = layer("lizard");
    public static final ModelLayerLocation SNAIL = layer("snail");
    public static final ModelLayerLocation MUSHROOM = layer("mushroom");
    public static final ModelLayerLocation MARMOT = layer("marmot");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Awaken.id("fauna_" + name), "main");
    }

    private static final AwLook NONE = new AwLook();

    private static AwLook look(LivingEntityRenderState s) {
        AwLook l = s.getRenderData(AwModels.LOOK);
        return l != null ? l : NONE;
    }

    private static boolean has(LivingEntityRenderState s, int bit) {
        return (look(s).fauna & bit) != 0;
    }

    // ---- lizard (Mudskipper, Crested Basilisk) ------------------------------------------------------------------------

    public static final class Lizard extends LifeModel {
        private final ModelPart body, head, crest, sail, tail, tailEnd, legFL, legFR, legHL, legHR;

        public Lizard(ModelPart root) {
            super(root);
            body = root.getChild("body");
            head = body.getChild("head");
            crest = head.getChild("crest");
            sail = body.getChild("sail");
            tail = body.getChild("tail");
            tailEnd = tail.getChild("tail_end");
            legFL = body.getChild("leg_front_left");
            legFR = body.getChild("leg_front_right");
            legHL = body.getChild("leg_hind_left");
            legHR = body.getChild("leg_hind_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -6, 6, 4, 12), PartPose.offset(0, 20, 0));
            PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-2.5F, -2, -5, 5, 4, 5)
                    .texOffs(20, 16).addBox(-1.5F, -0.5F, -8, 3, 2, 3), PartPose.offset(0, -0.5F, -6));
            head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(36, 0).addBox(0, -4, -3, 0, 4, 6), PartPose.offset(0, -2, -1));
            body.addOrReplaceChild("sail", CubeListBuilder.create().texOffs(36, 10).addBox(0, -3, -5, 0, 3, 10), PartPose.offset(0, -2, 0));
            PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -1.5F, 0, 3, 3, 8),
                    PartPose.offsetAndRotation(0, 0, 6, 0.12F, 0, 0));
            tail.addOrReplaceChild("tail_end", CubeListBuilder.create().texOffs(24, 32).addBox(-1, -1, 0, 2, 2, 7), PartPose.offsetAndRotation(0, 0, 8, 0.1F, 0, 0));
            CubeListBuilder legL = CubeListBuilder.create().texOffs(0, 26).addBox(0, -1, -1, 4, 2, 2);
            CubeListBuilder legR = CubeListBuilder.create().mirror().texOffs(0, 26).addBox(-4, -1, -1, 4, 2, 2);
            body.addOrReplaceChild("leg_front_left", legL, PartPose.offsetAndRotation(3, 1, -4, 0, 0, 0.6F));
            body.addOrReplaceChild("leg_front_right", legR, PartPose.offsetAndRotation(-3, 1, -4, 0, 0, -0.6F));
            body.addOrReplaceChild("leg_hind_left", legL, PartPose.offsetAndRotation(3, 1, 4, 0, 0, 0.6F));
            body.addOrReplaceChild("leg_hind_right", legR, PartPose.offsetAndRotation(-3, 1, 4, 0, 0, -0.6F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public boolean legsHeld(AwLook l) { // up on its hind legs to sprint
            return (l.fauna & FaunaMob.P_SPRINT) != 0 || super.legsHeld(l);
        }

        @Override
        protected void pose(LivingEntityRenderState s) {
            float pos = s.walkAnimationPos * 0.9F;
            float amp = Math.min(1.0F, s.walkAnimationSpeed * 1.6F);
            boolean sprint = has(s, FaunaMob.P_SPRINT);
            float swing = Mth.cos(pos) * 0.65F * amp;
            legFL.yRot = swing;
            legHR.yRot = swing;
            legFR.yRot = -swing;
            legHL.yRot = -swing;
            body.yRot = Mth.sin(pos) * 0.12F * amp;
            head.yRot = Motion.yaw(s, 0.8F);
            head.xRot = Motion.pitch(s, 0.5F);
            tail.yRot = Mth.sin(s.ageInTicks * 0.1F) * 0.15F - body.yRot * 2.0F;
            tailEnd.yRot = Mth.sin(s.ageInTicks * 0.1F - 0.7F) * 0.25F;
            crest.zRot = 0;
            if (sprint) {
                // Up on its hind legs, front legs tucked, tail out behind for balance.
                body.xRot = -0.55F;
                body.y = 18.5F;
                legFL.zRot = 1.3F;
                legFR.zRot = -1.3F;
                legHL.zRot = 0.9F + Mth.cos(pos * 1.6F) * 0.4F;
                legHR.zRot = -0.9F + Mth.cos(pos * 1.6F) * 0.4F;
                tail.xRot = 0.45F;
            } else {
                body.xRot = 0;
                body.y = 20;
                legFL.zRot = 0.6F;
                legFR.zRot = -0.6F;
                legHL.zRot = 0.6F;
                legHR.zRot = -0.6F;
                tail.xRot = 0.12F;
            }
            if (has(s, FaunaMob.P_GRAZE)) {
                head.xRot = 0.5F + Mth.sin(s.ageInTicks * 0.6F) * 0.12F;
                body.y += 0.4F;
            }
            boolean angry = has(s, FaunaMob.P_ANGRY) || look(s).telegraph > 0;
            sail.yScale = angry ? 1.4F : 1.0F;
            crest.yScale = angry ? 1.3F : 1.0F;
            // a lizard's throat pumps while it rests; struck, it jerks its head up and lashes its tail
            body.xScale = Motion.breathe(s, 0.35F, 0.04F);
            float fl = Motion.flinch(s);
            head.xRot -= fl * 0.6F;
            body.zRot = Motion.shudder(s, 0.2F);
            tail.yRot += Motion.shudder(s, 0.8F);
            sail.yScale += fl * 0.4F;
        }
    }

    // ---- snail (Gloam Snail, Sporeback Snail) -------------------------------------------------------------------------

    public static final class Snail extends LifeModel {
        private final ModelPart foot, head, eyeL, eyeR, shell, garden;

        public Snail(ModelPart root) {
            super(root);
            foot = root.getChild("foot");
            head = foot.getChild("head");
            eyeL = head.getChild("eye_left");
            eyeR = head.getChild("eye_right");
            shell = foot.getChild("shell");
            garden = shell.getChild("garden");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition foot = root.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2, -7, 5, 2, 14), PartPose.offset(0, 24, 0));
            PartDefinition head = foot.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-2, -3, -3, 4, 3, 3), PartPose.offset(0, -2, -6));
            head.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(16, 16).addBox(-0.5F, -4, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(1.2F, -3, -1.5F, 0, 0, 0.25F));
            head.addOrReplaceChild("eye_right", CubeListBuilder.create().mirror().texOffs(16, 16).addBox(-0.5F, -4, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(-1.2F, -3, -1.5F, 0, 0, -0.25F));
            PartDefinition shell = foot.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 24).addBox(-4, -8, -4, 8, 8, 8), PartPose.offsetAndRotation(0, -2, 2, -0.15F, 0, 0));
            shell.addOrReplaceChild("garden", CubeListBuilder.create().texOffs(24, 40).addBox(-1, -2, -1, 2, 2, 2).texOffs(0, 40).addBox(-3, -4, -3, 6, 2, 6), PartPose.offset(0, -8, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        protected void pose(LivingEntityRenderState s) {
            float amp = Math.min(1.0F, s.walkAnimationSpeed * 2.5F);
            float wave = s.walkAnimationPos * 0.8F, crawl = Mth.sin(wave) * amp;
            boolean tucked = has(s, FaunaMob.P_GRAZE) || has(s, FaunaMob.P_REST) || has(s, FaunaMob.P_GUARD);
            // a snail's gait is a wave down the foot, its pace the ground's: the front pushes out from a tail that stays
            // put, then the tail gathers in after it; the shell and the head keep their own length
            float stretch = 1.0F + crawl * 0.08F + (Motion.breathe(s, 0.09F, 0.02F) - 1.0F);
            foot.zScale = stretch;
            foot.z = -(stretch - 1.0F) * 7.0F;
            shell.zScale = 1.0F / stretch;
            head.zScale = 1.0F / stretch;
            // struck: the eye stalks shrink back and the head pulls in under the shell
            float fl = Motion.flinch(s);
            head.z = tucked ? -4.0F : -6.0F - crawl * 0.3F + fl * 2.0F;
            head.y = tucked ? -0.5F : -2.0F + fl;
            head.yRot = tucked ? 0 : Motion.yaw(s, 0.6F);
            head.xRot = tucked ? 0 : Motion.pitch(s, 0.4F);
            eyeL.visible = !tucked;
            eyeR.visible = !tucked;
            eyeL.zRot = 0.25F + Mth.sin(s.ageInTicks * 0.2F) * 0.12F;
            eyeR.zRot = -0.25F - Mth.sin(s.ageInTicks * 0.2F + 1.3F) * 0.12F;
            // under way the stalks sweep back; now and then one draws halfway in and out again (a snail's blink)
            eyeL.xRot = -0.3F * amp;
            eyeR.xRot = -0.3F * amp + Mth.sin(s.ageInTicks * 0.13F) * 0.06F;
            float blinkL = Mth.clamp(Mth.sin(s.ageInTicks * 0.031F) * 8.0F - 7.0F, 0.0F, 1.0F);
            float blinkR = Mth.clamp(Mth.sin(s.ageInTicks * 0.031F - 0.25F) * 8.0F - 7.0F, 0.0F, 1.0F);
            eyeL.yScale = (1.0F - fl * 0.7F) * (1.0F - 0.55F * blinkL);
            eyeR.yScale = (1.0F - fl * 0.7F) * (1.0F - 0.55F * blinkR);
            // the shell rides the wave: it rocks fore and aft with each push and sways side to side once in two
            shell.xRot = -0.15F + crawl * 0.03F;
            shell.zRot = Motion.shudder(s, 0.1F) + Mth.sin(wave * 0.5F) * 0.045F * amp;
            garden.zRot = Mth.sin(s.ageInTicks * 0.05F) * 0.05F;
        }
    }

    // ---- walking mushroom (Shroomlet, the Mycelord) -----------------------------------------------------------------------

    public static final class Mushroom extends LifeModel {
        private final ModelPart body, cap, armL, armR, legL, legR;

        public Mushroom(ModelPart root) {
            super(root);
            body = root.getChild("body");
            cap = body.getChild("cap");
            armL = body.getChild("arm_left");
            armR = body.getChild("arm_right");
            legL = root.getChild("leg_left");
            legR = root.getChild("leg_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -9, -3, 6, 9, 6), PartPose.offset(0, 21, 0));
            body.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 16).addBox(-6, -4, -6, 12, 4, 12)
                    .texOffs(0, 32).addBox(-4.5F, -6, -4.5F, 9, 2, 9), PartPose.offset(0, -8, 0));
            body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(44, 0).addBox(0, 0, -1, 1, 3, 2), PartPose.offsetAndRotation(3, -6, 0, 0, 0, -0.5F));
            body.addOrReplaceChild("arm_right", CubeListBuilder.create().mirror().texOffs(44, 0).addBox(-1, 0, -1, 1, 3, 2), PartPose.offsetAndRotation(-3, -6, 0, 0, 0, 0.5F));
            root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(36, 0).addBox(-1, 0, -1, 2, 3, 2), PartPose.offset(1.5F, 21, 0));
            root.addOrReplaceChild("leg_right", CubeListBuilder.create().mirror().texOffs(36, 0).addBox(-1, 0, -1, 2, 3, 2), PartPose.offset(-1.5F, 21, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        protected void pose(LivingEntityRenderState s) {
            float pos = s.walkAnimationPos;
            float amp = Math.min(1.0F, s.walkAnimationSpeed * 1.5F);
            // on planted feet (SPEC §23, "Motion") the gait's own bob and roll take over the waddle
            Life life = Life.from(s);
            float own = life != null ? 1.0F - life.feet() : 1.0F;
            legL.xRot = Mth.cos(pos * 1.3F) * 1.0F * amp;
            legR.xRot = -Mth.cos(pos * 1.3F) * 1.0F * amp;
            body.zRot = Mth.sin(pos * 0.65F) * 0.12F * amp * own;
            body.y = 21 - Math.abs(Mth.sin(pos * 0.65F)) * 0.6F * amp * own;
            float idle = Mth.sin(s.ageInTicks * 0.1F) * 0.08F;
            armL.zRot = -0.5F - idle;
            armR.zRot = 0.5F + idle;
            armL.xRot = -legL.xRot * 0.6F;
            armR.xRot = -legR.xRot * 0.6F;
            boolean puff = has(s, FaunaMob.P_PUFF);
            cap.y = puff ? -7.0F : -8.0F;
            cap.xScale = puff ? 1.15F : 1.0F;
            cap.zScale = puff ? 1.15F : 1.0F;
            cap.yScale = puff ? 0.8F : 1.0F;
            boolean angry = has(s, FaunaMob.P_ANGRY) || look(s).telegraph > 0;
            cap.xRot = angry ? 0.25F + look(s).telegraph * 0.2F : Motion.pitch(s, 0.3F);
            cap.yRot = Motion.yaw(s, 0.5F);
            body.yRot = Motion.yaw(s, 0.2F);
            // the cap breathes; struck, it squashes and wobbles like jelly
            float fl = Motion.flinch(s);
            float b = Motion.breathe(s, 0.1F, 0.03F);
            cap.xScale *= b + fl * 0.15F;
            cap.zScale *= b + fl * 0.15F;
            cap.yScale *= (2.0F - b) - fl * 0.25F;
            body.zRot += Motion.shudder(s, 0.25F);
        }
    }

    // ---- marmot (Scree Marmot) --------------------------------------------------------------------------------------------

    public static final class Marmot extends LifeModel {
        private final ModelPart body, head, tail, legFL, legFR, legHL, legHR;

        public Marmot(ModelPart root) {
            super(root);
            body = root.getChild("body");
            head = body.getChild("head");
            tail = body.getChild("tail");
            legFL = root.getChild("leg_front_left");
            legFR = root.getChild("leg_front_right");
            legHL = root.getChild("leg_hind_left");
            legHR = root.getChild("leg_hind_right");
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -5, 6, 6, 10), PartPose.offset(0, 19, 1));
            PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-2.5F, -2.5F, -4, 5, 4, 4), PartPose.offset(0, -1, -5));
            head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(18, 16).addBox(-0.5F, -1, -0.5F, 1, 1, 1), PartPose.offset(1.8F, -2.5F, -1));
            head.addOrReplaceChild("ear_right", CubeListBuilder.create().mirror().texOffs(18, 16).addBox(-0.5F, -1, -0.5F, 1, 1, 1), PartPose.offset(-1.8F, -2.5F, -1));
            body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, -0.5F, 0, 3, 2, 5), PartPose.offsetAndRotation(0, 1, 5, -0.3F, 0, 0));
            CubeListBuilder leg = CubeListBuilder.create().texOffs(24, 16).addBox(-1, 0, -1, 2, 2, 2);
            CubeListBuilder legM = CubeListBuilder.create().mirror().texOffs(24, 16).addBox(-1, 0, -1, 2, 2, 2);
            root.addOrReplaceChild("leg_front_left", leg, PartPose.offset(2, 22, -2));
            root.addOrReplaceChild("leg_front_right", legM, PartPose.offset(-2, 22, -2));
            root.addOrReplaceChild("leg_hind_left", leg, PartPose.offset(2, 22, 4));
            root.addOrReplaceChild("leg_hind_right", legM, PartPose.offset(-2, 22, 4));
            return LayerDefinition.create(mesh, 64, 32);
        }

        @Override
        public boolean legsHeld(AwLook l) { // up on its haunches to whistle, or digging in
            return (l.fauna & FaunaMob.P_ALERT) != 0 || l.faunaAnim > 0 || super.legsHeld(l);
        }

        @Override
        protected void pose(LivingEntityRenderState s) {
            float pos = s.walkAnimationPos * 0.6662F * 1.6F;
            float amp = Math.min(1.2F, s.walkAnimationSpeed * 1.4F);
            float burrow = look(s).faunaAnim;
            float sink = burrow * 8.0F;
            legFL.xRot = Mth.cos(pos) * amp;
            legHR.xRot = Mth.cos(pos) * amp;
            legFR.xRot = -Mth.cos(pos) * amp;
            legHL.xRot = -Mth.cos(pos) * amp;
            head.yRot = Motion.yaw(s, 0.8F);
            head.xRot = Motion.pitch(s, 0.6F);
            tail.xRot = -0.3F + Mth.sin(s.ageInTicks * 0.15F) * 0.08F;
            // a fat marmot breathes fast; struck, it flinches flat and flicks its tail
            body.yScale = Motion.breathe(s, 0.45F, 0.035F);
            float fl = Motion.flinch(s);
            head.xRot += fl * 0.5F;
            tail.xRot -= fl * 0.8F;
            body.zRot = Motion.shudder(s, 0.15F);
            legFL.y = legFR.y = 22 + sink;
            legHL.y = legHR.y = 22 + sink;
            legFL.z = legFR.z = -2;
            if (has(s, FaunaMob.P_ALERT) && burrow <= 0) {
                // Up on its haunches to whistle: the body pivots upright, paws tucked at the chest.
                body.xRot = -1.0F;
                body.y = 18.2F;
                head.xRot = 1.0F;
                legFL.y = legFR.y = 15.0F;
                legFL.z = legFR.z = -1.5F;
                legFL.xRot = legFR.xRot = -0.4F;
                tail.xRot = 0.9F;
            } else {
                body.xRot = 0;
                body.y = 19 + sink;
                if (has(s, FaunaMob.P_GRAZE)) {
                    head.xRot = 0.7F;
                    float paddle = Mth.sin(s.ageInTicks * 1.2F) * 0.8F;
                    legFL.xRot = paddle;
                    legFR.xRot = -paddle;
                }
            }
        }
    }
}
