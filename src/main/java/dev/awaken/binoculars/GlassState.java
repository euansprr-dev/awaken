package dev.awaken.binoculars;

/**
 * Where the binoculars are (SPEC §39.2): down, coming up, at the eyes or going down, with the raise's progress (0 down,
 * 1 at the eyes). A raise can turn back halfway: the hands follow the progress, never a fixed clip. Pure.
 */
public final class GlassState {
    public enum Phase { DOWN, RAISING, UP, LOWERING }

    /** Why they came down; {@link #instant} ones skip the lowering. */
    public enum Drop {
        KEY(false), ATTACK(true), USE(true), SPRINT(false), SCREEN(true), HURT(true), DEATH(true), DIMENSION(true),
        CAST(true), GRIP(true), SPYGLASS(true), PERSPECTIVE(true), OFF(true);

        public final boolean instant;

        Drop(boolean instant) {
            this.instant = instant;
        }
    }

    private Phase phase = Phase.DOWN;
    private float t;

    public Phase phase() {
        return phase;
    }

    /** The raise's progress, 0 (down) to 1 (at the eyes). */
    public float progress() {
        return t;
    }

    /** Up or on the way up: what the key and the relay see. */
    public boolean up() {
        return phase == Phase.RAISING || phase == Phase.UP;
    }

    /** Anything shows (the hands hold them). */
    public boolean active() {
        return phase != Phase.DOWN;
    }

    /** The eyecups are at the eyes: the zoom and the lens edge are on. */
    public boolean atEyes(float eyecup) {
        return up() && t >= eyecup;
    }

    public void raise() {
        if (phase == Phase.DOWN || phase == Phase.LOWERING) phase = t >= 1F ? Phase.UP : Phase.RAISING;
    }

    public void lower(Drop why) {
        if (phase == Phase.DOWN) return;
        if (why.instant) {
            phase = Phase.DOWN;
            t = 0;
        } else {
            phase = Phase.LOWERING;
        }
    }

    /** Moves the raise on by {@code ms} of real time. */
    public void advance(float ms, float raiseMs, float lowerMs) {
        switch (phase) {
            case RAISING -> {
                t = Math.min(1F, t + ms / Math.max(1F, raiseMs));
                if (t >= 1F) phase = Phase.UP;
            }
            case LOWERING -> {
                t = Math.max(0F, t - ms / Math.max(1F, lowerMs));
                if (t <= 0F) phase = Phase.DOWN;
            }
            default -> {}
        }
    }
}
