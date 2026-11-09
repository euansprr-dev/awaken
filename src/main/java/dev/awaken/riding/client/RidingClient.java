package dev.awaken.riding.client;

import dev.awaken.Awaken;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.riding.RidingNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Riding Along on the client (SPEC §55): the thread, the fold seen from inside, the K menu's section. No keys. */
public final class RidingClient {
    private RidingClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RidingClient::payloads);
        modBus.addListener(RidingClient::layers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(SubmitCustomGeometryEvent.class, ThreadFx::submit);
        bus.addListener(ClientTickEvent.Post.class, e -> FoldView.tick());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            FoldView.clear();
            ClientRiding.clear();
        });
        bus.addListener(RenderHandEvent.class, FoldView::hand);
        bus.addListener(RenderPlayerEvent.Pre.class, FoldView::player);
        bus.addListener(RenderNameTagEvent.CanRender.class, FoldView::nameTag);
        bus.addListener(ExtractBlockOutlineRenderStateEvent.class, FoldView::outline);
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, FoldView::interact);
        StatusSections.add(StatusSections.Tab.STATUS, new RidingSection());
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(RidingNet.Threads.TYPE, (m, ctx) -> ClientRiding.receive(m));
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Awaken.id("riding_veil"), FoldView::veil);
    }
}
