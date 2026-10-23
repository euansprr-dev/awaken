package dev.awaken.lucidium.plan;

import java.util.Optional;
import java.util.UUID;

/**
 * Dream signs: small recurring oddities. Real dream signs are personal and repeat from dream to dream, so every
 * player has one sign that always comes back (picked from their id) and one that changes each night. Spotting one
 * (looking right at it) raises lucidity by 15.
 */
public enum DreamSign {
    NO_SHADOW("A creature with no shadow", "It floats, perfectly still, and the light goes straight through where its shadow should be."),
    SPINNING_CLOCK("A clock that spins", "Its hand never stops. Clocks never tell the time in dreams."),
    SCRAMBLED_SIGNS("Signs that will not hold still", "Read it twice and it says something else. Text never stays the same in dreams."),
    DOOR_IN_SKY("A door in the sky", "A door, hanging in the air, opening onto nothing."),
    RAIN_UP("Rain falling up", "The drops rise from your feet into the sky.");

    public final String title;
    public final String description;

    DreamSign(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** The sign that recurs in every dream of this player. */
    public static DreamSign personal(UUID player) {
        long h = player.getMostSignificantBits() ^ (player.getLeastSignificantBits() * 31);
        return values()[(int) Math.floorMod(h, (long) values().length)];
    }

    public static Optional<DreamSign> byName(String name) {
        try {
            return Optional.of(valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
