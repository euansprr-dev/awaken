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
        dev.awaken.core.api.MapMarks.register(FaunaClient::lairMarks);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> RevealPulse.stop());
    }

    /** Lairs this player has heard of (outlined on the Gate Map) or found (filled), in the beast's colour. */
    private static java.util.List<dev.awaken.core.api.MapMarks.Mark> lairMarks(net.minecraft.world.entity.player.Player p) {
        var lairs = p.getData(dev.awaken.world.fauna.FaunaModule.STATE.get()).lairs();
        if (lairs.isEmpty()) return java.util.List.of();
        java.util.List<dev.awaken.core.api.MapMarks.Mark> out = new java.util.ArrayList<>(lairs.size());
        for (var l : lairs) {
            var def = dev.awaken.mobs.def.Bestiary.get(l.beast());
            String name = def == null ? l.beast() : def.name;
            int colour = def == null ? 0xFFD27A : def.glow;
            out.add(new dev.awaken.core.api.MapMarks.Mark(l.pos(), colour, "Lair · " + name, l.found()));
        }
        return out;
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
