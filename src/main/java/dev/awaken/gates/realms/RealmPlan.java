package dev.awaken.gates.realms;

import dev.awaken.progression.Rank;

/**
 * A Realm with its seed: the blueprint, the rank and the land drawn from them. Pure and immutable (SPEC §50.7). The version
 * is saved with the gate, so a land drawn before a change is drawn the same way for as long as the gate stands: 2 gives the
 * open lands their designed ways, stones to jump along and country that leans to the way (SPEC §50.9, 12 Nov).
 */
public record RealmPlan(String id, Blueprint bp, Rank rank, long seed, int version, Land land) {
    public static final int CURRENT_VERSION = 2;

    public static RealmPlan of(String id, Blueprint bp, Rank rank, long seed, int version) {
        return new RealmPlan(id, bp, rank, seed, version, LandPrograms.make(new Land.Ctx(bp, id, rank, seed, version)));
    }
}
