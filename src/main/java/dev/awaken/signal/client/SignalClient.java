package dev.awaken.signal.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> clear());
        SignalSections.register();
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
                SystemHud.toast(msg.text(), Glass.EDGE);
            }
        });
        event.register(SignalPayloads.Pending.TYPE, (msg, ctx) -> SystemHud.pending(msg.entries()));
        event.register(SignalPayloads.Now.TYPE, (msg, ctx) -> NowClient.accept(msg));
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, Awaken.id("signal"), SignalClient::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (classic()) ClassicHud.render(g, dt);
        else SystemHud.render(g, dt);
        TrackerHud.render(g, dt);
    }

    private static void tick() {
        NowClient.tick();
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
    }
}
