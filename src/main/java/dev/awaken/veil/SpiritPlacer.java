package dev.awaken.veil;

import java.util.List;

/**
 * Where the dead stand. The world is cut into 96-block cells; half of them are haunted. A haunted cell has one fixed spot
 * (from the world seed), and whoever's biome matches that spot stands there. Pure functions: the same world always puts
 * the same spirit in the same place.
 */
public final class SpiritPlacer {
    private SpiritPlacer() {}

    public static final int CELL = 96;
    private static final int MARGIN = 12;

    public static long mix(long seed, int cx, int cz, int salt) {
        long h = seed ^ 0x9E3779B97F4A7C15L * (salt + 1);
        h ^= (long) cx * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) cz * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    public static boolean haunted(long seed, int cx, int cz) {
        return (mix(seed, cx, cz, 1) & 1L) == 0L;
    }

    /** Block x and z of the cell's spot. */
    public static int[] spot(long seed, int cx, int cz) {
        long h = mix(seed, cx, cz, 2);
        int span = CELL - 2 * MARGIN;
        int ox = (int) Math.floorMod(h, (long) span);
        int oz = (int) Math.floorMod(h >>> 20, (long) span);
        return new int[]{cx * CELL + MARGIN + ox, cz * CELL + MARGIN + oz};
    }

    /** Picks one spirit among those whose biome matches (candidates must be in a stable order). */
    public static String choose(long seed, int cx, int cz, List<String> candidates) {
        if (candidates.isEmpty()) return null;
        int i = (int) Math.floorMod(mix(seed, cx, cz, 3), (long) candidates.size());
        return candidates.get(i);
    }

    public static int cellOf(double blockCoord) {
        return Math.floorDiv((int) Math.floor(blockCoord), CELL);
    }
}
