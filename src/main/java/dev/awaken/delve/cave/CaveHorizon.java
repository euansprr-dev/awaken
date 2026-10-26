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
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Horizon's sinkholes (PLAN 8.2: "Sinkhole · a way down"; the first one, by the Commons, "The Opening · a way
 * down"): found without generating anything, by walking the sinkholes' placement grid round the player and running each
 * candidate's own site search (the same answer worldgen gets), a couple of new candidates a call, cached for the session.
 * Shown from awakening, never at home or in a gate, until the player has stood at one.
 */
final class CaveHorizon {
    private CaveHorizon() {}

    static final int RANGE = 900, NEW_PER_CALL = 2;
    private static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("delve/sinkholes"));
    private static final ResourceKey<StructureSet> FIRST_SET = ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("delve/first_sinkhole"));
    private static final Map<Long, Optional<Spec>> FOUND = new ConcurrentHashMap<>();
    private static long seedFor;

    static void register() {
        HorizonHooks.register(CaveHorizon::sites);
    }

    static void clear() {
        FOUND.clear();
    }

    static List<HorizonHooks.Site> sites(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || !Progression.profile(p).awakened() || GateHooks.inGate(p)) return List.of();
        if (HearthHooks.inside(p.level(), p.blockPosition())) return List.of();
        ServerLevel level = p.level();
        if (level.getSeed() != seedFor) {
            FOUND.clear();
            seedFor = level.getSeed();
        }
        List<HorizonHooks.Site> out = new ArrayList<>();
        int[] budget = {NEW_PER_CALL};
        for (Spec s : near(level, p.blockPosition(), budget)) {
            String key = "awaken:discovered/cave/sinkhole/" + s.id();
            if (Progression.profile(p).counter(key) > 0) continue;
            boolean first = s.p(Sinkhole.FIRST) != 0;
            out.add(new HorizonHooks.Site("cave:" + s.id(), (first ? CaveNames.name(s) : "Sinkhole") + " · a way down",
                    new BlockPos(s.x(), s.p(Sinkhole.RIM), s.z()), "", first));
        }
        return out;
    }

    /** The sinkholes within {@link #RANGE} of {@code at} whose candidates have been searched (and up to budget new ones). */
    static List<Spec> near(ServerLevel level, BlockPos at, int[] budget) {
        List<Spec> out = new ArrayList<>();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var state = level.getChunkSource().getGeneratorState();
        // the first sinkhole, placed on purpose
        sets.get(FIRST_SET).ifPresent(set -> {
            if (set.value().placement() instanceof NearCityPlacement near) {
                ChunkPos c = near.target(state);
                Spec s = candidate(level, set.value(), c, true, budget);
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
                    Spec s = candidate(level, set.value(), c, false, budget);
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

    private static @Nullable Spec candidate(ServerLevel level, StructureSet set, ChunkPos c, boolean first, int[] budget) {
        Optional<Spec> known = FOUND.get(c.pack());
        if (known != null) return known.orElse(null);
        if (budget[0] <= 0) return null;
        budget[0]--;
        Spec found = null;
        for (var entry : set.structures()) {
            Structure st = entry.structure().value();
            if (!(st instanceof CaveStructure cs) || cs.kind() != CaveKind.SINKHOLE) continue;
            if (dev.awaken.terrain.api.CityBasin.reserved(c.getMinBlockX() + 8, c.getMinBlockZ() + 8)) break;
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            var ctx = new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(), rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()), rs,
                    level.getServer().getStructureTemplateManager(), level.getSeed(), c, level, st.biomes()::contains);
            found = CaveSites.find(ctx, CaveKind.SINKHOLE, first || cs.first());
            break;
        }
        FOUND.put(c.pack(), Optional.ofNullable(found));
        return found;
    }
}
