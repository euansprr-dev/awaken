package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A stand of the Leaper's Gauntlet (SPEC §71.3): a low brass plate you step onto. Standing on it and holding Use for a
 * second takes the move it holds ({@link #MOVE}: the course sets which), once a player; what happens then is
 * {@link WayUp#take}. It holds nothing of its own: which stands a player has taken is theirs ({@link WayMemory}), so it
 * is lit for one runner and dark for the next. Set by the course's builder, unbreakable, never dropped.
 */
public class LeapStandBlock extends BaseEntityBlock {
    public static final EnumProperty<WayUp.Leap> MOVE = EnumProperty.create("move", WayUp.Leap.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public LeapStandBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MOVE, WayUp.Leap.DOUBLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(MOVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LeapStandBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // the client's side only: it tells the Lesson a stand is near (which is lit is each player's own)
        return level.isClientSide() ? createTickerHelper(type, AscentContent.LEAP_STAND_BE.get(), LeapStandBlockEntity::clientTick) : null;
    }
}
