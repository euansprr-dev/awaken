package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.FpSwings;
import dev.awaken.arms.HandPose;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The weapon in first person (SPEC §25): every move's swing from its row (wind-up to the swing's start pose, the
 * blow through to its end pose, the recovery home), the guard across the body, a held heavy drawn back and
 * trembling. The swing is a source on Hands' view layer ({@code FirstPerson.view}, docs/anims/PLAN.md §9), so it
 * composes with every other first-person move (the look-lag, a stance) through the one system; the item's own hand
 * transform only puts the blade at vanilla's resting place, with the guard, so vanilla's swing never plays over it.
 * The clock is the hands' own: hit-stop holds it, so a swing freezes on the blow and resumes where it stopped.
 */
final class ArmsHands {
    private ArmsHands() {}

    /** Wall-clock ticks the guard took to come up (2 ticks), and how it is held. */
    private static final float GUARD_RISE = 2F;
    /** Vanilla's resting hand and the shoulder a swing turns about (camera space: x right, y up, -z ahead). */
    private static final float HX = 0.56F, HY = -0.52F, HZ = -0.72F, SX = 0.35F, SY = -0.75F;
    private static final float[] POSE = new float[5], MOVE = new float[6];

    /** Hands' view source: this frame's swing, as a move about the hand's own place. */
    static void view(float partial, FirstPerson.View v) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !ArmsPredict.fights(p)) return;
        trace(p);
        if (lab(p, v)) return;
        pose(POSE);
        float x = POSE[0], y = POSE[1], roll = POSE[2], lunge = POSE[3], tilt = POSE[4];
        if (x == 0 && y == 0 && roll == 0 && lunge == 0 && tilt == 0) return;
        int inv = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        // the hand travels across the view and the blade turns in the screen's plane, its flat to the eye: a flat
        // sprite turned about the vertical goes edge-on and vanishes mid-cut. At rest this adds nothing.
        HandPose.viewMove(inv * HX, HY, HZ, inv * x, y, -lunge * 0.55F, inv * roll, tilt, MOVE);
        FirstPerson.View.add(v.main, 1, MOVE[0], MOVE[1], MOVE[2], MOVE[3], MOVE[4], MOVE[5]);
    }

    /** The blade lays its ribbon through the blow, on the hands' own clock: from just before it starts to just after. */
    private static void trace(LocalPlayer p) {
        Archetypes.Loaded style = ArmsPredict.style;
        Archetype.Move m = ArmsPredict.move;
        if (style == null || m == null || ArmsPredict.charging || preview != null) return;
        Archetype.MoveDef def = style.move(m);
        if (def == null) return;
        float t = ArmsPredict.moveTime(), b = blowStart(def);
        if (t >= b - 0.15F && t <= b + def.active() + 0.6F) {
            BladeTrail.open(p.getId(), 60, ArmsFx.colour(p.getMainHandItem(), ArmsPredict.cadence, ArmsPredict.perfectHeavy, m));
        }
    }

    // vanilla's handheld first-person display (item/handheld.json) and where a sword's blade runs on its sprite:
    // the guard at pixel (5.5, 10.5), the tip at (15.5, 1.5); in first person the trail runs from mid-blade (the tip's
    // path leaves the view at the top-left in most cuts: an outer band alone was only a sliver at the frame's edge)
    private static final float[] VANILLA_ROOT = {(5.5F + 10F * 0.45F) / 16F, 1 - (10.5F - 9F * 0.45F) / 16F, 0.5F},
            VANILLA_TIP = {15.5F / 16F, 1 - 1.5F / 16F, 0.5F};
    private static final org.joml.Matrix4f M = new org.joml.Matrix4f();

    /** The item's hand transform: vanilla's resting place (no swing of its own) and the guard. */
    static boolean transform(PoseStack ps, HumanoidArm arm, ItemStack stack, float partial, float equip) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || stack != p.getMainHandItem() || arm != p.getMainArm() || !ArmsPredict.fights(p)) return false;
        int inv = arm == HumanoidArm.RIGHT ? 1 : -1;
        ps.translate(inv * HX, HY + equip * -0.6F, HZ);
        float g = guard(p, partial);
        if (g > 0) {
            // across the body, edge out (vanilla's own blocking pose, eased in)
            ps.translate(inv * -0.141F * g, 0.08F * g, 0.141F * g);
            ps.rotateDegrees(Axis.XP, -102.25F * g);
            ps.rotateDegrees(Axis.YP, inv * 13.37F * g);
            ps.rotateDegrees(Axis.ZP, inv * 78.05F * g);
        }
        if (BladeTrail.open(p.getId()) && ArmsItems.of(stack.getItem()) == null) {
            // a vanilla blade: where its sprite's edge is drawn (our weapons sample their own mesh as they draw)
            M.set(ps.last().pose()).translate(1.13F / 16F, 3.2F / 16F, 1.13F / 16F) // both hands: as handheld.json
                    .rotateXYZ(0, inv * -90F * Mth.DEG_TO_RAD, inv * 25F * Mth.DEG_TO_RAD).scale(0.68F).translate(-0.5F, -0.5F, -0.5F);
            BladeTrail.sample(p.getId(), M, VANILLA_ROOT[0], VANILLA_ROOT[1], VANILLA_ROOT[2], VANILLA_TIP[0], VANILLA_TIP[1], VANILLA_TIP[2], true);
        }
        return true;
    }

    /** The guard's blend: up over its first ticks of use. */
    private static float guard(LocalPlayer p, float partial) {
        if (!p.isUsingItem() || p.getUseItem() != p.getMainHandItem()) return 0;
        return Math.min(1F, p.getTicksUsingItem(partial) / GUARD_RISE);
    }

    private static final org.joml.Vector3f D = new org.joml.Vector3f();
    private static final org.joml.Quaternionf Q = new org.joml.Quaternionf();

    /**
     * The lab's swing (FpSwings), when it has one for the move: an arc about the sword arm's pivot, the edge leading,
     * through the target under the crosshair on the hit tick. A held heavy waits at the heavy's drawn-back start (and
     * trembles), an armed Plunge at the Plunge's. Mirrored for a left main arm. False: the row's own swing below.
     */
    private static boolean lab(LocalPlayer p, FirstPerson.View v) {
        var style = ArmsPredict.style != null ? ArmsPredict.style : preview != null ? Archetypes.get(Arms.LONGSWORD) : null;
        if (style == null) return false;
        Archetype.Move m;
        float t;
        boolean tremble = false;
        if (preview != null) {
            m = preview;
            Archetype.MoveDef def = style.move(m);
            t = def == null ? 0 : previewAt * def.total();
        } else if (ArmsPredict.plunging) {
            m = Archetype.Move.PLUNGE;
            t = 0;
        } else if (ArmsPredict.charging) {
            m = Archetype.Move.HEAVY;
            t = 0;
            tremble = true;
        } else {
            m = ArmsPredict.move;
            t = ArmsPredict.moveTime();
        }
        if (m == null) return false;
        boolean ours = ArmsItems.of(p.getMainHandItem().getItem()) != null;
        if (!FpSwings.sample(FpSwings.style(style.id()), m, t, ours, D, Q)) return false;
        int inv = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (tremble) {
            float c = ArmsPredict.moveTime();
            float k = Mth.sin(c * 2.7F) * Math.min(1F, c / 10F);
            D.add(k * 0.008F, k * 0.012F, 0);
        }
        // which hand strikes: a weapon in each hand (the Daggers) strikes with the off hand too, the main's move mirrored
        FpSwings.Hand hand = preview != null ? FpSwings.Hand.MAIN : FpSwings.hand(FpSwings.style(style.id()), m);
        if (hand != FpSwings.Hand.OFF) addMove(v.main, inv);
        if (hand != FpSwings.Hand.MAIN) addMove(v.off, -inv);
        return true;
    }

    private static final org.joml.Vector3f D2 = new org.joml.Vector3f();
    private static final org.joml.Quaternionf Q2 = new org.joml.Quaternionf();

    /** The sampled move (D, Q, authored for a right hand) onto one hand's view move, mirrored for a left one. */
    private static void addMove(float[] to, int side) {
        D2.set(D);
        Q2.set(Q);
        if (side < 0) {
            D2.x = -D2.x;
            Q2.set(Q2.x, -Q2.y, -Q2.z, Q2.w); // the turn mirrored across the view's vertical plane
        }
        HandPose.viewMove(side * HX, HY, HZ, D2.x, D2.y, D2.z, Q2, MOVE);
        FirstPerson.View.add(to, 1, MOVE[0], MOVE[1], MOVE[2], MOVE[3], MOVE[4], MOVE[5]);
    }

    /** The move now in play strikes with the off hand alone (its blade lays the ribbon, the main's rests). */
    static boolean offStrikes() {
        var style = ArmsPredict.style;
        return style != null && ArmsPredict.move != null && FpSwings.hand(FpSwings.style(style.id()), ArmsPredict.move) == FpSwings.Hand.OFF;
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

    /**
     * The row's swing at {@code t} ticks: eased into its start pose, hard through the blow with its lunge, home. The
     * blow is centred on the server's hit (the first active tick): it starts half the active early, so the blade is
     * crossing the target as the hit lands and the hit-stop holds it there, not still cocked (the first tour on the
     * Mac); the recovery takes up the difference, so the move ends when the row says.
     */
    private static void swing(float[] out, Archetype.MoveDef def, float t, int recovery) {
        List<Float> from = def.swing().from(), to = def.swing().to();
        float a = def.active(), w = blowStart(def), r = Math.max(1, recovery) + (def.windup() - w);
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

    /** When the blade starts its blow, on the move's clock (ticks): half the active before the first active tick. */
    static float blowStart(Archetype.MoveDef def) {
        return def.windup() <= 0 ? 0 : Math.max(0.5F, def.windup() - def.active() * 0.5F);
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
