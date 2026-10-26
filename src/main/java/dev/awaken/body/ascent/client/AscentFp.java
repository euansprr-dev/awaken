package dev.awaken.body.ascent.client;

import dev.awaken.anims.Ease;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The Ascent in first person (Hands P3; SPEC §17, §24): your hands through every state of the ladder, as fist stances
 * for the held states and view clips (tools/art/specs/fp_moves.py) for the moments, each started where the Ascent's own
 * moment is (the same call that gives the Feel Director its beat).
 * <p>
 * On a rope the main fist holds it where it leaves the hand ({@link #ROPE_X}; AscentRender starts the rope there), the
 * other joining it to pump the swing and climbing over it to reel in; on a zip both hands are on it. Flying free the arms
 * are out for balance; in a dive slam both fists are up for the smash. On wings both arms are out flat at the frame's
 * edges, trembling with the speed; in flight one fist is forward on a boost, both palms down in a hover, both sweeping up
 * to climb. In the Sovereign's form the hands are gold.
 */
public final class AscentFp {
    private AscentFp() {}

    /** Where a rope leaves the main hand, in view space (fp_moves.py ROPE_FIST: keep the same numbers). */
    public static final float ROPE_X = 0.3F, ROPE_Y = -0.02F, ROPE_Z = -0.72F;
    /** The arms out flat on wings (fp_moves.py GLIDE_FIST), the main hand's; the other mirrors x. */
    static final float GLIDE_X = 0.86F, GLIDE_Y = -0.04F, GLIDE_Z = -0.86F;
    /** The fists raised for a dive slam (fp_moves.py SLAM_UP). */
    static final float SLAM_X = 0.2F, SLAM_Y = 0.3F, SLAM_Z = -0.74F;
    /** The Sovereign's gold on the hands. */
    static final int GOLD = 0xFFD98A;
    /**
     * The rappel's brake hand, under the guiding one on the rope (AscentRender runs the rope down to it and its tail out
     * of it), on the main hand's side (x times the side, as ROPE_X).
     */
    static final float BRAKE_X = 0.16F, BRAKE_Y = -0.46F, BRAKE_Z = -0.6F;

    /** A kick off the wall (client clock): the arms take the push for a few ticks. */
    private static int kickAt = -100;
    /** Where the rappel's two fists are this frame, in view space (the rope is drawn through them). */
    static final float[] guideNow = {ROPE_X, ROPE_Y, ROPE_Z}, brakeNow = {BRAKE_X, BRAKE_Y, BRAKE_Z};

    static void kicked() {
        kickAt = AscentMoves.ticks;
    }

    private static AscentState was = AscentState.NONE;
    private static int since;

    // ---- the moments ------------------------------------------------------------------------------------------------

    /** A moment's view clip, unless first person's moves are switched off (feel settings: firstPersonMoves). */
    private static void play(String clip) {
        if (dev.awaken.body.client.feel.FeelConfig.fpMoves()) FirstPerson.play(clip);
    }

    static void fired() {
        play("fp_rope_fire");
    }

    static void released() {
        play("fp_rope_release");
    }

    static void launched() {
        play("fp_rope_launch");
    }

    static void wingsOpen() {
        play("fp_wings_open");
    }

    /** A wing beat in a glide or flight (and the beat that takes flight). */
    static void beat() {
        play("fp_wing_beat");
    }

    static void doubleJump() {
        play("fp_double_jump");
    }

    static void dash() {
        play("fp_air_dash");
    }

    /** A dive slam (or the Sovereign's Judgement Fall) begins: the fists go up. */
    static void dive() {
        play("fp_dive");
    }

    static void slamLand() {
        FirstPerson.stop("fp_dive");
        play("fp_slam_land");
    }

    static void roll() {
        play("fp_barrel_roll");
    }

    static void boom() {
        play("fp_boom");
    }

    static void ascend() {
        play("fp_sovereign_ascend");
    }

    // ---- the stances ------------------------------------------------------------------------------------------------

    static void viewPose(float pt, FirstPerson.View v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        AscentState st = AscentMoves.state();
        if (st != was) {
            was = st;
            since = AscentMoves.ticks;
        }
        float t = AscentMoves.ticks + pt, age = t - since;
        float m = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1 : 1; // the main hand's side
        if (SovereignClient.active()) v.tint = GOLD;
        float in = Ease.at(Ease.SMOOTH, age / 4F);
        switch (st) {
            case TETHER -> {
                FirstPerson.View.fist(v.fistMain, 1, m * ROPE_X, ROPE_Y, ROPE_Z);
                // the other hand joins to pump (forward) and climbs over to reel in (sneak)
                if (AscentMoves.keySneak) {
                    float reel = Mth.sin(t * Mth.PI / 5F);
                    FirstPerson.View.fist(v.fistOff, 1, m * (ROPE_X - 0.12F), ROPE_Y + 0.1F * reel, ROPE_Z + 0.02F);
                } else if (AscentMoves.keyFwd) {
                    FirstPerson.View.fist(v.fistOff, 1, m * (ROPE_X - 0.1F), ROPE_Y - 0.12F, ROPE_Z + 0.03F);
                }
            }
            case ZIP -> {
                FirstPerson.View.fist(v.fistMain, 1, m * ROPE_X, ROPE_Y, ROPE_Z);
                FirstPerson.View.fist(v.fistOff, in, m * (ROPE_X - 0.1F), ROPE_Y - 0.1F, ROPE_Z + 0.02F);
            }
            case RAPPEL -> {
                // the guiding hand up on the rope, the brake hand under it at the hip feeding it through: in bites (the
                // stroke up as it runs, a squeeze at each catch) or, on a Deepglass line, one smooth run
                float k = Math.max(0F, 1F - (t - kickAt) / 7F); // the kick's push through the arms
                float stroke = 0, slide = 0;
                if (AscentMoves.payNow > 0) {
                    if (AscentMoves.smoothLine()) {
                        stroke = 0.03F + 0.006F * Mth.sin(t * 2.3F);
                        slide = 0.015F;
                    } else {
                        float phase = (float) ((AscentMoves.paid % dev.awaken.body.ascent.AscentRules.RAPPEL_BITE) / dev.awaken.body.ascent.AscentRules.RAPPEL_BITE);
                        stroke = 0.05F * Mth.sin(phase * Mth.PI);
                        slide = 0.03F * phase;
                    }
                }
                float squeeze = Math.max(0F, 1F - (t - AscentMoves.catchAt) / 3F) * 0.025F;
                guideNow[0] = m * ROPE_X;
                guideNow[1] = ROPE_Y - slide - k * 0.05F;
                guideNow[2] = ROPE_Z + k * 0.08F;
                brakeNow[0] = m * (BRAKE_X - squeeze);
                brakeNow[1] = BRAKE_Y + stroke - squeeze - k * 0.04F;
                brakeNow[2] = BRAKE_Z + k * 0.06F;
                FirstPerson.View.fist(v.fistMain, 1, guideNow[0], guideNow[1], guideNow[2]);
                FirstPerson.View.fist(v.fistOff, in, brakeNow[0], brakeNow[1], brakeNow[2]);
            }
            case CARRY -> {
                if (AscentMoves.slamming) {
                    FirstPerson.View.fist(v.fistMain, 1, m * SLAM_X, SLAM_Y, SLAM_Z);
                    FirstPerson.View.fist(v.fistOff, 1, -m * SLAM_X, SLAM_Y, SLAM_Z);
                } else {
                    // flying free: arms out low for balance, easing in
                    float sway = 0.02F * Mth.sin(t * 0.3F);
                    FirstPerson.View.fist(v.fistMain, in, m * 0.82F, -0.4F + sway, -0.86F);
                    FirstPerson.View.fist(v.fistOff, in, -m * 0.82F, -0.4F - sway, -0.86F);
                }
            }
            case GLIDE -> {
                double speed = AscentMoves.speed();
                float tremble = (float) (0.004 + 0.006 * Mth.clamp(speed / 2.0, 0, 1)) * Mth.sin(t * 9.1F);
                if (AscentMoves.keySneak) {
                    // folding into the dive: the arms sweep back out of the frame
                    FirstPerson.View.fist(v.fistMain, 1, m * 0.7F, -0.9F, -0.6F);
                    FirstPerson.View.fist(v.fistOff, 1, -m * 0.7F, -0.9F, -0.6F);
                } else {
                    FirstPerson.View.fist(v.fistMain, 1, m * GLIDE_X, GLIDE_Y + tremble, GLIDE_Z);
                    FirstPerson.View.fist(v.fistOff, 1, -m * GLIDE_X, GLIDE_Y - tremble, GLIDE_Z);
                }
            }
            case SOAR -> {
                float breath = 0.012F * Mth.sin(t * 0.18F);
                if (AscentMoves.keySprint && AscentMoves.keyFwd) {
                    // the boost: the main fist forward through the air, the other back out of the frame
                    FirstPerson.View.fist(v.fistMain, 1, m * 0.14F, -0.08F, -1.05F);
                    FirstPerson.View.fist(v.fistOff, 1, -m * 0.6F, -1.1F, -0.8F);
                } else if (AscentMoves.keyJump) {
                    // climbing: the arms sweep up
                    FirstPerson.View.fist(v.fistMain, 1, m * 0.6F, -0.08F, -0.9F);
                    FirstPerson.View.fist(v.fistOff, 1, -m * 0.6F, -0.08F, -0.9F);
                } else if (!AscentMoves.keyFwd) {
                    // hovering: palms down in front, breathing
                    FirstPerson.View.fist(v.fistMain, 1, m * 0.34F, -0.48F + breath, -0.86F);
                    FirstPerson.View.fist(v.fistOff, 1, -m * 0.34F, -0.48F + breath, -0.86F);
                } else {
                    // cruising: the arms swept back low at the edges
                    FirstPerson.View.fist(v.fistMain, 1, m * 0.84F, -0.46F + breath, -0.9F);
                    FirstPerson.View.fist(v.fistOff, 1, -m * 0.84F, -0.46F - breath, -0.9F);
                }
            }
            default -> {
            }
        }
    }
}
