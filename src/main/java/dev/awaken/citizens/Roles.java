package dev.awaken.citizens;

/**
 * What an event asks of a record while it runs (CityEvents): the thief's steps, the Watch's chase, a bucket line, a
 * shout. A record with a role leaves its schedule; when the role ends the schedule takes it back where it stands.
 */
final class Roles {
    private Roles() {}

    static final int NONE = 0, THIEF_SNEAK = 1, THIEF_INSIDE = 2, THIEF_FLEE = 3, THIEF_CAUGHT = 4, WATCH_CHASE = 5,
            WATCH_ESCORT = 6, SHOUT = 7, BUCKET = 8, TRAPPED = 9, CLEAR = 10, GATE_GUARD = 11, PICKPOCKET = 12, GAWK = 13, REPAIR = 14,
            // Carillon's opening (SPEC §16.4): running from the gate in the square; standing by a bed in the infirmary
            FLEE = 15, ATTEND = 16,
            // a named citizen staged by another module for a scene (the Registry ceremony: Bram), CitizenHooks.stage
            STAGED = 17;

    /** Roles that move at a run. */
    static boolean runs(int role) {
        return role == THIEF_FLEE || role == WATCH_CHASE || role == FLEE;
    }

    static boolean thief(int role) {
        return role == THIEF_SNEAK || role == THIEF_INSIDE || role == THIEF_FLEE || role == THIEF_CAUGHT || role == PICKPOCKET;
    }
}
