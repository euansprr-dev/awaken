package dev.awaken.enchant.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Inscribing Table's messages (SPEC §45.4). The server opens a session when you lay a piece on the slate; the client
 * says what you chose and how each stroke went; the server takes the price, writes the enchantment onto the same piece
 * and says how it went. Everything the book shows is worked out on the client from the synced knowledge and the common
 * rules; the server checks it all again.
 */
public final class EnchantPayloads {
    private EnchantPayloads() {}

    /** Server → the writer: a session at this table for the piece in hand ({@code slot}); whether the table stands in a Study. */
    public record Open(BlockPos pos, int session, int slot, boolean study, int facing) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Awaken.id("enchant_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos, ByteBufCodecs.VAR_INT, Open::session, ByteBufCodecs.VAR_INT, Open::slot,
                ByteBufCodecs.BOOL, Open::study, ByteBufCodecs.VAR_INT, Open::facing, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** What an {@link Act} does. */
    public static final int CHOOSE = 0, MEMORY = 1, STROKE = 2, FINISH = 3, CANCEL = 4, SCRAPE = 5, LOOK = 6;

    /**
     * Client → server. CHOOSE / MEMORY: write enchantment {@code no} (its Journal number) at {@code level}, traced or from
     * memory; STROKE: stroke {@code level} done, {@code a} of it on the line (0-1); FINISH: the last stroke lifted (or
     * memory's two seconds ran); CANCEL: you stepped back (Esc); SCRAPE: take enchantment {@code no} off; LOOK: the book
     * turned to {@code no} (others see the floating book turn).
     */
    public record Act(int session, int act, int no, int level, float a) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(Awaken.id("enchant_act"));
        public static final StreamCodec<ByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Act::session, ByteBufCodecs.VAR_INT, Act::act, ByteBufCodecs.VAR_INT, Act::no,
                ByteBufCodecs.VAR_INT, Act::level, ByteBufCodecs.FLOAT, Act::a, Act::new);
        @Override public Type<Act> type() { return TYPE; }
    }

    /** {@link State#phase}: back at the book; tracing; writing from memory. */
    public static final int BOOK = 0, TRACE = 1, FROM_MEMORY = 2;

    /** Server → the writer: the session's phase after a choice or a scrape, and a line to show when one was refused. */
    public record State(int session, int phase, int no, int level, String line) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Awaken.id("enchant_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::session, ByteBufCodecs.VAR_INT, State::phase, ByteBufCodecs.VAR_INT, State::no,
                ByteBufCodecs.VAR_INT, State::level, ByteBufCodecs.STRING_UTF8, State::line, State::new);
        @Override public Type<State> type() { return TYPE; }
    }

    /** Server → the writer: it's written. Levels given back for a clean hand, and whether its mark went into memory. */
    public record Done(int session, int no, int level, int refund, boolean clean, boolean remembered, String card) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Awaken.id("enchant_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Done::session, ByteBufCodecs.VAR_INT, Done::no, ByteBufCodecs.VAR_INT, Done::level,
                ByteBufCodecs.VAR_INT, Done::refund, ByteBufCodecs.BOOL, Done::clean, ByteBufCodecs.BOOL, Done::remembered,
                ByteBufCodecs.STRING_UTF8, Done::card, Done::new);
        @Override public Type<Done> type() { return TYPE; }
    }

    /** Server → the writer: the session ended without writing (the piece changed, the table went, you walked away). */
    public record Closed(int session, String line) implements CustomPacketPayload {
        public static final Type<Closed> TYPE = new Type<>(Awaken.id("enchant_closed"));
        public static final StreamCodec<ByteBuf, Closed> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Closed::session, ByteBufCodecs.STRING_UTF8, Closed::line, Closed::new);
        @Override public Type<Closed> type() { return TYPE; }
    }

    /**
     * {@link Fx#kind}: a ward took some of a blow; a bane bit; an extra drop; a page learned; the old table turned; a block
     * came away whole (Silk Touch); an orb mended a piece; a fall softened (Feather Falling); and a swimmer's wake (Depth
     * Strider), which the client makes itself and is never sent.
     */
    public static final int FX_WARD = 0, FX_BANE = 1, FX_SPOILS = 2, FX_LEARN = 3, FX_FLARE = 4, FX_SILK = 5, FX_MEND = 6,
            FX_PUFF = 7, FX_WAKE = 8;

    /**
     * Server → everyone near: an enchantment at work, in its colour, at an entity (or a place: a block's centre, an orb).
     * {@code no} the enchantment's number whose mark it shows (0 none; for a mend, the slot mended), {@code dir} the
     * angle on the ground toward where a blow came from (NaN when it came from nowhere in particular).
     */
    public record Fx(int kind, int entity, double x, double y, double z, int colour, float strength, int no, float dir) implements CustomPacketPayload {
        public Fx(int kind, int entity, double x, double y, double z, int colour, float strength) {
            this(kind, entity, x, y, z, colour, strength, 0, Float.NaN);
        }
        public static final Type<Fx> TYPE = new Type<>(Awaken.id("enchant_fx"));
        public static final StreamCodec<ByteBuf, Fx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Fx::kind, ByteBufCodecs.VAR_INT, Fx::entity, ByteBufCodecs.DOUBLE, Fx::x,
                ByteBufCodecs.DOUBLE, Fx::y, ByteBufCodecs.DOUBLE, Fx::z, ByteBufCodecs.INT, Fx::colour,
                ByteBufCodecs.FLOAT, Fx::strength, ByteBufCodecs.VAR_INT, Fx::no, ByteBufCodecs.FLOAT, Fx::dir, Fx::new);
        @Override public Type<Fx> type() { return TYPE; }
    }
}
