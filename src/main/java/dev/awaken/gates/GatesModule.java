package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.run.Hud;
import dev.awaken.gates.run.Interactions;
import dev.awaken.progression.DaysEnd;
import dev.awaken.signal.Signal;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Entry point for the gates module (SPEC sections 2, 4.10 and 7). */
public final class GatesModule {
    private GatesModule() {}

    public static void init(IEventBus modBus) {
        GatesRegistry.init(modBus);
        modBus.addListener(GatesModule::registerPayloads);
        GateEvents.register();
        GateAccess.install();
        GateParty.install();
        GatesNow.register();
        PlayDays.onNewPlayDay(Breaks::onNewPlayDay);
        DaysEnd.addWaiting(Breaks::waitingLine);
        Signal.onAction(Interactions.ACTION, Interactions::answer);
        GateCommands.register();
        GateTests.register();
        GateLiveTests.register();
        GateHookTests.register();
        dev.awaken.gates.run.RunRegressionTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(GatePayloads.Beacons.TYPE, GatePayloads.Beacons.STREAM_CODEC);
        r.playToClient(GatePayloads.RunHud.TYPE, GatePayloads.RunHud.STREAM_CODEC);
        r.playToClient(GatePayloads.SatchelView.TYPE, GatePayloads.SatchelView.STREAM_CODEC);
        r.playToServer(GatePayloads.OpenSatchel.TYPE, GatePayloads.OpenSatchel.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Hud.sendSatchel(sp, true);
        });
    }
}
