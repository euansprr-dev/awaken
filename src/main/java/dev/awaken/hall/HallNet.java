package dev.awaken.hall;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Hall of Arms' messages (SPEC §52): the whole wall at a glance, judged by the server, and the suits. */
public final class HallNet {
    private HallNet() {}

    /** What a niche shows: not found; on Hale's rack; known, above your level; known, short of parts; ready to forge; yours. */
    public static final int SHAPE = 0, OFFER = 1, OVER = 2, KNOWN = 3, READY = 4, MADE = 5;

    public static final int COLS = 16, ROWS = 6;

    /** The wall's columns, left to right, in its five bays (Blades, Heavy, Reach, Close, Strange). */
    public static final String[] COLUMNS = {"longsword", "katana", "rapier", "greatblade", "axe", "maul", "spear", "glaive", "scythe", "warstaff",
            "daggers", "twinblades", "gauntlets", "chainblade", "shieldblade", "precursor"};

    /** The Picks wall's columns (SPEC §49, §52): the four forms, in PickForm's order. */
    public static final int PICK_COLS = 4;

    /**
     * Server → the fighter: level, one state a niche ({@code niches[col * ROWS + tier]}), a bit for each Lord-forged weapon
     * held, and the Picks wall's niches the same way ({@code picks[form * ROWS + tier]}; never OFFER, SHAPE for a form whose
     * deed isn't done).
     */
    public record Wall(int level, byte[] niches, int lords, byte[] picks) implements CustomPacketPayload {
        public static final Type<Wall> TYPE = new Type<>(Awaken.id("hall_wall"));
        public static final StreamCodec<ByteBuf, Wall> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Wall::level, ByteBufCodecs.BYTE_ARRAY, Wall::niches, ByteBufCodecs.VAR_INT, Wall::lords,
                ByteBufCodecs.BYTE_ARRAY, Wall::picks, Wall::new);
        @Override public Type<Wall> type() { return TYPE; }

        public int at(int col, int tier) {
            return niches[col * ROWS + tier];
        }

        public int pick(int form, int tier) {
            return picks[form * ROWS + tier];
        }
    }

    /** Server → the fighter: the Hall's armour, kept up to date without opening anything. */
    public record Suits(dev.awaken.armour.net.ArmourPayloads.Offers offers) implements CustomPacketPayload {
        public static final Type<Suits> TYPE = new Type<>(Awaken.id("hall_suits"));
        public static final StreamCodec<ByteBuf, Suits> STREAM_CODEC = StreamCodec.composite(
                dev.awaken.armour.net.ArmourPayloads.Offers.STREAM_CODEC, Suits::offers, Suits::new);
        @Override public Type<Suits> type() { return TYPE; }
    }

    public static final int REFRESH = 0, TRACK = 1, UNTRACK = 2, PEEK = 3;

    /** The fighter → server: the suits again, optionally following one first; {@code pos} the stand the Hall was opened at, if any. */
    public record Ask(int op, String set, net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("hall_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Ask::op, ByteBufCodecs.STRING_UTF8, Ask::set, net.minecraft.core.BlockPos.STREAM_CODEC, Ask::pos, Ask::new);
        @Override public Type<Ask> type() { return TYPE; }
    }

    /** Server → the fighter, dev only (/awaken hall piece): bring this weapon close, as a click on its niche does. */
    public record Show(String style, int tier) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(Awaken.id("hall_show"));
        public static final StreamCodec<ByteBuf, Show> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Show::style, ByteBufCodecs.VAR_INT, Show::tier, Show::new);
        @Override public Type<Show> type() { return TYPE; }
    }
}
