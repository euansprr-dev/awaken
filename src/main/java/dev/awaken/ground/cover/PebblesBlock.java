package dev.awaken.ground.cover;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One to three small stones on the ground: {@link #KIND} 0 stone, 1 andesite, 2 granite, 3 diorite. */
public final class PebblesBlock extends GroundCoverBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 3);
    public static final IntegerProperty STONES = IntegerProperty.create("stones", 1, 3);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 3, 13);

    public PebblesBlock(Properties properties) {
        super(properties, SHAPE);
        registerDefaultState(defaultBlockState().setValue(KIND, 0).setValue(STONES, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, STONES);
    }
}
