package dev.awaken.delve.cave;

import dev.awaken.Awaken;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Horizon's sinkholes (PLAN 8.2: "Sinkhole · a way down"; the first one, by the Commons, "The Opening · a way
 * down"): found without generating anything, by walking the sinkholes' placement grid round the player and running each
 * candidate's own site search (the same answer worldgen gets), cached for the session. The search plans lode regions and
 * samples noise columns, seconds of work under a flying player (SPEC §28.9), so it runs on a worker of its own, a couple
 * of new candidates a call: the server thread only reads what's done, and a candidate not searched yet has no sinkhole.
 * Shown from awakening, never at home or in a gate, until the player has stood at one.
 */
final class CaveHorizon {
    private CaveHorizon() {}

    static final int RANGE = 900, NEW_PER_CALL = 2;
    /** Searches waiting at most: past it a fast flight asks again later, nearer, rather than queueing the whole way. */
    static final int QUEUED_MAX = 8;
    private static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("delve/sinkholes"));
    private static final ResourceKey<StructureSet> FIRST_SET = ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("delve/first_sinkhole"));
    private static final Map<Long, Optional<Spec>> FOUND = new ConcurrentHashMap<>();
    /** Candidates handed to the worker and not answered yet. */
    private static final Set<Long> ASKED = ConcurrentHashMap.newKeySet();
    /** Bumped by {@link #clear}: an answer from before it is dropped. */
    private static final AtomicInteger GEN = new AtomicInteger();
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "Awaken cave horizon");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile long seedFor;

    /** A candidate's answer: the sinkhole its start chunk holds, or null. */
    interface Lookup {
        @Nullable Spec at(StructureSet set, ChunkPos c, boolean first);
    }

    static void register() {
        HorizonHooks.register(CaveHorizon::sites);
    }

    static void clear() {
        GEN.incrementAndGet();
        FOUND.clear();
        ASKED.clear();
    }

    /** The server is stopping: the waiting searches go (the one running finishes and is dropped). */
    static void stopping() {
        WORKER.getQueue().clear();
        clear();
    }

    static List<HorizonHooks.Site> sites(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || !Progression.profile(p).awakened() || GateHooks.inGate(p)) return List.of();
        if (HearthHooks.inside(p.level(), p.blockPosition())) return List.of();
        ServerLevel level = p.level();
        if (level.getSeed() != seedFor) {
            clear();
            seedFor = level.getSeed();
        }
        List<HorizonHooks.Site> out = new ArrayList<>();
        for (Spec s : near(level, p.blockPosition(), new int[]{NEW_PER_CALL})) {
            String key = "awaken:discovered/cave/sinkhole/" + s.id();
            if (Progression.profile(p).counter(key) > 0) continue;
            boolean first = s.p(Sinkhole.FIRST) != 0;
            out.add(new HorizonHooks.Site("cave:" + s.id(), (first ? CaveNames.name(s) : "Sinkhole") + " · a way down",
                    new BlockPos(s.x(), s.p(Sinkhole.RIM), s.z()), "", first));
        }
        return out;
    }

    /** The sinkholes within {@link #RANGE} of {@code at} whose candidates have been searched (up to budget new ones asked). */
    static List<Spec> near(ServerLevel level, BlockPos at, int[] budget) {
        return near(level, at, (set, c, first) -> known(level, set, c, first, budget));
    }

    /** Candidates round {@code at} not answered yet (the test waits on it). */
    static int unknown(ServerLevel level, BlockPos at) {
        int[] n = {0};
        near(level, at, (set, c, first) -> {
            if (!FOUND.containsKey(c.pack())) n[0]++;
            return null;
        });
        return n[0];
    }

    /** Every candidate round {@code at} through {@code lookup}, the ones that hold a sinkhole within range kept. */
    static List<Spec> near(ServerLevel level, BlockPos at, Lookup lookup) {
        List<Spec> out = new ArrayList<>();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var state = level.getChunkSource().getGeneratorState();
        // the first sinkhole, placed on purpose
        sets.get(FIRST_SET).ifPresent(set -> {
            if (set.value().placement() instanceof NearCityPlacement near) {
                ChunkPos c = near.target(state);
                Spec s = lookup.at(set.value(), c, true);
                if (s != null && close(s, at)) out.add(s);
            }
        });
        sets.get(SET).ifPresent(set -> {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement rsp)) return;
            int cx = at.getX() >> 4, cz = at.getZ() >> 4, r = (RANGE >> 4) + rsp.spacing();
            for (int gx = Math.floorDiv(cx - r, rsp.spacing()); gx <= Math.floorDiv(cx + r, rsp.spacing()); gx++) {
                for (int gz = Math.floorDiv(cz - r, rsp.spacing()); gz <= Math.floorDiv(cz + r, rsp.spacing()); gz++) {
                    ChunkPos c = rsp.getPotentialStructureChunk(level.getSeed(), gx * rsp.spacing(), gz * rsp.spacing());
                    double dx = (c.getMiddleBlockX() - at.getX()), dz = (c.getMiddleBlockZ() - at.getZ());
                    if (dx * dx + dz * dz > (RANGE + 160.0) * (RANGE + 160.0)) continue;
                    if (!rsp.isStructureChunk(state, c.x(), c.z())) continue;
                    Spec s = lookup.at(set.value(), c, false);
                    if (s != null && close(s, at)) out.add(s);
                }
            }
        });
        return out;
    }

    private static boolean close(Spec s, BlockPos at) {
        double dx = s.x() - at.getX(), dz = s.z() - at.getZ();
        return dx * dx + dz * dz <= (double) RANGE * RANGE;
    }

    /** The candidate's answer if the worker has given it; otherwise asks for it (within budget) and says none yet. */
    private static @Nullable Spec known(ServerLevel level, StructureSet set, ChunkPos c, boolean first, int[] budget) {
        long key = c.pack();
        Optional<Spec> known = FOUND.get(key);
        if (known != null) return known.orElse(null);
        if (budget[0] <= 0 || ASKED.size() >= QUEUED_MAX || !ASKED.add(key)) return null;
        budget[0]--;
        int gen = GEN.get();
        WORKER.execute(() -> {
            Optional<Spec> got;
            try {
                got = Optional.ofNullable(search(level, set, c, first));
            } catch (RuntimeException e) {
                Awaken.LOG.warn("[delve] the Horizon's sinkhole search at {} failed: {}", c, e.toString());
                got = Optional.empty();
            }
            if (GEN.get() != gen) return;
            FOUND.put(key, got);
            ASKED.remove(key);
        });
        return null;
    }

    /** The sinkhole a candidate start chunk holds, or null: its own site search, as worldgen runs it (any thread). */
    static @Nullable Spec search(ServerLevel level, StructureSet set, ChunkPos c, boolean first) {
        for (var entry : set.structures()) {
            Structure st = entry.structure().value();
            if (!(st instanceof CaveStructure cs) || cs.kind() != CaveKind.SINKHOLE) continue;
            if (dev.awaken.terrain.api.CityBasin.reserved(c.getMinBlockX() + 8, c.getMinBlockZ() + 8)) return null;
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            var ctx = new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(), rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()), rs,
                    level.getServer().getStructureTemplateManager(), level.getSeed(), c, level, st.biomes()::contains);
            return CaveSites.find(ctx, CaveKind.SINKHOLE, first || cs.first());
        }
        return null;
    }
}
