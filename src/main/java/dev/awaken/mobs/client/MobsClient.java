package dev.awaken.mobs.client;

import net.neoforged.bus.api.IEventBus;

/** Client entry point for the mobs module: renderers and model layers for every creature. Only loaded on the client. */
public final class MobsClient {
    private MobsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AwRenderers::layers);
        modBus.addListener(AwRenderers::renderers);
        modBus.addListener(RitualRenderers::renderers);
        modBus.addListener(SeepParticles::register);
    }
}
