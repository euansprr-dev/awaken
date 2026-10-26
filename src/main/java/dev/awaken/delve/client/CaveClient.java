package dev.awaken.delve.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The cave places on the client (SPEC §28.5): the place you are in ({@link CaveState}), its caption. */
public final class CaveClient {
    private CaveClient() {}

    public static void init(IEventBus modBus) {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> CaveState.tick());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> CaveState.reset());
    }
}
