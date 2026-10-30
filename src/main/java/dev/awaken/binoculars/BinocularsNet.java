package dev.awaken.binoculars;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The binoculars' network (SPEC §39.6): {@code awaken:binoculars} (C2S, whether yours are up) and its relay to the players
 * who can see you (S2C, entity id and up or down), and your spotting hold (S2C {@code awaken:binoculars_spot}). Only the
 * pose travels; the zoom is yours alone.
 */
public final class BinocularsNet {
    private BinocularsNet() {}

    /** C2S: the sender raised or lowered their binoculars. */
    public record State(boolean up) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Awaken.id("binoculars"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, State::up, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: another player raised or lowered theirs. */
    public record Relay(int entityId, boolean up) implements CustomPacketPayload {
        public static final Type<Relay> TYPE = new Type<>(Awaken.id("binoculars_relay"));
        public static final StreamCodec<ByteBuf, Relay> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Relay::entityId, ByteBufCodecs.BOOL, Relay::up, Relay::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * S2C: your spotting hold (§39.10). {@code phase}: {@link Spotter.Phase} ordinal (lost, holding, found); {@code hold}:
     * ticks a hold takes, so the ring fills with the server's number.
     */
    public record Spot(int phase, int hold) implements CustomPacketPayload {
        public static final Type<Spot> TYPE = new Type<>(Awaken.id("binoculars_spot"));
        public static final StreamCodec<ByteBuf, Spot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Spot::phase, ByteBufCodecs.VAR_INT, Spot::hold, Spot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(State.TYPE, State.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) BinocularsRelay.receive(sp, p.up(), BinocularsRelay.SEND);
        });
        r.playToClient(Relay.TYPE, Relay.STREAM_CODEC);
        r.playToClient(Spot.TYPE, Spot.STREAM_CODEC);
    }
}
