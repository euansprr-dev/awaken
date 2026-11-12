package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.run.Hud;
import dev.awaken.gates.run.RealmPacks;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.progression.Curve;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The run through a Realm, leg by leg (SPEC §50.9): an ambush springs when you walk into it, a fight's doors shut while it
 * is on and open when it's over or nobody is left inside, a way opens behind a Warden, a hidden place pays whoever finds
 * it, and a long fall on a stretch you can fall from costs you a little and puts you back on its last ledge. Traps are
 * {@link RealmTraps}. Everything here is asked twice a second for each player in the Realm and does its work once.
 */
final class RealmRun {
    private RealmRun() {}

    /** Fall this far on a course and you're caught. Low enough to catch a drop of sixteen at two looks a second. */
    static final double FALL = 6;
    static final float FALL_DAMAGE = 3F, FALL_SHARE = 0.08F;
    /** How near a leg's fight has to be before anyone asks about its doors or whether it's over. */
    private static final int NEAR = 72, CLOSE = 40;

    /** Per player: the gate, which course, and the last ledge of it they stood on. Not saved: you start a course again from its first. */
    private static final Map<UUID, int[]> LEDGE = new HashMap<>();

    static void tick(MinecraftServer server, ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, int lx, int lz) {
        Land land = plan.land();
        RealmData data = RealmData.get(server);
        RealmState st = data.state(g.id);
        boolean plays = !p.isCreative() && !p.isSpectator();
        int ly = p.getBlockY();
        for (Land.Leg leg : land.route()) {
            int dx = leg.x() - lx, dz = leg.z() - lz;
            // close: near enough that the leg's monsters are certainly in the world to be counted
            boolean near = Math.abs(dx) <= NEAR && Math.abs(dz) <= NEAR, close = Math.abs(dx) <= CLOSE && Math.abs(dz) <= CLOSE;
            boolean inside = dx * dx + dz * dz <= leg.radius() * leg.radius() && Math.abs(leg.y() - ly) <= 10;
            switch (leg.kind()) {
                case AMBUSH -> {
                    BlockPos at = Realms.world(ref.cell(), leg.x(), leg.y(), leg.z());
                    if (plays && inside && level.getDifficulty() != Difficulty.PEACEFUL && st.woken.add("den" + leg.site())) {
                        apply(level, ref, land, "ambush_" + leg.id());
                        FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
                        RealmPacks.den(level, g, f, at, leg.site(), leg.pack(), p);
                        level.playSound(null, at, SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 1.6F, 0.6F);
                        data.setDirty();
                    }
                    boolean woken = st.woken.contains("den" + leg.site());
                    int waves = waves(leg);
                    if (near && waves > 1 && woken && !st.densCleared.contains(leg.site())) {
                        // a stand (SPEC §50.9): the next wave comes when the last is down and someone still holds the room
                        int sent = 1;
                        while (sent < waves && st.woken.contains("wave" + leg.site() + "_" + (sent + 1))) sent++;
                        if (sent < waves) {
                            if (close && plays && inside && level.getDifficulty() != Difficulty.PEACEFUL && !RealmPacks.alive(level, at, "d" + leg.site())) {
                                st.woken.add("wave" + leg.site() + "_" + (sent + 1));
                                FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
                                // every other wave is the theme's own roll, so no two waves are the same company
                                RealmPacks.den(level, g, f, at, leg.site(), sent % 2 == 1 ? List.of() : leg.pack(), p);
                                level.playSound(null, at, SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 1.6F, 0.6F);
                                for (ServerPlayer q : level.players()) {
                                    if (Runs.run(q).gateId == g.id) Signal.toast(q, Component.literal("More are coming. Wave " + (sent + 1) + " of " + waves + "."));
                                }
                                data.setDirty();
                            }
                            if (land.seals(leg.id())) doors(level, g, land, ref, st, data, leg, "d" + leg.site(), true);
                            continue;
                        }
                    }
                    if (near) fight(level, g, land, ref, st, data, leg, "d" + leg.site(), woken, close);
                }
                case FIGHT -> {
                    if (near) fight(level, g, land, ref, st, data, leg, "d" + leg.site(), st.woken.contains("den" + leg.site()), close);
                }
                case WARDEN -> {
                    if (near && land.seals(leg.id())) doors(level, g, land, ref, st, data, leg, "w" + leg.site(), st.woken.contains("warden" + leg.site()) && !st.wardensDown.contains(leg.site()));
                }
                case SHORTCUT -> {
                    if (!st.woken.contains("open_" + leg.id()) && met(st, leg.when())) {
                        List<Land.Edit> edits = land.change("shortcut_" + leg.id());
                        if (!edits.isEmpty() && !dev.awaken.core.api.Ready.loaded(level, Realms.world(ref.cell(), edits.getFirst().x(), edits.getFirst().y(), edits.getFirst().z()))) continue;
                        for (Land.Edit e : edits) level.setBlock(Realms.world(ref.cell(), e.x(), e.y(), e.z()), e.state(), Block.UPDATE_CLIENTS);
                        st.woken.add("open_" + leg.id());
                        data.setDirty();
                        String line = land.shortcutLine(leg.id());
                        for (ServerPlayer q : level.players()) {
                            if (Runs.run(q).gateId != g.id) continue;
                            if (line != null) Signal.toast(q, Component.literal(line));
                            level.playSound(null, q.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.5F);
                        }
                    }
                }
                case HIDDEN -> {
                    if (plays && inside && st.woken.add("hidden_" + leg.id())) {
                        apply(level, ref, land, "found_" + leg.id());
                        double share = RealmLive.hiddenShare(land);
                        for (ServerPlayer q : level.players()) {
                            RunState run = Runs.run(q);
                            if (run.gateId != g.id) continue;
                            run.satchelXp += RealmLive.pool(q, g) * (1 - Curve.KILL_SHARE) * share;
                            Hud.send(q);
                            Signal.toast(q, Component.literal("You found a place most people walk straight past. There's extra in your Satchel for it."));
                        }
                        level.playSound(null, p.blockPosition(), GatesRegistry.SND_SHRINE.get(), SoundSource.BLOCKS, 1F, 1.3F);
                        data.setDirty();
                    }
                }
                default -> {}
            }
        }
        if (plays) course(level, p, g, land, ref, lx, ly, lz);
        if (!land.holds().isEmpty()) holds(level, p, g, plan, ref, st, data, plays, lx, ly, lz);
        if (!p.isSpectator() && !land.lessons().isEmpty()) lessons(p, g, land, ref, lx, ly, lz);
        if (plays && !land.lines().isEmpty()) clean(p, g, land, lx, ly, lz);
        RealmTrial.tick(level, p, g, land, ref, st, data, lx, ly, lz);
        if (!land.traps().isEmpty()) RealmTraps.tick(level, g, land, ref, p, lx, ly, lz);
        RealmCountry.tick(level, p, g, plan, ref, st, data, lx, lz);
    }

    /** How many waves a leg's fight comes in: a stand's ambush says "waves:8"; anything else is one. */
    static int waves(Land.Leg leg) {
        String w = leg.when();
        if (w == null || !w.startsWith("waves:")) return 1;
        try {
            return Math.max(1, Integer.parseInt(w.substring(6)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** A server is starting: gate ids begin again in another world, so nothing kept by gate id may be read as this one's. */
    static void reset() {
        CLEAN.clear();
        RESTING.clear();
        RealmCountry.reset();
        RealmTrial.reset();
    }

    /** A den's fight: once its pack is gone it counts as cleared (a way may open on that), and while it's on its doors are shut. */
    private static void fight(ServerLevel level, GateRecord g, Land land, RealmRef ref, RealmState st, RealmData data, Land.Leg leg, String pack, boolean woken, boolean close) {
        if (!woken) return;
        boolean cleared = st.densCleared.contains(leg.site());
        if (!cleared && close && !RealmPacks.alive(level, Realms.world(ref.cell(), leg.x(), leg.y(), leg.z()), pack)) {
            st.densCleared.add(leg.site());
            data.setDirty();
            cleared = true;
            spoils(level, Realms.world(ref.cell(), leg.x(), leg.y(), leg.z()));
        }
        if (land.seals(leg.id())) doors(level, g, land, ref, st, data, leg, pack, !cleared);
    }

    /** A herd put down leaves something: a cache where it stood, as a room's does in the old gates. */
    static void spoils(ServerLevel level, BlockPos center) {
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos at = center.above(dy);
            var here = level.getBlockState(at);
            var under = level.getBlockState(at.below());
            if (!here.isAir() && !here.is(net.minecraft.world.level.block.Blocks.WATER) || under.getCollisionShape(level, at.below()).isEmpty()) continue;
            // a floor that steps in half blocks: the slab under a cache is made whole, or the cache stands half a block up
            if (under.getBlock() instanceof net.minecraft.world.level.block.SlabBlock && under.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) {
                level.setBlock(at.below(), under.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE), Block.UPDATE_CLIENTS);
            }
            level.setBlock(at, GatesRegistry.CACHE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            level.playSound(null, at, GatesRegistry.SND_CACHE.get(), SoundSource.BLOCKS, 1F, 1F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 24, 0.4, 0.5, 0.4, 0.03);
            return;
        }
    }

    /** Shut while the pack is awake and someone of this gate stands inside; open again the moment either stops being true, so nobody is ever locked in or out. */
    private static void doors(ServerLevel level, GateRecord g, Land land, RealmRef ref, RealmState st, RealmData data, Land.Leg leg, String pack, boolean on) {
        boolean sealed = st.sealed.contains(leg.id());
        if (!on && !sealed) return;
        BlockPos at = Realms.world(ref.cell(), leg.x(), leg.y(), leg.z());
        boolean want = on && someoneIn(level, g, ref, leg) && RealmPacks.fighting(level, at, pack);
        if (want) {
            List<Land.Edit> shut = land.change("seal_" + leg.id());
            Land.Edit first = shut.getFirst();
            BlockPos probe = Realms.world(ref.cell(), first.x(), first.y(), first.z());
            // shut it, and shut it again if something else in the place (a toll) has lifted it since
            if (sealed && level.getBlockState(probe) == first.state()) return;
            place(level, ref, shut);
            if (!sealed) {
                st.sealed.add(leg.id());
                data.setDirty();
                level.playSound(null, at, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.4F, 0.55F);
            }
        } else if (sealed) {
            place(level, ref, land.change("unseal_" + leg.id()));
            st.sealed.remove(leg.id());
            data.setDirty();
            level.playSound(null, at, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.55F);
        }
    }

    private static boolean someoneIn(ServerLevel level, GateRecord g, RealmRef ref, Land.Leg leg) {
        int ox = RealmCells.centerX(ref.cell()), oz = RealmCells.centerZ(ref.cell());
        double r = leg.radius() + 2;
        for (ServerPlayer q : level.players()) {
            if (Runs.run(q).gateId != g.id || q.isSpectator() || !q.isAlive()) continue;
            double dx = q.getX() - ox - leg.x() - 0.5, dz = q.getZ() - oz - leg.z() - 0.5;
            if (dx * dx + dz * dz <= r * r && Math.abs(q.getY() - leg.y()) <= 12) return true;
        }
        return false;
    }

    private static boolean met(RealmState st, String when) {
        if (when.startsWith("warden")) return st.wardensDown.contains(Integer.parseInt(when.substring(6)));
        if (when.startsWith("den")) return st.densCleared.contains(Integer.parseInt(when.substring(3)));
        return false;
    }

    private static void apply(ServerLevel level, RealmRef ref, Land land, String moment) {
        place(level, ref, land.change(moment));
    }

    /** A door never comes down on anyone: a cell with somebody in it stays open. */
    private static void place(ServerLevel level, RealmRef ref, List<Land.Edit> edits) {
        for (Land.Edit e : edits) {
            BlockPos at = Realms.world(ref.cell(), e.x(), e.y(), e.z());
            if (!dev.awaken.core.api.Ready.loaded(level, at)) continue;
            if (!e.state().isAir() && !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
        }
    }

    /** How near a hold has to be to stand its monsters there (they wake as a den's do, about twelve off), and a chase's reach. */
    private static final int HOLD_FILL = 28, CHASE_FROM = 7, CHASE_TO = 20, TETHER = 6;

    /**
     * The fights on a line (SPEC §72.5). A hold or a perch stands its monsters when someone comes within {@link #HOLD_FILL}
     * and they wake as a den's pack does; one that has gone more than {@link #TETHER} past its place gives up and walks
     * back, so nobody is followed down a line. A chase comes up behind whoever has passed its start and is still in the line.
     * Never in peace, never in creative, never shut in, never required.
     */
    private static void holds(ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st, RealmData data, boolean plays, int lx, int ly, int lz) {
        Land land = plan.land();
        for (Land.Hold h : land.holds()) {
            int dx = h.x() - lx, dz = h.z() - lz, dy = h.y() - ly, d2 = dx * dx + dz * dz;
            if (d2 > 64 * 64) continue;
            String key = "hold" + h.index();
            BlockPos at = Realms.world(ref.cell(), h.x(), h.y(), h.z());
            if (st.woken.contains(key)) {
                // tied to its place: every two seconds, one that has wandered off it goes back
                if (h.kind() != Land.HoldKind.CHASE && (p.tickCount / 10) % 4 == 0 && d2 <= HOLD_FILL * HOLD_FILL * 2) {
                    for (net.minecraft.world.entity.Mob m : RealmPacks.members(level, at, "h" + h.index())) {
                        double ox = m.getX() - (at.getX() + 0.5), oz = m.getZ() - (at.getZ() + 0.5);
                        if (ox * ox + oz * oz > (h.radius() + TETHER) * (h.radius() + TETHER) || m.getY() < at.getY() - 3) {
                            m.setTarget(null);
                            dev.awaken.core.api.MobHooks.lead(m, at.getX() + 0.5, at.getZ() + 0.5, 1.1, 80);
                        }
                    }
                }
                continue;
            }
            if (!plays || level.getDifficulty() == Difficulty.PEACEFUL || !dev.awaken.core.api.Ready.loaded(level, at)) continue;
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            int made;
            if (h.kind() == Land.HoldKind.CHASE) {
                // a chase wakes once someone is past its start, along its own line, and comes from behind them
                if (d2 < CHASE_FROM * CHASE_FROM || d2 > CHASE_TO * CHASE_TO || Math.abs(dy) > 12 || !inLine(land, h.leg(), lx, ly, lz)) continue;
                made = RealmPacks.hold(level, g, f, at, h.index(), h.count(), h.radius(), "", p);
                level.playSound(null, at, SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 1.6F, 0.6F);
            } else {
                if (d2 > HOLD_FILL * HOLD_FILL || Math.abs(dy) > 16) continue;
                String marksman = h.kind() == Land.HoldKind.PERCH ? RealmCountry.marksman(plan.bp().theme()) : "";
                made = RealmPacks.hold(level, g, f, at, h.index(), h.count(), h.radius(), marksman, null);
            }
            dev.awaken.Awaken.LOG.info("[beats] gate {}: {} {} at {} {} {} on {}: {} stood", g.id, h.kind(), h.index(), h.x(), h.y(), h.z(), h.leg(), made);
            st.woken.add(key);
            data.setDirty();
        }
    }

    /** How near a family's first showing has to be (SPEC §72.6): six across, four up or down. */
    private static final int LESSON_NEAR = 6, LESSON_DY = 4;

    /**
     * Teach once, then go quiet (SPEC §72.6): the first time someone comes near the place a family of moves is first asked
     * for in a gate, the gold figure does the move once and the Now line says how in plain words. Each family is shown to
     * each player once, in whichever gate they meet it first, and never again. One at a time; a partner gets their own.
     */
    private static void lessons(ServerPlayer p, GateRecord g, Land land, RealmRef ref, int lx, int ly, int lz) {
        dev.awaken.gates.run.GateMemory mem = null;
        for (Land.Lesson l : land.lessons()) {
            int dx = l.x() - lx, dz = l.z() - lz;
            if (dx * dx + dz * dz > LESSON_NEAR * LESSON_NEAR || Math.abs(l.y() - ly) > LESSON_DY) continue;
            if (mem == null) mem = p.getData(GatesRegistry.MEMORY.get());
            if (!mem.beatsShown.add(l.family().name())) continue;
            int cx = RealmCells.centerX(ref.cell()), cz = RealmCells.centerZ(ref.cell());
            List<Vec3> path = new java.util.ArrayList<>(l.path().size());
            for (double[] q : l.path()) path.add(new Vec3(q[0] + cx, q[1], q[2] + cz));
            if (dev.awaken.core.AwakenNet.canReceive(p, dev.awaken.gates.net.BeatPayloads.Lesson.TYPE)) {
                dev.awaken.core.AwakenNet.send(p, new dev.awaken.gates.net.BeatPayloads.Lesson(path));
            }
            Signal.toast(p, Component.literal(l.words()));
            dev.awaken.Awaken.LOG.info("[beats] gate {}: showed {} to {} at {} {} {}", g.id, l.family(), p.getScoreboardName(), l.x(), l.y(), l.z());
            return;
        }
    }

    /** A line being crossed clean: the gate, which line, which end it's going to (0 its start), checks stood still, the feet's last place. */
    private static final class Clean {
        int gate, line, far, still;
        double x, z;
    }

    private static final Map<UUID, Clean> CLEAN = new HashMap<>();
    /** The line someone last finished or spoiled (gate, line): not begun again until they're well away from both its ends. */
    private static final Map<UUID, int[]> RESTING = new HashMap<>();
    /** How near an end counts as at it; how far from both ends a finished line has to be left; two seconds stood still. */
    private static final int END_NEAR = 2, END_LEFT = 4, STILL = 4;

    /**
     * A clean line (SPEC §72.6): from one end of a line to the other without a caught fall and without standing still
     * for two seconds gives a quiet sound and a little Flow on the client. No score, no timer, nothing said. Either way
     * along it counts; today's crossings (a fallback) don't.
     */
    private static void clean(ServerPlayer p, GateRecord g, Land land, int lx, int ly, int lz) {
        List<Land.Line> lines = land.lines();
        UUID id = p.getUUID();
        Clean c = CLEAN.get(id);
        if (c != null && (c.gate != g.id || c.line >= lines.size())) {
            CLEAN.remove(id);
            c = null;
        }
        int[] rest = RESTING.get(id);
        if (rest != null && (rest[0] != g.id || rest[1] >= lines.size() || end(lines.get(rest[1]), lx, ly, lz, END_LEFT) < 0)) {
            RESTING.remove(id);
            rest = null;
        }
        if (c == null) {
            if (!p.onGround()) return;
            for (int i = 0; i < lines.size(); i++) {
                Land.Line l = lines.get(i);
                if (l.fallback() || l.beats().isEmpty() || rest != null && rest[1] == i) continue;
                int at = end(l, lx, ly, lz, END_NEAR);
                if (at < 0) continue;
                c = new Clean();
                c.gate = g.id;
                c.line = i;
                c.far = 1 - at;
                c.x = p.getX();
                c.z = p.getZ();
                CLEAN.put(id, c);
                return;
            }
            return;
        }
        Land.Line l = lines.get(c.line);
        int at = end(l, lx, ly, lz, END_NEAR);
        if (at == c.far) {
            if (!p.onGround()) return;
            CLEAN.remove(id);
            RESTING.put(id, new int[]{g.id, c.line});
            if (dev.awaken.core.AwakenNet.canReceive(p, dev.awaken.gates.net.BeatPayloads.Clean.TYPE)) {
                dev.awaken.core.AwakenNet.send(p, new dev.awaken.gates.net.BeatPayloads.Clean(l.beats().size()));
            }
            dev.awaken.Awaken.LOG.info("[beats] gate {}: {} crossed {} clean ({} beats)", g.id, p.getScoreboardName(), l.leg(), l.beats().size());
            return;
        }
        double mx = p.getX() - c.x, mz = p.getZ() - c.z;
        c.x = p.getX();
        c.z = p.getZ();
        if (at >= 0) {
            // still at the end it began from: it hasn't begun, however long they wait there
            c.still = 0;
            return;
        }
        if (!inLine(land, l.leg(), lx, ly, lz)) {
            CLEAN.remove(id);
            return;
        }
        if (p.onGround() && mx * mx + mz * mz < 0.09) {
            if (++c.still >= STILL) {
                CLEAN.remove(id);
                RESTING.put(id, new int[]{g.id, c.line});
            }
        } else {
            c.still = 0;
        }
    }

    /** Which end of a line the feet are at (0 its start, 1 its end), or -1 for neither: within {@code r} across and two up or down. */
    private static int end(Land.Line l, int lx, int ly, int lz, int r) {
        if (Math.abs(l.x0() - lx) <= r && Math.abs(l.z0() - lz) <= r && Math.abs(l.y0() - ly) <= 2) return 0;
        if (Math.abs(l.x1() - lx) <= r && Math.abs(l.z1() - lz) <= r && Math.abs(l.y1() - ly) <= 2) return 1;
        return -1;
    }

    /** Whether a place is inside the box of a line's own stretch (its Course). */
    private static boolean inLine(Land land, String leg, int lx, int ly, int lz) {
        for (Land.Course c : land.courses()) if (c.leg().equals(leg) && c.holds(lx, ly, lz)) return true;
        return false;
    }

    /**
     * Being hit on a line (SPEC §72.5, Decision 6): it costs health, but never throws you off a wall, a lip or a ledge.
     * Inside a line's stretch nobody in a Realm is knocked back.
     */
    static void knockBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide()) return;
        GateRecord g = Runs.gateOf(p);
        RealmPlan plan = g == null ? null : Realms.plan(g);
        if (plan == null || plan.land().lines().isEmpty()) return;
        RealmRef ref = g.realm.orElseThrow();
        int lx = p.getBlockX() - RealmCells.centerX(ref.cell()), lz = p.getBlockZ() - RealmCells.centerZ(ref.cell());
        for (Land.Course c : plan.land().courses()) {
            if (c.catchY() != Integer.MIN_VALUE && c.holds(lx, p.getBlockY(), lz)) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /**
     * A line's fall (SPEC §72.5): more than three under the lowest ledge within five across, or under the line's lowest
     * where none is that near. A drop the line asks for lands on a ledge, so it is never caught on the way down, however
     * long; a fall into a gulf's water is caught however short, so nobody has the long walk back from its bed.
     */
    static boolean fell(Land.Course c, double y, int lx, int lz) {
        int low = Integer.MAX_VALUE;
        for (int[] l : c.ledges()) if (Math.abs(l[0] - lx) <= 5 && Math.abs(l[2] - lz) <= 5) low = Math.min(low, l[1]);
        return low == Integer.MAX_VALUE ? c.below(y) : y < low - 3;
    }

    /** On a stretch you can fall from: remember the last ledge stood on, and catch a long fall back to it. */
    private static void course(ServerLevel level, ServerPlayer p, GateRecord g, Land land, RealmRef ref, int lx, int ly, int lz) {
        List<Land.Course> courses = land.courses();
        for (int i = 0; i < courses.size(); i++) {
            Land.Course c = courses.get(i);
            if (!c.holds(lx, ly, lz)) continue;
            int[] last = LEDGE.get(p.getUUID());
            if (last == null || last[0] != g.id || last[1] != i) LEDGE.put(p.getUUID(), last = new int[]{g.id, i, 0});
            if (p.onGround()) {
                for (int k = 0; k < c.ledges().size(); k++) {
                    int[] l = c.ledges().get(k);
                    if (Math.abs(l[0] - lx) <= 3 && Math.abs(l[2] - lz) <= 3 && Math.abs(l[1] - ly) <= 2) last[2] = k;
                }
            } else if (c.catchY() != Integer.MIN_VALUE ? fell(c, p.getY(), lx, lz) : p.fallDistance > FALL) {
                int[] l = c.ledges().get(Math.min(last[2], c.ledges().size() - 1));
                BlockPos at = Realms.world(ref.cell(), l[0], l[1], l[2]);
                p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
                p.resetFallDistance();
                p.setDeltaMovement(Vec3.ZERO);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p)); // a player moves on their own side
                level.playSound(null, at, GatesRegistry.SND_RIG_PIT.get(), SoundSource.PLAYERS, 0.8F, 1F);
                // a caught fall spoils a clean line
                CLEAN.remove(p.getUUID());
                // a hall that teaches a move takes nothing for a fall and says nothing about it
                if (c.free()) {
                    RealmTrial.fell(p, g, land, ref, l);
                    return;
                }
                // on Gentle a caught fall costs half (SPEC §70.4, §72.6): the same line, asked more kindly
                float cost = FALL_DAMAGE + p.getMaxHealth() * FALL_SHARE;
                p.hurtServer(level, level.damageSources().generic(), dev.awaken.core.api.FightsHooks.gentle(p) ? cost * 0.5F : cost);
                Signal.toast(p, Component.literal("You fell. You're back on the last ledge you stood on."));
            }
            return;
        }
    }
}
