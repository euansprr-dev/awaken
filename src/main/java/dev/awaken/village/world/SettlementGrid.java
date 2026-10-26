package dev.awaken.village.world;

import dev.awaken.terrain.api.CitySite;
import dev.awaken.village.plan.VillagePlan.Kind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where settlements are meant to be (SPEC §28.6, PLAN 5.2): pure arithmetic on the world seed, no land read. The world
 * is cut into cells of {@link #PITCH} blocks (about two to a region of the region map's 900): each cell holds one anchor,
 * a hamlet two times in three, a village one time in four, nothing in the rest; none inside the Commons (the city and
 * its first ground). One more is placed on purpose: <b>the first hamlet</b>, 800-900 blocks up the road north of
 * Carillon past the Wayfarer camp (450 up it), so a new player's first hour shows the city, a camp, then people living
 * outside the walls. The planner moves each anchor up to 32 blocks to the best ground and drops it where nothing suits.
 */
public final class SettlementGrid {
    private SettlementGrid() {}

    public static final int PITCH = 640;
    /** No grid settlement this near the centre (the Commons is 700; the city's own fields and the camp road). */
    static final int COMMONS = 760;
    /** No grid settlement this near the first hamlet. */
    static final int FIRST_CLEAR = 440;
    public static final int FIRST_MIN = 840, FIRST_SPREAD = 60;

    public record Anchor(String key, int x, int z, Kind kind, boolean first, long seed) {}

    /** The world's centre: Carillon's site, else the dimension's origin (a classic world's spawn is within a few chunks of it). */
    public static int[] centre() {
        CitySite s = dev.awaken.terrain.api.CityBasin.current();
        return s == null ? new int[]{0, 0} : new int[]{s.x(), s.z()};
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The first hamlet's anchor: up the road north of the centre. */
    public static Anchor first(long worldSeed, int[] centre) {
        long h = mix(worldSeed ^ 0xF125_7A41E7L);
        int x = centre[0] + 6 + (int) Math.round((unit(h) - 0.5) * 40);
        int z = centre[1] - FIRST_MIN - (int) Math.round(unit(mix(h)) * FIRST_SPREAD);
        return new Anchor("first", x, z, Kind.HAMLET, true, mix(h + 17));
    }

    /** The anchor of grid cell (i, j), or null where the cell holds none. */
    public static @Nullable Anchor cell(long worldSeed, int i, int j, int[] centre) {
        long h = mix(worldSeed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ 0x5E771EL);
        double roll = unit(h);
        if (roll < 0.10) return null;
        Kind kind = roll < 0.35 ? Kind.VILLAGE : Kind.HAMLET;
        int x = i * PITCH + (int) Math.round(PITCH * (0.18 + 0.64 * unit(mix(h + 1))));
        int z = j * PITCH + (int) Math.round(PITCH * (0.18 + 0.64 * unit(mix(h + 2))));
        double dc = Math.hypot(x - centre[0], z - centre[1]);
        if (dc < COMMONS) return null;
        Anchor f = first(worldSeed, centre);
        if (Math.hypot(x - f.x(), z - f.z()) < FIRST_CLEAR) return null;
        return new Anchor("v" + i + "_" + j, x, z, kind, false, mix(h + 3));
    }

    /** The anchor whose chunk this is, or null. */
    public static @Nullable Anchor inChunk(long worldSeed, int chunkX, int chunkZ, int[] centre) {
        Anchor f = first(worldSeed, centre);
        if (f.x() >> 4 == chunkX && f.z() >> 4 == chunkZ) return f;
        int i = Math.floorDiv((chunkX << 4) + 8, PITCH), j = Math.floorDiv((chunkZ << 4) + 8, PITCH);
        Anchor a = cell(worldSeed, i, j, centre);
        return a != null && a.x() >> 4 == chunkX && a.z() >> 4 == chunkZ ? a : null;
    }

    /** Every anchor within a radius of a point (the first hamlet included), nearest first. */
    public static List<Anchor> near(long worldSeed, int x, int z, int radius, int[] centre) {
        List<Anchor> out = new ArrayList<>();
        Anchor f = first(worldSeed, centre);
        if (Math.hypot(f.x() - x, f.z() - z) <= radius) out.add(f);
        for (int i = Math.floorDiv(x - radius, PITCH); i <= Math.floorDiv(x + radius, PITCH); i++)
            for (int j = Math.floorDiv(z - radius, PITCH); j <= Math.floorDiv(z + radius, PITCH); j++) {
                Anchor a = cell(worldSeed, i, j, centre);
                if (a != null && Math.hypot(a.x() - x, a.z() - z) <= radius) out.add(a);
            }
        out.sort((a, b) -> Double.compare(Math.hypot(a.x() - x, a.z() - z), Math.hypot(b.x() - x, b.z() - z)));
        return out;
    }
}
