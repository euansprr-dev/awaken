package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Hearth lookups for other modules (break warnings, respawn, colour restoration). Installed by the hearth module. */
public final class HearthHooks {
    private HearthHooks() {}

    public interface Lookup {
        Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius);
        Optional<BlockPos> hearthOf(ServerLevel level, UUID owner);
        /** Inside any Hearth's own radius (it grows with the Hearth's tier). */
        default boolean inside(ServerLevel level, BlockPos pos) { return false; }
    }

    private static Lookup lookup = new Lookup() {
        @Override public Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius) { return Optional.empty(); }
        @Override public Optional<BlockPos> hearthOf(ServerLevel level, UUID owner) { return Optional.empty(); }
    };

    public static void install(Lookup l) {
        lookup = l;
    }

    /** The installed lookup (tests swap it in and out in try/finally). */
    public static Lookup installed() {
        return lookup;
    }

    /** Whether {@code pos} lies inside any Hearth's radius (exact, by tier). */
    public static boolean inside(ServerLevel level, BlockPos pos) {
        return lookup.inside(level, pos);
    }

    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius) {
        return lookup.nearest(level, pos, radius);
    }

    /** The player's Hearth position in the overworld, if they have one. */
    public static Optional<BlockPos> hearthOf(ServerPlayer player) {
        return lookup.hearthOf(player.level().getServer().overworld(), player.getUUID());
    }
}
