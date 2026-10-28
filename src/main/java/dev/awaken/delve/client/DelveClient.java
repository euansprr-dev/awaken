package dev.awaken.delve.client;

import dev.awaken.delve.DelveNet;
import dev.awaken.delve.TrueStrikes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The deep on the client (SPEC §28.3): the strike's feel ({@link StrikeFeel}), the heavy strike's charge
 * ({@link HeavyCharge}), a lode's glints and the vein giving ({@link VeinFx}), a cave-in's cracks, dust and fall
 * ({@link CaveInFx}), the Tuning Fork's song and arc ({@link ForkSong}), the miner's lamp ({@link LampGlow}) and light held
 * in a hand ({@link TorchGlow}); the lamps and the vein's light are drawn through {@link DelveRender}.
 */
public final class DelveClient {
    private DelveClient() {}

    public static void init(IEventBus modBus) {
        // W5 (SPEC §28.5): the place you are in and its caption, the way home, the senses below
        CaveClient.init(modBus);
        HomeClient.init(modBus);
        SenseClient.init(modBus);
        modBus.addListener(RegisterParticleProvidersEvent.class, DelveParticles::register);
        modBus.addListener(RegisterRenderPipelinesEvent.class, DelveRender::onRegister);
        // last on our bus, so a block another mod claimed keeps its own particles
        modBus.addListener(EventPriority.LOWEST, false, RegisterClientExtensionsEvent.class, StrikeFx::register);
        DelveClientConfig.register(modBus);
        modBus.addListener(RegisterGuiLayersEvent.class, ForkSong::registerLayer);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(DelveNet.VeinOpen.TYPE, (m, ctx) -> VeinFx.open(m));
            e.register(DelveNet.LodesNear.TYPE, (m, ctx) -> VeinFx.near(m));
            e.register(DelveNet.CaveWarn.TYPE, (m, ctx) -> CaveInFx.warn(m));
            e.register(DelveNet.HeavyFx.TYPE, (m, ctx) -> HeavyCharge.landed(m));
            e.register(DelveNet.ForkRing.TYPE, (m, ctx) -> ForkSong.ring(m));
            e.register(DelveNet.Demo.TYPE, (m, ctx) -> StrikeFeel.demo(m));
            e.register(DelveNet.TrueStrikeSync.TYPE, (m, ctx) -> {
                var p = net.minecraft.client.Minecraft.getInstance().player;
                if (p != null) TrueStrikes.synced(p, net.minecraft.core.BlockPos.of(m.pos()), m.trues());
            });
        });
        // the dig in your hands (SPEC §34.3), keyed to the strike's bites
        dev.awaken.core.api.FirstPerson.view(DigMotion::pose);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerInteractEvent.LeftClickBlock.class, StrikeFeel::onLeftClick);
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, HeavyCharge::onKey);
        bus.addListener(PlayerEvent.BreakSpeed.class, VeinFx::onBreakSpeed);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            StrikeFeel.tick();
            StrikeFx.tick();
            HeavyCharge.tick();
            VeinFx.tick();
            CaveInFx.tick();
            ForkSong.tick();
        });
        bus.addListener(SubmitCustomGeometryEvent.class, LampGlow::submit);
        bus.addListener(SubmitCustomGeometryEvent.class, TorchGlow::submit);
        bus.addListener(SubmitCustomGeometryEvent.class, VeinFx::submit);
        bus.addListener(SubmitCustomGeometryEvent.class, StrikeFx::submit);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            VeinFx.reset();
            CaveInFx.reset();
            ForkSong.reset();
            HeavyCharge.reset();
            TorchGlow.reset();
            StrikeFx.reset();
            TrueStrikes.clientReset();
        });
    }
}
