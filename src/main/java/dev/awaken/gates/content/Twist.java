package dev.awaken.gates.content;

import com.mojang.serialization.Codec;

/** The eight twists with their weights (plan generator TWISTS). NONE means an ordinary gate. */
public enum Twist {
    NONE("None", 0, ""),
    DOUBLE_GATE("Double Gate", 2, "a hidden second dungeon past the boss"),
    RED_GATE("Red Gate", 2, "the exit seals until the boss dies"),
    HOLLOW_GATE("Hollow Gate", 1, "no monsters, but something follows you"),
    TREASURE_GATE("Treasure Gate", 2, "a loot-carrier that runs"),
    INVERTED("Inverted", 1, "you possess one of the Gate Lord's minions"),
    FRIENDLY("Friendly", 1, "a spirit who needs your help"),
    ECHO_GATE("Echo Gate", 1, "race the ghost of your own past run"),
    GATE_WITHIN("Gate within a Gate", 1, "a smaller gate opens on floor 2");

    public static final Codec<Twist> CODEC = Codec.STRING.xmap(Twist::valueOf, Twist::name);

    public final String displayName;
    public final int weight;
    public final String description;

    Twist(String displayName, int weight, String description) {
        this.displayName = displayName;
        this.weight = weight;
        this.description = description;
    }

    public static Twist parse(String s) {
        for (Twist t : values()) if (t.name().equalsIgnoreCase(s) || t.displayName.equalsIgnoreCase(s)) return t;
        return null;
    }
}
