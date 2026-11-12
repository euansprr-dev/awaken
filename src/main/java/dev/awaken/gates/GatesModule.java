package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.run.Interactions;
import dev.awaken.gates.run.Spoils;
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(dev.awaken.gates.worlds.WorldThemes::chat);
        dev.awaken.gates.realms.RealmsModule.init(modBus);
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
        dev.awaken.gates.run.EncounterTests.register();
        dev.awaken.gates.run.TogetherRunTests.register();
        dev.awaken.gates.run.SpoilsTests.register();
        dev.awaken.gates.run.FairGateTests.register();
        TogetherGateTests.register();
        RigTests.register();
        dev.awaken.gates.world.GateWorldTests.register();
        dev.awaken.gates.world.GateWorldBellTests.register();
        dev.awaken.gates.world.GateDepthTests.register();
        dev.awaken.gates.world.PlacementTests.register();
        dev.awaken.gates.worlds.WorldLiveTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(GatePayloads.Beacons.TYPE, GatePayloads.Beacons.STREAM_CODEC);
        r.playToClient(GatePayloads.RealmLights.TYPE, GatePayloads.RealmLights.STREAM_CODEC);
        r.playToClient(GatePayloads.RunHud.TYPE, GatePayloads.RunHud.STREAM_CODEC);
        r.playToClient(GatePayloads.LordLevel.TYPE, GatePayloads.LordLevel.STREAM_CODEC);
        r.playToClient(GatePayloads.Crossing.TYPE, GatePayloads.Crossing.STREAM_CODEC);
        r.playToClient(GatePayloads.SpoilsView.TYPE, GatePayloads.SpoilsView.STREAM_CODEC);
        r.playToClient(GatePayloads.SpoilsFlight.TYPE, GatePayloads.SpoilsFlight.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.WorldPayloads.Arrival.TYPE, dev.awaken.gates.net.WorldPayloads.Arrival.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.WorldPayloads.Journey.TYPE, dev.awaken.gates.net.WorldPayloads.Journey.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.WorldPayloads.AtlasView.TYPE, dev.awaken.gates.net.WorldPayloads.AtlasView.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.WorldPayloads.Hush.TYPE, dev.awaken.gates.net.WorldPayloads.Hush.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Scene.TYPE, dev.awaken.gates.net.RigPayloads.Scene.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Cue.TYPE, dev.awaken.gates.net.RigPayloads.Cue.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Run.TYPE, dev.awaken.gates.net.RigPayloads.Run.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.RigPayloads.Ghost.TYPE, dev.awaken.gates.net.RigPayloads.Ghost.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.TrialPayloads.Tally.TYPE, dev.awaken.gates.net.TrialPayloads.Tally.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.BeatPayloads.Lesson.TYPE, dev.awaken.gates.net.BeatPayloads.Lesson.STREAM_CODEC);
        r.playToClient(dev.awaken.gates.net.BeatPayloads.Clean.TYPE, dev.awaken.gates.net.BeatPayloads.Clean.STREAM_CODEC);
        r.playToServer(GatePayloads.WatchSpoils.TYPE, GatePayloads.WatchSpoils.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Spoils.watch(sp, msg.watching());
        });
        r.playToServer(GatePayloads.TakeHeld.TYPE, GatePayloads.TakeHeld.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Spoils.take(sp, msg.index(), msg.item(), msg.all());
        });
    }
}
