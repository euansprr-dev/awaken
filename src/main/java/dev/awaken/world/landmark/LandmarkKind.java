package dev.awaken.world.landmark;

import dev.awaken.above.ruin.RuinKind;

/**
 * The kinds of overworld landmark the player can find: the Well (the Descent's entrance), the four Precursor ruin kinds
 * (the spawn vault counts as a vault) and gates (one kind per gate theme, see {@link LandmarkWatch}). The ordinal is
 * sent to the client for markers, so new kinds go at the end.
 */
public enum LandmarkKind {
    WELL("well", "The Well", 10, "The way down to the Descent",
            "“A well with no bottom, %s”",
            "There's a ring of old stone %s, about %d blocks. A shaft in the middle, lights at the bottom. Don't jump unless you mean it."),
    VAULT("vault", "Sealed Vault", 4, "Something waits in the antechamber",
            "“Something sealed, %s”",
            "A sealed bunker %s, about %d blocks. The door hums for some people. Not for me. The front room's open, though."),
    MONOLITH("monolith", "Monolith", 4, "A cache on the plaza, a chamber below",
            "“A tall stone that hums, %s”",
            "A tall shadow on the sand %s, about %d blocks. Amber lights on it at dusk. Something under it, if you dig."),
    OCEAN_LAB("ocean_lab", "Ocean Lab", 6, "Dry inside, under the sea",
            "“A light under the water, %s”",
            "Fishermen say the sea glows %s, about %d blocks out. A dome on the bottom. Dry inside, they swear."),
    SKY_PLATFORM("sky_platform", "Sky Platform", 20, "Above the clouds",
            "“Something above the clouds, %s”",
            "At dusk there's a light above the clouds %s, about %d blocks. Something up there. Bring wings."),
    GATE("gate", "Gate", 0, "", "", "");

    public final String id;
    public final String display;
    /** From this level the player has a reason to go (the Horizon line and the spirit's whisper wait for it). */
    public final int reasonLevel;
    /** The discovery toast's detail line. */
    public final String found;
    /** What a grove's spirit says (one sentence; %s = "600 m SE"). */
    public final String whisper;
    /** What Mira says in town (%s = "to the southeast", %d = rounded blocks). */
    public final String rumour;

    LandmarkKind(String id, String display, int reasonLevel, String found, String whisper, String rumour) {
        this.id = id;
        this.display = display;
        this.reasonLevel = reasonLevel;
        this.found = found;
        this.whisper = whisper;
        this.rumour = rumour;
    }

    /** The {@code Progression.discover} key for the first time a player reaches this kind. */
    public String discoveryKey() {
        return "landmark/" + id;
    }

    public static LandmarkKind of(RuinKind ruin) {
        return switch (ruin) {
            case VAULT, SPAWN_VAULT -> VAULT;
            case SKY -> SKY_PLATFORM;
            case OCEAN -> OCEAN_LAB;
            case MONOLITH -> MONOLITH;
        };
    }

    public static LandmarkKind of(int ordinal) {
        LandmarkKind[] v = values();
        return v[Math.floorMod(ordinal, v.length)];
    }
}
