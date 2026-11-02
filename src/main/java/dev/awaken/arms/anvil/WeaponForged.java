package dev.awaken.arms.anvil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A forged weapon's story, {@code awaken:weapon_forged} (SPEC §46.2; schemaVersion 1): how well it was forged (0 Plain to
 * 3 Flawless), its flaw (none, a burnt tip, a brittle hairline) and who made it.
 */
public record WeaponForged(int schemaVersion, int quality, int flaw, String maker) {
    public static final int SCHEMA = 1;

    public static final Codec<WeaponForged> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(WeaponForged::schemaVersion),
            Codec.INT.fieldOf("quality").forGetter(WeaponForged::quality),
            Codec.INT.optionalFieldOf("flaw", 0).forGetter(WeaponForged::flaw),
            Codec.STRING.optionalFieldOf("maker", "").forGetter(WeaponForged::maker)
    ).apply(i, WeaponForged::new));

    public static final StreamCodec<ByteBuf, WeaponForged> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponForged::schemaVersion, ByteBufCodecs.VAR_INT, WeaponForged::quality,
            ByteBufCodecs.VAR_INT, WeaponForged::flaw, ByteBufCodecs.STRING_UTF8, WeaponForged::maker, WeaponForged::new);

    /** The tooltip's words: "Masterwork, a burnt tip". */
    public String line() {
        String q = AnvilSong.Quality.of(quality).label;
        return switch (flaw) {
            case AnvilSong.BURNT_TIP -> q + ", a burnt tip";
            case AnvilSong.BRITTLE -> q + ", a brittle hairline";
            default -> q;
        };
    }
}
