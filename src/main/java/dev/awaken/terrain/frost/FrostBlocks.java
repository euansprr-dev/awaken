package dev.awaken.terrain.frost;

import dev.awaken.Awaken;
import dev.awaken.terrain.block.BlockSet;
import dev.awaken.terrain.block.CrystalClusterBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * Frost Spires' blocks (docs/modules/lands.md, "Frost Spires"): bluepine (blue-grey bark under a silver frost, needles
 * the blue of deep ice), frostspar (ice grown into crystal: the spires, the aurora's colours caught in its seams),
 * its slivers in the snow, and hoarfrost, the frozen ground snow never settles on. Glow is model emission only, never
 * block light. Stripping, fire and tags are data (tools/content/lands3_content.py).
 */
public final class FrostBlocks {
    private FrostBlocks() {}

    private static final BlockSet SET = new BlockSet();

    public static final ResourceKey<Feature> BLUEPINE_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/bluepine"));
    public static final ResourceKey<Feature> GIANT_BLUEPINE_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/giant_bluepine"));
    /** One sapling grows a bluepine; four in a square, a giant. */
    public static final TreeGrower BLUEPINE = new TreeGrower("awaken_bluepine", WeightedList.of(BLUEPINE_TREE),
            WeightedList.of(GIANT_BLUEPINE_TREE), WeightedList.of(), BLUEPINE_TREE);

    public static final DeferredBlock<RotatedPillarBlock> BLUEPINE_LOG = SET.log("bluepine_log", MapColor.COLOR_LIGHT_BLUE, MapColor.TERRACOTTA_CYAN);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_BLUEPINE_LOG = SET.log("stripped_bluepine_log", MapColor.COLOR_LIGHT_BLUE, MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<RotatedPillarBlock> BLUEPINE_WOOD = SET.log("bluepine_wood", MapColor.TERRACOTTA_CYAN, MapColor.TERRACOTTA_CYAN);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_BLUEPINE_WOOD = SET.log("stripped_bluepine_wood", MapColor.COLOR_LIGHT_BLUE, MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<Block> BLUEPINE_PLANKS = SET.planks("bluepine_planks", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<LeavesBlock> BLUEPINE_LEAVES = SET.leaves("bluepine_leaves", MapColor.COLOR_BLUE);
    public static final DeferredBlock<SaplingBlock> BLUEPINE_SAPLING = SET.sapling("bluepine_sapling", BLUEPINE);

    /** Ice grown into crystal: the spires; cold aurora light in its seams at night (model only). */
    public static final DeferredBlock<Block> FROSTSPAR = SET.withItem(SET.blocks.registerSimpleBlock("frostspar",
            p -> p.mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME).strength(1.5F, 4.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()));
    /** Slivers of frostspar standing in the snow, their tips glowing faintly (cross model with an emissive layer). */
    public static final DeferredBlock<CrystalClusterBlock> FROSTSPAR_CLUSTER = SET.withItem(SET.blocks.registerBlock("frostspar_cluster", CrystalClusterBlock::new,
            p -> p.mapColor(MapColor.ICE).noCollision().strength(0.3F).sound(SoundType.SMALL_AMETHYST_BUD)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    /** Frozen ground feathered with frost crystals: the biome's own ground, pale ice-blue from afar; snow never settles on it. */
    public static final DeferredBlock<Block> HOARFROST = SET.withItem(SET.blocks.registerSimpleBlock("hoarfrost",
            p -> p.mapColor(MapColor.ICE).strength(0.6F).sound(SoundType.SNOW)));

    /** Every Frost Spires block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(BLUEPINE_LOG, STRIPPED_BLUEPINE_LOG, BLUEPINE_WOOD, STRIPPED_BLUEPINE_WOOD, BLUEPINE_PLANKS, BLUEPINE_LEAVES,
                BLUEPINE_SAPLING, FROSTSPAR, FROSTSPAR_CLUSTER, HOARFROST);
    }

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
