package dev.awaken.holo.ui;

import java.util.List;

/**
 * One meaning for each key across the big screens (SPEC §57.11): the band's stage, the Hall of Arms, the map and the bag.
 * The screens read these constants instead of naming keys themselves, and {@link #USES} is the whole table, the handful of
 * honest exceptions with their reasons included, so adding a second meaning for a key means editing this file and its test
 * ({@code ui_keys_one_meaning}) says so. Plain numbers (the game's key codes): side-agnostic.
 */
public final class StageKeys {
    private StageKeys() {}

    // Esc, Tab, Q, E, T, X, Z, Enter, Space, as the game numbers them in 26.3 (SDL's scancodes, not the old GLFW codes).
    // The client checks these against the game's own constants as it starts (matches), so a change of numbering can't pass quietly.
    public static final int BACK = 41, NEXT = 43, BESIDE_BACK = 20, BESIDE_ON = 8, TRACK = 23, SET_ASIDE = 27, UNDO = 29, COMMIT = 40, PLACE = 44;

    /** Whether these are the game's own codes for Esc, Tab, Q, E, T, X, Z, Enter and Space. */
    public static boolean matches(int esc, int tab, int q, int e, int t, int x, int z, int enter, int space) {
        return esc == BACK && tab == NEXT && q == BESIDE_BACK && e == BESIDE_ON && t == TRACK && x == SET_ASIDE && z == UNDO && enter == COMMIT && space == PLACE;
    }

    public enum Verb {
        /** One step back; out when there's nothing to step back from. */
        BACK,
        /** The next thing on the screen (Shift: the one before). */
        NEXT,
        /** The view beside this one: a place on the band, a wing in the Hall. */
        BESIDE,
        /** Keep it on your screen and point the way. */
        TRACK,
        SET_ASIDE,
        /** Take back the last thing done. */
        UNDO,
        /** Held: commit what can't be undone. */
        COMMIT
    }

    /** A key on a surface: the verb it is there, or why it isn't the shared one. */
    public record Use(String surface, int key, Verb verb, String except) {
        static Use of(String surface, int key, Verb verb) {
            return new Use(surface, key, verb, "");
        }

        static Use but(String surface, int key, String why) {
            return new Use(surface, key, null, why);
        }
    }

    public static final List<Use> USES = List.of(
            Use.of("band", BACK, Verb.BACK), Use.of("band", NEXT, Verb.NEXT), Use.of("band", BESIDE_BACK, Verb.BESIDE), Use.of("band", BESIDE_ON, Verb.BESIDE),
            Use.of("band", TRACK, Verb.TRACK), Use.of("band", SET_ASIDE, Verb.SET_ASIDE), Use.of("band", UNDO, Verb.UNDO), Use.of("band", COMMIT, Verb.COMMIT),
            Use.of("hall", BACK, Verb.BACK), Use.of("hall", NEXT, Verb.NEXT), Use.of("hall", BESIDE_BACK, Verb.BESIDE), Use.of("hall", BESIDE_ON, Verb.BESIDE),
            Use.of("hall", TRACK, Verb.TRACK),
            Use.of("map", BACK, Verb.BACK), Use.of("map", NEXT, Verb.NEXT), Use.of("map", TRACK, Verb.TRACK),
            Use.but("map", BESIDE_BACK, "turns the map: it is a camera, and there is no view beside it"),
            Use.but("map", BESIDE_ON, "turns the map, and changes a waypoint of yours when one is picked"),
            Use.of("bag", BACK, Verb.BACK), Use.of("bag", UNDO, Verb.UNDO),
            Use.but("bag", TRACK, "tidies the bag (the player's own binding, T by default): there is nothing to track in a bag"));

    /** The one verb a key has, or null for a key the table doesn't share. */
    public static Verb verb(int key) {
        return switch (key) {
            case BACK -> Verb.BACK;
            case NEXT -> Verb.NEXT;
            case BESIDE_BACK, BESIDE_ON -> Verb.BESIDE;
            case TRACK -> Verb.TRACK;
            case SET_ASIDE -> Verb.SET_ASIDE;
            case UNDO -> Verb.UNDO;
            case COMMIT -> Verb.COMMIT;
            default -> null;
        };
    }
}
