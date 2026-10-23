package dev.awaken.above;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.above.halo.HaloChunkGenerator;
import dev.awaken.above.ruin.PrecursorRuinPiece;
import dev.awaken.above.ruin.PrecursorRuinStructure;
import dev.awaken.above.ruin.SpawnRingPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen codecs: the Precursor ruin structure + piece, the spawn-ring placement and the Halo's chunk generator. */
public final class AboveWorldgen {
    private AboveWorldgen() {}

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends StructurePlacement>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<PrecursorRuinStructure>> RUIN_TYPE = STRUCTURE_TYPES.register("precursor_ruin",
            () -> () -> PrecursorRuinStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUIN_PIECE = PIECES.register("precursor_ruin",
            () -> (StructurePieceType) (context, tag) -> new PrecursorRuinPiece(tag));
    public static final DeferredHolder<MapCodec<? extends StructurePlacement>, MapCodec<SpawnRingPlacement>> SPAWN_RING = PLACEMENTS.register(
            "precursor_spawn_ring", () -> SpawnRingPlacement.CODEC);
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<HaloChunkGenerator>> HALO_GENERATOR = GENERATORS.register(
            "halo_station", () -> HaloChunkGenerator.CODEC);

    public static void init(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECES.register(modBus);
        PLACEMENTS.register(modBus);
        GENERATORS.register(modBus);
    }
}
