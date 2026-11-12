package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.Throttle;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;
import java.util.UUID;

/**
 * The skiff's wire (SPEC §67). The server decides the flight (when it lifts, when you're moved, when it's over); the client
 * only films it. {@link Fly} goes to each traveller, {@link Seen} to anyone near the mooring it leaves from, and
 * {@link Landed} comes back when a traveller's film has set them down (it only ends their shield early).
 */
public final class FrontierNet {
    private FrontierNet() {}

    /**
     * Server → a traveller. START: the flight begins (the skiff at {@code from} lifts; {@code quick}: straight into the
     * cloud). ARRIVED: you're at {@code to} now, the land is loading under the cloud. END: the flight is over, whatever
     * the film is doing. Facings are {@code Direction.get2DDataValue}. {@code drawn}: there's no skiff built at
     * {@code from} (it came for you, SPEC §67 homecoming), so the client draws it as built and hides nothing there.
     */
    public record Fly(int kind, BlockPos from, int fromFacing, BlockPos to, int toFacing, boolean quick, String title, String line,
                      List<UUID> party, boolean drawn, int land) implements CustomPacketPayload {
        public static final int START = 0, ARRIVED = 1, END = 2;
        public static final Type<Fly> TYPE = new Type<>(Awaken.id("frontier_fly"));
        public static final StreamCodec<ByteBuf, Fly> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Fly::kind, BlockPos.STREAM_CODEC, Fly::from, ByteBufCodecs.VAR_INT, Fly::fromFacing,
                BlockPos.STREAM_CODEC, Fly::to, ByteBufCodecs.VAR_INT, Fly::toFacing, ByteBufCodecs.BOOL, Fly::quick,
                ByteBufCodecs.stringUtf8(128), Fly::title, ByteBufCodecs.stringUtf8(256), Fly::line,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Fly::party, ByteBufCodecs.BOOL, Fly::drawn, ByteBufCodecs.VAR_INT, Fly::land,
                Fly::new);

        @Override
        public Type<Fly> type() {
            return TYPE;
        }
    }

    /** Server → someone near a mooring: its skiff lifts away with {@code party} aboard, and comes back empty a while later. */
    public record Seen(BlockPos from, int facing, float bearing, boolean quick, List<UUID> party) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Awaken.id("frontier_seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Seen::from, ByteBufCodecs.VAR_INT, Seen::facing, ByteBufCodecs.FLOAT, Seen::bearing,
                ByteBufCodecs.BOOL, Seen::quick, UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Seen::party, Seen::new);

        @Override
        public Type<Seen> type() {
            return TYPE;
        }
    }

    /** Traveller → server: the film has set me down. */
    public record Landed(int unused) implements CustomPacketPayload {
        public static final Type<Landed> TYPE = new Type<>(Awaken.id("frontier_landed"));
        public static final StreamCodec<ByteBuf, Landed> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Landed::unused, Landed::new);

        @Override
        public Type<Landed> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Fly.TYPE, Fly.STREAM_CODEC);
        r.playToClient(Seen.TYPE, Seen.STREAM_CODEC);
        r.playToServer(Landed.TYPE, Landed.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p && Throttle.allow(p, "frontier_landed", 5)) Flights.landed(p);
        });
    }
}
