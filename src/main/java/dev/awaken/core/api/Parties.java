package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who shares a kill (SPEC "Together", shared rewards): the rest of a player's party ({@link PartyHooks}) that is online,
 * alive, not spectating and close to where it happened. Gate kills, Descent and home kills and Echo binding all ask
 * here, so the party and the range are one rule. Server side.
 */
public final class Parties {
    private Parties() {}

    /** Party members this close to a kill share it, each through their own budget and their own rolls. */
    public static final double SHARE_RANGE = 32;

    /** The player's party members within {@link #SHARE_RANGE} of {@code at} in its level, never the player themselves. */
    public static List<ServerPlayer> sharing(ServerPlayer player, Entity at) {
        List<UUID> members = PartyHooks.members(player);
        if (members.size() <= 1) return List.of();
        List<ServerPlayer> out = new ArrayList<>(members.size() - 1);
        for (UUID id : members) {
            if (id.equals(player.getUUID())) continue;
            ServerPlayer mate = player.level().getServer().getPlayerList().getPlayer(id);
            if (mate != null && mate.level() == at.level() && mate.isAlive() && !mate.isSpectator()
                    && mate.distanceToSqr(at) <= SHARE_RANGE * SHARE_RANGE) out.add(mate);
        }
        return out;
    }
}
