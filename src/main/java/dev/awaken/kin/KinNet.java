package dev.awaken.kin;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * The Hearth page's wire (SPEC §43.6): the page as rows of words, and its asks (show it, Track a rumour, name a beast);
 * and the kin plate's (SPEC §43.17): a read of one creature and the bond's words back.
 */
public final class KinNet {
    private KinNet() {}

    private static final StreamCodec<ByteBuf, List<List<String>>> ROWS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)).apply(ByteBufCodecs.list(64));

    /**
     * Server → player: the Hearth page. {@code home}: [text, rumour, kind to Track or "", state: head/need/met/dim/tier].
     * {@code beasts}: [kind, title, doing, feeling, made today, its id, the name you gave it or ""]. {@code kin}: [kind, title, met 1/0, where and when, its job,
     * its want so far ("\n"-joined, or "?"), standing, latest line, rumour, colour].
     */
    public record Page(List<List<String>> home, List<List<String>> beasts, List<List<String>> kin) implements CustomPacketPayload {
        public static final Type<Page> TYPE = new Type<>(Awaken.id("kin_page"));
        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC = StreamCodec.composite(ROWS, Page::home, ROWS, Page::beasts, ROWS, Page::kin, Page::new);

        @Override
        public Type<Page> type() {
            return TYPE;
        }
    }

    /** Player → server: the Hearth page is open; send it. */
    public record Ask() implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("kin_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.unit(new Ask());

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    /** Player → server: Track a kind's rumour (a rough bearing on the top-right card). */
    public record Track(String kind) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(Awaken.id("kin_track"));
        public static final StreamCodec<ByteBuf, Track> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Track::kind, Track::new);

        @Override
        public Type<Track> type() {
            return TYPE;
        }
    }

    /** Player → server: name one of your beasts (its id, the name; "" takes it away). */
    public record Name(String id, String name) implements CustomPacketPayload {
        public static final Type<Name> TYPE = new Type<>(Awaken.id("kin_name"));
        public static final StreamCodec<ByteBuf, Name> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), Name::id,
                ByteBufCodecs.stringUtf8(128), Name::name, Name::new);

        @Override
        public Type<Name> type() {
            return TYPE;
        }
    }

    /** Player → server: the kin plate is showing this creature; say what it is to me. */
    public record Read(int entity) implements CustomPacketPayload {
        public static final Type<Read> TYPE = new Type<>(Awaken.id("kin_read"));
        public static final StreamCodec<ByteBuf, Read> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Read::entity, Read::new);

        @Override
        public Type<Read> type() {
            return TYPE;
        }
    }

    /** Server → player: one creature's bond, for its plate ({@link KinPlates#read}). */
    public record Bond(int entity, String who, int tone, int step, String note) implements CustomPacketPayload {
        public static final Type<Bond> TYPE = new Type<>(Awaken.id("kin_bond"));
        public static final StreamCodec<ByteBuf, Bond> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Bond::entity,
                ByteBufCodecs.stringUtf8(128), Bond::who, ByteBufCodecs.VAR_INT, Bond::tone, ByteBufCodecs.VAR_INT, Bond::step,
                ByteBufCodecs.stringUtf8(256), Bond::note, Bond::new);

        @Override
        public Type<Bond> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("2");
        r.playToClient(Page.TYPE, Page.STREAM_CODEC);
        r.playToClient(Bond.TYPE, Bond.STREAM_CODEC);
        r.playToServer(Read.TYPE, Read.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) KinPlates.answer(p, m.entity());
        });
        r.playToServer(Name.TYPE, Name.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p && KinPlates.name(p, m.id(), m.name())) send(p);
        });
        r.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) send(p);
        });
        r.playToServer(Track.TYPE, Track.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) {
                // one pin (SPEC §27.13): the kind's step on the Tasks page when it has one, else the rough bearing as before
                String id = m.kind();
                if (!dev.awaken.core.api.Goals.pin(p, "kin/need/" + id) && !dev.awaken.core.api.Goals.pin(p, "kin/want/" + id)) KinPage.track(p, KinKind.of(id));
                send(p);
            }
        });
    }

    static void send(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, KinPage.build(p));
    }
}
