package dev.awaken.hearth.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The owner's client-side picture of their Hearth: drawn by the Status screen's HEARTH section and the Codex
 * rumour board. Rebuilt on the server whenever something it shows changes.
 */
public record HearthView(boolean has, BlockPos pos, int tier, int radius, int forgeTier, List<String> rooms, List<String> town,
                         int trophies, List<HearthPlayer.Rumour> rumours, int records, Home home, List<Tile> tiles, List<Look> looks) {

    public HearthView(boolean has, BlockPos pos, int tier, int radius, int forgeTier, List<String> rooms, List<String> town,
                      int trophies, List<HearthPlayer.Rumour> rumours, int records, Home home) {
        this(has, pos, tier, radius, forgeTier, rooms, town, trophies, rumours, records, home, List.of(), List.of());
    }

    /**
     * (SPEC §27.13) One room kind on the band's Hearth page: its {@code RoomType} name; built, started (some of its pieces
     * stand together in a room), open, or hidden (a kind whose rare piece you haven't come across: "?"); which pieces
     * stand in its best room (a {@code Furniture} bit mask) and whether that room is closed.
     */
    public record Tile(String kind, int state, int have, boolean closed) {
        public static final int HIDDEN = 0, OPEN = 1, STARTED = 2, BUILT = 3;

        public static final Codec<Tile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Tile::kind),
                Codec.INT.fieldOf("state").forGetter(Tile::state),
                Codec.INT.optionalFieldOf("have", 0).forGetter(Tile::have),
                Codec.BOOL.optionalFieldOf("closed", false).forGetter(Tile::closed)
        ).apply(i, Tile::new));
    }

    /**
     * (SPEC §27.13) One room as it reads ("Make it look good"): its name ("the snug timber bedroom"), its grade (the
     * {@code Reading.Grade} ordinal), its six readings 0-3 (closed, light, warmth, room, care, detail), the one thing that
     * lifts it, and its kinds ("Bedroom · Kitchen").
     */
    public record Look(String name, int grade, List<Integer> readings, String lift, String kinds) {
        public static final Codec<Look> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Look::name),
                Codec.INT.fieldOf("grade").forGetter(Look::grade),
                Codec.INT.listOf().fieldOf("readings").forGetter(Look::readings),
                Codec.STRING.optionalFieldOf("lift", "").forGetter(Look::lift),
                Codec.STRING.optionalFieldOf("kinds", "").forGetter(Look::kinds)
        ).apply(i, Look::new));
    }

    /**
     * The home as its people see it (W1): its name (the place caption on walking in), when this player's Rested ends
     * (the band, and the perk on the client), Comfort and Style.
     */
    public record Home(String name, long restedUntil, int restedMinutes, int comfort, String style, List<BlockPos> greenhouses) {
        public static final Home NONE = new Home("", 0, 0, 0, "", List.of());

        public Home(String name, long restedUntil, int restedMinutes, int comfort, String style) {
            this(name, restedUntil, restedMinutes, comfort, style, List.of());
        }

        public static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Home::name),
                Codec.LONG.fieldOf("rested_until").forGetter(Home::restedUntil),
                Codec.INT.fieldOf("rested_minutes").forGetter(Home::restedMinutes),
                Codec.INT.fieldOf("comfort").forGetter(Home::comfort),
                Codec.STRING.fieldOf("style").forGetter(Home::style),
                // (W6) the home's Greenhouse rooms as box corners (min, max, ...): herbs there ripen three times as often
                BlockPos.CODEC.listOf().optionalFieldOf("greenhouses", List.of()).forGetter(Home::greenhouses)
        ).apply(i, Home::new));

        /** Whether a position lies in one of the home's Greenhouses (by box: the client has no room cells). */
        public boolean inGreenhouse(BlockPos p) {
            for (int k = 0; k + 1 < greenhouses.size(); k += 2) {
                BlockPos a = greenhouses.get(k), b = greenhouses.get(k + 1);
                if (p.getX() >= a.getX() && p.getX() <= b.getX() && p.getY() >= a.getY() && p.getY() <= b.getY()
                        && p.getZ() >= a.getZ() && p.getZ() <= b.getZ()) return true;
            }
            return false;
        }
    }

    public static final HearthView EMPTY = new HearthView(false, BlockPos.ZERO, 0, 0, 0, List.of(), List.of(), 0, List.of(), 0, Home.NONE);

    public static final Codec<HearthView> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("has").forGetter(HearthView::has),
            BlockPos.CODEC.fieldOf("pos").forGetter(HearthView::pos),
            Codec.INT.fieldOf("tier").forGetter(HearthView::tier),
            Codec.INT.fieldOf("radius").forGetter(HearthView::radius),
            Codec.INT.fieldOf("forge").forGetter(HearthView::forgeTier),
            Codec.STRING.listOf().fieldOf("rooms").forGetter(HearthView::rooms),
            Codec.STRING.listOf().fieldOf("town").forGetter(HearthView::town),
            Codec.INT.fieldOf("trophies").forGetter(HearthView::trophies),
            HearthPlayer.Rumour.CODEC.listOf().fieldOf("rumours").forGetter(HearthView::rumours),
            Codec.INT.fieldOf("records").forGetter(HearthView::records),
            Home.CODEC.optionalFieldOf("home", Home.NONE).forGetter(HearthView::home),
            Tile.CODEC.listOf().optionalFieldOf("tiles", List.of()).forGetter(HearthView::tiles),
            Look.CODEC.listOf().optionalFieldOf("looks", List.of()).forGetter(HearthView::looks)
    ).apply(i, HearthView::new));

    public static final StreamCodec<ByteBuf, HearthView> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
