package dev.awaken.citizens;

import java.util.Locale;

/**
 * What a citizen's body is visibly doing: each value names a clip in {@code assets/awaken/citizen_clips.json}
 * (tools/art/specs/citizen_clips.py, lower case). The server sets one per body (a synced int); the client plays the
 * clip on the citizen's model. {@code NONE} keeps the vanilla pose. Appended only: the ordinal is sent.
 */
public enum Act {
    NONE,
    // standing about
    ARMS_CROSSED, HANDS_BEHIND, LEAN, SIT, LOOK_AROUND, TALK,
    // the twelve jobs and the rest
    LAMP_LIGHT, BELL_PULL, CARRY_BACK, SET_DOWN, SHUTTER, SERVE, TRAY, HAMMER, HALBERD, HALBERD_STAND, PROCLAIM,
    SCRUB, HANG, TROWEL, RUN, READ, HAUL, PLAY,
    // reactions and events
    WAVE, NOD, POINT_SHOUT, BUCKET_PASS, HURT, CHEER, THIEF_RUN, SURRENDER, TRAPPED,
    // (M4 B3b) the boatmen's work at their berths and the nets
    COIL, BAIL, MEND, POLE,
    // (SPEC §28.7) a village's troubles and festivals: the ring dance, the reunion, a parent's grief
    DANCE, EMBRACE, WEEP;

    private static final Act[] ALL = values();

    public static Act of(int ordinal) {
        return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : NONE;
    }

    public String clip() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Clips that only pose the arms (and maybe lean): the legs keep vanilla's walk, so they play while walking. */
    public boolean overlay() {
        return switch (this) {
            case CARRY_BACK, TRAY, HALBERD, RUN, HURT, THIEF_RUN, HANDS_BEHIND -> true;
            default -> false;
        };
    }
}
