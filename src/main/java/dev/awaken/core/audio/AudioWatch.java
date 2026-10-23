package dev.awaken.core.audio;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.world.anomaly.Anomaly;
import dev.awaken.world.anomaly.AnomalyData;
import dev.awaken.world.anomaly.AnomalyKind;
import dev.awaken.world.anomaly.FallenStarBlock;
import dev.awaken.world.landmark.LandmarkKind;
import dev.awaken.world.landmark.Landmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Two light watches, once a second / every five seconds per player (spread by UUID):
 * <ul>
 *   <li>discoveries: when the player's {@code awaken:discoveries} counter moves, the new discovery keys are looked at;
 *   a named beast's first sighting ({@code creature/<beast>}) plays {@link Cue#BEAST}, a lair found
 *   ({@code lair/<beast>}) plays {@link Cue#LAIR}. Nothing in the fauna module had to change for it.</li>
 *   <li>ambience emitters: the Well and Precursor ruins within {@value #EMIT_RANGE} blocks and fallen stars still
 *   sealed (cooling), sent to the client only when the set changes; the client loops their beds.</li>
 * </ul>
 */
final class AudioWatch {
    private AudioWatch() {}

    static final String DISCOVERIES = "awaken:discoveries";
    static final String BEAST_PREFIX = "awaken:discovered/creature/";
    static final String LAIR_PREFIX = "awaken:discovered/lair/";
    static final int EMIT_RANGE = 96;

    private static final class Seen {
        long discoveries = -1;
        Set<String> keys = Set.of();
        List<AudioNet.Emitter> emitters = List.of();
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static Set<String> beasts;

    static void tick(MinecraftServer server) {
        // GameTests call discoveries()/compute() themselves; other tests' mock players never wake the watches
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) return;
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int phase = Math.floorMod(p.getUUID().hashCode(), 100);
            if (t % 20 == phase % 20) discoveries(p);
            if (t % 100 == phase) emitters(p);
        }
    }

    /** Looks for new discoveries; returns the cue it played, if any. */
    static Cue discoveries(ServerPlayer p) {
        Profile prof = Progression.profile(p);
        if (!prof.awakened()) return null;
        long n = prof.counter(DISCOVERIES);
        Seen s = SEEN.computeIfAbsent(p.getUUID(), u -> new Seen());
        if (s.discoveries == n) return null;
        boolean first = s.discoveries < 0;
        s.discoveries = n;
        Set<String> now = new HashSet<>();
        for (String k : prof.counters().keySet()) {
            if (k.startsWith(BEAST_PREFIX) || k.startsWith(LAIR_PREFIX)) now.add(k);
        }
        Set<String> before = s.keys;
        s.keys = now;
        if (first) return null; // what was known before this session is not news
        Cue played = null;
        for (String k : now) {
            if (before.contains(k)) continue;
            Cue c = classify(k);
            if (c != null && AudioCues.play(p, c)) played = c;
        }
        return played;
    }

    /** The cue a discovery key earns: a named beast's first sighting, or a lair found. */
    static Cue classify(String key) {
        if (key.startsWith(LAIR_PREFIX)) return Cue.LAIR;
        if (key.startsWith(BEAST_PREFIX) && beasts().contains(key.substring(BEAST_PREFIX.length()))) return Cue.BEAST;
        return null;
    }

    private static Set<String> beasts() {
        if (beasts == null) {
            Set<String> s = new HashSet<>();
            for (MobDef d : Bestiary.fauna()) if (d.fauna == FaunaRole.BEAST) s.add(d.id);
            beasts = s;
        }
        return beasts;
    }

    static void emitters(ServerPlayer p) {
        Seen s = SEEN.computeIfAbsent(p.getUUID(), u -> new Seen());
        List<AudioNet.Emitter> now = compute(p);
        if (now.equals(s.emitters)) return;
        s.emitters = now;
        AwakenNet.send(p, new AudioNet.Emitters(now));
    }

    static List<AudioNet.Emitter> compute(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || !Progression.profile(p).awakened()) return List.of();
        ServerLevel level = p.level();
        BlockPos me = p.blockPosition();
        List<AudioNet.Emitter> out = new ArrayList<>();
        if (on(AwakenConfig.LANDMARKS)) {
            for (Landmarks.Landmark l : Landmarks.around(p, EMIT_RANGE, 2)) {
                if (out.size() >= AudioNet.MAX_EMITTERS) break;
                BlockPos at = l.pos();
                if (horizontal(me, at) > EMIT_RANGE) continue;
                if (l.kind() == LandmarkKind.WELL) {
                    out.add(new AudioNet.Emitter(AudioNet.WELL, at.getX(), at.getY() - 4, at.getZ()));
                } else if (l.kind() != LandmarkKind.GATE) {
                    int y = l.kind() == LandmarkKind.SKY_PLATFORM ? 266
                            : level.hasChunk(at.getX() >> 4, at.getZ() >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ())
                            : me.getY();
                    out.add(new AudioNet.Emitter(AudioNet.RUIN, at.getX(), y, at.getZ()));
                }
            }
        }
        if (on(AwakenConfig.ANOMALIES)) {
            for (Anomaly a : AnomalyData.get(level.getServer()).active()) {
                if (out.size() >= AudioNet.MAX_EMITTERS) break;
                if (a.kind != AnomalyKind.FALLEN_STAR || a.target.equals(BlockPos.ZERO) || horizontal(me, a.target) > EMIT_RANGE) continue;
                if (!level.isLoaded(a.target)) continue;
                BlockState st = level.getBlockState(a.target);
                if (st.getBlock() instanceof FallenStarBlock && st.getValue(FallenStarBlock.SEALED)) {
                    out.add(new AudioNet.Emitter(AudioNet.STAR, a.target.getX(), a.target.getY(), a.target.getZ()));
                }
            }
        }
        return out;
    }

    private static boolean on(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void forget(UUID player) {
        SEEN.remove(player);
    }

    static void clear() {
        SEEN.clear();
    }
}
