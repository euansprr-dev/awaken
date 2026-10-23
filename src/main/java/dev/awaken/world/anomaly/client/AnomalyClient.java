package dev.awaken.world.anomaly.client;

import dev.awaken.world.anomaly.AnomalyContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client side of the anomalies: the rift and mote renderers. Only loaded on the client. */
public final class AnomalyClient {
    private AnomalyClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AnomalyClient::onRenderers);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AnomalyContent.RIFT.get(), RiftRenderer::new);
        event.registerEntityRenderer(AnomalyContent.MOTE.get(), MoteRenderer::new);
    }
}
