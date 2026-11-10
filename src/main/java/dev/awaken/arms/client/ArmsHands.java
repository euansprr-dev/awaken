package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.ChargeDraw;
import dev.awaken.arms.FpSwings;
import dev.awaken.arms.HandPose;
import dev.awaken.arms.ArmsRules;
import dev.awaken.arms.SwingBlend;
import dev.awaken.arms.SwingWeight;
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
        if (p == null || !ArmsPredict.fights(p)) {
            FirstPerson.lagReach = FirstPerson.lagRate = 1F;
            return;
        }
        // the hands trail the view's turn by the weapon's weight: daggers tight, a Maul further and slower (SwingWeight)
        SwingWeight.Weight weight = ArmsComfort.livingHold() ? weightInHand(p) : SwingWeight.BLADE;
        FirstPerson.lagReach = weight.lagReach();
        FirstPerson.lagRate = weight.lagRate();
        trace(p);
        MAIN_D.zero();
        MAIN_Q.identity();
        OFF_D.zero();
        OFF_Q.identity();
        if (lab(p, v, partial)) {
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

    /**
     * The blade lays its ribbon through the blow, on the hands' own clock: from just before it starts to just after, or
     * (round 14) over the clip's own trail ticks, the stroke alone. The striking hand's blade lays it, each blade of a
     * pair its own ring; a both-hands strike with its own ticks lays both.
     */
    private static void trace(LocalPlayer p) {
        Archetypes.Loaded style = ArmsPredict.style;
        Archetype.Move m = ArmsPredict.move;
        if (style == null || m == null || ArmsPredict.drawn() || preview != null) return;
        Archetype.MoveDef def = style.move(m);
        if (def == null) return;
        FpSwings.Style fs = FpSwings.style(style.id());
        FpSwings.Clip clip = FpSwings.clip(fs, m);
        boolean own = clip != null && clip.trailTo() >= 0;
        float t = ArmsPredict.viewTime(), b = blowStart(def);
        if (own ? t < clip.trailFrom() || t > clip.trailTo() : t < b - 0.15F || t > b + def.active() + 0.6F) return;
        int argb = ArmsFx.colour(p.getMainHandItem(), ArmsPredict.cadence, ArmsPredict.perfectHeavy, m);
        float light = (fs != null && fs.trail() != null ? fs.trail()[1] : 1F) * (ArmsPredict.drawnCut ? DRAWN_LIGHT : 1F);
        FpSwings.Hand h = clip == null ? FpSwings.Hand.MAIN : clip.hand();
        if (h != FpSwings.Hand.OFF) BladeTrail.open(p.getId(), 0, 60, argb, light);
        if (h == FpSwings.Hand.OFF || h == FpSwings.Hand.BOTH && own) BladeTrail.open(p.getId(), 1, 60, argb, light);
    }

    /** Where the ribbon starts along the blade in first person (a style's own, else from mid-blade). */
    static float trailRoot() {
        var style = ArmsPredict.style;
        FpSwings.Style fs = style == null ? null : FpSwings.style(style.id());
        return fs != null && fs.trail() != null ? fs.trail()[0] : 0.5F;
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
        // the draw (SwingWeight): a changed weapon comes up on its own clock with a turn of the wrist. Vanilla raises it only
        // as fast as its attack cooldown refills, a cooldown the fighting never uses: nine ticks out of view for a Katana
        // on film, over twenty by that rule for the Maul, and a strike thrown meanwhile was drawn that far under the frame
        float turn = 0F;
        if (ArmsComfort.weaponDraw()) {
            double now = handsNow();
            if (drawPending) {
                drawPending = false;
                drawAt = now;
            }
            float t = (float) (now - drawAt);
            equip = SwingWeight.drawSink(t, drawTicks);
            turn = SwingWeight.drawTurn(t, drawTicks);
        }
        ArmsHands.equip = equip;
        ps.translate(inv * HX, HY + equip * -0.6F, HZ);
        if (turn != 0F) ps.rotateDegrees(com.mojang.math.Axis.ZP, inv * turn);
        guardOn(ps, p, stack, partial, inv, false);
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
    static void guardOn(PoseStack ps, LocalPlayer p, ItemStack stack, float partial, int inv, boolean off) {
        float g = guard(p, partial);
        if (g <= 0) return;
        float j = jolt(partial) * g;
        Archetypes.Loaded style = Arms.styleOf(stack);
        guardMove(style == null ? null : FpSwings.style(style.id()), ArmsItems.of(stack.getItem()) != null, g, j, inv, GD, GQ, off);
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
                                  org.joml.Quaternionf q, boolean off) {
        if (off && FpSwings.offGuard(st, d, GQ2) || FpSwings.guard(st, ours, d, GQ2)) {
            d.mul(g);
            ID.slerp(GQ2, g, q);
        } else {
            q.identity().rotateX(-102.25F * g * Mth.DEG_TO_RAD).rotateY(13.37F * g * Mth.DEG_TO_RAD).rotateZ(78.05F * g * Mth.DEG_TO_RAD);
            d.set(-0.141F * g, 0.08F * g, 0.141F * g);
        }
        // a shot on it: knocked back toward the eye, up and away from the side it struck, its tip thrown back
        d.add(inv * joltSide * JOLT_X * j, JOLT_Y * j, (joltToward ? -JOLT_Z : JOLT_Z) * j);
        GQ2.rotationX(JOLT_TIP * j * Mth.DEG_TO_RAD).mul(q, q);
    }

    private static final org.joml.Vector3f GD = new org.joml.Vector3f(), GD_OFF = new org.joml.Vector3f();
    private static final org.joml.Quaternionf GUARD_OFF = new org.joml.Quaternionf();
    private static final org.joml.Quaternionf GQ = new org.joml.Quaternionf(), GQ2 = new org.joml.Quaternionf();

    /**
     * The guard's blend. It comes up on a curve and seats with a small settle, in the two ticks the server's own raise
     * takes, and lowers over three (SwingWeight): it rose in a straight line and was back at rest one frame after the key
     * came up, measured on film. Only the picture: the deflect and block windows are the server's.
     */
    private static float guard(LocalPlayer p, float partial) {
        boolean up = p.isUsingItem() && p.getUseItem() == p.getMainHandItem();
        if (!ArmsComfort.guardEase()) {
            guardWas = up;
            return guardNow = up ? Math.min(1F, p.getTicksUsingItem(partial) / GUARD_RISE) : 0;
        }
        double now = handsNow();
        if (up != guardWas) { // the key went down or came up: carry on from wherever the guard is
            guardWas = up;
            guardFrom = guardNow;
            guardAt = now;
        }
        float t = (float) (now - guardAt);
        return guardNow = up ? SwingWeight.guardUp(t, guardFrom) : SwingWeight.guardDown(t, guardFrom);
    }

    private static boolean guardWas;
    private static float guardFrom, guardNow;
    private static double guardAt;

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
        joltToward = false;
    }

    /**
     * A blow deflected: the blade is thrown at it and back, a third harder than a block's knock (SPEC §25 "Weight in the
     * Hand": a parry answers the blow, it doesn't only take it).
     */
    static void parry(float side) {
        joltAt = ArmsPredict.ticks;
        joltSize = PARRY_SIZE;
        joltSide = Mth.clamp(side, -1F, 1F); // toward where it came from
        joltToward = true;
    }

    private static final float PARRY_SIZE = 1.3F;
    private static boolean joltToward;

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
     * through the target under the crosshair on the hit tick. A press changes nothing until it has outlasted a click; then
     * it heads for the heavy's drawn-back start and a held heavy waits there (and strains), an armed Plunge at the Plunge's; with no move, the chop or the hack while vanilla mines with the weapon
     * (ArmsChop). Mirrored for a left main arm. False: the row's own swing below.
     */
    private static boolean lab(LocalPlayer p, FirstPerson.View v, float partial) {
        far = false;
        // the chop is the weapon in hand's own (before any strike, or after one with another weapon)
        Archetypes.Loaded inHand = Arms.styleOf(p.getMainHandItem());
        var style = ArmsPredict.style != null ? ArmsPredict.style : preview != null ? Archetypes.get(Arms.LONGSWORD) : inHand;
        if (style == null) return false;
        Archetype.Move m;
        float t;
        boolean drawn = false, tremble = false, undecided = false;
        if (preview != null) {
            m = preview;
            Archetype.MoveDef def = style.move(m);
            t = def == null ? 0 : previewAt * def.total();
        } else if (ArmsPredict.plunging) {
            m = Archetype.Move.PLUNGE;
            t = 0;
        } else if (ArmsPredict.drawn()) {
            m = Archetype.Move.HEAVY;
            t = 0;
            drawn = true;
            tremble = ArmsPredict.charging;
        } else if (ArmsPredict.undecided()) {
            // not yet a tap or a hold: the hands go on with what they were doing, as if nothing had been pressed
            m = ArmsPredict.underMove();
            t = ArmsPredict.underTime();
            undecided = true;
        } else {
            m = ArmsPredict.move;
            t = ArmsPredict.viewTime();
        }
        boolean ours = ArmsItems.of(p.getMainHandItem().getItem()) != null;
        FpSwings.Style st = FpSwings.style(style.id());
        boolean has = m != null && FpSwings.sample(st, m, t, ours, D, Q);
        boolean cutting = !has && preview == null && inHand != null && ArmsChop.pose(FpSwings.style(inHand.id()), ours, partial, D, Q);
        has |= cutting;
        far = has && m != null && FpSwings.far(st, m);
        if (!has) {
            D.zero();
            Q.identity();
        }
        int inv = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (tremble) {
            float c = ArmsPredict.moveTime();
            // a slow strain: at 2.7 radians a tick this was an 8 Hz buzz that read as the weapon glitching
            float k = Mth.sin(c * 0.9F) * Math.min(1F, c / 14F);
            D.add(k * 0.005F, k * 0.007F, 0);
        }
        // which hand strikes: a weapon in each hand (the Daggers) strikes with the off hand too, the main's move mirrored
        FpSwings.Hand hand = !has || preview != null ? FpSwings.Hand.MAIN : FpSwings.hand(st, m);
        boolean mainMoves = has && hand != FpSwings.Hand.OFF, offMoves = has && hand != FpSwings.Hand.MAIN;
        // a weapon in each hand: the hand not striking draws back to the next strike's set while this one lands (Swing
        // Studio round 4; Euan 2026-10-28: "let the idle hand draw back while the other strikes"); in a both-hands
        // strike, the off hand's own way into it, from where it waited (round 9)
        boolean idle = has && !drawn && FpSwings.idle(st, m, t, ours, IDLE_D, IDLE_Q);
        boolean twin = idle && hand == FpSwings.Hand.BOTH;
        if (twin) offMoves = false;
        boolean mainIdle = idle && !mainMoves, offIdle = idle && !offMoves;
        // between moves, the style in hand's stance (SPEC §25 "Hand-keyed moves": the Daggers held forward, the points in
        // toward the crosshair), both hands of a pair; let go as the guard comes up, and never while the weapon cuts
        FpSwings.Style rest = inHand == null ? null : FpSwings.style(inHand.id());
        boolean stance = !cutting && preview == null && FpSwings.stance(rest, ours, REST_D, STANCE_Q);
        boolean offStance = stance && !ArmsOffHand.shownTwin(p).isEmpty();
        float restK = stance ? 1F - guard(p, partial) : 0F;
        if (stance) {
            REST_D.mul(restK);
            REST_Q.identity().slerp(STANCE_Q, restK);
        } else {
            REST_D.zero();
            REST_Q.identity();
        }
        // the Katana rested two seconds is lowered to the hip (its drawn cut's bonus, shown): the rest pose itself moves there
        // on an ease, so the strike that draws it is carried out of it by the blend like any other
        float hip = hip(p, inHand, partial) * (1F - guard(p, partial));
        if (hip > 0F && !cutting && preview == null) {
            REST_D.lerp(HIP_D, hip);
            REST_Q.slerp(HIP_Q, hip);
            stance = true;
        }
        if (offStance && FpSwings.offStance(rest, OFF_REST_D, STANCE_Q)) { // a shield rests as a shield, never as the sword's mirror
            OFF_REST_D.mul(restK);
            OFF_REST_Q.identity().slerp(STANCE_Q, restK);
        } else {
            OFF_REST_D.set(REST_D);
            OFF_REST_Q.set(REST_Q);
        }
        // a change of source (a chained strike, a move ended or refused, a press starting its draw, a tap's launch, the
        // release): each hand carried from where it was shown into the new one, never a jump (SwingBlend). The draw and
        // the charge held after it are one source, so the charge's mark changes nothing on the hands
        double now = handsNow();
        long source = undecided ? ArmsPredict.underSource : ChargeDraw.source(drawn, ArmsPredict.moveNanos);
        if (m != shownMove || source != shownNanos || cutting != shownCutting) {
            boolean cutOut = shownCutting && !cutting;
            shownMove = m;
            shownNanos = source;
            shownCutting = cutting;
            Archetype.MoveDef def = m == null ? null : style.move(m);
            float launch = drawn ? 0F : ArmsPredict.takeLaunch();
            float ticks = cutOut ? ArmsChop.OUT : drawn ? ArmsPredict.drawTicks() : launch > 0 ? launch : def == null ? REST_BLEND
                    : Mth.clamp(def.windup() * 0.8F, 1F, 2.5F);
            ArmsFilm.event("source " + (m == null ? "-" : m.key) + " at " + t + " blend " + ticks + (drawn ? " drawn" : ""));
            sourceMotion(st, m, t, ours, has && !drawn, launch > 0 ? ArmsPredict.launchRate() : 1F);
            MAIN_B.begin(now, mainMoves ? D : mainIdle ? IDLE_D : REST_D, mainMoves ? Q : mainIdle ? IDLE_Q : REST_Q,
                    mainMoves ? VD : ZERO, mainMoves ? VW : ZERO, ticks);
            OFF_B.begin(now, offMoves ? D : offIdle ? IDLE_D : offStance ? OFF_REST_D : ZERO, offMoves ? Q : offIdle ? IDLE_Q : offStance ? OFF_REST_Q : ID,
                    offMoves ? VD : ZERO, offMoves ? VW : ZERO, ticks);
        }
        // the living hold (SwingWeight): a held weapon breathes, so it is shown even when nothing else moves it
        float breath = preview == null && ArmsComfort.livingHold() ? SwingWeight.breath(ArmsFeel.nanos()) : 0F;
        boolean pair = breath != 0F && !ArmsOffHand.shownTwin(p).isEmpty();
        boolean mainShown = mainMoves || mainIdle || stance || breath != 0F || MAIN_B.active(now),
                offShown = offMoves || offIdle || offStance || pair || OFF_B.active(now);
        if (mainMoves) MAIN_D.set(D); else if (mainIdle) MAIN_D.set(IDLE_D); else MAIN_D.set(REST_D);
        if (mainMoves) MAIN_Q.set(Q); else if (mainIdle) MAIN_Q.set(IDLE_Q); else MAIN_Q.set(REST_Q);
        if (offMoves) OFF_D.set(D); else if (offIdle) OFF_D.set(IDLE_D); else if (offStance) OFF_D.set(OFF_REST_D); else OFF_D.zero();
        if (offMoves) OFF_Q.set(Q); else if (offIdle) OFF_Q.set(IDLE_Q); else if (offStance) OFF_Q.set(OFF_REST_Q); else OFF_Q.identity();
        // the settle: where the striking hand stops after its cut it carries a little past and comes back once, by its weight
        float settle = has && !drawn && !undecided && !cutting && preview == null && m != null ? settle(st, style, m, t) : 0F;
        if (settle > 0F) {
            SwingWeight.Weight w = SwingWeight.of(style.id());
            if (mainMoves) settleOn(MAIN_D, MAIN_Q, w, settle);
            if (offMoves) settleOn(OFF_D, OFF_Q, w, settle);
        }
        MAIN_D.y += breath;
        OFF_D.y += breath;
        MAIN_B.apply(now, MAIN_D, MAIN_Q);
        OFF_B.apply(now, OFF_D, OFF_Q);
        // after the blend, so neither is carried into the next move: the shake of a blow frozen on the hands, and a glance's bounce
        float shake = shakeNow();
        if (shake != 0F) {
            if (shakeHand != FpSwings.Hand.OFF) MAIN_D.add(shakeX * shake, shakeY * shake, 0F);
            if (shakeHand != FpSwings.Hand.MAIN) OFF_D.add(-shakeX * shake, shakeY * shake, 0F); // the off hand's move is mirrored on its way to the screen
        }
        float bounce = bounceNow(now);
        if (bounce != 0F) MAIN_D.add(-bounceX * BOUNCE * bounce, -bounceY * BOUNCE * bounce, BOUNCE_BACK * bounce);
        ArmsBlackBox.frame(m, t, (undecided ? ArmsBlackBox.UNDECIDED : 0) | (drawn ? ArmsBlackBox.DRAWN : 0) | (tremble ? ArmsBlackBox.CHARGING : 0)
                | (ArmsPredict.waitHit ? ArmsBlackBox.WAITING : 0) | (Minecraft.getInstance().options.keyAttack.isDown() ? ArmsBlackBox.KEY : 0)
                | (has ? ArmsBlackBox.HAS : 0) | (MAIN_B.active(now) ? ArmsBlackBox.BLENDING : 0), D, Q, MAIN_D, MAIN_Q, OFF_D, OFF_Q);
        if (!mainShown && !offShown) return false;
        if (mainShown) addMove(v.main, inv, MAIN_D, MAIN_Q);
        if (offShown) addMove(v.off, -inv, OFF_D, OFF_Q);
        return true;
    }

    // ---- the weight layers (SwingWeight; SPEC §25 "Weight in the Hand") ------------------------------------------------

    /** The weight of the weapon in hand. */
    private static SwingWeight.Weight weightInHand(LocalPlayer p) {
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        return SwingWeight.of(style == null ? null : style.id());
    }

    // the shake: armed by a landed blow of the move on the hands, for the freeze that blow began
    private static float shakeX, shakeY, shakeSize;
    private static FpSwings.Hand shakeHand = FpSwings.Hand.MAIN;
    private static long shakeHold = Long.MIN_VALUE;

    /**
     * A blow of the move on the hands has landed and frozen them: for that freeze the weapon shudders along the blow
     * ({@code bx}, {@code by}: its travel on the screen, x to the right, y up, authored right-handed), by the style's weight.
     */
    static void shake(Archetypes.Loaded style, Archetype.Move move, float bx, float by) {
        float n = (float) Math.sqrt(bx * bx + by * by);
        if (n < 1e-4F) { // a thrust drives into the screen: it shudders up and down the blade's own line, a nod
            bx = 0F;
            by = 1F;
            n = 1F;
        }
        shakeX = bx / n;
        shakeY = by / n;
        shakeSize = SwingWeight.of(style.id()).shake();
        shakeHand = FpSwings.hand(FpSwings.style(style.id()), move);
        shakeHold = ArmsFeel.holdStart();
    }

    /** The shake this frame (blocks along the blow, signed): nothing once its freeze is over, or with the layer off. */
    private static float shakeNow() {
        if (shakeHold == Long.MIN_VALUE) return 0F;
        float u = ArmsFeel.holdU();
        if (u < 0F || ArmsFeel.holdStart() != shakeHold) {
            shakeHold = Long.MIN_VALUE;
            return 0F;
        }
        return SwingWeight.shake(u, ArmsFeel.nanos()) * shakeSize * ArmsComfort.shake();
    }

    /** Where a clip's hand stops after its hit and the way it came in (SwingWeight.stop), worked out once a clip. */
    private record Stop(float tick, org.joml.Vector3f way, org.joml.Vector3f axis) {}

    private static final Stop NO_STOP = new Stop(-1F, new org.joml.Vector3f(), new org.joml.Vector3f());
    private static final java.util.IdentityHashMap<FpSwings.Clip, Stop> STOPS = new java.util.IdentityHashMap<>();
    private static Stop stopNow = NO_STOP;

    /** The settle of the move on the hands at {@code t} ticks of its clock (0..1, scaled by the setting), its stop left in {@code stopNow}. */
    private static float settle(FpSwings.@Nullable Style st, Archetypes.Loaded style, Archetype.Move m, float t) {
        float k = ArmsComfort.settle();
        FpSwings.Clip clip = FpSwings.clip(st, m);
        Archetype.MoveDef def = style.move(m);
        if (k <= 0F || clip == null || def == null) return 0F;
        Stop stop = STOPS.get(clip);
        if (stop == null) {
            org.joml.Vector3f way = new org.joml.Vector3f(), axis = new org.joml.Vector3f();
            float hit = m == Archetype.Move.HEAVY ? FpSwings.lands(st, m, def) : clip.lands() >= 0 ? clip.lands() : def.windup();
            float tick = SwingWeight.stop(clip.keys(), FpSwings.dt(), hit, way, axis);
            stop = tick < 0 ? NO_STOP : new Stop(tick, way, axis);
            if (STOPS.size() > 512) STOPS.clear(); // a tuning session swaps whole sets of clips in
            STOPS.put(clip, stop);
        }
        stopNow = stop;
        return stop.tick() < 0 ? 0F : k * SwingWeight.settle(t - stop.tick(), SwingWeight.of(style.id()).settleTicks());
    }

    private static final org.joml.Quaternionf SETTLE_Q = new org.joml.Quaternionf();

    /** The settle on a hand's pose: carried {@code k} of the weight's way past its stop, along the way it came in. */
    private static void settleOn(org.joml.Vector3f d, org.joml.Quaternionf q, SwingWeight.Weight w, float k) {
        d.fma(w.settle() * k, stopNow.way());
        org.joml.Vector3f a = stopNow.axis();
        if (a.lengthSquared() > 0F) q.premul(SETTLE_Q.fromAxisAngleDeg(a.x, a.y, a.z, w.settleTurn() * k));
    }

    // a glance off a wall (stage 5): the blade knocked back against its cut and toward the eye, rung out over two ticks and a half
    private static final float BOUNCE = 0.05F, BOUNCE_BACK = 0.06F, BOUNCE_TICKS = 2.5F;
    private static float bounceX, bounceY;
    private static double bounceAt = -1000;

    /** The blade met a block: it bounces back against the way it was cutting ({@code bx}, {@code by} on the screen, right-handed). */
    static void bounce(float bx, float by) {
        float n = Math.max(1e-4F, (float) Math.sqrt(bx * bx + by * by));
        bounceX = bx / n;
        bounceY = by / n;
        bounceAt = handsNow();
    }

    private static float bounceNow(double now) {
        float t = (float) (now - bounceAt);
        if (t < 0F || t >= BOUNCE_TICKS) return 0F;
        return (float) Math.exp(-t / 0.8F) * (1F - t / BOUNCE_TICKS);
    }

    // the draw: the weapon in hand changed (seen each tick, so a change made in third person is known too)
    private static net.minecraft.world.item.@Nullable Item drawItem;
    private static int drawSlot = -1, drawSoundAt = -1000;
    private static boolean drawKnown, drawPending;
    private static double drawAt = -1000;
    private static float drawTicks = 8F;

    /** Each tick: a change of the fighting weapon in hand starts its draw and plays its family's sound. */
    static void tick(LocalPlayer p) {
        Archetypes.Loaded style = ArmsPredict.fights(p) ? Arms.styleOf(p.getMainHandItem()) : null;
        net.minecraft.world.item.Item item = style == null ? null : p.getMainHandItem().getItem();
        int slot = p.getInventory().getSelectedSlot();
        if (item == drawItem && slot == drawSlot) return;
        boolean first = !drawKnown; // the weapon already in hand on joining a world isn't drawn
        drawKnown = true;
        drawItem = item;
        drawSlot = slot;
        if (style == null || first || !ArmsComfort.weaponDraw()) return;
        drawPending = true; // its clock starts on the frame the new weapon is first drawn, once the old one has sunk
        drawTicks = SwingWeight.of(style.id()).drawTicks();
        if (ArmsPredict.ticks - drawSoundAt >= DRAW_SOUND_GAP) {
            drawSoundAt = ArmsPredict.ticks;
            ArmsAudio.draw(p, style.id());
        }
    }

    /** Ticks between one draw's sound and the next: scrolling across the hotbar doesn't rattle. */
    private static final int DRAW_SOUND_GAP = 4;

    /** A strike has begun: a weapon still coming up is up within {@link SwingWeight#DRAW_HURRY} ticks, from where it is. */
    static void hurryDraw() {
        if (drawPending) { // not drawn once yet: it comes up with the strike
            drawPending = false;
            drawAt = handsNow();
            drawTicks = SwingWeight.DRAW_HURRY;
            return;
        }
        double now = handsNow();
        float t = (float) (now - drawAt);
        if (t < 0F || t >= drawTicks - SwingWeight.DRAW_HURRY) return;
        float u = t / drawTicks; // the same share of the way up, on a clock that ends two ticks from now
        drawTicks = SwingWeight.DRAW_HURRY / (1F - u);
        drawAt = now - u * drawTicks;
    }

    // the Katana's rest at the hip: its hand lowered toward the middle, the blade laid over to the off side and back past the
    // hip so the hilt shows and the blade trails out of the frame (authored right-handed, from vanilla's resting hand)
    private static final org.joml.Vector3f HIP_D = new org.joml.Vector3f(-0.26F, 0.05F, 0.06F);
    private static final org.joml.Quaternionf HIP_Q = new org.joml.Quaternionf().rotateZ(104F * Mth.DEG_TO_RAD).rotateX(24F * Mth.DEG_TO_RAD);
    /** Ticks the Katana takes to lower to the hip, and how much brighter its drawn cut's ribbon burns. */
    private static final float HIP_TICKS = 6F, DRAWN_LIGHT = 1.5F;
    private static boolean hipWas;

    /** How far the weapon in hand has lowered to the hip (0..1): a style with a drawn cut, rested as long as the cut asks. */
    private static float hip(LocalPlayer p, Archetypes.@Nullable Loaded inHand, float partial) {
        if (inHand == null || !ArmsComfort.weaponDraw()) return 0F;
        int idle = inHand.def().traits().drawIdle();
        float rested = idle <= 0 ? -1F : ArmsPredict.restedTicks() + partial - idle;
        boolean down = rested >= 0F && equip <= 0F;
        if (down && !hipWas) ArmsAudio.sheath(p);
        hipWas = down;
        if (!down) return 0F;
        float u = Math.min(1F, rested / HIP_TICKS);
        return u * u * (3F - 2F * u);
    }

    /** Whether the weapon in hand rests at the hip now (its next first strike is the drawn cut). */
    static boolean atHip() {
        return hipWas;
    }

    /** A world left: nothing is carried into the next. */
    static void reset() {
        drawKnown = drawPending = hipWas = guardWas = false;
        drawItem = null;
        drawSlot = -1;
        drawAt = bounceAt = -1000;
        guardNow = guardFrom = 0F;
        shakeHold = Long.MIN_VALUE;
        STOPS.clear();
        MAIN_B.reset();
        OFF_B.reset();
    }

    /** Ticks since a move of the hands' clock (hit-stop holds pause it, the tick rate scales it). */
    private static double handsNow() {
        return (ArmsFeel.nanos() - ArmsFeel.paused()) / (double) ArmsFeel.TICK_NANOS; // from the machine's boot: too big for a float (SwingBlend)
    }

    // each hand carried across a change of its source; what was shown last; the new source's motion (VD blocks a tick,
    // VW radians a tick about an axis), sampled just ahead
    private static final SwingBlend MAIN_B = new SwingBlend(), OFF_B = new SwingBlend();
    private static final float REST_BLEND = 3F;
    private static Archetype.@Nullable Move shownMove;
    private static long shownNanos;
    private static boolean shownCutting;
    private static final org.joml.Vector3f VD = new org.joml.Vector3f(), VW = new org.joml.Vector3f(), ZERO = new org.joml.Vector3f(),
            DA = new org.joml.Vector3f(), IDLE_D = new org.joml.Vector3f(), REST_D = new org.joml.Vector3f(), OFF_REST_D = new org.joml.Vector3f();
    private static final org.joml.Quaternionf IDLE_Q = new org.joml.Quaternionf(), REST_Q = new org.joml.Quaternionf(), OFF_REST_Q = new org.joml.Quaternionf(),
            STANCE_Q = new org.joml.Quaternionf();
    private static final org.joml.Quaternionf ID = new org.joml.Quaternionf(), QA = new org.joml.Quaternionf(), QC = new org.joml.Quaternionf();

    /** The new source's motion at {@code t} into VD, VW (zero for a held pose), its clock running at {@code rate}. */
    private static void sourceMotion(FpSwings.@Nullable Style st, Archetype.@Nullable Move m, float t, boolean ours, boolean moving, float rate) {
        VD.zero();
        VW.zero();
        if (!moving || m == null) return;
        float h = 0.1F;
        if (!FpSwings.sample(st, m, t + h, ours, DA, QA)) return;
        VD.set(DA).sub(D).mul(rate / h);
        Q.conjugate(QC);
        QA.mul(QC, QA); // the turn over h
        if (QA.w < 0) QA.set(-QA.x, -QA.y, -QA.z, -QA.w);
        float sn = (float) Math.sqrt(Math.max(0F, 1F - QA.w * QA.w));
        if (sn > 1e-6F) VW.set(QA.x / sn, QA.y / sn, QA.z / sn).mul(2F * (float) Math.acos(Math.min(1F, QA.w)) * rate / h);
    }

    // each hand's move this frame (authored right-handed, before mirroring): where its grip is
    private static final org.joml.Vector3f MAIN_D = new org.joml.Vector3f(), OFF_D = new org.joml.Vector3f(), G = new org.joml.Vector3f();
    private static final org.joml.Quaternionf MAIN_Q = new org.joml.Quaternionf(), OFF_Q = new org.joml.Quaternionf(),
            GUARD = new org.joml.Quaternionf();
    /** The main item's equip rise this frame (vanilla's swap: the item sinks and comes back up), from its hand transform. */
    static float equip;

    /**
     * Whether a style is worn rather than held (Euan 2026-11-09, the Gauntlets: "as if it was like your entire hand, basically
     * like a glove"): no arm rises to it, because the weapon's own sleeve is the arm and a bare fist showed round it.
     */
    static boolean worn(Archetypes.@Nullable Loaded style) {
        return style != null && "gauntlets".equals(style.id());
    }

    /**
     * The arms that hold the weapon (FirstPerson.View's grips; Euan 2026-10-26: "it feels like the weapon is just floating
     * and attacking on its own"): each hand's arm rises from the frame's corner to where it closes on the weapon, at
     * rest, through every swing's turn and the guard, the off hand's on the twin of a pair. A vanilla item's hand is on
     * its sprite's handle.
     */
    private static void grips(LocalPlayer p, FirstPerson.View v, float partial) {
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        FpSwings.Style st = style == null ? null : FpSwings.style(style.id());
        if (st == null || worn(style)) return;
        boolean ours = ArmsItems.of(p.getMainHandItem().getItem()) != null;
        int inv = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float g = guard(p, partial), j = g > 0 ? jolt(partial) * g : 0;
        if (g > 0) guardMove(st, ours, g, j, inv, GD, GUARD, false);
        onMain(G.set(ours ? st.hand() : st.vanillaGrip()), g);
        FirstPerson.View.fist(v.gripMain, 1, inv * G.x, G.y, G.z);
        org.joml.Vector3f both = ours ? st.offHand() : st.vanillaOffHand();
        if (!ArmsOffHand.shownTwin(p).isEmpty()) {
            G.set(st.hand());
            if (g > 0) { // the twin up in guard too, mirrored: the pair crossed (a shield up on its own guard)
                guardMove(st, ours, g, j, inv, GD_OFF, GUARD_OFF, true);
                GUARD_OFF.transform(G);
                G.add(GD_OFF);
            }
            OFF_Q.transform(G);
            G.add(OFF_D).add(HX, HY + ArmsOffHand.equip() * -0.6F, HZ); // the twin rises with the main hand
            FirstPerson.View.fist(v.gripOff, 1, -inv * G.x, G.y, G.z);
        } else if (both != null && p.getOffhandItem().isEmpty()) {
            // a two-handed weapon with the off hand free: the off hand closes on the handle below the main, through
            // every swing and the guard (SPEC §25 "Swing Studio": the katana, greatblade, maul, spear, glaive, scythe
            // and war-staff; the twin blades are twirled in one hand); a stance's own off fist still takes the hand (Hands' hideOff)
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

    /** Whether the off hand's twin strikes in the move on the hands now, alone or with the main. */
    static boolean twinStrikes() {
        var style = ArmsPredict.style;
        FpSwings.Style st = style == null ? null : FpSwings.style(style.id());
        return st != null && st.off() == null && ArmsPredict.move != null && FpSwings.hand(st, ArmsPredict.move) != FpSwings.Hand.MAIN; // a shield lays no ribbon
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
