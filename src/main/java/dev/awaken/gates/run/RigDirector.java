package dev.awaken.gates.run;

import dev.awaken.core.AwakenNet;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.RigState;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.RigMirrors;
import dev.awaken.gates.world.RigRoom;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gate Rig's director (SPEC §17 "Gate Rig"): runs the timelines of the rig rooms people are in, like the Ritual's
 * director runs its hall. Every tick it sees who stands in which rig room, fires the triggers, plays each running
 * track's beats by elapsed time (everything between two ticks fires in order, so lag never skips a fall), swaps blocks
 * between their painted and active states and tells the clients what to draw. What it has done is saved in the floor's
 * {@link RigState}; a room nobody is left in puts back what its resetting tracks changed. Server thread only.
 */
public final class RigDirector {
    private RigDirector() {}

    record Key(int gate, int area, int floor, int room) {}

    /** A delayed swap: the client draws the motion first, then the real block appears ({@link RigRoom#RISE_TICKS}). */
    record Pending(long at, BlockPos pos, BlockState state, int group, int tries) {}

    /** A rig room being run. Not saved: rebuilt from the floor's saved state and the world. */
    static final class Live {
        final GateRecord g;
        final FloorLayout f;
        final Room r;
        final RigRoom rig;
        final List<ServerPlayer> here = new ArrayList<>();
        final List<Pending> pending = new ArrayList<>();
        /** Mirror Hall: the light reached the seal this tick; who last turned a mirror (they get the burns' credit). */
        boolean beamOn;
        UUID turner;
        /** Collapse Run: ticks each player has spent in the pit. */
        final Map<UUID, Integer> inPit = new HashMap<>();
        /** Gravity Wells: who is falling up right now, and who just came down (soft landing until then). */
        final Set<UUID> flipped = new HashSet<>();
        final Map<UUID, Long> landing = new HashMap<>();
        /** Gauntlet: each player's run of the course, and the presses on their way down (placed when they land). */
        final Map<UUID, RigGauntletLive.Racer> racers = new HashMap<>();
        final List<RigGauntletLive.Press> presses = new ArrayList<>();
        boolean encounter;

        Live(GateRecord g, FloorLayout f, Room r) {
            this.g = g;
            this.f = f;
            this.r = r;
            this.rig = r.rig;
        }

        FloorState fs() {
            return g.floor(f.area, f.floor);
        }

        RigState.Room st() {
            return fs().rig.room(r.id);
        }

        BlockPos world(int x, int dy, int z) {
            return GateService.world(g, f, x, r.dy + dy, z);
        }

        /** A position in this room's floor-local frame: x, rows above the room's floor, z. */
        double[] local(Vec3 p) {
            BlockPos o = world(0, 0, 0);
            return new double[]{p.x - o.getX(), p.y - o.getY(), p.z - o.getZ()};
        }

        Vec3 center() {
            BlockPos c = world(r.centerX(), 1, r.centerZ());
            return Vec3.atCenterOf(c);
        }

        AABB box() {
            BlockPos a = world(r.x0(), -4, r.z0()), b = world(r.x1(), r.height + 1, r.z1());
            return new AABB(a.getX(), a.getY(), a.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1);
        }

        boolean running(int track) {
            return st().started.containsKey(track);
        }

        boolean idle() {
            return here.isEmpty() && st().started.isEmpty() && pending.isEmpty() && flipped.isEmpty() && inPit.isEmpty() && landing.isEmpty()
                    && racers.isEmpty() && presses.isEmpty();
        }
    }

    private static final Map<Key, Live> LIVE = new HashMap<>();
    private static boolean scanned;

    /** Server start/stop: live rooms belong to the old world. */
    public static void resetAll() {
        LIVE.clear();
        scanned = false;
        RigCaveIn.resetAll();
        RigMirrorsLive.resetAll();
        RigGauntletLive.resetAll();
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        ServerLevel level = GateService.gateLevel(server);
        if (level == null) return;
        long now = GateService.now(server);
        if (!scanned) {
            scanned = true;
            scanSaved(server);
        }
        for (Live l : LIVE.values()) l.here.clear();
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Where w = Where.at(server, p.blockPosition());
            if (w == null || w.room() == null || w.room().rig == null) continue;
            live(w.gate(), w.floor(), w.room()).here.add(p);
        }
        if (!LIVE.isEmpty()) {
            for (Live l : List.copyOf(LIVE.values())) {
                if (l.g.state == GateState.RELEASED) {
                    LIVE.values().remove(l);
                    continue;
                }
                tickRoom(level, l, now);
            }
            LIVE.values().removeIf(Live::idle);
        }
        RigCaveIn.tick(level, now);
    }

    private static Live live(GateRecord g, FloorLayout f, Room r) {
        return LIVE.computeIfAbsent(new Key(g.id, f.area, f.floor, r.id), k -> new Live(g, f, r));
    }

    /** After a start: rooms saved mid-track come back to life so they can finish or put themselves back. */
    private static void scanSaved(MinecraftServer server) {
        for (GateRecord g : GateRegistry.get(server).all()) {
            if (g.state == GateState.RELEASED) continue;
            GateLayout l = GateLayout.of(g.plan);
            for (var e : g.floors.entrySet()) {
                if (e.getValue().rig.rooms.isEmpty()) continue;
                String[] af = e.getKey().split(":");
                FloorLayout f = l.floor(Integer.parseInt(af[0]), Integer.parseInt(af[1]));
                if (f == null) continue;
                e.getValue().rig.rooms.forEach((id, st) -> {
                    Room r = f.room(id);
                    if (r != null && r.rig != null && !st.idle()) live(g, f, r);
                });
            }
        }
    }

    private static void tickRoom(ServerLevel level, Live l, long now) {
        RigState.Room st = l.st();
        l.encounter = l.fs().triggered.contains(l.r.id) && !l.fs().clearedRooms.contains(l.r.id)
                && GateMobs.alive(new GateMobs.Key(l.g.id, l.f.area, l.f.floor, l.r.id)) > 0;
        if (l.rig.kind == RigRoom.Kind.MIRROR_HALL) RigMirrorsLive.tick(level, l, now);
        triggers(level, l, now);
        for (int id : List.copyOf(st.started.keySet())) advance(level, l, id, now);
        // a room nobody is left in puts back what it changed; a fight's rhythm stops with the fight
        for (int id : List.copyOf(st.started.keySet())) {
            RigRoom.Track t = l.rig.track(id);
            boolean fight = startedBy(l.rig, id, RigRoom.On.ENCOUNTER);
            if (t.empty() == RigRoom.Empty.RESET && (l.here.isEmpty() || (fight && !l.encounter))) stop(level, l, id, now, true);
        }
        switch (l.rig.kind) {
            case COLLAPSE_RUN -> RigCollapseLive.tick(level, l, now);
            case GRAVITY_WELLS -> RigWellsLive.tick(level, l, now);
            case GAUNTLET -> RigGauntletLive.tick(level, l, now);
            case MIRROR_HALL -> {}
        }
        settle(level, l, now);
    }

    private static boolean startedBy(RigRoom rig, int track, RigRoom.On on) {
        for (RigRoom.Trigger t : rig.triggers) if (t.track() == track && t.on() == on) return true;
        return false;
    }

    // ---- triggers ---------------------------------------------------------------------------------------------------

    private static void triggers(ServerLevel level, Live l, long now) {
        RigState.Room st = l.st();
        List<RigRoom.Trigger> list = l.rig.triggers;
        for (int i = 0; i < list.size(); i++) {
            RigRoom.Trigger t = list.get(i);
            if (st.latched.contains(i) || st.started.containsKey(t.track()) || now < st.ready.getOrDefault(i, Long.MIN_VALUE)) continue;
            boolean fire = switch (t.on()) {
                case ZONE, PLATE -> anyIn(l, t.box());
                case BEAM -> l.beamOn;
                case ENCOUNTER -> l.encounter && !l.here.isEmpty();
            };
            if (!fire) continue;
            if (t.once()) st.latched.add(i);
            start(level, l, t.track(), now);
        }
    }

    private static boolean anyIn(Live l, RigRoom.Box box) {
        for (ServerPlayer p : l.here) {
            double[] q = l.local(p.position());
            if (box.contains(q[0], q[1], q[2])) return true;
        }
        return false;
    }

    static void start(ServerLevel level, Live l, int track, long now) {
        RigState.Room st = l.st();
        st.started.put(track, now);
        st.age.put(track, -1);
        dirty(level);
        if (l.rig.kind == RigRoom.Kind.COLLAPSE_RUN) RigCollapseLive.started(level, l);
        advance(level, l, track, now);
    }

    // ---- tracks -----------------------------------------------------------------------------------------------------

    /** Plays a track's beats between its last tick and now, in order, carrying over into its next loops if time jumped. */
    private static void advance(ServerLevel level, Live l, int id, long now) {
        RigState.Room st = l.st();
        RigRoom.Track t = l.rig.track(id);
        for (int guard = 0; guard < 8 && st.started.containsKey(id); guard++) {
            long age = now - st.started.get(id);
            int from = st.age.getOrDefault(id, -1);
            int to = (int) Math.min(age, t.length());
            if (to > from) {
                for (RigRoom.Beat b : t.beats()) if (b.at() > from && b.at() <= to) beat(level, l, b, now);
                st.age.put(id, to);
                dirty(level);
            }
            if (age < t.length()) return;
            if (t.loop()) {
                st.started.put(id, st.started.get(id) + t.length());
                st.age.put(id, -1);
            } else {
                stop(level, l, id, now, false);
                return;
            }
        }
    }

    /** Ends a track; {@code reset}: put back every group it changed (a room left mid-run). */
    static void stop(ServerLevel level, Live l, int id, long now, boolean reset) {
        RigState.Room st = l.st();
        st.started.remove(id);
        st.age.remove(id);
        List<RigRoom.Trigger> list = l.rig.triggers;
        for (int i = 0; i < list.size(); i++) if (list.get(i).track() == id) st.ready.put(i, now + list.get(i).cooldown());
        if (reset) {
            Set<Integer> groups = new HashSet<>();
            for (RigRoom.Beat b : l.rig.track(id).beats()) groups.add(b.group());
            for (int g : groups) if (st.applied.contains(g)) restore(level, l, l.rig.group(g), now);
        }
        dirty(level);
    }

    private static void beat(ServerLevel level, Live l, RigRoom.Beat b, long now) {
        RigRoom.Group g = l.rig.group(b.group());
        switch (b.cue()) {
            case WARN -> warn(level, l, g);
            case APPLY -> apply(level, l, g, now);
            case RESTORE -> restore(level, l, g, now);
        }
    }

    // ---- swaps ------------------------------------------------------------------------------------------------------

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static void warn(ServerLevel level, Live l, RigRoom.Group g) {
        if (g.apply() == RigRoom.Anim.PRESS) {
            // a press about to come down: the floor under it glows and the ratchet winds
            RigGauntletLive.warn(level, l, g);
            return;
        }
        cue(level, l, RigPayloads.Cue.WARN, g, false, warnTicks(l.rig.kind), 0);
        switch (l.rig.kind) {
            case COLLAPSE_RUN -> sound(level, at(l, g), GatesRegistry.SND_RIG_CRACK.get(), 0.7F, 0.9F + 0.2F * level.getRandom().nextFloat());
            case GRAVITY_WELLS -> sound(level, l.center(), GatesRegistry.SND_RIG_SURGE.get(), 1.2F, 1F);
            case MIRROR_HALL -> sound(level, at(l, g), GatesRegistry.SND_RIG_CRACK.get(), 0.6F, 1.6F);
            case GAUNTLET -> sound(level, at(l, g), GatesRegistry.SND_RIG_CRACK.get(), 0.8F, 1.1F + 0.2F * level.getRandom().nextFloat());
        }
    }

    private static int warnTicks(RigRoom.Kind kind) {
        return kind == RigRoom.Kind.GRAVITY_WELLS ? 30 : 10;
    }

    static void apply(ServerLevel level, Live l, RigRoom.Group g, long now) {
        RigState.Room st = l.st();
        if (!st.applied.add(g.id())) return;
        dirty(level);
        if (g.apply() == RigRoom.Anim.PRESS) {
            // drawn coming down first; its blocks land (and catch whoever is under them) a few ticks on
            RigGauntletLive.press(level, l, g, now);
            return;
        }
        for (RigRoom.Cell c : g.cells()) {
            BlockPos pos = l.world(c.x(), c.dy(), c.z());
            if (level.getBlockState(pos) != c.active()) level.setBlock(pos, c.active(), FLAGS);
        }
        cue(level, l, anim(g.apply()), g, true, animTicks(g.apply()), 0);
        switch (l.rig.kind) {
            case COLLAPSE_RUN -> sound(level, at(l, g), GatesRegistry.SND_RIG_FALL.get(), 0.9F, 0.85F + 0.3F * level.getRandom().nextFloat());
            case MIRROR_HALL -> sound(level, at(l, g), g.id() == 2 ? GatesRegistry.SND_RIG_SHATTER.get() : GatesRegistry.SND_RIG_IGNITE.get(),
                    1.2F, g.id() == 1 ? 1.25F : 1F);
            case GRAVITY_WELLS -> {
                cue(level, l, RigPayloads.Cue.SURGE, g, false, 0, 1);
                sound(level, l.center(), GatesRegistry.SND_RIG_FLIP_UP.get(), 1.4F, 0.8F);
            }
            case GAUNTLET -> sound(level, at(l, g), GatesRegistry.SND_RIG_FALL.get(), 0.8F, 1.0F + 0.2F * level.getRandom().nextFloat());
        }
    }

    static void restore(ServerLevel level, Live l, RigRoom.Group g, long now) {
        RigState.Room st = l.st();
        if (!st.applied.contains(g.id())) return;
        if (g.flag()) {
            st.applied.remove(g.id());
            dirty(level);
            if (l.rig.kind == RigRoom.Kind.GRAVITY_WELLS) {
                cue(level, l, RigPayloads.Cue.SURGE, g, false, 0, 0);
                sound(level, l.center(), GatesRegistry.SND_RIG_FLIP_DOWN.get(), 1.4F, 0.8F);
            }
            return;
        }
        boolean rise = g.restore() == RigRoom.Anim.RISE;
        for (RigRoom.Cell c : g.cells()) {
            BlockPos pos = l.world(c.x(), c.dy(), c.z());
            if (rise) l.pending.add(new Pending(now + RigRoom.RISE_TICKS, pos, c.rest(), g.id(), 0));
            else level.setBlock(pos, c.rest(), FLAGS);
        }
        if (!rise) st.applied.remove(g.id());
        boolean lift = g.restore() == RigRoom.Anim.LIFT;
        cue(level, l, anim(g.restore()), g, false, animTicks(g.restore()), lift ? RigGauntletLive.travel(l) : 0);
        if (l.rig.kind == RigRoom.Kind.COLLAPSE_RUN || (l.rig.kind == RigRoom.Kind.GAUNTLET && !lift))
            sound(level, at(l, g), GatesRegistry.SND_RIG_KNIT.get(), 0.6F, 0.9F + 0.2F * level.getRandom().nextFloat());
        if (lift) sound(level, at(l, g), GatesRegistry.SND_RIG_LIFT.get(), 0.8F, 1F);
        dirty(level);
    }

    /** Puts delayed blocks in once their motion has played; never inside a player (it waits for them to step off). */
    private static void settle(ServerLevel level, Live l, long now) {
        if (l.pending.isEmpty()) return;
        Set<Integer> touched = new HashSet<>();
        for (int i = l.pending.size() - 1; i >= 0; i--) {
            Pending p = l.pending.get(i);
            if (p.at() > now) continue;
            l.pending.remove(i);
            if (p.tries() < 200 && !level.getEntitiesOfClass(ServerPlayer.class, new AABB(p.pos())).isEmpty()) {
                l.pending.add(new Pending(now + 2, p.pos(), p.state(), p.group(), p.tries() + 1));
                continue;
            }
            level.setBlock(p.pos(), p.state(), FLAGS);
            if (p.group() >= 0) touched.add(p.group());
        }
        for (int g : touched) {
            boolean more = false;
            for (Pending p : l.pending) if (p.group() == g) more = true;
            if (!more && l.st().applied.remove(g)) dirty(level);
        }
    }

    private static int anim(RigRoom.Anim a) {
        return switch (a) {
            case FALL -> RigPayloads.Cue.FALL;
            case RISE -> RigPayloads.Cue.RISE;
            case SPIN -> RigPayloads.Cue.SPIN;
            case SHATTER -> RigPayloads.Cue.SHATTER;
            case IGNITE, NONE -> RigPayloads.Cue.IGNITE;
            case PRESS -> RigPayloads.Cue.PRESS;
            case LIFT -> RigPayloads.Cue.LIFT;
        };
    }

    private static int animTicks(RigRoom.Anim a) {
        return switch (a) {
            case FALL -> 22;
            case RISE -> RigRoom.RISE_TICKS;
            case SPIN -> RigRoom.SPIN_TICKS;
            case SHATTER -> 30;
            case IGNITE, NONE -> 20;
            case PRESS -> dev.awaken.gates.world.RigGauntlet.PRESS_TICKS;
            case LIFT -> 14;
        };
    }

    // ---- what the clients are told ----------------------------------------------------------------------------------

    /** Sends a cue for a group: its cells and what is drawn there ({@code rest}: the painted blocks, else the active ones). */
    static void cue(ServerLevel level, Live l, int kind, RigRoom.Group g, boolean rest, int ticks, float a) {
        List<Long> cells = new ArrayList<>(g.cells().size());
        List<Integer> states = new ArrayList<>(g.cells().size());
        // a flag (a surge) has no blocks: its cue names the room by its centre
        if (g.flag()) cells.add(BlockPos.containing(l.center()).asLong());
        for (RigRoom.Cell c : g.cells()) {
            cells.add(l.world(c.x(), c.dy(), c.z()).asLong());
            states.add(Block.getId(rest || kind == RigPayloads.Cue.RISE || kind == RigPayloads.Cue.WARN ? c.rest() : c.active()));
        }
        send(level, l, new RigPayloads.Cue(kind, l.f.theme.ordinal(), cells, states, ticks, a));
    }

    static void send(ServerLevel level, Live l, RigPayloads.Cue cue) {
        sendNear(level, l.center(), cue);
    }

    /** Everyone close enough to see it. */
    static void sendNear(ServerLevel level, Vec3 at, RigPayloads.Cue cue) {
        for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(at) < 96 * 96) AwakenNet.send(p, cue);
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.BLOCKS, volume, pitch);
    }

    /** The middle of a group's cells (where its sound comes from). */
    static Vec3 at(Live l, RigRoom.Group g) {
        if (g.cells().isEmpty()) return l.center();
        double x = 0, y = 0, z = 0;
        for (RigRoom.Cell c : g.cells()) {
            x += c.x();
            y += c.dy();
            z += c.z();
        }
        int n = g.cells().size();
        return Vec3.atCenterOf(l.world((int) Math.floor(x / n), (int) Math.floor(y / n), (int) Math.floor(z / n)));
    }

    static void dirty(ServerLevel level) {
        GateRegistry.get(level.getServer()).setDirty();
    }

    /**
     * Tells a player about the rig rooms of the floor they are on (arriving, logging in): where the light shines, where
     * the wells are, whether the room surges. An empty scene clears it.
     */
    public static void sendScene(ServerPlayer p) {
        RunState run = Runs.run(p);
        GateRecord g = run.active() ? GateRegistry.get(p.level().getServer()).gate(run.gateId) : null;
        FloorLayout f = g == null ? null : GateLayout.of(g.plan).floor(run.area, run.floor);
        if (f == null) {
            AwakenNet.send(p, RigPayloads.Scene.NONE);
            return;
        }
        List<RigPayloads.Room> rooms = new ArrayList<>();
        for (Room r : f.rooms) {
            if (r.rig == null) continue;
            Live l = new Live(g, f, r);
            BlockPos min = l.world(r.x0(), -4, r.z0()), max = l.world(r.x1(), r.height, r.z1());
            BlockPos lens = BlockPos.ZERO, seal = BlockPos.ZERO;
            int dir = 0;
            List<BlockPos> wells = new ArrayList<>();
            boolean surge = false;
            switch (r.rig.kind) {
                case MIRROR_HALL -> {
                    var pz = r.rig.mirrors;
                    lens = l.world(pz.lensX(), RigMirrors.BEAM_DY, pz.lensZ());
                    seal = l.world(pz.sealX(), RigMirrors.BEAM_DY, pz.sealZ());
                    dir = pz.lensDir();
                }
                case GRAVITY_WELLS -> {
                    for (int i = 0; i < r.rig.wells.wells(); i++) {
                        RigRoom.Box b = r.rig.wells.box(i);
                        wells.add(l.world(b.x0(), b.y0(), b.z0()));
                        wells.add(l.world(b.x1(), b.y1(), b.z1()));
                    }
                    RigState.Room st = g.floor(f.area, f.floor).rig.rooms.get(r.id);
                    surge = st != null && st.applied.contains(0);
                }
                case GAUNTLET -> {
                    // each ring as the two ends of the row it stands over
                    var c = r.rig.course;
                    for (int ring : c.rings()) {
                        BlockPos a = null, b = null;
                        for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) {
                            if (c.rowAt(x, z) != ring) continue;
                            BlockPos q = l.world(x, 0, z);
                            if (a == null) a = q;
                            b = q;
                        }
                        if (a != null) {
                            wells.add(a);
                            wells.add(b);
                        }
                    }
                }
                case COLLAPSE_RUN -> {}
            }
            rooms.add(new RigPayloads.Room(r.rig.kind.ordinal(), f.theme.ordinal(), min, max, lens, dir, seal, wells, surge));
        }
        AwakenNet.send(p, new RigPayloads.Scene(rooms));
    }

    // ---- players --------------------------------------------------------------------------------------------------

    /** A player left the gate: nothing of a rig room stays on them. */
    public static void leave(ServerPlayer p) {
        RigWellsLive.clear(p);
        for (Live l : LIVE.values()) {
            l.inPit.remove(p.getUUID());
            l.flipped.remove(p.getUUID());
            l.landing.remove(p.getUUID());
            l.racers.remove(p.getUUID());
        }
        AwakenNet.send(p, RigPayloads.Scene.NONE);
    }

    /** Turns the mirror at {@code pos} a quarter, if it stands in a Mirror Hall. */
    static void turnMirror(ServerPlayer p, BlockPos pos) {
        Where w = Where.at(p.level().getServer(), pos);
        if (w == null || w.room() == null || w.room().rig == null || w.room().rig.kind != RigRoom.Kind.MIRROR_HALL) return;
        Live l = live(w.gate(), w.floor(), w.room());
        RigMirrorsLive.turn((ServerLevel) p.level(), l, p, pos, GateService.now(p.level().getServer()));
    }

    // ---- tests and tours ------------------------------------------------------------------------------------------

    /** Runs the director for one tick now (tests drive time by moving their clock, not by waiting). */
    public static void tickNow(MinecraftServer server) {
        tick(server);
    }

    /** Puts every delayed block in now, due or not, presses too (tests can't wait for ticks). */
    public static void settleAll(ServerLevel level) {
        for (Live l : LIVE.values()) {
            RigGauntletLive.landNow(level, l);
            List<Pending> all = new ArrayList<>(l.pending);
            l.pending.clear();
            for (Pending p : all) l.pending.add(new Pending(Long.MIN_VALUE, p.pos(), p.state(), p.group(), 200));
            settle(level, l, Long.MAX_VALUE);
        }
    }

    /** One Collapse Timer wave over a player, fallen and landed at once (tests). Returns how many blocks it dropped. */
    public static int caveInNow(ServerLevel level, GateRecord g, FloorLayout f, Room r, ServerPlayer p) {
        RigCaveIn.wave(level, g, f, r, p);
        int n = RigCaveIn.pending();
        RigCaveIn.tick(level, Long.MAX_VALUE / 2);
        RigCaveIn.tick(level, Long.MAX_VALUE);
        return n;
    }

    /** The time of the run kept as this player's ghost on a Gauntlet's course, or -1 (tests). */
    public static int ghostTicks(GateRecord g, FloorLayout f, Room r, ServerPlayer p) {
        Live l = LIVE.get(new Key(g.id, f.area, f.floor, r.id));
        RigGauntletLive.Ghost ghost = l == null ? null : RigGauntletLive.best(l, p.getUUID());
        return ghost == null ? -1 : ghost.ticks();
    }

    /** Whether this player's run of a Gauntlet's course is on the clock (tests). */
    public static boolean racing(GateRecord g, FloorLayout f, Room r, ServerPlayer p) {
        Live l = LIVE.get(new Key(g.id, f.area, f.floor, r.id));
        RigGauntletLive.Racer racer = l == null ? null : l.racers.get(p.getUUID());
        return racer != null && racer.running;
    }

    /** The saved state of a rig room (tests). */
    public static RigState.Room state(GateRecord g, FloorLayout f, Room r) {
        return g.floor(f.area, f.floor).rig.room(r.id);
    }

    /** Starts a rig room's first track as if its trigger fired (the tour command), or false. */
    public static boolean force(ServerLevel level, GateRecord g, FloorLayout f, Room r) {
        if (r.rig == null) return false;
        Live l = live(g, f, r);
        if (l.running(0)) return false;
        start(level, l, 0, GateService.now(level.getServer()));
        return true;
    }
}
