package dev.awaken.binoculars.client;

import com.google.common.reflect.TypeToken;
import com.mojang.brigadier.Command;
import dev.awaken.Awaken;
import dev.awaken.binoculars.BinocularsNet;
import dev.awaken.core.api.CameraHooks;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Binoculars, client half (SPEC §39): the key, the zoom and its feel ({@link Glass}, {@link GlassView}), the hands
 * ({@link GlassHands}), the lens edge and readout ({@link GlassOverlay}, {@link Rangefinder}), and what you see of other
 * players' ({@link GlassOthers}). Must be initialised before the anims client: its hand listener has to run before
 * Hands' at the same priority, on a clean pose stack.
 */
public final class BinocularsClient {
    private BinocularsClient() {}

    private static long lastNanos;
    private static CameraType peekRestore;

    public static void init(IEventBus modBus) {
        GlassConfig.register(modBus);
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(Glass.KEY));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, GlassOverlay.LAYER, GlassOverlay::render));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(BinocularsNet.Relay.TYPE, (msg, ctx) -> GlassOthers.onRelay(msg)));
        modBus.addListener(RegisterRenderStateModifiersEvent.class,
                e -> e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, GlassOthers::modify));
        modBus.addListener(EntityRenderersEvent.AddLayers.class, BinocularsClient::onLayers);
        FirstPerson.view(GlassHands::pose);
        CameraHooks.installZoom(Glass::zoom);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RenderFrameEvent.Pre.class, e -> frame());
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeCameraAngles.class, GlassView::angles);
        // last: the zoom divides whatever FOV everything else made (speed effects stay what they are)
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeFov.class, GlassView::fov);
        bus.addListener(EventPriority.LOWEST, ComputeFovModifierEvent.class, GlassView::fovModifier);
        bus.addListener(EventPriority.LOWEST, CalculatePlayerTurnEvent.class, GlassView::turn);
        bus.addListener(EventPriority.HIGH, InputEvent.MouseScrollingEvent.class, GlassView::scroll);
        bus.addListener(EventPriority.HIGH, InputEvent.InteractionKeyMappingTriggered.class, GlassView::interaction);
        bus.addListener(EventPriority.HIGH, RenderGuiLayerEvent.Pre.class, GlassView::hud);
        // HIGHEST and registered before Hands (AwakenClient's order): the binoculars go down on an untouched stack
        bus.addListener(EventPriority.HIGHEST, RenderHandEvent.class, GlassHands::draw);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(RegisterClientCommandsEvent.class, BinocularsClient::commands);
    }

    private static void frame() {
        long now = System.nanoTime();
        float ms = lastNanos == 0 ? 0 : Math.min(100F, (now - lastNanos) / 1_000_000F);
        lastNanos = now;
        GlassView.frameStart();
        try {
            Glass.frame();
            Rangefinder.frame();
            GlassOverlay.focus();
            GlassOthers.advance(ms);
            if (peekRestore != null && now >= GlassOthers.peekUntil) {
                Minecraft.getInstance().options.setCameraType(peekRestore);
                peekRestore = null;
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("Binoculars failed this frame and were lowered", ex);
            reset();
        }
    }

    private static void reset() {
        Glass.reset();
        Rangefinder.reset();
        GlassOverlay.reset();
        GlassOthers.clear();
        peekRestore = null;
    }

    private static void onLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerModelType type : e.getSkins()) {
            AvatarRenderer<?> r = e.getPlayerRenderer(type);
            if (r != null) addLayer(r);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayer(AvatarRenderer r) {
        r.addLayer(new GlassOthers.Layer(r));
    }

    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("binoculars")
                .then(Commands.literal("peek").executes(c -> peek()))));
    }

    /** Five seconds of yourself from the front, binoculars up, as other players see you. */
    private static int peek() {
        Minecraft mc = Minecraft.getInstance();
        Glass.reset();
        if (peekRestore == null) peekRestore = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        GlassOthers.peekUntil = System.nanoTime() + 5_000_000_000L;
        mc.gui.hud.getChat().addClientSystemMessage(Component.translatable("binoculars.awaken.peek"));
        return Command.SINGLE_SUCCESS;
    }
}
