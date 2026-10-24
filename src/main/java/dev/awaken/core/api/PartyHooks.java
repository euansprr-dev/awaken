package dev.awaken.core.api;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * Parties (SPEC §16.7): gates use this to seal arenas and share rewards with the whole party. Installed by the guild
 * module; the defaults make every player a party of one.
 */
public final class PartyHooks {
    private PartyHooks() {}

    public interface Access {
        /** The player's party, the player included. */
        default List<UUID> members(Player p) { return List.of(p.getUUID()); }
        default boolean sameParty(Player a, Player b) { return a.getUUID().equals(b.getUUID()); }
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static List<UUID> members(Player p) {
        return access.members(p);
    }

    public static boolean sameParty(Player a, Player b) {
        return access.sameParty(a, b);
    }
}
