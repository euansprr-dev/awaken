package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Living Wilds (SPEC §21) for modules that don't depend on it. Installed by the living module; the defaults behave
 * as before it existed: progression pays its own home kill XP and sends the level toast.
 */
public final class LivingHooks {
    private LivingHooks() {}

    public interface Bridge {
        /** Whether the living module pays overworld kill XP (by the creature's level and role) instead of progression. */
        default boolean paysKills() { return false; }

        /**
         * A level was reached. True when the living module shows it (the level moment) and progression should not send
         * its own toast; false keeps the toast (in a fight, pure survival, or without the module).
         */
        default boolean levelMoment(ServerPlayer player, int level, int gained) { return false; }

        /** Whether this fauna species lives in the living module's ledger here (herds), so it never spawns naturally. */
        default boolean ledgered(net.minecraft.server.level.ServerLevel level, String speciesId) { return false; }
    }

    private static Bridge bridge = new Bridge() {};

    public static void install(Bridge b) {
        bridge = b;
    }

    /** Whether a fauna species' herds live in the Living Wilds' ledger instead of spawning naturally (SPEC §21.7). */
    public static boolean ledgered(net.minecraft.server.level.ServerLevel level, String speciesId) {
        return bridge.ledgered(level, speciesId);
    }

    public static boolean paysKills() {
        return bridge.paysKills();
    }

    public static boolean levelMoment(ServerPlayer player, int level, int gained) {
        return bridge.levelMoment(player, level, gained);
    }
}
