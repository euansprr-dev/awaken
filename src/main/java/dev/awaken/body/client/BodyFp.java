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
 * a wall climb's hands slapping up it, a hang's hands closed on the lip (shimmying hand over hand). In the world (P2.1):
 * a slide's trailing hand flat on the ground beside you, skimming it (knocking each step on stairs), a hard landing's
 * palms slapped onto the ground ahead; looking down in a hang shows your feet to the wall. No legs in a slide: from eyes
 * this low they could only ever be a slab across the frame.
 */
final class BodyFp {
    private BodyFp() {}

    /** Both fists on a lip in a hang (fp_moves.py HANG_FIST: keep the same numbers), right hand's; the left mirrors x. */
    static final float HANG_X = 0.26F, HANG_Y = -0.02F, HANG_Z = -0.82F;
    /** Falls under this (blocks a tick down) land with the soft dip; over it, a hand goes to the ground. */
    static final double HARD_LANDING = 0.95;

    private static int stairFloor = Integer.MIN_VALUE;
    /** A hard landing (palms to the ground): when, where the feet were, the way you faced. */
    private static int landAt = -100;
    private static double landX, landY, landZ, landFx, landFz = 1;
    /** A hard landing's palms, ticks: down to the ground, held, back. */
    static final int LAND_DOWN = 3, LAND_HOLD = 7, LAND_END = 12;
    private static final net.minecraft.core.BlockPos.MutableBlockPos M = new net.minecraft.core.BlockPos.MutableBlockPos();
    /** The landing palms' places (right x y z, left x y z), the slide hand's this frame. */
    private static final double[] LAND = new double[6], SLIDE = new double[3];
    private static final org.joml.Vector3f RAY = new org.joml.Vector3f();

    // ---- the one-shots ----------------------------------------------------------------------------------------------

    /** The speed the next move's view clip plays at (a ledge jump's, to land its catch with the body's path), then 1. */
    static float speed = 1F;

    /** A move's view clip, unless first person's moves are switched off (feel settings: firstPersonMoves). */
    static void play(String clip) {
        float sp = speed;
        speed = 1F;
        if (dev.awaken.body.client.feel.FeelConfig.fpMoves()) FirstPerson.play(clip, sp);
    }

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
        play(fp);
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
        LocalPlayer p = Minecraft.getInstance().player;
        if (speed > HARD_LANDING && p != null && dev.awaken.body.client.feel.FeelConfig.fpMoves()) {
            // the palms slap onto the ground ahead (the world layer), with the camera's own dip
            landAt = ClientMoves.ticks;
            landX = p.getX();
            landY = p.getY();
            landZ = p.getZ();
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            landFx = -Mth.sin(yaw);
            landFz = Mth.cos(yaw);
            // where each palm slaps: the ground under a point low in the frame either side of the middle, so it's seen
            var cam = Minecraft.getInstance().gameRenderer.mainCamera();
            onGround(p, cam, 0.2F, -0.3F, landY, LAND, 0);
            onGround(p, cam, -0.2F, -0.3F, landY, LAND, 3);
            return;
        }
        play(speed > HARD_LANDING ? BodyClips.FP_LAND_HARD : BodyClips.FP_LAND_SOFT);
    }

    static void vault() {
        play(BodyClips.FP_VAULT);
    }

    static void leap() {
        play(BodyClips.FP_LEAP);
    }

    static void slideJump() {
        play(BodyClips.FP_SLIDE_JUMP);
    }

    /** Each tick of a stair slide: the trailing hand knocks each step it passes. */
    static void stairTick(LocalPlayer p) {
        int floor = Mth.floor(p.getY() * 2); // half steps: slabs too
        if (stairFloor != Integer.MIN_VALUE && floor < stairFloor) play(BodyClips.FP_STAIR_BUMP);
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
            // empty hands: fists pumping up into the frame in turn, well in from its corners
            float sw = Mth.sin(stride), mm = leftMain(p) ? -1 : 1;
            // (at z -0.85 the frame shows y down to about -0.6, less with the sprint's wide view: keep them well inside)
            FirstPerson.View.fist(v.fistMain, a, mm * (0.36F - 0.03F * sw), -0.2F + 0.12F * sw, -0.85F - 0.05F * sw);
            FirstPerson.View.fist(v.fistOff, a, -mm * (0.36F + 0.03F * sw), -0.2F - 0.12F * sw, -0.85F + 0.05F * sw);
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
            rig.fx = fx;
            rig.fz = fz;
            rig.handDistance = 0.78;
            // the trailing hand (the off side's: the main hand keeps what it holds) flat on the ground beside you, a
            // little ahead, skimming it: on stairs it finds each step as it passes
            double side = leftMain(p) ? 1 : -1;
            double feet = Mth.lerp(pt, p.yo, p.getY());
            // the ground under a point low in the frame on that side: wherever you look, the hand is seen on the ground
            onGround(p, mc.gameRenderer.mainCamera(), (float) (0.42 * side), -0.34F, feet, SLIDE, 0);
            double hx = SLIDE[0], hz = SLIDE[2];
            double gy = SLIDE[1] + 0.03 + 0.012 * Mth.sin(t * 3.1F) + (ClientMoves.stairSliding ? 0.02 * Math.abs(Mth.sin(t * 1.6F)) : 0);
            double sx = cam.x + rx * side * 0.33 - fx * 0.05, sz = cam.z + rz * side * 0.33 - fz * 0.05;
            FirstPerson.Limb arm = side > 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM;
            rig.reach(arm, sx, cam.y - 0.3, sz, hx, gy, hz, FirstPerson.Rig.ARM_REACH, 0, 1, 0, s);
            return true;
        }
        float land = ClientMoves.ticks - landAt + pt;
        if (land >= 0 && land < LAND_END) {
            // a hard landing: both palms slapped onto the ground ahead, held a beat, then back
            rig.fx = landFx;
            rig.fz = landFz;
            rig.handDistance = 0.78;
            double rx = -landFz, rz = landFx;
            float k = land < LAND_DOWN ? Ease.at(Ease.OUT_CUBIC, land / LAND_DOWN)
                    : land < LAND_HOLD ? 1 : 1 - Ease.at(Ease.IN_OUT_SINE, (land - LAND_HOLD) / (LAND_END - LAND_HOLD));
            for (int i = 0; i < 2; i++) {
                double side = i == 0 ? 1 : -1;
                double gx = LAND[i * 3], gy = LAND[i * 3 + 1], gz = LAND[i * 3 + 2];
                // from under the frame down to the ground
                double y = Mth.lerp(k, cam.y - 0.9, gy + 0.03), x = Mth.lerp(k, cam.x + rx * side * 0.35, gx), z = Mth.lerp(k, cam.z + rz * side * 0.35, gz);
                double sx = cam.x + rx * side * 0.33, sz = cam.z + rz * side * 0.33;
                rig.reach(i == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, sx, cam.y - 0.35, sz, x, y, z, FirstPerson.Rig.ARM_REACH, 0, 1, 0, 1F);
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

    /**
     * Where the eye's ray through view point (vx, vy) (view space at depth 1) meets the ground (about groundY; the step
     * under it on stairs): written to out[at..at+2]. Looking level or up, a point an arm's length along the ray.
     */
    private static void onGround(LocalPlayer p, net.minecraft.client.Camera cam, float vx, float vy, double groundY, double[] out, int at) {
        var eye = cam.position();
        RAY.set(vx, vy, -1).normalize().rotate(cam.rotation());
        double t = RAY.y < -0.08 ? Math.min(2.2, (groundY - eye.y) / RAY.y) : 0.9;
        double x = eye.x + RAY.x * t, z = eye.z + RAY.z * t;
        double gy = ground(p, x, groundY + 0.6, z);
        if (RAY.y < -0.08 && Math.abs(gy - groundY) > 0.01) {
            // a step: meet that instead
            t = Math.min(2.2, (gy - eye.y) / RAY.y);
            x = eye.x + RAY.x * t;
            z = eye.z + RAY.z * t;
        }
        out[at] = x;
        out[at + 1] = RAY.y < -0.08 ? gy : eye.y + RAY.y * t;
        out[at + 2] = z;
    }

    /** The top of what's under (x, z), looking down from y (blocks; two down at most). */
    private static double ground(LocalPlayer p, double x, double y, double z) {
        var level = p.level();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y); by >= Mth.floor(y) - 2; by--) {
            M.set(bx, by, bz);
            var shape = level.getBlockState(M).getCollisionShape(level, M);
            if (!shape.isEmpty()) {
                double top = by + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (top <= y) return top;
            }
        }
        return Mth.floor(y) - 2;
    }

    static void reset() {
        stairFloor = Integer.MIN_VALUE;
        landAt = -100;
    }
}
