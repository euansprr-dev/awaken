package dev.awaken.signal.net;

import dev.awaken.Awaken;
import dev.awaken.signal.SignalWindow;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Network messages for the Signal. */
public final class SignalPayloads {
    private SignalPayloads() {}

    /** Server → client: show a window. */
    public record Show(SignalWindow window) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(Awaken.id("signal_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC =
                SignalWindow.STREAM_CODEC.map(Show::new, Show::window);
        @Override public Type<Show> type() { return TYPE; }
    }

    /** Server → client: a small toast line (combat-safe). */
    public record Toast(Component text) implements CustomPacketPayload {
        public static final Type<Toast> TYPE = new Type<>(Awaken.id("signal_toast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toast> STREAM_CODEC =
                ComponentSerialization.TRUSTED_STREAM_CODEC.map(Toast::new, Toast::text);
        @Override public Type<Toast> type() { return TYPE; }
    }

    /** Client → server: the player answered a window's action (accepted = Y, false = N). */
    public record Answer(String action, boolean accepted) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(Awaken.id("signal_answer"));
        public static final StreamCodec<ByteBuf, Answer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Answer::action,
                ByteBufCodecs.BOOL, Answer::accepted,
                Answer::new);
        @Override public Type<Answer> type() { return TYPE; }
    }
}
