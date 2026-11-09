package dev.awaken.gates.realms;

import dev.awaken.progression.Rank;

/** A Realm with its seed: the blueprint, the rank and the land drawn from them. Pure and immutable (SPEC §50.7). */
public record RealmPlan(String id, Blueprint bp, Rank rank, long seed, int version, Land land) {
    public static final int CURRENT_VERSION = 1;

    public static RealmPlan of(String id, Blueprint bp, Rank rank, long seed, int version) {
        return new RealmPlan(id, bp, rank, seed, version, LandPrograms.make(new Land.Ctx(bp, id, rank, seed, version)));
    }
}
