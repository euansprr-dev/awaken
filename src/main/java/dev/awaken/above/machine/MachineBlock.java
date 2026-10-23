package dev.awaken.above.machine;

import dev.awaken.above.AboveBlocks;
import dev.awaken.above.MachineType;
import dev.awaken.above.power.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Every built Precursor machine. {@code type} is null for the Fabricator (crafted at a table, needs no power). */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final @Nullable MachineType type;

    public MachineBlock(BlockBehaviour.Properties p, @Nullable MachineType type) {
        super(p);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, type == null));
    }

    public @Nullable MachineType type() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide() || type == null) return null;
        return createTickerHelper(beType, AboveBlocks.MACHINE_BE.get(), MachineBlockEntity::serverTick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // index a new reactor at once (onLoad only runs on the next block-entity tick)
        if (type == MachineType.REACTOR && level instanceof ServerLevel sl && !oldState.is(this)) Power.add(sl, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!(level instanceof ServerLevel sl) || !(level.getBlockEntity(pos) instanceof MachineBlockEntity be)) return;
        if (by instanceof ServerPlayer sp) be.setOwner(sp.getUUID());
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) be.setLabel(custom.getString());
        if (type == MachineType.WAYPOINT_PYLON && by instanceof ServerPlayer sp) {
            PylonNetwork.get(sl.getServer()).add(sp.getUUID(), sl, pos, be.label());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (type == MachineType.REACTOR && level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            InteractionResult r = Machines.feedReactor(player, be, stack);
            if (r != InteractionResult.PASS) return r;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MachineBlockEntity be) Machines.open(sp, be);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(type == MachineType.REACTOR ? 2 : 6) != 0) return;
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.6 * Math.abs(f.getStepZ());
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.6 * Math.abs(f.getStepX());
        level.addParticle(type == MachineType.REACTOR ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.ELECTRIC_SPARK,
                type == MachineType.REACTOR ? pos.getX() + 0.2 + random.nextDouble() * 0.6 : x, pos.getY() + (type == MachineType.REACTOR ? 1.05 : 0.3 + random.nextDouble() * 0.5),
                type == MachineType.REACTOR ? pos.getZ() + 0.2 + random.nextDouble() * 0.6 : z, 0, 0.01, 0);
    }
}
