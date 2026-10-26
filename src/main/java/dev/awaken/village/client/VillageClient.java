package dev.awaken.village.client;

import dev.awaken.village.VillageNet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The villages on the client: the notice board's screen (their sounds and smoke are the citizens' client's). */
public final class VillageClient {
    private VillageClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(VillageNet.Board.TYPE, (msg, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof NoticeBoardScreen s) s.update(msg);
            else mc.gui.setScreen(new NoticeBoardScreen(msg));
        }));
    }
}
