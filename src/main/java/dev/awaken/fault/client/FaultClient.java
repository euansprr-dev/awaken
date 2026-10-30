package dev.awaken.fault.client;

import dev.awaken.fault.FaultNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Faults on the client (SPEC §43.8): the payloads, the HUD layer that draws them, the tick that sounds them. */
public final class FaultClient {
    private FaultClient() {}

    public static void init(IEventBus modBus) {
        FaultConfig.register();
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(FaultNet.Play.TYPE, (m, ctx) -> FaultHud.play(m.fault()));
            e.register(FaultNet.Mend.TYPE, (m, ctx) -> FaultHud.mend());
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> FaultHud.register(e));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> FaultHud.tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> FaultHud.reset());
    }
}
