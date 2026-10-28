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
        modBus.addListener(WorldLife::register);
        modBus.addListener(WorldAir::register);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Pillars::onExtract);
        bus.addListener(Pillars::onAfterLevel);
        bus.addListener(Blight::onTick);
        bus.addListener(Blight::onFogColour);
        bus.addListener(Blight::onFog);
        bus.addListener(GateHum::onTick);
        bus.addListener(GatesClient::tick);
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientGates.clear();
            GateWorldClient.clear();
            WorldBed.stop();
        });
        StatusSections.add(StatusSections.Tab.STATUS, new RunSection()); // a run's summary on the band's Status page
        StatusSections.add(StatusSections.Tab.CODEX, new AtlasSection()); // the Atlas of Gates (SPEC §38.7)
        dev.awaken.core.api.MapMarks.registerGates(RunSection::marks); // the gates on the Atlas (the Gate Map is gone, SPEC §30)
        RigClient.init(modBus);
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(GatePayloads.Beacons.TYPE, (msg, ctx) -> ClientGates.beacons = List.copyOf(msg.beacons()));
        e.register(GatePayloads.RunHud.TYPE, (msg, ctx) -> {
            ClientGates.hud = msg;
            ClientGates.hudAt = System.currentTimeMillis();
        });
        e.register(dev.awaken.gates.net.RigPayloads.Scene.TYPE, (msg, ctx) -> RigClient.scene(msg));
        e.register(dev.awaken.gates.net.RigPayloads.Cue.TYPE, (msg, ctx) -> RigClient.cue(msg));
        e.register(dev.awaken.gates.net.RigPayloads.Run.TYPE, (msg, ctx) -> {
            RigRunHud.run(msg);
            RigGhost.run(msg);
        });
        e.register(dev.awaken.gates.net.RigPayloads.Ghost.TYPE, (msg, ctx) -> RigGhost.ghost(msg));
        e.register(dev.awaken.gates.net.WorldPayloads.Arrival.TYPE, (msg, ctx) -> GateWorldClient.arrive(msg));
        e.register(dev.awaken.gates.net.WorldPayloads.Journey.TYPE, (msg, ctx) -> WorldJourney.start(msg));
        e.register(dev.awaken.gates.net.WorldPayloads.AtlasView.TYPE, (msg, ctx) -> GateWorldClient.atlas(msg));
        e.register(dev.awaken.gates.net.WorldPayloads.Hush.TYPE, (msg, ctx) -> WorldBed.hush(msg.ticks()));
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
        e.registerBlockEntityRenderer(GatesRegistry.PORTAL_BE.get(), GatePortalRenderer::new);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        // after the boss bars, so the top-right stack knows where they end this frame
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, HUD, RunHudLayer::render);
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Awaken.id("gate_rig_clock"), RigRunHud::render);
        // the journey down's dark covers the whole HUD but chat; the world's title card draws over it as it lifts
        e.registerBelow(VanillaGuiLayers.CHAT, Awaken.id("gate_world_journey"), WorldJourney::render);
        e.registerBelow(VanillaGuiLayers.CHAT, Awaken.id("gate_world_card"), WorldCard::render);
    }

    static void tick(ClientTickEvent.Post e) {
        RunHudLayer.tick();
        WorldJourney.tick();
        WorldCard.tick();
        WorldBed.tick();
        WorldLife.tick();
        WorldAir.tick();
    }
}
