package dev.awaken.trails.client;

import dev.awaken.trails.net.TrailPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of trails: the Journal's TRAILS section and the copy it draws from. */
public final class TrailsClient {
    private TrailsClient() {}

    public static void init(IEventBus modBus) {
        TrailSection.register();
        modBus.addListener(TrailsClient::onPayloads);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> TrailCache.reset());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TrailPayloads.View.TYPE, (msg, ctx) -> TrailCache.onView(msg));
    }
}
