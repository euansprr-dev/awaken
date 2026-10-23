package dev.awaken.gates.content;

import com.mojang.serialization.Codec;

/** The eight layout grammars (plan generator LAYOUTS). Each one builds a different floor graph in {@code FloorBuilder}. */
public enum Layout {
    LABYRINTH("Labyrinth", "dead ends, loops and hidden caches"),
    SPIRAL_TOWER("Spiral Tower", "you climb as you go"),
    SHAFT("Shaft", "you descend around a great drop"),
    HUB_AND_SPOKES("Hub and Spokes", "three keys guard the shrine"),
    ARENA_CHAIN("Arena Chain", "each arena seals until it is empty"),
    STEALTH_HALLS("Stealth Halls", "sentries walk the halls; don't be seen"),
    PUZZLE_VAULT("Puzzle Vault", "the shrine opens when the runes agree"),
    OPEN_CAVERN("Open Cavern", "one vast cave and whatever lives in it");

    public static final Codec<Layout> CODEC = Codec.STRING.xmap(Layout::valueOf, Layout::name);

    public final String displayName;
    public final String hint;

    Layout(String displayName, String hint) {
        this.displayName = displayName;
        this.hint = hint;
    }

    public static Layout parse(String s) {
        for (Layout l : values()) if (l.name().equalsIgnoreCase(s) || l.displayName.equalsIgnoreCase(s)) return l;
        return null;
    }
}
