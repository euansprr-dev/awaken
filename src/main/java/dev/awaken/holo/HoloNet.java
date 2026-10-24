package dev.awaken.holo;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The holo module's network: {@code awaken:holo_state} (C2S, which panel you have open), its relay to the players who
 * can see you (S2C, entity id + kind, never the panel's content) and the admin demo trigger.
 */
public final class HoloNet {
    private HoloNet() {}

    /** C2S: the panel the sender has open. The kind travels as its raw id so the server can refuse unknown ones. */
    public record State(int kind) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Awaken.id("holo_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, State::kind, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: another player opened or closed a panel. */
    public record Relay(int entityId, int kind) implements CustomPacketPayload {
        public static final Type<Relay> TYPE = new Type<>(Awaken.id("holo_state_relay"));
        public static final StreamCodec<ByteBuf, Relay> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Relay::entityId, ByteBufCodecs.VAR_INT, Relay::kind, Relay::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: {@code /awaken holo demo}. 0 hides the demo, 1 shows the view panel, 2 plays the wrist unfold. */
    public record Demo(int mode) implements CustomPacketPayload {
        public static final Type<Demo> TYPE = new Type<>(Awaken.id("holo_demo"));
        public static final StreamCodec<ByteBuf, Demo> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Demo::mode, Demo::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(State.TYPE, State.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) HoloRelay.receive(sp, p.kind(), HoloRelay.SEND);
        });
        r.playToClient(Relay.TYPE, Relay.STREAM_CODEC);
        r.playToClient(Demo.TYPE, Demo.STREAM_CODEC);
    }
}
