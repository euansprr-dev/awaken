package dev.awaken.powers.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Network messages for the first-power choice (plan v2 §4.1). */
public final class StarterPayloads {
    private StarterPayloads() {}

    /** Server → client: choose one of these power ids (the three cards). */
    public record Open(List<String> powers) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Awaken.id("starter_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(3)), Open::powers, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** Server → client: the choice is made (close the screen, forget the cards). */
    public record Done(String power) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Awaken.id("starter_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Done::power, Done::new);
        @Override public Type<Done> type() { return TYPE; }
    }

    /** Client → server: this card. */
    public record Choose(String power) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(Awaken.id("starter_choose"));
        public static final StreamCodec<ByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Choose::power, Choose::new);
        @Override public Type<Choose> type() { return TYPE; }
    }
}
