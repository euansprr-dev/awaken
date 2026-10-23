package dev.awaken.world.client;

import net.neoforged.bus.api.IEventBus;

/** Client entry point for the world module. Only loaded on the client. */
public final class WorldClient {
    private WorldClient() {}

    public static void init(IEventBus modBus) {
        dev.awaken.world.anomaly.client.AnomalyClient.init(modBus);
    }
}
