package dev.awaken.frontier.client;

import dev.awaken.Awaken;
import dev.awaken.frontier.FrontierNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

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
            FlightFilm.holdAtHelm(Minecraft.getInstance());
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

    private static @Nullable Vec3 mark;
    private static int waited;

    /**
     * Tours ({@code core.client.dev.Autopilot}, a line "frontier ..."); null means not yet, ask again next tick. A
     * runner's server can be far behind its client, so a tour waits on what happened, not on a count of ticks:
     * {@code descent} until the flight on screen is coming down, {@code down} until it's over, {@code mark} then
     * {@code moved} until the player has been put somewhere else (a goto or a look command has landed).
     */
    public static @Nullable String autopilot(String arg) {
        Minecraft mc = Minecraft.getInstance();
        switch (arg.trim()) {
            case "descent" -> {
                FlightFilm.Flight f = FlightFilm.cur;
                if (f == null) return "no flight";
                if (f.phase != FlightFilm.Phase.DESCEND && ++waited < 20 * 120) return null;
                waited = 0;
                return f.phase == FlightFilm.Phase.DESCEND ? "coming down" : "gave up";
            }
            case "down" -> {
                if (FlightFilm.cur != null && ++waited < 20 * 60) return null;
                waited = 0;
                return FlightFilm.cur == null ? "set down" : "gave up";
            }
            case "mark" -> {
                mark = mc.player == null ? null : mc.player.position();
                waited = 0;
                return "marked";
            }
            case "moved" -> {
                Vec3 from = mark;
                if (mc.player == null || from == null) return "nothing to wait for";
                boolean moved = mc.player.position().distanceToSqr(from) > 1;
                if (!moved && ++waited < 20 * 90) return null;
                mark = null;
                waited = 0;
                return moved ? "moved" : "gave up";
            }
            default -> {
                return "unknown";
            }
        }
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
