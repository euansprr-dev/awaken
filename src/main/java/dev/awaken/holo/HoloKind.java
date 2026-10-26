package dev.awaken.holo;

import org.jspecify.annotations.Nullable;

/** Which holo panel a player has open, as other players may know it (never what is on it). */
public enum HoloKind {
    NONE(0),
    STATUS(1),
    INVENTORY(2),
    /** The map in the world (SPEC §30.5): others see a content-free disc of light in front of you. */
    MAP(3);

    public final int id;

    HoloKind(int id) {
        this.id = id;
    }

    private static final HoloKind[] BY_ID = {NONE, STATUS, INVENTORY, MAP};

    /** Null for an id no version of the mod sends. */
    public static @Nullable HoloKind byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }
}
