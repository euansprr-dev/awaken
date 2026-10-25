package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.arms.ArmsItems;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Where a vanilla blade is in a body's hand, seen from outside (SPEC §25 "The blade's own ribbon"): a layer that draws
 * nothing, it finds the hand as vanilla's item layer does (the arm as the animation library has posed it, then the
 * handheld third-person display) and gives the ribbon its sample. Our own weapons sample themselves as they draw.
 */
final class BladeTraceLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private BladeTraceLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addTo(AvatarRenderer<?> r) {
        ((AvatarRenderer) r).addLayer(new BladeTraceLayer(r));
    }

    // a sword's blade on its sprite (as ArmsHands): its outer third, from two thirds up the blade to the tip
    private static final float RX = (5.5F + 10F * 0.66F) / 16F, RY = 1 - (10.5F - 9F * 0.66F) / 16F, TX = 15.5F / 16F, TY = 1 - 1.5F / 16F;
    private static final Matrix4f M = new Matrix4f();

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector out, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (!BladeTrail.open(state.id)) return;
        ItemStack stack = state.getMainHandItemStack();
        if (stack.isEmpty() || ArmsItems.of(stack.getItem()) != null) return;
        HumanoidArm arm = state.mainArm;
        int inv = arm == HumanoidArm.LEFT ? -1 : 1;
        poseStack.pushPose();
        // vanilla's ItemInHandLayer to the hand, then item/handheld.json's third-person display
        getParentModel().translateToHand(state, arm, poseStack);
        poseStack.rotateDegrees(Axis.XP, -90.0F);
        poseStack.rotateDegrees(Axis.YP, 180.0F);
        poseStack.translate(inv / 16.0F, 2 / 16.0F, -10 / 16.0F);
        M.set(poseStack.last().pose()).translate(0, 4F / 16F, 0.5F / 16F)
                .rotateXYZ(0, inv * -90F * Mth.DEG_TO_RAD, inv * 55F * Mth.DEG_TO_RAD).scale(0.85F).translate(-0.5F, -0.5F, -0.5F);
        poseStack.popPose();
        BladeTrail.sample(state.id, M, RX, RY, 0.5F, TX, TY, 0.5F, false);
    }
}
