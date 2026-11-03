package dev.awaken.arms.anvil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A forged weapon's story, {@code awaken:weapon_forged} (SPEC §46.2, §46.7, §46.8, §46.9; schemaVersion 4): how well it
 * was forged (0 Plain to 3 Flawless), its flaw (none, a burnt tip, a brittle hairline), who made it, whether a helper's
 * strikes made it twin-struck, whether it was made from memory (a v1 story reads with both false), whether it was
 * reforged, its story then the reforging's (a v2 story reads false), and what its rank's acts left in the steel (a v3
 * story reads none): RIPPLES a well-twisted A blade, STARLIT an S blade quenched in starlight.
 */
public record WeaponForged(int schemaVersion, int quality, int flaw, String maker, boolean twin, boolean memory, boolean reforged, int acts) {
    public static final int SCHEMA = 4;
    public static final int RIPPLES = 1, STARLIT = 2;

    /** The story a weapon of ours gains when first held without one (made before the War Anvil, or Hale's issue). */
    public static final WeaponForged PLAIN = new WeaponForged(SCHEMA, 0, AnvilSong.NO_FLAW, "", false, false, false, 0);

    public static final Codec<WeaponForged> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(WeaponForged::schemaVersion),
            Codec.INT.fieldOf("quality").forGetter(WeaponForged::quality),
            Codec.INT.optionalFieldOf("flaw", 0).forGetter(WeaponForged::flaw),
            Codec.STRING.optionalFieldOf("maker", "").forGetter(WeaponForged::maker),
            Codec.BOOL.optionalFieldOf("twin", false).forGetter(WeaponForged::twin),
            Codec.BOOL.optionalFieldOf("memory", false).forGetter(WeaponForged::memory),
            Codec.BOOL.optionalFieldOf("reforged", false).forGetter(WeaponForged::reforged),
            Codec.INT.optionalFieldOf("acts", 0).forGetter(WeaponForged::acts)
    ).apply(i, WeaponForged::new));

    public static final StreamCodec<ByteBuf, WeaponForged> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponForged::schemaVersion, ByteBufCodecs.VAR_INT, WeaponForged::quality,
            ByteBufCodecs.VAR_INT, WeaponForged::flaw, ByteBufCodecs.STRING_UTF8, WeaponForged::maker,
            ByteBufCodecs.BOOL, WeaponForged::twin, ByteBufCodecs.BOOL, WeaponForged::memory, ByteBufCodecs.BOOL, WeaponForged::reforged,
            ByteBufCodecs.VAR_INT, WeaponForged::acts, WeaponForged::new);

    /** The tooltip's words: "Masterwork, a burnt tip", "Masterwork, twin-struck", "Fine, from memory", "Flawless, twisted steel". */
    public String line() {
        String q = AnvilSong.Quality.of(quality).label;
        q = switch (flaw) {
            case AnvilSong.BURNT_TIP -> q + ", a burnt tip";
            case AnvilSong.BRITTLE -> q + ", a brittle hairline";
            default -> q;
        };
        if ((acts & RIPPLES) != 0) q += ", twisted steel";
        if ((acts & STARLIT) != 0) q += ", quenched in starlight";
        if (twin) q += ", twin-struck";
        if (memory) q += ", from memory";
        return q;
    }
}
