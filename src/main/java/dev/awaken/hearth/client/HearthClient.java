package dev.awaken.hearth.client;

import com.google.common.reflect.TypeToken;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.echo.Echoes;
import dev.awaken.progression.client.CodexEntries;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/** Client entry point for the hearth module. Only loaded on the client. */
public final class HearthClient {
    private HearthClient() {}

    /** Echoes render as dark shapes with a violet edge. */
    public static final int ECHO_OUTLINE = 0xFF6A3FD0;

    public static void init(IEventBus modBus) {
        modBus.addListener(HearthClient::renderers);
        modBus.addListener(HearthClient::particles);
        modBus.addListener(HearthClient::renderStates);
        modBus.addListener(GuideClient::payloads);
        modBus.addListener(HomeClient::payloads);
        modBus.addListener(HomeHud::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            EchoKeys.tick();
            AuraFx.tick();
            GuideClient.tick();
            HomeClient.tick();
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.tickCount % 20 == 0) dev.awaken.hearth.player.ClientHome.set(mc.player.getData(HearthContent.VIEW.get()).home());
        });
        NeoForge.EVENT_BUS.addListener(HearthClient::tooltip);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            GuideClient.clear();
            HomeClient.clear();
            HomeHud.clear();
            dev.awaken.hearth.player.ClientHome.set(null);
        });
        StatusSections.add(StatusSections.Tab.STATUS, new RoomSection());
        StatusSections.add(StatusSections.Tab.STATUS, new HearthSection());
        dev.awaken.citizens.client.CityGlow.source(HomeClient.GLOW);
        CodexEntries.addSource(HearthClient::rumours);
        // (W6) from Tier IV your village has its name on the Atlas, as the settlements of the world do
        dev.awaken.core.api.MapMarks.register(p -> {
            var v = p.getData(HearthContent.VIEW.get());
            if (!v.has() || v.tier() < 4 || v.home().name().isEmpty()) return List.of();
            return List.of(new dev.awaken.core.api.MapMarks.Mark(v.pos(), 0xFFE2B450, v.home().name() + (v.tier() >= 5 ? " · your town" : " · your village"), true));
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(HearthContent.TOWNSFOLK.get(), TownNpcRenderer::new);
        e.registerEntityRenderer(HearthContent.TRAINING_GHOST.get(), GhostRenderer::new);
        e.registerBlockEntityRenderer(HearthContent.TROPHY_MOUNT_BE.get(), TrophyMountRenderer::new);
        e.registerBlockEntityRenderer(HearthContent.SPIRE_BOARD_BE.get(), SpireBoardRenderer::new);
        e.registerBlockEntityRenderer(HearthContent.CHECKPOINT_RING_BE.get(), CheckpointRenderer::new);
    }

    private static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(HearthContent.ECHO_WISP.get(), HearthParticles.Wisp.Provider::new);
        e.registerSpriteSet(HearthContent.HEARTH_EMBER.get(), HearthParticles.Ember.Provider::new);
        e.registerSpriteSet(HearthContent.HEARTH_RING.get(), HearthParticles.Mark.RingProvider::new);
        e.registerSpriteSet(HearthContent.HEARTH_LEAK.get(), HearthParticles.Mark.LeakProvider::new);
        e.registerSpriteSet(HearthContent.HEARTH_WARMTH.get(), HearthParticles.Warmth.Provider::new);
        e.registerSpriteSet(HearthContent.HEARTH_FLAME.get(), HearthParticles.Flame.Provider::new);
        e.registerSpriteSet(HearthContent.HEARTH_FIREFLY.get(), HearthParticles.Firefly.Provider::new);
    }

    private static void renderStates(RegisterRenderStateModifiersEvent e) {
        e.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, (entity, state) -> {
            if (!Echoes.isEcho(entity)) return;
            state.lightCoords = LightCoordsUtil.pack(1, 2);
            state.outlineColor = ECHO_OUTLINE;
        });
    }

    private static void tooltip(ItemTooltipEvent e) {
        int t = e.getItemStack().getOrDefault(HearthContent.TEMPER.get(), 0);
        if (t > 0) e.getToolTip().add(1, Component.literal("Tempered " + "✦".repeat(t)).withStyle(ChatFormatting.GOLD));
        if (Boolean.TRUE.equals(e.getItemStack().get(HearthContent.HOME_COOKED.get()))) {
            e.getToolTip().add(Math.min(1, e.getToolTip().size()), Component.literal("Home-cooked · lasts half again as long").withStyle(ChatFormatting.GOLD));
        }
    }

    private static List<CodexEntries.Entry> rumours() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return List.of();
        var view = mc.player.getData(HearthContent.VIEW.get());
        List<CodexEntries.Entry> out = new ArrayList<>();
        for (var r : view.rumours()) out.add(new CodexEntries.Entry("rumour/" + r.id(), "Rumours", r.title(), r.body(), false, List.of()));
        return out;
    }
}
