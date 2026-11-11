package dev.awaken.kin;

import dev.awaken.bigday.BigDays;
import dev.awaken.hearth.Hearth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * What Aya and Dubu decide (SPEC §68.3 to §68.5). At home each has a day of its own made of small things, picked from what
 * the home really has (a raised spot in the light, a bed, the sun, the fire, whoever is in). Out with someone, Dubu
 * ranges ahead and looks back and Aya trails at the heel; when a fight starts both keep well back and watch, and come back
 * when it is over. Decisions are taken every fifth tick; the navigation does the walking in between.
 */
final class FamilyBrain {
    private FamilyBrain() {}

    /** Its own day at home. */
    static final int NONE = 0, WANDER = 1, SITTING = 2, RESTING = 3, SLEEPING = 4, SUN = 5, SHADOW = 6;
    /** How far out with you each keeps before it is put behind you, and how long a wait at a door lasts. */
    static final double CATCH_UP = 28, WAIT_REACH = 64;
    static final int DOOR_TICKS = 6000;
    /** A fight within this of you is theirs to keep out of; they stand this far back from it. */
    static final double FIGHT = 16, BACK_DUBU = 11, BACK_AYA = 13;

    static void tick(ServerLevel level, FamilyPet b) {
        if (b.owner == null) {
            studio(b);
            return;
        }
        if ((b.tickCount + b.getId()) % 5 != 0) return;
        FamilyWorld world = FamilyWorld.get(level.getServer());
        FamilyWorld.Home home = world.of(b.owner);
        FamilyWorld.One one = home == null ? null : home.one(b.kind());
        // the record names one body for each of them: any other removes itself (so neither can ever be two)
        if (one == null || one.id.isEmpty() || !one.id.get().equals(b.getUUID())) {
            b.discard();
            return;
        }
        if (!FamilyContent.on(level)) { // the rule is off: the body goes, the record is kept
            one.id = Optional.empty();
            world.setDirty();
            b.discard();
            return;
        }
        long now = level.getGameTime();
        int a = b.act();
        if ((a == FamilyPet.NUDGE && b.tickCount - b.actAt() > 40) || (a == FamilyPet.STRETCH && b.tickCount - b.actAt() > 52)) b.act(FamilyPet.IDLE);
        // getting nowhere is counted, so one that is stuck can be put right
        if (b.position().distanceToSqr(b.lastAt) < 0.01 && !b.getNavigation().isDone()) b.stuck++;
        else b.stuck = 0;
        b.lastAt = b.position();
        if (b.stuck > 40 && FamilyService.rescue(level, b)) return;
        if (b.arriveTo != null) {
            arrive(level, b, now);
            return;
        }
        switch (one.state) {
            case FamilyWorld.WITH -> outWith(level, b, world, one, now);
            case FamilyWorld.WAITING, FamilyWorld.DOOR -> waiting(level, b, world, one, now);
            default -> atHome(level, b, world, home, one, now);
        }
    }

    /** A body placed to be looked at: it walks where it was told and holds its pose. */
    private static void studio(FamilyPet b) {
        Vec3 to = b.walkTo;
        if (to == null) return;
        if (b.distanceToSqr(to) < 1.0) {
            b.walkTo = null;
            b.getNavigation().stop();
        } else if (b.getNavigation().isDone()) b.getNavigation().moveTo(to.x, to.y, to.z, b.walkSpeed);
    }

    // ---- moving ---------------------------------------------------------------------------------------------------------

    /** Walks toward a point; true once it is within {@code near} of it. Up on its feet while it walks. */
    static boolean go(FamilyPet b, double x, double y, double z, double speed, double near) {
        if (b.distanceToSqr(x, y, z) <= near * near) {
            b.getNavigation().stop();
            return true;
        }
        int a = b.act();
        if (a != FamilyPet.IDLE && a != FamilyPet.CARRY && a != FamilyPet.NUDGE && a != FamilyPet.STRETCH) b.act(FamilyPet.IDLE);
        if (b.getNavigation().isDone() || (b.tickCount + b.getId()) % 20 == 0) b.getNavigation().moveTo(x, y, z, speed);
        return false;
    }

    static boolean go(FamilyPet b, BlockPos at, double speed, double near) {
        return go(b, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, speed, near);
    }

    private static void face(FamilyPet b, @Nullable ServerPlayer p) {
        if (p != null) b.getLookControl().setLookAt(p, 30F, 30F);
    }

    // ---- arriving: the first walk up to the fire -----------------------------------------------------------------------

    private static void arrive(ServerLevel level, FamilyPet b, long now) {
        ServerPlayer p = b.arriveTo == null ? null : level.getServer().getPlayerList().getPlayer(b.arriveTo);
        if (p == null || p.level() != level || now > b.arriveUntil) {
            b.arriveTo = null;
            return;
        }
        double d = Math.sqrt(b.distanceToSqr(p));
        if (b.kind() == FamilyKind.DUBU) {
            // he runs straight in, giraffe and all
            if (d < 14 && b.arriveStage == 0) {
                b.arriveStage = 1;
                FamilyService.say(p, "[That's Dubu. He must have followed you the whole way.]", 9);
                FamilyService.voice(b, FamilyContent.DUBU_BARK.get(), 1F);
            }
            b.act(FamilyPet.CARRY);
            if (go(b, p.getX(), p.getY(), p.getZ(), 1.45, 2.3)) {
                face(b, p);
                b.again(FamilyPet.NUDGE);
                FamilyService.voice(b, FamilyContent.DUBU_PANT.get(), 1F);
                b.arriveTo = null;
            }
            return;
        }
        // she takes her time: a walk, a sit a few blocks off to look you over, then over to your shin
        if (b.arriveStage == 0) {
            if (go(b, p.getX(), p.getY(), p.getZ(), 0.8, 6.0)) {
                b.arriveStage = 1;
                b.until = now + 70;
                b.act(FamilyPet.SIT);
            }
        } else if (b.arriveStage == 1) {
            face(b, p);
            if (now >= b.until) b.arriveStage = 2;
        } else if (go(b, p.getX(), p.getY(), p.getZ(), 0.7, 1.7)) {
            face(b, p);
            b.again(FamilyPet.NUDGE);
            FamilyService.voice(b, FamilyContent.AYA_PURR.get(), 1F);
            FamilyService.say(p, "[And Aya, taking her time. They live here now.]", 9);
            b.arriveTo = null;
        }
    }

    // ---- out with someone -----------------------------------------------------------------------------------------------

    private static void outWith(ServerLevel level, FamilyPet b, FamilyWorld world, FamilyWorld.One one, long now) {
        ServerPlayer p = one.with.map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        if (p == null || p.level() != level || !p.isAlive()) {
            // gone from this world (the events handle a gate; this is the net under them): wait where it is a moment, then home
            if (p == null) FamilyService.sendHome(level, b, one);
            else {
                one.state = FamilyWorld.DOOR;
                one.waitAt = Optional.of(b.blockPosition());
                one.waitUntil = now + DOOR_TICKS;
                world.setDirty();
            }
            return;
        }
        double d2 = b.distanceToSqr(p);
        // a boat with a free seat: in it hops
        if (p.getVehicle() instanceof AbstractBoat boat) {
            if (!b.isPassenger() && d2 < 20 && boat.getPassengers().size() < 2) b.startRiding(boat, false, true);
            if (b.isPassenger()) {
                b.act(FamilyPet.SIT);
                return;
            }
        } else if (b.isPassenger()) b.stopRiding();
        // a fight: well back from it, watching
        Vec3 threat = FamilyService.threat(level, p);
        if (threat != null) {
            b.waryUntil = now + 60;
            b.wasWary = true;
            Vec3 away = p.position().subtract(threat);
            away = away.horizontalDistanceSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
            double back = b.kind() == FamilyKind.AYA ? BACK_AYA : BACK_DUBU;
            BlockPos to = FamilySpots.standable(level, p.getX() + away.x * back, p.getY(), p.getZ() + away.z * back);
            if (to == null || go(b, to, 1.5, 2.0)) {
                b.getNavigation().stop();
                b.act(FamilyPet.HIDE);
                b.getLookControl().setLookAt(threat.x, threat.y + 1, threat.z);
            }
            return;
        }
        if (b.waryUntil > now) {
            b.act(FamilyPet.HIDE);
            face(b, p);
            return;
        }
        if (b.wasWary) {
            b.wasWary = false;
            b.returning = true;
        }
        // left behind (the rope, wings, a mount): it turns up out of sight behind you and runs in
        if (d2 > CATCH_UP * CATCH_UP || (b.stuck > 16 && d2 > 100)) {
            FamilyService.catchUp(level, b, p);
            return;
        }
        if (p.isSleeping()) { // a night away from home: asleep by your bed
            if (go(b, p.getX(), p.getY(), p.getZ(), 1.0, 2.2)) b.act(FamilyPet.SLEEP);
            return;
        }
        if (b.returning) { // the fight is over: he runs in and wriggles, she strolls
            if (go(b, p.getX(), p.getY(), p.getZ(), b.kind() == FamilyKind.DUBU ? 1.5 : 0.9, 2.4)) {
                b.returning = false;
                face(b, p);
                b.again(FamilyPet.NUDGE);
                FamilyService.voice(b, b.kind() == FamilyKind.DUBU ? FamilyContent.DUBU_PANT.get() : FamilyContent.AYA_TRILL.get(), 1F);
            }
            return;
        }
        follow(level, b, p, now, Math.sqrt(d2));
    }

    /** Keeping with someone, each in its own manner: Dubu a few blocks ahead, at heel when you sneak; Aya behind and to one side. */
    static void follow(ServerLevel level, FamilyPet b, ServerPlayer p, long now, double far) {
        double ps = FamilyService.speed(p);
        if (ps > 0.02) b.stillSince = now;
        long still = now - b.stillSince;
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 side = new Vec3(-look.z, 0, look.x);
        boolean dubu = b.kind() == FamilyKind.DUBU;
        if (still > (dubu ? 60 : 80)) {
            // you have stopped: over to you, a sit, and after a while a proper rest
            if (go(b, p.getX(), p.getY(), p.getZ(), 1.0, dubu ? 2.2 : 2.8)) {
                face(b, p);
                b.act(still > (dubu ? 320 : 420) ? FamilyPet.REST : FamilyPet.SIT);
            }
            return;
        }
        Vec3 want = dubu ? (p.isShiftKeyDown() ? p.position().add(side.scale(1.1)) : p.position().add(look.scale(3.2)).add(side.scale(0.8)))
                : p.position().subtract(look.scale(2.2)).subtract(side.scale(1.3));
        double speed = far > 10 ? 1.55 : ps > 0.24 ? 1.45 : far > 5 ? 1.25 : 1.0;
        go(b, want.x, p.getY(), want.z, speed, 1.2);
    }

    // ---- waiting: told to, or at a door you went through ----------------------------------------------------------------

    private static void waiting(ServerLevel level, FamilyPet b, FamilyWorld world, FamilyWorld.One one, long now) {
        BlockPos at = one.waitAt.orElse(b.blockPosition());
        ServerPlayer p = one.with.map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        boolean here = p != null && p.level() == level && p.isAlive();
        boolean near = here && p.blockPosition().distSqr(at) <= WAIT_REACH * WAIT_REACH;
        if (one.state == FamilyWorld.DOOR && near) { // you came back out where you went in
            one.state = FamilyWorld.WITH;
            one.waitAt = Optional.empty();
            one.waitUntil = 0;
            world.setDirty();
            b.returning = true;
            return;
        }
        if (one.state == FamilyWorld.DOOR && here) { // you came back out somewhere else: it goes home
            FamilyService.sendHome(level, b, one);
            return;
        }
        if (near) {
            if (one.waitUntil != 0 && one.state == FamilyWorld.WAITING) {
                one.waitUntil = 0;
                world.setDirty();
            }
        } else if (one.waitUntil == 0) { // you have gone off: five minutes, then home
            one.waitUntil = now + (here ? 100 : DOOR_TICKS);
            world.setDirty();
        } else if (now >= one.waitUntil) {
            FamilyService.sendHome(level, b, one);
            return;
        }
        if (FamilySpots.foeNear(level, b, 10)) {
            b.getNavigation().stop();
            b.act(FamilyPet.HIDE);
            return;
        }
        if (go(b, at, 1.0, 1.6)) {
            if (b.until == 0) b.until = now + 400;
            b.act(now > b.until ? FamilyPet.REST : FamilyPet.SIT);
            if (near && p.distanceToSqr(b) < 256) face(b, p);
        } else b.until = 0;
    }

    // ---- at home --------------------------------------------------------------------------------------------------------

    private static void atHome(ServerLevel level, FamilyPet b, FamilyWorld world, FamilyWorld.Home home, FamilyWorld.One one, long now) {
        Hearth.HearthInfo h = FamilyService.hearth(level.getServer(), b.owner);
        boolean dubu = b.kind() == FamilyKind.DUBU;
        if (h == null) {
            // no Hearth at all: it keeps with its owner until there is a home again
            ServerPlayer o = level.getServer().getPlayerList().getPlayer(b.owner);
            if (o != null && o.level() == level) {
                double d2 = b.distanceToSqr(o);
                if (d2 > CATCH_UP * CATCH_UP) FamilyService.catchUp(level, b, o);
                else follow(level, b, o, now, Math.sqrt(d2));
            }
            return;
        }
        BlockPos a = h.anchor();
        int r = h.radius();
        double da2 = b.distanceToSqr(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        if (da2 > (r + 24.0) * (r + 24.0)) {
            // far from home (sent back from the wild, or the Hearth moved): it sets off, and is home when you get there
            Vec3 dir = new Vec3(a.getX() + 0.5 - b.getX(), 0, a.getZ() + 0.5 - b.getZ()).normalize();
            go(b, b.getX() + dir.x * 12, b.getY(), b.getZ() + dir.z * 12, 1.3, 1.0);
            if (++b.leaving > 24) FamilyService.vanishHome(level, b, one);
            return;
        }
        b.leaving = 0;
        // a raid coming or on, or a foe close: in by the fire, low
        if (BigDays.uneasy(level, a) || BigDays.raiding(level, a) || FamilySpots.foeNear(level, b, 10)) {
            if (go(b, a.getX() + (dubu ? 1.5 : -1.5), a.getY(), a.getZ() + 1.5, 1.4, 2.5)) b.act(FamilyPet.HIDE);
            return;
        }
        if (b.songUntil > now) { // the tower's song: he howls along, she flattens her ears
            b.getNavigation().stop();
            b.act(FamilyPet.SONG);
            if (b.songAt != null) b.getLookControl().setLookAt(b.songAt.getX() + 0.5, b.songAt.getY() + 2.0, b.songAt.getZ() + 0.5);
            if (dubu && b.tickCount % 60 < 5) FamilyService.voice(b, FamilyContent.DUBU_HOWL.get(), 1F);
            return;
        }
        ServerPlayer p = FamilyService.homePerson(level, b, h);
        // someone has just come home
        if (b.greet != null && now >= b.greetAt) {
            ServerPlayer g = level.getServer().getPlayerList().getPlayer(b.greet);
            if (g == null || g.level() != level || now > b.greetAt + 600 || g.distanceToSqr(b) > 96 * 96) b.greet = null;
            else {
                if (dubu) b.act(FamilyPet.CARRY);
                if (go(b, g.getX(), g.getY(), g.getZ(), dubu ? 1.45 : 0.75, dubu ? 2.3 : 1.7)) {
                    face(b, g);
                    b.again(FamilyPet.NUDGE);
                    FamilyService.voice(b, dubu ? FamilyContent.DUBU_BARK.get() : FamilyContent.AYA_TRILL.get(), 1F);
                    b.greet = null;
                    b.until = now + 60;
                    b.activity = NONE;
                }
                return;
            }
        }
        // Dubu walks you to the yard's edge and sits, wanting to come
        if (dubu && b.escort != null) {
            ServerPlayer e = level.getServer().getPlayerList().getPlayer(b.escort);
            if (e == null || e.level() != level || now > b.escortUntil || e.distanceToSqr(b) > 48 * 48) b.escort = null;
            else {
                Vec3 in = new Vec3(a.getX() + 0.5 - e.getX(), 0, a.getZ() + 0.5 - e.getZ());
                in = in.lengthSqr() < 0.01 ? Vec3.ZERO : in.normalize().scale(2.0);
                if (go(b, e.getX() + in.x, e.getY(), e.getZ() + in.z, 1.25, 1.2)) {
                    face(b, e);
                    b.act(FamilyPet.SIT);
                    FamilyService.hint(e, b);
                }
                return;
            }
        }
        if (p != null) {
            if (p.isSleeping()) { // her at the foot of your bed, him beside it
                if (!home.bed.equals(p.getSleepingPos())) {
                    home.bed = p.getSleepingPos();
                    world.setDirty();
                }
                if (go(b, p.getX(), p.getY(), p.getZ(), 0.8, dubu ? 2.4 : 1.3)) b.act(FamilyPet.SLEEP);
                return;
            }
            if (p.containerMenu != p.inventoryMenu && p.distanceToSqr(b) < 144) { // you are at a bench: she watches, he lies at your feet
                if (go(b, p.getX(), p.getY(), p.getZ(), 0.8, dubu ? 1.7 : 2.5)) {
                    face(b, p);
                    b.act(dubu ? FamilyPet.REST : FamilyPet.SIT);
                }
                return;
            }
        }
        day(level, b, h, home, p, now);
    }

    /** Its own day: something small to do, for a while, then something else. */
    private static void day(ServerLevel level, FamilyPet b, Hearth.HearthInfo h, FamilyWorld.Home home, @Nullable ServerPlayer p, long now) {
        boolean dubu = b.kind() == FamilyKind.DUBU;
        RandomSource rnd = b.getRandom();
        if (now >= b.until) {
            boolean slept = b.activity == SLEEPING && b.act() == FamilyPet.SLEEP;
            choose(level, b, h, home, p, now, rnd);
            if (slept && !dubu && b.activity != SLEEPING) { // the long stretch after a sleep, then on with her day
                b.again(FamilyPet.STRETCH);
                return;
            }
        }
        if (b.act() == FamilyPet.STRETCH) return;
        BlockPos at = b.spot;
        switch (b.activity) {
            case WANDER -> {
                if (at == null || go(b, at, dubu ? 0.5 : 0.42, 1.3)) {
                    b.activity = SITTING;
                    b.until = now + 60 + rnd.nextInt(120);
                }
            }
            case RESTING -> {
                if (at == null || go(b, at, 0.45, 0.9)) b.act(FamilyPet.REST);
            }
            case SLEEPING -> {
                if (at == null || go(b, at, 0.45, 1.0)) b.act(FamilyPet.SLEEP);
            }
            case SUN -> {
                if (at == null || go(b, at, 0.45, 0.9)) b.act(dubu ? FamilyPet.REST : FamilyPet.ROLL);
            }
            case SHADOW -> { // wherever you go at home, he is a few steps off
                if (p == null) b.until = now;
                else if (p.distanceToSqr(b) > 16) go(b, p.getX(), p.getY(), p.getZ(), 0.95, 2.6);
                else {
                    b.getNavigation().stop();
                    face(b, p);
                    b.act(now - b.stillSince > 200 ? FamilyPet.REST : FamilyPet.SIT);
                    if (FamilyService.speed(p) > 0.02) b.stillSince = now;
                }
            }
            default -> {
                b.getNavigation().stop();
                b.act(b.activity == SITTING ? FamilyPet.SIT : FamilyPet.IDLE);
                if (p != null && p.distanceToSqr(b) < 64) face(b, p);
            }
        }
    }

    private static void choose(ServerLevel level, FamilyPet b, Hearth.HearthInfo h, FamilyWorld.Home home, @Nullable ServerPlayer p, long now, RandomSource rnd) {
        boolean dubu = b.kind() == FamilyKind.DUBU, night = KinWatch.night(level);
        int roll = rnd.nextInt(100);
        BlockPos a = h.anchor();
        b.spot = null;
        b.stillSince = now;
        if (b.act() == FamilyPet.CARRY) b.act(FamilyPet.IDLE);
        if (night) {
            if (roll < (dubu ? 78 : 68)) {
                // he sleeps by the fire, she on the bed if there is one
                b.activity = SLEEPING;
                BlockPos bed = dubu ? null : FamilySpots.bed(level, home);
                b.spot = bed != null ? bed : FamilySpots.byTheFire(level, a, rnd);
                b.until = now + 1200 + rnd.nextInt(2400);
            } else wander(level, b, a, h.radius(), now, rnd);
            return;
        }
        if (dubu) {
            if (p != null && roll < 36) {
                b.activity = SHADOW;
                b.until = now + 400 + rnd.nextInt(600);
            } else if (roll < 66) {
                wander(level, b, a, h.radius(), now, rnd);
                if (rnd.nextInt(5) < 2) b.act(FamilyPet.CARRY); // his giraffe goes with him
            } else if (roll < 82) {
                b.activity = RESTING;
                b.spot = p != null ? FamilySpots.standable(level, p.getX() + rnd.nextInt(5) - 2, p.getY(), p.getZ() + rnd.nextInt(5) - 2) : FamilySpots.byTheFire(level, a, rnd);
                b.until = now + 400 + rnd.nextInt(800);
            } else if (roll < 92) {
                b.activity = SUN;
                b.spot = FamilySpots.sun(level, a, rnd);
                if (b.spot == null) b.activity = SITTING;
                b.until = now + 300 + rnd.nextInt(500);
            } else {
                b.activity = SITTING;
                b.until = now + 100 + rnd.nextInt(200);
            }
            return;
        }
        if (roll < 30) { // the loaf, somewhere raised and in the light
            b.activity = RESTING;
            b.spot = FamilySpots.perch(level, a, rnd);
            if (b.spot == null) b.spot = FamilySpots.wander(level, a, 8, rnd);
            b.until = now + 600 + rnd.nextInt(1200);
        } else if (roll < 42) { // belly up in the sun
            b.activity = SUN;
            b.spot = FamilySpots.sun(level, a, rnd);
            if (b.spot == null) b.activity = SITTING;
            b.until = now + 200 + rnd.nextInt(300);
        } else if (roll < 68) wander(level, b, a, h.radius(), now, rnd);
        else if (roll < 84) {
            b.activity = SITTING;
            b.until = now + 160 + rnd.nextInt(300);
        } else { // a nap
            b.activity = SLEEPING;
            BlockPos bed = FamilySpots.bed(level, home);
            b.spot = bed != null ? bed : FamilySpots.byTheFire(level, a, rnd);
            b.until = now + 600 + rnd.nextInt(900);
        }
    }

    private static void wander(ServerLevel level, FamilyPet b, BlockPos a, int radius, long now, RandomSource rnd) {
        b.activity = WANDER;
        b.spot = FamilySpots.wander(level, a, Math.min(radius - 4, 14), rnd);
        b.until = now + 300 + rnd.nextInt(300);
    }
}
