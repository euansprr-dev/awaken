package dev.awaken.delve.cave;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * The cave places' messages (SPEC §28.5): the place you are in (for the senses and its caption), the places near you (a
 * sinkhole's mist, a chute's draught), the Delvers' logs you've read, and what a lode map showed. Client handlers are
 * registered by {@code delve.client.CaveClient}.
 */
public final class CaveNet {
    private CaveNet() {}

    /**
     * The place you are in now: its kind ({@link CaveKind} ordinal, -1 for none), name, how strongly a seep presses here
     * (0..1), whether its seep has gone quiet, and whether to caption it (the first time in a play-day).
     */
    public record Now(int kind, String name, float seep, boolean quiet, boolean caption) implements CustomPacketPayload {
        public static final Type<Now> TYPE = new Type<>(Awaken.id("delve_cave_now"));
        public static final StreamCodec<ByteBuf, Now> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Now::kind, ByteBufCodecs.STRING_UTF8, Now::name, ByteBufCodecs.FLOAT, Now::seep,
                ByteBufCodecs.BOOL, Now::quiet, ByteBufCodecs.BOOL, Now::caption, Now::new);

        @Override
        public Type<Now> type() {
            return TYPE;
        }
    }

    /** One place near you, for its look from outside: kind, its anchor (a sinkhole's centre at the rim, a chute's mouth), size. */
    public record Site(int kind, BlockPos at, int radius, int depth) {
        static final StreamCodec<ByteBuf, Site> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Site::kind, BlockPos.STREAM_CODEC, Site::at, ByteBufCodecs.VAR_INT, Site::radius, ByteBufCodecs.VAR_INT, Site::depth, Site::new);
    }

    public record Near(List<Site> sites) implements CustomPacketPayload {
        public static final Type<Near> TYPE = new Type<>(Awaken.id("delve_cave_near"));
        public static final StreamCodec<ByteBuf, Near> STREAM_CODEC = StreamCodec.composite(
                Site.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Near::sites, Near::new);

        @Override
        public Type<Near> type() {
            return TYPE;
        }
    }

    /** The Delvers' logs this player has read (bits). */
    public record Logs(int read) implements CustomPacketPayload {
        public static final Type<Logs> TYPE = new Type<>(Awaken.id("delve_logs"));
        public static final StreamCodec<ByteBuf, Logs> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Logs::read, Logs::new);

        @Override
        public Type<Logs> type() {
            return TYPE;
        }
    }

    /** One lode a map showed: its ore and heart. */
    public record Mark(int ore, BlockPos pos) {
        static final StreamCodec<ByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Mark::ore, BlockPos.STREAM_CODEC, Mark::pos, Mark::new);
    }

    public record LodeMarks(List<Mark> marks) implements CustomPacketPayload {
        public static final Type<LodeMarks> TYPE = new Type<>(Awaken.id("delve_lode_marks"));
        public static final StreamCodec<ByteBuf, LodeMarks> STREAM_CODEC = StreamCodec.composite(
                Mark.STREAM_CODEC.apply(ByteBufCodecs.list(8)), LodeMarks::marks, LodeMarks::new);

        @Override
        public Type<LodeMarks> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Now.TYPE, Now.STREAM_CODEC);
        r.playToClient(Near.TYPE, Near.STREAM_CODEC);
        r.playToClient(Logs.TYPE, Logs.STREAM_CODEC);
        r.playToClient(LodeMarks.TYPE, LodeMarks.STREAM_CODEC);
    }
}
