package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A beam you hang bells from, high up (SPEC §43.16, stage 3): the Bell Beam (three logs and two iron nuggets; four hooks;
 * its bells sing only 8 or more blocks above your fire) and the Great Beam (three logs, two chains and an iron ingot; one
 * hook, for a Great Bell, 12 or more above). Build your tower any shape you like and lay these across it.
 */
public final class BellBeamBlock extends HookBlock {
    private static final Map<Direction, VoxelShape> BEAM = Shapes.rotateHorizontal(Block.box(0, 13, 4, 16, 16, 12));
    private static final Map<Direction, VoxelShape> GREAT = Shapes.rotateHorizontal(Block.box(0, 12, 5, 16, 16, 11));

    private final boolean great;

    public BellBeamBlock(BlockBehaviour.Properties p, boolean great) {
        super(p);
        this.great = great;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return (great ? GREAT : BEAM).get(s.getValue(FACING));
    }
}
