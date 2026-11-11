package dev.awaken.arms;

/**
 * A creature's stance in a fight (SPEC §25; server only, never saved: a reload stands it back up; fighters near see
 * a snapshot of it on every change, drained on their side by the same rule). Deflects, heavies,
 * finishers and Undercuts fill it; left alone it drains; full, it breaks. Lives on the struck creature itself, so the
 * patent rule holds: nothing done to one creature changes another.
 */
final class Posture {
    float value;
    int max;
    long lastHitAt, brokenUntil, nextBreakAt;
    /**
     * A duel body that is fighting (a move playing, a limb coming back, reeling, kneeling, standing in its opening) holds
     * its stance: none drains until it stops, and the grace starts again from then (SPEC §47.3 "Held while it fights").
     */
    boolean held;

    boolean broken(long now) {
        return now < brokenUntil;
    }

    /** Drains what time has given back since the last touch (called lazily, never per tick); nothing while held. */
    void settle(long now, boolean lord) {
        if (held || now - lastHitAt <= ArmsRules.POSTURE_IDLE_TICKS) return;
        value = ArmsRules.drained(value, now - lastHitAt, lord);
        lastHitAt = now - ArmsRules.POSTURE_IDLE_TICKS; // drained up to now: never twice
    }
}
