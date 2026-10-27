package dev.awaken.signal.net;

import dev.awaken.Awaken;
import dev.awaken.core.api.HorizonHooks;
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

    /** Server → client: a small toast line (combat-safe), with its tier (SPEC §26) and merge topic. */
    public record Toast(Component text, int tier, String topic) implements CustomPacketPayload {
        public static final Type<Toast> TYPE = new Type<>(Awaken.id("signal_toast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toast> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Toast::text,
                ByteBufCodecs.VAR_INT, Toast::tier,
                ByteBufCodecs.STRING_UTF8, Toast::topic,
                Toast::new);
        @Override public Type<Toast> type() { return TYPE; }
    }

    /** Server → client: a tip's context came up ({@code offer}) or the player did what it teaches (SPEC §27.7). */
    public record Tip(String id, boolean offer) implements CustomPacketPayload {
        public static final Type<Tip> TYPE = new Type<>(Awaken.id("signal_tip"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tip> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Tip::id,
                ByteBufCodecs.BOOL, Tip::offer,
                Tip::new);
        @Override public Type<Tip> type() { return TYPE; }
    }

    /**
     * Server → client: the current task, top right (SPEC §27.10): a title and its steps as a checklist. A row's text may
     * hold "{key}", drawn as the key cap of {@code key} (a key mapping's name); {@code need} above 1 shows "have/need";
     * {@code at} (with {@code marked}) is a place nearby to do it. An empty title clears the panel.
     */
    public record TaskRow(String text, String key, boolean done, int have, int need, boolean marked, net.minecraft.core.BlockPos at, String place) {
        public static final StreamCodec<ByteBuf, TaskRow> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public TaskRow decode(ByteBuf b) {
                return new TaskRow(ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean(),
                        ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readBoolean(), net.minecraft.core.BlockPos.STREAM_CODEC.decode(b),
                        ByteBufCodecs.STRING_UTF8.decode(b));
            }

            @Override
            public void encode(ByteBuf b, TaskRow r) {
                ByteBufCodecs.STRING_UTF8.encode(b, r.text());
                ByteBufCodecs.STRING_UTF8.encode(b, r.key());
                b.writeBoolean(r.done());
                ByteBufCodecs.VAR_INT.encode(b, r.have());
                ByteBufCodecs.VAR_INT.encode(b, r.need());
                b.writeBoolean(r.marked());
                net.minecraft.core.BlockPos.STREAM_CODEC.encode(b, r.at());
                ByteBufCodecs.STRING_UTF8.encode(b, r.place());
            }
        };
    }

    /**
     * {@code dimension}: where the player stood when it was sent (a row's place counts only there). A row's {@code place}
     * names where it sends you, for the Now line (SPEC §27.11); empty when its mark is only a hint nearby (a ledge).
     */
    public record Task(String id, String title, java.util.List<TaskRow> rows, Identifier dimension) implements CustomPacketPayload {
        public static final Type<Task> TYPE = new Type<>(Awaken.id("signal_task"));
        public static final StreamCodec<ByteBuf, Task> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Task::id,
                ByteBufCodecs.STRING_UTF8, Task::title,
                TaskRow.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Task::rows,
                Identifier.STREAM_CODEC, Task::dimension,
                Task::new);
        @Override public Type<Task> type() { return TYPE; }
    }

    /** Server → client: the player's destination (SPEC §16.8), or none ({@code set} false). */
    public record Destination(boolean set, String name, Identifier dimension, net.minecraft.core.BlockPos at, String kind) implements CustomPacketPayload {
        public static final Type<Destination> TYPE = new Type<>(Awaken.id("signal_destination"));
        public static final StreamCodec<ByteBuf, Destination> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Destination::set,
                ByteBufCodecs.STRING_UTF8, Destination::name,
                Identifier.STREAM_CODEC, Destination::dimension,
                net.minecraft.core.BlockPos.STREAM_CODEC, Destination::at,
                ByteBufCodecs.STRING_UTF8, Destination::kind,
                Destination::new);
        @Override public Type<Destination> type() { return TYPE; }

        public static Destination of(Optional<dev.awaken.core.api.Destinations.Target> t) {
            return t.map(d -> new Destination(true, d.name(), d.dimension(), d.at(), d.kind()))
                    .orElseGet(() -> new Destination(false, "", Identifier.withDefaultNamespace("overworld"), net.minecraft.core.BlockPos.ZERO, ""));
        }

        public Optional<dev.awaken.core.api.Destinations.Target> target() {
            return set ? Optional.of(new dev.awaken.core.api.Destinations.Target(name, dimension, at, kind)) : Optional.empty();
        }
    }

    /** Client → server: make this place (in the dimension the player stands in) the destination; {@code set} false clears it. */
    public record SetDestination(boolean set, String name, net.minecraft.core.BlockPos at, String kind) implements CustomPacketPayload {
        public static final Type<SetDestination> TYPE = new Type<>(Awaken.id("signal_set_destination"));
        public static final StreamCodec<ByteBuf, SetDestination> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SetDestination::set,
                ByteBufCodecs.stringUtf8(96), SetDestination::name,
                net.minecraft.core.BlockPos.STREAM_CODEC, SetDestination::at,
                ByteBufCodecs.stringUtf8(16), SetDestination::kind,
                SetDestination::new);
        @Override public Type<SetDestination> type() { return TYPE; }
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

    public static final StreamCodec<ByteBuf, HorizonHooks.Line> LINE = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HorizonHooks.Line::id,
            ByteBufCodecs.STRING_UTF8, HorizonHooks.Line::name,
            BlockPos.STREAM_CODEC, HorizonHooks.Line::where,
            ByteBufCodecs.STRING_UTF8, HorizonHooks.Line::need,
            ByteBufCodecs.BOOL, HorizonHooks.Line::tease,
            HorizonHooks.Line::new);

    /** Server → client: the Horizon line (SPEC §17), or none. {@code dimension}: where it was picked (for the compass). */
    public record Horizon(Identifier dimension, Optional<HorizonHooks.Line> line) implements CustomPacketPayload {
        public static final Type<Horizon> TYPE = new Type<>(Awaken.id("signal_horizon"));
        public static final StreamCodec<ByteBuf, Horizon> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Horizon::dimension,
                ByteBufCodecs.optional(LINE), Horizon::line,
                Horizon::new);
        @Override public Type<Horizon> type() { return TYPE; }
    }

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
