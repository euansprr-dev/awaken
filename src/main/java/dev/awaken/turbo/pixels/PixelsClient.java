package dev.awaken.turbo.pixels;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Pixels on the client (SPEC §18): its settings, its pipelines, and a check after every frame. */
public final class PixelsClient {
    private PixelsClient() {}

    public static void init(IEventBus modBus) {
        PixelsConfig.register();
        modBus.addListener(RegisterRenderPipelinesEvent.class, PixelsPipelines::onRegister);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> Pixels.refresh());
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> PixelsFrame.frameEnd());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> PixelsPipelines.recheck());
    }
}
