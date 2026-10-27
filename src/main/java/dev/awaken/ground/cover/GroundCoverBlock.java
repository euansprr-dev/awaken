package dev.awaken.ground.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small things that lie on the ground (SPEC §31, P2): pebbles and twigs. No collision, broken by a touch, replaced by
 * whatever is placed over them, and they need a whole block under them (a stone, a path, gravel, dirt: never a slab's half
 * top). Not plants: they do not sway and need no {@code supports_vegetation} ground.
 */
public abstract class GroundCoverBlock extends VegetationBlock {
    private final VoxelShape shape;

    protected GroundCoverBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    protected VoxelShape shapeFor(BlockState state) {
        return this.shape;
    }
}
