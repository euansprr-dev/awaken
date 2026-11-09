package dev.awaken.hall;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Hall of Arms' one message of its own (SPEC §52): the whole wall at a glance, judged by the server. */
public final class HallNet {
    private HallNet() {}

    /** What a niche shows: not found; on Hale's rack; known, above your level; known, short of parts; ready to forge; yours. */
    public static final int SHAPE = 0, OFFER = 1, OVER = 2, KNOWN = 3, READY = 4, MADE = 5;

    public static final int COLS = 16, ROWS = 6;

    /** The wall's columns, left to right, in its five bays (Blades, Heavy, Reach, Close, Strange). */
    public static final String[] COLUMNS = {"longsword", "katana", "rapier", "greatblade", "axe", "maul", "spear", "glaive", "scythe", "warstaff",
            "daggers", "twinblades", "gauntlets", "chainblade", "shieldblade", "precursor"};

    /** Server → the fighter: level, one state a niche ({@code niches[col * ROWS + tier]}), and a bit for each Lord-forged weapon held. */
    public record Wall(int level, byte[] niches, int lords) implements CustomPacketPayload {
        public static final Type<Wall> TYPE = new Type<>(Awaken.id("hall_wall"));
        public static final StreamCodec<ByteBuf, Wall> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Wall::level, ByteBufCodecs.BYTE_ARRAY, Wall::niches, ByteBufCodecs.VAR_INT, Wall::lords, Wall::new);
        @Override public Type<Wall> type() { return TYPE; }

        public int at(int col, int tier) {
            return niches[col * ROWS + tier];
        }
    }

    /** Server → the fighter, dev only (/awaken hall piece): bring this weapon close, as a click on its niche does. */
    public record Show(String style, int tier) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(Awaken.id("hall_show"));
        public static final StreamCodec<ByteBuf, Show> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Show::style, ByteBufCodecs.VAR_INT, Show::tier, Show::new);
        @Override public Type<Show> type() { return TYPE; }
    }
}
