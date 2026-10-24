package dev.awaken.holo.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * The third-person "look at your wrist" pose on the vanilla player model, without the Player Animation Library:
 * an extended {@link HumanoidModel.ArmPose} ({@code AWAKEN_HOLO_WRIST} in META-INF/enumextensions.json) whose
 * transformer raises the forearm in front of the chest, turned inward, blended by how far the menu has opened
 * ({@link HoloOthers#progress}). {@link HoloOthers} sets it on the wrist arm of every player with a panel open.
 */
public final class HoloArmPose {
    private HoloArmPose() {}

    /** Read by NeoForge's enum extender (the json names this field). */
    public static final EnumProxy<HumanoidModel.ArmPose> WRIST_POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, false, (IArmPoseTransformer) HoloArmPose::apply);

    static HumanoidModel.ArmPose pose() {
        return WRIST_POSE.getValue();
    }

    private static void apply(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        float p = HoloOthers.progress(state);
        if (p <= 0) return;
        float s = arm == HumanoidArm.LEFT ? 1f : -1f;
        var part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        // raised to chest height and swung in across the chest, the wrist turned up toward the eyes: the shoulder and
        // the arm crossing the body read from behind and from the side
        part.xRot = Mth.lerp(p, part.xRot, -1.50f + model.head.xRot * 0.15f);
        part.yRot = Mth.lerp(p, part.yRot, 1.02f * s);
        part.zRot = Mth.lerp(p, part.zRot, 0.10f * s);
    }
}
