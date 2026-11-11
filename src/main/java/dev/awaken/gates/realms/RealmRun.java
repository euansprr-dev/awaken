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
                        if (!edits.isEmpty() && !level.isLoaded(Realms.world(ref.cell(), edits.getFirst().x(), edits.getFirst().y(), edits.getFirst().z()))) continue;
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
        RealmCountry.reset();
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
            if (!level.isLoaded(at)) continue;
            if (!e.state().isAir() && !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
        }
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
            } else if (p.fallDistance > FALL) {
                int[] l = c.ledges().get(Math.min(last[2], c.ledges().size() - 1));
                BlockPos at = Realms.world(ref.cell(), l[0], l[1], l[2]);
                p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
                p.resetFallDistance();
                p.setDeltaMovement(Vec3.ZERO);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p)); // a player moves on their own side
                p.hurtServer(level, level.damageSources().generic(), FALL_DAMAGE + p.getMaxHealth() * FALL_SHARE);
                level.playSound(null, at, GatesRegistry.SND_RIG_PIT.get(), SoundSource.PLAYERS, 0.8F, 1F);
                Signal.toast(p, Component.literal("You fell. You're back on the last ledge you stood on."));
            }
            return;
        }
    }
}
