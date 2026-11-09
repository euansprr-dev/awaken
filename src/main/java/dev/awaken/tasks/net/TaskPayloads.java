package dev.awaken.tasks.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/** What the Tasks page shows, and what its buttons ask for (SPEC §27.13). */
public final class TaskPayloads {
    private TaskPayloads() {}

    /**
     * The page's operations: pin a step, unpin, the page opened or closed (it's sent more often while open), steps seen
     * (a goal's ordinal as text, or one step's key), a choice picked on a step ("step key|choice id"), and a step set
     * aside or put back.
     */
    public static final int PIN = 0, UNPIN = 1, OPEN = 2, CLOSED = 3, SEEN = 4, CHOOSE = 5, ASIDE = 6, PUT_BACK = 7;
    /** A step's place when it isn't one of its tab's rows. */
    public static final int LATER = -1, SET_ASIDE = -2;

    private static final StreamCodec<ByteBuf, String> S = ByteBufCodecs.STRING_UTF8;

    /** One need: the item ("#" and a tag id for a tag; "" for a condition), its words, have and need. */
    public record NeedRow(String item, String label, int have, int need) {
        public static final StreamCodec<ByteBuf, NeedRow> STREAM_CODEC = StreamCodec.composite(
                S, NeedRow::item, S, NeedRow::label, ByteBufCodecs.VAR_INT, NeedRow::have, ByteBufCodecs.VAR_INT, NeedRow::need, NeedRow::new);

        public boolean met() {
            return have >= need;
        }
    }

    private static final StreamCodec<ByteBuf, List<NeedRow>> NEEDS = NeedRow.STREAM_CODEC.apply(ByteBufCodecs.list(24));

    /** One pick (an armour set, a room for your home): its id, name, line, the items that show it, what it needs, and what's said once picked. */
    public record ChoiceRow(String id, String title, String sub, List<String> show, List<NeedRow> needs, String detail) {
        public static final StreamCodec<ByteBuf, ChoiceRow> STREAM_CODEC = StreamCodec.composite(
                S, ChoiceRow::id, S, ChoiceRow::title, S, ChoiceRow::sub, S.apply(ByteBufCodecs.list(8)), ChoiceRow::show,
                NEEDS, ChoiceRow::needs, S, ChoiceRow::detail, ChoiceRow::new);
    }

    /**
     * What SPEC §53 added to a row: the line under it ("Then: ..." or "Pick one: ..."), whether you have all it needs, the
     * step's voice (Goals.Voice's ordinal), its thread, its place on its tab ({@link #LATER}, {@link #SET_ASIDE} off it),
     * what it gets you, and whether it can't be set aside.
     */
    public record Extra(String then, boolean ready, int voice, String thread, int place, String opens, boolean urgent) {
        public static final StreamCodec<ByteBuf, Extra> STREAM_CODEC = StreamCodec.composite(
                S, Extra::then, ByteBufCodecs.BOOL, Extra::ready, ByteBufCodecs.VAR_INT, Extra::voice, S, Extra::thread, ByteBufCodecs.VAR_INT, Extra::place,
                S, Extra::opens, ByteBufCodecs.BOOL, Extra::urgent, Extra::new);
    }

    /**
     * One step: its key, goal (Goals.Goal's ordinal), words, needs, place, whether it's on the main path, optional, new to
     * the player, its picks and the one picked ("" for none).
     */
    public record StepRow(String key, int goal, String title, String why, List<NeedRow> needs, Optional<BlockPos> where, boolean path,
                          boolean optional, boolean fresh, List<ChoiceRow> choices, String chosen, Extra extra) {
        public static final StreamCodec<ByteBuf, StepRow> STREAM_CODEC = StreamCodec.composite(
                S, StepRow::key, ByteBufCodecs.VAR_INT, StepRow::goal, S, StepRow::title, S, StepRow::why, NEEDS, StepRow::needs,
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC), StepRow::where, ByteBufCodecs.BOOL, StepRow::path, ByteBufCodecs.BOOL, StepRow::optional,
                ByteBufCodecs.BOOL, StepRow::fresh, ChoiceRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)), StepRow::choices, S, StepRow::chosen, Extra.STREAM_CODEC, StepRow::extra, StepRow::new);

        /** One of its tab's rows, not waiting in Later. */
        public boolean shown() {
            return extra.place() >= 0;
        }

        public boolean aside() {
            return extra.place() == SET_ASIDE;
        }
    }

    /**
     * Server → the player: every step (each tab's rows in their places first, then what waits), the one on top, the pinned
     * one, each goal's footer by ordinal, the goals whose tab has opened as bits, and whether the cap is on. Sent when it changes.
     */
    public record View(List<StepRow> steps, String top, String pinned, List<String> footers, int tabs, boolean capped) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(Awaken.id("tasks_view"));
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                StepRow.STREAM_CODEC.apply(ByteBufCodecs.list(96)), View::steps, S, View::top, S, View::pinned,
                S.apply(ByteBufCodecs.list(8)), View::footers, ByteBufCodecs.VAR_INT, View::tabs, ByteBufCodecs.BOOL, View::capped, View::new);

        public static final View EMPTY = new View(List.of(), "", "", List.of(), 0, true);

        public String footer(int goal) {
            return goal >= 0 && goal < footers.size() ? footers.get(goal) : "";
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The player → server: one of the page's operations, on a step's key (or a goal's ordinal as text for SEEN). */
    public record Act(int op, String key) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(Awaken.id("tasks_act"));
        public static final StreamCodec<ByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Act::op, S, Act::key, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
