package dev.awaken.core.client;

import net.neoforged.bus.api.IEventBus;

/** Client entry for core: key mappings and shared client services. */
public final class CoreClient {
    private CoreClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AwakenKeys::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> {
            while (AwakenKeys.PLAYTEST_FLAG.consumeClick()) {
                if (dev.awaken.core.Playtest.enabled()) {
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new dev.awaken.core.CorePayloads.FlagUnfair());
                }
            }
        });
    }
}
