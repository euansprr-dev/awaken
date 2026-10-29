package dev.awaken.armour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A piece's own story (item component {@code awaken:forged}, SPEC §44.7; schemaVersion 1): its set, the quality it left
 * the Forge at, its mastery experience, whether the heat left a scale flaw, whether two smiths struck it, and its maker
 * (empty: none named). A Hunter piece made before the Forge Song gains it, Plain, the first time it is worn.
 */
public record Forged(int schemaVersion, String set, int quality, int mastery, boolean flaw, boolean twin, String maker) {
    public static final int SCHEMA = 1;

    public static Forged plain(ArmourSet set) {
        return new Forged(SCHEMA, set.id, 0, 0, false, false, "");
    }

    public ForgeSong.Quality qualityLevel() {
        return ForgeSong.Quality.of(quality);
    }

    public static final Codec<Forged> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(Forged::schemaVersion),
            Codec.STRING.fieldOf("set").forGetter(Forged::set),
            Codec.INT.optionalFieldOf("quality", 0).forGetter(Forged::quality),
            Codec.INT.optionalFieldOf("mastery", 0).forGetter(Forged::mastery),
            Codec.BOOL.optionalFieldOf("flaw", false).forGetter(Forged::flaw),
            Codec.BOOL.optionalFieldOf("twin", false).forGetter(Forged::twin),
            Codec.STRING.optionalFieldOf("maker", "").forGetter(Forged::maker)
    ).apply(i, Forged::new));

    public static final StreamCodec<ByteBuf, Forged> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Forged::schemaVersion, ByteBufCodecs.STRING_UTF8, Forged::set, ByteBufCodecs.VAR_INT, Forged::quality,
            ByteBufCodecs.VAR_INT, Forged::mastery, ByteBufCodecs.BOOL, Forged::flaw, ByteBufCodecs.BOOL, Forged::twin,
            ByteBufCodecs.STRING_UTF8, Forged::maker, Forged::new);
}
