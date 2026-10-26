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
import net.minecraft.world.phys.BlockHitResult;

/** A panel of a sealed Precursor door. The door core among the panels decides who may pass; touching a panel asks it. */
public class PrecursorDoorBlock extends Block {
    public PrecursorDoorBlock(BlockBehaviour.Properties p) {
        super(p);
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
