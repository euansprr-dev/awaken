package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.worlds.GateWorld;

/**
 * One kind of world (SPEC §38, "Engines": halls, caves, drops and climbs, voids, open lands, folds). A pure sculptor:
 * {@link #plan} turns a recipe, a mood and a seed into a floor (places, paths, set-piece sites, light, the place map,
 * reachability) and returns its {@link WorldModel}, which carves chunks as they load. Deterministic and thread-safe.
 */
public interface WorldEngine {
    /** What an engine plans from: the gate, the floor, its plot, the recipe and mood, and which variation (0..2) to try. */
    record Ctx(GatePlan plan, Theme theme, int area, int floor, boolean bossFloor, Plots.Plot plot, String worldId, GateWorld world,
               String mood, long seed, int variation) {}

    /**
     * Plans the floor, or null when this variation can't make a floor that's complete on foot (every place, key, cache
     * and the end reachable from the arrival): the next variation is tried, and after three the floor is today's rooms.
     */
    FloorLayout plan(Ctx ctx);

    /** Why the last plan on this thread gave no floor (for the log and the tests). */
    ThreadLocal<String> WHY = ThreadLocal.withInitial(() -> "");

    /** Notes why a plan gives up and returns null. */
    static <T> T fail(String why) {
        WHY.set(why);
        return null;
    }

    /** The engine for a recipe kind, or null for "rooms" and kinds this build doesn't have (both build today's rooms). */
    static WorldEngine of(String kind) {
        return switch (kind) {
            case "hall" -> new HallEngine();
            case "passages" -> new PassageEngine();
            case "void" -> new VoidEngine();
            case "drop" -> new ShaftEngine();
            case "caves" -> new CaveEngine();
            default -> null;
        };
    }
}
