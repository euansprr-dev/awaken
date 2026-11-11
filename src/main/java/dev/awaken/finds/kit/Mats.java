package dev.awaken.finds.kit;

import dev.awaken.living.region.RegionMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stone and wood of the land a find stands in (SPEC §58.8), by the job each does. A find is built from these, so
 * the same kind looks like it belongs in a desert, a taiga or a swamp.
 */
public record Mats(BlockState stone, BlockState mossy, BlockState slab, BlockState stairs, BlockState wall, BlockState log, BlockState planks,
                   BlockState fence, BlockState woodSlab, BlockState path, BlockState loose) {

    private static Mats of(net.minecraft.world.level.block.Block stone, net.minecraft.world.level.block.Block mossy, net.minecraft.world.level.block.Block slab,
                           net.minecraft.world.level.block.Block stairs, net.minecraft.world.level.block.Block wall, net.minecraft.world.level.block.Block log,
                           net.minecraft.world.level.block.Block planks, net.minecraft.world.level.block.Block fence, net.minecraft.world.level.block.Block woodSlab,
                           net.minecraft.world.level.block.Block path, net.minecraft.world.level.block.Block loose) {
        return new Mats(stone.defaultBlockState(), mossy.defaultBlockState(), slab.defaultBlockState(), stairs.defaultBlockState(), wall.defaultBlockState(),
                log.defaultBlockState(), planks.defaultBlockState(), fence.defaultBlockState(), woodSlab.defaultBlockState(), path.defaultBlockState(), loose.defaultBlockState());
    }

    private static final Mats TEMPERATE = of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_WALL,
            Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.OAK_SLAB, Blocks.DIRT_PATH, Blocks.COARSE_DIRT);
    private static final Mats COLD = of(Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE_SLAB, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_WALL,
            Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_SLAB, Blocks.GRAVEL, Blocks.COARSE_DIRT);
    private static final Mats DRY = of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL,
            Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.ACACIA_SLAB, Blocks.SMOOTH_SANDSTONE, Blocks.SAND);
    private static final Mats RED = of(Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_WALL,
            Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.ACACIA_SLAB, Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SAND);
    private static final Mats WET = of(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_STAIRS,
            Blocks.MOSSY_COBBLESTONE_WALL, Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_SLAB, Blocks.PACKED_MUD, Blocks.MUD);
    private static final Mats GREEN = of(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_STAIRS,
            Blocks.MOSSY_STONE_BRICK_WALL, Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_SLAB, Blocks.PODZOL, Blocks.COARSE_DIRT);
    private static final Mats SHADE = of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_WALL,
            Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_SLAB, Blocks.PODZOL, Blocks.COARSE_DIRT);

    public static Mats of(RegionMap.Group g) {
        return switch (g) {
            case SNOW, MOUNTAIN -> COLD;
            case DESERT -> DRY;
            case BADLANDS -> RED;
            case SWAMP -> WET;
            case JUNGLE -> GREEN;
            case DARK, MUSHROOM -> SHADE;
            default -> TEMPERATE;
        };
    }
}
