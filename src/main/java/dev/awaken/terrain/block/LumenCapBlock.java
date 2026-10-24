package dev.awaken.terrain.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lumen caps: a cluster of small glowing mushrooms on the Lumen Forest floor (cross model, emissive gills). Unlike a
 * vanilla mushroom it never spreads and does not care about light; it grows on any soil or sturdy top.
 */
public class LumenCapBlock extends VegetationBlock {
    /** Real light: a soft pool round each cluster at night, the forest floor's only lamps. */
    public static final int LIGHT = 4;
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 9.0);

    public LumenCapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.move(state.getOffset(pos));
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_VEGETATION) || state.isFaceSturdy(level, pos, Direction.UP);
    }
}
