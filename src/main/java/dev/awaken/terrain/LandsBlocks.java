package dev.awaken.terrain;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.terrain.block.LumenCapBlock;
import dev.awaken.terrain.tree.LandsTrees;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
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
 * The Awakened Lands' blocks (docs/modules/lands.md): Elderwood's pale elder and Lumen Forest's ash-white lumen, their
 * leaves, saplings and ground. Blocks exist in every world (an old world simply never generates them); stripping,
 * flammability and tags are data ({@code neoforge/data_maps/block/*}, {@code src/main/merge/terrain}). Lumen glow is
 * model emission only ({@code light_emission} faces), never block light, except the caps' small light.
 */
public final class LandsBlocks {
    private LandsBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    private static final List<DeferredItem<BlockItem>> TAB = new ArrayList<>();

    // ---- Elderwood ---------------------------------------------------------------------------------------------------
    public static final DeferredBlock<RotatedPillarBlock> ELDER_LOG = log("elder_log", MapColor.SAND, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_ELDER_LOG = log("stripped_elder_log", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<RotatedPillarBlock> ELDER_WOOD = log("elder_wood", MapColor.QUARTZ, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_ELDER_WOOD = log("stripped_elder_wood", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<Block> ELDER_PLANKS = planks("elder_planks", MapColor.SAND);
    public static final DeferredBlock<LeavesBlock> ELDER_LEAVES = leaves("elder_leaves", MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredBlock<SaplingBlock> ELDER_SAPLING = sapling("elder_sapling", LandsTrees.ELDER);

    // ---- Lumen Forest ------------------------------------------------------------------------------------------------
    public static final DeferredBlock<RotatedPillarBlock> LUMEN_LOG = log("lumen_log", MapColor.CLAY, MapColor.SNOW);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LUMEN_LOG = log("stripped_lumen_log", MapColor.CLAY, MapColor.CLAY);
    public static final DeferredBlock<RotatedPillarBlock> LUMEN_WOOD = log("lumen_wood", MapColor.SNOW, MapColor.SNOW);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LUMEN_WOOD = log("stripped_lumen_wood", MapColor.CLAY, MapColor.CLAY);
    /** The Lumen Spire's bark: glowing veins wind up it (emissive model faces, no block light). */
    public static final DeferredBlock<RotatedPillarBlock> VEINED_LUMEN_LOG = log("veined_lumen_log", MapColor.CLAY, MapColor.SNOW);
    public static final DeferredBlock<Block> LUMEN_PLANKS = planks("lumen_planks", MapColor.CLAY);
    public static final DeferredBlock<LeavesBlock> LUMEN_LEAVES = leaves("lumen_leaves", MapColor.WARPED_WART_BLOCK);
    public static final DeferredBlock<SaplingBlock> LUMEN_SAPLING = sapling("lumen_sapling", LandsTrees.LUMEN);
    public static final DeferredBlock<CarpetBlock> LUMEN_MOSS = withItem(BLOCKS.registerBlock("lumen_moss", CarpetBlock::new,
            p -> p.mapColor(MapColor.WARPED_NYLIUM).strength(0.1F).sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.POPPED)));
    /** Small glowing mushrooms: the only Lumen block with real light (a soft pool round each cluster at night). */
    public static final DeferredBlock<LumenCapBlock> LUMEN_CAP = withItem(BLOCKS.registerBlock("lumen_cap", LumenCapBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).noCollision().instabreak().sound(SoundType.FUNGUS).lightLevel(s -> LumenCapBlock.LIGHT)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    /** Hanging vines from Lumen canopies; the last block of each strand ends in a glowing bulb (emissive model, no light). */
    public static final DeferredBlock<HangingMossBlock> LUMEN_VINES = withItem(BLOCKS.registerBlock("lumen_vines", HangingMossBlock::new,
            p -> p.mapColor(MapColor.WARPED_WART_BLOCK).noCollision().replaceable().sound(SoundType.CAVE_VINES).ignitedByLava()
                    .pushReaction(PushReaction.POPPED)));

    private static DeferredBlock<RotatedPillarBlock> log(String name, MapColor top, MapColor side) {
        return withItem(BLOCKS.registerBlock(name, RotatedPillarBlock::new,
                p -> p.mapColor(s -> s.getValue(RotatedPillarBlock.AXIS) == net.minecraft.core.Direction.Axis.Y ? top : side)
                        .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    private static DeferredBlock<Block> planks(String name, MapColor color) {
        return withItem(BLOCKS.registerSimpleBlock(name,
                p -> p.mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    /** Vanilla leaf behaviour (decay, persistence, waterlogging); no vanilla falling-leaf particles (the lands' own pool owns those). */
    private static DeferredBlock<LeavesBlock> leaves(String name, MapColor color) {
        return withItem(BLOCKS.registerBlock(name, p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
                p -> p.mapColor(color).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
                        .isValidSpawn((s, l, pos, type) -> false).isSuffocating((s, l, pos) -> false)
                        .ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, pos) -> false)));
    }

    private static DeferredBlock<SaplingBlock> sapling(String name, net.minecraft.world.level.block.grower.TreeGrower grower) {
        return withItem(BLOCKS.registerBlock(name, p -> new SaplingBlock(grower, p),
                p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED)));
    }

    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block) {
        TAB.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    /** Every Awakened Lands block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(ELDER_LOG, STRIPPED_ELDER_LOG, ELDER_WOOD, STRIPPED_ELDER_WOOD, ELDER_PLANKS, ELDER_LEAVES, ELDER_SAPLING,
                LUMEN_LOG, STRIPPED_LUMEN_LOG, LUMEN_WOOD, STRIPPED_LUMEN_WOOD, VEINED_LUMEN_LOG, LUMEN_PLANKS, LUMEN_LEAVES, LUMEN_SAPLING,
                LUMEN_MOSS, LUMEN_CAP, LUMEN_VINES);
    }

    static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(LandsBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : TAB) e.accept(item.get());
    }
}
