package dev.awaken.finds.api;

import dev.awaken.finds.Sites;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the monuments stand, for whatever is laid across the land (SPEC §64.9): a road or a yard that would cross a
 * monument's ground asks here first and keeps off it. A pure function of the seed, safe from any thread, and it makes
 * no land. The first ask of a stretch of country costs the search for the spots of the monuments in it (up to about two
 * seconds for each one that stands there, once in a session), so a planner asks from its own thread.
 */
public final class Monuments {
    private Monuments() {}

    /**
     * Whether (x, z) lies on a monument's ground or within {@code margin} blocks of it: its terrace, the land eased
     * round it and what it clears, which is everything within its reach.
     */
    public static boolean near(ServerLevel level, int x, int z, int margin) {
        return Sites.monumentNear(level, x, z, margin);
    }
}
