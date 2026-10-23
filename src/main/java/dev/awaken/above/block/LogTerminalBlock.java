package dev.awaken.above.block;

import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.scan.Scanning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Precursor Log lectern. Reading it (hand or Scanner) adds a log to your Codex. */
public class LogTerminalBlock extends Block {
    public static final EnumProperty<PrecursorLogs.Pool> POOL = EnumProperty.create("pool", PrecursorLogs.Pool.class);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(5, 2, 5, 11, 11, 11),
            Block.box(1, 11, 1, 15, 14, 15));

    public LogTerminalBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(POOL, PrecursorLogs.Pool.UNKNOWN).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POOL, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) Scanning.readTerminal(sp, pos, state);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.95,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
    }
}
