package dev.awaken.signal;

import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Signal: the voice of the system that chose you. See docs/SPEC.md section 5 and docs/modules/system-ui.md. */
public final class SignalModule {
    private SignalModule() {}

    public static void init(IEventBus modBus) {
        PendingChoices.ATTACHMENTS.register(modBus);
        modBus.addListener(SignalModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(SignalModule::onLogin);
        bus.addListener(SignalModule::onLogout);
        bus.addListener(SignalModule::onChangedDimension);
        bus.addListener(SignalModule::onServerTick);
        bus.addListener((ServerStoppingEvent e) -> {
            NowSync.clear();
            SignalCommands.clear();
        });
        SignalCommands.register();
        SignalTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(SignalPayloads.Show.TYPE, SignalPayloads.Show.STREAM_CODEC);
        r.playToClient(SignalPayloads.Toast.TYPE, SignalPayloads.Toast.STREAM_CODEC);
        r.playToClient(SignalPayloads.Pending.TYPE, SignalPayloads.Pending.STREAM_CODEC);
        r.playToClient(SignalPayloads.Now.TYPE, SignalPayloads.Now.STREAM_CODEC);
        r.playToServer(SignalPayloads.Answer.TYPE, SignalPayloads.Answer.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Signal.answer(sp, msg.action(), msg.accepted());
        });
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        NowSync.forget(p.getUUID());
        PendingChoices.sync(p); // folded: they show in the tracker and the Status screen, not as cards
        NowSync.update(p);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        NowSync.forget(e.getEntity().getUUID());
        SignalCommands.forget(e.getEntity().getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) NowSync.update(p);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        NowSync.tick(server);
        if (server.getTickCount() % 100 == 37) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) PendingChoices.sweep(p);
        }
    }
}
