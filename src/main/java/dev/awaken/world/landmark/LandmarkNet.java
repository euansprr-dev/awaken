package dev.awaken.world.landmark;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** The landmarks' one payload: the markers near a player (client: {@code world.landmark.client.LandmarkClient}). */
public final class LandmarkNet {
    private LandmarkNet() {}

    /** At most this many markers go to one player at a time. */
    public static final int MAX = 16;

    /** One landmark to mark: kind = {@link LandmarkKind} ordinal, x/z its centre, y its lowest block, height its span. */
    public record Marker(int kind, int x, int y, int z, int height) {
        public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Marker::kind,
                ByteBufCodecs.INT, Marker::x,
                ByteBufCodecs.INT, Marker::y,
                ByteBufCodecs.INT, Marker::z,
                ByteBufCodecs.VAR_INT, Marker::height,
                Marker::new);

        public LandmarkKind landmark() {
            return LandmarkKind.of(kind);
        }
    }

    /** The full set of markers near the player (replaces the previous set; empty clears them). */
    public record Markers(List<Marker> markers) implements CustomPacketPayload {
        public static final Type<Markers> TYPE = new Type<>(Awaken.id("landmark_markers"));
        public static final StreamCodec<ByteBuf, Markers> STREAM_CODEC = Marker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX))
                .map(Markers::new, Markers::markers);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(Markers.TYPE, Markers.STREAM_CODEC);
    }
}
