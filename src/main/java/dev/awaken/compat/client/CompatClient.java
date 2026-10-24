package dev.awaken.compat.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client side of the companion integrations; each loads only when its mod is present. */
public final class CompatClient {
    private CompatClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, e -> {
            if (ModList.get().isLoaded("distanthorizons")) e.enqueueWork(DistantHorizonsClient::init);
        });
    }
}
