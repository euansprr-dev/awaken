package dev.awaken.veil.block;

import dev.awaken.veil.VeilContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A door of spirit matter. Physical things pass through it either way; a closed one stops a spirit. Touching one toggles
 * the whole connected pane. Locked doors (Veil Gates) only open by their puzzle; any ghost door also follows redstone.
 */
public class GhostDoorBlock extends Block implements SpiritUsable {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    private static final int MAX_PANE = 48;

    public GhostDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, POWERED, LOCKED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) SpiritUsable.ghostOnly(sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean spiritUse(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer spirit) {
        if (state.getValue(LOCKED)) {
            dev.awaken.signal.Signal.toast(spirit, Component.translatable("block.awaken.ghost_door.locked"));
            return false;
        }
        setPane(level, pos, !state.getValue(OPEN));
        return true;
    }

    /** Opens or closes every ghost door connected to {@code start}. Returns the positions changed. */
    public static List<BlockPos> setPane(Level level, BlockPos start, boolean open) {
        List<BlockPos> pane = pane(level, start);
        for (BlockPos p : pane) {
            BlockState s = level.getBlockState(p);
            if (s.getValue(OPEN) != open) level.setBlock(p, s.setValue(OPEN, open), Block.UPDATE_CLIENTS);
        }
        if (!pane.isEmpty() && level instanceof ServerLevel sl) {
            BlockPos c = pane.get(pane.size() / 2);
            sl.playSound(null, c, VeilContent.TOUCH.get(), SoundSource.BLOCKS, 0.8F, open ? 0.7F : 0.5F);
            sl.sendParticles(ParticleTypes.GLOW, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 10, 0.6, 0.6, 0.6, 0.02);
        }
        return pane;
    }

    public static List<BlockPos> pane(Level level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockState(start).getBlock() instanceof GhostDoorBlock)) return out;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && out.size() < MAX_PANE) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && level.getBlockState(n).getBlock() instanceof GhostDoorBlock) queue.add(n);
            }
        }
        return out;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
            if (!state.getValue(LOCKED)) setPane(level, pos, signal);
        }
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbor, Direction direction) {
        return neighbor.getBlock() == this && neighbor.getValue(OPEN) == state.getValue(OPEN) || super.skipRendering(state, neighbor, direction);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
