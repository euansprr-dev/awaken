package dev.awaken.world.fauna;

import dev.awaken.Awaken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The fauna layer's one payload: the awakening reveal's screen pulse (client: {@code world.fauna.client.RevealPulse}). */
public final class FaunaNet {
    private FaunaNet() {}

    /** {@code strength} 0..100: 100 for the awakening itself, lower for a softer echo. */
    public record Pulse(int strength) implements CustomPacketPayload {
        public static final Type<Pulse> TYPE = new Type<>(Awaken.id("fauna_pulse"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pulse> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Pulse::strength, Pulse::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(Pulse.TYPE, Pulse.STREAM_CODEC);
    }
}
