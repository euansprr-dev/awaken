package dev.awaken.kin.works;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Chime Perch (SPEC §43.12): a post, a cross-bar for the wrens and a woken bell's clapper hung under it. The Rookery's heart. */
public final class ChimePerchBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(6, 0, 6, 10, 14, 10), Block.box(1, 12, 6, 15, 14, 10));

    public ChimePerchBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
