package dev.awaken.body;

import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.Later;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.RankHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Awakened Body (docs/modules/body.md, SPEC §13): the awakening felt in the body. Soft landing and tactical sprint
 * at awakening, the slide at D, the mantle at C; momentum hits and trips in a fight. Grounded moves only: anything
 * airborne or long-range is a power. Players who never awakened move exactly like vanilla.
 */
public final class BodyModule {
    private BodyModule() {}

    public static void init(IEventBus modBus) {
        BodyContent.register(modBus);
        modBus.addListener(BodyModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(BodyServer::onPlayerTick);
        bus.addListener(EventPriority.LOW, BodyServer::onFall);
        bus.addListener(BodyServer::onAttack);
        bus.addListener(EventPriority.HIGH, BodyServer::onIncomingDamage);
        bus.addListener(BodyServer::onKnockBack);
        // old saves that already hold a move hear about it once, a moment after joining (with the login's own toasts)
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Later.run(p.level().getServer(), 60, () -> {
                if (!p.isRemoved()) BodyServer.announce(p);
            });
        });
        Progression.onAwakened(BodyServer::announce);
        RankHooks.onRankUp((p, rank) -> BodyServer.announce(p));
        MovementHooks.install(new Hooks());
        BodyCommands.register();
        BodyTests.register();
        dev.awaken.body.ascent.AscentModule.init(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(BodyPayloads.Start.TYPE, BodyPayloads.Start.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) BodyServer.onStart(sp, msg);
        });
        r.playToServer(BodyPayloads.End.TYPE, BodyPayloads.End.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) BodyServer.onEnd(sp, msg);
        });
        r.playToClient(BodyPayloads.Refuse.TYPE, BodyPayloads.Refuse.STREAM_CODEC);
        r.playToClient(BodyPayloads.Seen.TYPE, BodyPayloads.Seen.STREAM_CODEC);
        r.playToClient(BodyPayloads.Try.TYPE, BodyPayloads.Try.STREAM_CODEC);
    }

    /** What powers may ask about the body (core/api/MovementHooks). */
    static final class Hooks implements MovementHooks.Access {
        @Override public boolean sliding(Player p) { return Body.isSliding(p); }
        @Override public boolean tacticalSprinting(Player p) { return Body.isTacticalSprinting(p); }
        @Override public boolean mantling(Player p) { return Body.isMantling(p); }
        @Override public long lastMoveTick(Player p) { return Body.lastMoveTick(p); }
        @Override public @Nullable Vec3 slideDirection(Player p) { return Body.slideDirection(p); }

        /** A cast ends a tactical sprint at once (the cast is never held back); a slide keeps going. */
        @Override public void castStarting(ServerPlayer p) {
            BodyMotion m = p.getExistingDataOrNull(BodyContent.MOTION);
            if (m != null) BodyServer.endTactical(p, m, BodyServer.now(p), true);
        }
    }
}
