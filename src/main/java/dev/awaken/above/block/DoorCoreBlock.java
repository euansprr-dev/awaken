package dev.awaken.above.block;

import dev.awaken.above.Above;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.AboveSounds;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The heart of a sealed Precursor door. It holds the rank it waits for (block state {@link #RANK}, a {@link Rank}
 * ordinal). It hums when someone strong enough stands near; touched by them, the whole door folds away.
 */
public class DoorCoreBlock extends BaseEntityBlock {
    public static final IntegerProperty RANK = IntegerProperty.create("rank", 0, 6);
    private static final int MAX_DOOR = 48;

    public DoorCoreBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(RANK, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RANK);
    }

    public static Rank required(BlockState state) {
        return Rank.of(state.getValue(RANK));
    }

    public static boolean canOpen(Player player, BlockState core) {
        return Progression.rank(player).ordinal() >= core.getValue(RANK);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoorCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AboveBlocks.DOOR_CORE_BE.get(), DoorCoreBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) tryOpen(sp, level, pos);
        return InteractionResult.SUCCESS;
    }

    private static boolean isDoor(BlockState s) {
        return s.is(AboveBlocks.DOOR.get()) || s.is(AboveBlocks.DOOR_CORE.get());
    }

    /** Every door panel and core connected to {@code start}. */
    static List<BlockPos> doorBlocks(Level level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < MAX_DOOR) {
            BlockPos p = queue.poll();
            if (!isDoor(level.getBlockState(p))) continue;
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n)) queue.add(n);
            }
        }
        return out;
    }

    public static @Nullable BlockPos findCore(Level level, BlockPos panel) {
        for (BlockPos p : doorBlocks(level, panel)) if (level.getBlockState(p).is(AboveBlocks.DOOR_CORE.get())) return p;
        return null;
    }

    public static void tryOpen(ServerPlayer player, Level level, BlockPos core) {
        BlockState state = level.getBlockState(core);
        if (!state.is(AboveBlocks.DOOR_CORE.get())) return;
        Rank need = required(state);
        if (!canOpen(player, state)) {
            player.sendOverlayMessage(Component.literal("Sealed. It reads you and stays shut. Come back at " + need.label + "-rank."));
            level.playSound(null, core, AboveSounds.SCAN_FAIL.get(), SoundSource.BLOCKS, 0.7f, 0.6f);
            return;
        }
        open((ServerLevel) level, core);
        Progression.count(player, Above.DOORS_OPENED, 1);
        Progression.markNewThing(player, "precursor_door");
        Signal.toast(player, Component.literal("The door knows you."));
    }

    /** Folds the whole door away (no drops). */
    public static void open(ServerLevel level, BlockPos core) {
        List<BlockPos> blocks = doorBlocks(level, core);
        for (BlockPos p : blocks) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
        level.playSound(null, core, AboveSounds.DOOR_OPEN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
