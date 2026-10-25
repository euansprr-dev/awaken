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
    /** On a run of stairs or slabs (SPEC §17.22): the slide goes on while the server sees the run under the body. */
    boolean stairSliding;
    double stairFallX, stairFallZ;
    final int[] tripped = new int[8];
    int trippedCount;
    // mantle
    long mantleUntil;
    // ---- spike S3: the Runner's Kit ----
    // wall-run
    long runUntil, runEndedAt = Long.MIN_VALUE / 2, lastWallJumpAt = Long.MIN_VALUE / 2;
    int runAxis, runSide, runMiss, links;
    double runPlane;
    long lastWall;
    /** Walls run since the last landing (fresh starts never reuse one). */
    final long[] walls = new long[8];
    int wallCount;
    // hang
    boolean hanging, shimmying;
    int hangAxis, hangSign, hangMiss;
    double hangTop;
    long hangSince, hangEndedAt = Long.MIN_VALUE / 2, hangQuietUntil;
    /** The hold the server last accepted (the spike's fallback re-check and its diagnostics). */
    double hangX, hangY, hangZ;
    int hangFallbacks;
    boolean hangExplained;
    // grip on a climb line (SPEC §17.23): the line's column, since when, misses of the re-check
    boolean gripping;
    int gripBx, gripBz, gripMiss;
    long gripSince, gripEndedAt = Long.MIN_VALUE / 2;
    // the server's own recent positions (a claimed start must be one of them)
    final double[] hist = new double[RunnerRules.HISTORY * 3];
    int histAt, histCount;
    // the bench's view of the trial in progress
    boolean tStarted, tAccepted, tRefused, tEnded, tCorrected;
    int tRuns, tGrabs;

    boolean runner() {
        return runUntil != 0 || hanging || gripping;
    }

    boolean ranWall(long id) {
        for (int i = 0; i < wallCount; i++) if (walls[i] == id) return true;
        return false;
    }

    void addWall(long id) {
        if (wallCount < walls.length) walls[wallCount++] = id;
    }

    void record(double x, double y, double z) {
        hist[histAt * 3] = x;
        hist[histAt * 3 + 1] = y;
        hist[histAt * 3 + 2] = z;
        histAt = (histAt + 1) % RunnerRules.HISTORY;
        if (histCount < RunnerRules.HISTORY) histCount++;
    }

    /** Whether the server saw the feet near (x, y, z) in its last ticks. */
    boolean saw(double x, double y, double z, double tol) {
        for (int i = 0; i < histCount; i++) {
            double dx = hist[i * 3] - x, dy = hist[i * 3 + 1] - y, dz = hist[i * 3 + 2] - z;
            if (dx * dx + dz * dz <= tol * tol && Math.abs(dy) <= tol) return true;
        }
        return false;
    }

    // anything
    long lastMove = Long.MIN_VALUE, lastGlide = Long.MIN_VALUE / 2, lastSprint = Long.MIN_VALUE / 2;
    /** The dev command's stand-in for input: the next {@code assistMove} within the window skips the input checks. */
    int assistMove = -1;
    long assistUntil;

    boolean active() {
        return tacUntil != 0 || slideUntil != 0 || mantleUntil != 0 || runUntil != 0 || hanging;
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
