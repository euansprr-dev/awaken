package dev.awaken.signal.net;

import dev.awaken.Awaken;
import dev.awaken.core.api.NowHooks;
import dev.awaken.signal.SignalWindow;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

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

    /** One pending decision and the ticks it has left. */
    public record PendingEntry(SignalWindow window, int ticksLeft) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PendingEntry> STREAM_CODEC = StreamCodec.composite(
                SignalWindow.STREAM_CODEC, PendingEntry::window,
                ByteBufCodecs.VAR_INT, PendingEntry::ticksLeft,
                PendingEntry::new);
    }

    /** Server → client: every decision still waiting for this player (login, answers, expiry, withdrawals). */
    public record Pending(List<PendingEntry> entries) implements CustomPacketPayload {
        public static final Type<Pending> TYPE = new Type<>(Awaken.id("signal_pending"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pending> STREAM_CODEC =
                PendingEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Pending::new, Pending::entries);
        @Override public Type<Pending> type() { return TYPE; }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NowHooks.Suggestion> SUGGESTION = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NowHooks.Suggestion::id,
            ByteBufCodecs.VAR_INT, NowHooks.Suggestion::priority,
            ComponentSerialization.TRUSTED_STREAM_CODEC, NowHooks.Suggestion::text,
            ByteBufCodecs.<RegistryFriendlyByteBuf, BlockPos>optional(BlockPos.STREAM_CODEC), NowHooks.Suggestion::where,
            NowHooks.Suggestion::new);

    /**
     * Server → client: the Now tracker's suggestions (best first, up to three). {@code dimension} is where the player
     * was when they were collected: compass positions only count in that dimension.
     */
    public record Now(Identifier dimension, List<NowHooks.Suggestion> suggestions) implements CustomPacketPayload {
        public static final Type<Now> TYPE = new Type<>(Awaken.id("signal_now"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Now> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Now::dimension,
                SUGGESTION.apply(ByteBufCodecs.list(8)), Now::suggestions,
                Now::new);
        @Override public Type<Now> type() { return TYPE; }

        public Optional<NowHooks.Suggestion> top() {
            return suggestions.isEmpty() ? Optional.empty() : Optional.of(suggestions.get(0));
        }
    }
}
