package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Riding Along (SPEC §55), retired by §70.4: nobody rides any more, so {@link #rider}, {@link #bonded} and
 * {@link #partner} always answer that nobody does, and the branches that ask them are inert. {@link #folded} lives on:
 * the together module answers it for a player on a knee (a second wind, or down and waiting for a hand), which is what
 * powers ask before a cast. Server side.
 */
public final class RidingHooks {
    private RidingHooks() {}

    /** The thread holds this far apart (blocks), and the same level. */
    public static final double RANGE = 48;
    /** What a rider's hit counts for. */
    public static final float HIT_SHARE = 0.4F;
    /** What a rider earns of the XP. */
    public static final double XP_SHARE = 0.5;

    public interface Access {
        /** Riding along with someone now, near or not. */
        default boolean rider(Player p) { return false; }

        /** Riding along, or being ridden along with. */
        default boolean bonded(Player p) { return false; }

        default Optional<UUID> partner(Player p) { return Optional.empty(); }

        /** In their partner's shadow right now: not hittable, not acting. */
        default boolean folded(Player p) { return false; }

        /** A right click on a gate's door: go in together, or let go. */
        default void door(ServerPlayer p) {}

        /** The player's gate run is ending, however it ends. Called before the run is cleared. */
        default void leftRun(ServerPlayer p) {}
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static boolean rider(Player p) {
        return access.rider(p);
    }

    public static boolean bonded(Player p) {
        return access.bonded(p);
    }

    public static Optional<UUID> partner(Player p) {
        return access.partner(p);
    }

    public static boolean folded(Player p) {
        return access.folded(p);
    }

    public static void door(ServerPlayer p) {
        access.door(p);
    }

    public static void leftRun(ServerPlayer p) {
        access.leftRun(p);
    }
}
