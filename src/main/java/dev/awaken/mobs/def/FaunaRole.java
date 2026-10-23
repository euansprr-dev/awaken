package dev.awaken.mobs.def;

/**
 * What an overworld creature (the Awakened layer, plan v2 section 4.2) is for. Gate and stratum creatures have no
 * fauna role. The per-role numbers are how many of that role may live within spawn range of one player; together
 * they stay under the fauna category's own cap of 16 per player (docs/modules/fauna.md).
 */
public enum FaunaRole {
    /** Never hostile. Makes a biome feel alive; drops a material or leads you somewhere. */
    AMBIENT(8),
    /** Grazes or wanders and fights back when struck. */
    NEUTRAL(5),
    /** Hostile only in darkness (vanilla's light rule: torches mean safety). */
    HUNTER(5),
    /** A rare named roaming beast: a mini-boss with phases, essence and a good Power Core chance. */
    BEAST(1);

    /** At most this many of the role within 128 blocks of the nearest player. */
    public final int capPerPlayer;

    FaunaRole(int capPerPlayer) {
        this.capPerPlayer = capPerPlayer;
    }

    /** Ambient creatures are the only ones allowed before awakening and inside a Hearth radius. */
    public boolean peaceful() {
        return this == AMBIENT;
    }

    public boolean hostileInDark() {
        return this == HUNTER;
    }
}
