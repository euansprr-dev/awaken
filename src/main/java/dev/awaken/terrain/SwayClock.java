package dev.awaken.terrain;

/**
 * A clock that follows the game's time without ever jumping (SPEC 15, "Swaying plants"). The client's game time is
 * not smooth: the client adds a tick each client tick, and the server overwrites it with its own count every 20 server
 * ticks, so whenever the integrated server falls behind (chunk generation, a busy machine) the client's clock is pulled
 * back a few ticks every second or two. Wind read straight from it rewinds, and every plant and leaf with it.
 *
 * <p>This clock runs on real frame time and eases toward the game's clock. A small error is worked off at no more
 * than {@link #MAX_CORRECTION} of the clock's own pace; a growing one (a server stuck at half speed) lets the pace
 * change more, up to {@link #MAX_PACE_CHANGE}, so the clock follows a slow server instead of falling seconds behind. It
 * never runs backwards and never visibly lurches. It snaps only on a jump of over {@link #SNAP_SECONDS} (joining a
 * world, a dimension change, a frozen or sprinted tick rate). It stands still while the game is paused. One per use;
 * not thread-safe.
 */
public final class SwayClock {
    /** The share of real time by which a correction may speed the clock up or slow it down, for a small error. */
    public static final double MAX_CORRECTION = 0.35;
    /** The most the pace may change, for an error of several seconds (below 1: the clock never runs backwards). */
    public static final double MAX_PACE_CHANGE = 0.9;
    /** How fast an error is worked off, per second, before the cap. */
    public static final double GAIN = 1.2;
    /** An error larger than this is a real jump in time, not drift: follow it at once. */
    public static final double SNAP_SECONDS = 4.0;
    /** A frame longer than this (a hitch, a breakpoint) counts as this long. */
    public static final double MAX_FRAME = 0.25;

    private double seconds;
    private boolean started;

    /**
     * This frame's time: {@code target} is the game's own clock in seconds now, {@code frame} the real time since the
     * last call in seconds.
     */
    public double advance(double target, double frame, boolean paused) {
        if (!started || Math.abs(target - seconds) > SNAP_SECONDS) {
            seconds = target;
            started = true;
            return seconds;
        }
        if (paused) return seconds;
        double dt = Math.clamp(frame, 0.0, MAX_FRAME);
        double ahead = seconds + dt;
        double error = target - ahead;
        double cap = dt * Math.min(MAX_PACE_CHANGE, MAX_CORRECTION + 0.15 * Math.abs(error));
        double correction = Math.clamp(error * Math.min(1.0, dt * GAIN), -cap, cap);
        seconds = ahead + correction;
        return seconds;
    }

    public double seconds() {
        return seconds;
    }

    /** Forget the past (a new world): the next call takes the game's time as it is. */
    public void reset() {
        started = false;
    }
}
