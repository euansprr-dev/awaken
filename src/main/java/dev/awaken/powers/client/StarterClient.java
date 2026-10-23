package dev.awaken.powers.client;

import dev.awaken.powers.net.StarterPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Client side of the first-power choice. The cards open as soon as nothing else is on screen (never over a loading
 * screen, the inventory or chat). While the choice is waiting, K opens the cards instead of Status, because that's
 * what the Now tracker's "Choose your first power · K" promises.
 */
public final class StarterClient {
    private StarterClient() {}

    private static List<String> cards = List.of();
    private static boolean wantOpen;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, StarterClient::payloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ScreenEvent.Opening.class, StarterClient::onOpening);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            cards = List.of();
            wantOpen = false;
        });
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(StarterPayloads.Open.TYPE, (msg, ctx) -> {
            cards = List.copyOf(msg.powers());
            wantOpen = !cards.isEmpty();
        });
        e.register(StarterPayloads.Done.TYPE, (msg, ctx) -> {
            cards = List.of();
            wantOpen = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof StarterScreen s) s.onClose();
        });
    }

    private static void tick() {
        if (!wantOpen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.gui.overlay() != null) return;
        wantOpen = false;
        mc.gui.setScreen(new StarterScreen(cards));
    }

    private static void onOpening(ScreenEvent.Opening e) {
        if (cards.isEmpty() || e.getCurrentScreen() != null) return;
        if (e.getNewScreen() instanceof dev.awaken.progression.client.StatusScreen) e.setNewScreen(new StarterScreen(cards));
    }

    static void choose(String power) {
        ClientPacketDistributor.sendToServer(new StarterPayloads.Choose(power));
    }
}
