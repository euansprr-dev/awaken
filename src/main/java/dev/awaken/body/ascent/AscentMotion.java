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
    // the chain of moves (style XP) and the minute's style budget
    int chain;
    long lastLinkAt = Long.MIN_VALUE / 2, styleMinuteAt = Long.MIN_VALUE / 2;
    double stylePaid;
    /** Skyborn's Aether as the server keeps it (the cheapest drain: a fair flight is never refused), and time aloft. */
    double aether = 1;
    int aloft;

    // ---- how the player got here (Ascent.arrivedByMovement) ----
    /** The trail's length: 20 seconds of ticks, a bit each (moving by Ascent or Runner, or not). */
    static final int TRAIL = 400;
    private final long[] trail = new long[(TRAIL + 63) / 64];
    private int head;
    /** Overworld game time of the last block this player placed. */
    long placedAt = Long.MIN_VALUE / 2;

    void mark(boolean moving) {
        head = (head + 1) % TRAIL;
        long bit = 1L << (head & 63);
        if (moving) trail[head >> 6] |= bit;
        else trail[head >> 6] &= ~bit;
    }

    /** How many of the last n ticks were moving. */
    int movingTicks(int n) {
        int count = 0;
        for (int i = 0, at = head; i < Math.min(n, TRAIL); i++, at = (at + TRAIL - 1) % TRAIL) {
            if ((trail[at >> 6] & (1L << (at & 63))) != 0) count++;
        }
        return count;
    }
}
