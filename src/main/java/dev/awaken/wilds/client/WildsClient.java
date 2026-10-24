package dev.awaken.wilds.client;

import dev.awaken.wilds.WildsContent;
import dev.awaken.wilds.ward.WardFx;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The Wilds on the client: the ward's particles and its effect events. */
public final class WildsClient {
    private WildsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(WildsContent.WARD_GRAIN.get(), WardParticles.GrainProvider::new);
            e.registerSpriteSet(WildsContent.WARD_SPARK.get(), WardParticles.SparkProvider::new);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(WardFx.Event.TYPE, (msg, ctx) -> WardFxClient.play(msg)));
    }
}
