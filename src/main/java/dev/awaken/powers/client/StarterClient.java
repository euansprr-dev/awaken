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
 * what the task card's "Press [K] to choose your first power" promises.
 */
public final class StarterClient {
    private StarterClient() {}

    private static List<String> cards = List.of();
    private static boolean wantOpen;
    /** Development only: under the autopilot the picked card stays on screen this many ticks so a tour can shoot it. */
    private static final int AUTOPILOT_HOLD = System.getProperty("awaken.autopilot") != null ? 12 : 0;
    private static int closeIn = -1;

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
            if (AUTOPILOT_HOLD > 0) closeIn = AUTOPILOT_HOLD;
            else if (mc.gui.screen() instanceof StarterScreen s) s.onClose();
        });
    }

    private static void tick() {
        if (closeIn >= 0 && --closeIn < 0 && Minecraft.getInstance().gui.screen() instanceof StarterScreen s) s.onClose();
        if (!wantOpen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.gui.overlay() != null) return;
        wantOpen = false;
        mc.gui.setScreen(new StarterScreen(cards));
    }

    private static void onOpening(ScreenEvent.Opening e) {
        if (cards.isEmpty() || e.getCurrentScreen() != null) return;
        if (e.getNewScreen() instanceof dev.awaken.progression.client.StatusScreen
                || e.getNewScreen() instanceof dev.awaken.holo.client.HoloStatusScreen) e.setNewScreen(new StarterScreen(cards));
    }

    static void choose(String power) {
        ClientPacketDistributor.sendToServer(new StarterPayloads.Choose(power));
    }
}
