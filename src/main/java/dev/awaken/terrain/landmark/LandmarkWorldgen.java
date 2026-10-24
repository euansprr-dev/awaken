package dev.awaken.terrain.landmark;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;

/**
 * The landmark structure type and piece ({@code awaken:landmark}; the structures and their sets are data in the lands
 * pack, {@code worldgen/structure/<landmark>.json}; where they stand is the biome table's
 * {@code awaken:has_structure/<landmark>}), and the shapes of the landmarks being generated.
 */
public final class LandmarkWorldgen {
    private LandmarkWorldgen() {}

    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Awaken.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<LandmarkStructure>> TYPE = TYPES.register("landmark", () -> () -> LandmarkStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("landmark",
            () -> (StructurePieceType) (context, tag) -> new LandmarkPiece(tag));

    private record DriftKey(long seed, float angle, int[] ground) {
        @Override
        public boolean equals(Object o) {
            return o instanceof DriftKey k && k.seed == seed && k.angle == angle && Arrays.equals(k.ground, ground);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(seed) * 31 + Float.hashCode(angle) * 17 + Arrays.hashCode(ground);
        }
    }

    /**
     * A landmark spans many chunks, generated one at a time on worker threads (and again by Distant Horizons): its
     * shape is built once and kept while they come through. Thread-safe; a dropped one is simply rebuilt.
     */
    private static final LoadingCache<Long, GateScar.Plan> SCARS = CacheBuilder.newBuilder().maximumSize(8)
            .build(CacheLoader.from(seed -> {
                long t0 = System.nanoTime();
                GateScar.Plan p = GateScar.plan(seed);
                LandsClock.add("gate_scar shape", System.nanoTime() - t0);
                return p;
            }));
    private static final LoadingCache<Long, AuroraSpire.Plan> SPIRES = CacheBuilder.newBuilder().maximumSize(8)
            .build(CacheLoader.from(seed -> {
                long t0 = System.nanoTime();
                AuroraSpire.Plan p = AuroraSpire.plan(seed);
                LandsClock.add("aurora_spire shape", System.nanoTime() - t0);
                return p;
            }));
    private static final LoadingCache<DriftKey, Voxels> DRIFTS = CacheBuilder.newBuilder().maximumSize(8)
            .build(CacheLoader.from(k -> {
                long t0 = System.nanoTime();
                Voxels v = ShardDrift.build(k.seed(), k.angle(), k.ground());
                LandsClock.add("shard_drift shape", System.nanoTime() - t0);
                return v;
            }));

    static GateScar.Plan scar(long seed) {
        return SCARS.getUnchecked(seed);
    }

    static AuroraSpire.Plan spire(long seed) {
        return SPIRES.getUnchecked(seed);
    }

    static Voxels drift(long seed, float angle, int[] ground) {
        return DRIFTS.getUnchecked(new DriftKey(seed, angle, ground));
    }

    public static ResourceKey<Structure> structure(Landmark landmark) {
        return ResourceKey.create(Registries.STRUCTURE, Awaken.id(landmark.getSerializedName()));
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }
}
