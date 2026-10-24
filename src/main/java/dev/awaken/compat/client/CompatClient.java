package dev.awaken.compat.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client side of the companion integrations (each loads only when its mod is present) and the Look switch. */
public final class CompatClient {
    private CompatClient() {}

    public static void init(IEventBus modBus) {
        LookClient.init(modBus);
        modBus.addListener(FMLClientSetupEvent.class, e -> {
            if (ModList.get().isLoaded("distanthorizons")) e.enqueueWork(DistantHorizonsClient::init);
        });
    }

    /**
     * Development (autopilot {@code fardepth share|auto}): Distant Horizons shares its depth with Lens on every graphics
     * API, so a tour can check it where it isn't checked yet (Vulkan), or again only where it was. Nothing without DH.
     */
    public static void shareFarDepthEverywhere(boolean on) {
        if (ModList.get().isLoaded("distanthorizons")) DistantHorizonsClient.shareEverywhere(on);
    }
}
