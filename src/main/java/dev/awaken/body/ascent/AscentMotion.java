package dev.awaken.body.ascent;

/** A player's Ascent state on the server (not saved: a relog or a death ends it). Plain fields, nothing allocated per tick. */
final class AscentMotion {
    AscentState state = AscentState.NONE;
    double ax, ay, az;
    float len;
    /** Overworld game time the state began, and when the last airborne state ended (a landing within it is soft). */
    long since, endedAt = Long.MIN_VALUE / 2;
    /** Ticks on the ground while the client still says airborne (a lost NONE): the server ends it itself. */
    int groundTicks;
}
