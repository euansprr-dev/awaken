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

    private static AscentState was = AscentState.NONE;
    private static int since;

    // ---- the moments ------------------------------------------------------------------------------------------------

    static void fired() {
        FirstPerson.play("fp_rope_fire");
    }

    static void released() {
        FirstPerson.play("fp_rope_release");
    }

    static void launched() {
        FirstPerson.play("fp_rope_launch");
    }

    static void wingsOpen() {
        FirstPerson.play("fp_wings_open");
    }

    /** A wing beat in a glide or flight (and the beat that takes flight). */
    static void beat() {
        FirstPerson.play("fp_wing_beat");
    }

    static void doubleJump() {
        FirstPerson.play("fp_double_jump");
    }

    static void dash() {
        FirstPerson.play("fp_air_dash");
    }

    /** A dive slam (or the Sovereign's Judgement Fall) begins: the fists go up. */
    static void dive() {
        FirstPerson.play("fp_dive");
    }

    static void slamLand() {
        FirstPerson.stop("fp_dive");
        FirstPerson.play("fp_slam_land");
    }

    static void roll() {
        FirstPerson.play("fp_barrel_roll");
    }

    static void boom() {
        FirstPerson.play("fp_boom");
    }

    static void ascend() {
        FirstPerson.play("fp_sovereign_ascend");
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
