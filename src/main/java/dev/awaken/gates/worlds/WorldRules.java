package dev.awaken.gates.worlds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Each world's one rule (SPEC §38.6): what the place lets you do that no other does. Server side; the blocks route to it.
 */
public final class WorldRules {
    private WorldRules() {}

    /** Bronze was struck. True when a world's rule answered (the bell rang). */
    public static boolean strike(ServerPlayer p, BlockPos pos) {
        return false;
    }
}
