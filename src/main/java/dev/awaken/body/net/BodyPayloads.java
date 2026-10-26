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
    /** A crossing (SPEC §17.27): SEEN_VAULT + its kind (VaultRules.VAULT .. OVER), so others see the right one. */
    public static final int SEEN_VAULT = 19;
    /** A Runner move ended (its loop settles); a Runner clip started: SEEN_CLIP + its index in BodyClips.RUNNER. */
    public static final int SEEN_RUNNER_END = 31, SEEN_CLIP = 32;

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

    /**
     * Spike S3 · client → server: a Runner's Kit move started (predicted). flag = the run's link / the hang's kind /
     * the ledge jump's way; (sx, sy, sz) = the feet when it started (checked against the server's recent positions);
     * (tx, ty, tz) = the hold, target or stand spot (a run: ty = the wall's plane); axis, sign = the wall's normal axis
     * (0 x, 2 z) and which way the wall is.
     */
    public record Run(int move, int flag, float yaw, double sx, double sy, double sz, double tx, double ty, double tz, int axis, int sign)
            implements CustomPacketPayload {
        public static final Type<Run> TYPE = new Type<>(Awaken.id("body_run"));
        public static final StreamCodec<ByteBuf, Run> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.move);
            ByteBufCodecs.VAR_INT.encode(buf, m.flag);
            buf.writeFloat(m.yaw);
            buf.writeDouble(m.sx);
            buf.writeDouble(m.sy);
            buf.writeDouble(m.sz);
            buf.writeDouble(m.tx);
            buf.writeDouble(m.ty);
            buf.writeDouble(m.tz);
            buf.writeByte(m.axis);
            buf.writeByte(m.sign);
        }, buf -> new Run(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(), buf.readByte()));
        @Override public Type<Run> type() { return TYPE; }
    }

    /** Run flags: a hang's kind, a ledge jump's way, a shimmy's corner turn. */
    public static final int HANG_GRAB = 0, HANG_LOWER = 1, HANG_EAVE = 2, JUMP_UP = 0, JUMP_CLIMB = 1, JUMP_SIDE = 2, JUMP_BACK = 3, JUMP_DOWN = 4, JUMP_OVER = 5, SHIMMY_START = 0, SHIMMY_CORNER = 2,
            /** A shimmy starting to the right (as you face the wall); SHIMMY_START is to the left. */
            SHIMMY_START_RIGHT = 3;

    /**
     * Grip flags (SPEC §17.28): the first Run of a grip starts it (holding or climbing), later ones change what the
     * hands do; the last four leave the line (a kick off the wall, a swing out left or right, a landing at its foot).
     */
    public static final int GRIP_HOLD = 0, GRIP_CLIMB = 1, GRIP_CLIMB_FAST = 2, GRIP_SLIDE = 3, GRIP_HOP = 4,
            GRIP_KICK = 5, GRIP_SIDE_L = 6, GRIP_SIDE_R = 7, GRIP_LAND = 8;

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

    /**
     * Server → client (dev command): press the keys of a move as a player would, after {@code delay} ticks. what = the
     * index of a name in {@link #TRIES}.
     */
    public record Try(int what, int delay) implements CustomPacketPayload {
        public static final Type<Try> TYPE = new Type<>(Awaken.id("body_try"));
        public static final StreamCodec<ByteBuf, Try> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Try::what,
                ByteBufCodecs.VAR_INT, Try::delay, Try::new);
        @Override public Type<Try> type() { return TYPE; }
    }

    /** The dev driver's scripts, by name (the command's argument). */
    public static final String[] TRIES = {"roll", "sprint", "slide", "mantle", "slidejump", "hit", "tacslide", "release",
            // spike S3: the Runner's Kit (the bench and the S3 tour drive these)
            "wallrun", "walljump", "chain", "hang", "shimmy", "ledgeup", "climb", "ledgeside", "ledgeback", "lower", "crane", "alley",
            // the wall-run's trigger for the comparison: jump held (§16.3 as written) or a fresh press in the air; spam taps
            "runheld", "runpress", "alleyspam",
            // the one-button free-run (SPEC §17.14): forward and sprint held, nothing else
            "freerun"};
}
