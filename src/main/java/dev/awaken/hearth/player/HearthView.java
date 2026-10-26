package dev.awaken.hearth.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

/**
 * The owner's client-side picture of their Hearth: drawn by the Status screen's HEARTH section, the Echo chooser
 * (hold B) and the Codex rumour board. Rebuilt on the server whenever something it shows changes.
 */
public record HearthView(boolean has, BlockPos pos, int tier, int radius, int forgeTier, List<String> rooms, List<String> town,
                         int trophies, int fieldCap, int pads, boolean fieldOut, List<EchoView> echoes,
                         List<HearthPlayer.Rumour> rumours, int records, Home home) {

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

    /** One roster entry as the client sees it. duty = role id of the pad it works, "" if none (resting or field). */
    public record EchoView(UUID id, String name, int level, boolean field, String duty, boolean down, boolean boss) {
        public static final Codec<EchoView> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(EchoView::id),
                Codec.STRING.fieldOf("name").forGetter(EchoView::name),
                Codec.INT.fieldOf("level").forGetter(EchoView::level),
                Codec.BOOL.fieldOf("field").forGetter(EchoView::field),
                Codec.STRING.fieldOf("duty").forGetter(EchoView::duty),
                Codec.BOOL.fieldOf("down").forGetter(EchoView::down),
                Codec.BOOL.fieldOf("boss").forGetter(EchoView::boss)
        ).apply(i, EchoView::new));
    }

    public static final HearthView EMPTY = new HearthView(false, BlockPos.ZERO, 0, 0, 0, List.of(), List.of(), 0, 3, 0,
            false, List.of(), List.of(), 0, Home.NONE);

    public static final Codec<HearthView> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("has").forGetter(HearthView::has),
            BlockPos.CODEC.fieldOf("pos").forGetter(HearthView::pos),
            Codec.INT.fieldOf("tier").forGetter(HearthView::tier),
            Codec.INT.fieldOf("radius").forGetter(HearthView::radius),
            Codec.INT.fieldOf("forge").forGetter(HearthView::forgeTier),
            Codec.STRING.listOf().fieldOf("rooms").forGetter(HearthView::rooms),
            Codec.STRING.listOf().fieldOf("town").forGetter(HearthView::town),
            Codec.INT.fieldOf("trophies").forGetter(HearthView::trophies),
            Codec.INT.fieldOf("field_cap").forGetter(HearthView::fieldCap),
            Codec.INT.fieldOf("pads").forGetter(HearthView::pads),
            Codec.BOOL.fieldOf("field_out").forGetter(HearthView::fieldOut),
            EchoView.CODEC.listOf().fieldOf("echoes").forGetter(HearthView::echoes),
            HearthPlayer.Rumour.CODEC.listOf().fieldOf("rumours").forGetter(HearthView::rumours),
            Codec.INT.fieldOf("records").forGetter(HearthView::records),
            Home.CODEC.optionalFieldOf("home", Home.NONE).forGetter(HearthView::home)
    ).apply(i, HearthView::new));

    public static final StreamCodec<ByteBuf, HearthView> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
