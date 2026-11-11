package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Way Up's messages (SPEC §63): the road as this player's client needs it, and a lesson that begins at a place. */
public final class WayPayloads {
    private WayPayloads() {}

    /** {@link Lesson} kinds the server starts (the others the client starts itself from the scenes it already hears). */
    public static final int FIRST_FLIGHT = 0;

    /**
     * Server → client: your road. {@code told}, {@code done}, {@code owed}: a bit a rung ({@link WayUp.Rung}); the five
     * places (packed blocks, {@link WayMemory#NONE} for none); the Gauntlet's gate while it stands; {@code woke}: the
     * rung whose place lit up just now (-1 for none), so the client shows it once; {@code moves}: which of Rope and
     * Wings' six moves have been made once (a bit each, {@link WayUp.Move}), so a move's lesson ends for good;
     * {@code leap}: which of the leap's moves may be made (a bit each, {@link WayUp.Leap}; all of them unless the leap is
     * being taken a stand at a time, SPEC §71.3) and {@code stands}: the stands taken.
     */
    public record Way(int told, int done, int owed, long[] places, long gateAt, int woke, int moves, int leap, int stands) implements CustomPacketPayload {
        public static final Type<Way> TYPE = new Type<>(Awaken.id("ascent_way"));
        public static final StreamCodec<ByteBuf, Way> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.told);
            ByteBufCodecs.VAR_INT.encode(buf, m.done);
            ByteBufCodecs.VAR_INT.encode(buf, m.owed);
            for (int i = 0; i < WayUp.Rung.VALUES.length; i++) buf.writeLong(i < m.places.length ? m.places[i] : WayMemory.NONE);
            buf.writeLong(m.gateAt);
            buf.writeByte(m.woke);
            buf.writeByte(m.moves);
            buf.writeByte(m.leap);
            buf.writeByte(m.stands);
        }, buf -> {
            int told = ByteBufCodecs.VAR_INT.decode(buf), done = ByteBufCodecs.VAR_INT.decode(buf), owed = ByteBufCodecs.VAR_INT.decode(buf);
            long[] places = new long[WayUp.Rung.VALUES.length];
            for (int i = 0; i < places.length; i++) places[i] = buf.readLong();
            return new Way(told, done, owed, places, buf.readLong(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
        });
        @Override public Type<Way> type() { return TYPE; }
    }

    /**
     * Server → client: a lesson begins at a place. {@link #FIRST_FLIGHT}: (x, y, z) is the pier's end and (dx, dz) the way
     * off it, a unit vector.
     */
    public record Lesson(int kind, double x, double y, double z, double dx, double dz) implements CustomPacketPayload {
        public static final Type<Lesson> TYPE = new Type<>(Awaken.id("ascent_lesson"));
        public static final StreamCodec<ByteBuf, Lesson> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.kind);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeDouble(m.dx);
            buf.writeDouble(m.dz);
        }, buf -> new Lesson(ByteBufCodecs.VAR_INT.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        @Override public Type<Lesson> type() { return TYPE; }
    }

    /** What a {@link Spot} marks: the gulf before the first stand, a spot that needs a move, a spot missed three times. */
    public static final int SPOT_REACH = 0, SPOT_USE = 1, SPOT_MISS = 2;

    /**
     * Server → client: a mark of the Leaper's Gauntlet's course reached (SPEC §71.4), with its two ends (packed blocks:
     * a gulf's lips, a spot's take-off and landing). {@code leap}: the move the spot needs ({@link WayUp.Leap}'s ordinal,
     * -1 for none); {@code n}: which of the hall's spots it is, from 1.
     */
    public record Spot(int kind, int leap, int n, long from, long to) implements CustomPacketPayload {
        public static final Type<Spot> TYPE = new Type<>(Awaken.id("ascent_spot"));
        public static final StreamCodec<ByteBuf, Spot> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            buf.writeByte(m.kind);
            buf.writeByte(m.leap);
            buf.writeByte(m.n);
            buf.writeLong(m.from);
            buf.writeLong(m.to);
        }, buf -> new Spot(buf.readByte(), buf.readByte(), buf.readByte(), buf.readLong(), buf.readLong()));
        @Override public Type<Spot> type() { return TYPE; }
    }

    /** Server → client: your stand's stroke has landed (§71.3): the move is yours, saved; play it and show its title. */
    public record Stroke(int leap, long pos) implements CustomPacketPayload {
        public static final Type<Stroke> TYPE = new Type<>(Awaken.id("ascent_stroke"));
        public static final StreamCodec<ByteBuf, Stroke> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            buf.writeByte(m.leap);
            buf.writeLong(m.pos);
        }, buf -> new Stroke(buf.readByte(), buf.readLong()));
        @Override public Type<Stroke> type() { return TYPE; }
    }

    /** Client → server: Use was held for a second on the stand at {@code pos}, standing on it. The server checks it all again. */
    public record Take(long pos) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(Awaken.id("ascent_take"));
        public static final StreamCodec<ByteBuf, Take> STREAM_CODEC = StreamCodec.of((buf, m) -> buf.writeLong(m.pos), buf -> new Take(buf.readLong()));
        @Override public Type<Take> type() { return TYPE; }
    }
}
