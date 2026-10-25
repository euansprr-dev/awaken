package dev.awaken.gates.run;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.TrialHooks;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.RigState;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.world.RigGauntlet;
import dev.awaken.gates.world.RigRoom;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Gauntlet while it plays (SPEC §17.5 "Gauntlet"): each player's run of the course (the clock from the first step off
 * the landing, the rings passed, the far landing and its medal), the pit throwing whoever falls back to their last ring,
 * and the presses landing (whoever is under one when it lands is thrown back too).
 */
final class RigGauntletLive {
    private RigGauntletLive() {}

    /** One player's run. {@code armed}: stood on the start landing since the last run (a run starts from there). */
    static final class Racer {
        boolean armed, running, edged;
        long start;
        int ring = -1;
    }

    /** A press on its way down: its blocks land at {@code at}. */
    record Press(long at, int group) {}

    /** Ticks in the pit before it throws you out, and what it costs; what a press costs. */
    static final int PIT_GRACE = 4;
    static final float PIT_DAMAGE = 3F, PIT_SHARE = 0.08F, PRESS_DAMAGE = 4F, PRESS_SHARE = 0.12F;
    /** A medal against the course's par: gold within 10% (and a second), silver within half again. */
    static int medal(int ticks, int par) {
        if (ticks <= par * 1.1 + 20) return RigPayloads.Run.GOLD;
        if (ticks <= par * 1.5 + 40) return RigPayloads.Run.SILVER;
        return RigPayloads.Run.BRONZE;
    }

    /** How far a press travels out of the ceiling: the room's height less the press. */
    static float travel(RigDirector.Live l) {
        return Math.max(1, l.r.height - RigGauntlet.PRESS_H);
    }

    // ---- presses ----------------------------------------------------------------------------------------------------

    /** A press is about to come down: the floor under it glows (the cells one under its foot) and it winds up. */
    static void warn(ServerLevel level, RigDirector.Live l, RigRoom.Group g) {
        List<Long> cells = new ArrayList<>();
        for (RigRoom.Cell c : g.cells()) if (c.dy() == 0) cells.add(l.world(c.x(), -1, c.z()).asLong());
        RigDirector.send(level, l, new RigPayloads.Cue(RigPayloads.Cue.HAZARD, l.f.theme.ordinal(), cells, List.of(),
                RigGauntlet.PRESS_WARN, 0));
        RigDirector.sound(level, RigDirector.at(l, g), GatesRegistry.SND_RIG_WINDUP.get(), 0.9F, 1F);
    }

    /** A press lets go: the clients draw it coming down; its blocks land PRESS_TICKS on. */
    static void press(ServerLevel level, RigDirector.Live l, RigRoom.Group g, long now) {
        l.presses.add(new Press(now + RigGauntlet.PRESS_TICKS, g.id()));
        RigDirector.cue(level, l, RigPayloads.Cue.PRESS, g, false, RigGauntlet.PRESS_TICKS, travel(l));
    }

    /** Presses whose time has come land: anyone under one is thrown back, then its blocks are placed. */
    private static void land(ServerLevel level, RigDirector.Live l, long now) {
        if (l.presses.isEmpty()) return;
        RigState.Room st = l.st();
        for (int i = l.presses.size() - 1; i >= 0; i--) {
            Press p = l.presses.get(i);
            if (p.at > now) continue;
            l.presses.remove(i);
            // put back before it landed (the room emptied): nothing comes down
            if (!st.applied.contains(p.group)) continue;
            RigRoom.Group g = l.rig.group(p.group);
            AABB box = null;
            for (RigRoom.Cell c : g.cells()) {
                AABB b = new AABB(l.world(c.x(), c.dy(), c.z()));
                box = box == null ? b : box.minmax(b);
            }
            if (box != null) {
                for (ServerPlayer pl : level.getEntitiesOfClass(ServerPlayer.class, box.deflate(0.05))) {
                    if (pl.isSpectator()) continue;
                    pl.hurtServer(level, level.damageSources().source(RigCollapseLive.RUBBLE), PRESS_DAMAGE + pl.getMaxHealth() * PRESS_SHARE);
                    back(level, l, pl, "The press throws you back.");
                }
            }
            for (RigRoom.Cell c : g.cells()) {
                BlockPos pos = l.world(c.x(), c.dy(), c.z());
                if (level.getBlockState(pos) != c.active()) level.setBlock(pos, c.active(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            RigDirector.sound(level, RigDirector.at(l, g), GatesRegistry.SND_RIG_PRESS.get(), 1.1F,
                    l.f.theme == dev.awaken.gates.content.Theme.FROST_MONASTERY ? 1.25F : 0.95F + 0.1F * level.getRandom().nextFloat());
            // dust off the floor where it lands
            List<Long> foot = new ArrayList<>();
            for (RigRoom.Cell c : g.cells()) if (c.dy() == 0 && ((c.x() + c.z()) & 1) == 0) foot.add(l.world(c.x(), 0, c.z()).asLong());
            RigDirector.send(level, l, new RigPayloads.Cue(RigPayloads.Cue.PIT, l.f.theme.ordinal(), foot, List.of(), 10, 0));
        }
    }

    /** Every press on its way down lands now (tests). */
    static void landNow(ServerLevel level, RigDirector.Live l) {
        if (l.presses.isEmpty()) return;
        List<Press> all = new ArrayList<>(l.presses);
        l.presses.clear();
        for (Press p : all) l.presses.add(new Press(Long.MIN_VALUE, p.group()));
        land(level, l, Long.MAX_VALUE);
    }

    // ---- the runs ---------------------------------------------------------------------------------------------------

    static void tick(ServerLevel level, RigDirector.Live l, long now) {
        land(level, l, now);
        RigGauntlet.Course c = l.rig.course;
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : l.here) {
            here.add(p.getUUID());
            double[] q = l.local(p.position());
            Racer r = l.racers.computeIfAbsent(p.getUUID(), u -> new Racer());
            int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[2]);
            int row = q[1] > -0.6 && q[1] < 3 ? c.rowAt(x, z) : -1;
            if (row >= 0 && row < c.first()) {
                // back on the start landing: a run in progress is given up, the next step off starts a new one
                if (r.running) stop(p, r);
                r.armed = true;
            }
            if (row == c.first() && r.armed && !r.running) start(level, l, p, r, now);
            if (r.running && row >= 0) {
                for (int i = r.ring + 1; i < c.rings().length; i++) {
                    if (row < c.rings()[i]) break;
                    r.ring = i;
                    AwakenNet.send(p, new RigPayloads.Run(RigPayloads.Run.RING, (int) (now - r.start), i, c.rings().length, 0, c.par()));
                    level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), GatesRegistry.SND_RIG_RING.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                            0.9F, 1F + 0.06F * i);
                }
                if (row >= c.last()) finish(level, l, p, r, now);
            }
            // a trial's edge: the movement module gives what crosses the chasm (once a player, the first time with its moment)
            if (c.trial() && row == c.edge() && !r.edged) {
                r.edged = true;
                TrialHooks.atEdge(p, TrialHooks.LEAPER);
            }
            // the pit: a few ticks in it and it throws you back to your last ring
            boolean in = q[1] < -1.4 && q[0] >= c.x0() && q[0] < c.x0() + c.w() && q[2] >= c.z0() && q[2] < c.z0() + c.d();
            if (!in) {
                l.inPit.remove(p.getUUID());
                continue;
            }
            if (l.inPit.merge(p.getUUID(), 1, Integer::sum) >= PIT_GRACE) {
                l.inPit.remove(p.getUUID());
                p.hurtServer(level, level.damageSources().source(RigCollapseLive.RUBBLE), PIT_DAMAGE + p.getMaxHealth() * PIT_SHARE);
                back(level, l, p, "The pit throws you back.");
            }
        }
        // someone who left the room mid-run has given it up
        l.racers.entrySet().removeIf(e -> {
            if (here.contains(e.getKey())) return false;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && e.getValue().running) stop(p, e.getValue());
            return true;
        });
    }

    private static void start(ServerLevel level, RigDirector.Live l, ServerPlayer p, Racer r, long now) {
        RigGauntlet.Course c = l.rig.course;
        boolean alone = true;
        for (Racer o : l.racers.values()) if (o != r && o.running) alone = false;
        // the presses keep the beat the course's par was set to: a fresh run alone starts them fresh
        if (alone && c.presses() > 0) {
            if (l.running(0)) RigDirector.stop(level, l, 0, now, true);
            RigDirector.start(level, l, 0, now);
        }
        r.running = true;
        r.armed = false;
        r.start = now;
        r.ring = -1;
        AwakenNet.send(p, new RigPayloads.Run(RigPayloads.Run.START, 0, -1, c.rings().length, 0, c.par()));
        RigDirector.sound(level, p.position(), GatesRegistry.SND_RIG_RUN.get(), 0.8F, 1.3F);
    }

    private static void stop(ServerPlayer p, Racer r) {
        r.running = false;
        AwakenNet.send(p, new RigPayloads.Run(RigPayloads.Run.STOP, 0, r.ring, 0, 0, 0));
    }

    private static void finish(ServerLevel level, RigDirector.Live l, ServerPlayer p, Racer r, long now) {
        RigGauntlet.Course c = l.rig.course;
        int ticks = (int) (now - r.start), medal = medal(ticks, c.par());
        r.running = false;
        AwakenNet.send(p, new RigPayloads.Run(RigPayloads.Run.FINISH, ticks, r.ring, c.rings().length, medal, c.par()));
        RigDirector.sound(level, p.position(), GatesRegistry.SND_RIG_MEDAL.get(), 1F, medal == RigPayloads.Run.GOLD ? 1.12F
                : medal == RigPayloads.Run.SILVER ? 1F : 0.89F);
        Progression.count(p, "awaken:gauntlet_runs");
        if (medal == RigPayloads.Run.GOLD) Progression.count(p, "awaken:gauntlet_gold");
        Progression.worldXp(p, "awaken:gauntlet", medal == RigPayloads.Run.GOLD ? 0.06 : medal == RigPayloads.Run.SILVER ? 0.035 : 0.02);
        DirectorLog.log(p, "gate", String.format("ran a Gauntlet in %.1f s (par %.1f s): %s", ticks / 20.0, c.par() / 20.0,
                medal == RigPayloads.Run.GOLD ? "gold" : medal == RigPayloads.Run.SILVER ? "silver" : "bronze"));
        if (c.trial()) trialRun(level, l, p, ticks, medal);
    }

    /**
     * The trial run to its far landing: the movement module gives the leap's scene, and the gate is done: the way out
     * opens in the room past the course (the gate's exit, as after a Lord), and it comes down once everyone is out.
     */
    private static void trialRun(ServerLevel level, RigDirector.Live l, ServerPlayer p, int ticks, int medal) {
        TrialHooks.done(p, TrialHooks.LEAPER, ticks, medal);
        var g = l.g;
        if (g.bossDead) return;
        g.bossDead = true;
        g.clearedAt = dev.awaken.gates.GateService.now(level.getServer());
        dev.awaken.gates.GateService.setState(level.getServer(), g, dev.awaken.gates.GateState.CLEARED);
        dev.awaken.gates.world.Room past = l.f.room(l.f.end);
        Twists.placePortal(level, g, l.f, past, past.feature(dev.awaken.gates.world.Room.FeatureKind.EXIT_ANCHOR),
                GatesRegistry.EXIT.get().defaultBlockState());
        dev.awaken.gates.GateRegistry.get(level.getServer()).setDirty();
    }

    /** Back to your last ring (the start landing before the first), facing the way on, with the pit's roar. */
    private static void back(ServerLevel level, RigDirector.Live l, ServerPlayer p, String why) {
        RigGauntlet.Course c = l.rig.course;
        Racer r = l.racers.get(p.getUUID());
        double[] at = c.ringAt(r == null || !r.running ? -1 : r.ring);
        BlockPos o = l.world(0, 0, 0);
        Vec3 to = new Vec3(o.getX() + at[0], o.getY(), o.getZ() + at[1]);
        float yaw = (float) Math.toDegrees(Math.atan2(-(at[2] - at[0]), at[3] - at[1]));
        p.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, 10F, true);
        p.resetFallDistance();
        RigDirector.sound(level, to, GatesRegistry.SND_RIG_PIT.get(), 1F, 1.1F);
        RigDirector.send(level, l, new RigPayloads.Cue(RigPayloads.Cue.PIT, l.f.theme.ordinal(), List.of(BlockPos.containing(to).asLong()),
                List.of(), 20, 0));
        Signal.toast(p, Component.literal(why));
    }
}
