package dev.awaken.gates.worlds;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.ThemeBlocks;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.run.Where;
import dev.awaken.gates.world.FloorAir;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * A world floor while people are on it (SPEC §38.8): its events (the Tolling, the Flood, the Bone Fall, the Silence, the
 * Swing), the Ringer, the secrets found in its places and its keepsakes, and the catch on a drop world. Server side. What
 * must outlive a restart (events that ran, the Flood, the Ringer, a niche opened) is in {@link FloorState#world}; the rest
 * lives here while the server runs and is idempotent when it's rebuilt.
 */
public final class WorldLive {
    private WorldLive() {}

    /** Bells struck on one floor before the Ringer comes. */
    public static final int RINGER_AT = 8;
    /** A fall longer than this on a drop world is caught; the catch costs this much health. */
    static final double CATCH_FALL = 9;
    static final float CATCH_DAMAGE = 3;
    /** In the Tolling mood the bells ring again this often, in ticks. */
    static final int TOLL_AGAIN = 90 * 20;
    /** A hung bell hums as you pass under it, each bell at most once in this many ticks (SPEC §38.9). */
    static final int HUM_AGAIN = 400;
    /** The Flood's blocks per tick at most (each of its two rises takes about two seconds). */
    static final int FLOOD_RATE = 300;
    static final String RINGER_TAG = "awaken_the_ringer";
    /** Water set without neighbour updates, so the risen water stands still and schedules nothing. */
    static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    // what FloorState.world remembers
    static final String FLOOD = "flood", FLOOD_DONE = "flood_done", RINGER = "ringer";

    record Key(int gate, int area, int floor) {
        static Key of(GateRecord g, FloorLayout f) {
            return new Key(g.id, f.area, f.floor);
        }
    }

    /** One floor's state while the server runs. */
    static final class Live {
        int strikes;
        long lastToll = Long.MIN_VALUE;
        /** The rooms from the arrival to the end, in order (world rooms). */
        List<Integer> path;
        Flood flood;
        /** When each bell (by its mark's index) last hummed. */
        final Map<Integer, Long> hummed = new HashMap<>();
    }

    /** Where a player last stood safely on a drop world's floor. */
    static final class Ledge {
        Key floor;
        /** A drop world's shaft, or the Rooftops' streets (38.11): the same catch, its own words. */
        boolean drop, lava, streets;
        Vec3 pos;
        float yaw;
    }

    record Timed(long at, long seq, Runnable run) {}

    static final Map<Key, Live> LIVE = new HashMap<>();
    static final Map<UUID, Ledge> LEDGES = new HashMap<>();
    /** The room of the place world each player stands in, if any. */
    static final Map<UUID, Integer> PLACE = new HashMap<>();
    static final PriorityQueue<Timed> LATER = new PriorityQueue<>(Comparator.comparingLong(Timed::at).thenComparingLong(Timed::seq));
    private static long now, seq;

    /** Singleplayer opens another world in the same JVM: nothing survives in statics. */
    public static void clear() {
        LIVE.clear();
        LEDGES.clear();
        PLACE.clear();
        LATER.clear();
        WorldRules.clear();
        WorldProps.clear();
    }

    static void later(int ticks, Runnable r) {
        LATER.add(new Timed(now + Math.max(0, ticks), seq++, r));
    }

    static Live live(GateRecord g, FloorLayout f) {
        Live l = LIVE.computeIfAbsent(Key.of(g, f), k -> new Live());
        if (l.path == null) l.path = path(f, f.start);
        return l;
    }

    // ---- the server's tick and a player's ---------------------------------------------------------------------------

    /** Every tick: timed cues, the Flood rising, the drop worlds' catch. */
    public static void tick(MinecraftServer server) {
        now = server.getTickCount();
        while (!LATER.isEmpty() && LATER.peek().at() <= now) LATER.poll().run().run();
        ServerLevel gl = GateService.gateLevel(server);
        if (gl == null) return;
        // the Flood is the Belfry's (never under the open sky); ledges and props are wherever their floor is
        for (Live l : LIVE.values()) if (l.flood != null && l.flood.step(gl)) l.flood = null;
        if (!LEDGES.isEmpty()) for (ServerLevel lv : GateService.levels(server)) for (ServerPlayer p : lv.players()) ledge(lv, p);
        WorldProps.tick(gl, now);
    }

    /** A player stepped onto a floor (WorldsLive.arrive): nothing carries over from the last one. */
    static void left(ServerPlayer p) {
        PLACE.remove(p.getUUID());
        Ledge l = LEDGES.get(p.getUUID());
        if (l != null) l.pos = null;
    }

    /** Arrived on a world, or in a place that's one: a keepsake that comes with arriving. */
    static void arrived(ServerPlayer p, GateRecord g, FloorLayout f, String world) {
        GateWorld w = GateWorlds.get(world);
        if (w != null && w.dressing().keepsakeFrom().equals("arrival")) keepsake(p, world);
    }

    /** Every half second for each player on a run, in a room of a floor (Runs.tickPlayer). */
    public static void enter(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        UUID id = p.getUUID();
        if (f.world.isEmpty()) {
            LEDGES.remove(id);
            PLACE.remove(id);
            return;
        }
        GateWorld w = GateWorlds.get(f.world);
        if (w == null) return;
        Key key = Key.of(g, f);
        Ledge ledge = LEDGES.computeIfAbsent(id, k -> new Ledge());
        if (!key.equals(ledge.floor)) {
            ledge.floor = key;
            ledge.pos = null;
        }
        ledge.streets = w.param("deep", 0) > 0;
        ledge.drop = w.kind().equals("drop") || ledge.streets;
        ledge.lava = w.dressing().blocks().getOrDefault("fluid", "").equals("minecraft:lava");
        places(p, g, f, r);
        nooks(p, g, f, r);
        Live live = live(g, f);
        FloorState fs = g.floor(f.area, f.floor);
        int at = live.path.indexOf(r.id);
        for (String ev : w.dressing().events()) {
            int from = trigger(ev, live.path, f);
            if (from < 0 || at < from) continue;
            if (ev.equals(FLOOD)) {
                flood(level, g, f);
                continue;
            }
            if (!fs.world.add("ev:" + ev)) continue;
            GateRegistry.get(level.getServer()).setDirty();
            run(ev, level, p, g, f, r, live);
        }
        // the Tolling mood rings them all again every minute and a half while anyone's here
        if (f.mood.equals("tolling") && fs.world.contains("ev:tolling") && now - live.lastToll > TOLL_AGAIN) {
            live.lastToll = now;
            tolling(level, g, f, p.blockPosition());
        }
        // a Flood the server stopped halfway rises the rest of the way; water it couldn't reach (an unloaded chunk) when it can
        if (fs.world.contains(FLOOD) && !fs.world.contains(FLOOD_DONE) && live.flood == null) live.flood = Flood.of(g, f, false);
        hum(level, p, g, f, live);
        WorldProps.near(level, p, g, f);
        if (f.world.endsWith("/inkwell") && (level.getGameTime() + p.getId()) % 4 == 0) unfold(level, p);
    }

    /** A hung bell over you hums softly as you pass under it (within its rim, 32 below it at most). */
    static void hum(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Live live) {
        BlockPos o = GateService.world(g, f, 0, 0, 0);
        double x = p.getX() - o.getX(), y = p.getY() - o.getY(), z = p.getZ() - o.getZ();
        for (int i = 0; i < f.marks.size(); i++) {
            FloorLayout.Mark m = f.marks.get(i);
            if (!m.kind().equals(FloorLayout.Mark.BELL) || m.id().equals(FloorLayout.Mark.HANDBELL) || y > m.y0() || m.y0() - y > 32) continue;
            double dx = x - m.cx(), dz = z - m.cz(), r = m.r() + 1.5;
            if (dx * dx + dz * dz > r * r) continue;
            Long last = live.hummed.get(i);
            if (last != null && now - last < HUM_AGAIN) continue;
            live.hummed.put(i, now);
            level.playSound(null, at(g, f, m).above((m.y1() - m.y0()) / 2), GatesRegistry.SND_WORLD_HUM.get(), SoundSource.BLOCKS,
                    (float) (0.8 + m.r() * 0.1), pitch(m));
            return;
        }
    }

    /** The room on the way (its index in the path) whose arrival runs an event, or -1 for one that waits on something else. */
    static int trigger(String ev, List<Integer> path, FloorLayout f) {
        int n = path.size();
        if (n == 0) return -1;
        return switch (ev) {
            case "tolling" -> n / 2;
            case "bone_fall" -> Math.max(1, n / 3);
            case "the_swing" -> Math.min(n - 1, (2 * n) / 3);
            case "the_silence" -> {
                for (int i = 1; i < n; i++) if (f.room(path.get(i)).type == Room.Type.HUB && i != n / 2) yield i;
                yield Math.min(n - 1, (2 * n) / 3);
            }
            // the Flood waits on the first key (a cache, a solved puzzle, the shrine), or rises behind you three quarters down
            case "flood" -> Math.min(n - 1, (3 * n) / 4);
            default -> WorldThemes.trigger(ev, n);
        };
    }

    static void run(String ev, ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, Live live) {
        switch (ev) {
            case "tolling" -> {
                live.lastToll = now;
                tolling(level, g, f, p.blockPosition());
            }
            case "bone_fall" -> boneFall(level, p, g, f, r);
            case "the_silence" -> silence(level, g, f);
            case "the_swing" -> swing(level, g, f, p.blockPosition());
            default -> WorldThemes.run(ev, level, p, g, f, r);
        }
    }

    /** The way from a room to the floor's end, room by room through its doors (breadth first). */
    static List<Integer> path(FloorLayout f, int from) {
        Map<Integer, Integer> prev = new HashMap<>();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(from);
        prev.put(from, -1);
        while (!q.isEmpty()) {
            int id = q.poll();
            if (id == f.end) break;
            Room r = f.room(id);
            if (r == null) continue;
            for (Room.Door d : r.doors) {
                if (d.toRoom < 0 || prev.containsKey(d.toRoom)) continue;
                prev.put(d.toRoom, id);
                q.add(d.toRoom);
            }
        }
        List<Integer> out = new ArrayList<>();
        if (!prev.containsKey(f.end)) return out;
        for (int id = f.end; id >= 0; id = prev.get(id)) out.add(0, id);
        return out;
    }

    /** Players on a run on this floor. */
    static List<ServerPlayer> on(ServerLevel level, GateRecord g, FloorLayout f) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            RunState run = Runs.run(p);
            if (run.active() && run.gateId == g.id && run.area == f.area && run.floor == f.floor) out.add(p);
        }
        return out;
    }

    static List<ServerPlayer> near(ServerLevel level, GateRecord g, FloorLayout f, BlockPos pos, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : on(level, g, f)) if (p.blockPosition().distSqr(pos) <= radius * radius) out.add(p);
        return out;
    }

    // ---- marks in the world ----------------------------------------------------------------------------------------

    static BlockPos at(GateRecord g, FloorLayout f, FloorLayout.Mark m) {
        return GateService.world(g, f, (int) Math.floor(m.cx()), m.y0(), (int) Math.floor(m.cz()));
    }

    /** A floor's marks of a kind nearest a point first, within a radius. */
    static List<FloorLayout.Mark> nearest(GateRecord g, FloorLayout f, String kind, BlockPos pos, double radius, int most) {
        List<FloorLayout.Mark> out = new ArrayList<>();
        for (FloorLayout.Mark m : f.marks) if (m.kind().equals(kind) && at(g, f, m).distSqr(pos) <= radius * radius) out.add(m);
        out.sort(Comparator.comparingDouble(m -> at(g, f, m).distSqr(pos)));
        return out.size() > most ? new ArrayList<>(out.subList(0, most)) : out;
    }

    static FloorLayout.Mark named(FloorLayout f, String kind, String id) {
        for (FloorLayout.Mark m : f.marks) if (m.kind().equals(kind) && m.id().equals(id)) return m;
        return null;
    }

    /** Bells this wide across (a radius) or more are great bells, with the Great Bell's own voice. */
    static final double GREAT = 6;

    /** A bell's sound: a handbell's, a great bell's (the church bell an octave down), or a hung bell's strike. */
    static SoundEvent voice(FloorLayout.Mark m) {
        if (m.id().equals(FloorLayout.Mark.HANDBELL)) return GatesRegistry.SND_WORLD_HANDBELL.get();
        return m.r() >= GREAT ? GatesRegistry.SND_WORLD_GREAT.get() : GatesRegistry.SND_WORLD_STRIKE.get();
    }

    /** Its pitch over its sound: the bigger, the lower. */
    static float pitch(FloorLayout.Mark m) {
        if (m.id().equals(FloorLayout.Mark.HANDBELL)) return 1F;
        if (m.r() >= GREAT) return (float) Math.max(0.8, 1 - (m.r() - GREAT) * 0.06);
        return (float) Math.max(0.7, Math.min(1.3, 1.25 - m.r() * 0.08));
    }

    static void ring(ServerLevel level, GateRecord g, FloorLayout f, FloorLayout.Mark m, float pitchBy) {
        boolean hand = m.id().equals(FloorLayout.Mark.HANDBELL);
        SoundEvent s = voice(m);
        BlockPos pos = hand ? at(g, f, m) : at(g, f, m).above((m.y1() - m.y0()) / 2);
        level.playSound(null, pos, s, SoundSource.BLOCKS, hand ? 1.2F : (float) (2.5 + m.r() * 0.3), pitch(m) * pitchBy);
        if (!hand) WorldProps.swing(g, f, m, 1F);
    }

    /** Dust shaken down round each player within reach. */
    static void dust(ServerLevel level, List<ServerPlayer> to, int bursts) {
        for (int i = 0; i < bursts; i++) {
            later(i * 10, () -> {
                for (ServerPlayer p : to) {
                    if (!p.isAlive()) continue;
                    level.sendParticles(p, GatesRegistry.P_GRIT.get(), true, false, p.getX(), p.getY() + 7, p.getZ(), 26, 5, 1.5, 5, 0.01);
                }
            });
        }
    }

    // ---- the events -------------------------------------------------------------------------------------------------

    /** The Tolling: every bell near you rings, one after another, and dust comes down from the vault. */
    static void tolling(ServerLevel level, GateRecord g, FloorLayout f, BlockPos pos) {
        List<FloorLayout.Mark> bells = nearest(g, f, FloorLayout.Mark.BELL, pos, 64, 12);
        if (bells.isEmpty()) {
            // a world with no bells of its own hears the ones above it, far off
            for (int i = 0; i < 3; i++) later(i * 30, () -> level.playSound(null, pos.above(14), GatesRegistry.SND_WORLD_TOLL.get(), SoundSource.AMBIENT, 1.6F, 0.6F));
        }
        for (int i = 0; i < bells.size(); i++) {
            FloorLayout.Mark m = bells.get(i);
            int delay = i * 6 + (int) Math.floorMod(m.x0() * 31L + m.z0(), 5);
            later(delay, () -> ring(level, g, f, m, 1F));
        }
        dust(level, near(level, g, f, pos, 48), 3);
    }

    /** The Bone Fall: the passage rattles, bones come down, and two of the dead climb out of them. */
    static void boneFall(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        BlockPos at = p.blockPosition();
        level.playSound(null, at.above(3), GatesRegistry.SND_WORLD_BONES.get(), SoundSource.HOSTILE, 1.6F, 0.9F);
        dust(level, List.of(p), 2);
        later(30, () -> {
            List<BlockPos> spots = Rooms.spots(level, g, f, r);
            spots.sort(Comparator.comparingDouble(s -> Math.abs(Math.sqrt(s.distSqr(at)) - 6)));
            for (int i = 0; i < Math.min(2, spots.size()); i++) {
                BlockPos s = spots.get(i);
                level.sendParticles(GatesRegistry.P_GRIT.get(), s.getX() + 0.5, s.getY() + 2.5, s.getZ() + 0.5, 30, 0.5, 1.2, 0.5, 0.02);
                Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, s);
            }
        });
    }

    /** The Silence: the field's sound drains away, and after a held breath the Still Bell tolls once. */
    static void silence(ServerLevel level, GateRecord g, FloorLayout f) {
        for (ServerPlayer p : on(level, g, f)) AwakenNet.send(p, new WorldPayloads.Hush(160));
        FloorLayout.Mark still = named(f, FloorLayout.Mark.BELL, "still");
        if (still == null) still = biggest(f);
        FloorLayout.Mark bell = still;
        if (bell == null) return;
        later(120, () -> level.playSound(null, at(g, f, bell).above(6), GatesRegistry.SND_WORLD_GREAT.get(), SoundSource.BLOCKS, 8F, pitch(bell) * 0.9F));
    }

    static FloorLayout.Mark biggest(FloorLayout f) {
        FloorLayout.Mark best = null;
        for (FloorLayout.Mark m : f.marks) if (m.kind().equals(FloorLayout.Mark.BELL) && (best == null || m.r() > best.r())) best = m;
        return best;
    }

    /** The Swing: the bell column swings, and a peal runs down it from the top, each bell higher than the last. */
    static void swing(ServerLevel level, GateRecord g, FloorLayout f, BlockPos pos) {
        List<FloorLayout.Mark> bells = new ArrayList<>();
        for (FloorLayout.Mark m : f.marks) if (m.kind().equals(FloorLayout.Mark.BELL) && !m.id().equals(FloorLayout.Mark.HANDBELL)) bells.add(m);
        bells.sort(Comparator.comparingInt(FloorLayout.Mark::y0).reversed());
        if (bells.size() > 16) bells = bells.subList(0, 16);
        if (!bells.isEmpty()) level.playSound(null, at(g, f, bells.get(0)), GatesRegistry.SND_WORLD_SWING.get(), SoundSource.BLOCKS, 4F, 0.8F);
        for (int i = 0; i < bells.size(); i++) {
            FloorLayout.Mark m = bells.get(i);
            float up = 0.75F + 0.5F * i / Math.max(1, bells.size() - 1);
            later(10 + i * 8, () -> ring(level, g, f, m, up));
        }
        dust(level, near(level, g, f, pos, 64), 4);
    }

    // ---- the Flood -------------------------------------------------------------------------------------------------

    /** A key was taken on a floor (a cache opened, a puzzle solved, the shrine opened): on a Flood world the water rises. */
    public static void progress(ServerLevel level, GateRecord g, FloorLayout f) {
        if (f == null || f.world.isEmpty()) return;
        GateWorld w = GateWorlds.get(f.world);
        if (w != null && w.dressing().events().contains(FLOOD)) flood(level, g, f);
    }

    static void flood(ServerLevel level, GateRecord g, FloorLayout f) {
        FloorState fs = g.floor(f.area, f.floor);
        if (!fs.world.add(FLOOD)) return;
        GateRegistry.get(level.getServer()).setDirty();
        live(g, f).flood = Flood.of(g, f, true);
        for (ServerPlayer p : on(level, g, f)) {
            p.sendOverlayMessage(Component.literal("The water is rising."));
            level.playSound(null, p.blockPosition(), GatesRegistry.SND_WORLD_FLOOD.get(), SoundSource.AMBIENT, 1.4F, 1F);
        }
    }

    /** Gate Air for a floor as it is now: after the Flood its water stands at the brim. */
    public static FloorAir air(GateRecord g, FloorLayout f) {
        if (f.marks(FloorLayout.Mark.BASIN).isEmpty() || !g.floor(f.area, f.floor).world.contains(FLOOD)) return f.air;
        int top = Integer.MIN_VALUE, low = Integer.MAX_VALUE;
        for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.BASIN)) {
            top = Math.max(top, m.y1());
            low = Math.min(low, m.y0() - 1);
        }
        List<FloorAir.Water> waters = new ArrayList<>();
        for (FloorAir.Water wt : f.air.waters()) waters.add(wt.y() == low ? new FloorAir.Water(top, wt.x0(), wt.z0(), wt.x1(), wt.z1()) : wt);
        return new FloorAir(f.air.lights(), f.air.shafts(), f.air.mists(), waters);
    }

    /** The water filling a floor's basins: the lower layer, a breath, then the brim; lilies ride up on it. */
    static final class Flood {
        final GateRecord g;
        final FloorLayout f;
        final List<List<BlockPos>> layers = new ArrayList<>();
        final List<BlockPos> lilies = new ArrayList<>(), waiting = new ArrayList<>();
        int layer, at;
        long resume;

        Flood(GateRecord g, FloorLayout f) {
            this.g = g;
            this.f = f;
        }

        static Flood of(GateRecord g, FloorLayout f, boolean fresh) {
            Flood fl = new Flood(g, f);
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.BASIN)) {
                lo = Math.min(lo, m.y0());
                hi = Math.max(hi, m.y1());
            }
            for (int y = lo; y <= hi; y++) {
                List<BlockPos> layer = new ArrayList<>();
                for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.BASIN)) {
                    if (y < m.y0() || y > m.y1()) continue;
                    for (int x = m.x0(); x <= m.x1(); x++) for (int z = m.z0(); z <= m.z1(); z++) layer.add(GateService.world(g, f, x, y, z));
                }
                fl.layers.add(layer);
            }
            fl.resume = now + (fresh ? 20 : 0);
            return fl;
        }

        /** One tick's worth; true when it's done. */
        boolean step(ServerLevel level) {
            if (now < resume) return false;
            if (layer >= layers.size()) return finish(level);
            List<BlockPos> l = layers.get(layer);
            int rate = Math.max(64, Math.min(FLOOD_RATE, l.size() / 40 + 1));
            BlockState water = Blocks.WATER.defaultBlockState();
            for (int n = 0; n < rate && at < l.size(); n++, at++) {
                BlockPos pos = l.get(at);
                if (!level.hasChunkAt(pos)) {
                    waiting.add(pos);
                    continue;
                }
                BlockState s = level.getBlockState(pos);
                if (s.is(GatesRegistry.CRYPT_LILY_CANDLE.get())) lilies.add(pos);
                else if (!s.isAir()) continue;
                level.setBlock(pos, water, QUIET);
            }
            if (at >= l.size()) {
                layer++;
                at = 0;
                resume = now + 20;
            }
            return false;
        }

        boolean finish(ServerLevel level) {
            int brim = GateService.world(g, f, 0, 0, 0).getY();
            for (BlockPos p : lilies) {
                BlockPos up = new BlockPos(p.getX(), brim, p.getZ());
                if (level.hasChunkAt(up) && level.getBlockState(up).isAir()) level.setBlock(up, GatesRegistry.CRYPT_LILY_CANDLE.get().defaultBlockState(), QUIET);
            }
            lilies.clear();
            BlockState water = Blocks.WATER.defaultBlockState();
            waiting.removeIf(p -> {
                if (!level.hasChunkAt(p)) return false;
                if (level.getBlockState(p).isAir()) level.setBlock(p, water, QUIET);
                return true;
            });
            if (!waiting.isEmpty()) {
                // part of the nave isn't loaded: come back for it
                resume = now + 100;
                return false;
            }
            FloorState fs = g.floor(f.area, f.floor);
            if (fs.world.add(FLOOD_DONE)) GateRegistry.get(level.getServer()).setDirty();
            for (ServerPlayer p : on(level, g, f)) WorldsLive.resend(p, g, f);
            return true;
        }
    }

    // ---- places that are worlds of their own -----------------------------------------------------------------------

    /** A hidden nook (SPEC §38.10): its secret is found by standing in it. */
    static void nooks(ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        String world = r == null ? f.world : f.worldOf(r);
        BlockPos o = GateService.world(g, f, 0, 0, 0);
        int x = p.getBlockX() - o.getX(), y = p.getBlockY() - o.getY(), z = p.getBlockZ() - o.getZ();
        for (FloorLayout.Mark m : f.marks) {
            if (!m.kind().equals(FloorLayout.Mark.NOOK) || !m.contains(x, y, z)) continue;
            GateAtlas.Entry e = p.getData(GatesRegistry.ATLAS.get()).entry(world);
            if (e == null || !e.secrets.contains(m.id())) found(p, world, m.id());
        }
    }

    static void places(ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        UUID id = p.getUUID();
        String inner = f.placeWorlds.get(r.id);
        Integer was = PLACE.get(id);
        if (inner != null && (was == null || was != r.id)) {
            PLACE.put(id, r.id);
            WorldsLive.arrivePlace(p, g, f, inner);
            // the secret that leads here is found by coming
            GateWorld w = GateWorlds.get(f.world);
            if (w != null) for (GateWorld.Secret s : w.dressing().secrets()) if (s.leadsTo().equals(inner)) found(p, f.world, s.id());
        } else if (inner == null && was != null) {
            PLACE.remove(id);
            WorldsLive.resend(p, g, f);
        }
    }

    // ---- secrets and keepsakes ------------------------------------------------------------------------------------

    /** A secret of a world is found: the Atlas keeps it, and a keepsake that comes with it is yours. */
    static void found(ServerPlayer p, String world, String secret) {
        GateWorld w = GateWorlds.get(world);
        if (w == null) return;
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        boolean fresh = atlas.secret(world, secret);
        p.setData(GatesRegistry.ATLAS.get(), atlas);
        if (fresh) {
            String name = secret;
            for (GateWorld.Secret s : w.dressing().secrets()) if (s.id().equals(secret)) name = s.name();
            p.sendOverlayMessage(Component.literal("You found one of this place's secrets: " + name + "."));
            p.level().playSound(null, p.blockPosition(), GatesRegistry.SND_WORLD_FOUND.get(), SoundSource.PLAYERS, 1F, 1F);
        }
        if (w.dressing().keepsakeFrom().equals(secret)) keepsake(p, world);
        WorldsLive.sync(p);
    }

    /** A world's keepsake, once per player: a trophy to hang in the Hearth's Trophy Hall. */
    static void keepsake(ServerPlayer p, String world) {
        GateWorld w = GateWorlds.get(world);
        if (w == null || w.dressing().keepsake().isEmpty()) return;
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        GateAtlas.Entry e = atlas.entry(world);
        if (e == null || e.keepsake) return;
        e.keepsake = true;
        p.setData(GatesRegistry.ATLAS.get(), atlas);
        String name = w.dressing().keepsakeName().isEmpty() ? WorldsLive.pretty(w.dressing().keepsake()) : w.dressing().keepsakeName();
        ItemStack s = HearthHooks.keepsake(w.dressing().keepsake(), name, PlayDays.current(p.level().getServer()));
        if (!s.isEmpty() && !p.addItem(s)) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
        p.sendSystemMessage(Component.literal("A keepsake from " + w.name() + ": " + name + ". Hang it in your Trophy Hall at home."));
        WorldSets.after(p, w);
        WorldsLive.sync(p);
    }

    // ---- the Ringer ---------------------------------------------------------------------------------------------------

    /** A bell was struck on a floor (WorldRules): the count that brings the Ringer. */
    static void struck(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, BlockPos pos) {
        Live live = live(g, f);
        live.strikes++;
        FloorState fs = g.floor(f.area, f.floor);
        if (live.strikes < RINGER_AT || fs.world.contains(RINGER)) return;
        GateWorld w = GateWorlds.get(f.world);
        if (w == null || w.dressing().signature().isEmpty()) return;
        fs.world.add(RINGER);
        GateRegistry.get(level.getServer()).setDirty();
        List<ServerPlayer> heard = near(level, g, f, pos, 48);
        for (ServerPlayer q : heard) q.sendOverlayMessage(Component.literal("You've rung too many bells. The Ringer is coming."));
        for (int i = 0; i < 3; i++) later(i * 25, () -> level.playSound(null, pos, GatesRegistry.SND_WORLD_RINGER.get(), SoundSource.HOSTILE, 3F, 0.7F));
        Room in = r;
        later(75, () -> {
            List<BlockPos> spots = in == null ? List.of() : Rooms.spots(level, g, f, in);
            BlockPos far = null;
            for (BlockPos s : spots) if (far == null || Math.abs(s.distSqr(pos) - 64) < Math.abs(far.distSqr(pos) - 64)) far = s;
            if (far == null) far = p.blockPosition();
            // its own body when the mobs module has one (the Creature Kit), else an elite of the theme in its name
            LivingEntity e = Rooms.signature(level, g, f, -1, WorldLook.id(w.dressing().signature()), far);
            if (e == null) {
                e = Rooms.spawn(level, g, f, -1, MobRole.Role.ELITE, far);
                if (e == null) return;
                e.setCustomName(Component.literal("The Ringer"));
                e.setCustomNameVisible(true);
                AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(hp.getBaseValue() * 2);
                    e.setHealth(e.getMaxHealth());
                }
            }
            e.getPersistentData().putString(RINGER_TAG, f.world);
            WorldsLive.dressSignature(e, f.world, w.dressing().signature());
            level.sendParticles(GatesRegistry.P_GRIT.get(), e.getX(), e.getY() + 1, e.getZ(), 40, 0.6, 1, 0.6, 0.03);
            for (ServerPlayer q : near(level, g, f, far, 48)) {
                GateAtlas atlas = q.getData(GatesRegistry.ATLAS.get());
                if (atlas.met(w.dressing().signature())) {
                    q.setData(GatesRegistry.ATLAS.get(), atlas);
                    WorldsLive.sync(q);
                }
            }
        });
    }

    /** Something died in a gate: the Ringer leaves its world's keepsake to everyone who fought it. */
    public static void died(ServerLevel level, LivingEntity dead) {
        String world = dead.getPersistentData().getStringOr(RINGER_TAG, "");
        if (world.isEmpty()) return;
        GateWorld w = GateWorlds.get(world);
        if (w == null || !w.dressing().keepsakeFrom().equals(w.dressing().signature())) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(dead) > 48 * 48 || !Runs.run(p).active()) continue;
            keepsake(p, world);
        }
    }

    /** Whether an entity is a gate's Ringer (its name and health are its own). */
    public static boolean ringer(LivingEntity e) {
        return !e.getPersistentData().getStringOr(RINGER_TAG, "").isEmpty();
    }

    // ---- the catch on a drop world -----------------------------------------------------------------------------------

    static void ledge(ServerLevel level, ServerPlayer p) {
        Ledge l = LEDGES.get(p.getUUID());
        if (l == null || !(l.drop || l.lava) || !p.isAlive() || p.isSpectator() || p.getAbilities().flying || p.isFallFlying()) return;
        if (l.lava && l.pos != null && p.isInLava()) {
            // a magma world gives you back too: on the last ground you stood on, the fire put out
            caught(level, p, l);
            p.clearFire();
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 100, 0));
            p.sendOverlayMessage(Component.literal("The magma spat you back out onto the last ground you stood on."));
            return;
        }
        if (p.onGround()) {
            if (p.isInWater() || p.isInLava() || (now + p.getId()) % 4 != 0) return;
            Where w = Where.at(level.getServer(), p.blockPosition());
            if (w != null && w.room() != null && w.floor() != null && Key.of(w.gate(), w.floor()).equals(l.floor)) {
                l.pos = p.position();
                l.yaw = p.getYRot();
            }
            return;
        }
        if (!l.drop || l.pos == null || p.isInWater() || p.fallDistance <= CATCH_FALL || p.getY() > l.pos.y - 4) return;
        caught(level, p, l);
    }

    /** Back on the last ledge you stood on, a little the worse for it. */
    static void caught(ServerLevel level, ServerPlayer p, Ledge l) {
        p.teleportTo(level, l.pos.x, l.pos.y, l.pos.z, Set.of(), l.yaw, p.getXRot(), true);
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtServer(level, level.damageSources().fall(), CATCH_DAMAGE);
        level.playSound(null, BlockPos.containing(l.pos), GatesRegistry.SND_WORLD_CATCH.get(), SoundSource.PLAYERS, 1F, 1F);
        level.sendParticles(GatesRegistry.P_GLINT.get(), l.pos.x, l.pos.y + 1, l.pos.z, 16, 0.4, 0.6, 0.4, 0.02);
        p.sendOverlayMessage(Component.literal(l.streets ? "You fell into the streets, and the market lifted you back up onto the roofs."
                : "You fell, and the shaft gave you back to the last ledge you stood on."));
    }

    /** The Inkwell's paper bridges open out under anyone who comes near (SPEC §38.11), and fold again once nobody is. */
    static void unfold(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 0; dy++) {
                    m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (!(s.getBlock() instanceof ThemeBlocks.Folding) || s.getValue(ThemeBlocks.Folding.OPEN)) continue;
                    ThemeBlocks.Folding.open(level, m.immutable(), s);
                    any = true;
                }
            }
        }
        if (any) WorldThemes.play(level, at, "page", 0.6F, 1.3F);
    }

    /** The floor a world position's in, for code that has only a position. */
    static FloorLayout floorOf(GateRecord g, int area, int floor) {
        return GateLayout.of(g.plan).floor(area, floor);
    }
}
