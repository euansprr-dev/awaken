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
    private static final int MAX_UNCACHED = 8;
    private static long cachedSeed;

    private static long key(ChunkPos chunk, RuinKind kind) {
        return chunk.pack() * 31 + kind.ordinal();
    }

    private static boolean cached(ServerLevel level, RuinKind kind, ChunkPos chunk) {
        if (level.getSeed() != cachedSeed) {
            VALID.clear();
            cachedSeed = level.getSeed();
        }
        return VALID.containsKey(key(chunk, kind));
    }

    private static boolean valid(ServerLevel level, RuinKind kind, Holder<Structure> structure, ChunkPos chunk) {
        return VALID.computeIfAbsent(key(chunk, kind), k -> {
            ChunkGenerator gen = level.getChunkSource().getGenerator();
            // the spaced kinds are asked exactly as generation asks (the rule's neighbours must get the same answer there)
            if (RuinSpacing.spaced(kind)) return RuinSpacing.validAt(kind, chunk, structure.value(), gen, level.getChunkSource().randomState(), level,
                    level.uncachedBiomeResolver());
            int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
            Heightmap.Types hm = kind == RuinKind.OCEAN ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
            int y = gen.getFirstOccupiedHeight(x, z, hm, level, level.getChunkSource().randomState());
            Holder<net.minecraft.world.level.biome.Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
            return structure.value().biomes().contains(biome);
        });
    }

    // ---- land made before the rule (SPEC §17.11 "Room between them") ------------------------------------------------

    /** Spots the rule passes over, as their saved chunks have them: whether a start of the kind stands there. */
    private static final Map<Long, Boolean> OLD = new ConcurrentHashMap<>();
    private static final java.util.Set<Long> ASKED = ConcurrentHashMap.newKeySet();

    /** The rule was turned off or on (a dev's seam check): what the saved chunks said is asked again. */
    static void forgetOldLand() {
        OLD.clear();
        ASKED.clear();
    }

    /** The server stopped: the next world may share the seed but not the save. */
    public static void forgetSave() {
        forgetOldLand();
        VALID.clear();
        cachedSeed = 0;
    }

    /**
     * Whether a spot the rule passes over holds a start all the same: land made before the rule keeps what stands in it.
     * A loaded chunk is read at once; a saved one off the server thread (the answer is false until it comes, so a later
     * call finds it); a chunk never made holds nothing.
     */
    static boolean standsFromBefore(ServerLevel level, RuinKind kind, Holder<Structure> structure, ChunkPos chunk) {
        long k = key(chunk, kind);
        Boolean known = OLD.get(k);
        if (known != null) return known;
        var loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
        if (loaded != null) {
            var start = loaded.getStartForStructure(structure.value());
            boolean stands = start != null && start.isValid();
            OLD.put(k, stands);
            return stands;
        }
        if (ASKED.add(k)) {
            String id = kind.structure.identifier().toString();
            level.getChunkSource().chunkMap.read(chunk)
                    .thenAccept(tag -> OLD.put(k, tag.map(t -> holdsStart(t, id)).orElse(false)))
                    .exceptionally(e -> {
                        ASKED.remove(k);
                        return null;
                    });
        }
        return false;
    }

    /** Whether a saved chunk's data holds a valid start of this structure. */
    static boolean holdsStart(net.minecraft.nbt.CompoundTag chunk, String structureId) {
        return structureId.equals(chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty(structureId).getStringOr("id", ""));
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
        return near(level, origin, radius, kinds, data, undiscoveredOnly, max, MAX_UNCACHED);
    }

    /** As above, with an explicit budget of uncached terrain samples (commands may spend more). */
    public static List<Found> near(ServerLevel level, BlockPos origin, int radius, List<RuinKind> kinds, @Nullable AboveData data,
                                   boolean undiscoveredOnly, int max, int maxUncached) {
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
        // Each uncached candidate costs a terrain height sample; cap them per call (results are cached, so repeated
        // calls finish the job) to keep one call to a few milliseconds.
        int[] checks = {0};
        RuinSpacing.Grids grids = RuinSpacing.Grids.of(level.registryAccess(), state.getLevelSeed());
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        // the spacing rule's neighbours cost a sample each too, from the same budget
        RuinSpacing.Ground ground = (k, chunk) -> {
            var s = structures.get(k.structure);
            if (s.isEmpty()) return false;
            if (!cached(level, k, chunk)) {
                if (checks[0] >= maxUncached) return null;
                checks[0]++;
            }
            return valid(level, k, s.get(), chunk);
        };
        for (Candidate c : candidates) {
            if (out.size() >= max) break;
            BlockPos p = center(c.chunk(), origin.getY());
            boolean discovered = data != null && data.hasRuinNear(p, 48);
            if (undiscoveredOnly && discovered) continue;
            if (!cached(level, c.kind(), c.chunk())) {
                if (checks[0] >= maxUncached) continue;
                checks[0]++;
            }
            if (!valid(level, c.kind(), c.structure(), c.chunk())) continue;
            if (grids != null && RuinSpacing.spaced(c.kind())) {
                Boolean keeps = RuinSpacing.keeps(c.kind(), c.chunk(), grids, ground);
                if (keeps == null) continue; // out of samples: a later call finishes it (they are cached)
                if (!keeps && !standsFromBefore(level, c.kind(), c.structure(), c.chunk())) continue;
            }
            out.add(new Found(c.kind(), p, discovered, c.dist()));
        }
        return out;
    }
}
