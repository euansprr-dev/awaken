package dev.awaken.world.fauna.lair;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The lair structure type and piece (the structures, structure sets and biome tags themselves are data:
 * {@code data/awaken/worldgen/structure/lair/<beast>.json}, {@code .../structure_set/lair/<beast>.json},
 * {@code tags/worldgen/biome/has_lair/<beast>.json}).
 */
public final class LairWorldgen {
    private LairWorldgen() {}

    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Awaken.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<LairStructure>> TYPE = TYPES.register("fauna_lair", () -> () -> LairStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("fauna_lair",
            () -> (StructurePieceType) (context, tag) -> new LairPiece(tag));

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }

    public static ResourceKey<Structure> structure(String beast) {
        return ResourceKey.create(Registries.STRUCTURE, Awaken.id("lair/" + beast));
    }

    public static ResourceKey<StructureSet> set(String beast) {
        return ResourceKey.create(Registries.STRUCTURE_SET, Awaken.id("lair/" + beast));
    }
}
