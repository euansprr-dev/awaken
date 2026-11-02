package dev.awaken.trails.client;

import dev.awaken.trails.net.TrailPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of trails: the copy the engine sends. Their steps show on the Tasks page now (SPEC §27.13). */
public final class TrailsClient {
    private TrailsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(TrailsClient::onPayloads);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> TrailCache.reset());
    }

    /** The autopilot's hook (tools/autopilot/trails-*.txt): "now", "browse [area]", "open" (unfold every trail's steps). */
    public static void debug(String arg) {
        String[] w = arg.trim().split("\\s+");
        switch (w[0]) {
            case "browse" -> {
                TrailCache.browse = true;
                if (w.length > 1) TrailCache.area = w[1];
            }
            case "open" -> {
                var v = TrailCache.view;
                if (v != null) for (var t : v.trails()) TrailCache.expanded.add(t.id());
            }
            default -> TrailCache.browse = false;
        }
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TrailPayloads.View.TYPE, (msg, ctx) -> TrailCache.onView(msg));
    }
}
