package dev.awaken.binoculars.client;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.binoculars.BinocularsModule;
import dev.awaken.binoculars.GlassFeel;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Your hands raising the binoculars (SPEC §39.4): a Hands view stance (§24) driven by the raise's progress, so a raise
 * can turn back halfway, and the binoculars drawn in the hand pass where the stance's fists hold them. What you hold eases
 * away as they come up ({@code View.climb}). Up to {@code 0.6} of the raise they rise from below the frame to in front of
 * the eyes; then they come to the eyes and the fists leave the frame; from the end of the eyecup hand-off nothing is
 * drawn (the lens edge has taken over) and the fists wait out of view, so vanilla's hands stay hidden.
 */
final class GlassHands {
    private GlassHands() {}

    /** The binoculars' scale in the hand pass (the model is 11.5 pixels across: about 0.36 of a block). */
    private static final float SCALE = 0.5F;
    private static final ItemStackRenderState ITEM = new ItemStackRenderState();
    private static ItemStack stack = ItemStack.EMPTY;

    // where the binoculars and fists are this frame (view space), filled by place()
    private static float bx, by, bz, tilt, fx, fy, fz;
    private static boolean shown;

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static void place(float t) {
        float lift = ease(t / 0.6F), near = ease((t - 0.6F) / 0.25F);
        shown = t < GlassFeel.get().eyecupEnd();
        bx = 0;
        by = -0.78F + 0.72F * lift + 0.02F * near;
        bz = -0.72F + 0.10F * lift + 0.37F * near;
        tilt = -38F * (1 - lift);  // the objectives low while they come up
        // the fists wrap the barrels' outer sides, nearer the eyepieces; as the binoculars reach the eyes they drop away
        fx = 0.19F + 0.16F * near;
        fy = by - 0.07F - 0.55F * near;
        fz = bz + 0.10F;
        if (!shown) {
            fx = 0.32F;
            fy = -1.6F;
            fz = -0.5F;
        }
    }

    /** The view stance. */
    static void pose(float partialTick, FirstPerson.View view) {
        if (!Glass.active()) return;
        place(Glass.raise());
        view.climb = true;
        boolean leftMain = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.LEFT;
        float s = leftMain ? -1 : 1;
        FirstPerson.View.fist(view.fistMain, 1F, s * fx, fy, fz);
        FirstPerson.View.fist(view.fistOff, 1F, -s * fx, fy, fz);
    }

    /** The binoculars, drawn in the main hand's pass before Hands moves anything (a clean stack: view space). */
    static void draw(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !Glass.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        place(Glass.raise());
        if (!shown) return;
        if (stack.isEmpty()) stack = new ItemStack(BinocularsModule.BINOCULARS.get());
        mc.getItemModelResolver().updateForTopItem(ITEM, stack, ItemDisplayContext.NONE, mc.level, null, 0);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(bx, by, bz);
        ps.rotateDegrees(Axis.XP, tilt);
        ps.scale(SCALE, SCALE, SCALE);
        ITEM.submit(ps, e.getSubmitNodeCollector(), e.getPackedLight(), OverlayTexture.NO_OVERLAY, 0);
        ps.popPose();
    }
}
