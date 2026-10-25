package dev.awaken.body.ascent;

import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.above.ruin.SkyRig;
import dev.awaken.core.AwakenNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's windways (SPEC §17.12): every five seconds per player, the sky platforms within {@link #LOOK} of them
 * (the Above's own placement search, a few new terrain samples at most), each linked to its nearest within
 * {@link Windways#MAX_LINK}, their decks' heights from the seed; the lanes of every pair seen in the last five minutes
 * are the server's air, and the ones within {@link #NEAR} of a player are sent to them when that set changes. Nothing
 * saved: the lanes follow from the platforms.
 */
final class WindwaysServer {
    private WindwaysServer() {}

    private record Pair(List<Windways.Way> ways, long seen) {}

    private static final Map<Long, Pair> PAIRS = new HashMap<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    static final int LOOK = 1400, NEAR = 900, FORGET = 20 * 300, MAX_SENT = 64;

    static void tick(ServerPlayer p, long now) {
        if ((now + (p.getUUID().hashCode() & 127)) % 100 != 37 || p.level().dimension() != Level.OVERWORLD) return;
        scan(p, now, 4);
    }

    /**
     * Finds the platforms round a player (at most {@code budget} new terrain samples: the search is cached, so the
     * next scans finish it), links them, and sends the lanes near them if those changed.
     */
    static void scan(ServerPlayer p, long now, int budget) {
        ServerLevel level = (ServerLevel) p.level();
        List<RuinLocator.Found> found = RuinLocator.near(level, p.blockPosition(), LOOK, List.of(RuinKind.SKY), null, false, 12, budget);
        // each platform's start chunk (its height comes from that chunk's random) and its deck's centre (a chunk over)
        List<ChunkPos> starts = new ArrayList<>();
        List<BlockPos> sky = new ArrayList<>();
        for (RuinLocator.Found f : found) {
            ChunkPos start = new ChunkPos(f.pos().getX() >> 4, f.pos().getZ() >> 4);
            BlockPos c = SkyRig.centre(start);
            // the city keeps its basin: no platform forms there
            if (dev.awaken.terrain.api.CityBasin.reserved(c.getX(), c.getZ())) continue;
            starts.add(start);
            sky.add(c);
        }
        boolean changed = false;
        for (int i = 0; i < sky.size(); i++) {
            BlockPos a = sky.get(i), best = null;
            int bi = -1;
            double bd = Windways.MAX_LINK;
            for (int j = 0; j < sky.size(); j++) {
                if (j == i) continue;
                BlockPos b = sky.get(j);
                double d = Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
                if (d < bd) {
                    bd = d;
                    best = b;
                    bi = j;
                }
            }
            if (best == null) continue;
            long key = key(a, best);
            Pair old = PAIRS.get(key);
            if (old == null) {
                changed = true;
                long seed = level.getSeed();
                int da = SkyRig.deckY(seed, starts.get(i)), db = SkyRig.deckY(seed, starts.get(bi));
                PAIRS.put(key, new Pair(Windways.between(a, da, best, db), now));
            } else {
                PAIRS.put(key, new Pair(old.ways(), now));
            }
        }
        changed |= PAIRS.values().removeIf(x -> now - x.seen() > FORGET);
        if (changed) {
            List<Windways.Way> all = new ArrayList<>();
            for (Pair x : PAIRS.values()) all.addAll(x.ways());
            Windways.setServer(all);
        }
        List<Windways.Way> near = new ArrayList<>();
        for (Windways.Way w : Windways.serverWays()) if (w.reach(p.getX(), p.getZ()) < NEAR) near.add(w);
        if (near.size() > MAX_SENT) {
            near.sort(Comparator.comparingDouble(w -> w.reach(p.getX(), p.getZ())));
            near = new ArrayList<>(near.subList(0, MAX_SENT));
        }
        int h = near.hashCode();
        Integer sent = SENT.get(p.getUUID());
        if (sent == null || sent != h) {
            SENT.put(p.getUUID(), h);
            AwakenNet.send(p, new AscentPayloads.Lanes(near));
        }
    }

    /** The same key whichever way round. */
    private static long key(BlockPos a, BlockPos b) {
        long ka = a.asLong(), kb = b.asLong();
        return ka < kb ? ka * 31 + kb : kb * 31 + ka;
    }

    /** Logged out or gone to another dimension: their lanes are sent afresh next time. */
    static void forget(UUID id) {
        SENT.remove(id);
    }

    static void clear() {
        PAIRS.clear();
        SENT.clear();
        Windways.setServer(List.of());
    }
}
