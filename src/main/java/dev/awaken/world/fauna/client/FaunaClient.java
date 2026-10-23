package dev.awaken.world.fauna.client;

import dev.awaken.Awaken;
import dev.awaken.mobs.entity.FaunaHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.client.CodexEntries;
import dev.awaken.world.fauna.FaunaNet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of the Awakened layer's creatures: the reveal pulse, the Creatures Codex, faint hints before awakening. */
public final class FaunaClient {
    private FaunaClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(FaunaClient::payloads);
        modBus.addListener(FaunaClient::layers);
        FaunaHooks.installViewer(FaunaClient::viewerAwakened);
        CodexEntries.addSource(CreatureCodex::entries);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> RevealPulse.stop());
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(FaunaNet.Pulse.TYPE, (msg, ctx) -> RevealPulse.start(msg.strength()));
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Awaken.id("fauna_reveal_pulse"), RevealPulse::render);
    }

    private static long cachedAt = Long.MIN_VALUE;
    private static boolean cached = true;

    /** Read by every ambient creature every frame: looked up once per game tick. */
    private static boolean viewerAwakened() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return true;
        long now = mc.level.getGameTime();
        if (now != cachedAt) {
            cachedAt = now;
            cached = Progression.profile(mc.player).awakened();
        }
        return cached;
    }
}
