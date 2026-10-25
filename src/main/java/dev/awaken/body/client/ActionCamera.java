package dev.awaken.body.client;

import dev.awaken.body.ActionRig;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.ascent.client.AscentMoves;
import dev.awaken.body.client.feel.FeelConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;

/**
 * The action camera (SPEC §17.13): the over-the-shoulder view follows what the body is doing. Once a tick it reads
 * the body (the Ascent's state, a Runner move, a slide, the speed) as an {@link ActionRig.Pose} and springs its arm
 * toward that pose's shape; each frame the view's offset is shaped by the arm between the last two ticks. With
 * Shoulder Surfing it shapes that mod's own offset (feel.ShoulderSurfingPlugin, the only class that names it);
 * without it, vanilla's third person (behind) gets the arm's pull-back as distance. First person is untouched. It
 * stands aside (the arm comes home) through the opening's shots, as a spirit, riding, spectating, or switched off.
 */
public final class ActionCamera {
    private ActionCamera() {}

    private static final ActionRig.Arm ARM = new ActionRig.Arm();
    private static final double[] TARGET = new double[ActionRig.N], SHAPE = new double[3];
    private static boolean wallRight;
    private static ActionRig.Pose pose = ActionRig.Pose.GROUND;

    /** Client tick: the pose now and a step of the arm toward it. */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        ActionRig.Pose now = standAside(mc, p) ? null : read(p);
        if (now == null) {
            for (int i = 0; i < ActionRig.N; i++) TARGET[i] = 0;
        } else {
            double speed = AscentMoves.state() != AscentState.NONE ? AscentMoves.speed()
                    : Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
            ActionRig.target(now, speed, TARGET);
            if (FeelConfig.reduced()) for (int i = 0; i < ActionRig.N; i++) TARGET[i] *= 0.5;
            pose = now;
        }
        ARM.step(TARGET);
    }

    private static boolean standAside(Minecraft mc, LocalPlayer p) {
        return !FeelConfig.actionCamera() || p.isPassenger() || p.isSpectator() || dev.awaken.opening.client.OpeningCamera.active()
                || dev.awaken.veil.client.VeilClientState.spirit() || dev.awaken.veil.client.VeilClientState.possessing();
    }

    /** What the body is doing, as the camera sees it. */
    private static ActionRig.Pose read(LocalPlayer p) {
        if (dev.awaken.body.ascent.client.SovereignClient.active()) return ActionRig.Pose.SOVEREIGN;
        switch (AscentMoves.state()) {
            case TETHER -> {
                return ActionRig.Pose.ROPE;
            }
            case ZIP -> {
                return ActionRig.Pose.ZIP;
            }
            case CARRY -> {
                return ActionRig.Pose.CARRY;
            }
            case GLIDE -> {
                return ActionRig.Pose.GLIDE;
            }
            case SOAR -> {
                return ActionRig.Pose.FLIGHT;
            }
            default -> {
            }
        }
        if (RunnerMoves.state == RunnerMoves.State.RUN && !RunnerMoves.runClimb) {
            // the wall's side against where you look: the camera goes to the other side
            double wx = RunnerMoves.runAxis == 0 ? RunnerMoves.runSide : 0, wz = RunnerMoves.runAxis == 2 ? RunnerMoves.runSide : 0;
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            wallRight = wx * -Mth.cos(yaw) + wz * -Mth.sin(yaw) > 0;
            return ActionRig.Pose.WALL_RUN;
        }
        if (RunnerMoves.state != RunnerMoves.State.NONE) return ActionRig.Pose.HANG;
        if (ClientMoves.sliding) return ActionRig.Pose.SLIDE;
        if (p.isUsingItem()) return ActionRig.Pose.AIM;
        return ActionRig.Pose.GROUND;
    }

    /** The shoulder view's offset (x left, y up, z back) shaped by the arm, between the last two ticks. */
    public static Vec3 shoulder(Vec3 base, float partial) {
        ActionRig.shape(base.x, base.y, base.z, ARM.get(ActionRig.BACK, partial), ARM.get(ActionRig.UP, partial),
                ARM.get(ActionRig.OPEN, partial), ARM.get(ActionRig.CENTRE, partial), wallRight, SHAPE);
        // the footsteps: vanilla's own bob (its setting, its stride), which first person has and this view lacked
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.options.bobView().get() && !FeelConfig.reduced()) {
            float w = mc.player.avatarState().getBackwardsInterpolatedWalkDistance(partial), bob = mc.player.avatarState().getInterpolatedBob(partial);
            SHAPE[0] += Mth.sin(w * (float) Math.PI) * bob * BOB_SWAY;
            SHAPE[1] -= Math.abs(Mth.cos(w * (float) Math.PI) * bob) * BOB_DIP;
        }
        return new Vec3(SHAPE[0], SHAPE[1], SHAPE[2]);
    }

    /** The bob's size in the shoulder view against vanilla's first person (0.5 sideways, 1 down). */
    static final float BOB_SWAY = 0.3F, BOB_DIP = 0.6F;

    /** A landing from a fall at {@code speed} (blocks a tick down): the camera drops with the body and springs back. */
    static void land(double speed) {
        if (speed < LAND_FROM || FeelConfig.reduced()) return;
        ARM.kick(ActionRig.UP, -Math.min(0.32, (speed - LAND_FROM) * 0.35 + 0.08));
    }

    /** Falls under this (blocks a tick: about two blocks) land without a dip. */
    static final double LAND_FROM = 0.55;

    /** Without Shoulder Surfing: vanilla's third person behind you pulls back as the arm does. */
    static void onDetached(CalculateDetachedCameraDistanceEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isCameraFlipped() || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        e.setDistance((float) (e.getDistance() + ARM.get(ActionRig.BACK, pt) + 0.5 * Math.max(0, ARM.get(ActionRig.UP, pt))));
    }

    /** The pose the camera last read (dev command, tours). */
    public static ActionRig.Pose pose() {
        return pose;
    }

    static void reset() {
        ARM.reset();
        pose = ActionRig.Pose.GROUND;
    }
}
