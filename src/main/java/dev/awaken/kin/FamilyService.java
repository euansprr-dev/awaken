package dev.awaken.kin;

import dev.awaken.bigday.BigDays;
import dev.awaken.core.api.Goals;
import dev.awaken.hearth.Hearth;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Hearth's family, kept (SPEC §69): their arrival at a household's first Hearth, the record and the bodies kept in
 * step (a lost body is made again; one out with you that falls behind turns up out of your sight), who is asked along and
 * who is told to stay, the greeting when you come home, and their words on the plate, the Hearth page and Tasks.
 * Nothing here fights, helps a fight or can be hurt.
 */
public final class FamilyService {
    private FamilyService() {}

    /** They set out this long after the family's clock first sees a Hearth, and Aya this long after Dubu. */
    static final int ARRIVE_AFTER = 600, AYA_AFTER = 220;
    /** A body unseen this long, where it ought to be loaded, is lost and made again. */
    static final int LOST_AFTER = 200;
    /** Away from home this long, and far, earns a greeting. */
    static final int GREET_AFTER = 2400;

    private static final class Track {
        Vec3 at = Vec3.ZERO;
        double speed;
        boolean inside, far;
        long awaySince, deep, escortAt;
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final Map<String, Long> MISSING = new HashMap<>();
    private static final Map<UUID, long[]> THREAT_AT = new HashMap<>();
    private static final Map<UUID, Vec3> THREAT = new HashMap<>();

    static void serverStopped() {
        TRACKS.clear();
        MISSING.clear();
        THREAT_AT.clear();
        THREAT.clear();
    }

    // ---- who and where --------------------------------------------------------------------------------------------------

    /** The Hearth this owner owns, or null when they have none now. */
    static Hearth.@Nullable HearthInfo hearth(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null) return null;
        for (Hearth.HearthInfo h : Hearth.hearthsOf(server, owner)) if (h.owner().equals(owner)) return h;
        return null;
    }

    /** The nearest of the household who is in at this Hearth. */
    static @Nullable ServerPlayer homePerson(ServerLevel level, FamilyPet b, Hearth.HearthInfo h) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        double reach = (h.radius() + 8.0) * (h.radius() + 8.0);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !h.has(p.getUUID()) || p.blockPosition().distSqr(h.anchor()) > reach) continue;
            double d = p.distanceToSqr(b);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** How fast this player is going over the ground (blocks a tick, measured by the family's clock). */
    static double speed(ServerPlayer p) {
        Track t = TRACKS.get(p.getUUID());
        return t == null ? 0 : t.speed;
    }

    /**
     * Where the fight is, when this player is in one: something within {@link FamilyBrain#FIGHT} hunting them, or that
     * hurt them, or a foe they struck, in the last five seconds. Null in peace. Asked at most twice a second a player.
     */
    static @Nullable Vec3 threat(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        long[] at = THREAT_AT.computeIfAbsent(p.getUUID(), k -> new long[]{Long.MIN_VALUE});
        if (now - at[0] < 10) return THREAT.get(p.getUUID());
        at[0] = now;
        Vec3 found = null;
        List<Mob> hunting = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(FamilyBrain.FIGHT), m -> m.isAlive() && m.getTarget() == p);
        if (!hunting.isEmpty()) found = hunting.getFirst().position();
        else {
            LivingEntity by = p.getLastHurtByMob(), hit = p.getLastHurtMob();
            if (by != null && by.isAlive() && p.tickCount - p.getLastHurtByMobTimestamp() < 100) found = by.position();
            else if (hit instanceof Enemy && hit.isAlive() && p.tickCount - p.getLastHurtMobTimestamp() < 100) found = hit.position();
        }
        if (found == null) THREAT.remove(p.getUUID());
        else THREAT.put(p.getUUID(), found);
        return found;
    }

    static void say(ServerPlayer p, String line, int seconds) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line)).topic("kin").ttl(20 * seconds).build());
    }

    static void voice(FamilyPet b, SoundEvent sound, float pitch) {
        b.level().playSound(null, b.getX(), b.getY(), b.getZ(), sound, SoundSource.NEUTRAL, 0.7F, pitch * (0.95F + b.getRandom().nextFloat() * 0.1F));
    }

    // ---- bodies ---------------------------------------------------------------------------------------------------------

    /** Makes one of them here, and names it in the record as the body that counts. */
    static @Nullable FamilyPet spawn(ServerLevel level, UUID owner, FamilyKind kind, BlockPos at, float yaw) {
        FamilyWorld world = FamilyWorld.get(level.getServer());
        FamilyWorld.Home home = world.ensure(owner, level.getGameTime());
        FamilyPet b = (kind == FamilyKind.DUBU ? FamilyContent.DUBU.get() : FamilyContent.AYA.get()).create(level, EntitySpawnReason.EVENT);
        if (b == null) return null;
        FamilyWorld.One one = home.one(kind);
        b.owner = owner;
        b.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        b.setYBodyRot(yaw);
        b.setYHeadRot(yaw);
        b.setCustomName(Component.literal(one.name.isBlank() ? kind.name : one.name));
        b.setPersistenceRequired();
        one.id = Optional.of(b.getUUID());
        one.arrived = true;
        world.setDirty();
        level.addFreshEntity(b);
        return b;
    }

    /** The body the record names, when it is loaded. */
    static @Nullable FamilyPet body(ServerLevel level, FamilyWorld.One one) {
        return one.id.isPresent() && level.getEntity(one.id.get()) instanceof FamilyPet b && b.isAlive() ? b : null;
    }

    /** Out of sight behind the one it is with, a dozen blocks back, and it runs in. False when there is no ground for it now. */
    static boolean catchUp(ServerLevel level, FamilyPet b, ServerPlayer p) {
        BlockPos at = FamilySpots.unseen(level, p, 12, 20, false, b.getRandom());
        if (at == null) at = FamilySpots.unseen(level, p, 5, 11, false, b.getRandom());
        if (at == null) return false;
        if (b.isPassenger()) b.stopRiding();
        b.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        b.getNavigation().stop();
        b.stuck = 0;
        return true;
    }

    /** Stuck, or fallen out of the world: put back where it belongs (behind the one it is with, else by the fire). */
    static boolean rescue(ServerLevel level, FamilyPet b) {
        b.stuck = 0;
        FamilyWorld.Home home = b.owner == null ? null : FamilyWorld.get(level.getServer()).of(b.owner);
        FamilyWorld.One one = home == null ? null : home.one(b.kind());
        if (one == null) return false;
        if (one.state == FamilyWorld.WITH && one.with.isPresent()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(one.with.get());
            if (p != null && p.level() == level) return catchUp(level, b, p);
        }
        Hearth.HearthInfo h = hearth(level.getServer(), b.owner);
        if (h == null) return false;
        if (one.state != FamilyWorld.HOME && b.getY() > level.getMinY()) return false; // waiting somewhere: it stays, unless it fell out
        BlockPos at = FamilySpots.byTheFire(level, h.anchor(), b.getRandom());
        if (at == null) return false;
        if (one.state != FamilyWorld.HOME) {
            one.home();
            FamilyWorld.get(level.getServer()).setDirty();
        }
        b.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        b.getNavigation().stop();
        return true;
    }

    /** It goes home: at once in the record; the body walks off, and is at home when you next get there. */
    static void sendHome(ServerLevel level, @Nullable FamilyPet b, FamilyWorld.One one) {
        one.home();
        FamilyWorld.get(level.getServer()).setDirty();
        if (b != null) {
            if (b.isPassenger()) b.stopRiding();
            b.returning = false;
            b.wasWary = false;
            b.activity = FamilyBrain.NONE;
            b.until = 0;
        }
    }

    /** The body leaves the wild; the record has it at home, and it is made there when the home is next loaded. */
    static void vanishHome(ServerLevel level, FamilyPet b, FamilyWorld.One one) {
        one.id = Optional.empty();
        FamilyWorld.get(level.getServer()).setDirty();
        b.discard();
    }

    // ---- asking along, telling to stay, petting -------------------------------------------------------------------------

    /** A use on one of them: sneaking asks it along or tells it to stay; a plain use pets it. */
    static void used(ServerPlayer p, FamilyPet b, boolean sneak) {
        ServerLevel level = p.level();
        FamilyWorld world = FamilyWorld.get(level.getServer());
        FamilyWorld.Home home = b.owner == null ? null : world.of(b.owner);
        if (home == null) return;
        FamilyWorld.One one = home.one(b.kind());
        long now = level.getGameTime();
        boolean dubu = b.kind() == FamilyKind.DUBU;
        if (!sneak) {
            if (now - b.pettedAt < 30) return;
            b.pettedAt = now;
            b.getLookControl().setLookAt(p, 30F, 30F);
            if (b.act() != FamilyPet.SLEEP) b.again(FamilyPet.NUDGE);
            voice(b, dubu ? FamilyContent.DUBU_PANT.get() : FamilyContent.AYA_PURR.get(), 1F);
            return;
        }
        Hearth.HearthInfo h = hearth(level.getServer(), b.owner);
        boolean household = h != null ? h.has(p.getUUID()) : p.getUUID().equals(b.owner);
        if (!household) {
            p.sendOverlayMessage(Component.literal(b.kind().name + " lives with someone else."));
            return;
        }
        if (one.state != FamilyWorld.HOME && one.with.isPresent() && !one.with.get().equals(p.getUUID())) {
            p.sendOverlayMessage(Component.literal(b.kind().name + " is out with " + KinPlates.nameOf(p, one.with.get()) + "."));
            return;
        }
        if (one.state == FamilyWorld.WITH) {
            // stay: at home that is simply home; away from it, right here
            boolean atHome = h != null && b.blockPosition().distSqr(h.anchor()) <= (double) h.radius() * h.radius();
            if (atHome) one.home();
            else {
                one.state = FamilyWorld.WAITING;
                one.waitAt = Optional.of(b.blockPosition());
                one.waitUntil = 0;
            }
            b.until = 0;
            b.activity = FamilyBrain.NONE;
            b.getNavigation().stop();
            b.act(FamilyPet.SIT);
            if (dubu) voice(b, FamilyContent.DUBU_WHINE.get(), 1F);
        } else {
            one.state = FamilyWorld.WITH;
            one.with = Optional.of(p.getUUID());
            one.waitAt = Optional.empty();
            one.waitUntil = 0;
            b.stillSince = now;
            b.escort = null;
            b.greet = null;
            b.again(FamilyPet.NUDGE);
            voice(b, dubu ? FamilyContent.DUBU_BARK.get() : FamilyContent.AYA_TRILL.get(), 1F);
            if (world.asked.add(p.getUUID())) Progression.count(p, "awaken:family_asked_along");
        }
        world.setDirty();
    }

    /** Dubu sat at the yard's edge wanting to come: said once to each player. */
    static void hint(ServerPlayer p, FamilyPet b) {
        FamilyWorld world = FamilyWorld.get(p.level().getServer());
        if (world.asked.contains(p.getUUID()) || !world.hinted.add(p.getUUID())) return;
        world.setDirty();
        voice(b, FamilyContent.DUBU_WHINE.get(), 1F);
        say(p, "[Dubu wants to come. Crouch and call him and he will.]", 10);
    }

    /** The tower rings its song at this home (SPEC §43.7): he howls along, she puts her ears back. */
    static void song(ServerLevel level, BlockPos anchor, BlockPos tower, long until) {
        Hearth.HearthInfo h = Hearth.hearthAt(level, anchor).orElse(null);
        FamilyWorld.Home home = h == null ? null : FamilyWorld.get(level.getServer()).of(h.owner());
        if (home == null) return;
        for (FamilyKind k : FamilyKind.values()) {
            FamilyWorld.One one = home.one(k);
            FamilyPet b = body(level, one);
            if (b != null && one.state == FamilyWorld.HOME) {
                b.songUntil = Math.min(until, level.getGameTime() + 400);
                b.songAt = tower;
            }
        }
    }

    // ---- the family's clock ---------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 5 != 0) return;
        ServerLevel level = server.overworld();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Track t = TRACKS.computeIfAbsent(p.getUUID(), k -> new Track());
            Vec3 at = p.position();
            t.speed = p.level() == level ? Math.min(2.0, Math.hypot(at.x - t.at.x, at.z - t.at.z) / 5.0) : 0;
            t.at = at;
        }
        if (tick % 20 != 0 || !FamilyContent.on(level)) return;
        long now = level.getGameTime();
        FamilyWorld world = FamilyWorld.get(server);
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !Progression.profile(p).awakened()) continue;
            Hearth.HearthInfo h = Hearth.home(p).orElse(null);
            if (h == null) continue;
            if (p.level() == level) comings(level, world, p, h, now);
            if (done.add(h.owner())) home(level, world, h, now);
        }
        // homes whose people are away or offline but whose two are out somewhere still get their record kept straight
        for (Map.Entry<UUID, FamilyWorld.Home> e : world.homes.entrySet()) if (done.add(e.getKey())) away(level, world, e.getValue(), now);
    }

    /** Coming home and going out, for one player: the greeting, and Dubu's walk to the yard's edge. */
    private static void comings(ServerLevel level, FamilyWorld world, ServerPlayer p, Hearth.HearthInfo h, long now) {
        Track t = TRACKS.computeIfAbsent(p.getUUID(), k -> new Track());
        FamilyWorld.Home home = world.of(h.owner());
        double dist = Math.sqrt(p.blockPosition().distSqr(h.anchor()));
        int r = h.radius();
        boolean inside = dist <= r;
        if (inside && !t.inside) {
            if (home != null && t.awaySince != 0 && now - t.awaySince > GREET_AFTER && t.far) {
                for (FamilyKind k : FamilyKind.values()) {
                    FamilyWorld.One one = home.one(k);
                    FamilyPet b = body(level, one);
                    if (b == null || one.state != FamilyWorld.HOME) continue;
                    b.greet = p.getUUID();
                    b.greetAt = now + (k == FamilyKind.AYA ? 100 + level.getRandom().nextInt(120) : 0);
                }
            }
            t.awaySince = 0;
            t.far = false;
        }
        if (!inside && t.inside) t.awaySince = now;
        if (dist > r + 64) t.far = true;
        if (dist < r - 8) t.deep = now;
        if (home != null && inside && dist > r - 3 && now - t.deep < 300 && now - t.escortAt > 1200 && !world.asked.contains(p.getUUID())) {
            FamilyWorld.One one = home.dubu;
            FamilyPet b = body(level, one);
            if (b != null && one.state == FamilyWorld.HOME && b.arriveTo == null && b.distanceToSqr(p) < 40 * 40) {
                b.escort = p.getUUID();
                b.escortUntil = now + 260;
                t.escortAt = now;
            }
        }
        t.inside = inside;
    }

    /** One Hearth: its two set out when it is time, and a body that has gone missing is made again. */
    private static void home(ServerLevel level, FamilyWorld world, Hearth.HearthInfo h, long now) {
        FamilyWorld.Home home = world.ensure(h.owner(), now);
        BlockPos a = h.anchor();
        if (home.stage < 3) {
            ServerPlayer in = null;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && h.has(p.getUUID()) && p.blockPosition().distSqr(a) <= (double) h.radius() * h.radius()) {
                    in = p;
                    break;
                }
            }
            if (in != null && !BigDays.uneasy(level, a) && !BigDays.raiding(level, a)) {
                if (home.stage == 0 && now - home.seen >= ARRIVE_AFTER) {
                    if (setOut(level, h.owner(), FamilyKind.DUBU, in, now)) {
                        home.stage = 1;
                        home.next = now + AYA_AFTER + level.getRandom().nextInt(80);
                        world.setDirty();
                    }
                } else if (home.stage == 1 && now >= home.next) {
                    if (setOut(level, h.owner(), FamilyKind.AYA, in, now)) {
                        home.stage = 2;
                        home.next = now + 1200;
                        world.setDirty();
                    }
                } else if (home.stage == 2) {
                    FamilyPet aya = body(level, home.aya), dubu = body(level, home.dubu);
                    if (now >= home.next || ((aya == null || aya.arriveTo == null) && (dubu == null || dubu.arriveTo == null))) {
                        home.stage = 3;
                        world.setDirty();
                    }
                }
            }
        }
        for (FamilyKind k : FamilyKind.values()) keep(level, world, h, home, k, now);
    }

    /** One of them leaves the land for the fire: made out of sight, 24 to 36 blocks off, walking up to whoever is in. */
    private static boolean setOut(ServerLevel level, UUID owner, FamilyKind kind, ServerPlayer to, long now) {
        RandomSource rnd = level.getRandom();
        BlockPos at = FamilySpots.unseen(level, to, 24, 36, true, rnd);
        if (at == null) at = FamilySpots.unseen(level, to, 12, 20, true, rnd);
        if (at == null) return false;
        float yaw = (float) (Math.toDegrees(Math.atan2(to.getZ() - at.getZ(), to.getX() - at.getX())) - 90.0);
        FamilyPet b = spawn(level, owner, kind, at, yaw);
        if (b == null) return false;
        b.arriveTo = to.getUUID();
        b.arriveStage = 0;
        b.arriveUntil = now + 1200;
        return true;
    }

    /** The record and the body, kept in step for one of them at a Hearth that is loaded. */
    static void keep(ServerLevel level, FamilyWorld world, Hearth.HearthInfo h, FamilyWorld.Home home, FamilyKind k, long now) {
        FamilyWorld.One one = home.one(k);
        if (!one.arrived) return;
        String key = h.owner() + "/" + k.id;
        if (body(level, one) != null) {
            MISSING.remove(key);
            return;
        }
        ServerPlayer with = one.with.map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        boolean here = with != null && with.level() == level && with.isAlive();
        switch (one.state) {
            case FamilyWorld.WITH -> {
                if (with == null) one.home(); // they logged off: it has gone home
                else if (here) {
                    // its body fell out of the loaded world behind you: made again out of your sight
                    BlockPos at = FamilySpots.unseen(level, with, 12, 20, false, level.getRandom());
                    if (at != null) {
                        FamilyPet b = spawn(level, h.owner(), k, at, with.getYRot());
                        if (b != null) b.returning = false;
                    }
                } else { // through a gate without a body to wait at the door: it waits in the record
                    one.state = FamilyWorld.DOOR;
                    one.waitUntil = now + FamilyBrain.DOOR_TICKS;
                }
                world.setDirty();
            }
            case FamilyWorld.WAITING, FamilyWorld.DOOR -> {
                BlockPos at = one.waitAt.orElse(null);
                boolean near = here && at != null && with.blockPosition().distSqr(at) <= FamilyBrain.WAIT_REACH * FamilyBrain.WAIT_REACH;
                if (one.state == FamilyWorld.DOOR && here && (at == null || near)) {
                    one.state = FamilyWorld.WITH; // back out of the gate: with you again (its body follows by the rule above)
                    one.waitAt = Optional.empty();
                    one.waitUntil = 0;
                    world.setDirty();
                } else if (!near) {
                    if (one.waitUntil == 0) {
                        one.waitUntil = now + FamilyBrain.DOOR_TICKS;
                        world.setDirty();
                    } else if (now >= one.waitUntil || with == null) {
                        one.home();
                        one.id = Optional.empty();
                        world.setDirty();
                    }
                } else if (at != null && level.isPositionEntityTicking(at) && lost(key, now)) {
                    spawn(level, h.owner(), k, at, 0F); // it was waiting here and its body is gone: made again where it waits
                }
            }
            default -> {
                BlockPos a = h.anchor();
                if (!level.isPositionEntityTicking(a)) return; // only where bodies live and tick: elsewhere it sleeps with the land
                // sent home from the wild (no body by design) it is there at once; a body simply gone is given a moment first
                if (one.id.isPresent() && !lost(key, now)) return;
                BlockPos at = FamilySpots.wander(level, a, Math.min(h.radius() - 4, 12), level.getRandom());
                if (at == null) at = FamilySpots.byTheFire(level, a, level.getRandom());
                if (at != null && spawn(level, h.owner(), k, at, level.getRandom().nextFloat() * 360F) != null) MISSING.remove(key);
            }
        }
    }

    /** A Hearth nobody of its household is online for: one still out or waiting goes home in the record. */
    private static void away(ServerLevel level, FamilyWorld world, FamilyWorld.Home home, long now) {
        for (FamilyKind k : FamilyKind.values()) {
            FamilyWorld.One one = home.one(k);
            if (one.state == FamilyWorld.HOME || body(level, one) != null) continue;
            ServerPlayer with = one.with.map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
            if (with == null) {
                one.home();
                one.id = Optional.empty();
                world.setDirty();
            }
        }
    }

    private static boolean lost(String key, long now) {
        Long since = MISSING.putIfAbsent(key, now);
        return since != null && now - since >= LOST_AFTER;
    }

    /** Nothing may take one of them as its target (SPEC §69.5). */
    static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof FamilyPet) e.setCanceled(true);
    }

    // ---- words: the plate, the Hearth page, Tasks -----------------------------------------------------------------------

    /** What one is doing, as the Hearth page says it after "Now:". */
    static String doing(MinecraftServer server, FamilyWorld.@Nullable One one, @Nullable FamilyPet b, ServerPlayer reader, FamilyKind k) {
        boolean dubu = k == FamilyKind.DUBU;
        if (one != null && one.state == FamilyWorld.WITH) {
            boolean you = one.with.isPresent() && one.with.get().equals(reader.getUUID());
            return you ? "coming with you" : "out with " + one.with.map(id -> KinPlates.nameOf(reader, id)).orElse("someone");
        }
        if (one != null && one.state == FamilyWorld.WAITING) return "waiting where " + k.they + " was told to";
        if (one != null && one.state == FamilyWorld.DOOR) return "waiting for you to come back out";
        if (b == null) return "at home";
        return switch (b.act()) {
            case FamilyPet.SLEEP -> dubu ? "asleep, with his giraffe" : "asleep";
            case FamilyPet.REST -> dubu ? "lying with his chin on his paws" : "loafing in the light";
            case FamilyPet.ROLL -> "belly up in the sun";
            case FamilyPet.SIT -> "sitting, watching";
            case FamilyPet.HIDE -> "keeping well out of the way";
            case FamilyPet.CARRY -> "carrying his giraffe about";
            case FamilyPet.SONG -> dubu ? "howling along with the bells" : "putting up with the bells";
            case FamilyPet.NUDGE -> "saying hello";
            case FamilyPet.STRETCH -> "having a long stretch";
            default -> "about the place";
        };
    }

    /** Its plate, as this player reads it (SPEC §43.17): whose it is, and what it is doing. */
    static KinNet.Bond bond(ServerPlayer p, FamilyPet b) {
        MinecraftServer server = p.level().getServer();
        FamilyWorld world = FamilyWorld.get(server);
        FamilyWorld.Home home = b.owner == null ? null : world.of(b.owner);
        FamilyWorld.One one = home == null ? null : home.one(b.kind());
        Hearth.HearthInfo h = hearth(server, b.owner);
        boolean mine = h != null ? h.has(p.getUUID()) : p.getUUID().equals(b.owner);
        String who = b.owner == null ? "" : mine ? "yours" : KinPlates.nameOf(p, b.owner) + "'s";
        String note = cap(doing(server, one, b, p, b.kind())) + ".";
        if (mine && one != null && !world.asked.contains(p.getUUID())) {
            if (one.state == FamilyWorld.HOME) note += " Crouch and use to ask " + b.kind().them + " along.";
            else if (one.state == FamilyWorld.WITH) note += " Crouch and use to tell " + b.kind().them + " to stay.";
        }
        return new KinNet.Bond(b.getId(), who, mine ? KinPlates.WARM : KinPlates.OTHER, -1, note);
    }

    /** Their two cards, first under Your beasts: [kind, what it is, doing, a line about it, "", its id, its name]. */
    static List<List<String>> rows(ServerPlayer p, ServerLevel level, Hearth.HearthInfo h) {
        List<List<String>> out = new ArrayList<>(2);
        FamilyWorld.Home home = FamilyWorld.get(level.getServer()).of(h.owner());
        if (home == null) return out;
        for (FamilyKind k : FamilyKind.values()) {
            FamilyWorld.One one = home.one(k);
            if (!one.arrived) continue;
            FamilyPet b = body(level, one);
            out.add(List.of("family_" + k.id, k.sort, doing(level.getServer(), one, b, p, k), "came with you; nothing can hurt " + k.them, "",
                    b == null ? "" : b.getUUID().toString(), one.name.isBlank() ? k.name : one.name));
        }
        return out;
    }

    /** A new name from the Hearth page: only the Hearth's owner gives it; an empty one gives its own name back. */
    static boolean name(ServerPlayer p, FamilyPet b, String raw) {
        if (b.owner == null || !b.owner.equals(p.getUUID())) return false;
        FamilyWorld world = FamilyWorld.get(p.level().getServer());
        FamilyWorld.Home home = world.of(b.owner);
        if (home == null) return false;
        String n = KinPlates.clean(raw);
        if (n.isEmpty()) n = b.kind().name;
        home.one(b.kind()).name = n;
        world.setDirty();
        b.setCustomName(Component.literal(n));
        return true;
    }

    /** One row on the Tasks page's Hearth tab, until you have once asked one of them along (SPEC §69.8). */
    static List<Goals.Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        MinecraftServer server = p.level().getServer();
        FamilyWorld world = FamilyWorld.get(server);
        if (world.asked.contains(p.getUUID())) return List.of();
        Hearth.HearthInfo h = Hearth.home(p).orElse(null);
        FamilyWorld.Home home = h == null ? null : world.of(h.owner());
        if (home == null || home.stage < 3) return List.of();
        String a = home.aya.name.isBlank() ? FamilyKind.AYA.name : home.aya.name, d = home.dubu.name.isBlank() ? FamilyKind.DUBU.name : home.dubu.name;
        return List.of(Goals.Step.of("family/ask", Goals.Goal.HEARTH, "Take " + a + " or " + d + " out with you")
                .why(a + " and " + d + " will come with you if you ask. At home, crouch and use on one of them. Do it again to tell it to stay.")
                .at(h.anchor()).optional().priority(12).build());
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** For commands and tests: the two set out now for this player's Hearth, whatever the clock says. */
    static boolean arriveNow(ServerPlayer p) {
        Hearth.HearthInfo h = Hearth.home(p).orElse(null);
        if (h == null || p.level().dimension() != Level.OVERWORLD) return false;
        FamilyWorld world = FamilyWorld.get(p.level().getServer());
        FamilyWorld.Home home = world.ensure(h.owner(), p.level().getGameTime());
        if (home.stage >= 3) return false;
        home.seen = p.level().getGameTime() - ARRIVE_AFTER;
        if (home.stage == 1) home.next = p.level().getGameTime();
        world.setDirty();
        return true;
    }

    /** For commands and tests: every body of this Hearth's two that is loaded. */
    static List<FamilyPet> bodies(ServerLevel level, UUID owner) {
        List<FamilyPet> out = new ArrayList<>(2);
        FamilyWorld.Home home = FamilyWorld.get(level.getServer()).of(owner);
        if (home != null) for (FamilyKind k : FamilyKind.values()) {
            Entity e = home.one(k).id.map(id -> level.getEntity(id)).orElse(null);
            if (e instanceof FamilyPet b) out.add(b);
        }
        return out;
    }
}
