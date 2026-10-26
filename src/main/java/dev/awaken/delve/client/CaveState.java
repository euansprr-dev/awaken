package dev.awaken.delve.client;

import org.jspecify.annotations.Nullable;

/**
 * What the client knows of the cave place the player is in (SPEC §28.5), for the senses: the server sends it when it
 * changes ({@code CaveClient}); {@link #seep()} eases in and out over a couple of seconds so the hum, the muffle and the
 * violet never switch. Client thread only; nothing allocated.
 */
public final class CaveState {
    private CaveState() {}

    /** The place kinds, as the server names them ({@code delve.cave.CaveKind}'s serialized names). */
    public static final String SINKHOLE = "sinkhole", MINE = "mine", SEEP = "seep", CHUTE = "chute", GROTTO = "grotto";

    private static @Nullable String place;
    private static String name = "";
    private static boolean quiet;
    private static float seepGoal, seep;
    /** A dev override of the seep's weight (-1: none), for listening and looking without a seep. */
    private static float forced = -1F;

    /** The place the player is in ({@link #SEEP}, ...), or null outside every cave place. */
    public static @Nullable String place() {
        return place;
    }

    /** The place's name ("The Delvers' Deep"), or "" outside. */
    public static String name() {
        return name;
    }

    /**
     * 0..1: how strongly a seep presses on the listener (inside a seep's halls, easing at its edges); 0 outside one and
     * once its heart has fallen (a quiet seep is only stained rock).
     */
    public static float seep() {
        return forced >= 0F ? forced : seep;
    }

    /** Whether the seep the player is in has gone quiet (its heart fell). */
    public static boolean seepQuiet() {
        return quiet;
    }

    /** From the server: the place now ({@code null} for none), its name, the seep's strength there, and a quiet seep. */
    static void set(@Nullable String kind, String placeName, float seepStrength, boolean seepQuiet) {
        place = kind;
        name = placeName;
        quiet = seepQuiet;
        seepGoal = seepQuiet ? 0F : Math.max(0F, Math.min(1F, seepStrength));
    }

    /** Dev: hold the seep's weight at {@code w} (0..1), or let go with a negative value. */
    public static void force(float w) {
        forced = w < 0F ? -1F : Math.min(1F, w);
    }

    static void tick() {
        seep += (seepGoal - seep) * (seepGoal > seep ? 0.04F : 0.03F);
        if (Math.abs(seepGoal - seep) < 1e-3F) seep = seepGoal;
    }

    static void reset() {
        place = null;
        name = "";
        quiet = false;
        seepGoal = seep = 0F;
        forced = -1F;
    }
}
