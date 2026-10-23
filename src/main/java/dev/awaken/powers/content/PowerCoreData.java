package dev.awaken.powers.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Data component on a Power Core: which power (id string, e.g. "awaken:beam") and which variant. */
public record PowerCoreData(String power, int variant) {
    public static final PowerCoreData EMPTY = new PowerCoreData("", 0);

    public static final Codec<PowerCoreData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("power").forGetter(PowerCoreData::power),
            Codec.INT.optionalFieldOf("variant", 0).forGetter(PowerCoreData::variant)
    ).apply(i, PowerCoreData::new));

    public static final StreamCodec<ByteBuf, PowerCoreData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PowerCoreData::power, ByteBufCodecs.VAR_INT, PowerCoreData::variant, PowerCoreData::new);
}
