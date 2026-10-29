package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sleeping bell (SPEC §43.7): a great bell half sunk where it fell, grey-green and dull. Strike it (use it, or hit it)
 * and, once nothing keeps it, it wakes: bronze again, and the place round it with it. It can't be broken.
 */
public final class SleepingBellBlock extends Block {
    public static final BooleanProperty AWAKE = BooleanProperty.create("awake");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public SleepingBellBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(AWAKE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(AWAKE);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) BellSites.strike(sl, pos, sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) BellSites.strike(sl, pos, sp);
    }
}
