package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.EconomyHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/** The notice board on the wire: the board's notices to the player (it opens their screen), and a "hand over" back. */
public final class VillageNet {
    private VillageNet() {}

    /** One notice: its id, the item wanted (registry id), the words, the count wanted and brought, the pay, the standing. */
    public record Note(String id, String item, String text, int need, int have, int pay, int standing) {
        public static final StreamCodec<ByteBuf, Note> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Note decode(ByteBuf b) {
                return new Note(ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                        ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b));
            }

            @Override
            public void encode(ByteBuf b, Note n) {
                ByteBufCodecs.STRING_UTF8.encode(b, n.id());
                ByteBufCodecs.STRING_UTF8.encode(b, n.item());
                ByteBufCodecs.STRING_UTF8.encode(b, n.text());
                ByteBufCodecs.VAR_INT.encode(b, n.need());
                ByteBufCodecs.VAR_INT.encode(b, n.have());
                ByteBufCodecs.VAR_INT.encode(b, n.pay());
                ByteBufCodecs.VAR_INT.encode(b, n.standing());
            }
        };
    }

    /** The board, opened: which settlement, its name, how it knows you, your emeralds, its notices. */
    public record Board(String key, String village, String standing, int emeralds, List<Note> notes) implements CustomPacketPayload {
        public static final Type<Board> TYPE = new Type<>(Awaken.id("village_board"));
        public static final StreamCodec<ByteBuf, Board> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Board::key, ByteBufCodecs.STRING_UTF8, Board::village, ByteBufCodecs.STRING_UTF8, Board::standing,
                ByteBufCodecs.VAR_INT, Board::emeralds, Note.STREAM_CODEC.apply(ByteBufCodecs.list(8)), Board::notes, Board::new);

        @Override
        public Type<Board> type() {
            return TYPE;
        }
    }

    /** "Hand over" pressed on a notice. */
    public record Deliver(String key, String id) implements CustomPacketPayload {
        public static final Type<Deliver> TYPE = new Type<>(Awaken.id("village_deliver"));
        public static final StreamCodec<ByteBuf, Deliver> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Deliver::key, ByteBufCodecs.STRING_UTF8, Deliver::id, Deliver::new);

        @Override
        public Type<Deliver> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Board.TYPE, Board.STREAM_CODEC);
        r.playToServer(Deliver.TYPE, Deliver.STREAM_CODEC, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            VillageService.Live l = VillageService.LIVE.get(msg.key());
            if (l == null || Math.hypot(sp.getX() - l.plan().ox, sp.getZ() - l.plan().oz) > l.plan().radius() + 24) return;
            Requests.deliver(sp, l, msg.id());
        });
    }

    /** Opens (or refreshes) a player's board for a settlement. */
    static void openBoard(ServerPlayer p, VillageService.Live l) {
        List<Note> notes = new ArrayList<>();
        for (VillageState.Request q : Requests.board(p.level().getServer(), l))
            notes.add(new Note(q.id, q.item, q.text, q.need, q.have, q.pay, q.standing));
        AwakenNet.send(p, new Board(l.key(), l.plan().name, VillageService.standingName(p, l), (int) Math.min(Integer.MAX_VALUE, EconomyHooks.balance(p)),
                notes));
    }
}
