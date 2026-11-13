package dev.awaken.gates.realms;

import dev.awaken.progression.Rank;

/**
 * A Realm with its seed: the blueprint, the rank and the land drawn from them. Pure and immutable (SPEC §50.7). The version
 * is saved with the gate, so a land drawn before a change is drawn the same way for as long as the gate stands: 2 gives the
 * open lands their designed ways, stones to jump along and country that leans to the way (SPEC §50.9, 12 Nov). 3 gives the
 * rolled gates their beats: ways, crossings and wells laid as runs of moves (SPEC §72), given to a new gate only while
 * gateBeats is on and the Runner's kit is in the game ({@link Realms#planVersion}).
 */
public record RealmPlan(String id, Blueprint bp, Rank rank, long seed, int version, Land land) {
    public static final int CURRENT_VERSION = 3;
    /** The version before the beats (SPEC §72): what a new gate gets with gateBeats off or without the Runner's kit. */
    public static final int BEFORE_BEATS = 2;
    /** The version that fixed the open lands (SPEC §50.9): one drawn before it comes back as the land Euan didn't enjoy. */
    public static final int OPEN_LANDS_FIXED = 2;

    public static RealmPlan of(String id, Blueprint bp, Rank rank, long seed, int version) {
        return new RealmPlan(id, bp, rank, seed, version, LandPrograms.make(new Land.Ctx(bp, id, rank, seed, version)));
    }
}
