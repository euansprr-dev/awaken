package dev.awaken.village.plan;

/**
 * What the planner reads of the land (SPEC §28.6): the generator's own estimate, never a chunk load. Implementations
 * wrap the chunk generator's height query (world generation, commands) or a function (tests). Must be deterministic and
 * safe from any thread.
 */
public interface Terrain {
    /** The top block's world y at a column (the water's surface where there is water). */
    int top(int x, int z);

    /** How deep the water is over the ground at a column (0 on dry land). */
    int water(int x, int z);

    /** The land's family at a column ({@code dev.awaken.city.VillageStyles} TEMPERATE .. CHERRY), and whether it is sea or a river. */
    int family(int x, int z);

    /** The sea level (water below it on open ground is the sea). */
    default int seaLevel() {
        return 63;
    }
}
