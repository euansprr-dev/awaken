package dev.awaken.armour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.Forces;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * A piece's own story (item component {@code awaken:forged}, SPEC §44.7; schemaVersion 2): its set, the quality it left
 * the Forge at, its mastery experience, whether the heat left a scale flaw, whether two smiths struck it, its maker
 * (empty: none named); from 2 (P3) the Force of the catalyst inlaid in it (empty: none), the set whose look it wears
 * (empty: its own), its accent colour (0: its set's own) and whether its Deepglass was tuned true. A Hunter piece made
 * before the Forge Song gains it, Plain, the first time it is worn; a schema 1 piece reads with the new fields empty.
 */
public record Forged(int schemaVersion, String set, int quality, int mastery, boolean flaw, boolean twin, String maker,
                     String inlay, String look, int accent, boolean trueRing) {
    public static final int SCHEMA = 2;

    public Forged(int schemaVersion, String set, int quality, int mastery, boolean flaw, boolean twin, String maker) {
        this(schemaVersion, set, quality, mastery, flaw, twin, maker, "", "", 0, false);
    }

    public static Forged plain(ArmourSet set) {
        return new Forged(SCHEMA, set.id, 0, 0, false, false, "");
    }

    public ForgeSong.Quality qualityLevel() {
        return ForgeSong.Quality.of(quality);
    }

    /**
     * Mastery's steps (SPEC §44.5, "Mastery"): the experience each of II to V needs. I is the piece as forged; II its
     * trims brighten; III its seams glow and beat with your heart when you are low; IV its awakened parts grow (P3);
     * V its set's aura (P3).
     */
    public static final int[] MASTERY = {0, 300, 1200, 3000, 6000};
    /** The highest step built (0-based: 4 is V). */
    public static final int MASTERY_BUILT = 4;

    /** This piece's mastery step, 0-based (0 is I). */
    public int masteryStep() {
        int step = 0;
        while (step < MASTERY_BUILT && mastery >= MASTERY[step + 1]) step++;
        return step;
    }

    public Forged withMastery(int xp) {
        return new Forged(SCHEMA, set, quality, xp, flaw, twin, maker, inlay, look, accent, trueRing);
    }

    public Forged withInlay(String force, int colour) {
        return new Forged(SCHEMA, set, quality, mastery, flaw, twin, maker, force, look, colour, trueRing);
    }

    public Forged withLook(String lookSet) {
        return new Forged(SCHEMA, set, quality, mastery, flaw, twin, maker, inlay, lookSet, accent, trueRing);
    }

    public Forged withAccent(int colour) {
        return new Forged(SCHEMA, set, quality, mastery, flaw, twin, maker, inlay, look, colour, trueRing);
    }

    public Forged withTrueRing(boolean ring) {
        return new Forged(SCHEMA, set, quality, mastery, flaw, twin, maker, inlay, look, accent, ring);
    }

    /** The inlaid catalyst's Force, or null. */
    public Forces.@Nullable Force inlayForce() {
        for (Forces.Force f : Forces.Force.values()) if (f.name().equals(inlay)) return f;
        return null;
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
            Codec.STRING.optionalFieldOf("maker", "").forGetter(Forged::maker),
            Codec.STRING.optionalFieldOf("inlay", "").forGetter(Forged::inlay),
            Codec.STRING.optionalFieldOf("look", "").forGetter(Forged::look),
            Codec.INT.optionalFieldOf("accent", 0).forGetter(Forged::accent),
            Codec.BOOL.optionalFieldOf("trueRing", false).forGetter(Forged::trueRing)
    ).apply(i, Forged::new));

    public static final StreamCodec<ByteBuf, Forged> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Forged::schemaVersion, ByteBufCodecs.STRING_UTF8, Forged::set, ByteBufCodecs.VAR_INT, Forged::quality,
            ByteBufCodecs.VAR_INT, Forged::mastery, ByteBufCodecs.BOOL, Forged::flaw, ByteBufCodecs.BOOL, Forged::twin,
            ByteBufCodecs.STRING_UTF8, Forged::maker, ByteBufCodecs.STRING_UTF8, Forged::inlay, ByteBufCodecs.STRING_UTF8, Forged::look,
            ByteBufCodecs.INT, Forged::accent, ByteBufCodecs.BOOL, Forged::trueRing, Forged::new);
}
