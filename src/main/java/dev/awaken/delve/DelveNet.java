package dev.awaken.delve;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * The deep's messages (SPEC §28.3). Everything the player sees and hears of a lode, a cave-in, a heavy strike or the
 * Tuning Fork is built on the client from these; the server decides every block.
 */
public final class DelveNet {
    private DelveNet() {}

    /** Block positions ({@code BlockPos.asLong}), at most 512. */
    static final StreamCodec<ByteBuf, long[]> LONGS = StreamCodec.of((buf, v) -> {
        int n = Math.min(v.length, 512);
        buf.writeShort(n);
        for (int i = 0; i < n; i++) buf.writeLong(v[i]);
    }, buf -> {
        int n = buf.readUnsignedShort();
        long[] v = new long[Math.min(n, 512)];
        for (int i = 0; i < v.length; i++) v[i] = buf.readLong();
        return v;
    });

    /**
     * A lode's heart broke ("the vein gives"): its blocks still there, the heart, and for how long the breaker's blows
     * take them in two ({@code yours}: this player broke it; onlookers see the light, not the window).
     */
    public record VeinOpen(long lode, int ore, long heart, long[] cells, int ticks, boolean yours) implements CustomPacketPayload {
        public static final Type<VeinOpen> TYPE = new Type<>(Awaken.id("delve_vein_open"));
        public static final StreamCodec<ByteBuf, VeinOpen> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, VeinOpen::lode,
                ByteBufCodecs.VAR_INT, VeinOpen::ore,
                ByteBufCodecs.LONG, VeinOpen::heart,
                LONGS, VeinOpen::cells,
                ByteBufCodecs.VAR_INT, VeinOpen::ticks,
                ByteBufCodecs.BOOL, VeinOpen::yours,
                VeinOpen::new);

        @Override
        public Type<VeinOpen> type() {
            return TYPE;
        }
    }

    /** One lode near the player, for the glints and Vein Sight. */
    public record Near(long id, int ore, long[] cells) {
        static final StreamCodec<ByteBuf, Near> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Near::id, ByteBufCodecs.VAR_INT, Near::ore, LONGS, Near::cells, Near::new);
    }

    /** The lodes within reach of the player (sent when the set changes). */
    public record LodesNear(List<Near> lodes) implements CustomPacketPayload {
        public static final Type<LodesNear> TYPE = new Type<>(Awaken.id("delve_lodes_near"));
        public static final StreamCodec<ByteBuf, LodesNear> STREAM_CODEC = StreamCodec.composite(
                Near.STREAM_CODEC.apply(ByteBufCodecs.list(16)), LodesNear::lodes, LodesNear::new);

        @Override
        public Type<LodesNear> type() {
            return TYPE;
        }
    }

    /** A roof is about to come down: the blocks that will fall (nearest first), the warning's length, and how long they take to let go. */
    public record CaveWarn(long centre, long[] roof, int ticks, int spread) implements CustomPacketPayload {
        public static final Type<CaveWarn> TYPE = new Type<>(Awaken.id("delve_cave_warn"));
        public static final StreamCodec<ByteBuf, CaveWarn> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.LONG, CaveWarn::centre, LONGS, CaveWarn::roof, ByteBufCodecs.VAR_INT, CaveWarn::ticks,
                ByteBufCodecs.VAR_INT, CaveWarn::spread, CaveWarn::new);

        @Override
        public Type<CaveWarn> type() {
            return TYPE;
        }
    }

    /** A heavy strike landed: who swung, the blocks it bored (their states' ids, for the dust's colour), on the glint or not. */
    public record HeavyFx(int player, long[] bored, List<Integer> states, boolean perfect) implements CustomPacketPayload {
        public static final Type<HeavyFx> TYPE = new Type<>(Awaken.id("delve_heavy_fx"));
        public static final StreamCodec<ByteBuf, HeavyFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HeavyFx::player, LONGS, HeavyFx::bored, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), HeavyFx::states,
                ByteBufCodecs.BOOL, HeavyFx::perfect, HeavyFx::new);

        @Override
        public Type<HeavyFx> type() {
            return TYPE;
        }
    }

    /** The Tuning Fork rang: the ore it was tuned to and, when a lode of it lies within 48, the block to sing toward. */
    public record ForkRing(int ore, boolean found, long target) implements CustomPacketPayload {
        public static final Type<ForkRing> TYPE = new Type<>(Awaken.id("delve_fork_ring"));
        public static final StreamCodec<ByteBuf, ForkRing> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ForkRing::ore, ByteBufCodecs.BOOL, ForkRing::found, ByteBufCodecs.LONG, ForkRing::target, ForkRing::new);

        @Override
        public Type<ForkRing> type() {
            return TYPE;
        }
    }

    /** Show the strike's feel on a block as if mined ({@code kind} 0: blows then the last; 1: a heavy charge to its glint): tours. */
    public record Demo(int kind, long pos, int face) implements CustomPacketPayload {
        public static final int BLOWS = 0, CHARGE = 1;
        public static final Type<Demo> TYPE = new Type<>(Awaken.id("delve_demo"));
        public static final StreamCodec<ByteBuf, Demo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Demo::kind, ByteBufCodecs.LONG, Demo::pos, ByteBufCodecs.VAR_INT, Demo::face, Demo::new);

        @Override
        public Type<Demo> type() {
            return TYPE;
        }
    }

    /** The client let go of a charged strike with a pick or mattock ({@code charge}: ticks held; on the glint or not). */
    public record HeavyStrike(int charge, boolean perfect) implements CustomPacketPayload {
        public static final Type<HeavyStrike> TYPE = new Type<>(Awaken.id("delve_heavy_strike"));
        public static final StreamCodec<ByteBuf, HeavyStrike> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HeavyStrike::charge, ByteBufCodecs.BOOL, HeavyStrike::perfect, HeavyStrike::new);

        @Override
        public Type<HeavyStrike> type() {
            return TYPE;
        }
    }

    /** To one player, if their connection speaks our channel (fake players in tests don't). */
    static void send(ServerPlayer p, CustomPacketPayload m) {
        dev.awaken.core.AwakenNet.send(p, m);
    }

    /** To every player within {@code radius} of (x, y, z) in this level but {@code except}. */
    static void near(net.minecraft.server.level.ServerLevel level, ServerPlayer except, double x, double y, double z, double radius, CustomPacketPayload m) {
        for (ServerPlayer p : level.players()) {
            if (p != except && p.distanceToSqr(x, y, z) <= radius * radius) send(p, m);
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(VeinOpen.TYPE, VeinOpen.STREAM_CODEC);
        r.playToClient(LodesNear.TYPE, LodesNear.STREAM_CODEC);
        r.playToClient(CaveWarn.TYPE, CaveWarn.STREAM_CODEC);
        r.playToClient(HeavyFx.TYPE, HeavyFx.STREAM_CODEC);
        r.playToClient(ForkRing.TYPE, ForkRing.STREAM_CODEC);
        r.playToClient(Demo.TYPE, Demo.STREAM_CODEC);
        r.playToServer(HeavyStrike.TYPE, HeavyStrike.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) HeavyStrikes.release(p, m.charge(), m.perfect());
        });
    }
}
