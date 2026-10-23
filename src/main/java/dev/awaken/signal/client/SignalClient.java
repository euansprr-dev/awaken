package dev.awaken.signal.client;

import dev.awaken.Awaken;
import dev.awaken.signal.net.SignalPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of the Signal: payload handlers, the HUD layer and key handling. */
public final class SignalClient {
    private SignalClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(SignalClient::onPayloads);
        modBus.addListener(SignalClient::onLayers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> SignalHud.tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> SignalHud.clear());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(SignalPayloads.Show.TYPE, (msg, ctx) -> SignalHud.show(msg.window()));
        event.register(SignalPayloads.Toast.TYPE, (msg, ctx) -> SignalHud.toast(msg.text(), Glass.EDGE));
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, Awaken.id("signal"), SignalHud::render);
    }
}
