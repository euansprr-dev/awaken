package dev.awaken.world.anomaly.client;

import dev.awaken.world.anomaly.AnomalyContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of the anomalies: the rift, mote, falling-star and shimmer renderers, and ore sight. Only loaded on the client. */
public final class AnomalyClient {
    private AnomalyClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AnomalyClient::onRenderers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, OreSight::tick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AnomalyContent.RIFT.get(), RiftRenderer::new);
        event.registerEntityRenderer(AnomalyContent.MOTE.get(), MoteRenderer::new);
        event.registerEntityRenderer(AnomalyContent.FALLING_STAR.get(), StarRenderer::new);
        event.registerEntityRenderer(AnomalyContent.SHIMMER.get(), ShimmerRenderer::new);
    }
}
