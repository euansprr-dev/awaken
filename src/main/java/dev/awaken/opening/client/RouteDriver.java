package dev.awaken.opening.client;

import dev.awaken.Awaken;
import dev.awaken.body.ascent.client.AscentRoutes;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.opening.OpeningRoutes;
import dev.awaken.opening.net.OpeningPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Development (tours and golden runs, {@code /awaken opening drive <route> [rec]}): runs one of the opening's routes on
 * this client with its own keys, point by point, the way a player would: sprinting at the next point, jumping at the
 * edge of a gap, stepping off for a drop, jumping into a wall to grab its ledge and climbing out, lowering into a hang
 * and shimmying, swimming, climbing ladders. With {@code rec} the Ascent's route recorder (AscentRoutes) takes the run
 * down tick by tick as a golden run, so a later tour plays it back blind and reads how far off it ends. A run that stops
 * making progress for four seconds is called stuck and logged with the point it was going for.
 */
final class RouteDriver {
    private RouteDriver() {}

    private static OpeningRoutes.@Nullable Route route;
    private static Vec3 origin = Vec3.ZERO;
    private static int i, age, phase, since, ticks;
    private static boolean rec;
    private static double best;

    static boolean active() {
        return route != null;
    }

    static void start(OpeningPayloads.Drive d) {
        LocalPlayer p = Minecraft.getInstance().player;
        OpeningRoutes.Route r = OpeningRoutes.get().route(d.route());
        if (p == null || r == null || r.points().size() < 2) return;
        stop("replaced");
        route = r;
        origin = d.origin();
        rec = d.rec();
        Vec3 a = world(0), b = world(1);
        float yaw = yawTo(a, b);
        p.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f 8", a.x, a.y, a.z, yaw));
        phase = 0;
        age = 0;
        i = 1;
        ticks = 0;
        Awaken.LOG.info("[opening] drive {} ({} points){}", r.name(), r.points().size(), rec ? ", recording" : "");
    }

    static void stop(String why) {
        if (route == null) return;
        if (rec && AscentRoutes.recording()) AscentRoutes.stop();
        keys(false, false, false, false, false, false, false);
        Awaken.LOG.info("[opening] drive {} {} at point {}/{} after {} ticks", route.name(), why, i, route.points().size() - 1, ticks);
        route = null;
    }

    private static Vec3 world(int k) {
        OpeningRoutes.Point q = route.points().get(k);
        return new Vec3(origin.x + q.x(), origin.y + q.ry(), origin.z + q.z());
    }

    private static float yawTo(Vec3 a, Vec3 b) {
        return (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
    }

    /** Before the player's tick: the keys for this tick. */
    static void tick() {
        if (route == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            route = null;
            return;
        }
        age++;
        if (phase == 0) {
            keys(false, false, false, false, false, false, false);
            if (age >= 24) {
                phase = 1;
                since = 0;
                best = Double.MAX_VALUE;
                if (rec) AscentRoutes.record(route.name());
            }
            return;
        }
        ticks++;
        if (ticks > 20 * 240) {
            stop("timed out");
            return;
        }
        OpeningRoutes.Point q = route.points().get(i);
        OpeningRoutes.Move m = q.move();
        Vec3 t = world(i);
        Vec3 pos = p.position();
        double dx = t.x - pos.x, dz = t.z - pos.z, dxz = Math.sqrt(dx * dx + dz * dz), dy = t.y - pos.y;
        boolean ground = p.onGround(), hang = MovementHooks.hanging(p), water = p.isInWater(), ladder = p.onClimbable();
        if (arrived(m, dxz, dy, ground, hang, water, ladder)) {
            Awaken.LOG.info("[opening] drive {} point {} {} at ({}, {}, {}) after {} ticks", route.name(), i, m,
                    Math.round(pos.x - origin.x), Math.round((pos.y - origin.y) * 10) / 10.0, Math.round(pos.z - origin.z), ticks);
            i++;
            since = 0;
            best = Double.MAX_VALUE;
            if (i >= route.points().size()) {
                stop("done");
                return;
            }
            q = route.points().get(i);
            m = q.move();
            t = world(i);
            dx = t.x - pos.x;
            dz = t.z - pos.z;
            dxz = Math.sqrt(dx * dx + dz * dz);
            dy = t.y - pos.y;
        }
        // progress, or stuck
        double d = dxz + Math.abs(dy) * 0.5;
        if (d < best - 0.15) {
            best = d;
            since = 0;
        } else if (++since > 80) {
            stop("stuck");
            return;
        }
        // the view: toward the point (held facing the wall while hanging)
        float want = yawTo(pos, t);
        if (!(hang && (m == OpeningRoutes.Move.SHIMMY || m == OpeningRoutes.Move.LEDGE_UP))) {
            float step = Mth.clamp(Mth.wrapDegrees(want - p.getYRot()), -24F, 24F);
            p.setYRot(p.getYRot() + step);
            p.setYHeadRot(p.getYRot());
            p.setXRot(10F);
        }
        Vec3 dir = dxz < 1e-4 ? Vec3.ZERO : new Vec3(dx / dxz, 0, dz / dxz);
        switch (m) {
            case RUN, WALK -> {
                boolean step = p.horizontalCollision && ground;
                keys(true, false, false, false, step, false, m == OpeningRoutes.Move.RUN && dxz > 1.2);
            }
            case JUMP -> keys(true, false, false, false, ground && (dxz < 1.5 || p.horizontalCollision), false, true);
            case MANTLE -> keys(true, false, false, false, ground && (dxz < 1.2 || p.horizontalCollision), false, true);
            case DROP -> keys(true, false, false, false, false, false, dxz > 1.0);
            case SLIDE -> keys(true, false, false, false, false, dxz < 3.0, true);
            case LEAP -> keys(true, false, false, false, ground && edgeAhead(p, dir), false, true);
            case HANG -> {
                if (dy < -0.5) {
                    // lower into a hang: walk to the edge holding sneak
                    keys(!hang, false, false, false, false, true, false);
                } else {
                    // jump into the wall: the hands find its ledge; then climb out
                    keys(!hang, false, false, false, hang ? age % 6 < 3 : ground && dxz < 1.8, false, !hang);
                }
            }
            case SHIMMY -> {
                double yr = Math.toRadians(p.getYRot());
                double rx = -Math.cos(yr), rz = -Math.sin(yr);
                boolean right = dx * rx + dz * rz > 0;
                keys(false, false, !right, right, false, false, false);
            }
            case LEDGE_UP -> keys(false, false, false, false, hang && age % 6 < 3, false, false);
            case SWIM -> keys(true, false, false, false, true, false, false);
            case CLIMB -> {
                if (dy > 0.2) keys(true, false, false, false, ladder || p.horizontalCollision, false, false);
                else if (ladder) keys(false, false, false, false, false, false, false); // let go: down the ladder
                else keys(true, false, false, false, false, false, false);
            }
            default -> keys(true, false, false, false, false, false, true);
        }
    }

    private static boolean arrived(OpeningRoutes.Move m, double dxz, double dy, boolean ground, boolean hang, boolean water, boolean ladder) {
        return switch (m) {
            case RUN, WALK, JUMP, MANTLE, SLIDE -> dxz < 0.7 && Math.abs(dy) < 1.4;
            case DROP, LEAP -> ground && dxz < 1.2 && Math.abs(dy) < 1.2;
            case HANG -> dy < -0.5 ? hang : ground && dxz < 1.0 && Math.abs(dy) < 0.8;
            case SHIMMY -> hang && dxz < 0.45;
            case LEDGE_UP -> ground && !hang && Math.abs(dy) < 0.8;
            case SWIM -> dxz < 1.2;
            case CLIMB -> dxz < 1.4 && (dy > 0 ? dy < 0.15 : dy > -0.15) && !ladder || dxz < 1.0 && Math.abs(dy) < 0.15;
            default -> dxz < 0.7;
        };
    }

    /** No floor a step ahead at the feet: the edge of a gap, jump now. */
    private static boolean edgeAhead(LocalPlayer p, Vec3 dir) {
        var level = p.level();
        double ax = p.getX() + dir.x * 0.55, az = p.getZ() + dir.z * 0.55;
        BlockPos below = BlockPos.containing(ax, p.getY() - 0.3, az);
        BlockPos below2 = below.below();
        return level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.getBlockState(below2).getCollisionShape(level, below2).isEmpty();
    }

    private static void keys(boolean fwd, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        Options o = Minecraft.getInstance().options;
        set(o.keyUp, fwd);
        set(o.keyDown, back);
        set(o.keyLeft, left);
        set(o.keyRight, right);
        set(o.keyJump, jump);
        set(o.keyShift, sneak);
        set(o.keySprint, sprint);
    }

    private static void set(KeyMapping k, boolean down) {
        k.setDown(down);
    }
}
