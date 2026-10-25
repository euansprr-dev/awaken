package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizensContent;
import dev.awaken.citizens.CitizensNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Citizens on the client: their renderer, the city's particles, chimney smoke and what people say. */
public final class CitizensClient {
    private CitizensClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(CitizensContent.CITIZEN.get(), CitizenRenderer::new);
            e.registerEntityRenderer(CitizensContent.CRITTER.get(), CritterRenderer::new);
        });
        modBus.addListener(CritterRenderer::layers);
        CityCritters.init();
        CityGlow.source(CityCritters.LANTERNS);
        NeoForge.EVENT_BUS.addListener(CityCritters::tick);
        NeoForge.EVENT_BUS.addListener(CityCritters::onJoin);
        NeoForge.EVENT_BUS.addListener(CityCritters::onLeave);
        NeoForge.EVENT_BUS.addListener(CityCritters::onSound);
        modBus.addListener((RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(CitizensContent.CHIMNEY_SMOKE.get(), CitizenParticles.Smoke.Provider::new);
            e.registerSpriteSet(CitizensContent.FORGE_SPARK.get(), CitizenParticles.Spark.Provider::new);
            e.registerSpriteSet(CitizensContent.LAMP_FLARE.get(), CitizenParticles.Flare.Provider::new);
            e.registerSpriteSet(CitizensContent.FIRE_EMBER.get(), CitizenParticles.Ember.Provider::new);
        });
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(CitizensNet.Speech.TYPE, CitizenFx::speech);
            e.register(CitizensNet.Emitters.TYPE, CitizenFx::emitters);
            e.register(CitizensNet.Site.TYPE, CityLife::site);
        });
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent.class, CityGlow::onRegister);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) -> CityGlow.submit(e));
        NeoForge.EVENT_BUS.addListener(CityGlow::onJoin);
        NeoForge.EVENT_BUS.addListener(CityGlow::onLeave);
        NeoForge.EVENT_BUS.addListener(CitizenFx::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            CitizenFx.clear();
            CityGlow.clear();
            CityLife.clear();
            CityCritters.clear();
        });
    }
}
