package dev.awaken.progression;

import com.mojang.serialization.Codec;

/** The six stats (SPEC section 3). */
public enum Stat {
    STR("Strength"), AGI("Agility"), VIT("Vitality"), INT("Intellect"), PER("Perception"), SPI("Spirit");

    public static final Codec<Stat> CODEC = Codec.STRING.xmap(Stat::valueOf, Stat::name);
    public static final int START = 5;

    public final String displayName;

    Stat(String displayName) {
        this.displayName = displayName;
    }
}
