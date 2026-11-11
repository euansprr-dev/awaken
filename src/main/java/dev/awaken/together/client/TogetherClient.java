package dev.awaken.together.client;

import dev.awaken.aim.client.AimClient;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.together.TogetherNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Together on the client (SPEC §70): the party strip, the edge arrows, pings on the lock key, the band's section. No key of its own. */
public final class TogetherClient {
    private TogetherClient() {}

    public static void init(IEventBus modBus) {
        TogetherClientConfig.register();
        modBus.addListener(TogetherClient::payloads);
        modBus.addListener(TogetherClient::layers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ExtractLevelRenderStateEvent.class, PartyStrip::extract);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            Pings.tick();
            Kneeling.tick();
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ClientParty.clear();
            Pings.clear();
            Kneeling.clear();
        });
        AimClient.freePress = Pings::press;
        StatusSections.add(StatusSections.Tab.STATUS, new TogetherSection());
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(TogetherNet.Party.TYPE, (m, ctx) -> ClientParty.receive(m));
        e.register(TogetherNet.Ping.TYPE, (m, ctx) -> Pings.received(m));
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, PartyStrip.LAYER, PartyStrip::render);
    }
}
