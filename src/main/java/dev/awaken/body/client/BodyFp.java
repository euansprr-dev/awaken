package dev.awaken.body.client;

import dev.awaken.anims.Ease;
import dev.awaken.body.BodyClips;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The Runner's Kit in first person (Hands P2; SPEC §24): every move of the body as your own eyes see it, in the
 * language of its third-person clip and started from the same event (a move starts its 3P clip through BodyAnims, which
 * hands the name here). One-shots are view clips (tools/art/specs/fp_moves.py); the held moves are stances, phase-locked
 * to the move: the tactical sprint's pump, the slide's brace with a hand trailing, a wall-run's hand brushing the wall,
 * a wall climb's hands slapping up it, a hang's fists on the lip (shimmying hand over hand). Looking down in a slide or a
 * hang shows your legs (out ahead along the ground; feet to the wall).
 */
final class BodyFp {
    private BodyFp() {}

    /** Both fists on a lip in a hang (fp_moves.py HANG_FIST: keep the same numbers), right hand's; the left mirrors x. */
    static final float HANG_X = 0.26F, HANG_Y = -0.02F, HANG_Z = -0.82F;
    /** Falls under this (blocks a tick down) land with the soft dip; over it, a hand goes to the ground. */
    static final double HARD_LANDING = 0.95;

    private static int stairFloor = Integer.MIN_VALUE;

    // ---- the one-shots ----------------------------------------------------------------------------------------------

    /** Our own body started a clip (third person): play its first-person counterpart. */
    static void clip(String clip) {
        String fp = switch (clip) {
            case BodyClips.ROLL -> BodyClips.FP_ROLL;
            case BodyClips.MANTLE -> BodyClips.FP_MANTLE;
            case BodyClips.GRAB -> BodyClips.FP_GRAB;
            case BodyClips.LOWER -> BodyClips.FP_LOWER;
            case BodyClips.LEDGE_UP -> BodyClips.FP_LEDGE_UP;
            case BodyClips.LEDGE_SIDE_L -> BodyClips.FP_LEDGE_SIDE_L;
            case BodyClips.LEDGE_SIDE_R -> BodyClips.FP_LEDGE_SIDE_R;
            case BodyClips.LEDGE_BACK -> BodyClips.FP_LEDGE_BACK;
            case BodyClips.WALLJUMP -> wallLeft() ? BodyClips.FP_WALLJUMP_L : BodyClips.FP_WALLJUMP_R;
            default -> null;
        };
        if (fp == null) return;
        if (fp.equals(BodyClips.FP_ROLL)) {
            FirstPerson.stop(BodyClips.FP_LAND_SOFT);
            FirstPerson.stop(BodyClips.FP_LAND_HARD);
        }
        FirstPerson.play(fp);
    }

    /** The wall the run that just ended was on, as you faced it then. */
    private static boolean wallLeft() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && BodyClips.wallOnLeft(p.getYRot(), RunnerMoves.runAxis, RunnerMoves.runSide);
    }

    /** A landing from a fall (blocks a tick down): a dip, or a hand to the ground; never on top of a roll. */
    static void landed(double speed) {
        if (FirstPerson.playing(BodyClips.FP_ROLL)) return;
        ClientMoves.trace(String.format("first person: landing at %.2f (%s)", speed, speed > HARD_LANDING ? "hard" : "soft"));
        FirstPerson.play(speed > HARD_LANDING ? BodyClips.FP_LAND_HARD : BodyClips.FP_LAND_SOFT);
    }

    static void vault() {
        FirstPerson.play(BodyClips.FP_VAULT);
    }

    static void leap() {
        FirstPerson.play(BodyClips.FP_LEAP);
    }

    static void slideJump() {
        FirstPerson.play(BodyClips.FP_SLIDE_JUMP);
    }

    /** Each tick of a stair slide: the trailing hand knocks each step it passes. */
    static void stairTick(LocalPlayer p) {
        int floor = Mth.floor(p.getY() * 2); // half steps: slabs too
        if (stairFloor != Integer.MIN_VALUE && floor < stairFloor) FirstPerson.play(BodyClips.FP_STAIR_BUMP);
        stairFloor = floor;
    }

    static void stairEnd() {
        stairFloor = Integer.MIN_VALUE;
    }

    // ---- the stances ------------------------------------------------------------------------------------------------

    private static float now(float pt) {
        return ClientMoves.ticks + pt;
    }

    /** A first-person stance (core.api.FirstPerson): the held moves, each blended in and out on the body's own curves. */
    static void viewPose(float pt, FirstPerson.View v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        float t = now(pt);
        // tactical sprint: hands lowered, pumping in step (both drawn)
        float a = Ease.hold(t, ClientMoves.tacStart, ClientMoves.tacEnd, ClientMoves.tac, 4F, 4F);
        if (a > 0) {
            float stride = p.avatarState().getBackwardsInterpolatedWalkDistance(pt) * Mth.PI;
            float pump = Mth.sin(stride) * 0.05F;
            FirstPerson.View.add(v.both, a, 0, -0.22F, 0.06F, 16F, 0, 0);
            FirstPerson.View.add(v.main, a, 0, pump, -pump * 0.6F, pump * 40F, 0, 0);
            FirstPerson.View.add(v.off, a, 0, -pump, pump * 0.6F, -pump * 40F, 0, 0);
            v.drawOff = true;
        }
        // the slide: the lead hand braced, the off hand trailing low behind (both drawn); a stair slide the same
        float s = Ease.hold(t, ClientMoves.slideStart, ClientMoves.slideEnd, ClientMoves.sliding, 2.5F, 4F);
        if (s > 0) {
            FirstPerson.View.add(v.both, s, 0.04F, -0.1F, 0.1F, 0, 0, -6F);
            float judder = ClientMoves.stairSliding ? 0.01F * Mth.sin(t * 2.3F) : 0.006F * Mth.sin(t * 3.1F);
            FirstPerson.View.add(v.off, s, -0.12F, -0.2F + judder, 0.16F, -26F, 0, -18F);
            v.drawOff = true;
        }
        // a wall-run: the wall-side hand out at the frame's edge brushing the wall, the other pumping in the stride
        float w = Ease.hold(t, RunnerMoves.runStart, RunnerMoves.runEnd, RunnerMoves.state == RunnerMoves.State.RUN && !RunnerMoves.runClimb, 4F, 6F);
        if (w > 0) {
            boolean left = BodyClips.wallOnLeft(p.getYRot(), RunnerMoves.runAxis, RunnerMoves.runSide);
            float brush = Mth.sin((RunnerMoves.runAge + pt) * Mth.PI / 4F); // a footfall every 4 ticks
            float[] wallHand = left == leftMain(p) ? v.fistMain : v.fistOff;
            FirstPerson.View.fist(wallHand, w, (left ? -1 : 1) * (0.84F + 0.02F * brush), -0.22F + 0.03F * brush, -0.78F);
            float pump = 0.05F * brush;
            FirstPerson.View.add(left == leftMain(p) ? v.off : v.main, w, 0, pump, -pump, pump * 40F, 0, 0);
        }
        // a wall climb (Wall Stride): hands slapping up the face in turn
        float c = Ease.hold(t, RunnerMoves.runStart, RunnerMoves.runEnd, RunnerMoves.state == RunnerMoves.State.RUN && RunnerMoves.runClimb, 2F, 4F);
        float m = leftMain(p) ? -1 : 1; // the main hand's side of the frame
        if (c > 0) {
            float phase = Mth.sin((RunnerMoves.runAge + pt) * Mth.PI / 4F);
            FirstPerson.View.fist(v.fistMain, c, m * 0.26F, -0.1F + 0.2F * phase, -0.84F);
            FirstPerson.View.fist(v.fistOff, c, -m * 0.26F, -0.1F - 0.2F * phase, -0.84F);
        }
        // a hang: both fists on the lip (a slow sway); shimmying, hand over hand toward the way you go
        float h = Ease.hold(t, RunnerMoves.hangStart, RunnerMoves.hangEnd, RunnerMoves.hanging(), 3F, 4F);
        if (h > 0) {
            float sway = 0.006F * Mth.sin(t * 0.12F);
            float sh = RunnerMoves.shimmying ? Mth.sin(t * Mth.PI / 4F) : 0;
            float dir = RunnerMoves.shimmyDir; // +1 left as you face the wall
            float lead = 0.05F * sh * -dir;
            FirstPerson.View.fist(v.fistMain, h, m * HANG_X + lead, HANG_Y + sway + 0.03F * Math.max(0, sh), HANG_Z);
            FirstPerson.View.fist(v.fistOff, h, -m * HANG_X + lead, HANG_Y + sway + 0.03F * Math.max(0, -sh), HANG_Z);
        }
    }

    private static boolean leftMain(LocalPlayer p) {
        return p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
    }

    // ---- the legs in the world --------------------------------------------------------------------------------------

    /** The world layer's source: your legs out ahead along the ground in a slide, your feet to the wall in a hang. */
    static boolean worldPose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return false;
        float t = now(pt);
        var cam = mc.gameRenderer.mainCamera().position();
        float s = Ease.hold(t, ClientMoves.slideStart, ClientMoves.slideEnd, ClientMoves.sliding, 2.5F, 4F);
        if (s > 0) {
            double fx = ClientMoves.slideX, fz = ClientMoves.slideZ, l = Math.sqrt(fx * fx + fz * fz);
            if (l < 1e-4) return false;
            fx /= l;
            fz /= l;
            double rx = -fz, rz = fx;
            double feet = Mth.lerp(pt, p.yo, p.getY());
            rig.fx = fx;
            rig.fz = fz;
            // the hips low on the ground behind the eyes (a slide's eyes are only 0.4 up); the legs lie along the ground
            // ahead, so looking ahead shows the feet at the frame's foot and looking down the whole leg
            double hipY = feet + 0.1, drop = ClientMoves.stairSliding ? 0.3 : 0.0;
            for (int i = 0; i < 2; i++) {
                double side = i == 0 ? 1 : -1;
                double hx = cam.x - fx * 0.3 + rx * side * 0.12, hz = cam.z - fz * 0.3 + rz * side * 0.12;
                // the lead leg out straight along the ground, the other knee a little up (a shorter reach ahead)
                double ahead = i == 0 ? 0.95 : 0.75, rise = i == 0 ? -0.04 : 0.1;
                rig.reach(i == 0 ? FirstPerson.Limb.RIGHT_LEG : FirstPerson.Limb.LEFT_LEG, hx, hipY, hz, hx + fx * ahead, hipY + rise - drop,
                        hz + fz * ahead, FirstPerson.Rig.LEG_REACH, 0, 1, 0, s);
            }
            return true;
        }
        float h = Ease.hold(t, RunnerMoves.hangStart, RunnerMoves.hangEnd, RunnerMoves.hanging(), 3F, 4F);
        if (h > 0) {
            float yaw = RunnerMoves.wallYaw() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -fz, rz = fx;
            rig.fx = fx;
            rig.fz = fz;
            double hipY = cam.y - 0.87;
            for (int i = 0; i < 2; i++) {
                double side = i == 0 ? 1 : -1;
                double sway = 0.04 * Mth.sin(t * 0.12F + i);
                double hx = cam.x + rx * side * 0.12, hz = cam.z + rz * side * 0.12;
                // the feet against the face below the hands, toes in
                rig.reach(i == 0 ? FirstPerson.Limb.RIGHT_LEG : FirstPerson.Limb.LEFT_LEG, hx, hipY, hz, hx + fx * (0.3 + sway), cam.y - 1.55,
                        hz + fz * (0.3 + sway), FirstPerson.Rig.LEG_REACH, fx, 0, fz, h);
            }
            return true;
        }
        return false;
    }

    static void reset() {
        stairFloor = Integer.MIN_VALUE;
    }
}
