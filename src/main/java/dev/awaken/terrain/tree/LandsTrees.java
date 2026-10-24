package dev.awaken.terrain.tree;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Awakened trees' shapes (registered placer and decorator types; the trees themselves are data in the lands pack,
 * {@code worldgen/feature/lands/*.json}) and the saplings' growers. A sapling only grows where its tree feature exists,
 * so in a world without the lands pack the saplings stay saplings.
 */
public final class LandsTrees {
    private LandsTrees() {}

    private static final DeferredRegister<TrunkPlacerType<?>> TRUNKS = DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, Awaken.MODID);
    private static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE = DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, Awaken.MODID);
    private static final DeferredRegister<TreeDecoratorType<?>> DECORATORS = DeferredRegister.create(Registries.TREE_DECORATOR_TYPE, Awaken.MODID);

    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<LimbedTrunkPlacer>> LIMBED_TRUNK =
            TRUNKS.register("limbed_trunk_placer", () -> new TrunkPlacerType<>(LimbedTrunkPlacer.CODEC));
    public static final DeferredHolder<FoliagePlacerType<?>, FoliagePlacerType<CloudFoliagePlacer>> CLOUD_FOLIAGE =
            FOLIAGE.register("cloud_foliage_placer", () -> new FoliagePlacerType<>(CloudFoliagePlacer.CODEC));
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<HangingVinesDecorator>> HANGING_VINES =
            DECORATORS.register("hanging_vines", () -> new TreeDecoratorType<>(HangingVinesDecorator.CODEC));

    public static final ResourceKey<Feature> ELDER_TREE = key("elder");
    public static final ResourceKey<Feature> YOUNG_ELDER_TREE = key("young_elder");
    public static final ResourceKey<Feature> LUMEN_TREE = key("lumen");
    public static final ResourceKey<Feature> SMALL_LUMEN_TREE = key("small_lumen");

    /** One elder sapling grows a young elder; four in a square, a full Elder. */
    public static final TreeGrower ELDER = new TreeGrower("awaken_elder", WeightedList.of(YOUNG_ELDER_TREE), WeightedList.of(ELDER_TREE),
            WeightedList.of(), YOUNG_ELDER_TREE);
    public static final TreeGrower LUMEN = new TreeGrower("awaken_lumen", WeightedList.of(SMALL_LUMEN_TREE), WeightedList.of(LUMEN_TREE),
            WeightedList.of(), SMALL_LUMEN_TREE);

    private static ResourceKey<Feature> key(String name) {
        return ResourceKey.create(Registries.FEATURE, Awaken.id("lands/" + name));
    }

    public static void init(IEventBus modBus) {
        TRUNKS.register(modBus);
        FOLIAGE.register(modBus);
        DECORATORS.register(modBus);
    }
}
