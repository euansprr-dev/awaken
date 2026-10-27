package dev.awaken.trails.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** What the Journal's TRAILS section shows, and what its buttons ask for (docs/trails/PLAN.md §3.4, §6). */
public final class TrailPayloads {
    private TrailPayloads() {}

    /** The trail states a row can be in. */
    public static final int OPEN = 0, FINISHED = 1, HIDDEN = 2, SILHOUETTE = 3;
    /** Step flags. */
    public static final int DONE = 1, CURRENT = 2, WHERE = 4, MANUAL = 8;
    /** The buttons' operations. */
    public static final int FOLLOW = 0, UNFOLLOW = 1, HIDE = 2, UNHIDE = 3, MARK = 4;

    private static final StreamCodec<ByteBuf, String> S = ByteBufCodecs.STRING_UTF8;

    public record StepRow(String id, String text, int flags, String hint) {
        public static final StreamCodec<ByteBuf, StepRow> STREAM_CODEC = StreamCodec.composite(
                S, StepRow::id, S, StepRow::text, ByteBufCodecs.VAR_INT, StepRow::flags, S, StepRow::hint, StepRow::new);

        public boolean done() { return (flags & DONE) != 0; }
        public boolean current() { return (flags & CURRENT) != 0; }
        public boolean where() { return (flags & WHERE) != 0; }
        public boolean manual() { return (flags & MANUAL) != 0; }
    }

    /** One trail: its state, how far (done of total), whether it is the followed one, what a silhouette waits for, and the steps of an open one. */
    public record TrailRow(String id, String title, String area, String blurb, int state, boolean followed, String need, int done, int total,
                           List<StepRow> steps) {
        private static final StreamCodec<ByteBuf, List<StepRow>> STEPS = StepRow.STREAM_CODEC.apply(ByteBufCodecs.list(32));
        public static final StreamCodec<ByteBuf, TrailRow> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public TrailRow decode(ByteBuf b) {
                String id = S.decode(b), title = S.decode(b), area = S.decode(b), blurb = S.decode(b);
                int state = ByteBufCodecs.VAR_INT.decode(b);
                boolean followed = ByteBufCodecs.BOOL.decode(b);
                String need = S.decode(b);
                int done = ByteBufCodecs.VAR_INT.decode(b), total = ByteBufCodecs.VAR_INT.decode(b);
                return new TrailRow(id, title, area, blurb, state, followed, need, done, total, STEPS.decode(b));
            }

            @Override
            public void encode(ByteBuf b, TrailRow r) {
                S.encode(b, r.id);
                S.encode(b, r.title);
                S.encode(b, r.area);
                S.encode(b, r.blurb);
                ByteBufCodecs.VAR_INT.encode(b, r.state);
                ByteBufCodecs.BOOL.encode(b, r.followed);
                S.encode(b, r.need);
                ByteBufCodecs.VAR_INT.encode(b, r.done);
                ByteBufCodecs.VAR_INT.encode(b, r.total);
                STEPS.encode(b, r.steps);
            }
        };
    }

    /** One line of "what you can do now": the trail and its next step. */
    public record NowRow(String trail, String title, String step, boolean followed) {
        public static final StreamCodec<ByteBuf, NowRow> STREAM_CODEC = StreamCodec.composite(
                S, NowRow::trail, S, NowRow::title, S, NowRow::step, ByteBufCodecs.BOOL, NowRow::followed, NowRow::new);
    }

    /** Server → the player: every trail they can see, and the best next steps. Sent when it changes. */
    public record View(List<TrailRow> trails, List<NowRow> now) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(Awaken.id("trails_view"));
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                TrailRow.STREAM_CODEC.apply(ByteBufCodecs.list(64)), View::trails,
                NowRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)), View::now, View::new);
        @Override public Type<View> type() { return TYPE; }
    }

    /** Player → server: a button. The server checks every one against what the player has opened. */
    public record Act(int op, String trail, String step) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(Awaken.id("trails_act"));
        public static final StreamCodec<ByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Act::op, S, Act::trail, S, Act::step, Act::new);
        @Override public Type<Act> type() { return TYPE; }
    }
}
