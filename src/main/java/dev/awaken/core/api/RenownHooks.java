package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Renown in Carillon (SPEC §16.6): modules report deeds, and the renown module keeps what witnesses saw and how the
 * city talks about it. Installed by the renown module; the defaults keep nothing (tier 0, standing 0).
 */
public final class RenownHooks {
    private RenownHooks() {}

    /** Tiers in order: Stranger, Familiar face, Known, Hunter of note, Hero of Carillon, Legend. */
    public static final int STRANGER = 0, FAMILIAR = 1, KNOWN = 2, OF_NOTE = 3, HERO = 4, LEGEND = 5;

    public interface Access {
        /** A deed happened at {@code pos}; it counts only if a citizen saw it. Negative weight for bad deeds. */
        default void witness(ServerPlayer p, Identifier deed, BlockPos pos, int weight) {}
        default int tier(Player p) { return STRANGER; }
        default int standing(Player p, Identifier faction) { return 0; }
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static void witness(ServerPlayer p, Identifier deed, BlockPos pos, int weight) {
        access.witness(p, deed, pos, weight);
    }

    public static int tier(Player p) {
        return access.tier(p);
    }

    public static int standing(Player p, Identifier faction) {
        return access.standing(p, faction);
    }
}
