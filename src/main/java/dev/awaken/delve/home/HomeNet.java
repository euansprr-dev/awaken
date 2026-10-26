package dev.awaken.delve.home;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The way home's two messages (SPEC §28.5): the client asks for the thread (the line just appeared, you stood still
 * underground, or the Way Forward's glance), the server answers with the path you came by. The server keeps the trail.
 */
public final class HomeNet {
    private HomeNet() {}

    /** Crumbs ({@code BlockPos.asLong}), at most {@link HomeTrail#PATH}. */
    static final StreamCodec<ByteBuf, long[]> PATH = StreamCodec.of((buf, v) -> {
        int n = Math.min(v.length, HomeTrail.PATH);
        buf.writeByte(n);
        for (int i = 0; i < n; i++) buf.writeLong(v[i]);
    }, buf -> {
        long[] v = new long[Math.min(buf.readUnsignedByte(), HomeTrail.PATH)];
        for (int i = 0; i < v.length; i++) v[i] = buf.readLong();
        return v;
    });

    /** Client to server: run the thread home ({@code force}: the glance asked, so the 30 s rest doesn't apply). */
    public record Ask(boolean force) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("delve_home_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Ask::force, Ask::new);

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    /** Server to client: the path home, newest crumb first; {@code sky}: its last crumb is where the sky opens. */
    public record Way(long[] path, boolean sky, boolean force) implements CustomPacketPayload {
        public static final Type<Way> TYPE = new Type<>(Awaken.id("delve_home_way"));
        public static final StreamCodec<ByteBuf, Way> STREAM_CODEC = StreamCodec.composite(
                PATH, Way::path, ByteBufCodecs.BOOL, Way::sky, ByteBufCodecs.BOOL, Way::force, Way::new);

        @Override
        public Type<Way> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Way.TYPE, Way.STREAM_CODEC);
        r.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) HomeServer.ask(p, m.force());
        });
    }
}
