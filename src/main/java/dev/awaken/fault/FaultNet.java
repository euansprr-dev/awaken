package dev.awaken.fault;

import dev.awaken.Awaken;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Fault's wire (SPEC §43.8): play one, and the mend. The client draws and sounds both. */
public final class FaultNet {
    private FaultNet() {}

    /** Server → player: play this Fault now. */
    public record Play(Fault fault) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(Awaken.id("fault_play"));
        public static final StreamCodec<FriendlyByteBuf, Play> STREAM_CODEC = Fault.STREAM_CODEC.map(Play::new, Play::fault);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    /** Server → player: the event is over; the System mends a little. */
    public record Mend() implements CustomPacketPayload {
        public static final Type<Mend> TYPE = new Type<>(Awaken.id("fault_mend"));
        public static final StreamCodec<FriendlyByteBuf, Mend> STREAM_CODEC = StreamCodec.unit(new Mend());

        @Override
        public Type<Mend> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Play.TYPE, Play.STREAM_CODEC);
        r.playToClient(Mend.TYPE, Mend.STREAM_CODEC);
    }
}
