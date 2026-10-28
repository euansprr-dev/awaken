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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(dev.awaken.gates.worlds.GateWorlds::addReload);
        GateEvents.register();
        GateAccess.install();
        GateParty.install();
        GatesNow.register();
        dev.awaken.core.api.HorizonHooks.register(Director::firstGateSite); // the first gate, raised (SPEC §27.6)
        PlayDays.onNewPlayDay(Breaks::onNewPlayDay);
        DaysEnd.addWaiting(Breaks::waitingLine);
        Signal.onAction(Interactions.ACTION, Interactions::answer);
        GateCommands.register();
        GateTests.register();
        GateLiveTests.register();
        GateHookTests.register();
        dev.awaken.gates.run.RunRegressionTests.register();
        dev.awaken.gates.run.TogetherRunTests.register();
        TogetherGateTests.register();
        RigTests.register();
        dev.awaken.gates.world.GateWorldTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(GatePayloads.Beacons.TYPE, GatePayloads.Beacons.STREAM_CODEC);
        r.playToClient(GatePayloads.RunHud.TYPE, GatePayloads.RunHud.STREAM_CODEC);
        r.playToClient(GatePayloads.SatchelView.TYPE, GatePayloads.SatchelView.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.WorldPayloads.Arrival.TYPE, dev.awaken.gates.net.WorldPayloads.Arrival.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Scene.TYPE, dev.awaken.gates.net.RigPayloads.Scene.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Cue.TYPE, dev.awaken.gates.net.RigPayloads.Cue.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Run.TYPE, dev.awaken.gates.net.RigPayloads.Run.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Ghost.TYPE, dev.awaken.gates.net.RigPayloads.Ghost.STREAM_CODEC);
        r.playToServer(GatePayloads.OpenSatchel.TYPE, GatePayloads.OpenSatchel.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Hud.sendSatchel(sp, true);
        });
    }
}
