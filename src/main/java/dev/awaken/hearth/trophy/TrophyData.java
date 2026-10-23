package dev.awaken.hearth.trophy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A boss trophy: which boss, its display name, the play-day it fell, and whether it has ever hung on a mount. */
public record TrophyData(String bossId, String bossName, long day, boolean placed) {
    public static final Codec<TrophyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("boss").forGetter(TrophyData::bossId),
            Codec.STRING.fieldOf("name").forGetter(TrophyData::bossName),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(TrophyData::day),
            Codec.BOOL.optionalFieldOf("placed", false).forGetter(TrophyData::placed)
    ).apply(i, TrophyData::new));

    public static final StreamCodec<ByteBuf, TrophyData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TrophyData::bossId, ByteBufCodecs.STRING_UTF8, TrophyData::bossName,
            ByteBufCodecs.VAR_LONG, TrophyData::day, ByteBufCodecs.BOOL, TrophyData::placed, TrophyData::new);

    public TrophyData asPlaced() {
        return placed ? this : new TrophyData(bossId, bossName, day, true);
    }
}
