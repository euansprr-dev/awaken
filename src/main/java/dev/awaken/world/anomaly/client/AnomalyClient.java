package dev.awaken.world.anomaly.client;

import dev.awaken.world.anomaly.AnomalyContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of the anomalies: the rift (its drawn light), mote, falling-star and shimmer renderers, and ore sight. Only loaded on the client. */
public final class AnomalyClient {
    private AnomalyClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AnomalyClient::onRenderers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, OreSight::tick);
        // the rift's drawn light (SPEC §26.4)
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent.class, RiftGlow::onRegister);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) -> RiftGlow.submit(e));
        NeoForge.EVENT_BUS.addListener(RiftGlow::onJoin);
        NeoForge.EVENT_BUS.addListener(RiftGlow::onLeave);
        NeoForge.EVENT_BUS.addListener(RiftGlow::onUnload);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> RiftGlow.tick());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> RiftGlow.clear());
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AnomalyContent.RIFT.get(), RiftRenderer::new);
        event.registerEntityRenderer(AnomalyContent.MOTE.get(), MoteRenderer::new);
        event.registerEntityRenderer(AnomalyContent.FALLING_STAR.get(), StarRenderer::new);
        event.registerEntityRenderer(AnomalyContent.SHIMMER.get(), ShimmerRenderer::new);
    }
}
