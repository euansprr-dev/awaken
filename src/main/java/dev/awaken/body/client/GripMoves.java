package dev.awaken.body.client;

import dev.awaken.body.BodyClips;
import dev.awaken.body.BodyContent;
import dev.awaken.body.GripRules;
import dev.awaken.body.Move;
import dev.awaken.body.RunnerRules;
import dev.awaken.body.net.BodyPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * The grip on a climb line, predicted here (SPEC §17.23; Euan: "it just looks like the boring vanilla ladder ... you
 * just float up"). A Runner state ({@link RunnerMoves.State#GRIP}): the body holds a drainpipe, trellis, rope or ladder
 * from in front of it and moves only as the grip says. No keys holds still; forward climbs hand over hand (the free-run's
 * pace with it held); Shift held slides down, faster and faster, and letting go brakes; Jump with back kicks off the
 * wall, with left or right swings out to the side, alone lunges up the line; the foot of the line lands you. The grip
 * moves the body itself ({@code move}), so vanilla's ladder cap (0.15 down) doesn't hold the slide back, on any line.
 * <p>
 * What the hands and feet do is kept here for the first-person view ({@link GripView}) and the clips.
 */
public final class GripMoves {
    private GripMoves() {}

    public static final int HOLD = 0, CLIMB = 1, SLIDE = 2, BRAKE = 3, HOP = 4;

    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    private static final double[] ACC = new double[2];

    /** What the grip is doing (HOLD..HOP). */
    static int mode = HOLD;
    /** Vertical speed this tick, blocks a tick (+ up). */
    static double v;
    /** At a top with nothing to take over it: holding still while forward is held (no climb, no jitter). */
    private static boolean capped;
    /** The line: where the body holds it, which way it faces, its column and centre. */
    static GripRules.@Nullable Line line;
    /** Distance climbed or slid since the grip began (the hands' cycle runs on it). */
    static double travelled;
    static int since = -100, endedAt = -100, modeSince;
    private static int brakeLeft, hopAge, hopReadyAt, regrabAt;
    private static double brakeFrom;
    /** The column just left: not taken again until the body has been away from it (a kick, a top-out, a swing). */
    private static int leftBx = Integer.MIN_VALUE, leftBz;
    /** A swing out to the side catches the next line without forward held. */
    private static int swingUntil = -100;
    private static boolean fast;

    static boolean active() {
        return RunnerMoves.state == RunnerMoves.State.GRIP;
    }

    /** For the views: the grip began this many ticks ago (or a large number). */
    public static int age() {
        return active() ? ClientMoves.ticks - since : 10_000;
    }

    // ---- taking a line ----------------------------------------------------------------------------------------------

    /**
     * Into a line: on the ground walking into it, or in the air with forward (or the free-run) held, facing it. True when
     * the grip began (it owns the tick).
     */
    static boolean tryStart(LocalPlayer p, ClientInput in, Input k, BlockGetter level, boolean ground, int now) {
        if (now < regrabAt || p.isUsingItem() || Minecraft.getInstance().gui.screen() != null || p.isInLiquid()) return false;
        if (k.shift() && ground) return false;
        GripRules.Line l = GripRules.find(level, M, p.getX(), p.getY(), p.getZ(), p.getYRot());
        if (l == null) return false;
        if (l.bx() == leftBx && l.bz() == leftBz) return false; // the line just left
        if (!GripRules.facing(l, p.getYRot())) return false;
        boolean intent = ground ? k.forward() && (p.horizontalCollision || l.free()) : k.forward() || FreeRun.held || now <= swingUntil;
        if (!intent) return false;
        start(p, l, k, now);
        tick(p, in, k, false, level, now);
        return true;
    }

    private static void start(LocalPlayer p, GripRules.Line l, Input k, int now) {
        RunnerMoves.state = RunnerMoves.State.GRIP;
        line = l;
        since = modeSince = now;
        travelled = 0;
        brakeLeft = hopAge = 0;
        swingUntil = -100;
        // a catch from a fall keeps a little of it, as a short slide that brakes into the hold
        double vy = p.getDeltaMovement().y;
        v = vy < -0.2 ? Math.max(-GripRules.SLIDE_MAX, vy) : 0;
        fast = FreeRun.held;
        mode = v < 0 ? BRAKE : k.forward() ? CLIMB : HOLD;
        if (mode == BRAKE) {
            brakeLeft = GripRules.BRAKE_TICKS;
            brakeFrom = v;
        }
        int flag = mode == CLIMB ? fast ? BodyPayloads.GRIP_CLIMB_FAST : BodyPayloads.GRIP_CLIMB : BodyPayloads.GRIP_HOLD;
        RunnerMoves.send(Move.GRIP, flag, p, l.x(), p.getY(), l.z(), axis(l), sign(l));
        ClientMoves.trace(String.format("grip (%s) at (%.2f, %.2f) facing (%.0f, %.0f)", l.free() ? "rope" : "line", l.x(), l.z(), l.fx(), l.fz()));
        BodyAnims.runner(p.getId(), clip(mode));
        BodyFx.sound(p, BodyContent.GRAB.get(), 0.55F, 1.15F);
        dev.awaken.body.client.feel.FeelDirector.bite(vy < -0.3 ? 1.6F : 0.8F);
    }

    private static int axis(GripRules.Line l) {
        return Math.abs(l.fx()) >= Math.abs(l.fz()) ? 0 : 2;
    }

    private static int sign(GripRules.Line l) {
        return (axis(l) == 0 ? l.fx() : l.fz()) > 0 ? 1 : -1;
    }

    // ---- each tick --------------------------------------------------------------------------------------------------

    static void tick(LocalPlayer p, ClientInput in, Input k, boolean jumpPressed, BlockGetter level, int now) {
        GripRules.Line l = line;
        if (l == null) {
            end(p, true, "no line");
            return;
        }
        // jump and shift belong to the grip: no vanilla hop, no crouch
        in.keyPresses = ClientMoves.with(k, false, false);
        if (!GripRules.holds(level, M, l.bx(), l.bz(), p.getY())) {
            end(p, true, "line ends");
            return;
        }
        // a screen open holds the grip still, as a hang holds
        boolean screen = Minecraft.getInstance().gui.screen() != null;
        if (!screen && jumpPressed && leave(p, k, l, now)) return;
        int was = mode;
        boolean fwd = !screen && k.forward(), shift = !screen && k.shift();
        if (mode == HOP) {
            if (++hopAge > GripRules.HOP_TICKS) mode = fwd ? CLIMB : HOLD;
        } else if (shift) {
            mode = SLIDE;
        } else if (mode == SLIDE) {
            mode = BRAKE;
            brakeLeft = GripRules.BRAKE_TICKS;
            brakeFrom = v;
            BodyFx.sound(p, BodyContent.PIPE_BRAKE.get(), 0.6F, 0.95F + p.getRandom().nextFloat() * 0.1F);
            dev.awaken.body.client.feel.FeelDirector.bite(0.6F + (float) (-brakeFrom / GripRules.SLIDE_MAX));
        } else if (mode == BRAKE) {
            if (--brakeLeft <= 0) mode = fwd ? CLIMB : HOLD;
        } else {
            mode = fwd && !capped ? CLIMB : HOLD;
        }
        if (!fwd || shift) capped = false;
        boolean free = FreeRun.held;
        switch (mode) {
            case HOP -> v = GripRules.hopStep(hopAge);
            case SLIDE -> v = -GripRules.slide(-v);
            case BRAKE -> v = brakeFrom * brakeLeft / GripRules.BRAKE_TICKS;
            case CLIMB -> v += ((free ? GripRules.CLIMB_FREE : GripRules.CLIMB) - v) * 0.6;
            default -> v = Math.abs(v) < 0.005 ? 0 : v * 0.4;
        }
        // the top: the hands find no more line above, or the head meets what's over it (a cornice, an eave). It always
        // leads somewhere (Euan, 2026-10-25: "the last block doesn't let me through"): a lip over it is a hang, from its
        // outer face (out, then up), and the hang climbs out onto the roof; open air over it hands the body to
        // vanilla's own climb, up to the eave where the hang's catch takes the lip; under something with neither, the
        // hands hold still at the top
        boolean headBlocked = v > 0 && !p.level().noCollision(p, p.getBoundingBox().move(0, v, 0));
        if (v > 0 && (headBlocked || !GripRules.goesOn(level, M, l.bx(), l.bz(), p.getY() + v))) {
            if (fwd) {
                RunnerRules.Edge lip = GripRules.topLip(level, CollisionContext.of(p), M, p.getX(), p.getY(), p.getZ(), axis(l), sign(l), 0);
                if (lip != null) {
                    end(p, true, "eave");
                    RunnerMoves.eaveHang(p, lip, now);
                    return;
                }
                if (!headBlocked) {
                    end(p, true, "top");
                    p.setDeltaMovement(p.getDeltaMovement().x, free ? GripRules.CLIMB_FREE : GripRules.CLIMB, p.getDeltaMovement().z);
                    return;
                }
                if (!capped) ClientMoves.trace("grip capped: nothing over the top to take");
                capped = true;
            }
            v = 0;
            if (mode != HOP) mode = HOLD;
        }
        if (mode != was || mode == CLIMB && free != fast) {
            fast = free;
            modeSince = now;
            changed(p, l);
        }
        // the body to the line (gently: at most a ladder's sideways step), then up or down by the grip's own move
        double dx = Mth.clamp(l.x() - p.getX(), -0.15, 0.15), dz = Mth.clamp(l.z() - p.getZ(), -0.15, 0.15);
        RunnerMoves.inputAccel(p, in, ACC);
        p.setDeltaMovement(dx - ACC[0], 0, dz - ACC[1]);
        if (v != 0) {
            p.move(MoverType.SELF, new Vec3(0, v, 0));
            travelled += Math.abs(v);
        }
        p.resetFallDistance();
        if (v < 0 && p.verticalCollisionBelow) {
            land(p, l, now);
            return;
        }
        // standing at the line's foot without climbing: stepped off (the grip's own move leaves onGround unset)
        if (mode == HOLD && now - since > 4 && !p.level().noCollision(p, p.getBoundingBox().move(0, -0.06, 0))) {
            end(p, true, "stepped off");
            return;
        }
        feel(p, l, now);
    }

    /** What the hands do changed: others see its clip. */
    private static void changed(LocalPlayer p, GripRules.Line l) {
        int flag = switch (mode) {
            case CLIMB -> fast ? BodyPayloads.GRIP_CLIMB_FAST : BodyPayloads.GRIP_CLIMB;
            case SLIDE -> BodyPayloads.GRIP_SLIDE;
            case HOP -> BodyPayloads.GRIP_HOP;
            default -> BodyPayloads.GRIP_HOLD;
        };
        // a brake is the hold to watchers (the clip blends into it)
        RunnerMoves.send(Move.GRIP, flag, p, l.x(), p.getY(), l.z(), axis(l), sign(l));
        BodyAnims.runner(p.getId(), clip(mode));
        ClientMoves.trace("grip " + name(mode));
    }

    static String clip(int mode) {
        return switch (mode) {
            case CLIMB -> fast ? BodyClips.PIPE_CLIMB_FAST : BodyClips.PIPE_CLIMB;
            case SLIDE -> BodyClips.PIPE_SLIDE;
            case HOP -> BodyClips.PIPE_HOP;
            default -> BodyClips.PIPE_HOLD;
        };
    }

    private static String name(int mode) {
        return switch (mode) {
            case CLIMB -> "climb";
            case SLIDE -> "slide";
            case BRAKE -> "brake";
            case HOP -> "hop";
            default -> "hold";
        };
    }

    /** Jump: with back, kick off the wall; with left or right, swing out; alone, lunge up the line. True when it left. */
    private static boolean leave(LocalPlayer p, Input k, GripRules.Line l, int now) {
        if (k.backward()) {
            double nx = -l.fx(), nz = -l.fz();
            leaving(p, l, BodyPayloads.GRIP_KICK, BodyClips.PIPE_KICK, now);
            p.setDeltaMovement(nx * GripRules.KICK_OUT, GripRules.KICK_UP, nz * GripRules.KICK_OUT);
            RunnerMoves.turnTo(RunnerMoves.yawOf(nx, nz), 5);
            ClientMoves.suppressJump();
            BodyFx.sound(p, BodyContent.WALLJUMP.get(), 0.75F, 1.05F);
            BodyFp.play(BodyClips.FP_PIPE_KICK);
            ClientMoves.trace("grip: kick off");
            return true;
        }
        if (k.left() != k.right()) {
            int dir = k.left() ? 1 : -1;
            // left as you face the line is +1 (the shimmy's way)
            double tx = l.fz() * dir, tz = -l.fx() * dir;
            leaving(p, l, dir > 0 ? BodyPayloads.GRIP_SIDE_L : BodyPayloads.GRIP_SIDE_R, dir > 0 ? BodyClips.PIPE_SIDE_L : BodyClips.PIPE_SIDE_R, now);
            p.setDeltaMovement(tx * GripRules.SIDE_OUT - l.fx() * GripRules.SIDE_AWAY, GripRules.SIDE_UP, tz * GripRules.SIDE_OUT - l.fz() * GripRules.SIDE_AWAY);
            swingUntil = now + 14;
            ClientMoves.suppressJump();
            BodyFx.sound(p, BodyContent.SHIMMY.get(), 0.6F, 0.85F);
            BodyFp.play(dir > 0 ? BodyClips.FP_PIPE_SIDE_L : BodyClips.FP_PIPE_SIDE_R);
            ClientMoves.trace("grip: swing " + (dir > 0 ? "left" : "right"));
            return true;
        }
        if (now >= hopReadyAt && GripRules.goesOn(level(p), M, l.bx(), l.bz(), p.getY() + GripRules.HOP * 0.5)) {
            mode = HOP;
            hopAge = 1;
            hopReadyAt = now + GripRules.HOP_EVERY;
            modeSince = now;
            changed(p, l);
            BodyFx.sound(p, BodyContent.GRAB.get(), 0.6F, 1.25F);
            dev.awaken.body.client.feel.FeelDirector.bite(-0.9F);
        }
        return false;
    }

    private static BlockGetter level(LocalPlayer p) {
        return p.level();
    }

    /** The hands leave the line for a jump (the Run with the way tells the server the grip is over). */
    private static void leaving(LocalPlayer p, GripRules.Line l, int flag, String clip, int now) {
        RunnerMoves.send(Move.GRIP, flag, p, l.x(), p.getY(), l.z(), axis(l), sign(l));
        BodyAnims.runner(p.getId(), clip);
        leftBx = l.bx();
        leftBz = l.bz();
        regrabAt = now + 3;
        finish(now);
    }

    private static void land(LocalPlayer p, GripRules.Line l, int now) {
        RunnerMoves.send(Move.GRIP, BodyPayloads.GRIP_LAND, p, l.x(), p.getY(), l.z(), axis(l), sign(l));
        BodyAnims.runner(p.getId(), BodyClips.PIPE_LAND);
        BodyFx.sound(p, BodyContent.DROP.get(), 0.55F, 0.8F);
        BodyFp.play(BodyClips.FP_PIPE_LAND);
        dev.awaken.body.client.feel.FeelDirector.land((float) (-v * 0.9));
        ClientMoves.trace(String.format("grip: landed from a slide at %.2f", -v));
        leftBx = l.bx();
        leftBz = l.bz();
        regrabAt = now + 6;
        finish(now);
    }

    /** The grip is over here (the caller told the server, or the server ended it). */
    private static void finish(int now) {
        RunnerMoves.state = RunnerMoves.State.NONE;
        capped = false;
        endedAt = now;
        line = null;
        mode = HOLD;
        v = 0;
    }

    /** Why the last grip ended ("top": the line ran out under the hands as they climbed). */
    static String lastEnd = "";

    /** Lets go: the line ended, a stop, the server's word. */
    static void end(LocalPlayer p, boolean tellServer, String why) {
        if (!active()) return;
        GripRules.Line l = line;
        if (l != null) {
            leftBx = l.bx();
            leftBz = l.bz();
        }
        ClientMoves.trace("grip ends (" + why + ") after " + (ClientMoves.ticks - since));
        lastEnd = why;
        BodyAnims.runnerEnd(p.getId());
        if (tellServer) RunnerMoves.sendEnd(Move.GRIP);
        regrabAt = ClientMoves.ticks + (why.equals("top") ? 0 : 4);
        finish(ClientMoves.ticks);
    }

    /** Every tick, grip or not: forget the line just left once the body is away from it or down. */
    static void forget(LocalPlayer p) {
        if (leftBx == Integer.MIN_VALUE || active()) return;
        int bx = Mth.floor(p.getX()), bz = Mth.floor(p.getZ());
        if (p.onGround() || bx != leftBx || bz != leftBz) leftBx = Integer.MIN_VALUE;
    }

    static void reset() {
        if (active()) RunnerMoves.state = RunnerMoves.State.NONE;
        line = null;
        mode = HOLD;
        v = 0;
        leftBx = Integer.MIN_VALUE;
        regrabAt = 0;
        swingUntil = -100;
    }

    static float yaw() {
        GripRules.Line l = line;
        return l == null ? 0 : l.yaw();
    }

    // ---- feel -------------------------------------------------------------------------------------------------------

    /** The slide's hiss and grit off the hands; a sound every few pulls of a climb. */
    private static void feel(LocalPlayer p, GripRules.Line l, int now) {
        if (!(p.level() instanceof ClientLevel level)) return;
        int age = now - modeSince;
        if (mode == SLIDE) {
            float speed = (float) (-v / GripRules.SLIDE_MAX);
            if (age % 8 == 0) BodyFx.sound(p, BodyContent.PIPE_SLIDE.get(), 0.35F + 0.35F * speed, 0.85F + 0.3F * speed);
            if (age % 2 == 0) grit(level, p, l, speed);
        } else if (mode == CLIMB && age % (fast ? 4 : 6) == 0) {
            BodyFx.sound(p, BodyContent.GRAB.get(), 0.22F, 1.15F + p.getRandom().nextFloat() * 0.2F);
        }
    }

    /** The line's own grit off the sliding hands; sparks off a metal line at speed. */
    private static void grit(ClientLevel level, LocalPlayer p, GripRules.Line l, float speed) {
        RandomSource r = level.getRandom();
        double hx = l.cx() - l.fx() * (l.radius() + 0.05), hz = l.cz() - l.fz() * (l.radius() + 0.05), hy = p.getY() + 1.45;
        M.set(l.bx(), Mth.floor(hy), l.bz());
        BlockState s = level.getBlockState(M);
        int col = s.isAir() ? 0x8A8378 : s.getMapColor(level, M).col;
        int tint = (((col >> 16) & 255) * 7 / 10 + 0x8E * 3 / 10) << 16 | (((col >> 8) & 255) * 7 / 10 + 0x88 * 3 / 10) << 8 | ((col & 255) * 7 / 10 + 0x7C * 3 / 10);
        SoundType sound = s.getSoundType();
        boolean metal = sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.CHAIN || sound == SoundType.LANTERN;
        for (int i = 0; i < 2; i++) {
            BodyFx.puff(level, tint, 0.5F + 0.3F * speed, hx + (r.nextDouble() - 0.5) * 0.15, hy + r.nextDouble() * 0.2, hz + (r.nextDouble() - 0.5) * 0.15,
                    -l.fx() * 0.03 + (r.nextDouble() - 0.5) * 0.04, 0.06 + 0.06 * speed, -l.fz() * 0.03 + (r.nextDouble() - 0.5) * 0.04);
        }
        if (metal && speed > 0.6F && r.nextFloat() < speed) {
            BodyFx.puff(level, 0xFFD58A, 0.3F, hx, hy, hz, -l.fx() * 0.08 + (r.nextDouble() - 0.5) * 0.1, 0.1 + r.nextDouble() * 0.08,
                    -l.fz() * 0.08 + (r.nextDouble() - 0.5) * 0.1);
        }
    }
}
