package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Ascent's messages: one per state change each way, the event others see, and the dev switches. */
public final class AscentPayloads {
    private AscentPayloads() {}

    /** One-off moments others see and hear (a rope firing, a launch, the wings opening, the boom, a landing). */
    public static final int EV_FIRE = 1, EV_LAUNCH = 2, EV_DEPLOY = 3, EV_BOOM = 4, EV_LAND = 5, EV_FLAP = 6, EV_CATCH = 7,
            /** The Leaper's air moves: a double jump, an air dash, a dive slam (its landing is EV_SLAM_LAND). */
            EV_DOUBLE = 8, EV_DASH = 9, EV_DIVE = 10, EV_SLAM_LAND = 11,
            /** "Your body remembers": the scene of an old world's tiers given back (others see the wings unfold). */
            EV_REMEMBER = 12,
            /** A barrel roll in flight (speed: its direction, +1 right, -1 left). */
            EV_ROLL = 13;

    /**
     * Client → server: the local player's state changed (predicted). (x, y, z) = the rope's anchor while one is out;
     * len = the rope's length.
     */
    public record State(int state, double x, double y, double z, float len) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Awaken.id("ascent_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.state);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.len);
        }, buf -> new State(ByteBufCodecs.VAR_INT.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
        @Override public Type<State> type() { return TYPE; }
    }

    /** Client → server: a one-off moment (EV_*), at a speed, for the server to replay to everyone watching. */
    public record Event(int what, float speed) implements CustomPacketPayload {
        public static final Type<Event> TYPE = new Type<>(Awaken.id("ascent_event"));
        public static final StreamCodec<ByteBuf, Event> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Event::what, ByteBufCodecs.FLOAT, Event::speed, Event::new);
        @Override public Type<Event> type() { return TYPE; }
    }

    /** Server → watching clients: an entity's state (and its rope's anchor). */
    public record Seen(int entityId, int state, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Awaken.id("ascent_seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.entityId);
            ByteBufCodecs.VAR_INT.encode(buf, m.state);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
        }, buf -> new Seen(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readDouble(), buf.readDouble(),
                buf.readDouble()));
        @Override public Type<Seen> type() { return TYPE; }
    }

    /** Server → watching clients: someone else's one-off moment. */
    public record SeenEvent(int entityId, int what, float speed) implements CustomPacketPayload {
        public static final Type<SeenEvent> TYPE = new Type<>(Awaken.id("ascent_seen_event"));
        public static final StreamCodec<ByteBuf, SeenEvent> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SeenEvent::entityId, ByteBufCodecs.VAR_INT, SeenEvent::what, ByteBufCodecs.FLOAT, SeenEvent::speed,
                SeenEvent::new);
        @Override public Type<SeenEvent> type() { return TYPE; }
    }

    /**
     * Server → client: which tiers this player holds (a bit per {@link Tier}) and its six mastery levels, 4 bits each
     * (AscentMemory.Track order).
     */
    public record Tiers(int bits, int mastery) implements CustomPacketPayload {
        public static final Type<Tiers> TYPE = new Type<>(Awaken.id("ascent_tiers"));
        public static final StreamCodec<ByteBuf, Tiers> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Tiers::bits,
                ByteBufCodecs.VAR_INT, Tiers::mastery, Tiers::new);
        @Override public Type<Tiers> type() { return TYPE; }
    }

    /** Server → client: the server refused the state the client is in (a rope with no anchor, a tier not held): stop. */
    public record Stop(int state) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(Awaken.id("ascent_stop"));
        public static final StreamCodec<ByteBuf, Stop> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Stop::state, Stop::new);
        @Override public Type<Stop> type() { return TYPE; }
    }

    /** Server → client: play "your body remembers" for these tiers (an old world's first load, SPEC §17.6). */
    public record Remember(int bits) implements CustomPacketPayload {
        public static final Type<Remember> TYPE = new Type<>(Awaken.id("ascent_remember"));
        public static final StreamCodec<ByteBuf, Remember> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Remember::bits, Remember::new);
        @Override public Type<Remember> type() { return TYPE; }
    }

    /**
     * Server → client: the Tether Vow's stage (TetherVow.CALL, LINE, KEPT, LAPSED). (ax, ay, az): where the line takes
     * the body (LINE; NaN for none); (mx, my, mz): the beacon mast (CALL, LINE).
     */
    public record Vow(int stage, double ax, double ay, double az, double mx, double my, double mz) implements CustomPacketPayload {
        public static final Type<Vow> TYPE = new Type<>(Awaken.id("ascent_vow"));
        public static final StreamCodec<ByteBuf, Vow> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.stage);
            buf.writeDouble(m.ax);
            buf.writeDouble(m.ay);
            buf.writeDouble(m.az);
            buf.writeDouble(m.mx);
            buf.writeDouble(m.my);
            buf.writeDouble(m.mz);
        }, buf -> new Vow(ByteBufCodecs.VAR_INT.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble()));
        @Override public Type<Vow> type() { return TYPE; }
    }

    /** Server → client: a chain of moves beat the player's longest (from six links): its length. */
    public record Record(int chain) implements CustomPacketPayload {
        public static final Type<Record> TYPE = new Type<>(Awaken.id("ascent_record"));
        public static final StreamCodec<ByteBuf, Record> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Record::chain, Record::new);
        @Override public Type<Record> type() { return TYPE; }
    }

    /** Server → client (dev, autopilot): run a line of the pilot (scripted input for tours). */
    public record Pilot(String line) implements CustomPacketPayload {
        public static final Type<Pilot> TYPE = new Type<>(Awaken.id("ascent_pilot"));
        public static final StreamCodec<ByteBuf, Pilot> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Pilot::line, Pilot::new);
        @Override public Type<Pilot> type() { return TYPE; }
    }

    /** The ladder's tiers above the Runner (the Runner is everyone's from the start). */
    public enum Tier {
        TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN;

        public int bit() {
            return 1 << ordinal();
        }

        public boolean in(int bits) {
            return (bits & bit()) != 0;
        }
    }
}
