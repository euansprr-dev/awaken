package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.client.feel.FeelDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry of the Ascent: prediction, other players' ropes and wings, the feel, the light, the tour pilot. */
public final class AscentClient {
    private AscentClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AscentClient::onPayloads);
        modBus.addListener((RegisterParticleProvidersEvent e) -> e.registerSpriteSet(AscentContent.MOTE.get(), MoteParticle.Provider::new));
        modBus.addListener(AscentClient::onAddLayers);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(AscentContent.UPDRAFT_VENT_BE.get(), UpdraftRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, dev.awaken.Awaken.id("ascent_flow"), AscentFlow::render));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, dev.awaken.Awaken.id("ascent_scene"), AscentScene::render));
        FeelDirector.init(modBus);
        var bus = NeoForge.EVENT_BUS;
        // before the Awakened Body's own moves: an airborne state owns the body and the body's moves stand aside
        bus.addListener(EventPriority.HIGH, MovementInputUpdateEvent.class, AscentMoves::onInput);
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ExtractLevelRenderStateEvent.class, AscentRender::onExtract);
        bus.addListener(SubmitCustomGeometryEvent.class, AscentRender::onSubmit);
        bus.addListener(RenderFrameEvent.Post.class, e -> ClipRecorder.frame());
        // the whole figure turns with the flight (after the body's own lean, LOW, and the casts' at LOWEST)
        bus.addListener(EventPriority.HIGH, RenderPlayerEvent.Pre.class, AscentAnims::onPre);
        bus.addListener(EventPriority.LOW, RenderPlayerEvent.Post.class, AscentAnims::onPost);
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> AscentMoves.forget());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            AscentMoves.reset();
            AscentScene.reset();
            dev.awaken.body.ascent.Updrafts.clear();
            AscentRemote.reset();
            AscentPilot.stop();
            AscentRoutes.stop();
            AscentAnims.reset();
            FeelDirector.reset();
        });
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerModelType type : e.getSkins()) {
            AvatarRenderer<?> r = e.getPlayerRenderer(type);
            if (r != null) WingLayer.addTo(r);
        }
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(AscentPayloads.Seen.TYPE, (msg, ctx) -> AscentRemote.seen(msg));
        event.register(AscentPayloads.SeenEvent.TYPE, (msg, ctx) -> AscentRemote.event(msg));
        event.register(AscentPayloads.Tiers.TYPE, (msg, ctx) -> {
            AscentMoves.tiers = msg.bits();
            AscentMoves.mastery = msg.mastery();
            dev.awaken.body.ascent.AscentServer.clientTiers(msg.bits());
        });
        event.register(AscentPayloads.Pilot.TYPE, (msg, ctx) -> AscentPilot.line(msg.line()));
        event.register(AscentPayloads.Stop.TYPE, (msg, ctx) -> AscentMoves.refused(msg.state()));
        event.register(AscentPayloads.Remember.TYPE, (msg, ctx) -> AscentScene.remember(msg.bits()));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        AscentMoves.tick();
        AscentFlow.tick(mc.player);
        AscentScene.tick(mc.player);
        AscentPilot.tick();
        AscentRoutes.tick();
        AscentRemote.tick();
        AscentAnims.tick();
        AscentFx.tick();
        WingLayer.tick();
        AscentSounds.tick();
        FeelDirector.tick();
        ClipRecorder.tick();
    }
}
