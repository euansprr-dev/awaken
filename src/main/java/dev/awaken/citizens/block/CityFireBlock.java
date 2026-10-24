package dev.awaken.citizens.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A house fire in the Works (CityEvents): flames that never spread and never burn anything by themselves; the event
 * owns them, the bucket line puts them out, and a player can beat them out. Strength 0..3 (smaller as it dies down).
 */
public class CityFireBlock extends Block {
    public static final IntegerProperty FLAME = IntegerProperty.create("flame", 0, 3);

    public CityFireBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FLAME, 3));
    }

    public static int light(BlockState s) {
        return 9 + s.getValue(FLAME) * 2;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FLAME);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, 2 + state.getValue(FLAME) * 3, 16);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
