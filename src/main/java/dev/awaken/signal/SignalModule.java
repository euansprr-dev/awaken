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
        NoticeHistory.ATTACHMENTS.register(modBus);
        dev.awaken.core.api.NoticeEvents.install(NoticeHistory::tell);
        Destination.ATTACHMENTS.register(modBus);
        Destination.init();
        modBus.addListener(SignalModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(SignalModule::onLogin);
        bus.addListener(SignalModule::onLogout);
        bus.addListener(SignalModule::onChangedDimension);
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) { Destination.sync(p); NoticeHistory.sync(p); }
        });
        bus.addListener(SignalModule::onServerTick);
        bus.addListener((ServerStoppingEvent e) -> {
            NowSync.clear();
            HorizonSync.clear();
            SignalCommands.clear();
        });
        // what this module puts in front of a player as something to do (SPEC §27.12): the destination, the Now
        // suggestions the band lists, decisions waiting
        dev.awaken.core.api.Shown.register("destination", p -> dev.awaken.core.api.Destinations.get(p).map(t -> java.util.List.of(t.name())).orElse(java.util.List.of()));
        dev.awaken.core.api.Shown.register("now", p -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (var s : dev.awaken.core.api.NowHooks.collect(p, 64)) out.add(s.text().getString());
            return out;
        });
        dev.awaken.core.api.Shown.register("choices", p -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (SignalWindow w : Signal.pending(p)) out.add(w.title().getString() + w.body().map(b -> " · " + b.getString()).orElse(""));
            return out;
        });
        SignalCommands.register();
        SignalTests.register();
        NoticeHistoryTests.register();
        // tips (SPEC §27.7): the server's side only says a context came up or the move was done; the client decides
        dev.awaken.core.api.TipHooks.install(new dev.awaken.core.api.TipHooks.Server() {
            @Override public void offer(net.minecraft.server.level.ServerPlayer p, net.minecraft.resources.Identifier tip) {
                dev.awaken.core.AwakenNet.send(p, new SignalPayloads.Tip(tip.toString(), true));
            }
            @Override public void done(net.minecraft.server.level.ServerPlayer p, net.minecraft.resources.Identifier tip) {
                dev.awaken.core.AwakenNet.send(p, new SignalPayloads.Tip(tip.toString(), false));
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(NoticeHistory.Update.TYPE, NoticeHistory.Update.STREAM_CODEC);
        r.playToServer(NoticeHistory.Read.TYPE, NoticeHistory.Read.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) NoticeHistory.read(p, msg.sequence());
        });
        r.playToClient(SignalPayloads.Show.TYPE, SignalPayloads.Show.STREAM_CODEC);
        r.playToClient(SignalPayloads.Toast.TYPE, SignalPayloads.Toast.STREAM_CODEC);
        r.playToClient(SignalPayloads.Pending.TYPE, SignalPayloads.Pending.STREAM_CODEC);
        r.playToClient(SignalPayloads.Now.TYPE, SignalPayloads.Now.STREAM_CODEC);
        r.playToClient(SignalPayloads.Horizon.TYPE, SignalPayloads.Horizon.STREAM_CODEC);
        r.playToClient(SignalPayloads.Tip.TYPE, SignalPayloads.Tip.STREAM_CODEC);
        r.playToClient(SignalPayloads.Task.TYPE, SignalPayloads.Task.STREAM_CODEC);
        r.playToClient(SignalPayloads.Destination.TYPE, SignalPayloads.Destination.STREAM_CODEC);
        r.playToServer(SignalPayloads.SetDestination.TYPE, SignalPayloads.SetDestination.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Destination.request(sp, msg);
        });
        r.playToServer(SignalPayloads.Answer.TYPE, SignalPayloads.Answer.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Signal.answer(sp, msg.action(), msg.accepted());
        });
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        NowSync.forget(p.getUUID());
        HorizonSync.forget(p.getUUID());
        NoticeHistory.sync(p);
        PendingChoices.sync(p); // folded: they show in the tracker and the Status screen, not as cards
        NowSync.update(p);
        HorizonSync.update(p);
        Destination.sync(p);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        NowSync.forget(e.getEntity().getUUID());
        HorizonSync.forget(e.getEntity().getUUID());
        SignalCommands.forget(e.getEntity().getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            NowSync.update(p);
            HorizonSync.update(p);
            Destination.sync(p);
        }
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        NowSync.tick(server);
        HorizonSync.tick(server);
        Destination.tick(server);
        if (server.getTickCount() % 100 == 37) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) PendingChoices.sweep(p);
        }
    }
}
