package dev.awaken.village.world;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The settlement's worldgen codecs: the structure type, its painter piece and terrain markers, and the grid placement.
 * The structure, its set and its biome tag are data in the Awakened Lands pack (new worlds only), beside the override
 * that empties vanilla's village set.
 */
public final class VillageWorldgen {
    private VillageWorldgen() {}

    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends StructurePlacement>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Awaken.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<VillageStructure>> TYPE = TYPES.register("settlement", () -> () -> VillageStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("settlement",
            () -> (StructurePieceType) (context, tag) -> new VillagePiece(tag));
    public static final DeferredHolder<StructurePieceType, StructurePieceType> MARKER = PIECES.register("settlement_marker",
            () -> (StructurePieceType) (context, tag) -> new VillageMarker(tag));
    public static final DeferredHolder<MapCodec<? extends StructurePlacement>, MapCodec<SettlementPlacement>> GRID = PLACEMENTS.register(
            "settlement_grid", () -> SettlementPlacement.CODEC);

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
        PLACEMENTS.register(modBus);
    }
}
