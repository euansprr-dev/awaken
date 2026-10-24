package dev.awaken.core.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Coin (bell marks) for other modules (SPEC §16.7): contracts and events pay through here, once per claim id.
 * Installed by the economy module; the defaults hold no coin and pay nothing.
 */
public final class EconomyHooks {
    private EconomyHooks() {}

    public interface Access {
        default long balance(Player p) { return 0L; }
        /** Pays once per claim id; false when that claim was already paid (or there is no economy). */
        default boolean pay(ServerPlayer p, long amount, Identifier claimId) { return false; }
        /** Takes coin if the player has it; false and nothing taken otherwise. */
        default boolean charge(ServerPlayer p, long amount) { return false; }
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static long balance(Player p) {
        return access.balance(p);
    }

    public static boolean pay(ServerPlayer p, long amount, Identifier claimId) {
        return access.pay(p, amount, claimId);
    }

    public static boolean charge(ServerPlayer p, long amount) {
        return access.charge(p, amount);
    }
}
