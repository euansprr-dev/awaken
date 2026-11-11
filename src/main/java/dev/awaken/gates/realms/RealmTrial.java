package dev.awaken.gates.realms;

import dev.awaken.Awaken;
import dev.awaken.core.api.TrialHooks;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.run.Runs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A trial's course as it runs (SPEC §17.10, the Leaper's Gauntlet): its stands set into the world, the press over a
 * stand making its stroke when the stand is taken, the bars past it opening, the presses over a way turning over on the
 * Realm's clock without ever hurting anyone, what only a slam moves, and the places the Ascent is told a runner has come
 * to ({@link TrialHooks#at}). The land says where everything is ({@link Land#stands}, {@link Land#rams},
 * {@link Land#slams}, {@link Land#spots}); nothing here knows one course from another.
 *
 * <p>What is open is the gate's and is saved with it ({@link RealmState#woken}: {@code stand_<move>},
 * {@code slam_<id>}); what a runner has been shown is theirs and lasts as long as they are in the gate.
 */
public final class RealmTrial {
    private RealmTrial() {}

    /** A press's beat: how long it stays down, how long it takes to come down, and how long before that it smokes. */
    static final int RAM_DOWN = 34, RAM_FALL = 3, RAM_WARN = 12;
    /** Half seconds stood on a stand that does not answer before its way on opens anyway (a stand already taken is dark). */
    static final int DWELL = 8;
    /** How near a spot's first end a runner is told of it, and how near a press has to be to anyone to be worth turning. */
    static final int SPOT_NEAR = 5, RAM_NEAR = 56;
    /** A stand's press: how far its stroke comes down, and its whole length in ticks. */
    static final int STROKE_DROP = 3, STROKE_TICKS = 40;

    /** What one runner has been shown and holds in the gate they are in. */
    private static final class Run {
        int gate;
        /** The stands they hold, a bit a stand. */
        int taken;
        final BitSet told = new BitSet();
        final int[] falls = new int[64];
        int dwellAt = -1, dwell;
    }

    private record Stroke(int gate, int stand, long from) {}

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final List<Stroke> STROKES = new ArrayList<>();
    /** Where each press was last put, by gate and press; and when a freed one began to lift. */
    private static final Map<Long, Integer> RAM_AT = new HashMap<>();
    private static final Map<Long, Long> LIFTED = new HashMap<>();

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(RealmTrial::onFall);
        TrialHooks.onStand((p, trial, move, pos) -> {
            if (TrialHooks.LEAPER.equals(trial)) standTaken(p, move, true);
        });
        TrialHooks.onSlam((p, x, y, z, speed) -> slam(p, x, y, z));
    }

    public static void reset() {
        RUNS.clear();
        STROKES.clear();
        RAM_AT.clear();
        LIFTED.clear();
    }

    private static Run run(ServerPlayer p, GateRecord g) {
        Run r = RUNS.get(p.getUUID());
        if (r == null || r.gate != g.id) {
            r = new Run();
            r.gate = g.id;
            RUNS.put(p.getUUID(), r);
        }
        return r;
    }

    private static int standOf(Land land, String move) {
        List<Land.Stand> stands = land.stands();
        for (int i = 0; i < stands.size(); i++) if (stands.get(i).move().equals(move)) return i;
        return -1;
    }

    /** A fall in a trial's land costs nothing. */
    private static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level() != Realms.level(p.level().getServer())) return;
        GateRecord g = Runs.gateOf(p);
        RealmPlan plan = g == null ? null : Realms.plan(g);
        if (plan != null && plan.land().soft()) e.setDamageMultiplier(0);
    }

    // ---- each runner, twice a second -------------------------------------------------------------------------------------

    static void tick(ServerLevel level, ServerPlayer p, GateRecord g, Land land, RealmRef ref, RealmState st, RealmData data, int lx, int ly, int lz) {
        List<Land.Stand> stands = land.stands();
        if (stands.isEmpty()) return;
        Run run = run(p, g);
        boolean dwelling = false;
        for (int i = 0; i < stands.size(); i++) {
            Land.Stand s = stands.get(i);
            BlockPos at = Realms.world(ref.cell(), s.x(), s.y(), s.z());
            // the stand itself: the Ascent's block, set live so that it has what a block of its kind keeps
            if (!st.woken.contains("stand_set_" + i) && level.isLoaded(at)) {
                BlockState stand = standState(s.move());
                if (stand != null) level.setBlock(at, stand, Block.UPDATE_CLIENTS);
                st.woken.add("stand_set_" + i);
                data.setDirty();
            }
            // someone stood on a stand that does not answer them (theirs already, from another day): its way on opens all the same
            if ((run.taken & 1 << i) == 0 && p.onGround() && ly == s.y() && Math.abs(lx - s.x()) <= 1 && Math.abs(lz - s.z()) <= 1) {
                dwelling = true;
                if (run.dwellAt != i) {
                    run.dwellAt = i;
                    run.dwell = 0;
                }
                if (++run.dwell >= DWELL) standTaken(p, s.move(), false);
            }
        }
        if (!dwelling) run.dwellAt = -1;
        if (!p.onGround()) return;
        List<Land.Spot> spots = land.spots();
        for (int i = 0; i < spots.size() && i < 64; i++) {
            Land.Spot s = spots.get(i);
            if (run.told.get(i) || Math.abs(s.x0() - lx) > SPOT_NEAR || Math.abs(s.z0() - lz) > SPOT_NEAR || Math.abs(s.y0() - ly) > 2) continue;
            String[] parts = s.mark().split("/");
            int stand = parts.length > 1 ? standOf(land, parts[1]) : -1;
            boolean first = parts.length > 2 && parts[2].equals("1");
            if (s.mark().equals(TrialHooks.MARK_REACH)) {
                // the gulf nobody crosses yet: shown to whoever has not been to the first stand
                if ((run.taken & 1) != 0) continue;
            } else if (stand >= 0 && (run.taken & 1 << stand) == 0) {
                // a move's first place waits for its stand; whoever stands at a later one evidently holds the move
                if (first) continue;
                run.taken |= 1 << stand;
            }
            run.told.set(i);
            TrialHooks.at(p, TrialHooks.LEAPER, s.mark(), Realms.world(ref.cell(), s.x0(), s.y0(), s.z0()), Realms.world(ref.cell(), s.x1(), s.y1(), s.z1()));
        }
    }

    /** The line of the gate's HUD for a runner of a trial, or null in any other land. */
    public static String hint(ServerPlayer p, GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        if (plan == null || plan.land().stands().isEmpty()) return null;
        Run run = RUNS.get(p.getUUID());
        return run == null || run.gate != g.id || (run.taken & 1) == 0 ? "Reach the first stand" : "Cross the hall";
    }

    /**
     * A runner fell in one of the trial's halls and was set back on a ledge: the fall is theirs at the place they were
     * trying (the spot whose first end is nearest that ledge), and every third one there the place is shown again.
     */
    static void fell(ServerPlayer p, GateRecord g, Land land, RealmRef ref, int[] ledge) {
        List<Land.Spot> spots = land.spots();
        if (spots.isEmpty()) return;
        Run run = run(p, g);
        int best = -1, bestD = 14 * 14;
        for (int i = 0; i < spots.size() && i < 64; i++) {
            Land.Spot s = spots.get(i);
            if (!run.told.get(i) || Math.abs(s.y0() - ledge[1]) > 3) continue;
            int d = (s.x0() - ledge[0]) * (s.x0() - ledge[0]) + (s.z0() - ledge[2]) * (s.z0() - ledge[2]);
            // of two marks at one place (the gulf before and after its stand) the later is the one being tried
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0 || ++run.falls[best] % 3 != 0) return;
        Land.Spot s = spots.get(best);
        String[] parts = s.mark().split("/");
        TrialHooks.at(p, TrialHooks.LEAPER, parts.length > 1 ? TrialHooks.MARK_MISS + "/" + parts[1] : TrialHooks.MARK_MISS,
                Realms.world(ref.cell(), s.x0(), s.y0(), s.z0()), Realms.world(ref.cell(), s.x1(), s.y1(), s.z1()));
    }

    // ---- a stand taken ---------------------------------------------------------------------------------------------------

    /**
     * A stand's move is this runner's (the Ascent said so, or the stand had nothing left to give them): they hold it for
     * the course's marks, its bars open, and when it was taken just now the press over it makes its stroke.
     */
    static void standTaken(ServerPlayer p, String move, boolean stroke) {
        take(p, move, stroke);
    }

    /** Tours and tests: a stand taken as the Ascent would say it. False when the player is in no trial or it has no such stand. */
    public static boolean take(ServerPlayer p, String move) {
        return take(p, move, true);
    }

    private static boolean take(ServerPlayer p, String move, boolean stroke) {
        GateRecord g = Runs.gateOf(p);
        RealmPlan plan = g == null ? null : Realms.plan(g);
        if (plan == null) return false;
        int i = standOf(plan.land(), move);
        if (i < 0) return false;
        Run run = run(p, g);
        // the stroke is the stand's answer to being taken, whatever the course had already made of this runner
        if (stroke) STROKES.add(new Stroke(g.id, i, p.level().getGameTime()));
        if ((run.taken & 1 << i) != 0) return true;
        run.taken |= 1 << i;
        RealmLive.trialStand(p, move);
        dev.awaken.gates.run.Hud.send(p);
        Awaken.LOG.info("[trial] gate {}: the {} stand {} for {}", g.id, move, stroke ? "taken" : "was theirs already; its way on opens", p.getPlainTextName());
        return true;
    }

    private static BlockState standState(String move) {
        Block b = BuiltInRegistries.BLOCK.getValue(Awaken.id("leap_stand"));
        if (b == Blocks.AIR) {
            Awaken.LOG.warn("[trial] no awaken:leap_stand block: the stands of this course are not set");
            return null;
        }
        BlockState s = b.defaultBlockState();
        Property<?> prop = b.getStateDefinition().getProperty("move");
        return prop == null ? s : with(s, prop, move);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> prop, String name) {
        return prop.getValue(name).map(v -> s.setValue(prop, v)).orElse(s);
    }

    // ---- a slam ----------------------------------------------------------------------------------------------------------

    /** A slam landed: whatever of this land it landed on that only a slam moves, moves. */
    public static void slam(ServerPlayer p, double x, double y, double z) {
        GateRecord g = Runs.gateOf(p);
        RealmPlan plan = g == null ? null : Realms.plan(g);
        ServerLevel level = Realms.level(p.level().getServer());
        if (plan == null || level == null || p.level() != level || plan.land().slams().isEmpty()) return;
        RealmRef ref = g.realm.orElseThrow();
        Land land = plan.land();
        int lx = (int) Math.floor(x) - RealmCells.centerX(ref.cell()), ly = (int) Math.floor(y + 0.2), lz = (int) Math.floor(z) - RealmCells.centerZ(ref.cell());
        RealmData data = RealmData.get(level.getServer());
        RealmState st = data.state(g.id);
        for (Land.Slam s : land.slams()) {
            if (lx < s.x0() || lx > s.x1() || ly < s.y0() || ly > s.y1() || lz < s.z0() || lz > s.z1() || !st.woken.add("slam_" + s.id())) continue;
            data.setDirty();
            int shown = 0;
            for (Land.Edit e : land.change("slam_" + s.id())) {
                BlockPos at = Realms.world(ref.cell(), e.x(), e.y(), e.z());
                BlockState was = level.getBlockState(at);
                // the game's own breaking of a block, for a few of them: its dust and its sound
                if (!was.isAir() && shown++ % 4 == 0) level.levelEvent(2001, at, Block.getId(was));
                level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
            }
            List<Land.Ram> rams = land.rams();
            for (int i = 0; i < rams.size(); i++) if (rams.get(i).freedBy().equals(s.id())) {
                LIFTED.put(key(g.id, i), level.getGameTime());
                Land.Ram r = rams.get(i);
                level.playSound(null, Realms.world(ref.cell(), (r.x0() + r.x1()) / 2, r.feet(), (r.z0() + r.z1()) / 2), GatesRegistry.SND_RIG_LIFT.get(), SoundSource.BLOCKS, 2F, 0.7F);
            }
            level.playSound(null, p.blockPosition(), GatesRegistry.SND_RIG_SHATTER.get(), SoundSource.BLOCKS, 1.4F, 0.7F);
            level.sendParticles(ParticleTypes.DUST_PLUME, x, y + 0.2, z, 30, 1.6, 0.2, 1.6, 0.02);
            Awaken.LOG.info("[trial] gate {}: a slam by {} moved '{}'", g.id, p.getPlainTextName(), s.id());
        }
    }

    // ---- every tick: the presses -----------------------------------------------------------------------------------------

    private static long key(int gate, int index) {
        return (long) gate << 8 | index;
    }

    /** How far up a press that runs on a beat is, {@code c} ticks into its beat: down, a rise of a cell a tick, up, and a fall in three. */
    static int offset(Land.Ram r, int c) {
        if (c < RAM_DOWN) return 0;
        if (c < RAM_DOWN + r.travel()) return c - RAM_DOWN + 1;
        int left = r.period() - c;
        return left > RAM_FALL ? r.travel() : left == 3 ? Math.max(1, r.travel() * 3 / 5) : left == 2 ? Math.max(1, r.travel() / 5) : 0;
    }

    /** Whether a press on a beat can be walked under at this tick of it. */
    static boolean passable(Land.Ram r, int c) {
        return offset(r, c) >= 2;
    }

    public static void tickServer(MinecraftServer server) {
        ServerLevel level = Realms.level(server);
        if (level == null || level.players().isEmpty()) return;
        long now = level.getGameTime();
        Set<Integer> seen = null;
        for (ServerPlayer p : level.players()) {
            GateRecord g = Runs.gateOf(p);
            RealmPlan plan = g == null ? null : Realms.plan(g);
            if (plan == null || plan.land().stands().isEmpty() && plan.land().rams().isEmpty()) continue;
            if (seen == null) seen = new java.util.HashSet<>();
            if (!seen.add(g.id)) continue;
            rams(level, g, plan.land(), now);
        }
        if (STROKES.isEmpty()) return;
        for (Iterator<Stroke> it = STROKES.iterator(); it.hasNext(); ) {
            Stroke s = it.next();
            GateRecord g = dev.awaken.gates.GateRegistry.get(server).gate(s.gate());
            RealmPlan plan = g == null ? null : Realms.plan(g);
            long age = now - s.from();
            if (plan == null || g.realm.isEmpty() || age > STROKE_TICKS + 20 || s.stand() >= plan.land().stands().size()) {
                it.remove();
                continue;
            }
            Land.Stand st = plan.land().stands().get(s.stand());
            RealmRef ref = g.realm.orElseThrow();
            // down a cell every other tick, held, and back up: it ends where it began
            int drop = age < 2 ? 0 : age < 8 ? (int) Math.min(STROKE_DROP, (age - 2) / 2 + 1) : age < 30 ? STROKE_DROP : (int) Math.max(0, STROKE_DROP - (age - 30) / 3 - 1);
            BlockPos mid = Realms.world(ref.cell(), st.x(), st.head(), st.z());
            if (!level.isLoaded(mid)) continue;
            BlockState head = GatesRegistry.PRESS.get().defaultBlockState(), rod = level.getBlockState(mid.above(2));
            if (rod.isAir() || rod == head) rod = Blocks.IRON_CHAIN.defaultBlockState();
            place(level, ref, st.x() - 1, st.z() - 1, st.x() + 1, st.z() + 1, st.head() - STROKE_DROP, st.head() + 1, st.head() - drop, 2, head, rod);
            if (age == 2) level.playSound(null, mid, GatesRegistry.SND_RIG_PRESS.get(), SoundSource.BLOCKS, 1.2F, 0.8F);
            if (age == 8) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.getX() + 0.5, st.head() - STROKE_DROP - 0.2, mid.getZ() + 0.5, 24, 1.2, 0.1, 1.2, 0.3);
            if (age == 30) level.playSound(null, mid, GatesRegistry.SND_RIG_LIFT.get(), SoundSource.BLOCKS, 0.9F, 1F);
            if (age >= STROKE_TICKS) it.remove();
        }
    }

    private static void rams(ServerLevel level, GateRecord g, Land land, long now) {
        List<Land.Ram> rams = land.rams();
        if (rams.isEmpty() || g.realm.isEmpty()) return;
        RealmRef ref = g.realm.orElseThrow();
        RealmState st = null;
        int ox = RealmCells.centerX(ref.cell()), oz = RealmCells.centerZ(ref.cell());
        for (int i = 0; i < rams.size(); i++) {
            Land.Ram r = rams.get(i);
            double mx = ox + (r.x0() + r.x1() + 1) / 2.0, mz = oz + (r.z0() + r.z1() + 1) / 2.0;
            boolean near = false;
            for (ServerPlayer q : level.players()) near |= Math.abs(q.getX() - mx) < RAM_NEAR && Math.abs(q.getZ() - mz) < RAM_NEAR;
            if (!near || !level.isLoaded(BlockPos.containing(mx, r.feet(), mz))) continue;
            long key = key(g.id, i);
            int want;
            if (r.period() > 0) {
                int c = (int) Math.floorMod(now + r.phase(), (long) r.period());
                want = offset(r, c);
                if (c == r.period() - RAM_FALL - RAM_WARN) level.sendParticles(ParticleTypes.DUST_PLUME, mx, r.feet() + 0.1, mz, 10, (r.x1() - r.x0() + 1) / 2.6, 0.05, (r.z1() - r.z0() + 1) / 2.6, 0.01);
                if (c == 0) level.playSound(null, mx, r.feet(), mz, GatesRegistry.SND_RIG_PRESS.get(), SoundSource.BLOCKS, 0.55F, 0.9F);
            } else {
                if (st == null) st = RealmData.get(level.getServer()).state(g.id);
                Long from = LIFTED.get(key);
                want = !st.woken.contains("slam_" + r.freedBy()) ? 0 : from == null ? r.travel() : (int) Math.min(r.travel(), (now - from) / 3);
            }
            Integer is = RAM_AT.get(key);
            if (is != null && is == want) continue;
            if (is == null || want < is) clear(level, r, ox, oz, want);
            place(level, ref, r.x0(), r.z0(), r.x1(), r.z1(), r.feet(), r.feet() + r.travel() + r.tall() - 1, r.feet() + want, r.tall(), r.block(), r.rod());
            RAM_AT.put(key, want);
        }
    }

    /** Whoever a press is about to come down on is set clear of it, to the nearer side along the way, unhurt. */
    private static void clear(ServerLevel level, Land.Ram r, int ox, int oz, int want) {
        AABB box = new AABB(ox + r.x0(), r.feet() + want, oz + r.z0(), ox + r.x1() + 1, r.feet() + want + r.tall(), oz + r.z1() + 1);
        boolean alongX = r.x1() - r.x0() <= r.z1() - r.z0();
        double mx = ox + (r.x0() + r.x1() + 1) / 2.0, mz = oz + (r.z0() + r.z1() + 1) / 2.0;
        for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, box, e -> e.isAlive() && !e.isSpectator())) {
            double out = alongX ? (sp.getX() < mx ? ox + r.x0() - 0.6 : ox + r.x1() + 1.6) : (sp.getZ() < mz ? oz + r.z0() - 0.6 : oz + r.z1() + 1.6);
            sp.teleportTo(level, alongX ? out : sp.getX(), r.feet(), alongX ? sp.getZ() : out, Set.of(), sp.getYRot(), sp.getXRot(), true);
            sp.setDeltaMovement(Vec3.ZERO);
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp)); // a player moves on their own side
            level.playSound(null, sp.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.BLOCKS, 0.8F, 0.7F);
        }
    }

    /**
     * Puts a press head where it should be: over the footprint, {@code tall} cells from {@code lowest} up are the head,
     * the middle column above it is the rod, and every other cell from {@code min} to {@code max} is air. Only cells that
     * differ are touched.
     */
    private static void place(ServerLevel level, RealmRef ref, int x0, int z0, int x1, int z1, int min, int max, int lowest, int tall, BlockState head, BlockState rod) {
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = min; y <= max; y++) {
            BlockState want = y >= lowest && y < lowest + tall ? head : x == cx && z == cz && y >= lowest + tall ? rod : air;
            BlockPos w = Realms.world(ref.cell(), x, y, z);
            at.set(w);
            if (level.getBlockState(at) != want) level.setBlock(at, want, Block.UPDATE_CLIENTS);
        }
    }
}
