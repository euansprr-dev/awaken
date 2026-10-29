package dev.awaken.bells.client;

import dev.awaken.bells.BellNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The bells' client side: the song as it rings, and the Bell Book in the Codex. */
public final class BellClient {
    private BellClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(BellNet.Song.TYPE, (m, ctx) -> SongPlayer.start(m, false));
            e.register(BellNet.Page.TYPE, (m, ctx) -> BellBookSection.page = m);
        });
        NeoForge.EVENT_BUS.addListener(SongPlayer::tick);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            BellBookSection.forget();
            SongPlayer.stop();
        });
        BellBookSection.register();
    }
}
