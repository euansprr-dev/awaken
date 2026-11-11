package dev.awaken.mobs.client;

import dev.awaken.mobs.client.anim.HeroRenderer;
import dev.awaken.mobs.client.anim.WildRenderer;
import dev.awaken.mobs.client.kit.KitRenderer;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.function.BiConsumer;

/** Client entry point for the mobs module: renderers and model layers for every creature. Only loaded on the client. */
public final class MobsClient {
    private MobsClient() {}

    public static void init(IEventBus modBus) {
        dev.awaken.mobs.client.kit.KitConfig.register();
        modBus.addListener(AwRenderers::layers);
        modBus.addListener(AwRenderers::renderers);
        modBus.addListener(RitualRenderers::renderers);
        modBus.addListener(SeepParticles::register);
        modBus.addListener(ShotParticles::register);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, MobsClient::looks);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, ToppleView::register); // big beasts on their side (SPEC §47.5)
        modBus.addListener(net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(dev.awaken.mobs.net.MobsNet.Pace.TYPE, (msg, ctx) -> dev.awaken.mobs.client.kit.ServerPace.onPace(msg.gameTime()));
            // the carriers (SPEC §47.2)
            e.register(dev.awaken.mobs.net.MobsNet.Carrier.TYPE, (msg, ctx) -> CarrierView.onCarrier(msg));
            e.register(dev.awaken.mobs.net.MobsNet.CarrierEnd.TYPE, (msg, ctx) -> CarrierView.onEnd(msg.id()));
            e.register(dev.awaken.mobs.net.MobsNet.Tell.TYPE, (msg, ctx) -> {
                var level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) dev.awaken.mobs.entity.ToldBite.heard(msg.id(), msg.ticks(), level.getGameTime());
            });
        });
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent.class, CarrierPipelines::onRegister);
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            dev.awaken.mobs.client.kit.ServerPace.reset();
            CarrierView.reset();
            ShotRenderer.reset();
            dev.awaken.mobs.entity.ToldBite.reset(true);
        });
        bus.addListener(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent.class, CarrierView::submit);
        BodyRest.register(bus); // a dying or lying body rests on the ground, never in it (SPEC §47.12)
        bus.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> CarrierView.tick());
        bus.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, ShotRenderer::onJoin);
        bus.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, dev.awaken.mobs.client.life.LifeTrace::commands); // dev: /awaken lifetrace
    }

    /**
     * NeoForge clears a render state's data right after extractRenderState (RenderStateExtensions), so the look our
     * renderers put there never reached the model: no life layer (SPEC §23), tint, glow or flash. This puts the same
     * look back after the clear, on every renderer that sets one, bosses' too (the kit's springs, breath, blinks and
     * wind-up glow, SPEC §35). Modifiers match by renderer class, so each one that doesn't extend another is named.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void looks(RegisterRenderStateModifiersEvent e) {
        BiConsumer<Entity, EntityRenderState> back = (entity, state) -> {
            if (entity instanceof AwakenMob m) state.setRenderData(AwModels.LOOK, m.lastLook());
        };
        e.registerEntityModifier((Class) HeroRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) WildRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) AwMobRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) AwHumanoidRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) AwSkinRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) KitRenderer.class, (BiConsumer) back);
    }
}
