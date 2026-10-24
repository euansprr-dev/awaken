package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Carillon's opening (SPEC §16.4: the sky shot, the run, the arrival, the infirmary and the Registry) for other modules:
 * the HUD and the Director stay quiet while a player is in it. Installed by the opening module; the defaults mean no
 * opening is running.
 */
public final class OpeningHooks {
    private OpeningHooks() {}

    public interface Access {
        default boolean inOpening(Player p) { return false; }
        /** The current beat's name (e.g. "run"), or null when not in the opening. */
        default @Nullable String stage(Player p) { return null; }
        default void skip(ServerPlayer p) {}
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static boolean inOpening(Player p) {
        return access.inOpening(p);
    }

    public static @Nullable String stage(Player p) {
        return access.stage(p);
    }

    public static void skip(ServerPlayer p) {
        access.skip(p);
    }
}
