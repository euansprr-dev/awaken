package dev.awaken.core;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Safe sending. Fake players (GameTests, other mods' machines) have no real connection or don't speak our channels;
 * sending to them would throw, so every module sends through here.
 */
public final class AwakenNet {
    private AwakenNet() {}

    public static boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && NetworkRegistry.hasChannel(player.connection, type.id());
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (canReceive(player, payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }
}
