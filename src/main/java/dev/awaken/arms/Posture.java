package dev.awaken.arms;

/**
 * A creature's stance in a fight (SPEC §24; server only, never saved: a reload stands it back up). Deflects, heavies,
 * finishers and Undercuts fill it; left alone it drains; full, it breaks. Lives on the struck creature itself, so the
 * patent rule holds: nothing done to one creature changes another.
 */
final class Posture {
    float value;
    int max;
    long lastHitAt, brokenUntil, nextBreakAt;

    boolean broken(long now) {
        return now < brokenUntil;
    }

    /** Drains what time has given back since the last touch (called lazily, never per tick). */
    void settle(long now, boolean lord) {
        long idle = now - lastHitAt - ArmsRules.POSTURE_IDLE_TICKS;
        if (idle <= 0) return;
        if (value > 0) value = Math.max(0, value - idle * (lord ? ArmsRules.POSTURE_REGEN_LORD : ArmsRules.POSTURE_REGEN));
        lastHitAt = now - ArmsRules.POSTURE_IDLE_TICKS; // drained up to now: never twice
    }
}
