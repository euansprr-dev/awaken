package dev.awaken.frontier.client;

import dev.awaken.Awaken;
import dev.awaken.frontier.FrontierNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The Far Landing on the client (SPEC §67): the skiff's flight on film, the skiff in the air, its wind and cloud, the cloud crossing. No keys. */
public final class FrontierClient {
    private FrontierClient() {}

    /** The client's own clock (ticks, not counting pauses). */
    static long ticks;

    public static void init(IEventBus modBus) {
        modBus.addListener(FrontierClient::payloads);
        modBus.addListener(FrontierClient::layers);
        modBus.addListener(FlightAir::register);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> {
            if (Minecraft.getInstance().isPaused()) return;
            ticks++;
            SkiffFx.tick();
        });
        bus.addListener(SubmitCustomGeometryEvent.class, SkiffFx::submit);
        bus.addListener(RenderPlayerEvent.Pre.class, FrontierClient::player);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            FlightFilm.reset();
            SkiffFx.clear();
        });
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(FrontierNet.Fly.TYPE, (m, ctx) -> FlightFilm.onFly(m));
        e.register(FrontierNet.Seen.TYPE, (m, ctx) -> SkiffFx.onSeen(m));
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(Awaken.id("frontier_cloud"), CloudCrossing::render);
        dev.awaken.opening.client.Cinema.keepLayer(Awaken.id("frontier_cloud")); // it is the film's, over the shot
    }

    /** Someone aboard a lifting skiff is on it, out of sight. */
    private static void player(RenderPlayerEvent.Pre<?> e) {
        if (FlightFilm.cur == null && SkiffFx.WATCHES.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity who = level.getEntity(e.getRenderState().id);
        if (who != null && (FlightFilm.aboard(who.getUUID()) || SkiffFx.aboard(who.getUUID()))) e.setCanceled(true);
    }
}
