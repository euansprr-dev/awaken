package dev.awaken.world.stilled.client;

import dev.awaken.Awaken;
import dev.awaken.world.stilled.StilledText;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * A Stilled's figure (SPEC §43.3): its own jointed skeleton (knees and elbows, the body pivoting at the hips with the
 * head and arms on it) so each of the twelve poses is set by hand, and the props they sat down with: a pack, a
 * milestone, a grave marker, a bundle, a lantern, a dog's lead, a cart's tail. 128 x 128 texture, its own layout
 * (tools/art/stilled.py paints it). A pair or a parent with a child is this model submitted twice.
 */
public final class StilledModel extends Model<StilledModel.Figure> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Awaken.id("stilled"), "main");

    /** Which figure of a block is drawn: the first (with the props), or the second of a pair or the child. */
    public record Figure(StilledText.Pose pose, boolean second) {}

    private final ModelPart figure, body, head, rightArm, rightFore, leftArm, leftFore, rightLeg, rightShin, leftLeg, leftShin;
    private final ModelPart pack, lantern, milestone, packGround, grave, bundle, lead, cart;

    public StilledModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        figure = root.getChild("figure");
        body = figure.getChild("body");
        head = body.getChild("head");
        rightArm = body.getChild("right_arm");
        rightFore = rightArm.getChild("right_fore");
        lantern = rightFore.getChild("lantern");
        leftArm = body.getChild("left_arm");
        leftFore = leftArm.getChild("left_fore");
        pack = body.getChild("pack");
        rightLeg = figure.getChild("right_leg");
        rightShin = rightLeg.getChild("right_shin");
        leftLeg = figure.getChild("left_leg");
        leftShin = leftLeg.getChild("left_shin");
        milestone = root.getChild("milestone");
        packGround = root.getChild("pack_ground");
        grave = root.getChild("grave");
        bundle = root.getChild("bundle");
        lead = root.getChild("lead");
        cart = root.getChild("cart");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition fig = root.addOrReplaceChild("figure", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = fig.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4, -12, -2, 8, 12, 4)
                .texOffs(24, 16).addBox(-4, -12, -2, 8, 12, 4, new CubeDeformation(0.3F)), PartPose.offset(0, 12, 0));
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
                .texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.4F)), PartPose.offset(0, -12, 0));
        PartDefinition ra = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 16).addBox(-3, -2, -2, 4, 6, 4),
                PartPose.offset(-5, -10, 0));
        PartDefinition rf = ra.addOrReplaceChild("right_fore", CubeListBuilder.create().texOffs(48, 26).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(-1, 4, 0));
        rf.addOrReplaceChild("lantern", CubeListBuilder.create().texOffs(88, 0).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
        PartDefinition la = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 16).mirror().addBox(-1, -2, -2, 4, 6, 4),
                PartPose.offset(5, -10, 0));
        la.addOrReplaceChild("left_fore", CubeListBuilder.create().texOffs(48, 26).mirror().addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(1, 4, 0));
        body.addOrReplaceChild("pack", CubeListBuilder.create().texOffs(64, 0).addBox(-4, 0, 0, 8, 10, 4), PartPose.offset(0, -11, 2));
        PartDefinition rl = fig.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(-1.9F, 12, 0));
        rl.addOrReplaceChild("right_shin", CubeListBuilder.create().texOffs(0, 42).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
        PartDefinition ll = fig.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(1.9F, 12, 0));
        ll.addOrReplaceChild("left_shin", CubeListBuilder.create().texOffs(0, 42).mirror().addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));

        root.addOrReplaceChild("milestone", CubeListBuilder.create().texOffs(64, 28).addBox(-4, -7, -3, 8, 7, 6), PartPose.offset(0, 24, 2));
        root.addOrReplaceChild("pack_ground", CubeListBuilder.create().texOffs(64, 0).addBox(-4, -10, -2, 8, 10, 4),
                PartPose.offsetAndRotation(10, 24, 1, 0, 0.35F, 0));
        root.addOrReplaceChild("grave", CubeListBuilder.create().texOffs(96, 16).addBox(-3, -10, -1, 6, 10, 2), PartPose.offset(0, 24, -12));
        root.addOrReplaceChild("bundle", CubeListBuilder.create().texOffs(64, 44).addBox(-5, -6, -4, 10, 6, 8), PartPose.offset(0, 24, 2));
        root.addOrReplaceChild("lead", CubeListBuilder.create().texOffs(100, 44).addBox(-0.5F, -1, -12, 1, 1, 12),
                PartPose.offsetAndRotation(-7, 24, -3, 0, 0.3F, 0));
        root.addOrReplaceChild("cart", CubeListBuilder.create().texOffs(0, 64).addBox(-12, -10, -4, 24, 10, 8), PartPose.offset(0, 24, 3));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(Figure f) {
        super.setupAnim(f);
        for (ModelPart p : new ModelPart[]{pack, lantern, milestone, packGround, grave, bundle, lead, cart}) p.visible = false;
        if (f.second()) {
            if (f.pose() == StilledText.Pose.CHILD) kid();
            else pairB();
            return;
        }
        switch (f.pose()) {
            case MILESTONE -> {
                milestone.visible = packGround.visible = true;
                sit(4, -1.2F, 0.12F);
                body.xRot = 0.3F;
                head.xRot = 0.45F;
                head.zRot = 0.08F;
                arms(-0.85F, 0.12F, -0.5F);
            }
            case WALKING -> {
                pack.visible = true;
                body.xRot = 0.3F;
                head.xRot = -0.25F;
                rightLeg.xRot = 0.55F;
                rightShin.xRot = 0.5F;
                leftLeg.xRot = -0.65F;
                leftShin.xRot = 0.2F;
                figure.y = 0.8F;
                rightArm.xRot = -0.7F;
                rightFore.xRot = -0.5F;
                leftArm.xRot = 0.55F;
                leftFore.xRot = -0.3F;
            }
            case GRAVE -> {
                grave.visible = true;
                kneel();
                body.xRot = 0.32F;
                head.xRot = 0.6F;
                rightArm.xRot = -1.05F;
                rightFore.xRot = -0.25F;
                leftArm.xRot = 0.15F;
                leftArm.zRot = -0.08F;
                leftFore.xRot = -0.9F;
                leftFore.yRot = 0.6F;
            }
            case PAIR -> {
                cart.visible = true;
                figure.x = -6.5F;
                sit(0, -1.45F, 0.08F);
                rightShin.xRot = 1.2F;
                leftShin.xRot = 1.25F;
                body.xRot = 0.12F;
                head.xRot = 0.2F;
                head.zRot = -0.12F;
                arms(-0.6F, 0.1F, -0.3F);
            }
            case LEAD -> {
                pack.visible = lead.visible = true;
                figure.y = 10;
                rightLeg.xRot = leftLeg.xRot = -1.57F;
                rightLeg.yRot = 0.1F;
                leftLeg.yRot = -0.15F;
                body.xRot = -0.55F;
                head.xRot = 0.15F;
                head.zRot = 0.4F;
                rightArm.xRot = 0.35F;
                rightArm.zRot = 0.35F;
                rightFore.xRot = -0.7F;
                leftArm.xRot = 0.25F;
                leftArm.zRot = -0.1F;
                leftFore.xRot = -1.1F;
                leftFore.yRot = -0.5F;
            }
            case REACHING -> {
                kneel();
                body.xRot = 0.75F;
                head.xRot = -0.55F;
                rightArm.xRot = -2.35F;
                rightFore.xRot = -0.15F;
                leftArm.xRot = -1.15F;
                leftArm.zRot = -0.15F;
                leftFore.xRot = -0.2F;
            }
            case LOOKING_BACK -> {
                body.yRot = 0.3F;
                head.yRot = 1.15F;
                head.xRot = 0.05F;
                rightLeg.xRot = 0.22F;
                rightShin.xRot = 0.25F;
                leftLeg.xRot = -0.18F;
                rightArm.xRot = 0.1F;
                rightArm.zRot = 0.05F;
                leftArm.xRot = -0.2F;
                leftArm.zRot = -0.08F;
                leftFore.xRot = -0.35F;
            }
            case WAITING -> {
                figure.y = 10;
                rightLeg.xRot = leftLeg.xRot = -2.2F;
                rightLeg.yRot = 0.1F;
                leftLeg.yRot = -0.1F;
                rightShin.xRot = leftShin.xRot = 1.8F;
                body.xRot = 0.45F;
                head.xRot = 0.65F;
                rightArm.xRot = leftArm.xRot = -1.05F;
                rightArm.zRot = 0.3F;
                leftArm.zRot = -0.3F;
                rightFore.xRot = leftFore.xRot = -0.5F;
                rightFore.yRot = -0.7F;
                leftFore.yRot = 0.7F;
            }
            case LANTERN -> {
                lantern.visible = true;
                head.xRot = -0.25F;
                rightArm.xRot = -2.3F;
                rightArm.zRot = 0.1F;
                rightFore.xRot = -0.1F;
                lantern.xRot = 2.4F;
                rightLeg.xRot = 0.25F;
                leftLeg.xRot = -0.25F;
                leftShin.xRot = 0.15F;
                leftArm.xRot = 0.1F;
                leftArm.zRot = -0.1F;
            }
            case CHILD -> {
                rightLeg.xRot = 0.2F;
                leftLeg.xRot = -0.2F;
                leftShin.xRot = 0.15F;
                head.yRot = -0.35F;
                head.xRot = 0.35F;
                leftArm.zRot = -0.32F;
                leftArm.xRot = 0.05F;
                leftFore.xRot = -0.15F;
                rightArm.xRot = 0.15F;
                rightFore.xRot = -0.3F;
            }
            case CURLED -> {
                figure.zRot = 1.5708F;
                figure.x = 9;
                figure.y = 20;
                head.xRot = 0.35F;
                body.xRot = 0.25F;
                rightLeg.xRot = leftLeg.xRot = -1.35F;
                rightShin.xRot = leftShin.xRot = 1.7F;
                rightArm.xRot = leftArm.xRot = -1.2F;
                rightFore.xRot = leftFore.xRot = -1.1F;
            }
            case BUNDLE -> {
                bundle.visible = true;
                sit(5, -1.3F, 0.18F);
                body.xRot = 0.55F;
                head.xRot = -0.35F;
                rightArm.xRot = leftArm.xRot = -0.55F;
                rightArm.zRot = 0.08F;
                leftArm.zRot = -0.08F;
                rightFore.xRot = leftFore.xRot = -1.9F;
                rightFore.yRot = 0.25F;
                leftFore.yRot = -0.25F;
            }
        }
    }

    /** Sat on a seat: the hips lowered, thighs forward, shins back to hanging straight, knees a little apart. */
    private void sit(float drop, float thigh, float apart) {
        figure.y = drop;
        rightLeg.xRot = leftLeg.xRot = thigh;
        rightLeg.yRot = apart;
        leftLeg.yRot = -apart;
        rightShin.xRot = leftShin.xRot = -thigh;
    }

    /** Down on both knees, the shins along the ground behind. */
    private void kneel() {
        figure.y = 4;
        rightShin.xRot = leftShin.xRot = 1.57F;
        rightLeg.yRot = 0.06F;
        leftLeg.yRot = -0.06F;
    }

    /** Both forearms resting forward (on the knees, in the lap). */
    private void arms(float upper, float out, float fore) {
        rightArm.xRot = leftArm.xRot = upper;
        rightArm.zRot = out;
        leftArm.zRot = -out;
        rightFore.xRot = leftFore.xRot = fore;
    }

    /** The woman of a pair: beside him on the cart's tail, her head on his shoulder. */
    private void pairB() {
        figure.x = 6.5F;
        sit(0, -1.45F, 0.08F);
        rightShin.xRot = 1.3F;
        leftShin.xRot = 1.15F;
        body.zRot = 0.12F;
        body.xRot = 0.1F;
        head.zRot = 0.42F;
        head.x = -1.2F;
        head.xRot = 0.15F;
        rightArm.xRot = -0.75F;
        rightArm.zRot = -0.25F;
        rightFore.xRot = -0.45F;
        rightFore.yRot = 0.5F;
        leftArm.xRot = -0.55F;
        leftFore.xRot = -0.4F;
    }

    /** The child, a step to the side, holding the grown-up's hand and looking up at them. */
    private void kid() {
        rightArm.zRot = 0.55F;
        rightArm.xRot = -0.25F;
        rightFore.xRot = -0.2F;
        head.yRot = 0.35F;
        head.xRot = -0.35F;
        rightLeg.xRot = -0.25F;
        leftLeg.xRot = 0.25F;
        leftShin.xRot = 0.3F;
        leftArm.xRot = 0.3F;
    }
}
