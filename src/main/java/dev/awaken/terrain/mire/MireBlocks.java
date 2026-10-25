package dev.awaken.terrain.mire;

import dev.awaken.Awaken;
import dev.awaken.terrain.block.BlockSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * Mirror Marsh's blocks (docs/modules/lands.md, "Mirror Marsh"): weepwood (pale silver bark, narrow silver-sage leaves)
 * and the tendrils that hang from it in curtains to the water, moonblooms (pale lilies on the black water whose petals
 * glow at night: model only, no block light, so the marsh stays dark) and mirepeat, the dark wet ground. Stripping,
 * fire and tags are data (tools/content/lands3_content.py).
 */
public final class MireBlocks {
    private MireBlocks() {}

    private static final BlockSet SET = new BlockSet();

    public static final ResourceKey<Feature> WEEPWOOD_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/weepwood"));
    public static final ResourceKey<Feature> GREAT_WEEPWOOD_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/great_weepwood"));
    /** One sapling grows a weepwood; four in a square, a great one. */
    public static final TreeGrower WEEPWOOD = new TreeGrower("awaken_weepwood", WeightedList.of(WEEPWOOD_TREE),
            WeightedList.of(GREAT_WEEPWOOD_TREE), WeightedList.of(), WEEPWOOD_TREE);

    public static final DeferredBlock<RotatedPillarBlock> WEEPWOOD_LOG = SET.log("weepwood_log", MapColor.TERRACOTTA_WHITE, MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_WEEPWOOD_LOG = SET.log("stripped_weepwood_log", MapColor.TERRACOTTA_LIGHT_GREEN, MapColor.TERRACOTTA_LIGHT_GREEN);
    public static final DeferredBlock<RotatedPillarBlock> WEEPWOOD_WOOD = SET.log("weepwood_wood", MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_WEEPWOOD_WOOD = SET.log("stripped_weepwood_wood", MapColor.TERRACOTTA_LIGHT_GREEN, MapColor.TERRACOTTA_LIGHT_GREEN);
    public static final DeferredBlock<Block> WEEPWOOD_PLANKS = SET.planks("weepwood_planks", MapColor.TERRACOTTA_LIGHT_GREEN);
    public static final DeferredBlock<LeavesBlock> WEEPWOOD_LEAVES = SET.leaves("weepwood_leaves", MapColor.TERRACOTTA_GREEN);
    public static final DeferredBlock<SaplingBlock> WEEPWOOD_SAPLING = SET.sapling("weepwood_sapling", WEEPWOOD);
    /** Strands hanging in curtains from weepwood canopies down to the water; each ends in a pale catkin. */
    public static final DeferredBlock<HangingMossBlock> WEEPWOOD_TENDRILS = SET.withItem(SET.blocks.registerBlock("weepwood_tendrils", HangingMossBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_GREEN).noCollision().replaceable().sound(SoundType.HANGING_ROOTS).ignitedByLava()
                    .pushReaction(PushReaction.POPPED)));
    /** A pale lily on the black water; its petals glow at night (emissive model, no light). Placed on water like a lily pad. */
    public static final DeferredBlock<LilyPadBlock> MOONBLOOM = SET.withItem(SET.blocks.registerBlock("moonbloom", LilyPadBlock::new,
            p -> p.mapColor(MapColor.SNOW).instabreak().sound(SoundType.LILY_PAD).noOcclusion().pushReaction(PushReaction.POPPED)),
            PlaceOnWaterBlockItem::new);
    /** Wet peat threaded with silver moss: the marsh's own ground, dark from afar, under the water as well as beside it. */
    public static final DeferredBlock<Block> MIREPEAT = SET.withItem(SET.blocks.registerSimpleBlock("mirepeat",
            p -> p.mapColor(MapColor.TERRACOTTA_BLACK).strength(0.6F).sound(SoundType.MUD)));

    /** Every Mirror Marsh block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(WEEPWOOD_LOG, STRIPPED_WEEPWOOD_LOG, WEEPWOOD_WOOD, STRIPPED_WEEPWOOD_WOOD, WEEPWOOD_PLANKS, WEEPWOOD_LEAVES,
                WEEPWOOD_SAPLING, WEEPWOOD_TENDRILS, MOONBLOOM, MIREPEAT);
    }

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
