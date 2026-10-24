package dev.awaken.terrain;

import dev.awaken.terrain.block.BlockSet;
import dev.awaken.terrain.block.LumenCapBlock;
import dev.awaken.terrain.tree.LandsTrees;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * The Awakened Lands' blocks (docs/modules/lands.md): Elderwood's pale elder and Lumen Forest's ash-white lumen, their
 * leaves, saplings and ground. Blocks exist in every world (an old world simply never generates them); stripping,
 * flammability and tags are data ({@code neoforge/data_maps/block/*}, {@code src/main/merge/terrain}). Lumen glow is
 * model emission only ({@code light_emission} faces), never block light, except the caps' small light.
 */
public final class LandsBlocks {
    private LandsBlocks() {}

    private static final BlockSet SET = new BlockSet();

    // ---- Elderwood ---------------------------------------------------------------------------------------------------
    public static final DeferredBlock<RotatedPillarBlock> ELDER_LOG = SET.log("elder_log", MapColor.SAND, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_ELDER_LOG = SET.log("stripped_elder_log", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<RotatedPillarBlock> ELDER_WOOD = SET.log("elder_wood", MapColor.QUARTZ, MapColor.QUARTZ);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_ELDER_WOOD = SET.log("stripped_elder_wood", MapColor.SAND, MapColor.SAND);
    public static final DeferredBlock<Block> ELDER_PLANKS = SET.planks("elder_planks", MapColor.SAND);
    public static final DeferredBlock<LeavesBlock> ELDER_LEAVES = SET.leaves("elder_leaves", MapColor.COLOR_LIGHT_GREEN);
    /** The warm gold tone of crown tops (elder canopies mix it in by noise; the Colossus's high masses). */
    public static final DeferredBlock<LeavesBlock> ELDER_LEAVES_GOLD = SET.leaves("elder_leaves_gold", MapColor.GOLD);
    public static final DeferredBlock<SaplingBlock> ELDER_SAPLING = SET.sapling("elder_sapling", LandsTrees.ELDER);

    // ---- Lumen Forest ------------------------------------------------------------------------------------------------
    public static final DeferredBlock<RotatedPillarBlock> LUMEN_LOG = SET.log("lumen_log", MapColor.CLAY, MapColor.SNOW);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LUMEN_LOG = SET.log("stripped_lumen_log", MapColor.CLAY, MapColor.CLAY);
    public static final DeferredBlock<RotatedPillarBlock> LUMEN_WOOD = SET.log("lumen_wood", MapColor.SNOW, MapColor.SNOW);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LUMEN_WOOD = SET.log("stripped_lumen_wood", MapColor.CLAY, MapColor.CLAY);
    /** The Lumen Spire's bark: glowing veins wind up it (emissive model faces, no block light). */
    public static final DeferredBlock<RotatedPillarBlock> VEINED_LUMEN_LOG = SET.log("veined_lumen_log", MapColor.CLAY, MapColor.SNOW);
    public static final DeferredBlock<Block> LUMEN_PLANKS = SET.planks("lumen_planks", MapColor.CLAY);
    public static final DeferredBlock<LeavesBlock> LUMEN_LEAVES = SET.leaves("lumen_leaves", MapColor.WARPED_WART_BLOCK);
    public static final DeferredBlock<SaplingBlock> LUMEN_SAPLING = SET.sapling("lumen_sapling", LandsTrees.LUMEN);
    public static final DeferredBlock<CarpetBlock> LUMEN_MOSS = SET.withItem(SET.blocks.registerBlock("lumen_moss", CarpetBlock::new,
            p -> p.mapColor(MapColor.WARPED_NYLIUM).strength(0.1F).sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.POPPED)));
    /** Small glowing mushrooms: the only Lumen block with real light (a soft pool round each cluster at night). */
    public static final DeferredBlock<LumenCapBlock> LUMEN_CAP = SET.withItem(SET.blocks.registerBlock("lumen_cap", LumenCapBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).noCollision().instabreak().sound(SoundType.FUNGUS).lightLevel(s -> LumenCapBlock.LIGHT)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    /** Hanging vines from Lumen canopies; the last block of each strand ends in a glowing bulb (emissive model, no light). */
    public static final DeferredBlock<HangingMossBlock> LUMEN_VINES = SET.withItem(SET.blocks.registerBlock("lumen_vines", HangingMossBlock::new,
            p -> p.mapColor(MapColor.WARPED_WART_BLOCK).noCollision().replaceable().sound(SoundType.CAVE_VINES).ignitedByLava()
                    .pushReaction(PushReaction.POPPED)));

    /** Every Awakened Lands block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(ELDER_LOG, STRIPPED_ELDER_LOG, ELDER_WOOD, STRIPPED_ELDER_WOOD, ELDER_PLANKS, ELDER_LEAVES, ELDER_LEAVES_GOLD, ELDER_SAPLING,
                LUMEN_LOG, STRIPPED_LUMEN_LOG, LUMEN_WOOD, STRIPPED_LUMEN_WOOD, VEINED_LUMEN_LOG, LUMEN_PLANKS, LUMEN_LEAVES, LUMEN_SAPLING,
                LUMEN_MOSS, LUMEN_CAP, LUMEN_VINES);
    }

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
