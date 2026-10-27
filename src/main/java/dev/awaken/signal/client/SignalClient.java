package dev.awaken.signal.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.Notices;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the Signal: payload handlers, the HUD layer and key handling. The {@code systemStyle} setting picks
 * System v2 ({@link SystemHud}) or the old window ({@link ClassicHud}); the Now tracker and the Status sections work
 * with either.
 */
public final class SignalClient {
    private SignalClient() {}

    private static boolean wasClassic;

    public static void init(IEventBus modBus) {
        modBus.addListener(SignalClient::onPayloads);
        modBus.addListener(SignalClient::onLayers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(CustomizeGuiOverlayEvent.BossEventProgress.class, TrackerHud::onBossBar);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre.class, e -> TrackerHud.frameStart());
        dev.awaken.core.api.UiHooks.installTopRight(TaskHud.STACK); // the task card heads the top-right stack
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> clear());
        // the player's own clicks and keys: what the server says right after answers them (SPEC §26.2)
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered.class, e -> Herald.acted());
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Post.class, e -> Herald.acted());
        SignalSections.register();
        Journal.register();
        // the Atlas asks for a destination through core.api (SPEC §16.8); the server answers with the one now set
        dev.awaken.core.api.Destinations.installSender(t -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                t.map(d -> new SignalPayloads.SetDestination(true, d.name(), d.at(), d.kind()))
                        .orElseGet(() -> new SignalPayloads.SetDestination(false, "", net.minecraft.core.BlockPos.ZERO, ""))));
        Herald.install();
        Tips.install();
        modBus.addListener(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.class, e -> e.addListener(dev.awaken.Awaken.id("tips"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) Tips::load));
    }

    static boolean classic() {
        try {
            return AwakenConfig.Client.SYSTEM_STYLE.get() == AwakenConfig.Client.SystemStyle.CLASSIC;
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(SignalPayloads.Show.TYPE, (msg, ctx) -> {
            if (classic()) {
                RecentLog.add(msg.window());
                ClassicHud.show(msg.window());
            } else {
                SystemHud.show(msg.window());
            }
        });
        event.register(SignalPayloads.Toast.TYPE, (msg, ctx) -> {
            if (classic()) {
                RecentLog.add(msg.text());
                ClassicHud.toast(msg.text(), Glass.EDGE);
            } else {
                SystemHud.toast(msg.text(), Glass.EDGE, Notices.Tier.byId(msg.tier()), msg.topic());
            }
        });
        event.register(SignalPayloads.Pending.TYPE, (msg, ctx) -> SystemHud.pending(msg.entries()));
        event.register(SignalPayloads.Now.TYPE, (msg, ctx) -> NowClient.accept(msg));
        event.register(SignalPayloads.Horizon.TYPE, (msg, ctx) -> HorizonClient.accept(msg));
        event.register(SignalPayloads.Task.TYPE, (msg, ctx) -> TaskHud.onTask(msg));
        event.register(SignalPayloads.Destination.TYPE, (msg, ctx) -> dev.awaken.core.api.Destinations.accept(msg.target().orElse(null)));
        event.register(SignalPayloads.Tip.TYPE, (msg, ctx) -> {
            if (msg.offer()) Tips.serverOffer(msg.id());
            else Tips.done(msg.id());
        });
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, Awaken.id("signal"), SignalClient::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (classic()) ClassicHud.render(g, dt);
        else SystemHud.render(g, dt);
        TrackerHud.render(g, dt);
        TaskHud.render(g, dt);
        Tips.render(g, dt);
    }

    private static void tick() {
        NowClient.tick();
        TaskHud.tick();
        Tips.tick();
        HorizonClient.tick();
        boolean c = classic();
        if (c != wasClassic) { // switched style in the settings: drop what the other one had on screen
            if (c) SystemHud.clearScreen();
            else ClassicHud.clear();
            wasClassic = c;
        }
        if (c) ClassicHud.tick();
        else SystemHud.tick();
    }

    private static void clear() {
        ClassicHud.clear();
        SystemHud.clear();
        RecentLog.clear();
        NowClient.clear();
        TaskHud.clear();
        HorizonClient.clear();
    }
}
