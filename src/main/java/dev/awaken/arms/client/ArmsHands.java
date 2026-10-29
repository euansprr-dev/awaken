package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.FpSwings;
import dev.awaken.arms.HandPose;
import dev.awaken.arms.SwingBlend;
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
        MAIN_D.zero();
        MAIN_Q.identity();
        OFF_D.zero();
        OFF_Q.identity();
        if (lab(p, v)) {
            grips(p, v, partial);
            return;
        }
        grips(p, v, partial);
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
        float t = ArmsPredict.viewTime(), b = blowStart(def);
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
        ArmsHands.equip = equip;
        ps.translate(inv * HX, HY + equip * -0.6F, HZ);
        guardOn(ps, p, stack, partial, inv);
        if (BladeTrail.open(p.getId()) && ArmsItems.of(stack.getItem()) == null) {
            // a vanilla blade: where its sprite's edge is drawn (our weapons sample their own mesh as they draw)
            M.set(ps.last().pose()).translate(1.13F / 16F, 3.2F / 16F, 1.13F / 16F) // both hands: as handheld.json
                    .rotateXYZ(0, inv * -90F * Mth.DEG_TO_RAD, inv * 25F * Mth.DEG_TO_RAD).scale(0.68F).translate(-0.5F, -0.5F, -0.5F);
            BladeTrail.sample(p.getId(), M, VANILLA_ROOT[0], VANILLA_ROOT[1], VANILLA_ROOT[2], VANILLA_TIP[0], VANILLA_TIP[1], VANILLA_TIP[2], true);
        }
        return true;
    }

    /**
     * The guard on a hand's pose stack (at its resting place) while the guard is up: the weapon across the body, mirrored
     * for a left hand ({@code inv} -1; a pair's twin is the main's mirrored, so the two cross).
     */
    static void guardOn(PoseStack ps, LocalPlayer p, ItemStack stack, float partial, int inv) {
        float g = guard(p, partial);
        if (g <= 0) return;
        float j = jolt(partial) * g;
        Archetypes.Loaded style = Arms.styleOf(stack);
        guardMove(style == null ? null : FpSwings.style(style.id()), ArmsItems.of(stack.getItem()) != null, g, j, inv, GD, GQ);
        if (inv < 0) {
            GD.x = -GD.x;
            GQ.set(GQ.x, -GQ.y, -GQ.z, GQ.w);
        }
        ps.translate(GD.x, GD.y, GD.z);
        ps.rotate(GQ);
    }

    /**
     * The guard's move and turn about the hand's place (authored right-handed), {@code g} of the way up, jolted by
     * {@code j}: the style's own (Swing Studio round 8: across the body in view, its tip up to the left; Euan 2026-10-29:
     * "blocking with most weapons hides the weapon under your view"), else vanilla 1.8's block turn.
     */
    private static void guardMove(FpSwings.@Nullable Style st, boolean ours, float g, float j, int inv, org.joml.Vector3f d,
                                  org.joml.Quaternionf q) {
        if (FpSwings.guard(st, ours, d, GQ2)) {
            d.mul(g);
            ID.slerp(GQ2, g, q);
        } else {
            q.identity().rotateX(-102.25F * g * Mth.DEG_TO_RAD).rotateY(13.37F * g * Mth.DEG_TO_RAD).rotateZ(78.05F * g * Mth.DEG_TO_RAD);
            d.set(-0.141F * g, 0.08F * g, 0.141F * g);
        }
        // a shot on it: knocked back toward the eye, up and away from the side it struck, its tip thrown back
        d.add(inv * joltSide * JOLT_X * j, JOLT_Y * j, JOLT_Z * j);
        GQ2.rotationX(JOLT_TIP * j * Mth.DEG_TO_RAD).mul(q, q);
    }

    private static final org.joml.Vector3f GD = new org.joml.Vector3f();
    private static final org.joml.Quaternionf GQ = new org.joml.Quaternionf(), GQ2 = new org.joml.Quaternionf();

    /** The guard's blend: up over its first ticks of use. */
    private static float guard(LocalPlayer p, float partial) {
        if (!p.isUsingItem() || p.getUseItem() != p.getMainHandItem()) return 0;
        return Math.min(1F, p.getTicksUsingItem(partial) / GUARD_RISE);
    }

    // a shot on the guard (SPEC §25 "Shots"): the blade knocked back toward the eye, up, and away from the side it
    // struck (blocks), its tip thrown back (degrees); it rings out over JOLT_TICKS
    private static final float JOLT_X = 0.035F, JOLT_Y = 0.03F, JOLT_Z = 0.075F, JOLT_TIP = 9F, JOLT_TICKS = 9F;
    private static int joltAt = -1000;
    private static float joltSize, joltSide;

    /** A shot struck the guard: {@code side} -1..1 (+ from the right), {@code size} 1 for a deflect's knock. */
    static void jolt(float side, float size) {
        joltAt = ArmsPredict.ticks;
        joltSize = size;
        joltSide = -Mth.clamp(side, -1F, 1F); // pushed away from it
    }

    /** The jolt now: at once, then a damped rebound. */
    private static float jolt(float partial) {
        float t = ArmsPredict.ticks - joltAt + partial;
        if (t < 0 || t >= JOLT_TICKS) return 0;
        return joltSize * (float) Math.exp(-t / 2.2F) * Mth.cos(t * 1.25F) * (1 - t / JOLT_TICKS);
    }

    private static final org.joml.Vector3f D = new org.joml.Vector3f();
    private static final org.joml.Quaternionf Q = new org.joml.Quaternionf();

    private static boolean far;

    /** Whether the move on the hands now is led by its double weapon's other end (its trail runs along that end). */
    static boolean far() {
        return far;
    }

    /**
     * The lab's swing (FpSwings), when it has one for the move: an arc about the sword arm's pivot, the edge leading,
     * through the target under the crosshair on the hit tick. A held heavy waits at the heavy's drawn-back start (and
     * trembles), an armed Plunge at the Plunge's. Mirrored for a left main arm. False: the row's own swing below.
     */
    private static boolean lab(LocalPlayer p, FirstPerson.View v) {
        far = false;
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
            t = ArmsPredict.viewTime();
        }
        boolean ours = ArmsItems.of(p.getMainHandItem().getItem()) != null;
        FpSwings.Style st = FpSwings.style(style.id());
        boolean has = m != null && FpSwings.sample(st, m, t, ours, D, Q);
        far = has && FpSwings.far(st, m);
        if (!has) {
            D.zero();
            Q.identity();
        }
        int inv = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (tremble) {
            float c = ArmsPredict.moveTime();
            float k = Mth.sin(c * 2.7F) * Math.min(1F, c / 10F);
            D.add(k * 0.008F, k * 0.012F, 0);
        }
        // which hand strikes: a weapon in each hand (the Daggers) strikes with the off hand too, the main's move mirrored
        FpSwings.Hand hand = !has || preview != null ? FpSwings.Hand.MAIN : FpSwings.hand(st, m);
        boolean mainMoves = has && hand != FpSwings.Hand.OFF, offMoves = has && hand != FpSwings.Hand.MAIN;
        // a weapon in each hand: the hand not striking draws back to the next strike's set while this one lands (Swing
        // Studio round 4; Euan 2026-10-28: "let the idle hand draw back while the other strikes"); in a both-hands
        // strike, the off hand's own way into it, from where it waited (round 9)
        boolean idle = has && !tremble && FpSwings.idle(st, m, t, ours, IDLE_D, IDLE_Q);
        boolean twin = idle && hand == FpSwings.Hand.BOTH;
        if (twin) offMoves = false;
        boolean mainIdle = idle && !mainMoves, offIdle = idle && !offMoves;
        // a change of source (a chained strike, a move ended or refused, the charge taken up): each hand carried from
        // where it was shown into the new one, never a jump (SwingBlend)
        float now = handsNow();
        if (m != shownMove || ArmsPredict.moveNanos != shownNanos || ArmsPredict.charging != shownCharging) {
            shownMove = m;
            shownNanos = ArmsPredict.moveNanos;
            shownCharging = ArmsPredict.charging;
            Archetype.MoveDef def = m == null ? null : style.move(m);
            float ticks = def == null ? REST_BLEND : Mth.clamp(def.windup() * 0.8F, 1F, 2.5F);
            sourceMotion(st, m, t, ours, has && !tremble);
            MAIN_B.begin(now, mainMoves ? D : mainIdle ? IDLE_D : ZERO, mainMoves ? Q : mainIdle ? IDLE_Q : ID, mainMoves ? VD : ZERO,
                    mainMoves ? VW : ZERO, ticks);
            OFF_B.begin(now, offMoves ? D : offIdle ? IDLE_D : ZERO, offMoves ? Q : offIdle ? IDLE_Q : ID, offMoves ? VD : ZERO,
                    offMoves ? VW : ZERO, ticks);
        }
        boolean mainShown = mainMoves || mainIdle || MAIN_B.active(now), offShown = offMoves || offIdle || OFF_B.active(now);
        if (mainMoves) MAIN_D.set(D); else if (mainIdle) MAIN_D.set(IDLE_D); else MAIN_D.zero();
        if (mainMoves) MAIN_Q.set(Q); else if (mainIdle) MAIN_Q.set(IDLE_Q); else MAIN_Q.identity();
        if (offMoves) OFF_D.set(D); else if (offIdle) OFF_D.set(IDLE_D); else OFF_D.zero();
        if (offMoves) OFF_Q.set(Q); else if (offIdle) OFF_Q.set(IDLE_Q); else OFF_Q.identity();
        MAIN_B.apply(now, MAIN_D, MAIN_Q);
        OFF_B.apply(now, OFF_D, OFF_Q);
        if (!mainShown && !offShown) return false;
        if (mainShown) addMove(v.main, inv, MAIN_D, MAIN_Q);
        if (offShown) addMove(v.off, -inv, OFF_D, OFF_Q);
        return true;
    }

    /** Ticks since a move of the hands' clock (hit-stop holds pause it, the tick rate scales it). */
    private static float handsNow() {
        return (System.nanoTime() - ArmsFeel.paused()) / ArmsPredict.nanosPerTick();
    }

    // each hand carried across a change of its source; what was shown last; the new source's motion (VD blocks a tick,
    // VW radians a tick about an axis), sampled just ahead
    private static final SwingBlend MAIN_B = new SwingBlend(), OFF_B = new SwingBlend();
    private static final float REST_BLEND = 3F;
    private static Archetype.@Nullable Move shownMove;
    private static long shownNanos;
    private static boolean shownCharging;
    private static final org.joml.Vector3f VD = new org.joml.Vector3f(), VW = new org.joml.Vector3f(), ZERO = new org.joml.Vector3f(),
            DA = new org.joml.Vector3f(), IDLE_D = new org.joml.Vector3f();
    private static final org.joml.Quaternionf IDLE_Q = new org.joml.Quaternionf();
    private static final org.joml.Quaternionf ID = new org.joml.Quaternionf(), QA = new org.joml.Quaternionf(), QC = new org.joml.Quaternionf();

    /** The new source's motion at {@code t} into VD, VW (zero for a held pose). */
    private static void sourceMotion(FpSwings.@Nullable Style st, Archetype.@Nullable Move m, float t, boolean ours, boolean moving) {
        VD.zero();
        VW.zero();
        if (!moving || m == null) return;
        float h = 0.1F;
        if (!FpSwings.sample(st, m, t + h, ours, DA, QA)) return;
        VD.set(DA).sub(D).div(h);
        Q.conjugate(QC);
        QA.mul(QC, QA); // the turn over h
        if (QA.w < 0) QA.set(-QA.x, -QA.y, -QA.z, -QA.w);
        float sn = (float) Math.sqrt(Math.max(0F, 1F - QA.w * QA.w));
        if (sn > 1e-6F) VW.set(QA.x / sn, QA.y / sn, QA.z / sn).mul(2F * (float) Math.acos(Math.min(1F, QA.w)) / h);
    }

    // each hand's move this frame (authored right-handed, before mirroring): where its grip is
    private static final org.joml.Vector3f MAIN_D = new org.joml.Vector3f(), OFF_D = new org.joml.Vector3f(), G = new org.joml.Vector3f();
    private static final org.joml.Quaternionf MAIN_Q = new org.joml.Quaternionf(), OFF_Q = new org.joml.Quaternionf(),
            GUARD = new org.joml.Quaternionf();
    /** The main item's equip rise this frame (vanilla's swap: the item sinks and comes back up), from its hand transform. */
    private static float equip;

    /**
     * The arms that hold the weapon (FirstPerson.View's grips; Euan 2026-10-26: "it feels like the weapon is just floating
     * and attacking on its own"): each hand's arm rises from the frame's corner to where it closes on the weapon, at
     * rest, through every swing's turn and the guard, the off hand's on the twin of a pair. A vanilla item's hand is on
     * its sprite's handle.
     */
    private static void grips(LocalPlayer p, FirstPerson.View v, float partial) {
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        FpSwings.Style st = style == null ? null : FpSwings.style(style.id());
        if (st == null) return;
        boolean ours = ArmsItems.of(p.getMainHandItem().getItem()) != null;
        int inv = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float g = guard(p, partial), j = g > 0 ? jolt(partial) * g : 0;
        if (g > 0) guardMove(st, ours, g, j, inv, GD, GUARD);
        onMain(G.set(ours ? st.hand() : st.vanillaGrip()), g);
        FirstPerson.View.fist(v.gripMain, 1, inv * G.x, G.y, G.z);
        org.joml.Vector3f both = ours ? st.offHand() : st.vanillaOffHand();
        if (!ArmsOffHand.shownTwin(p).isEmpty()) {
            G.set(st.hand());
            if (g > 0) { // the twin up in guard too, mirrored: the pair crossed
                GUARD.transform(G);
                G.add(GD);
            }
            OFF_Q.transform(G);
            G.add(OFF_D).add(HX, HY + ArmsOffHand.equip() * -0.6F, HZ); // the twin rises with the main hand
            FirstPerson.View.fist(v.gripOff, 1, -inv * G.x, G.y, G.z);
        } else if (both != null && p.getOffhandItem().isEmpty()) {
            // a two-handed weapon with the off hand free: the off hand closes on the handle below the main, through
            // every swing and the guard (SPEC §25 "Swing Studio": the katana, greatblade, maul, spear, glaive, scythe,
            // war-staff and twin blades); a stance's own off fist still takes the hand (Hands' hideOff)
            onMain(G.set(both), g);
            FirstPerson.View.fist(v.gripOff, 1, inv * G.x, G.y, G.z);
        }
    }

    /**
     * A point on the main weapon (hand space, authored right-handed) to where it is this frame: the guard (GUARD and GD,
     * set by the caller from guardMove: the hands go with the blade), the swing.
     */
    private static void onMain(org.joml.Vector3f at, float g) {
        if (g > 0) {
            GUARD.transform(at);
            at.add(GD);
        }
        MAIN_Q.transform(at);
        at.add(MAIN_D).add(HX, HY + equip * -0.6F, HZ);
    }

    private static final org.joml.Vector3f D2 = new org.joml.Vector3f();
    private static final org.joml.Quaternionf Q2 = new org.joml.Quaternionf();

    /** A hand's move (authored for a right hand) onto its view move, mirrored for a left one. */
    private static void addMove(float[] to, int side, org.joml.Vector3f d, org.joml.Quaternionf q) {
        D2.set(d);
        Q2.set(q);
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
