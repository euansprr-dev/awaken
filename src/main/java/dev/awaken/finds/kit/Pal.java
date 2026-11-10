package dev.awaken.finds.kit;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A place's materials by job (SPEC §59.5): the wall's body and its second stone, the foot course, the trim, the floor,
 * the roof's stair and slab, and its timber. A place picks one or two of these and never builds a face from one block.
 */
public record Pal(BlockState wall, BlockState wallB, BlockState base, BlockState trim, BlockState floor, BlockState stair, BlockState slab,
                  BlockState log, BlockState plank, BlockState fence, BlockState pstair, BlockState pslab) {
    private static Pal of(Block wall, Block wallB, Block base, Block trim, Block floor, Block stair, Block slab, Block log, Block plank, Block fence, Block pstair, Block pslab) {
        return new Pal(wall.defaultBlockState(), wallB.defaultBlockState(), base.defaultBlockState(), trim.defaultBlockState(), floor.defaultBlockState(),
                stair.defaultBlockState(), slab.defaultBlockState(), log.defaultBlockState(), plank.defaultBlockState(), fence.defaultBlockState(),
                pstair.defaultBlockState(), pslab.defaultBlockState());
    }

    /** The same palette under another roof. */
    public Pal roof(Block stair, Block slab) {
        return new Pal(wall, wallB, base, trim, floor, stair.defaultBlockState(), slab.defaultBlockState(), log, plank, fence, pstair, pslab);
    }

    /** The same palette on another foot course. */
    public Pal base(Block b) {
        return new Pal(wall, wallB, b.defaultBlockState(), trim, floor, stair, slab, log, plank, fence, pstair, pslab);
    }

    /** The same palette with other walls. */
    public Pal walls(Block wall, Block wallB) {
        return new Pal(wall.defaultBlockState(), wallB.defaultBlockState(), base, trim, floor, stair, slab, log, plank, fence, pstair, pslab);
    }

    /** Dressed stone under a dark timber roof. */
    public static final Pal STONE = of(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_ANDESITE,
            Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
    /** The same stone long abandoned: mossy, with a stone roof where any is left. */
    public static final Pal RUIN = of(Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
    /** Field stone: a farm, a mill, a dry-stone wall. */
    public static final Pal COBBLE = of(Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE,
            Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_LOG, Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.OAK_STAIRS, Blocks.OAK_SLAB);
    /** A timber house on a stone foot. */
    public static final Pal TIMBER = of(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS,
            Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
    public static final Pal SAND = of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE,
            Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB);
    public static final Pal MUD = of(Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.PACKED_MUD, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA,
            Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB);
    public static final Pal BRICK = of(Blocks.BRICKS, Blocks.GRANITE, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.DEEPSLATE_TILES,
            Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
    /** White walls, dark frames, a tiled roof: mountain cells and an observatory. */
    public static final Pal WHITE = of(Blocks.CALCITE, Blocks.DIORITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
            Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB);
    public static final Pal JUNGLE = of(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE, Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE,
            Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB);
    public static final Pal MARSH = of(Blocks.MANGROVE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.MANGROVE_LOG, Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS,
            Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB);
    public static final Pal DARK = of(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES,
            Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
}
