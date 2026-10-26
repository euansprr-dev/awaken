package dev.awaken.village.client;

import dev.awaken.village.trouble.Rescuee;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Carrying someone on your back (SPEC §28.7), as everyone sees you: both arms reach back and down to hold their legs
 * at your sides, the shoulders drawn back. An extended {@link HumanoidModel.ArmPose} ({@code AWAKEN_CARRY} in
 * META-INF/enumextensions.json) set on both arms of any player with someone riding on their back.
 */
public final class CarryArmPose {
    private CarryArmPose() {}

    /** Read by NeoForge's enum extender (the json names this field). */
    public static final EnumProxy<HumanoidModel.ArmPose> CARRY_POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, false, (IArmPoseTransformer) CarryArmPose::apply);

    private static void apply(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        float s = arm == HumanoidArm.LEFT ? 1f : -1f;
        var part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        // back and a little out, hands under the knees; the walk barely moves them
        part.xRot = 0.62f + Mth.cos(state.walkAnimationPos * 0.6662f) * 0.06f * state.walkAnimationSpeed;
        // (a right arm's zRot opens it outward when positive, a left arm's when negative)
        part.yRot = 0f;
        part.zRot = -0.30f * s;
    }

    /** Render state modifier: a player with someone on their back holds them. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player p) || !(state instanceof HumanoidRenderState s)) return;
        if (!Rescuee.carrying(p)) return;
        HumanoidModel.ArmPose pose = CARRY_POSE.getValue();
        s.rightArmPose = pose;
        s.leftArmPose = pose;
    }
}
