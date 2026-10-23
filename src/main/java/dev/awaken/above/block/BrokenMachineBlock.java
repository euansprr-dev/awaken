package dev.awaken.above.block;

import dev.awaken.above.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A dead Precursor machine in a ruin. The Scanner reads which machine it was (its blueprint); after that it is only
 * salvage (scrap and circuits when broken).
 */
public class BrokenMachineBlock extends Block {
    public static final EnumProperty<MachineType> MACHINE = EnumProperty.create("machine", MachineType.class);
    public static final BooleanProperty SCANNED = BooleanProperty.create("scanned");
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public BrokenMachineBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MACHINE, MachineType.GATE_SCANNER).setValue(SCANNED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MACHINE, SCANNED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        if (player instanceof ServerPlayer sp) {
            if (state.getValue(SCANNED)) {
                sp.sendOverlayMessage(Component.literal("A dead " + state.getValue(MACHINE).displayName + ". Already read. Salvage it for parts."));
            } else {
                sp.sendOverlayMessage(Component.literal("A broken Precursor machine. A Scanner could read what it was."));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SCANNED) || random.nextInt(5) != 0) return;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                0, 0.02, 0);
    }
}
