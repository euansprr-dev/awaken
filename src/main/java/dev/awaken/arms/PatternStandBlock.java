package dev.awaken.arms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A place a weapon pattern is kept (docs/arms-progression/PLAN.md §3.4 door 2): built into a structure so it looks as if
 * it belongs, with the tier's weapon hung above it and the Blueprint's white line in its make. Use it with an empty hand
 * to take the pattern, once per fighter.
 */
public class PatternStandBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<StandKind> KIND = EnumProperty.create("kind", StandKind.class);
    private static final VoxelShape RACK = Block.box(0, 0, 3, 16, 14, 13), LECTERN = Block.box(1, 0, 1, 15, 12, 15),
            PLINTH = Block.box(2, 0, 2, 14, 13, 14), BIER = Block.box(0, 0, 0, 16, 8, 16);

    public PatternStandBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, StandKind.LECTERN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, KIND);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(KIND)) {
            case RACK -> RACK;
            case LECTERN -> LECTERN;
            case PLINTH -> PLINTH;
            case BIER -> BIER;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatternStandBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PatternStandBlockEntity be) || (be.style().isEmpty() && !be.pending())) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) ArmsStands.take(sp, sl, pos, be);
        return InteractionResult.SUCCESS;
    }
}
