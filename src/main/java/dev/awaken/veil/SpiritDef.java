package dev.awaken.veil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** One of the dead, from {@code data/awaken/awaken/spirit/*.json}. */
public record SpiritDef(String name, String faction, String biomeHint, List<String> lines, String gives) {
    public static final Codec<SpiritDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(SpiritDef::name),
            Codec.STRING.optionalFieldOf("faction", "hunter").forGetter(SpiritDef::faction),
            Codec.STRING.fieldOf("biome_hint").forGetter(SpiritDef::biomeHint),
            Codec.STRING.listOf().fieldOf("lines").forGetter(SpiritDef::lines),
            Codec.STRING.optionalFieldOf("gives", "").forGetter(SpiritDef::gives)
    ).apply(i, SpiritDef::new));

    /** 0 hunter, 1 keeper, 2 sleeper, 3 ascendant (tints the ghost). */
    public int factionIndex() {
        return switch (faction) {
            case "keeper" -> 1;
            case "sleeper" -> 2;
            case "ascendant" -> 3;
            default -> 0;
        };
    }

    /** "Iri the Cartographer" stays as written; "the Quiet One" becomes "The Quiet One" at the start of a line. */
    public String displayName() {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
