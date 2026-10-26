package dev.awaken.above.client;

import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.net.AboveNet;
import dev.awaken.progression.client.CodexEntries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point for the above module. Only loaded on the client. */
public final class AboveClient {
    private AboveClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AboveClient::onPayloads);
        // a sealed door's seams and rune glow in its rank's colour, so the rank reads from afar (SPEC §17.29)
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class, e -> {
            e.register(java.util.List.of(DoorTint.CORE), dev.awaken.above.AboveBlocks.DOOR_CORE.get());
            e.register(java.util.List.of(DoorTint.PANEL), dev.awaken.above.AboveBlocks.DOOR.get());
        });
        CodexEntries.addSource(ClientAbove::codex);
        dev.awaken.core.api.MapMarks.registerGates(ClientAbove::gates); // the scanner's last reading, on the Atlas (SPEC §30)
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> JetBoots.tick());
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, HaloSky::onSubmit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ClientAbove.reset());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(AboveNet.Sync.TYPE, (msg, ctx) -> ClientAbove.data = msg.data());
        event.register(AboveNet.Logs.TYPE, (msg, ctx) -> PrecursorLogs.replace(msg.logs()));
        event.register(AboveNet.OpenPanel.TYPE, (msg, ctx) -> PanelScreen.open(msg.panel()));
        event.register(AboveNet.GateMarks.TYPE, (msg, ctx) -> ClientAbove.gateMarks = msg.marks());
        event.register(AboveNet.Leviathan.TYPE, (msg, ctx) -> HaloSky.sighting(msg));
    }
}
