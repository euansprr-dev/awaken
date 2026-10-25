package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

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
    /** Vanilla's resting hand and the shoulder a swing turns about (camera space: x right, y up, -z ahead). */
    private static final float HX = 0.56F, HY = -0.52F, HZ = -0.72F, SX = 0.35F, SY = -0.75F;
    private static final float[] POSE = new float[5];

    static boolean transform(PoseStack ps, HumanoidArm arm, ItemStack stack, float partial, float equip) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || stack != p.getMainHandItem() || arm != p.getMainArm() || !ArmsPredict.fights(p)) return false;
        int inv = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose(POSE);
        float x = POSE[0], y = POSE[1], roll = POSE[2], lunge = POSE[3], tilt = POSE[4];
        // the hand travels across the view and the blade turns in the screen's plane, its flat to the eye: a flat
        // sprite turned about the vertical goes edge-on and vanishes mid-cut. At rest this is vanilla's place.
        ps.translate(inv * (HX + x), HY + y + equip * -0.6F, HZ - lunge * 0.55F);
        ps.rotateDegrees(Axis.ZP, inv * roll);
        ps.rotateDegrees(Axis.XP, -tilt);
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

    /** Autopilot only: hold the hands at a fraction (0..1) through a move, for a still review frame. */
    static Archetype.@Nullable Move preview;
    static float previewAt;

    /** The hand's x and y (blocks from rest), the blade's roll, the lunge (blocks) and the tip's tilt, now. */
    static void pose(float[] out) {
        java.util.Arrays.fill(out, 0F);
        var style = ArmsPredict.style != null ? ArmsPredict.style : preview != null ? Archetypes.get(Arms.LONGSWORD) : null;
        if (style == null) return;
        if (preview != null) {
            Archetype.MoveDef def = style.move(preview);
            if (def != null) swing(out, def, previewAt * def.total(), def.recovery());
            return;
        }
        if (ArmsPredict.plunging) {
            // armed in the fall: the blade held high in the Plunge's start pose
            Archetype.MoveDef plunge = style.move(Archetype.Move.PLUNGE);
            if (plunge != null) lerp(out, null, plunge.swing().from(), 1, false);
            return;
        }
        if (ArmsPredict.charging) {
            Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
            if (heavy == null) return;
            float t = ArmsPredict.moveTime();
            float a = easeOutSine(Math.min(1F, t / 6F));
            List<Float> from = heavy.swing().from();
            // the arm straining against the held cut: a fine shake (blocks and degrees), growing as it charges
            float tremble = Mth.sin(t * 2.7F) * Math.min(1F, t / 10F);
            lerp(out, null, from, a, false);
            out[0] += tremble * 0.008F;
            out[1] += tremble * 0.012F;
            out[2] += tremble * 1.5F;
            return;
        }
        Archetype.Move m = ArmsPredict.move;
        if (m == null) return;
        Archetype.MoveDef def = style.move(m);
        if (def != null) swing(out, def, ArmsPredict.moveTime(), ArmsPredict.recovery);
    }

    /** The row's swing at {@code t} ticks: eased into its start pose, hard through the blow with its lunge, home. */
    private static void swing(float[] out, Archetype.MoveDef def, float t, int recovery) {
        List<Float> from = def.swing().from(), to = def.swing().to();
        float w = def.windup(), a = def.active(), r = Math.max(1, recovery);
        if (t < w) {
            lerp(out, null, from, easeOutSine(t / Math.max(1F, w)), false);
        } else if (t < w + a) {
            float k = easeOutCubic((t - w) / a);
            lerp(out, from, to, k, true);
            out[3] = def.swing().lunge() * Mth.sin(k * Mth.HALF_PI);
        } else if (t < w + a + r) {
            float k = easeInOutSine((t - w - a) / r);
            lerp(out, to, null, k, false);
            out[3] = def.swing().lunge() * (1 - k);
        }
    }

    /**
     * Between two poses (null = rest), filling x, y, roll and tilt (0, 1, 2, 4). The blow ({@code arc}) carries the
     * hand on an arc about the shoulder, its angle and reach eased, so a cut curves like an arm's; the wind-up and the
     * way home go straight (an arc there would lift the blade back over the top of the view).
     */
    private static void lerp(float[] out, List<Float> from, List<Float> to, float k, boolean arc) {
        float fx = value(from, 0), fy = value(from, 1), tx = value(to, 0), ty = value(to, 1);
        if (arc) {
            float ax = HX + fx - SX, ay = HY + fy - SY, bx = HX + tx - SX, by = HY + ty - SY;
            float a0 = (float) Mth.atan2(ay, ax), a1 = (float) Mth.atan2(by, bx);
            float r0 = Mth.sqrt(ax * ax + ay * ay), r1 = Mth.sqrt(bx * bx + by * by);
            float ang = a0 + Mth.wrapDegrees((a1 - a0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k, rad = r0 + (r1 - r0) * k;
            out[0] = SX + Mth.cos(ang) * rad - HX;
            out[1] = SY + Mth.sin(ang) * rad - HY;
        } else {
            out[0] = fx + (tx - fx) * k;
            out[1] = fy + (ty - fy) * k;
        }
        out[2] = value(from, 2) + (value(to, 2) - value(from, 2)) * k;
        out[4] = value(from, 3) + (value(to, 3) - value(from, 3)) * k;
    }

    private static float value(List<Float> pose, int i) {
        return pose == null || i >= pose.size() ? 0 : pose.get(i);
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
