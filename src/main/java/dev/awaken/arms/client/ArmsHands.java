package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.arms.Archetype;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The weapon in first person (SPEC §24): every move's swing from its row (wind-up to the swing's start pose, the
 * blow through to its end pose, the recovery home), the guard across the body, a held heavy drawn back and
 * trembling. Drawn through the item's own hand transform (NeoForge's {@code applyForgeHandTransform}), so vanilla's
 * swing never plays over it. The clock is the hands' own: hit-stop holds it, so a swing freezes on the blow and
 * resumes where it stopped. Stands in for Hands' action layer until that rig exists (docs/anims/PLAN.md).
 */
final class ArmsHands {
    private ArmsHands() {}

    /** Wall-clock ticks the guard took to come up (2 ticks), and how it is held. */
    private static final float GUARD_RISE = 2F;
    private static final float[] POSE = new float[4];

    static boolean transform(PoseStack ps, HumanoidArm arm, ItemStack stack, float partial, float equip) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || stack != p.getMainHandItem() || arm != p.getMainArm() || !ArmsPredict.fights(p)) return false;
        int inv = arm == HumanoidArm.RIGHT ? 1 : -1;
        ps.translate(inv * 0.56F, -0.52F + equip * -0.6F, -0.72F); // vanilla's resting place for a held item
        pose(POSE);
        float yaw = POSE[0], pitch = POSE[1], roll = POSE[2], lunge = POSE[3];
        // the blade travels along its arc, not only turns on the grip
        ps.translate(inv * Mth.sin(yaw * Mth.DEG_TO_RAD) * 0.32F, -Mth.sin(pitch * Mth.DEG_TO_RAD) * 0.22F, -lunge * 0.55F);
        ps.rotateDegrees(Axis.YP, -inv * yaw);
        ps.rotateDegrees(Axis.XP, -pitch);
        ps.rotateDegrees(Axis.ZP, inv * roll);
        float g = guard(p, partial);
        if (g > 0) {
            // across the body, edge out (vanilla's own blocking pose, eased in)
            ps.translate(inv * -0.141F * g, 0.08F * g, 0.141F * g);
            ps.rotateDegrees(Axis.XP, -102.25F * g);
            ps.rotateDegrees(Axis.YP, inv * 13.37F * g);
            ps.rotateDegrees(Axis.ZP, inv * 78.05F * g);
        }
        return true;
    }

    /** The guard's blend: up over its first ticks of use. */
    private static float guard(LocalPlayer p, float partial) {
        if (!p.isUsingItem() || p.getUseItem() != p.getMainHandItem()) return 0;
        return Math.min(1F, p.getTicksUsingItem(partial) / GUARD_RISE);
    }

    /** yaw, pitch, roll (degrees) and lunge (blocks) of the move now, on the hands' clock. */
    static void pose(float[] out) {
        out[0] = out[1] = out[2] = out[3] = 0;
        var style = ArmsPredict.style;
        if (style == null) return;
        if (ArmsPredict.charging) {
            Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
            if (heavy == null) return;
            float t = ArmsPredict.moveTime();
            float a = easeOutSine(Math.min(1F, t / 6F));
            List<Float> from = heavy.swing().from();
            float tremble = Mth.sin(t * 2.7F) * 1.6F * Math.min(1F, t / 10F);
            out[0] = from.get(0) * a + tremble * 0.5F;
            out[1] = from.get(1) * a + tremble;
            out[2] = from.get(2) * a;
            return;
        }
        Archetype.Move m = ArmsPredict.move;
        if (m == null) return;
        Archetype.MoveDef def = style.move(m);
        if (def == null) return;
        float t = ArmsPredict.moveTime();
        List<Float> from = def.swing().from(), to = def.swing().to();
        float w = def.windup(), a = def.active(), r = Math.max(1, ArmsPredict.recovery);
        if (t < w) {
            float k = easeOutSine(t / Math.max(1F, w));
            lerp(out, null, from, k);
        } else if (t < w + a) {
            float k = easeOutCubic((t - w) / a);
            lerp(out, from, to, k);
            out[3] = def.swing().lunge() * Mth.sin(k * Mth.HALF_PI);
        } else if (t < w + a + r) {
            float k = easeInOutSine((t - w - a) / r);
            lerp(out, to, null, k);
            out[3] = def.swing().lunge() * (1 - k);
        }
    }

    private static void lerp(float[] out, List<Float> from, List<Float> to, float k) {
        for (int i = 0; i < 3; i++) {
            float f = from == null ? 0 : from.get(i), t = to == null ? 0 : to.get(i);
            out[i] = f + (t - f) * k;
        }
    }

    static float easeOutSine(float x) {
        return Mth.sin(Mth.clamp(x, 0, 1) * Mth.HALF_PI);
    }

    static float easeOutCubic(float x) {
        float c = 1 - Mth.clamp(x, 0, 1);
        return 1 - c * c * c;
    }

    static float easeInOutSine(float x) {
        return -(Mth.cos(Mth.PI * Mth.clamp(x, 0, 1)) - 1) / 2;
    }
}
