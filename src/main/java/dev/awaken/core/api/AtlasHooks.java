package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Atlas (SPEC §16.8): what each player has explored, kept on the server. Modules reveal places (a bell tower clears
 * its district) and ask whether a place is known before showing a marker for it. Installed by the atlas module; the
 * defaults reveal nothing and treat everywhere as explored, so markers behave as they did before the Atlas.
 */
public final class AtlasHooks {
    private AtlasHooks() {}

    public interface Access {
        default void reveal(ServerPlayer p, BlockPos center, int radius) {}
        default boolean explored(Player p, BlockPos pos) { return true; }
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static void reveal(ServerPlayer p, BlockPos center, int radius) {
        access.reveal(p, center, radius);
    }

    public static boolean explored(Player p, BlockPos pos) {
        return access.explored(p, pos);
    }
}
