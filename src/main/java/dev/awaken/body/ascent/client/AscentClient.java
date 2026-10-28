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
        // the Sovereign's Ascend key (default U; SPEC §17.19)
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> e.register(SovereignClient.ASCEND));
        modBus.addListener((RegisterParticleProvidersEvent e) -> e.registerSpriteSet(AscentContent.MOTE.get(), MoteParticle.Provider::new));
        modBus.addListener(AscentClient::onAddLayers);
        // on a rope the body hangs, never crouches (sneak is the rope's own key there: reeling in, the rappel)
        modBus.addListener((net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent e) -> e.registerEntityModifier(
                new com.google.common.reflect.TypeToken<net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.Entity,
                        net.minecraft.client.renderer.entity.state.EntityRenderState>>() {}, AscentAnims::modify));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(AscentContent.UPDRAFT_VENT_BE.get(), UpdraftRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, dev.awaken.Awaken.id("ascent_flow"), AscentFlow::render));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, dev.awaken.Awaken.id("ascent_scene"), AscentScene::render));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, dev.awaken.Awaken.id("ascent_vow"), VowScene::render));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, dev.awaken.Awaken.id("ascent_notes"), AscentNotes::render));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR, dev.awaken.Awaken.id("ascent_aether"), AscentFlow::renderAether));
        FeelDirector.init(modBus);
        RopeTuning.init();
        // the Tab glance shows the Aether (SPEC §40.7)
        dev.awaken.core.api.HudState.installAether(() -> AscentMoves.has(dev.awaken.body.ascent.AscentPayloads.Tier.SKYBORN) ? AscentMoves.aether : -1);
        var bus = NeoForge.EVENT_BUS;
        // before the Awakened Body's own moves: an airborne state owns the body and the body's moves stand aside
        bus.addListener(EventPriority.HIGH, MovementInputUpdateEvent.class, AscentMoves::onInput);
        // first person (core.api.FirstPerson): the hands through every state of the ladder
        dev.awaken.core.api.FirstPerson.view((pt, v) -> {
            if (dev.awaken.body.client.feel.FeelConfig.fpMoves()) AscentFp.viewPose(pt, v);
        });
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ExtractLevelRenderStateEvent.class, AscentRender::onExtract);
        bus.addListener(SubmitCustomGeometryEvent.class, AscentRender::onSubmit);
        bus.addListener(SubmitCustomGeometryEvent.class, WindwayRenderer::onSubmit);
        bus.addListener(RenderFrameEvent.Post.class, e -> ClipRecorder.frame());
        // the whole figure turns with the flight (after the body's own lean, LOW, and the casts' at LOWEST)
        bus.addListener(EventPriority.HIGH, RenderPlayerEvent.Pre.class, AscentAnims::onPre);
        bus.addListener(EventPriority.LOW, RenderPlayerEvent.Post.class, AscentAnims::onPost);
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> AscentMoves.forget());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            AscentMoves.reset();
            AscentScene.reset();
            VowScene.reset();
            AscentNotes.reset();
            dev.awaken.body.ascent.Updrafts.clear();
            dev.awaken.body.ascent.Windways.setClient(java.util.List.of());
            SovereignClient.reset();
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
            // a level gained (not the first sync of a session, which only fills the copy)
            if (AscentMoves.masterySynced) AscentNotes.mastery(AscentMoves.mastery, msg.mastery());
            AscentMoves.mastery = msg.mastery();
            AscentMoves.masterySynced = true;
            dev.awaken.body.ascent.AscentServer.clientTiers(msg.bits());
        });
        event.register(AscentPayloads.Pilot.TYPE, (msg, ctx) -> AscentPilot.line(msg.line()));
        event.register(AscentPayloads.Stop.TYPE, (msg, ctx) -> AscentMoves.refused(msg.state()));
        event.register(AscentPayloads.Remember.TYPE, (msg, ctx) -> AscentScene.remember(msg.bits(), msg.mode()));
        event.register(AscentPayloads.Vow.TYPE, (msg, ctx) -> VowScene.on(msg));
        event.register(AscentPayloads.Record.TYPE, (msg, ctx) -> AscentNotes.record(msg.chain()));
        event.register(AscentPayloads.Lanes.TYPE, (msg, ctx) -> dev.awaken.body.ascent.Windways.setClient(msg.ways()));
        // the fittings on this player's rope (SPEC §28.5): prediction reads them, a fresh fit is told
        event.register(AscentPayloads.Kit.TYPE, (msg, ctx) -> {
            var kit = dev.awaken.body.ascent.RopeKit.ofBits(msg.bits());
            if (msg.fitted()) AscentNotes.fitted(AscentMoves.kitLine, AscentMoves.kitReel, kit.line(), kit.reel());
            AscentMoves.kitLine = kit.line();
            AscentMoves.kitReel = kit.reel();
            dev.awaken.body.ascent.RopeKit.client = kit;
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        AscentMoves.tick();
        RopeTuning.tick();
        SovereignClient.tick(mc);
        AscentFlow.tick(mc.player);
        AscentScene.tick(mc.player);
        VowScene.tick(mc.player);
        AscentNotes.tick();
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
