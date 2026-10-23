package dev.awaken.core.client;

import net.neoforged.bus.api.IEventBus;

/** Client entry for core: key mappings and shared client services. */
public final class CoreClient {
    private CoreClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AwakenKeys::register);
    }
}
