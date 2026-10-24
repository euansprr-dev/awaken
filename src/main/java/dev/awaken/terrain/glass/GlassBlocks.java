package dev.awaken.terrain.glass;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Glass Steppe's blocks (docs/modules/lands.md, "Glass Steppe"): bleached glassbark and its amber leaves, the fused
 * glass the old gates left in the ground, sunshard (glass grown into crystal: the spires) and its little clusters in the
 * grass, and the scorched glass at a Gate Scar's heart. Glow is model emission only (amber specks and cracks), never
 * block light. Stripping, fire and tags are data (tools/content/lands2_content.py).
 */
public final class GlassBlocks {
    private GlassBlocks() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    private static final List<DeferredItem<BlockItem>> TAB = new ArrayList<>();

    public static final ResourceKey<Feature> GLASSBARK_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/glassbark"));
    public static final ResourceKey<Feature> TALL_GLASSBARK_TREE = ResourceKey.create(Registries.FEATURE, Awaken.id("lands/tall_glassbark"));
    /** One sapling grows a glassbark; four in a square, a tall one. */
    public static final TreeGrower GLASSBARK = new TreeGrower("awaken_glassbark", WeightedList.of(GLASSBARK_TREE),
            WeightedList.of(TALL_GLASSBARK_TREE), WeightedList.of(), GLASSBARK_TREE);

    public static final DeferredBlock<RotatedPillarBlock> GLASSBARK_LOG = log("glassbark_log", MapColor.TERRACOTTA_WHITE, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_GLASSBARK_LOG = log("stripped_glassbark_log", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<RotatedPillarBlock> GLASSBARK_WOOD = log("glassbark_wood", MapColor.QUARTZ, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_GLASSBARK_WOOD = log("stripped_glassbark_wood", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<Block> GLASSBARK_PLANKS = withItem(BLOCKS.registerSimpleBlock("glassbark_planks",
            p -> p.mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final DeferredBlock<LeavesBlock> GLASSBARK_LEAVES = withItem(BLOCKS.registerBlock("glassbark_leaves",
            p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
                    .isValidSpawn((s, l, pos, type) -> false).isSuffocating((s, l, pos) -> false)
                    .ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, pos) -> false)));
    public static final DeferredBlock<SaplingBlock> GLASSBARK_SAPLING = withItem(BLOCKS.registerBlock("glassbark_sapling",
            p -> new SaplingBlock(GLASSBARK, p),
            p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED)));

    /** Sand melted to glass and cooled into a crackle: the steppe's bare ground (opaque, cheap, white from afar). */
    public static final DeferredBlock<Block> FUSED_GLASS = withItem(BLOCKS.registerSimpleBlock("fused_glass",
            p -> p.mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.HAT).strength(1.0F, 3.0F).sound(SoundType.GLASS)));
    /** Glass grown into crystal: what the shard spires are made of; amber specks glow in it at night (model only). */
    public static final DeferredBlock<Block> SUNSHARD = withItem(BLOCKS.registerSimpleBlock("sunshard",
            p -> p.mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.CHIME).strength(1.5F, 4.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()));
    /** Little slivers of sunshard in the grass, their tips glowing faintly (cross model with an emissive layer). */
    public static final DeferredBlock<ShardClusterBlock> SUNSHARD_CLUSTER = withItem(BLOCKS.registerBlock("sunshard_cluster", ShardClusterBlock::new,
            p -> p.mapColor(MapColor.QUARTZ).noCollision().strength(0.3F).sound(SoundType.SMALL_AMETHYST_BUD)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    /** The heart of a Gate Scar: sand fused black, cracks still holding the gate's amber light (model only). */
    public static final DeferredBlock<Block> SCORCHED_GLASS = withItem(BLOCKS.registerSimpleBlock("scorched_glass",
            p -> p.mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0F, 6.0F).sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops()));

    private static DeferredBlock<RotatedPillarBlock> log(String name, MapColor top, MapColor side) {
        return withItem(BLOCKS.registerBlock(name, RotatedPillarBlock::new,
                p -> p.mapColor(s -> s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? top : side)
                        .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block) {
        TAB.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    /** Every Glass Steppe block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(GLASSBARK_LOG, STRIPPED_GLASSBARK_LOG, GLASSBARK_WOOD, STRIPPED_GLASSBARK_WOOD, GLASSBARK_PLANKS, GLASSBARK_LEAVES,
                GLASSBARK_SAPLING, FUSED_GLASS, SUNSHARD, SUNSHARD_CLUSTER, SCORCHED_GLASS);
    }

    static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(GlassBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : TAB) e.accept(item.get());
    }
}
