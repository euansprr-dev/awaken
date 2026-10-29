package dev.awaken.bells;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** The bells' wire (SPEC §43.7): the song as it rings, the Bell Book's page and its two asks (show it, Track a lead). */
public final class BellNet {
    private BellNet() {}

    private static final StreamCodec<ByteBuf, List<List<String>>> ROWS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)).apply(ByteBufCodecs.list(128));

    /** Server → player: a tower rings its song from {@code at}; bell #n plays if bit n of (lo, hi) is set, else a knock. */
    public record Song(BlockPos at, long lo, long hi) implements CustomPacketPayload {
        public static final Type<Song> TYPE = new Type<>(Awaken.id("bells_song"));
        public static final StreamCodec<ByteBuf, Song> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Song::at,
                ByteBufCodecs.LONG, Song::lo, ByteBufCodecs.LONG, Song::hi, Song::new);

        public boolean has(int no) {
            return no < 64 ? (lo >>> no & 1L) != 0 : (hi >>> (no - 64) & 1L) != 0;
        }

        @Override
        public Type<Song> type() {
            return TYPE;
        }
    }

    /**
     * Server → player: the Bell Book. {@code hundred}: one row a bell, [number, name or "", rank, state: held/hung/lead/""].
     * {@code leads}: [number, title, lines ("\n"-joined), how learned, trackable 1/0, tracked 1/0]. {@code tower}: first row
     * [bells hung, stage, what's next, lo, hi, frame 1/0]; then ["voice", words, count] and ["peal", name, words, state].
     */
    public record Page(List<List<String>> hundred, List<List<String>> leads, List<List<String>> tower) implements CustomPacketPayload {
        public static final Type<Page> TYPE = new Type<>(Awaken.id("bells_page"));
        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC = StreamCodec.composite(ROWS, Page::hundred, ROWS, Page::leads, ROWS, Page::tower, Page::new);

        @Override
        public Type<Page> type() {
            return TYPE;
        }
    }

    /** Player → server: the Bell Book is open; send it. */
    public record Ask() implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("bells_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.unit(new Ask());

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    /** Player → server: Track a lead (its rough bearing on the top-right card). */
    public record Track(int no) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(Awaken.id("bells_track"));
        public static final StreamCodec<ByteBuf, Track> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Track::no, Track::new);

        @Override
        public Type<Track> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Song.TYPE, Song.STREAM_CODEC);
        r.playToClient(Page.TYPE, Page.STREAM_CODEC);
        r.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) send(p);
        });
        r.playToServer(Track.TYPE, Track.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p && m.no() >= 0 && m.no() < 100) {
                Bells.track(p, m.no());
                send(p);
            }
        });
    }

    static void send(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, BellPage.build(p));
    }
}
