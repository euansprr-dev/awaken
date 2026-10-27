package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where a cast's cast points are on a body that is drawn (docs/power-anims/PLAN.md section 5.2): a layer that draws nothing.
 * It finds the palm as vanilla's item layer finds the hand (the arm as the animation library has posed it,
 * {@code translateToHand}), and the sternum and the stamping foot the same way from their model parts, and records the
 * world positions in {@link Muzzles} for effects to start at. Only for bodies with a live cast; like Arms'
 * {@code BladeTraceLayer}, which this follows.
 */
final class MuzzleLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private MuzzleLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addTo(AvatarRenderer<?> r) {
        ((AvatarRenderer) r).addLayer(new MuzzleLayer(r));
    }

    private static final Matrix4f M = new Matrix4f();
    private static final Vector3f V = new Vector3f();
    private static final float PX = 1F / 16F;

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector out, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (!CastAnims.live(state.id)) return;
        PlayerModel model = getParentModel();
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        HumanoidArm main = state.mainArm;
        HumanoidArm other = main == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        // the palms: the middle of the hand's end, in the arm's own frame (a wide arm's box is x -3..1 right, -1..3 left)
        for (int side = 0; side < 2; side++) {
            HumanoidArm arm = side == 0 ? main : other;
            poseStack.pushPose();
            model.translateToHand(state, arm, poseStack);
            M.set(poseStack.last().pose());
            poseStack.popPose();
            sample(state.id, side == 0 ? Muzzles.PALM : Muzzles.PALM_OFF, cam, (arm == HumanoidArm.RIGHT ? -1 : 1) * PX, 10.5F * PX, 0);
        }
        // the sternum: on the chest's front face, a third of the way down
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        model.body.translateAndRotate(poseStack);
        M.set(poseStack.last().pose());
        poseStack.popPose();
        sample(state.id, Muzzles.CORE, cam, 0, 3.5F * PX, -2.4F * PX);
        // the stamping foot: the main-side leg's sole
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        (main == HumanoidArm.RIGHT ? model.rightLeg : model.leftLeg).translateAndRotate(poseStack);
        M.set(poseStack.last().pose());
        poseStack.popPose();
        sample(state.id, Muzzles.FOOT, cam, 0, 12.2F * PX, 0);
    }

    private static void sample(int entity, int point, Vec3 cam, float x, float y, float z) {
        M.transformPosition(x, y, z, V);
        Muzzles.put(entity, point, cam.x + V.x, cam.y + V.y, cam.z + V.z);
    }
}
