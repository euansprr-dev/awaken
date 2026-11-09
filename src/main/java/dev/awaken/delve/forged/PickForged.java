package dev.awaken.delve.forged;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A forged pick's story, {@code awaken:pick_forged} (SPEC §49.6, schemaVersion 1). Its form and rank come from the item,
 * so a story can never disagree with what it's on. {@code quality} is what the smith earned (0 Plain to 3 Flawless);
 * {@code temper} the steps of it the Forge could hold (speed and uses come from that); {@code colour} where the oxide
 * was stopped (0 straw, 0.5 peacock, 0.75 and past blue). {@code gifts} are {@link dev.awaken.delve.api.Pick.Gift} bits
 * worked in; {@code from} the rank it started at (only ranks it has passed show as rings); {@code story} is
 * {@link #TWIN} and the rest; {@code room} a floor on its enchantment room (an old pick's room, kept).
 */
public record PickForged(int schemaVersion, int quality, int temper, float colour, String smith, String maker, long day, Tally tally,
                         int gifts, int from, int story, int room) {
    public static final int SCHEMA = 1;
    /** A helper struck it; made from memory; worn longer for the Armoury room; from an old Delver's Pick or mattock. */
    public static final int TWIN = 1, MEMORY = 2, ARMOURY = 4, OLD = 8;

    /** What it has done: {@code keen} is blocks left of a Dressing's edge. */
    public record Tally(int blocks, int ores, int hearts, int keen) {
        public static final Tally NONE = new Tally(0, 0, 0, 0);
        public static final Codec<Tally> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("blocks", 0).forGetter(Tally::blocks),
                Codec.INT.optionalFieldOf("ores", 0).forGetter(Tally::ores),
                Codec.INT.optionalFieldOf("hearts", 0).forGetter(Tally::hearts),
                Codec.INT.optionalFieldOf("keen", 0).forGetter(Tally::keen)
        ).apply(i, Tally::new));
        public static final StreamCodec<ByteBuf, Tally> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tally::blocks, ByteBufCodecs.VAR_INT, Tally::ores, ByteBufCodecs.VAR_INT, Tally::hearts,
                ByteBufCodecs.VAR_INT, Tally::keen, Tally::new);
    }

    /** A pick nobody forged (given by a command, or from the creative tab): peacock, Plain, no history, no maker. */
    public static PickForged plain(int tier) {
        return new PickForged(SCHEMA, 0, 0, 0.5F, "", "", 0L, Tally.NONE, 0, tier, 0, 0);
    }

    public static final Codec<PickForged> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(PickForged::schemaVersion),
            Codec.INT.optionalFieldOf("quality", 0).forGetter(PickForged::quality),
            Codec.INT.optionalFieldOf("temper", 0).forGetter(PickForged::temper),
            Codec.FLOAT.optionalFieldOf("colour", 0.5F).forGetter(PickForged::colour),
            Codec.STRING.optionalFieldOf("smith", "").forGetter(PickForged::smith),
            Codec.STRING.optionalFieldOf("maker", "").forGetter(PickForged::maker),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(PickForged::day),
            Tally.CODEC.optionalFieldOf("tally", Tally.NONE).forGetter(PickForged::tally),
            Codec.INT.optionalFieldOf("gifts", 0).forGetter(PickForged::gifts),
            Codec.INT.optionalFieldOf("from", 0).forGetter(PickForged::from),
            Codec.INT.optionalFieldOf("story", 0).forGetter(PickForged::story),
            Codec.INT.optionalFieldOf("room", 0).forGetter(PickForged::room)
    ).apply(i, PickForged::new));

    public static final StreamCodec<ByteBuf, PickForged> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PickForged::schemaVersion, ByteBufCodecs.VAR_INT, PickForged::quality, ByteBufCodecs.VAR_INT, PickForged::temper,
            ByteBufCodecs.FLOAT, PickForged::colour, ByteBufCodecs.STRING_UTF8, PickForged::smith, ByteBufCodecs.STRING_UTF8, PickForged::maker,
            ByteBufCodecs.VAR_LONG, PickForged::day, Tally.STREAM_CODEC, PickForged::tally, ByteBufCodecs.VAR_INT, PickForged::gifts,
            ByteBufCodecs.VAR_INT, PickForged::from, ByteBufCodecs.VAR_INT, PickForged::story, ByteBufCodecs.VAR_INT, PickForged::room,
            PickForged::new);

    public PickForged withTally(Tally t) {
        return new PickForged(schemaVersion, quality, temper, colour, smith, maker, day, t, gifts, from, story, room);
    }

    public PickForged withGifts(int g) {
        return new PickForged(schemaVersion, quality, temper, colour, smith, maker, day, tally, g, from, story, room);
    }

    public boolean is(int storyBit) {
        return (story & storyBit) != 0;
    }
}
