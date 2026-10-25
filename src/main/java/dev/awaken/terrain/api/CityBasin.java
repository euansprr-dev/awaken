package dev.awaken.terrain.api;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The city basin at spawn (Carillon; docs/SPEC.md §15 "City basin"). The city asks for it once at mod init with
 * {@link #request}; terrain then picks the site from the world seed before any chunk generates, flattens the ground
 * there through the {@code awaken:city_basin} density function (new worlds with the lands pack and Lithostitched),
 * and keeps lairs, ruins and hero trees out. The site is the same for the same seed on every start.
 */
public final class CityBasin {
    private CityBasin() {}

    /** Street level inside the core. */
    public static final int STREET_Y = 70;
    /** Flat ground reaches this far from the centre (the wall is about 240 out). */
    public static final int CORE_RADIUS = 330;
    /** Back to natural terrain by here. */
    public static final int BLEND_RADIUS = 700;
    /** Structures and features keep this far outside the core. */
    public static final int MARGIN = 48;
    /** The river's half width at street level and the channel floor; water fills to sea level. */
    public static final int RIVER_HALF_WIDTH = 9;
    public static final int RIVER_FLOOR_Y = 57;
    /**
     * The city's river through the core, as (dx, dz) from the centre, north (−z) to south-west. Carillon is one designed
     * city, so this is fixed, never per seed; the basin continues it outward to the blend edge.
     */
    public static final List<int[]> RIVER = List.of(
            new int[]{-95, -330}, new int[]{-92, -240}, new int[]{-78, -170}, new int[]{-70, -110}, new int[]{-74, -50},
            new int[]{-72, -10}, new int[]{-66, 40}, new int[]{-80, 100}, new int[]{-110, 150}, new int[]{-150, 200},
            new int[]{-190, 260}, new int[]{-215, 320});

    private static volatile Predicate<MinecraftServer> enabled = s -> false;
    private static volatile CitySite current;

    /** The city's rule for whether a world gets a city (e.g. its feature flag). Call once at mod init. */
    public static void request(Predicate<MinecraftServer> rule) {
        enabled = rule;
    }

    /** The chosen site, or empty when this world has no city. */
    public static Optional<CitySite> site(MinecraftServer server) {
        return Optional.ofNullable(current);
    }

    /** Whether (x, z) is inside the city reserve (the core plus {@link #MARGIN}). Thread-safe, allocation-free. */
    public static boolean reserved(int x, int z) {
        CitySite s = current;
        return s != null && s.within(x, z, MARGIN);
    }

    /**
     * Whether any point within {@code reach} blocks of (x, z) lies in the reserve: a structure whose blocks all fall
     * inside that circle (a hero tree's crown, a landmark's rim) may not stand here. The test is the circle's, so it
     * holds in every direction; probing a few points on its edge misses the reserve between them.
     */
    public static boolean reservedWithin(int x, int z, int reach) {
        CitySite s = current;
        return s != null && s.within(x, z, MARGIN + Math.max(0, reach));
    }

    // --- terrain internals ---

    /** The current site for terrain's own code (the density function, structures); null when none. */
    public static CitySite current() {
        return current;
    }

    /** Terrain only: whether the city wants a site in this world. */
    public static boolean wanted(MinecraftServer server) {
        return enabled.test(server);
    }

    /** Terrain only: set before the levels exist, cleared when the server stops. */
    public static void set(CitySite site) {
        current = site;
    }
}
