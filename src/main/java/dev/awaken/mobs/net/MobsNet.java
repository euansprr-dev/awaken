package dev.awaken.mobs.net;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/** The mobs module's payloads (server to client; handled in {@code mobs.client}). */
public final class MobsNet {
    private MobsNet() {}

    /**
     * The server's clock while a boss fights near you (SPEC §47.1): sent at the end of each of its ticks, so the
     * client plays boss moves at the server's real pace. In a stall the boss holds instead of running ahead of its blows.
     */
    public record Pace(long gameTime) implements CustomPacketPayload {
        public static final Type<Pace> TYPE = new Type<>(Awaken.id("mobs_pace"));
        public static final StreamCodec<ByteBuf, Pace> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(Pace::new, Pace::gameTime);

        @Override
        public Type<Pace> type() {
            return TYPE;
        }
    }

    /**
     * A carrier (SPEC §47.2): a wave, a volley, a ground line, a locked line, a landing ring, a field or a pull, sent
     * once as it begins; both sides run it on the server's clock from {@code start} for {@code life} ticks. Kind,
     * class and the rest as {@code mobs.entity.Carriers} names them.
     */
    public record Carrier(int id, int kind, int cls, int theme, double x, double y, double z, float dx, float dz, float from, float to,
                          float speed, float width, long start, int life, List<Vec3> points) implements CustomPacketPayload {
        public static final Type<Carrier> TYPE = new Type<>(Awaken.id("mobs_carrier"));
        public static final StreamCodec<ByteBuf, Carrier> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            ByteBufCodecs.VAR_INT.encode(buf, c.id);
            buf.writeByte(c.kind);
            buf.writeByte(c.cls);
            buf.writeInt(c.theme);
            buf.writeDouble(c.x);
            buf.writeDouble(c.y);
            buf.writeDouble(c.z);
            buf.writeFloat(c.dx);
            buf.writeFloat(c.dz);
            buf.writeFloat(c.from);
            buf.writeFloat(c.to);
            buf.writeFloat(c.speed);
            buf.writeFloat(c.width);
            ByteBufCodecs.VAR_LONG.encode(buf, c.start);
            ByteBufCodecs.VAR_INT.encode(buf, c.life);
            int n = Math.min(8, c.points.size());
            buf.writeByte(n);
            for (int i = 0; i < n; i++) Vec3.STREAM_CODEC.encode(buf, c.points.get(i));
        }, buf -> {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            int kind = buf.readByte(), cls = buf.readByte(), theme = buf.readInt();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float dx = buf.readFloat(), dz = buf.readFloat(), from = buf.readFloat(), to = buf.readFloat(), speed = buf.readFloat(), width = buf.readFloat();
            long start = ByteBufCodecs.VAR_LONG.decode(buf);
            int life = ByteBufCodecs.VAR_INT.decode(buf);
            int n = Math.min(8, buf.readByte());
            List<Vec3> pts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) pts.add(Vec3.STREAM_CODEC.decode(buf));
            return new Carrier(id, kind, cls, theme, x, y, z, dx, dz, from, to, speed, width, start, life, pts);
        });

        @Override
        public Type<Carrier> type() {
            return TYPE;
        }
    }

    /** A shown carrier ended early (a leap landed, its maker died): the client drops it. */
    public record CarrierEnd(int id) implements CustomPacketPayload {
        public static final Type<CarrierEnd> TYPE = new Type<>(Awaken.id("mobs_carrier_end"));
        public static final StreamCodec<ByteBuf, CarrierEnd> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(CarrierEnd::new, CarrierEnd::id);

        @Override
        public Type<CarrierEnd> type() {
            return TYPE;
        }
    }

    /** A beast's told bite (SPEC §47.2): its class, and the ticks until it lands (0: it was called off). */
    public record Tell(int id, int kind, int ticks) implements CustomPacketPayload {
        public static final Type<Tell> TYPE = new Type<>(Awaken.id("mobs_tell"));
        public static final StreamCodec<ByteBuf, Tell> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tell::id, ByteBufCodecs.VAR_INT, Tell::kind, ByteBufCodecs.VAR_INT, Tell::ticks, Tell::new);

        @Override
        public Type<Tell> type() {
            return TYPE;
        }
    }

    /**
     * A boss's finishing blow has landed (SPEC §68.4): which body fell, who struck it, and how many ticks its own ending
     * plays before it is gone. Sent once, as it dies, to everyone near; only the striker's view is taken.
     */
    public record Finish(int boss, int striker, int ticks) implements CustomPacketPayload {
        public static final Type<Finish> TYPE = new Type<>(Awaken.id("mobs_finish"));
        public static final StreamCodec<ByteBuf, Finish> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Finish::boss, ByteBufCodecs.VAR_INT, Finish::striker, ByteBufCodecs.VAR_INT, Finish::ticks, Finish::new);

        @Override
        public Type<Finish> type() {
            return TYPE;
        }
    }

    /** Those within this of a boss as it falls see its ending. */
    public static final double FINISH_RANGE = 64;

    public static void finish(Entity boss, ServerPlayer striker, int ticks) {
        Finish msg = new Finish(boss.getId(), striker.getId(), ticks);
        for (ServerPlayer p : ((ServerLevel) boss.level()).players()) {
            if (p.distanceToSqr(boss) <= FINISH_RANGE * FINISH_RANGE) AwakenNet.send(p, msg);
        }
    }

    /** Players this far from a fighting boss get its pace. */
    public static final double PACE_RANGE = 96;

    // the bosses that fought this tick (at most a handful)
    private static final List<Entity> FIGHTING = new ArrayList<>();

    public static void init(IEventBus modBus) {
        modBus.addListener(MobsNet::register);
        NeoForge.EVENT_BUS.addListener(MobsNet::onPost);
    }

    private static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Pace.TYPE, Pace.STREAM_CODEC);
        r.playToClient(Carrier.TYPE, Carrier.STREAM_CODEC);
        r.playToClient(CarrierEnd.TYPE, CarrierEnd.STREAM_CODEC);
        r.playToClient(Tell.TYPE, Tell.STREAM_CODEC);
        r.playToClient(Finish.TYPE, Finish.STREAM_CODEC);
    }

    /** A boss with a fight ticked: players near it get the server's clock at the tick's end. */
    public static void fighting(Entity boss) {
        if (FIGHTING.size() < 16 && !FIGHTING.contains(boss) && boss.level().getNearestPlayer(boss, PACE_RANGE) != null) FIGHTING.add(boss);
    }

    private static void onPost(ServerTickEvent.Post e) {
        if (FIGHTING.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (Entity b : FIGHTING) {
                if (b.level() != p.level() || b.distanceToSqr(p) > PACE_RANGE * PACE_RANGE) continue;
                AwakenNet.send(p, new Pace(((ServerLevel) p.level()).getGameTime()));
                break;
            }
        }
        FIGHTING.clear();
    }
}
