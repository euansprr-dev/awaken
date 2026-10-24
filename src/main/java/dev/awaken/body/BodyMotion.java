package dev.awaken.body;

/**
 * A player's moves in progress on the server (not saved: a relog or a death ends them). Plain fields so the per-tick
 * check allocates nothing. Times are overworld game time, the clock powers uses.
 */
final class BodyMotion {
    // tactical sprint
    long tacUntil, tacReadyAt, tacEndedAt = Long.MIN_VALUE / 2;
    int tacNoSprint;
    // slide
    long slideUntil, slideReadyAt, slideStart;
    double slideDirX, slideDirZ, slideV0;
    boolean slideFromTac;
    final int[] tripped = new int[8];
    int trippedCount;
    // mantle
    long mantleUntil;
    // anything
    long lastMove = Long.MIN_VALUE, lastGlide = Long.MIN_VALUE / 2;
    /** The dev command's stand-in for input: the next {@code assistMove} within the window skips the input checks. */
    int assistMove = -1;
    long assistUntil;

    boolean active() {
        return tacUntil != 0 || slideUntil != 0 || mantleUntil != 0;
    }

    boolean assisted(Move m, long now) {
        return assistMove == m.ordinal() && now <= assistUntil;
    }

    boolean tripped(int id) {
        for (int i = 0; i < trippedCount; i++) if (tripped[i] == id) return true;
        return false;
    }

    void markTripped(int id) {
        if (trippedCount < tripped.length) tripped[trippedCount++] = id;
    }
}
