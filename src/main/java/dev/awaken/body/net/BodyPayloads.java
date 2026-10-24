package dev.awaken.body.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Network messages of the Awakened Body: one per move start, its end, a refusal, what others see, the dev driver. */
public final class BodyPayloads {
    private BodyPayloads() {}

    /** What a {@link Seen} shows besides the moves themselves (their codes are the {@code Move} ordinals). */
    public static final int SEEN_TRIP = 16, SEEN_MOMENTUM = 17, SEEN_SLIDE_JUMP = 18;

    /**
     * Client → server: a move started (predicted). yaw = the facing (mantle) or the slide's way; (x, y, z) = where a
     * mantle puts the feet; speed = a slide's entry speed; flag = the slide came out of a tactical sprint.
     */
    public record Start(int move, float yaw, double x, double y, double z, float speed, boolean flag) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(Awaken.id("body_start"));
        public static final StreamCodec<ByteBuf, Start> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.move);
            buf.writeFloat(m.yaw);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.speed);
            buf.writeBoolean(m.flag);
        }, buf -> new Start(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readBoolean()));
        @Override public Type<Start> type() { return TYPE; }
    }

    /** Client → server: a move ended early on the client (a wall, a jump out, a stop, a swing). */
    public record End(int move) implements CustomPacketPayload {
        public static final Type<End> TYPE = new Type<>(Awaken.id("body_end"));
        public static final StreamCodec<ByteBuf, End> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, End::move, End::new);
        @Override public Type<End> type() { return TYPE; }
    }

    /** Server → client: the server refused (or ended) a move the client is running: stop it. */
    public record Refuse(int move) implements CustomPacketPayload {
        public static final Type<Refuse> TYPE = new Type<>(Awaken.id("body_refuse"));
        public static final StreamCodec<ByteBuf, Refuse> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Refuse::move, Refuse::new);
        @Override public Type<Refuse> type() { return TYPE; }
    }

    /** Server → watching clients: an entity did a move (or was tripped, or took a momentum hit). yaw = its way. */
    public record Seen(int entityId, int what, float yaw) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Awaken.id("body_seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Seen::entityId, ByteBufCodecs.VAR_INT, Seen::what, ByteBufCodecs.FLOAT, Seen::yaw, Seen::new);
        @Override public Type<Seen> type() { return TYPE; }
    }

    /** Server → client (dev command): press the keys of a move as a player would. what = a {@code Try} name's index. */
    public record Try(int what) implements CustomPacketPayload {
        public static final Type<Try> TYPE = new Type<>(Awaken.id("body_try"));
        public static final StreamCodec<ByteBuf, Try> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Try::what, Try::new);
        @Override public Type<Try> type() { return TYPE; }
    }

    /** The dev driver's scripts, by name (the command's argument). */
    public static final String[] TRIES = {"roll", "sprint", "slide", "mantle", "slidejump", "hit", "tacslide", "release"};
}
