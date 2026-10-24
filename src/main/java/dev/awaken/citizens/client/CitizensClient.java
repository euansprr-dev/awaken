package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizensContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Citizens on the client: their renderer, the city's particles, chimney smoke and what people say. */
public final class CitizensClient {
    private CitizensClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(CitizensContent.CITIZEN.get(), CitizenRenderer::new));
        modBus.addListener((RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(CitizensContent.CHIMNEY_SMOKE.get(), CitizenParticles.Smoke.Provider::new);
            e.registerSpriteSet(CitizensContent.FORGE_SPARK.get(), CitizenParticles.Spark.Provider::new);
            e.registerSpriteSet(CitizensContent.LAMP_FLARE.get(), CitizenParticles.Flare.Provider::new);
            e.registerSpriteSet(CitizensContent.FIRE_EMBER.get(), CitizenParticles.Ember.Provider::new);
        });
    }
}
