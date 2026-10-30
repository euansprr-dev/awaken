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
     * (a goal's ordinal as text, or one step's key), and a choice picked on a step ("step key|choice id").
     */
    public static final int PIN = 0, UNPIN = 1, OPEN = 2, CLOSED = 3, SEEN = 4, CHOOSE = 5;

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

    /** One pick (an armour set): its id, name, line, the items that show it, and what it needs. */
    public record ChoiceRow(String id, String title, String sub, List<String> show, List<NeedRow> needs) {
        public static final StreamCodec<ByteBuf, ChoiceRow> STREAM_CODEC = StreamCodec.composite(
                S, ChoiceRow::id, S, ChoiceRow::title, S, ChoiceRow::sub, S.apply(ByteBufCodecs.list(8)), ChoiceRow::show,
                NEEDS, ChoiceRow::needs, ChoiceRow::new);
    }

    /**
     * One step: its key, goal (Goals.Goal's ordinal), words, needs, place, whether it's on the main path, optional, new to
     * the player, its picks and the one picked ("" for none).
     */
    public record StepRow(String key, int goal, String title, String why, List<NeedRow> needs, Optional<BlockPos> where, boolean path,
                          boolean optional, boolean fresh, List<ChoiceRow> choices, String chosen) {
        public static final StreamCodec<ByteBuf, StepRow> STREAM_CODEC = StreamCodec.composite(
                S, StepRow::key, ByteBufCodecs.VAR_INT, StepRow::goal, S, StepRow::title, S, StepRow::why, NEEDS, StepRow::needs,
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC), StepRow::where, ByteBufCodecs.BOOL, StepRow::path, ByteBufCodecs.BOOL, StepRow::optional,
                ByteBufCodecs.BOOL, StepRow::fresh, ChoiceRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)), StepRow::choices, S, StepRow::chosen, StepRow::new);
    }

    /** Server → the player: every step, the one on top and the pinned one. Sent when it changes. */
    public record View(List<StepRow> steps, String top, String pinned) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(Awaken.id("tasks_view"));
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                StepRow.STREAM_CODEC.apply(ByteBufCodecs.list(96)), View::steps, S, View::top, S, View::pinned, View::new);

        public static final View EMPTY = new View(List.of(), "", "");

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
