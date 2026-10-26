package dev.awaken.above.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A panel of a sealed Precursor door. The door core among the panels decides who may pass; touching a panel asks it.
 * Each panel carries its door's rank ({@link #RANK}) only for its light: its seams glow in that rank's colour (SPEC
 * §17.29). {@link #UNKNOWN} until its core paints it (doors built before panels knew).
 */
public class PrecursorDoorBlock extends Block {
    /** The door's rank (a {@link dev.awaken.progression.Rank} ordinal), or {@link #UNKNOWN}. */
    public static final IntegerProperty RANK = IntegerProperty.create("rank", 0, 7);
    public static final int UNKNOWN = 7;

    public PrecursorDoorBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(RANK, UNKNOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RANK);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) ask(sp, level, pos);
        return InteractionResult.SUCCESS;
    }

    /** A strike is a touch: the panel can't be broken, so the door answers instead of doing nothing. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer sp) ask(sp, level, pos);
    }

    private static void ask(ServerPlayer sp, Level level, BlockPos pos) {
        BlockPos core = DoorCoreBlock.findCore(level, pos);
        if (core != null) DoorCoreBlock.tryOpen(sp, level, core);
        else sp.sendOverlayMessage(Component.literal("Sealed. Nothing here answers."));
    }
}
