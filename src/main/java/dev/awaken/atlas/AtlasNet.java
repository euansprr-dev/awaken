package dev.awaken.atlas;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** The Atlas on the wire: explored tiles to their owner, the run's trail, and a request to share with the party. */
public final class AtlasNet {
    private AtlasNet() {}

    static final StreamCodec<ByteBuf, Explored.TileBits> TILE = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Explored.TileBits::tx,
            ByteBufCodecs.VAR_INT, Explored.TileBits::tz,
            ByteBufCodecs.LONG_ARRAY, Explored.TileBits::bits,
            Explored.TileBits::new);

    /**
     * Explored tiles in {@code dim}, whole (16 longs each). {@code reset}: forget every other tile of that dimension
     * first (login, a new dimension). {@code ripple}: the reveal spreads from (ox, oz), a bell's ring.
     */
    public record Sync(String dim, boolean reset, List<Explored.TileBits> tiles, boolean ripple, int ox, int oz) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Awaken.id("atlas_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Sync::dim,
                ByteBufCodecs.BOOL, Sync::reset,
                TILE.apply(ByteBufCodecs.list(4096)), Sync::tiles,
                ByteBufCodecs.BOOL, Sync::ripple,
                ByteBufCodecs.VAR_INT, Sync::ox,
                ByteBufCodecs.VAR_INT, Sync::oz,
                Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** The opening run's path (x, y, z per point); {@code play}: the opening just ended, show the Registry reveal. */
    public record Trail(List<Integer> points, boolean play) implements CustomPacketPayload {
        public static final Type<Trail> TYPE = new Type<>(Awaken.id("atlas_trail"));
        public static final StreamCodec<ByteBuf, Trail> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(AtlasData.TRAIL_MAX * 3)), Trail::points,
                ByteBufCodecs.BOOL, Trail::play,
                Trail::new);

        @Override
        public Type<Trail> type() {
            return TYPE;
        }
    }

    static final StreamCodec<ByteBuf, AtlasData.Region> REGION = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, AtlasData.Region::id,
            ByteBufCodecs.stringUtf8(64), AtlasData.Region::name,
            ByteBufCodecs.VAR_INT, AtlasData.Region::heartX,
            ByteBufCodecs.VAR_INT, AtlasData.Region::heartZ,
            ByteBufCodecs.VAR_INT, AtlasData.Region::low,
            ByteBufCodecs.VAR_INT, AtlasData.Region::high,
            AtlasData.Region::new);

    /** Every region the player has been in (the overworld's), whole. */
    public record Regions(List<AtlasData.Region> regions) implements CustomPacketPayload {
        public static final Type<Regions> TYPE = new Type<>(Awaken.id("atlas_regions"));
        public static final StreamCodec<ByteBuf, Regions> STREAM_CODEC = StreamCodec.composite(
                REGION.apply(ByteBufCodecs.list(1024)), Regions::regions, Regions::new);

        @Override
        public Type<Regions> type() {
            return TYPE;
        }
    }

    /** One pin as a party member sees it: whose, its colour, where, and whether it's the viewer's own. */
    public record PinView(String owner, int colour, int x, int y, int z, boolean mine) {
        static final StreamCodec<ByteBuf, PinView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), PinView::owner,
                ByteBufCodecs.INT, PinView::colour,
                ByteBufCodecs.VAR_INT, PinView::x,
                ByteBufCodecs.VAR_INT, PinView::y,
                ByteBufCodecs.VAR_INT, PinView::z,
                ByteBufCodecs.BOOL, PinView::mine,
                PinView::new);
    }

    /** The pins of your party (yours included) in your dimension, whole. */
    public record Pins(List<PinView> pins) implements CustomPacketPayload {
        public static final Type<Pins> TYPE = new Type<>(Awaken.id("atlas_pins"));
        public static final StreamCodec<ByteBuf, Pins> STREAM_CODEC = StreamCodec.composite(
                PinView.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Pins::pins, Pins::new);

        @Override
        public Type<Pins> type() {
            return TYPE;
        }
    }

    /** Drop a pin at (x, y, z), or with {@code remove}, take back your own pin nearest it. */
    public record Pin(int x, int y, int z, boolean remove) implements CustomPacketPayload {
        public static final Type<Pin> TYPE = new Type<>(Awaken.id("atlas_pin"));
        public static final StreamCodec<ByteBuf, Pin> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pin::x,
                ByteBufCodecs.VAR_INT, Pin::y,
                ByteBufCodecs.VAR_INT, Pin::z,
                ByteBufCodecs.BOOL, Pin::remove,
                Pin::new);

        @Override
        public Type<Pin> type() {
            return TYPE;
        }
    }

    /** The player asks to share what they know of this dimension with their party. */
    public record Share() implements CustomPacketPayload {
        public static final Type<Share> TYPE = new Type<>(Awaken.id("atlas_share"));
        public static final StreamCodec<ByteBuf, Share> STREAM_CODEC = StreamCodec.unit(new Share());

        @Override
        public Type<Share> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Sync.TYPE, Sync.STREAM_CODEC);
        r.playToClient(Trail.TYPE, Trail.STREAM_CODEC);
        r.playToClient(Regions.TYPE, Regions.STREAM_CODEC);
        r.playToClient(Pins.TYPE, Pins.STREAM_CODEC);
        r.playToServer(Pin.TYPE, Pin.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Discovery.pin(sp, m.x(), m.y(), m.z(), m.remove());
        });
        r.playToServer(Share.TYPE, Share.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Discovery.share(sp);
        });
    }
}
