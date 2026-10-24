package dev.awaken.body.ascent;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Ascent (SPEC §17, docs/plan-ascent.html): the body's states above the ground (the rope, the momentum you
 * carry, wings, flight) and the ladder of tiers that opens them. Part of the body module: movement is built once.
 * Phase 0 is the feel spike: the states and their feel, tiers granted by command.
 */
public final class AscentModule {
    private AscentModule() {}

    public static void init(IEventBus modBus) {
        AscentContent.register(modBus);
        modBus.addListener(AscentModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(AscentServer::onPlayerTick);
        // before the body's own roll (LOW): an airborne state's landing never hurts
        bus.addListener(EventPriority.HIGH, AscentServer::onFall);
        bus.addListener(AscentServer::onLogin);
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) TetherVow.forget(p);
        });
        bus.addListener(AscentServer::onRespawn);
        bus.addListener(AscentServer::onDimension);
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, AscentServer::onPlace);
        AscentCommands.register();
        AscentTests.register();
        // sky platforms: the ladder of rings and the Wings core, placed once as each platform loads (SPEC §17.9)
        dev.awaken.above.ruin.SkyRig.register(bus);
        // updraft vents are moving air for the free states (the list is filled on the client only)
        dev.awaken.core.api.AirHooks.register(Updrafts::airAt);
        // the Horizon's sky sites ("needs Wings"): Wings, or a rope and the Leaper's air moves, get you up there
        dev.awaken.core.api.HorizonHooks.addReach((p, need) -> need.equals(dev.awaken.core.api.HorizonHooks.WINGS)
                && (Ascent.has(p, Ascent.Tier.WINGS) || Ascent.has(p, Ascent.Tier.TETHER) && Ascent.has(p, Ascent.Tier.LEAPER)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(AscentPayloads.State.TYPE, AscentPayloads.State.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) AscentServer.onState(sp, msg);
        });
        r.playToServer(AscentPayloads.Event.TYPE, AscentPayloads.Event.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) AscentServer.onEvent(sp, msg);
        });
        r.playToClient(AscentPayloads.Seen.TYPE, AscentPayloads.Seen.STREAM_CODEC);
        r.playToClient(AscentPayloads.SeenEvent.TYPE, AscentPayloads.SeenEvent.STREAM_CODEC);
        r.playToClient(AscentPayloads.Tiers.TYPE, AscentPayloads.Tiers.STREAM_CODEC);
        r.playToClient(AscentPayloads.Pilot.TYPE, AscentPayloads.Pilot.STREAM_CODEC);
        r.playToClient(AscentPayloads.Remember.TYPE, AscentPayloads.Remember.STREAM_CODEC);
        r.playToClient(AscentPayloads.Vow.TYPE, AscentPayloads.Vow.STREAM_CODEC);
        r.playToClient(AscentPayloads.Record.TYPE, AscentPayloads.Record.STREAM_CODEC);
        r.playToClient(AscentPayloads.Stop.TYPE, AscentPayloads.Stop.STREAM_CODEC);
    }
}
