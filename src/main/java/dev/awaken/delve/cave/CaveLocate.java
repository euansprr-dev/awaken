package dev.awaken.delve.cave;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the nearest real place of a kind is, without generating anything: its structure set's placement grid walked
 * outward, each candidate's own site search run (the same answer worldgen gets), nearest candidate first. For
 * {@code /awaken delve goto} (tours: the places the world really made) and the Horizon.
 */
final class CaveLocate {
    private CaveLocate() {}

    static ResourceKey<StructureSet> set(CaveKind kind) {
        return ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("delve/" + kind.id + "s"));
    }

    static final ResourceKey<StructureSet> FIRST = ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("delve/first_sinkhole"));

    /** The nearest place of {@code kind} whose start lies within {@code radius} of {@code at}, or null. */
    static @Nullable Spec nearest(ServerLevel level, BlockPos at, CaveKind kind, int radius) {
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var state = level.getChunkSource().getGeneratorState();
        record Candidate(ChunkPos chunk, StructureSet set, boolean first, double d) {}
        List<Candidate> all = new ArrayList<>();
        if (kind == CaveKind.SINKHOLE) {
            sets.get(FIRST).ifPresent(h -> {
                if (h.value().placement() instanceof NearCityPlacement near) {
                    ChunkPos c = near.target(state);
                    all.add(new Candidate(c, h.value(), true, dist(c, at)));
                }
            });
        }
        sets.get(set(kind)).ifPresent(h -> {
            if (!(h.value().placement() instanceof RandomSpreadStructurePlacement rsp)) return;
            int cx = at.getX() >> 4, cz = at.getZ() >> 4, r = (radius >> 4) + rsp.spacing();
            for (int gx = Math.floorDiv(cx - r, rsp.spacing()); gx <= Math.floorDiv(cx + r, rsp.spacing()); gx++) {
                for (int gz = Math.floorDiv(cz - r, rsp.spacing()); gz <= Math.floorDiv(cz + r, rsp.spacing()); gz++) {
                    ChunkPos c = rsp.getPotentialStructureChunk(level.getSeed(), gx * rsp.spacing(), gz * rsp.spacing());
                    if (dist(c, at) > radius || !rsp.isStructureChunk(state, c.x(), c.z())) continue;
                    all.add(new Candidate(c, h.value(), false, dist(c, at)));
                }
            }
        });
        all.sort(Comparator.comparingDouble(Candidate::d));
        for (Candidate c : all) {
            Spec s = site(level, c.set(), c.chunk(), kind, c.first());
            if (s != null) return s;
        }
        return null;
    }

    private static double dist(ChunkPos c, BlockPos at) {
        double dx = c.getMiddleBlockX() - at.getX(), dz = c.getMiddleBlockZ() - at.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The place a candidate start chunk really holds, or null (no site, or the city's reserve). */
    static @Nullable Spec site(ServerLevel level, StructureSet set, ChunkPos c, CaveKind kind, boolean first) {
        if (dev.awaken.terrain.api.CityBasin.reserved(c.getMinBlockX() + 8, c.getMinBlockZ() + 8)) return null;
        for (var entry : set.structures()) {
            Structure st = entry.structure().value();
            if (!(st instanceof CaveStructure cs) || cs.kind() != kind) continue;
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            var ctx = new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(),
                    rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()), rs,
                    level.getServer().getStructureTemplateManager(), level.getSeed(), c, level, st.biomes()::contains);
            return CaveSites.find(ctx, kind, first || cs.first());
        }
        return null;
    }
}
