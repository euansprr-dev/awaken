package dev.awaken.mobs.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Flight for fliers: straight at the wanted point (the Vex approach, no 3D pathfinding, cheap enough for a room full of
 * wisps), but it finds its way (SPEC "Fliers find their way"; Euan 2026-10-28, the Plumed Coil stuck in a tree). Every
 * few ticks it looks along the way ahead with its body and the clearance its wings or coils need, and goes round what
 * is there, keeping to the side it chose. A flight pressed against something backs out the way it came; a body caught
 * inside leaves works its way out (Unwedge). It never teleports.
 */
final class FlyerMoveControl extends MoveControl<AwakenMob> {
    /** Ticks without getting closer before a flight is given up (something solid is in the way). */
    static final int STALL_TICKS = 40;
    /** Ticks between looks along the way ahead (staggered by id). */
    static final int LOOK_EVERY = 4;
    /** Ticks a way round something is flown before it is chosen again. */
    static final int DETOUR_LIFE = 40;
    /** Ticks it keeps to the side it went round, so it doesn't dither left, right, left. */
    static final int SIDE_LIFE = 80;
    /** Ticks a flight may push without the body moving half a block before it backs out. */
    static final int PRESSED_TICKS = 30;
    /** Ticks a way out is followed at most before it looks again. */
    static final int OUT_LIFE = 120;
    /** Ways round: turns off the line to the goal (degrees), each level, climbing and dropping; then straight up. */
    private static final float[] YAWS = {35, -35, 70, -70, 110, -110, 150, -150}, PITCHES = {0, 30, -25};
    private static final int CANDIDATES = YAWS.length * PITCHES.length + 1;

    private double closest = Double.MAX_VALUE;
    private int stalled;
    private @Nullable Vec3 detour;
    private int detourUntil, side, sideUntil, blockedLooks;
    /** How much of the last look ahead was clear (1: all of it). */
    private double ahead = 1.0;
    private Unwedge.@Nullable Out out;
    private int outUntil, outRetry, outIdle;
    /** The last places it flew through with room: the way back out of a dead end. */
    private final Vec3[] trail = new Vec3[4];
    private int trailAt;
    private Vec3 pressedFrom = Vec3.ZERO;
    private int pressed;

    FlyerMoveControl(AwakenMob mob) {
        super(mob);
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        // A dive or orbit can point beyond a legal target, especially uphill. Never steer a Lord out of its own home.
        if (mob instanceof AwakenBoss && mob.hasHome()) {
            var home = mob.getHomePosition();
            double hx = home.getX() + 0.5, hy = home.getY(), hz = home.getZ() + 0.5;
            double dx = x - hx, dy = y - hy, dz = z - hz;
            double distance = dx * dx + dy * dy + dz * dz, radius = mob.getHomeRadius();
            if (distance > radius * radius && distance > 0) {
                double scale = radius / Math.sqrt(distance);
                x = hx + dx * scale;
                y = hy + dy * scale;
                z = hz + dz * scale;
            }
        }
        if (x != wantedX || y != wantedY || z != wantedZ) {
            closest = Double.MAX_VALUE;
            stalled = 0;
        }
        super.setWantedPosition(x, y, z, speedModifier);
    }

    /** Whether it is working its way out of somewhere (a dead end, leaves); its goals' flights wait till it is out. */
    boolean gettingOut() {
        return out != null;
    }

    /** Asked by its idle goal when nowhere it could fly has room for its wings: work its way to somewhere that has. */
    void getClear() {
        if (out == null) startOut(true);
    }

    @Override
    public void tick() {
        if (mob.grounded()) {
            // brought down (SPEC §40): gravity has it until it rises again
            operation = Operation.WAIT;
            return;
        }
        if (mob.isVehicle() || mob.noPhysics) {
            straight(); // ridden, or passing through walls: nothing to find a way round
            return;
        }
        int beat = mob.tickCount + mob.getId();
        if (beat % 5 == 0) sense(beat);
        if (out != null) {
            getOut();
            return;
        }
        if (operation != Operation.MOVE_TO) {
            hover();
            return;
        }
        Vec3 here = mob.position(), wanted = new Vec3(wantedX, wantedY, wantedZ);
        double len = here.distanceTo(wanted);
        if (len < Math.max(0.5, mob.getBoundingBox().getSize() * 0.5)) {
            end();
            return;
        }
        // a flight that stops closing in (a branch, a wall) ends, so the flier chooses again instead of pressing on
        if (len < closest - 0.15) {
            closest = len;
            stalled = 0;
        } else if (++stalled > (detour != null ? STALL_TICKS * 2 : STALL_TICKS)) {
            end();
            return;
        }
        if (pressed(here)) return;
        if (detour != null && (here.distanceToSqr(detour) < 1.0 || mob.tickCount > detourUntil)) detour = null;
        if (beat % LOOK_EVERY == 0) look(here, wanted);
        fly(here, detour != null ? detour : wanted, speedModifier);
        face();
    }

    // ---- open air ----------------------------------------------------------------------------------------------------

    /** Ridden or passing through walls: straight at the wanted point, as fliers always flew. */
    private void straight() {
        if (operation != Operation.MOVE_TO) {
            hover();
            return;
        }
        Vec3 delta = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
        double len = delta.length();
        if (len < Math.max(0.5, mob.getBoundingBox().getSize() * 0.5)) {
            end();
            return;
        }
        double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedModifier * 0.16;
        mob.setDeltaMovement(mob.getDeltaMovement().add(delta.scale(speed / len)));
        face();
    }

    /** Hover: bleed off speed and bob a little so fliers don't look frozen. */
    private void hover() {
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(v.x * 0.8, v.y * 0.8 + Mth.sin(mob.tickCount * 0.15F) * 0.004, v.z * 0.8);
    }

    /** The flight is over (there, or given up): ease off, and its goal chooses again. */
    private void end() {
        operation = Operation.WAIT;
        closest = Double.MAX_VALUE;
        stalled = 0;
        detour = null;
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
    }

    /**
     * Thrust toward {@code aim}. In open air the flight is as it always was; going round something it steers its
     * velocity itself and eases off nearer what is ahead, so it turns before it touches (the bank reads the turn).
     */
    private void fly(Vec3 here, Vec3 aim, double modifier) {
        Vec3 d = aim.subtract(here);
        double len = d.length();
        if (len < 1.0E-4) return;
        double thrust = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * modifier * 0.16;
        Vec3 v = mob.getDeltaMovement();
        if (detour == null && ahead >= 1.0) {
            mob.setDeltaMovement(v.add(d.scale(thrust / len)));
            return;
        }
        // no way round found yet: it holds off rather than creep into what is there
        double cruise = thrust * 9.0 * (detour != null ? 0.8 : blockedLooks > 0 ? 0.0 : Math.max(0.2, ahead));
        Vec3 steer = d.scale(Math.min(cruise, len * 0.5) / len).subtract(v);
        double s = steer.length(), most = thrust * 2.5;
        mob.setDeltaMovement(v.add(s > most ? steer.scale(most / s) : steer));
    }

    private void face() {
        LivingEntity target = mob.getTarget();
        double fx = target != null ? target.getX() - mob.getX() : mob.getDeltaMovement().x;
        double fz = target != null ? target.getZ() - mob.getZ() : mob.getDeltaMovement().z;
        if (fx * fx + fz * fz > 1.0E-4) {
            float yaw = (float) (Mth.atan2(fz, fx) * Mth.RAD_TO_DEG) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 20.0F));
            mob.yBodyRot = mob.getYRot();
        }
    }

    // ---- the way ahead -----------------------------------------------------------------------------------------------

    /**
     * Looks along the way to where it is going (or round, on a detour) with its body and clearance. Something there:
     * it goes round. Its way open again: it leaves the detour. Nowhere to go three looks running: it backs out.
     */
    private void look(Vec3 here, Vec3 wanted) {
        double pad = FlightRoom.pad(mob);
        Vec3 aim = detour != null ? detour : wanted;
        double range = range(here, aim, pad);
        if (range <= 0.25) {
            ahead = 1.0;
            return;
        }
        AABB box = FlightRoom.padded(mob, here.x, here.y, here.z, pad);
        double f = FlightRoom.sweep(mob, box, aim.subtract(here).normalize().scale(range));
        if (f >= 1.0) {
            ahead = 1.0;
            blockedLooks = 0;
            if (detour != null) {
                double straight = range(here, wanted, pad);
                if (straight <= 0.25 || FlightRoom.sweep(mob, box, wanted.subtract(here).normalize().scale(straight)) >= 1.0) detour = null;
            }
            return;
        }
        ahead = f;
        if (detourRound(here, wanted, range, pad)) {
            blockedLooks = 0;
            return;
        }
        detour = null;
        if (++blockedLooks >= 3) {
            blockedLooks = 0;
            startOut(false);
        }
    }

    /** How far ahead to look: further the faster it goes; the last stretch to the spot is its goal's business. */
    private double range(Vec3 here, Vec3 aim, double pad) {
        double far = Mth.clamp(mob.getDeltaMovement().length() * 12.0 + 2.0, 3.0, 10.0);
        return Math.min(here.distanceTo(aim) - (mob.getBbWidth() * 0.5 + pad + 0.5), far);
    }

    /**
     * A way round what is ahead: the clear leg that turns least off the line to the goal, on the side it chose before,
     * climbing rather than dropping; with its full clearance first, then squeezing past with the bare body.
     */
    private boolean detourRound(Vec3 here, Vec3 wanted, double range, double pad) {
        Vec3 to = wanted.subtract(here);
        double yaw0 = Math.atan2(to.z, to.x), pitch0 = Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
        double leg = Mth.clamp(range, 3.0, 6.0);
        int keep = mob.tickCount < sideUntil ? side : 0;
        for (int pass = 0; pass < 2; pass++) {
            AABB box = FlightRoom.padded(mob, here.x, here.y, here.z, pass == 0 ? pad : 0.05);
            long tried = 0L;
            for (int t = pass == 0 ? 10 : 6; t > 0; t--) {
                int best = -1;
                double bestScore = Double.MAX_VALUE;
                for (int i = 0; i < CANDIDATES; i++) {
                    if ((tried >> i & 1L) != 0L) continue;
                    double s = score(i, keep);
                    if (s < bestScore) {
                        bestScore = s;
                        best = i;
                    }
                }
                tried |= 1L << best;
                Vec3 c = candidate(best, yaw0, pitch0);
                double f = FlightRoom.sweep(mob, box, c.scale(leg));
                if (f * leg < 2.0) continue;
                detour = here.add(c.scale(f >= 1.0 ? leg : f * leg - 0.5));
                detourUntil = mob.tickCount + DETOUR_LIFE;
                if (best < CANDIDATES - 1) {
                    side = YAWS[best / PITCHES.length] > 0 ? 1 : -1;
                    sideUntil = mob.tickCount + SIDE_LIFE;
                }
                return true;
            }
        }
        return false;
    }

    /** Lower is better: the turn, a climb a little and a drop more, and the other side from last time a lot. */
    private static double score(int i, int keep) {
        if (i == CANDIDATES - 1) return 0.62; // straight up: as dear as a wide turn
        float yaw = YAWS[i / PITCHES.length], pitch = PITCHES[i % PITCHES.length];
        double s = Math.abs(yaw) / 180.0 + Math.abs(pitch) / 180.0 * 0.8 + (pitch < 0 ? 0.1 : 0.0);
        if (keep != 0 && (yaw > 0 ? 1 : -1) != keep) s += 0.5;
        return s;
    }

    private static Vec3 candidate(int i, double yaw0, double pitch0) {
        if (i == CANDIDATES - 1) return new Vec3(0, 1, 0);
        double yaw = yaw0 + Math.toRadians(YAWS[i / PITCHES.length]);
        double pitch = Mth.clamp(pitch0 + Math.toRadians(PITCHES[i % PITCHES.length]), -1.0, 1.3);
        double flat = Math.cos(pitch);
        return new Vec3(flat * Math.cos(yaw), Math.sin(pitch), flat * Math.sin(yaw));
    }

    // ---- getting out -------------------------------------------------------------------------------------------------

    /** Every five ticks: caught inside a block (saved or spawned there) it starts out; with room, it marks the way back. */
    private void sense(int beat) {
        if (out != null) return;
        if (mob.tickCount >= outRetry && Unwedge.inside(mob)) {
            startOut(false);
            return;
        }
        if (beat % 10 == 0 && FlightRoom.passes(mob, mob.getX(), mob.getY(), mob.getZ(), FlightRoom.pad(mob))) {
            trail[trailAt++ & 3] = mob.position();
        }
    }

    /** Every eight ticks: a flight that pushes on without the body getting half a block anywhere backs out. */
    private boolean pressed(Vec3 here) {
        if ((mob.tickCount & 7) != 0) return false;
        if (here.distanceToSqr(pressedFrom) > 0.25) {
            pressedFrom = here;
            pressed = 0;
            return false;
        }
        if ((pressed += 8) < PRESSED_TICKS) return false;
        pressed = 0;
        startOut(false);
        return out != null;
    }

    /**
     * Chooses a way out: back the way it came if it can fly straight there, else the nearest spot where its body (or,
     * with {@code room}, its wings) is clear (Unwedge). None: it waits and looks again in two seconds.
     */
    private void startOut(boolean room) {
        detour = null;
        Unwedge.Out o = null;
        Vec3 here = mob.position();
        if (!room) {
            for (int i = 1; i <= trail.length && o == null; i++) {
                Vec3 p = trail[(trailAt - i) & 3];
                if (p == null) continue;
                double d = p.distanceToSqr(here);
                if (d > 1.0 && d < 16 * 16 && FlightRoom.sweep(mob, mob.getBoundingBox(), p.subtract(here)) >= 1.0) o = new Unwedge.Out(p, false);
            }
        }
        if (o == null) o = Unwedge.wayOut(mob, true, Math.min(FlightRoom.pad(mob), 0.25), room);
        out = o;
        outUntil = mob.tickCount + OUT_LIFE;
        outIdle = 0;
        if (o == null) outRetry = mob.tickCount + 40;
        else operation = Operation.WAIT; // the flight it was on is over; its goal chooses again once it is out
    }

    /** One tick of getting out: flown if the way is straight, else pushed through the leaves (Unwedge). */
    private void getOut() {
        Unwedge.Out o = out;
        Vec3 here = mob.position();
        if (here.distanceToSqr(o.to()) < 0.36 || mob.tickCount > outUntil) {
            out = null; // out (or long enough at it): its goals choose again, and ask again if there's still no room
            return;
        }
        if (o.push()) {
            if (!Unwedge.step(mob, o.to(), true)) {
                out = null; // something solid came in the way: look again in a second
                outRetry = mob.tickCount + 20;
            }
            return;
        }
        Vec3 before = mob.getDeltaMovement();
        Vec3 d = o.to().subtract(here);
        double thrust = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.6 * 0.16;
        Vec3 steer = d.scale(Math.min(thrust * 6.0, d.length() * 0.4) / d.length()).subtract(before);
        double s = steer.length(), most = thrust * 2.0;
        mob.setDeltaMovement(before.add(s > most ? steer.scale(most / s) : steer));
        face();
        if (here.distanceToSqr(pressedFrom) > 0.04) {
            pressedFrom = here;
            outIdle = 0;
        } else if (++outIdle > 20) {
            out = null; // it isn't getting anywhere this way: choose again
            outRetry = mob.tickCount + 10;
        }
    }
}
