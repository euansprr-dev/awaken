package dev.awaken.gates.worlds;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.run.GateMobs;
import dev.awaken.gates.run.Where;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each world's one rule (SPEC §38.6, §38.8): what the place lets you do that no other does. Server side; the blocks route
 * to it. The Bell Crypt's is the bells: strike one and its sound shows the way on for a moment, glints on any secret
 * near, and stuns the dead round it; ring too many on one floor and the Ringer comes.
 */
public final class WorldRules {
    private WorldRules() {}

    /** Ticks between one player's strikes. */
    static final int COOLDOWN = 15;
    /** How far the stun reaches, how long it lasts; how far the way is shown and secrets glint. */
    static final double STUN_R = 10, SHOW_R = 40, SECRET_R = 24;
    static final int STUN_TICKS = 50, SHOW_DOORS = 5, SHOW_POINTS = 120;

    private static final Map<UUID, Long> LAST = new HashMap<>();

    static void clear() {
        LAST.clear();
    }

    /** Bronze was struck. True when a world's rule answered (the bell rang). */
    public static boolean strike(ServerPlayer p, BlockPos pos) {
        ServerLevel level = (ServerLevel) p.level();
        Where w = Where.at(level.getServer(), pos);
        if (w == null || w.floor() == null) return false;
        FloorLayout f = w.floor();
        String world = w.room() == null ? f.world : f.worldOf(w.room());
        GateWorld gw = GateWorlds.get(world);
        if (gw == null || !gw.dressing().rule().equals("bells")) return false;
        long t = level.getServer().getTickCount();
        Long last = LAST.get(p.getUUID());
        if (last != null && t - last < COOLDOWN) return true;
        LAST.put(p.getUUID(), t);
        GateRecord g = w.gate();
        int yRel = pos.getY() - f.baseY;
        FloorLayout.Mark bell = markAt(f, FloorLayout.Mark.BELL, w.x(), yRel, w.z(), 1);
        FloorLayout.Mark secret = markAt(f, FloorLayout.Mark.SECRET, w.x(), yRel, w.z(), 0);
        boolean silent = secret != null && secret.id().equals("silent_bell");
        if (!silent) {
            boolean hand = bell != null && bell.id().equals(FloorLayout.Mark.HANDBELL);
            level.playSound(null, pos, bell == null ? GatesRegistry.SND_WORLD_STRIKE.get() : WorldLive.voice(bell), SoundSource.BLOCKS,
                    hand ? 1.4F : 2.5F, bell == null ? 1F : WorldLive.pitch(bell) * (0.96F + p.getRandom().nextFloat() * 0.08F));
        }
        List<ServerPlayer> near = WorldLive.near(level, g, f, pos, SHOW_R);
        if (!near.contains(p)) near.add(p);
        show(level, g, f, w.room(), pos, near);
        glintSecrets(level, g, f, world, pos, p);
        stun(level, pos);
        if (secret != null) secret(level, p, g, f, world, secret, pos);
        WorldLive.struck(level, p, g, f, w.room(), pos);
        return true;
    }

    /** The mark of a kind round a floor-local block (grown by {@code pad}), or null. */
    static FloorLayout.Mark markAt(FloorLayout f, String kind, int x, int y, int z, int pad) {
        for (FloorLayout.Mark m : f.marks) {
            if (!m.kind().equals(kind)) continue;
            if (x >= m.x0() - pad && x <= m.x1() + pad && y >= m.y0() - pad && y <= m.y1() + pad && z >= m.z0() - pad && z <= m.z1() + pad) return m;
        }
        return null;
    }

    /** The way on, shown for a moment: a run of glints from the bell through the next few doorways toward the end. */
    static void show(ServerLevel level, GateRecord g, FloorLayout f, Room from, BlockPos pos, List<ServerPlayer> to) {
        List<Vec3> pts = way(g, f, from, pos);
        List<Vec3> trail = new ArrayList<>();
        for (int i = 0; i + 1 < pts.size() && trail.size() < SHOW_POINTS; i++) {
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            int n = Math.max(1, (int) (a.distanceTo(b) / 1.25));
            for (int k = 0; k < n && trail.size() < SHOW_POINTS; k++) trail.add(a.lerp(b, k / (double) n));
        }
        for (int i = 0; i < trail.size(); i += 3) {
            List<Vec3> batch = trail.subList(i, Math.min(trail.size(), i + 3));
            WorldLive.later(i / 3, () -> {
                for (ServerPlayer p : to) {
                    if (!p.isAlive()) continue;
                    for (Vec3 v : batch) level.sendParticles(p, GatesRegistry.P_GLINT.get(), true, false, v.x, v.y, v.z, 1, 0.08, 0.08, 0.08, 0.005);
                }
            });
        }
    }

    /** The points of the way on: the bell, then each doorway on the path from its room toward the floor's end. */
    static List<Vec3> way(GateRecord g, FloorLayout f, Room from, BlockPos pos) {
        List<Vec3> out = new ArrayList<>();
        out.add(Vec3.atCenterOf(pos).add(0, 0.6, 0));
        if (from == null) return out;
        List<Integer> path = WorldLive.path(f, from.id);
        for (int i = 0; i + 1 < path.size() && i < SHOW_DOORS; i++) {
            Room r = f.room(path.get(i));
            for (Room.Door d : r.doors) {
                if (d.toRoom != path.get(i + 1)) continue;
                out.add(Vec3.atCenterOf(GateService.world(g, f, d.centerX(), d.y, d.centerZ())).add(0, 0.6, 0));
                break;
            }
        }
        return out;
    }

    /** Secrets near the bell that the striker hasn't found glint where they're hidden. */
    static void glintSecrets(ServerLevel level, GateRecord g, FloorLayout f, String world, BlockPos pos, ServerPlayer p) {
        GateAtlas.Entry e = p.getData(GatesRegistry.ATLAS.get()).entry(world);
        for (FloorLayout.Mark m : WorldLive.nearest(g, f, FloorLayout.Mark.SECRET, pos, SECRET_R, 4)) {
            if (e != null && e.secrets.contains(m.id())) continue;
            BlockPos at = WorldLive.at(g, f, m);
            int hi = Math.min(m.y1() - m.y0(), 6);
            WorldLive.later(12, () -> level.sendParticles(p, GatesRegistry.P_GLINT.get(), true, false, at.getX() + 0.5, at.getY() + 0.5 + hi / 2.0,
                    at.getZ() + 0.5, 14, 0.35, Math.max(0.3, hi / 2.0), 0.35, 0.01));
        }
    }

    /** The dead within reach reel at the sound: slowed hard and weakened for a moment. Lords stand it. */
    static void stun(ServerLevel level, BlockPos pos) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(STUN_R), e -> e.isAlive() && GateMobs.keyOf(e) != null)) {
            MobRole role = m.getData(CoreApi.MOB_ROLE.get());
            if (role.role() == MobRole.Role.BOSS) continue;
            m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, 3));
            m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 1));
            level.sendParticles(GatesRegistry.P_GLINT.get(), m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 5, 0.25, 0.1, 0.25, 0.01);
        }
    }

    /** A handbell that keeps a secret was struck. */
    static void secret(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, String world, FloorLayout.Mark m, BlockPos pos) {
        List<ServerPlayer> here = WorldLive.near(level, g, f, pos, SECRET_R);
        if (!here.contains(p)) here.add(p);
        FloorState fs = g.floor(f.area, f.floor);
        switch (m.id()) {
            case "choir_hymn" -> {
                // the choir sings its last note: three bells in a chord, held
                float[] chord = {1.0F, 1.26F, 1.5F};
                for (int i = 0; i < chord.length; i++) {
                    float pitch = chord[i];
                    WorldLive.later(8 + i * 6, () -> level.playSound(null, pos, GatesRegistry.SND_WORLD_HANDBELL.get(), SoundSource.BLOCKS, 1.6F, pitch));
                }
                WorldLive.later(14, () -> level.sendParticles(GatesRegistry.P_GLINT.get(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 6, 1.5, 4, 0.01));
            }
            case "sealed_niche" -> {
                if (fs.world.add("open:sealed_niche")) {
                    GateRegistry.get(level.getServer()).setDirty();
                    for (int x = m.x0(); x <= m.x1(); x++) {
                        for (int y = m.y0(); y <= m.y1(); y++) {
                            for (int z = m.z0(); z <= m.z1(); z++) {
                                BlockPos b = GateService.world(g, f, x, y, z);
                                if (!level.getBlockState(b).is(GatesRegistry.CRYPT_NICHE.get())) continue;
                                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                                level.playSound(null, b, GatesRegistry.SND_WORLD_BONES.get(), SoundSource.BLOCKS, 1.2F, 1.2F);
                                level.sendParticles(GatesRegistry.P_GRIT.get(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.03);
                            }
                        }
                    }
                }
            }
            case "lowest_bell" -> {
                FloorLayout.Mark low = WorldLive.named(f, FloorLayout.Mark.BELL, "lowest");
                if (low != null) WorldLive.later(20, () -> WorldLive.ring(level, g, f, low, 0.8F));
            }
            case "silent_bell" -> {
                // it makes no sound; the Still Bell answers it
                FloorLayout.Mark still = WorldLive.named(f, FloorLayout.Mark.BELL, "still");
                if (still != null) WorldLive.later(40, () -> level.playSound(null, WorldLive.at(g, f, still).above(6),
                        GatesRegistry.SND_WORLD_GREAT.get(), SoundSource.BLOCKS, 8F, WorldLive.pitch(still) * 0.9F));
            }
            default -> {}
        }
        for (ServerPlayer q : here) WorldLive.found(q, world, m.id());
    }
}
