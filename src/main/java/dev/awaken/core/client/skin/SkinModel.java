package dev.awaken.core.client.skin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The player mesh (ModelLayers.PLAYER or PLAYER_SLIM) as a plain humanoid model. The overlay parts are children of the
 * head, body, arms and legs (PlayerModel.createMesh), so they draw with them; this only toggles them and, like
 * PlayerModel, draws translucent and moves held items in by half a pixel on slim arms.
 */
public class SkinModel<S extends SkinRenderState> extends HumanoidModel<S> {
    private final boolean slim;
    private final ModelPart jacket;
    private final ModelPart leftSleeve;
    private final ModelPart rightSleeve;
    private final ModelPart leftPants;
    private final ModelPart rightPants;

    public SkinModel(ModelPart root, boolean slim) {
        super(root, RenderTypes::entityTranslucent);
        this.slim = slim;
        this.jacket = body.getChild("jacket");
        this.leftSleeve = leftArm.getChild("left_sleeve");
        this.rightSleeve = rightArm.getChild("right_sleeve");
        this.leftPants = leftLeg.getChild("left_pants");
        this.rightPants = rightLeg.getChild("right_pants");
    }

    @Override
    public void setupAnim(S state) {
        hat.visible = state.showHat;
        jacket.visible = state.showJacket;
        leftSleeve.visible = state.showSleeves;
        rightSleeve.visible = state.showSleeves;
        leftPants.visible = state.showPants;
        rightPants.visible = state.showPants;
        super.setupAnim(state);
    }

    @Override
    public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
        if (!slim) {
            super.translateToHand(state, arm, poseStack);
            return;
        }
        root().translateAndRotate(poseStack);
        ModelPart part = getArm(arm);
        float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
        part.x += offset;
        part.translateAndRotate(poseStack);
        part.x -= offset;
    }
}
