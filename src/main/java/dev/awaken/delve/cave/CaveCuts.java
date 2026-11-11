package dev.awaken.delve.cave;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the cave places that open at the surface (a mouth, a sinkhole, a mine's adit) cut the land, without generating
 * anything (SPEC §34.1, "Ground others stand on"): each candidate start round a box has its own site search run, the answer
 * worldgen gets, and the place's box is tested. A place fits within {@link #FIT} chunks of its start, so no candidate
 * further out can reach the box. Found places are remembered per start, so asking again round the same land is cheap.
 * Safe from any thread.
 */
public final class CaveCuts {
    private CaveCuts() {}

    /** No cave place reaches further than this from its start chunk ({@link CaveSites}' fit). */
    static final int FIT = 8;
    private static final CaveKind[] OPEN = {CaveKind.MOUTH, CaveKind.SINKHOLE, CaveKind.MINE};
    /** Past this many starts the memory is dropped and refilled (a few thousand regions' worth). */
    private static final int KEEP = 20_000;

    private static final ConcurrentHashMap<Long, Optional<Spec>> SEEN = new ConcurrentHashMap<>();
    private static volatile @Nullable Object owner;

    /** Whether the box of a cave place open to the sky crosses (x0..x1, y0..y1, z0..z1). */
    public static boolean crosses(ServerLevel level, int x0, int z0, int x1, int z1, int y0, int y1) {
        Object gen = level.getChunkSource().getGenerator();
        if (owner != gen || SEEN.size() > KEEP) {
            SEEN.clear();
            owner = gen;
        }
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var state = level.getChunkSource().getGeneratorState();
        int cx0 = (x0 >> 4) - FIT, cx1 = (x1 >> 4) + FIT, cz0 = (z0 >> 4) - FIT, cz1 = (z1 >> 4) + FIT;
        var first = sets.get(CaveLocate.FIRST);
        if (first.isPresent() && first.get().value().placement() instanceof NearCityPlacement near) {
            ChunkPos c = near.target(state);
            if (c.x() >= cx0 && c.x() <= cx1 && c.z() >= cz0 && c.z() <= cz1
                    && crosses(spec(level, first.get().value(), c, CaveKind.SINKHOLE, true), x0, z0, x1, z1, y0, y1)) return true;
        }
        for (CaveKind kind : OPEN) {
            var h = sets.get(CaveLocate.set(kind));
            if (h.isEmpty() || !(h.get().value().placement() instanceof RandomSpreadStructurePlacement rsp)) continue;
            int sp = rsp.spacing();
            for (int gx = Math.floorDiv(cx0, sp); gx <= Math.floorDiv(cx1, sp); gx++) {
                for (int gz = Math.floorDiv(cz0, sp); gz <= Math.floorDiv(cz1, sp); gz++) {
                    ChunkPos c = rsp.getPotentialStructureChunk(level.getSeed(), gx * sp, gz * sp);
                    if (c.x() < cx0 || c.x() > cx1 || c.z() < cz0 || c.z() > cz1 || !rsp.isStructureChunk(state, c.x(), c.z())) continue;
                    if (crosses(spec(level, h.get().value(), c, kind, false), x0, z0, x1, z1, y0, y1)) return true;
                }
            }
        }
        return false;
    }

    private static boolean crosses(@Nullable Spec s, int x0, int z0, int x1, int z1, int y0, int y1) {
        return s != null && s.box().intersects(x0, y0, z0, x1, y1, z1);
    }

    private static @Nullable Spec spec(ServerLevel level, StructureSet set, ChunkPos c, CaveKind kind, boolean first) {
        long key = c.pack() << 4 | (long) kind.ordinal() << 1 | (first ? 1 : 0);
        return SEEN.computeIfAbsent(key, k -> Optional.ofNullable(CaveLocate.site(level, set, c, kind, first))).orElse(null);
    }
}
