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
     * places (packed blocks, {@link WayMemory#NONE} for none); the Gauntlet's gate while it stands; and {@code woke}: the
     * rung whose place lit up just now (-1 for none), so the client shows it once.
     */
    public record Way(int told, int done, int owed, long[] places, long gateAt, int woke) implements CustomPacketPayload {
        public static final Type<Way> TYPE = new Type<>(Awaken.id("ascent_way"));
        public static final StreamCodec<ByteBuf, Way> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.told);
            ByteBufCodecs.VAR_INT.encode(buf, m.done);
            ByteBufCodecs.VAR_INT.encode(buf, m.owed);
            for (int i = 0; i < WayUp.Rung.VALUES.length; i++) buf.writeLong(i < m.places.length ? m.places[i] : WayMemory.NONE);
            buf.writeLong(m.gateAt);
            buf.writeByte(m.woke);
        }, buf -> {
            int told = ByteBufCodecs.VAR_INT.decode(buf), done = ByteBufCodecs.VAR_INT.decode(buf), owed = ByteBufCodecs.VAR_INT.decode(buf);
            long[] places = new long[WayUp.Rung.VALUES.length];
            for (int i = 0; i < places.length; i++) places[i] = buf.readLong();
            return new Way(told, done, owed, places, buf.readLong(), buf.readByte());
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
}
