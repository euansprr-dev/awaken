package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The troubles on the wire (SPEC §28.7): a ring of light running out over the land (a bell rehung, someone home, a
 * village rebuilt), a trail's points for whoever is following it (the Tracks perk lights them), a festival's strings of
 * lanterns over the green, and the rooftop race's marks. Drawn by {@code village.client.TroubleFx}.
 */
public final class TroubleNet {
    private TroubleNet() {}

    /** A ring of light from a point over the land, out to {@code radius} blocks, in a colour. */
    public record Ring(float x, float y, float z, int colour, int radius) implements CustomPacketPayload {
        public static final Type<Ring> TYPE = new Type<>(Awaken.id("village_ring"));
        public static final StreamCodec<ByteBuf, Ring> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Ring::x, ByteBufCodecs.FLOAT, Ring::y,
                ByteBufCodecs.FLOAT, Ring::z, ByteBufCodecs.INT, Ring::colour, ByteBufCodecs.VAR_INT, Ring::radius, Ring::new);

        @Override
        public Type<Ring> type() {
            return TYPE;
        }
    }

    /** A trail's points (BlockPos longs; y unused), or none to forget it. */
    public record Trail(int id, long[] points) implements CustomPacketPayload {
        public static final Type<Trail> TYPE = new Type<>(Awaken.id("village_trail"));
        public static final StreamCodec<ByteBuf, Trail> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Trail::id, ByteBufCodecs.LONG_ARRAY,
                Trail::points, Trail::new);

        @Override
        public Type<Trail> type() {
            return TYPE;
        }
    }

    /**
     * A festival's lantern strings over a green: each rope as two ends (x, y, z, x, y, z) in {@code ends}; none to take
     * them down. {@code key} names the village.
     */
    public record Festoons(String key, long[] ends) implements CustomPacketPayload {
        public static final Type<Festoons> TYPE = new Type<>(Awaken.id("village_festoons"));
        public static final StreamCodec<ByteBuf, Festoons> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Festoons::key,
                ByteBufCodecs.LONG_ARRAY, Festoons::ends, Festoons::new);

        @Override
        public Type<Festoons> type() {
            return TYPE;
        }
    }

    /** The rooftop race's marks for one runner (the next one first), or none: the race is over. */
    public record Marks(long[] points, int next, int ticks) implements CustomPacketPayload {
        public static final Type<Marks> TYPE = new Type<>(Awaken.id("village_marks"));
        public static final StreamCodec<ByteBuf, Marks> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.LONG_ARRAY, Marks::points, ByteBufCodecs.VAR_INT,
                Marks::next, ByteBufCodecs.VAR_INT, Marks::ticks, Marks::new);

        @Override
        public Type<Marks> type() {
            return TYPE;
        }
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            var r = e.registrar("1");
            r.playToClient(Ring.TYPE, Ring.STREAM_CODEC);
            r.playToClient(Trail.TYPE, Trail.STREAM_CODEC);
            r.playToClient(Festoons.TYPE, Festoons.STREAM_CODEC);
            r.playToClient(Marks.TYPE, Marks.STREAM_CODEC);
        });
    }

    static void ring(ServerLevel level, Vec3 at, int colour, int radius) {
        Ring msg = new Ring((float) at.x, (float) at.y, (float) at.z, colour, radius);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(at) < 512 * 512) AwakenNet.send(p, msg);
    }

    /** Sends (or clears) a trail to a helper. */
    static void trail(ServerPlayer p, Trouble t, boolean on) {
        int n = on ? Taken.trailLength(t) : 0;
        long[] pts = new long[n];
        for (int i = 0; i < n; i++) pts[i] = t.get("t" + i, 0);
        AwakenNet.send(p, new Trail(t.id, pts));
    }

    static void festoons(ServerLevel level, String key, Vec3 at, long[] ends) {
        Festoons msg = new Festoons(key, ends);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(at) < 320 * 320) AwakenNet.send(p, msg);
    }

    static void marks(ServerPlayer p, long[] points, int next, int ticks) {
        AwakenNet.send(p, new Marks(points, next, ticks));
    }
}
