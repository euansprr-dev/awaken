package dev.awaken.veil.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a spirit can touch. Spirits are spectators and cannot click blocks, so the client sends a Veil action and
 * {@code VeilInteract} calls this after checking the block is in {@code #awaken:veil_interactable} and within reach.
 */
public interface SpiritUsable {
    /** Returns true if something happened. */
    boolean spiritUse(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer spirit);

    /** What a living hand gets for trying. */
    static void ghostOnly(ServerPlayer player) {
        dev.awaken.signal.Signal.toast(player, Component.translatable("block.awaken.ghost.living_hand"));
    }
}
