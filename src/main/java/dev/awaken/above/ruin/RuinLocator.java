package dev.awaken.above.ruin;

import dev.awaken.above.AboveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds Precursor ruins without generating chunks: walks the structure sets' placement grids (pure maths), then checks
 * the start biome for the nearest candidates only. Results per chunk are cached for the session. Used by the
 * Scanner's sky omens, the Observatory and {@code /awaken above ruin locate}.
 */
public final class RuinLocator {
    private RuinLocator() {}

    public record Found(RuinKind kind, BlockPos pos, boolean discovered, double distance) {}

    private static final Map<Long, Boolean> VALID = new ConcurrentHashMap<>();
    private static long cachedSeed;

    private static boolean valid(ServerLevel level, RuinKind kind, Holder<Structure> structure, ChunkPos chunk) {
        long seed = level.getSeed();
        if (seed != cachedSeed) {
            VALID.clear();
            cachedSeed = seed;
        }
        long key = chunk.pack() * 31 + kind.ordinal();
        return VALID.computeIfAbsent(key, k -> {
            int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
            ChunkGenerator gen = level.getChunkSource().getGenerator();
            Heightmap.Types hm = kind == RuinKind.OCEAN ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
            int y = gen.getFirstOccupiedHeight(x, z, hm, level, level.getChunkSource().randomState());
            Holder<net.minecraft.world.level.biome.Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
            return structure.value().biomes().contains(biome);
        });
    }

    /** Approximate centre of a ruin that starts in this chunk. */
    public static BlockPos center(ChunkPos chunk, int y) {
        return new BlockPos(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
    }

    /** The guaranteed vault's chunk in this level, or null if its structure set is missing. */
    public static @Nullable ChunkPos spawnVault(ServerLevel level) {
        var set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(RuinKind.SPAWN_VAULT.set);
        if (set.isEmpty() || !(set.get().value().placement() instanceof SpawnRingPlacement p)) return null;
        return p.target(level.getChunkSource().getGeneratorState());
    }

    /**
     * Ruins of the given kinds within {@code radius} blocks, nearest first, at most {@code max}. If {@code data} is
     * given, ruins the player already found are flagged (and skipped when {@code undiscoveredOnly}).
     */
    public static List<Found> near(ServerLevel level, BlockPos origin, int radius, List<RuinKind> kinds, @Nullable AboveData data,
                                   boolean undiscoveredOnly, int max) {
        if (level.dimension() != Level.OVERWORLD) return List.of();
        record Candidate(RuinKind kind, Holder<Structure> structure, ChunkPos chunk, double dist) {}
        List<Candidate> candidates = new ArrayList<>();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        for (RuinKind kind : kinds) {
            var holder = sets.get(kind.set);
            if (holder.isEmpty()) continue;
            StructureSet set = holder.get().value();
            if (set.structures().isEmpty()) continue;
            Holder<Structure> structure = set.structures().get(0).structure();
            StructurePlacement placement = set.placement();
            if (placement instanceof SpawnRingPlacement ring) {
                ChunkPos c = ring.target(state);
                double d = Math.sqrt(center(c, origin.getY()).distSqr(origin));
                if (d <= radius) candidates.add(new Candidate(kind, structure, c, d));
            } else if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = spread.spacing();
                int cx0 = Math.floorDiv(origin.getX() - radius, 16), cx1 = Math.floorDiv(origin.getX() + radius, 16);
                int cz0 = Math.floorDiv(origin.getZ() - radius, 16), cz1 = Math.floorDiv(origin.getZ() + radius, 16);
                for (int gx = Math.floorDiv(cx0, spacing); gx <= Math.floorDiv(cx1, spacing); gx++) {
                    for (int gz = Math.floorDiv(cz0, spacing); gz <= Math.floorDiv(cz1, spacing); gz++) {
                        ChunkPos c = spread.getPotentialStructureChunk(state.getLevelSeed(), gx * spacing, gz * spacing);
                        double d = Math.sqrt(center(c, origin.getY()).distSqr(origin));
                        if (d > radius) continue;
                        if (!placement.isStructureChunk(state, c.x(), c.z())) continue;
                        candidates.add(new Candidate(kind, structure, c, d));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::dist));
        List<Found> out = new ArrayList<>();
        int checks = 0;
        for (Candidate c : candidates) {
            if (out.size() >= max || checks >= 24) break;
            BlockPos p = center(c.chunk(), origin.getY());
            boolean discovered = data != null && data.hasRuinNear(p, 48);
            if (undiscoveredOnly && discovered) continue;
            checks++;
            if (!valid(level, c.kind(), c.structure(), c.chunk())) continue;
            out.add(new Found(c.kind(), p, discovered, c.dist()));
        }
        return out;
    }
}
