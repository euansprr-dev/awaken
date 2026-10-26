package dev.awaken.living.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pot hung over the Wayfarers' fire on its tripod (SPEC §27.6): it sits in the block above the campfire, its legs
 * reaching down past the flames to the ground round it. You walk through it; only the pot itself can be hit.
 */
public final class CampPot extends Block {
    private static final VoxelShape POT = Block.box(4.0, 0.0, 4.0, 12.0, 7.0, 12.0);

    public CampPot(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return POT;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
