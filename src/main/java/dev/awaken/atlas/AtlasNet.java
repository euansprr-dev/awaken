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

    /**
     * A tile on the wire: its explored bits, and the zone of each explored cell (32×32 bytes: the Living Wilds region
     * it lies in, 1-based into the player's known regions, 0 for none; empty where regions don't apply), and the Living Wilds'
     * level of the land at each explored cell (a byte, 255 at most; SPEC §21.11: the map shades smoothly by it).
     */
    public record WireTile(int tx, int tz, long[] bits, byte[] zones, byte[] levels) {
        static final StreamCodec<ByteBuf, WireTile> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WireTile::tx,
                ByteBufCodecs.VAR_INT, WireTile::tz,
                ByteBufCodecs.LONG_ARRAY, WireTile::bits,
                ByteBufCodecs.BYTE_ARRAY, WireTile::zones,
                ByteBufCodecs.BYTE_ARRAY, WireTile::levels,
                WireTile::new);
    }

    /** Where Carillon's Choir Square is in this world (the city plan's origin), or {@code present} false. */
    public record City(boolean present, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<City> TYPE = new Type<>(Awaken.id("atlas_city"));
        public static final StreamCodec<ByteBuf, City> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, City::present,
                ByteBufCodecs.VAR_INT, City::x,
                ByteBufCodecs.VAR_INT, City::y,
                ByteBufCodecs.VAR_INT, City::z,
                City::new);

        @Override
        public Type<City> type() {
            return TYPE;
        }
    }

    /**
     * Explored tiles in {@code dim}, whole (16 longs each). {@code reset}: forget every other tile of that dimension
     * first (login, a new dimension). {@code ripple}: the reveal spreads from (ox, oz), a bell's ring.
     */
    public record Sync(String dim, boolean reset, List<WireTile> tiles, boolean ripple, int ox, int oz) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Awaken.id("atlas_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Sync::dim,
                ByteBufCodecs.BOOL, Sync::reset,
                WireTile.STREAM_CODEC.apply(ByteBufCodecs.list(4096)), Sync::tiles,
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

    /**
     * (SPEC §16.8) A Hearth on the Atlas: its name, where its Anchor stands, its tier, and whose it is to the viewer
     * ({@link Homes#OWN} their own, {@link Homes#SHARED} one they are a member of, {@link Homes#HOUSEHOLD} a housemate's own).
     */
    public record HomeView(String name, int x, int y, int z, int tier, int kind) {
        static final StreamCodec<ByteBuf, HomeView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), HomeView::name,
                ByteBufCodecs.VAR_INT, HomeView::x,
                ByteBufCodecs.VAR_INT, HomeView::y,
                ByteBufCodecs.VAR_INT, HomeView::z,
                ByteBufCodecs.VAR_INT, HomeView::tier,
                ByteBufCodecs.VAR_INT, HomeView::kind,
                HomeView::new);
    }

    /** The Hearths the viewer belongs to and their household's own, whole (empty outside the overworld). */
    public record Homes(List<HomeView> homes) implements CustomPacketPayload {
        public static final int OWN = 0, SHARED = 1, HOUSEHOLD = 2;
        public static final Type<Homes> TYPE = new Type<>(Awaken.id("atlas_homes"));
        public static final StreamCodec<ByteBuf, Homes> STREAM_CODEC = StreamCodec.composite(
                HomeView.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Homes::homes, Homes::new);

        @Override
        public Type<Homes> type() {
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
        var r = e.registrar("2");
        r.playToClient(Sync.TYPE, Sync.STREAM_CODEC);
        r.playToClient(Trail.TYPE, Trail.STREAM_CODEC);
        r.playToClient(Regions.TYPE, Regions.STREAM_CODEC);
        r.playToClient(Pins.TYPE, Pins.STREAM_CODEC);
        r.playToClient(Homes.TYPE, Homes.STREAM_CODEC);
        r.playToClient(City.TYPE, City.STREAM_CODEC);
        r.playToServer(Pin.TYPE, Pin.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Discovery.pin(sp, m.x(), m.y(), m.z(), m.remove());
        });
        r.playToServer(Share.TYPE, Share.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Discovery.share(sp);
        });
    }
}
