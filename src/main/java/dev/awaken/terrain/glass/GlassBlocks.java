package dev.awaken.terrain.glass;

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
 * Glass Steppe's blocks (docs/modules/lands.md, "Glass Steppe"): bleached glassbark and its amber leaves, the fused
 * glass the old gates left in the ground, sunshard (glass grown into crystal: the spires) and its little clusters in the
 * grass, and the scorched glass at a Gate Scar's heart. Glow is model emission only (amber specks and cracks), never
 * block light. Stripping, fire and tags are data (tools/content/lands2_content.py).
 */
public final class GlassBlocks {
    private GlassBlocks() {}

    private static final BlockSet SET = new BlockSet();

    public static final ResourceKey<Feature> GLASSBARK_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/glassbark"));
    public static final ResourceKey<Feature> TALL_GLASSBARK_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/tall_glassbark"));
    /** One sapling grows a glassbark; four in a square, a tall one. */
    public static final TreeGrower GLASSBARK = new TreeGrower("awaken_glassbark", WeightedList.of(GLASSBARK_TREE),
            WeightedList.of(TALL_GLASSBARK_TREE), WeightedList.of(), GLASSBARK_TREE);

    public static final DeferredBlock<RotatedPillarBlock> GLASSBARK_LOG = SET.log("glassbark_log", MapColor.TERRACOTTA_WHITE, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_GLASSBARK_LOG = SET.log("stripped_glassbark_log", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<RotatedPillarBlock> GLASSBARK_WOOD = SET.log("glassbark_wood", MapColor.QUARTZ, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_GLASSBARK_WOOD = SET.log("stripped_glassbark_wood", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<Block> GLASSBARK_PLANKS = SET.planks("glassbark_planks", MapColor.SAND);
    public static final DeferredBlock<LeavesBlock> GLASSBARK_LEAVES = SET.leaves("glassbark_leaves", MapColor.COLOR_ORANGE);
    public static final DeferredBlock<SaplingBlock> GLASSBARK_SAPLING = SET.sapling("glassbark_sapling", GLASSBARK);

    /** Sand melted to glass and cooled into a crackle: the steppe's bare ground (opaque, cheap, white from afar). */
    public static final DeferredBlock<Block> FUSED_GLASS = SET.withItem(SET.blocks.registerSimpleBlock("fused_glass",
            p -> p.mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.HAT).strength(1.0F, 3.0F).sound(SoundType.GLASS)));
    /** Glass grown into crystal: what the shard spires are made of; amber specks glow in it at night (model only). */
    public static final DeferredBlock<Block> SUNSHARD = SET.withItem(SET.blocks.registerSimpleBlock("sunshard",
            p -> p.mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.CHIME).strength(1.5F, 4.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()));
    /** Little slivers of sunshard in the grass, their tips glowing faintly (cross model with an emissive layer). */
    public static final DeferredBlock<CrystalClusterBlock> SUNSHARD_CLUSTER = SET.withItem(SET.blocks.registerBlock("sunshard_cluster", CrystalClusterBlock::new,
            p -> p.mapColor(MapColor.QUARTZ).noCollision().strength(0.3F).sound(SoundType.SMALL_AMETHYST_BUD)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    /** The heart of a Gate Scar: sand fused black, cracks still holding the gate's amber light (model only). */
    public static final DeferredBlock<Block> SCORCHED_GLASS = SET.withItem(SET.blocks.registerSimpleBlock("scorched_glass",
            p -> p.mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0F, 6.0F).sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops()));

    /** Every Glass Steppe block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(GLASSBARK_LOG, STRIPPED_GLASSBARK_LOG, GLASSBARK_WOOD, STRIPPED_GLASSBARK_WOOD, GLASSBARK_PLANKS, GLASSBARK_LEAVES,
                GLASSBARK_SAPLING, FUSED_GLASS, SUNSHARD, SUNSHARD_CLUSTER, SCORCHED_GLASS);
    }

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
