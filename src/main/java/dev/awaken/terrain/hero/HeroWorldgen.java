package dev.awaken.terrain.hero;

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

/**
 * The hero-tree structure type and piece (the structures and their sets are data in the lands pack:
 * {@code worldgen/structure/<tree>.json}, {@code worldgen/structure_set/<tree>.json}; where they grow is the biome
 * table's {@code awaken:has_structure/<tree>}), and the blueprints of the trees being generated.
 */
public final class HeroWorldgen {
    private HeroWorldgen() {}

    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Awaken.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<HeroStructure>> TYPE = TYPES.register("hero_tree", () -> () -> HeroStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("hero_tree",
            () -> (StructurePieceType) (context, tag) -> new HeroPiece(tag));

    private record Key(HeroTree tree, long seed) {}

    /**
     * A tree spans many chunks, generated one at a time on worker threads (and again by Distant Horizons): its
     * blueprint is built once and kept while they come through. Thread-safe; a dropped one is simply rebuilt.
     */
    private static final LoadingCache<Key, Blueprint> BLUEPRINTS = CacheBuilder.newBuilder().maximumSize(12)
            .build(CacheLoader.from(k -> k.tree().build(k.seed())));

    public static Blueprint blueprint(HeroTree tree, long seed) {
        return BLUEPRINTS.getUnchecked(new Key(tree, seed));
    }

    public static ResourceKey<Structure> structure(HeroTree tree) {
        return ResourceKey.create(Registries.STRUCTURE, Awaken.id(tree.getSerializedName()));
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }
}
