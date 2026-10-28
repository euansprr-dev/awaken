package dev.awaken.powers.client;

import com.google.common.reflect.TypeToken;
import dev.awaken.Awaken;
import dev.awaken.powers.Powers;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Client entry point for the powers module. Only loaded on the client. */
public final class PowersClient {
    private PowersClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, PowerHud::register);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, PowersClient::payloads);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, PowersClient::renderers);
        modBus.addListener(RegisterSelectItemModelPropertyEvent.class,
                e -> e.register(Awaken.id("core_power"), CorePowerSelect.TYPE));
        modBus.addListener(RegisterRenderStateModifiersEvent.class, PowersClient::stateModifiers);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> {
            ClientPowers.tick();
            ClientCasting.tick();
            CombatFeel.tick();
        });
        bus.addListener(PlayerTickEvent.Pre.class, ClientCasting::onPlayerTick);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, CombatFeel::onCameraAngles);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            CorePreview.clear();
            ClientPowers.reset();
            ClientCasting.reset();
        });

        dev.awaken.powers.PowerPreviews.client = CorePreview::lines;
        Powers.clientLoadout = () -> {
            var p = Minecraft.getInstance().player;
            return p == null ? null : p.getData(PowerContent.LOADOUT.get());
        };
        StatusSections.add(StatusSections.Tab.POWERS, new PowersSection());
        StarterClient.init(modBus);
        dev.awaken.powers.client.fx.FxClient.init(modBus);
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(dev.awaken.powers.PowerPreviews.Reply.TYPE, (msg, ctx) -> CorePreview.accept(msg));
        e.register(PowerPayloads.Feedback.TYPE, (msg, ctx) -> ClientPowers.onFeedback(msg));
        e.register(PowerPayloads.Feel.TYPE, (msg, ctx) -> ClientPowers.onFeel(msg));
        e.register(PowerPayloads.Marks.TYPE, (msg, ctx) -> ClientPowers.onMarks(msg));
        e.register(PowerPayloads.Beam.TYPE, (msg, ctx) -> dev.awaken.powers.client.fx.FxClient.legacyBeam(msg.casterId(), msg.x(), msg.y(), msg.z(),
                msg.colour(), msg.ticks(), msg.width(), msg.range(), msg.follow()));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(PowerContent.SHADOW_SOLDIER.get(), SummonRenderers.Soldier::new);
        e.registerEntityRenderer(PowerContent.DRONE.get(), SummonRenderers.Drone::new);
    }

    private static void stateModifiers(RegisterRenderStateModifiersEvent e) {
        e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, CombatFeel::modify);
    }
}
