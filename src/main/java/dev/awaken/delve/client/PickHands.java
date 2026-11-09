package dev.awaken.delve.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.delve.forged.ForgedPicks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * A forged pick is swung with both hands (SPEC §49.8), if the other is free. First person: while you dig, {@link DigMotion}
 * has the whole swing, so vanilla's one-armed whack steps aside. Third person: both arms reach for the haft and go down
 * together, for anyone watching.
 */
public final class PickHands implements IClientItemExtensions {
    static final PickHands INSTANCE = new PickHands();

    private PickHands() {}

    /** Read by NeoForge's enum extender (the json names this field). */
    public static final EnumProxy<HumanoidModel.ArmPose> DIG_POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, false, (IArmPoseTransformer) PickHands::apply);

    /** Vanilla's resting place of a hand. */
    private static final float HX = 0.56F, HY = -0.52F, HZ = -0.72F;

    @Override
    public boolean applyForgeHandTransform(PoseStack ps, PlayerRenderState player, HumanoidArm arm, ItemStack stack, float partialTick, float equip, float swing) {
        if (!DigMotion.digging(stack, arm, partialTick)) return false;
        ps.translate((arm == HumanoidArm.RIGHT ? 1 : -1) * HX, HY + equip * -0.6F, HZ);
        return true;
    }

    private static void apply(HumanoidModel<?> model, HumanoidRenderState s, HumanoidArm arm) {
        boolean main = arm == s.mainArm;
        var part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        float side = arm == HumanoidArm.LEFT ? 1F : -1F;
        // the leading hand low on the haft, the other forward of it and across the body
        part.xRot = main ? -0.70F : -1.05F;
        part.yRot = main ? 0F : -side * 0.45F;
        part.zRot = 0F;
        if (!main) {
            // the same blow as the arm that vanilla swings (HumanoidModel.setupAttackAnimation), or it would be left behind
            float t = s.swingAnimation, q = 1F - t;
            q = 1F - q * q * q * q;
            part.xRot -= Mth.sin(q * Mth.PI) * 1.2F + Mth.sin(t * Mth.PI) * -(model.head.xRot - 0.7F) * 0.75F;
        }
    }

    /** Render state modifier: a forged pick mid-swing in the main hand, with the other hand free, is held in both. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player p) || !(state instanceof HumanoidRenderState s)) return;
        if (s.swingAnimation <= 0F || s.currentSwing == null || s.currentSwing.hand() != InteractionHand.MAIN_HAND) return;
        if (!ForgedPicks.is(p.getMainHandItem()) || !p.getOffhandItem().isEmpty()) return;
        HumanoidModel.ArmPose pose = DIG_POSE.getValue();
        s.rightArmPose = pose;
        s.leftArmPose = pose;
    }
}
