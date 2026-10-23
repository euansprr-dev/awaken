package dev.awaken.gates.client;

import dev.awaken.Awaken;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** Client entry point for the gates module. Only loaded on the client. */
public final class GatesClient {
    private GatesClient() {}

    private static final Identifier HUD = Awaken.id("gate_run_hud");

    public static void init(IEventBus modBus) {
        modBus.addListener(GatesClient::payloads);
        modBus.addListener(GatesClient::renderers);
        modBus.addListener(GatesClient::layers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Pillars::onExtract);
        bus.addListener(Pillars::onSubmit);
        bus.addListener(Blight::onTick);
        bus.addListener(Blight::onFogColour);
        bus.addListener(Blight::onFog);
        bus.addListener(GateHum::onTick);
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientGates.clear());
        StatusSections.add(StatusSections.Tab.GATE_MAP, new GateMapSection());
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(GatePayloads.Beacons.TYPE, (msg, ctx) -> ClientGates.beacons = List.copyOf(msg.beacons()));
        e.register(GatePayloads.RunHud.TYPE, (msg, ctx) -> {
            ClientGates.hud = msg;
            ClientGates.hudAt = System.currentTimeMillis();
        });
        e.register(GatePayloads.SatchelView.TYPE, (msg, ctx) -> {
            ClientGates.satchel = List.copyOf(msg.items());
            ClientGates.satchelXp = msg.xp();
            if (msg.open()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof SatchelScreen) return;
                if (mc.gui.screen() != null) mc.gui.pushScreenLayer(new SatchelScreen());
                else mc.gui.setScreen(new SatchelScreen());
            }
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(GatesRegistry.SHADE.get(), ShadeRenderer::new);
        e.registerEntityRenderer(GatesRegistry.GHOST.get(), ShadeRenderer::new);
        e.registerEntityRenderer(GatesRegistry.HOLLOW.get(), ShadeRenderer::new);
        e.registerEntityRenderer(GatesRegistry.HOARDER.get(), ZombieRenderer::new);
        e.registerBlockEntityRenderer(GatesRegistry.VORTEX_BE.get(), GateVortexRenderer::new);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.EFFECTS, HUD, RunHudLayer::render);
    }

    static void tick(ClientTickEvent.Post e) {
    }
}
