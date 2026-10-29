package dev.awaken.kin.works.client;

import dev.awaken.kin.works.WorksContent;
import dev.awaken.kin.works.WorksNet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The works' client side: the Drop's heap and the Ledger's screen. Started by the kin's client. */
public final class WorksClient {
    private WorksClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(WorksContent.DROP_BE.get(), DropRenderer::new));
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(WorksNet.Ledger.TYPE, (m, ctx) -> Minecraft.getInstance().gui.setScreen(new LedgerScreen(m))));
    }
}
