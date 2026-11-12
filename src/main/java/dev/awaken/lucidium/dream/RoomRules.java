package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Forces;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.trial.Course;
import dev.awaken.lucidium.trial.RoomLayouts;
import dev.awaken.lucidium.trial.Trial;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Rooms' rules while a dream runs (SPEC §9.5), and the Chase's fears and the Duel's mentor. One rule a room, shared
 * by everyone in the dream: the door (or the bell) that moves when you look away; the sleeper that wakes if you're loud;
 * the fall that goes on while you watch it; the three wrong things; the hallway that comes back until you walk past its
 * door backwards; the sealed room you make a door in. A Room won is won for everyone in it.
 */
final class RoomRules {
    private RoomRules() {}

    /** Look away: where the goal goes, this far from you. Hush: the sleeper hears you walk this near, or sprint. */
    static final int MOVE_NEAR = 16, MOVE_FAR = 28, HUSH = 12, HUSH_RUN = 18, HUNTERS = 3;
    /** The long fall: eyes down (degrees) lifts you, eyes up lands you; how far fallen it starts, the lift, the landing's drop. */
    static final float DOWN = 40f, UP = 15f;
    static final int FALL_FROM = 8, LIFT = 10, PAD_BELOW = 6, PAD = 3, FALL_DEPTH = 60;
    /** What's wrong here: a wrong guess costs 5 s. */
    static final int WRONG_COST = 100;
    /** In view (60 degrees either side), and aimed at (9 degrees). */
    static final double SEE = Math.cos(Math.toRadians(60)), AIM = Math.cos(Math.toRadians(9));

    /** A dream with a rule of its own: a Room, the Chase, the Duel. */
    static boolean is(ActiveDream ad) {
        return ad.bp.course != null && !ad.bp.course.trial().run();
    }

    /** A Room (one rule, won for everyone in it). */
    static boolean room(ActiveDream ad) {
        return ad.bp.course != null && ad.bp.course.trial().kind == Trial.Kind.ROOM;
    }

    static boolean bell(ActiveDream ad) {
        return ad.bp.plan.trialRef().startsWith("bell:");
    }

    /** How much deeper than its floor a fall in this dream goes before it's caught (the long fall's is the room). */
    static int extraDepth(Course c) {
        return c.trial() == Trial.LONG_FALL ? FALL_DEPTH : 0;
    }

    // ---- lines -------------------------------------------------------------------------------------------------------

    static String goal(Course c, boolean bell) {
        Trial t = c.trial();
        return switch (t) {
            case WRONG -> bell ? "Three things in this place are wrong. Find them all, and the bell will be there." : t.goal;
            case LOOK_AWAY -> bell ? t.goal : "The door moves whenever you look away from it. Reach it.";
            case HUSH -> bell ? t.goal : "Something is asleep by the door. Get to the door without waking it.";
            case CHASE -> bell ? "Your fear is coming after you, and it's carrying a bell. Take the bell from it." : t.goal;
            default -> t.goal;
        };
    }

    static String sub(Course c) {
        return switch (c.trial()) {
            case WRONG -> "Look at something wrong and press H. Look up at the sky and press H for a hint.";
            case HUSH -> "Sneak to move quietly.";
            case NO_DOOR -> "Look at a wall and press H.";
            default -> c.trial().sub;
        };
    }

    /** Where the end's light stands: the goal where it is now, the room's fixed door, or nowhere yet. */
    static @Nullable Vec3 end(ActiveDream ad) {
        Course c = ad.bp.course;
        if (ad.roomGoal != null) return Vec3.atBottomCenterOf(ad.roomGoal);
        if (c.goal().minY > -60) return new Vec3(c.goal().getCenter().x, c.goal().minY, c.goal().getCenter().z);
        return null;
    }

    static void sendGoal(ActiveDream ad) {
        Vec3 end = end(ad);
        LucidiumNet.TrialGoal msg = new LucidiumNet.TrialGoal(end != null, end != null ? end : Vec3.ZERO);
        for (UUID id : ad.participants) {
            ServerPlayer p = ad.level.getServer().getPlayerList().getPlayer(id);
            if (p != null) AwakenNet.send(p, msg);
        }
    }

    /** A line for everyone in the dream (the room's rule is everyone's). */
    static void tell(ActiveDream ad, long now, String line) {
        for (UUID id : ad.participants) {
            ServerPlayer p = ad.level.getServer().getPlayerList().getPlayer(id);
            if (p != null && Dreams.isDreaming(p)) Trials.hud(p, Dreams.state(p), ad.bp.course, now, line, false);
        }
    }

    // ---- the goal ----------------------------------------------------------------------------------------------------

    /** The goal moves (or appears, or hides): the small Waking Door with it, or, for a bell, the bells follow the spot. */
    static void moveGoal(ActiveDream ad, @Nullable BlockPos to) {
        BlockPos from = ad.roomGoal;
        boolean bell = bell(ad);
        if (!bell && from != null)
            for (BlockPos p : RoomLayouts.doorBlocks(from)) if (dev.awaken.core.api.Ready.loaded(ad.level, p)) ad.level.setBlock(p, Blocks.AIR.defaultBlockState(), ActiveDream.UPDATE);
        ad.roomGoal = to;
        if (to != null) {
            if (!bell)
                for (BlockPos p : RoomLayouts.doorBlocks(to)) if (dev.awaken.core.api.Ready.loaded(ad.level, p)) ad.level.setBlock(p, RoomLayouts.doorState(to, p), ActiveDream.UPDATE);
            ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), to.getX() + 0.5, to.getY() + 1.2, to.getZ() + 0.5, 16, 0.6, 0.9, 0.6, 0.02);
        }
        sendGoal(ad);
    }

    /** Somewhere on the room's floor {@link #MOVE_NEAR} to {@link #MOVE_FAR} from {@code p} (nearer if the floor's too small). */
    static @Nullable BlockPos away(ActiveDream ad, Entity p) {
        Course.Room r = ad.bp.course.room();
        if (r == null) return null;
        var random = ad.level.getRandom();
        BlockPos at = RoomLayouts.onFloor(r, p.getX(), p.getZ(), MOVE_NEAR, MOVE_FAR, random::nextInt);
        if (at == null) at = RoomLayouts.onFloor(r, p.getX(), p.getZ(), 8, MOVE_NEAR, random::nextInt);
        if (at == null) at = new BlockPos(2 * r.centre().getX() - p.getBlockX(), r.centre().getY(), 2 * r.centre().getZ() - p.getBlockZ());
        return at;
    }

    /** Whether {@code at} is in front of this dreamer's eyes, within {@code cos} of where they look and 64 blocks. */
    static boolean looksAt(ServerPlayer sp, Vec3 at, double cos) {
        Vec3 to = at.subtract(sp.getEyePosition());
        double dist = to.length();
        return dist < 1 || dist <= 64 && sp.getLookAngle().dot(to.scale(1 / dist)) >= cos;
    }

    /** A Room won: won for everyone in it in control (one still waiting for H can take control and walk out after). */
    static void won(ActiveDream ad, long now) {
        for (UUID id : List.copyOf(ad.participants)) {
            ServerPlayer p = ad.level.getServer().getPlayerList().getPlayer(id);
            if (p == null || !Dreams.isDreaming(p)) continue;
            DreamState st = Dreams.state(p);
            if (st.phase == 1) Trials.won(p, st, ad, now);
        }
    }

    /** Touching the goal: the small door where it stands now (bells are taken by striking them). */
    static boolean touched(ActiveDream ad, AABB box) {
        return ad.roomGoal != null && !bell(ad) && box.intersects(RoomLayouts.doorBox(ad.roomGoal));
    }

    // ---- each tick ---------------------------------------------------------------------------------------------------

    /** Once a tick for the dream. */
    static void tickDream(ActiveDream ad, List<ServerPlayer> players, long now) {
        switch (ad.bp.course.trial()) {
            case HUSH -> {
                if (now % 20 == 0) sleeper(ad, players);
            }
            case NO_DOOR -> {
                if (now % 8 == 0 && !ad.roomOpen) draft(ad);
            }
            case CHASE -> {
                if (now % 10 == 0) chase(ad, players, now);
            }
            case DUEL -> {
                if (now % 10 == 0 && ad.mentorState == -1 && now >= ad.roomNext) duel(ad, players);
            }
            default -> {}
        }
    }

    /** Once a tick for one dreamer in control. */
    static void tick(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course.Room r = ad.bp.course.room();
        if (r == null) return;
        switch (ad.bp.course.trial()) {
            case LOOK_AWAY -> {
                if (now % 2 == 0) lookAway(sp, ad, now);
            }
            case HUSH -> {
                if (now % 4 == 0) hush(sp, ad, now);
            }
            case LONG_FALL -> fall(sp, st, ad, r, now);
            case FOLD -> fold(sp, st, ad, r, now);
            case NO_DOOR -> {
                if (!ad.roomSealed && sp.getZ() > r.centre().getZ() - 2) seal(ad, r);
            }
            default -> {}
        }
    }

    /** H in control: the rule's own use of it (what's wrong here's guess, no door's door). False: it's the usual glint. */
    static boolean press(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course.Room r = ad.bp.course.room();
        if (r == null) return false;
        return switch (ad.bp.course.trial()) {
            case WRONG -> guess(sp, st, ad, r, now);
            case NO_DOOR -> makeDoor(sp, ad, r, now);
            default -> false;
        };
    }

    /** The glint also shows the rule's true thing: the wrong things left, the right wall, the fold's door, the goal. */
    static void glint(ActiveDream ad) {
        Course.Room r = ad.bp.course.room();
        if (r == null) return;
        switch (ad.bp.course.trial()) {
            case WRONG -> {
                for (int i = 0; i < r.things().size(); i++) if ((ad.roomFound & 1 << i) == 0) sparkle(ad, thingAt(r, i));
            }
            case NO_DOOR -> {
                AABB w = r.walls().get(r.right());
                sparkle(ad, w.getCenter());
            }
            case FOLD -> sparkle(ad, Vec3.atCenterOf(r.things().getFirst()));
            default -> {
                if (ad.roomGoal != null) sparkle(ad, Vec3.atCenterOf(ad.roomGoal).add(0, 0.6, 0));
            }
        }
    }

    private static void sparkle(ActiveDream ad, Vec3 at) {
        ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), at.x, at.y, at.z, 24, 0.5, 0.6, 0.5, 0.03);
    }

    // ---- look away ---------------------------------------------------------------------------------------------------

    static void lookAway(ServerPlayer sp, ActiveDream ad, long now) {
        BlockPos g = ad.roomGoal;
        if (g == null) return;
        Vec3 at = Vec3.atCenterOf(g).add(0, 0.5, 0);
        if (looksAt(sp, at, SEE)) {
            ad.roomSeen = true;
            return;
        }
        if (!ad.roomSeen || sp.distanceToSqr(at) < 9) return;
        ad.roomSeen = false;
        moveGoal(ad, away(ad, sp));
        if (ad.roomLoops++ == 0) tell(ad, now, "It moved the moment you looked away.");
        Dreams.playTo(sp, LucidiumContent.SIGN.get(), 0.4f, 0.7f);
    }

    // ---- hush ----------------------------------------------------------------------------------------------------------

    /** Someone asleep beside the goal (another once one wakes), breathing slow clouds. */
    static void sleeper(ActiveDream ad, List<ServerPlayer> players) {
        BlockPos g = ad.roomGoal;
        if (g == null || players.isEmpty()) return;
        Entity e = ad.roomSleeper == null ? null : ad.level.getEntity(ad.roomSleeper);
        if (e == null || !e.isAlive()) {
            BlockPos at = g.offset(2, 0, 0);
            if (!dev.awaken.core.api.Ready.loaded(ad.level, at)) return;
            int lv = Math.max(1, (int) (Progression.contentLevel(Progression.rank(players.getFirst())) * 0.8));
            LivingEntity sleeper = MobHooks.spawnThemed(ad.level, Nightmares.Family.GENERIC.theme(), MobRole.Role.NIGHTMARE, at, lv, Set.of(), "");
            if (!(sleeper instanceof Mob m)) return;
            m.setNoAi(true);
            m.setSilent(true);
            ad.roomSleeper = m.getUUID();
            ad.roomBeasts.add(m.getUUID());
            return;
        }
        ad.level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getEyeY() + 0.6, e.getZ(), 1, 0.15, 0.1, 0.15, 0.005);
    }

    static void hush(ServerPlayer sp, ActiveDream ad, long now) {
        BlockPos g = ad.roomGoal;
        if (g == null || now < ad.roomQuietUntil) return;
        double d2 = sp.distanceToSqr(Vec3.atCenterOf(g));
        boolean loud = d2 < HUSH * HUSH && !sp.isShiftKeyDown() || d2 < HUSH_RUN * HUSH_RUN && sp.isSprinting();
        if (!loud) return;
        // it wakes and comes for you; the goal is somewhere else, with something new asleep beside it
        if (ad.roomSleeper != null && ad.level.getEntity(ad.roomSleeper) instanceof Mob m) {
            m.setNoAi(false);
            m.setSilent(false);
            m.setTarget(sp);
        }
        ad.roomSleeper = null;
        hunters(ad);
        ad.level.playSound(null, g, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2F, 1.4F);
        moveGoal(ad, away(ad, sp));
        tell(ad, now, bell(ad) ? "It woke up. The bell is somewhere else now." : "It woke up. The door is somewhere else now.");
    }

    /** At most {@link #HUNTERS} woken things hunt you at once: the oldest fades. */
    private static void hunters(ActiveDream ad) {
        int awake = 0;
        for (int i = ad.roomBeasts.size() - 1; i >= 0; i--) {
            UUID id = ad.roomBeasts.get(i);
            Entity e = ad.level.getEntity(id);
            if (e == null || !e.isAlive()) {
                ad.roomBeasts.remove(i);
                continue;
            }
            if (id.equals(ad.roomSleeper)) continue;
            if (++awake > HUNTERS) {
                e.discard();
                ad.roomBeasts.remove(i);
            }
        }
    }

    // ---- the long fall -----------------------------------------------------------------------------------------------

    /** A fall you watch goes on (the dream lifts you back up while you look down); look up and there's somewhere to land. */
    static void fall(ServerPlayer sp, DreamState st, ActiveDream ad, Course.Room r, long now) {
        if (sp.onGround() || sp.fallDistance < 3 || sp.getY() > r.edgeY() - FALL_FROM) return;
        if (ad.roomLanded) {
            // someone found the landing: the dream steers a later faller over it
            BlockPos g = ad.roomGoal;
            if (g != null && sp.getY() > g.getY() + 2 && (Math.abs(sp.getX() - g.getX() - 0.5) > PAD || Math.abs(sp.getZ() - g.getZ() + 1.5) > PAD))
                Dreams.moveWithin(sp, new Vec3(g.getX() + 0.5, sp.getY(), g.getZ() - 1.5), sp.getYRot());
            return;
        }
        if (sp.getXRot() > DOWN) {
            Dreams.moveWithin(sp, sp.position().add(0, LIFT, 0), sp.getYRot());
            if (++st.lifts == 2) Trials.hud(sp, st, ad.bp.course, now, "The fall goes on for as long as you watch it.", false);
            return;
        }
        if (sp.getXRot() < UP) land(ad, sp, r, now);
    }

    /** A landing below the faller, and the goal on it. */
    static void land(ActiveDream ad, ServerPlayer sp, Course.Room r, long now) {
        int px = sp.getBlockX(), pz = sp.getBlockZ();
        int py = Math.max(sp.getBlockY() - PAD_BELOW, r.edgeY() - FALL_DEPTH + 4);
        BlockState road = LucidiumContent.DREAM_ROAD.get().defaultBlockState(), cloud = LucidiumContent.DREAM_CLOUD.get().defaultBlockState();
        for (int dx = -PAD; dx <= PAD; dx++)
            for (int dz = -PAD; dz <= PAD + 2; dz++) {
                BlockPos p = new BlockPos(px + dx, py - 1, pz + dz);
                if (!dev.awaken.core.api.Ready.loaded(ad.level, p)) continue;
                ad.level.setBlock(p, Math.abs(dx) == PAD || dz == -PAD || dz == PAD + 2 ? cloud : road, ActiveDream.UPDATE);
            }
        ad.roomLanded = true;
        moveGoal(ad, new BlockPos(px, py, pz + 3));
        tell(ad, now, "The fall's ending. There's somewhere to land.");
    }

    // ---- the fold ----------------------------------------------------------------------------------------------------

    /** Walk on and the hallway comes back; walk backwards past its door and the door opens. */
    static void fold(ServerPlayer sp, DreamState st, ActiveDream ad, Course.Room r, long now) {
        double x = sp.getX(), z = sp.getZ(), mx = x - st.lastX, mz = z - st.lastZ;
        st.lastX = x;
        st.lastZ = z;
        boolean inHall = Math.abs(x - (r.centre().getX() + 0.5)) < 2.5 && Math.abs(sp.getY() - r.edgeY()) < 3;
        if (!ad.roomOpen && inHall) {
            BlockPos door = r.things().getFirst();
            if (Math.abs(z - (door.getZ() + 0.5)) < 2.5 && backwards(sp, mx, mz)) openFold(ad, r, now);
        }
        if (inHall && z > r.loopTo() + 0.5) {
            Dreams.moveWithin(sp, new Vec3(x, sp.getY(), z - (r.loopTo() - r.loopFrom())), sp.getYRot());
            st.lastZ = z - (r.loopTo() - r.loopFrom());
            ad.roomLoops++;
            if (ad.roomLoops == 2) Trials.hud(sp, st, ad.bp.course, now, "You've passed that door before.", false);
            if (ad.roomLoops == 4) Trials.hud(sp, st, ad.bp.course, now, "Try walking backwards past the door you keep passing.", false);
        }
    }

    /** Moving (more than a crawl) the opposite way to where you look. */
    static boolean backwards(ServerPlayer sp, double mx, double mz) {
        double speed = Math.sqrt(mx * mx + mz * mz);
        Vec3 look = sp.getLookAngle();
        double ll = Math.sqrt(look.x * look.x + look.z * look.z);
        return speed > 0.04 && speed < 2 && ll > 0.1 && (mx * look.x + mz * look.z) / (speed * ll) < -0.6;
    }

    static void openFold(ActiveDream ad, Course.Room r, long now) {
        ad.roomOpen = true;
        for (BlockPos p : r.things()) {
            if (dev.awaken.core.api.Ready.loaded(ad.level, p)) ad.level.setBlock(p, Blocks.AIR.defaultBlockState(), ActiveDream.UPDATE);
            ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.2, 0.4, 0.3, 0.02);
        }
        ad.level.playSound(null, r.things().getFirst(), LucidiumContent.DOOR.get(), SoundSource.AMBIENT, 1f, 1.2f);
        tell(ad, now, "There it is: the way out.");
    }

    // ---- no door -----------------------------------------------------------------------------------------------------

    /** The way in closes behind you. */
    static void seal(ActiveDream ad, Course.Room r) {
        ad.roomSealed = true;
        BlockState stone = LucidiumContent.SHIFTING_STONE.get().defaultBlockState();
        for (BlockPos p : r.things()) if (dev.awaken.core.api.Ready.loaded(ad.level, p)) ad.level.setBlock(p, stone, ActiveDream.UPDATE);
        ad.level.playSound(null, r.things().getFirst(), LucidiumContent.DOOR.get(), SoundSource.AMBIENT, 0.8f, 0.6f);
    }

    /** A draft through the right wall's cracks: pearl motes drifting in. */
    static void draft(ActiveDream ad) {
        Course.Room r = ad.bp.course.room();
        if (r == null || r.walls().isEmpty()) return;
        AABB w = r.walls().get(r.right());
        var random = ad.level.getRandom();
        double x = w.minX + random.nextDouble() * w.getXsize(), y = w.minY + random.nextDouble() * w.getYsize(), z = w.minZ + random.nextDouble() * w.getZsize();
        Vec3 in = Vec3.atCenterOf(r.centre()).subtract(x, y, z).normalize().scale(0.04);
        ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), x, y, z, 0, in.x, in.y, in.z, 1);
    }

    /** H looking at a wall: a door in it (the right one leads out; the others only into more wall). */
    static boolean makeDoor(ServerPlayer sp, ActiveDream ad, Course.Room r, long now) {
        Vec3 eye = sp.getEyePosition(), to = eye.add(sp.getLookAngle().scale(14));
        int hit = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < r.walls().size(); i++) {
            Optional<Vec3> at = r.walls().get(i).clip(eye, to);
            if (at.isPresent() && at.get().distanceToSqr(eye) < best) {
                best = at.get().distanceToSqr(eye);
                hit = i;
            }
        }
        if (hit < 0) return false;
        Course c = ad.bp.course;
        DreamState st = Dreams.state(sp);
        if ((ad.roomFound & 1 << hit) != 0) {
            Trials.hud(sp, st, c, now, "There's a door there already.", false);
            return true;
        }
        ad.roomFound |= 1 << hit;
        AABB wall = r.walls().get(hit);
        for (BlockPos p : RoomLayouts.opening(wall)) if (dev.awaken.core.api.Ready.loaded(ad.level, p)) ad.level.setBlock(p, Blocks.AIR.defaultBlockState(), ActiveDream.UPDATE);
        for (BlockPos p : RoomLayouts.frame(wall)) if (dev.awaken.core.api.Ready.loaded(ad.level, p)) ad.level.setBlock(p, LucidiumContent.WAKING_DOOR.get().defaultBlockState(), ActiveDream.UPDATE);
        sparkle(ad, wall.getCenter());
        Dreams.playTo(sp, LucidiumContent.MANIFEST.get(), 0.9f, 1.0f);
        if (hit == r.right()) {
            ad.roomOpen = true;
            tell(ad, now, "There's your door.");
        } else Trials.hud(sp, st, c, now, "A door into a wall. Try another wall.", false);
        return true;
    }

    // ---- what's wrong here -------------------------------------------------------------------------------------------

    /** Where a wrong thing is looked at (a door between its two halves). */
    static Vec3 thingAt(Course.Room r, int i) {
        Vec3 c = Vec3.atCenterOf(r.things().get(i));
        return i == RoomLayouts.DOOR ? c.add(0, 0.5, 0) : c;
    }

    /** H: the wrong thing you're looking at is found; a guess at nothing wrong costs 5 s; eyes on the sky is the hint. */
    static boolean guess(ServerPlayer sp, DreamState st, ActiveDream ad, Course.Room r, long now) {
        if (sp.getXRot() < -45) return false;
        Course c = ad.bp.course;
        int best = -1;
        double bestDot = AIM;
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        for (int i = 0; i < r.things().size(); i++) {
            if ((ad.roomFound & 1 << i) != 0) continue;
            Vec3 to = thingAt(r, i).subtract(eye);
            double dist = to.length();
            if (dist > 40) continue;
            double dot = dist < 1.5 ? 1 : look.dot(to.scale(1 / dist));
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        if (best < 0) {
            st.clockEnd -= WRONG_COST;
            Dreams.playTo(sp, LucidiumContent.REALITY_CHECK.get(), 0.6f, 0.6f);
            Trials.hud(sp, st, c, now, "Nothing wrong there. 5 seconds gone.", false);
            return true;
        }
        ad.roomFound |= 1 << best;
        BlockPos p = r.things().get(best);
        int high = best == RoomLayouts.FLOATING ? 0 : best == RoomLayouts.DOOR ? 1 : 2;
        for (int h = 0; h <= high; h++)
            if (dev.awaken.core.api.Ready.loaded(ad.level, p.above(h))) ad.level.setBlock(p.above(h), Blocks.AIR.defaultBlockState(), ActiveDream.UPDATE);
        sparkle(ad, thingAt(r, best));
        Dreams.playTo(sp, LucidiumContent.SIGN.get(), 0.9f, 1.2f);
        int left = r.things().size() - Integer.bitCount(ad.roomFound);
        if (left > 0) tell(ad, now, left == 2 ? "That's wrong. Two more." : "That's wrong. One more.");
        else if (bell(ad)) {
            moveGoal(ad, r.centre().offset(0, 0, -(r.half() - 3)));
            tell(ad, now, "That's all three. Now there's a bell where there wasn't one.");
        } else won(ad, now);
        return true;
    }

    // ---- the chase ---------------------------------------------------------------------------------------------------

    /** Your fear, behind you once you're out of the doorway; the first carries the Nightmare's Bell when it's this dream's. */
    static void chase(ActiveDream ad, List<ServerPlayer> players, long now) {
        if (!ad.roomSpawned) {
            ServerPlayer lead = null;
            for (ServerPlayer p : players) if (Dreams.state(p).phase == 1 && Dreams.state(p).runStart > 0) lead = p;
            if (lead == null) return;
            ad.roomSpawned = true;
            Nightmares.Family f = fear(ad, lead);
            int rank = Progression.rank(lead).ordinal();
            int lv = Math.max(1, (int) (Progression.contentLevel(Progression.rank(lead)) * 0.8));
            int count = 2 + (rank >= dev.awaken.progression.Rank.C.ordinal() ? 1 : 0);
            Set<Forces.Tag> tags = switch (f) {
                case DROWNING -> Set.of(Forces.Tag.WATER);
                case EXPLOSION -> Set.of(Forces.Tag.VOLATILE);
                case UNDEAD -> Set.of(Forces.Tag.HAUNTED);
                default -> Set.of();
            };
            Vec3 s = ad.bp.course.start();
            for (int k = 0; k < count; k++) {
                BlockPos at = BlockPos.containing(s.x + (k - 1), s.y, s.z + 1);
                LivingEntity mob = MobHooks.spawnThemed(ad.level, f.theme(), MobRole.Role.NIGHTMARE, at, lv, tags, "");
                if (mob == null) continue;
                ad.roomBeasts.add(mob.getUUID());
                if (mob instanceof Mob m) m.setTarget(lead);
                if (k == 0 && bell(ad)) ad.roomCarrier = mob.getUUID();
            }
            ad.level.playSound(null, BlockPos.containing(s), LucidiumContent.NIGHTMARE.get(), SoundSource.HOSTILE, 1f, 0.8f);
            DirectorLog.log(lead, "dream", "chase: " + ad.roomBeasts.size() + " " + f.id + " behind you");
            return;
        }
        // whoever falls behind still hunts someone
        for (UUID id : ad.roomBeasts) {
            if (!(ad.level.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            if (m.getTarget() == null || !m.getTarget().isAlive()) {
                ServerPlayer near = null;
                for (ServerPlayer p : players) if (near == null || p.distanceToSqr(m) < near.distanceToSqr(m)) near = p;
                if (near != null) m.setTarget(near);
            }
        }
        // the carrier fell: the bell lies where it was
        if (ad.roomCarrier != null) {
            Entity e = ad.level.getEntity(ad.roomCarrier);
            if (e != null && e.isAlive()) {
                ad.roomCarrierAt = e.blockPosition();
                return;
            }
            ad.roomCarrier = null;
            BlockPos where = ad.roomCarrierAt != null ? ad.standingSpot(ad.roomCarrierAt) : null;
            if (where == null) {
                Course.Ring ring = ad.bp.course.ring(players.isEmpty() ? 0 : Dreams.state(players.getFirst()).ring);
                where = BlockPos.containing(ring.at()).offset(0, 0, 2);
            }
            moveGoal(ad, where);
            tell(ad, now, "It dropped the bell.");
        }
    }

    /** The fear behind you: the family this dream was for, the week's worst, or something hunting you in the dark. */
    static Nightmares.Family fear(ActiveDream ad, ServerPlayer p) {
        String ref = ad.bp.plan.trialRef();
        if (ref.startsWith("fear:")) return Nightmares.Family.byId(ref.substring(5)).orElse(Nightmares.Family.GENERIC);
        return Nightmares.top(Nightmares.scores(java.util.Map.copyOf(Progression.profile(p).counters()),
                Dreams.dreamer(p).weeklyMarks(dev.awaken.core.PlayDays.current(p.level().getServer())))).orElse(Nightmares.Family.GENERIC);
    }

    // ---- solving (tours, tests) ---------------------------------------------------------------------------------------

    /** One step of the rule, the way a player would take it ({@link DreamOffers#solve}). */
    static boolean solve(ServerPlayer sp, ActiveDream ad, long now) {
        DreamState st = Dreams.state(sp);
        if (st.phase == 0) Trials.takeControl(sp, st, ad, now);
        if (st.phase != 1) return false;
        Course c = ad.bp.course;
        Course.Room r = c.room();
        switch (c.trial()) {
            case WRONG -> {
                if (r == null) return false;
                for (int i = 0; i < r.things().size(); i++) {
                    if ((ad.roomFound & 1 << i) != 0) continue;
                    Vec3 at = thingAt(r, i);
                    // two blocks to the side of it, looking at it
                    Vec3 out = at.subtract(Vec3.atBottomCenterOf(r.centre())).multiply(1, 0, 1).normalize();
                    Vec3 feet = new Vec3(at.x - out.z * 2, r.centre().getY(), at.z + out.x * 2);
                    lookFrom(sp, feet, at);
                    return guess(sp, st, ad, r, now);
                }
            }
            case NO_DOOR -> {
                if (r == null || ad.roomOpen) break;
                Vec3 wall = r.walls().get(r.right()).getCenter();
                lookFrom(sp, Vec3.atBottomCenterOf(r.centre()), new Vec3(wall.x, r.centre().getY() + 1.6, wall.z));
                return makeDoor(sp, ad, r, now);
            }
            case FOLD -> {
                if (r == null || ad.roomOpen) break;
                BlockPos door = r.things().getFirst();
                Dreams.moveWithin(sp, new Vec3(r.centre().getX() + 0.5, r.edgeY(), door.getZ() + 0.5), 180f, 0f);
                openFold(ad, r, now);
                return true;
            }
            case LONG_FALL -> {
                if (r == null || ad.roomLanded) break;
                Dreams.moveWithin(sp, new Vec3(r.centre().getX() + 0.5, r.edgeY() - 24, r.centre().getZ() + 6.5), 0f, -20f);
                land(ad, sp, r, now);
                return true;
            }
            case DUEL -> {
                if (ad.mentorState == -1 && r != null) {
                    Dreams.moveWithin(sp, Vec3.atBottomCenterOf(r.centre()), 0f, 0f);
                    Mentors.start(ad, r.centre(), sp, true);
                    return true;
                }
                if (ad.mentorState == 1) {
                    Mentors.end(ad, sp, true);
                    return true;
                }
                return false;
            }
            case CHASE -> {
                if (ad.roomCarrier != null && ad.level.getEntity(ad.roomCarrier) instanceof LivingEntity carrier) {
                    carrier.kill(ad.level);
                    return true;
                }
            }
            // a step that lands you by the sleeper is a sneak's step: it stays asleep while you take the next one
            case HUSH -> ad.roomQuietUntil = now + 100;
            default -> {}
        }
        // the rule's done (or has nothing to do): into the door, or two steps from the bell looking at it (strike it)
        Vec3 end = Trials.end(c, ad);
        if (end == null) return false;
        if (Trials.bell(c, ad) && ad.roomGoal != null) lookFrom(sp, end.add(0, 0, -2), end.add(0, 0.5, 0));
        else Dreams.moveWithin(sp, end, 0f, 10f);
        return true;
    }

    /** Stand at {@code feet} looking at {@code at}. */
    static void lookFrom(ServerPlayer sp, Vec3 feet, Vec3 at) {
        double dx = at.x - feet.x, dy = at.y - (feet.y + sp.getEyeHeight()), dz = at.z - feet.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        Dreams.moveWithin(sp, feet, yaw, pitch);
    }

    // ---- the duel ----------------------------------------------------------------------------------------------------

    /** Step into the ring and your mentor is there. */
    static void duel(ActiveDream ad, List<ServerPlayer> players) {
        Course.Room r = ad.bp.course.room();
        if (r == null) return;
        for (ServerPlayer p : players) {
            if (Dreams.state(p).phase != 1 || p.distanceToSqr(Vec3.atBottomCenterOf(r.centre())) > 8 * 8) continue;
            Mentors.start(ad, r.centre(), p, true);
            return;
        }
    }
}
