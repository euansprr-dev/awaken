package dev.awaken.world.stilled.client;

import dev.awaken.world.stilled.StilledContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The Stilled's client side: the figure's model layer and its renderer. */
public final class StilledClient {
    private StilledClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(StilledModel.LAYER, StilledModel::createLayer));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(StilledContent.STILLED_BE.get(), StilledRenderer::new));
    }
}
