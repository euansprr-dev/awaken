package dev.awaken.core.client;

import net.neoforged.bus.api.IEventBus;

/** Client entry for core: key mappings and shared client services. */
public final class CoreClient {
    private CoreClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AwakenKeys::register);
        ExperimentalWarning.init();
        DialogueInput.init();
        InputLock.init(); // the one way scenes hold the player still (SPEC §27.6)
        // Awaken's HUD never draws over a screen (chat aside)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre.class, HudUnderScreens::pre);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> {
            while (AwakenKeys.PLAYTEST_FLAG.consumeClick()) {
                if (dev.awaken.core.Playtest.enabled()) {
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new dev.awaken.core.CorePayloads.FlagUnfair());
                }
            }
        });
    }
}
