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

    /**
     * Mastery's steps (SPEC §44.5, "Mastery"): the experience each of II to V needs. I is the piece as forged; P2 builds
     * II (its trims brighten) and III (its seams glow and beat with your heart when you are low); IV and V come with P3.
     */
    public static final int[] MASTERY = {0, 300, 1200, 3000, 6000};
    /** The highest step built so far (0-based: 2 is III). */
    public static final int MASTERY_BUILT = 2;

    /** This piece's mastery step, 0-based (0 is I). */
    public int masteryStep() {
        int step = 0;
        while (step < MASTERY_BUILT && mastery >= MASTERY[step + 1]) step++;
        return step;
    }

    public Forged withMastery(int xp) {
        return new Forged(schemaVersion, set, quality, xp, flaw, twin, maker);
    }

    public static String numeral(int step) {
        return switch (step) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            default -> "V";
        };
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
