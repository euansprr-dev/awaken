package dev.awaken.hall.client;

import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/** The Hall of Arms' client side (SPEC §52). */
public final class HallClient {
    private HallClient() {}

    static HallNet.@Nullable Wall wall;

    public static void init(IEventBus modBus) {
        HallConfig.register();
        modBus.addListener(RegisterRenderPipelinesEvent.class, HallPipelines::register);
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, e -> e.register(PicState.class, PicRenderer::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(HallNet.Wall.TYPE, (m, ctx) -> wall = m);
            e.register(HallNet.Show.TYPE, (m, ctx) -> {
                if (Minecraft.getInstance().gui.screen() instanceof HallScreen h) h.show(m.style(), m.tier());
            });
        });
    }
}
