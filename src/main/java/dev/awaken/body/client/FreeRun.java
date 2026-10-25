package dev.awaken.body.client;

import dev.awaken.body.Body;
import dev.awaken.body.RunnerRules;
import dev.awaken.body.client.feel.FeelConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The one-button free-run (SPEC §17.14): hold sprint (with forward) and the body reads the ground ahead and picks the
 * move. On the ground: a low wall is vaulted, a ledge to chest height mantled (ClientMoves), a wall jumped at (into a
 * climb with Wall Stride, a hang when its top is in reach), an edge leapt from. In the air: a wall beside you is run
 * along and a wall ahead climbed as if jump were held (RunnerMoves). Hanging: forward climbs out (or up). Every jump,
 * yours or the free-run's, bends a little toward the ledge you're heading for (capped: {@link RunnerRules#PULL_MAX}°).
 * The keys still do everything by hand; the server checks every move exactly as before.
 */
final class FreeRun {
    private FreeRun() {}

    /** Hanging, how long forward must be held (with sprint) before the body climbs. */
    static final int HANG_CLIMB = 6;

    /** This tick: sprint held with forward, the free-run on. */
    static boolean held;
    private static int groundJumpAt = -100, hangHeld, pullLeft;
    private static double pullX, pullZ, pulled;
    private static final double[] OUT = new double[2];
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    /** Start of the input step. */
    static void begin(LocalPlayer p, Input k) {
        held = FeelConfig.freeRun() && k.sprint() && k.forward() && !k.backward() && !k.shift()
                && !p.isUsingItem() && Minecraft.getInstance().gui.screen() == null;
        if (!held) hangHeld = 0;
    }

    /** Up a climb line (Carillon's drainpipes, trellises, hoist ropes, any ladder: #minecraft:climbable), free-running. */
    static final double CLIMB_UP = 0.3;
    private static int climbTicks;

    /**
     * Against a climb line with the free-run held: hand over hand, half again a ladder's pace (0.3 a tick against
     * 0.2), a grip sound every few pulls. At the top the eave's lip is caught as a hang and the hold climbs out of it.
     */
    static void climb(LocalPlayer p) {
        if (!held || !p.onClimbable() || !p.horizontalCollision) {
            climbTicks = 0;
            return;
        }
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, Math.max(v.y, CLIMB_UP), v.z);
        if (climbTicks++ % 6 == 0) BodyFx.sound(p, dev.awaken.body.BodyContent.GRAB.get(), 0.3F, 1.1F + p.getRandom().nextFloat() * 0.2F);
    }

    /** On the ground, running: a vault, a jump at a wall or a leap from an edge. True when it jumped. */
    static boolean ground(LocalPlayer p, BlockGetter level, CollisionContext ctx, int now) {
        if (!held) {
            tip(p, level, ctx);
            return false;
        }
        if (!p.onGround() || !p.isSprinting() || now - groundJumpAt < 6) return false;
        Vec3 v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h < 0.12) return false;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        if ((v.x * fx + v.z * fz) / h < 0.8) return false;
        int what = RunnerRules.ahead(level, ctx, M, p.getX(), p.getY(), p.getZ(), fx, fz, h, OUT);
        String why = switch (what) {
            case RunnerRules.AHEAD_VAULT -> String.format("vault %.2f", OUT[0]);
            case RunnerRules.AHEAD_LEAP -> "leap from the edge";
            case RunnerRules.AHEAD_WALL -> Body.wallStride(p) || topInReach(p, level, ctx) ? String.format("jump at the wall (%.1f)", OUT[0]) : null;
            default -> null;
        };
        if (why == null) return false;
        dev.awaken.core.api.TipHooks.client().done(dev.awaken.body.ascent.AscentTips.FREERUN);
        if (what == RunnerRules.AHEAD_VAULT && p.level() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
            // the hand planted on the top: a slap and a puff off it (weight, SPEC §17.15)
            double tx = p.getX() + fx * (0.3 + RunnerRules.AHEAD_REACH), tz = p.getZ() + fz * (0.3 + RunnerRules.AHEAD_REACH);
            BodyFx.lip(cl, tx, p.getY() + OUT[0], tz, fx, fz);
            BodyFx.sound(p, dev.awaken.body.BodyContent.GRAB.get(), 0.55F, 1.25F);
        }
        p.jumpFromGround();
        groundJumpAt = now;
        ClientMoves.trace("free-run: " + why);
        return true;
    }

    /**
     * Running at a low wall or an edge without the free-run: its tip (while it can still teach; no reading once it's
     * retired or tips are off).
     */
    private static void tip(LocalPlayer p, BlockGetter level, CollisionContext ctx) {
        if (!p.onGround() || !p.isSprinting() || !FeelConfig.freeRun() || !dev.awaken.core.api.TipHooks.client().wants(dev.awaken.body.ascent.AscentTips.FREERUN)) return;
        Vec3 v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h < 0.12) return;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        int what = RunnerRules.ahead(level, ctx, M, p.getX(), p.getY(), p.getZ(), -Mth.sin(yaw), Mth.cos(yaw), h, OUT);
        if (what == RunnerRules.AHEAD_VAULT || what == RunnerRules.AHEAD_LEAP) dev.awaken.core.api.TipHooks.client().offer(dev.awaken.body.ascent.AscentTips.FREERUN);
    }

    /** A wall's top a hang can reach from a jump's height: worth jumping at without Wall Stride. */
    private static boolean topInReach(LocalPlayer p, BlockGetter level, CollisionContext ctx) {
        float yaw = p.getYRot();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int axis = Math.abs(fx) >= Math.abs(fz) ? 0 : 2, sign = (axis == 0 ? fx : fz) > 0 ? 1 : -1;
        return RunnerRules.findEdge(level, ctx, M, p.getX(), p.getY(), p.getZ(), axis, sign, 0.8, RunnerRules.HANG_LO,
                RunnerRules.HANG_HI + 1.2, 0, 0.2) != null;
    }

    /**
     * Leaving the ground with a jump: the ledge the jump is heading for (a lip ahead within 4.5, from a drop to a lower
     * roof up to a hang over the jump's top), which the next few ticks bend toward.
     */
    static void jumped(LocalPlayer p, BlockGetter level, CollisionContext ctx) {
        pullLeft = 0;
        float yaw = p.getYRot();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        if (Math.max(Math.abs(fx), Math.abs(fz)) < 0.643) return;
        int axis = Math.abs(fx) >= Math.abs(fz) ? 0 : 2, sign = (axis == 0 ? fx : fz) > 0 ? 1 : -1;
        for (double d = 1.0; d <= 4.5; d += 0.5) {
            RunnerRules.Edge e = RunnerRules.findEdge(level, ctx, M, p.getX() + fx * d, p.getY(), p.getZ() + fz * d, axis, sign, 0.5,
                    -1.0, RunnerRules.HANG_HI + 1.2, 0, 0.2);
            if (e == null) continue;
            pullX = e.hx();
            pullZ = e.hz();
            pullLeft = RunnerRules.PULL_TICKS;
            pulled = 0;
            return;
        }
    }

    /** In the air: the jump's bend toward its ledge, a few degrees a tick. */
    static void air(LocalPlayer p) {
        if (pullLeft <= 0) return;
        pullLeft--;
        Vec3 v = p.getDeltaMovement();
        double turned = RunnerRules.bend(p.getX(), p.getZ(), v.x, v.z, pullX, pullZ,
                Math.min(RunnerRules.PULL_STEP, RunnerRules.PULL_MAX - pulled), OUT);
        if (turned <= 0) {
            pullLeft = 0;
            return;
        }
        pulled += turned;
        p.setDeltaMovement(OUT[0], v.y, OUT[1]);
    }

    /** Hanging with sprint and forward held long enough: climb (a jump's own climb out, or up). */
    static boolean climbFromHang() {
        if (!held) return false;
        if (++hangHeld < HANG_CLIMB) return false;
        hangHeld = 0;
        return true;
    }

    static void landed() {
        pullLeft = 0;
    }

    static void reset() {
        held = false;
        groundJumpAt = -100;
        hangHeld = pullLeft = 0;
    }
}
