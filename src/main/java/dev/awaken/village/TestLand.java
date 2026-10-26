package dev.awaken.village;

import dev.awaken.city.VillageStyles;
import dev.awaken.village.plan.Terrain;

/**
 * A made-up valley for tests and the studio (no generator needed): rolling ground round 72 with a river winding across
 * the south at the sea's level, so a village planned on it has a mill and a dock to make.
 */
record TestLand(long seed, int family) implements Terrain {
    TestLand(long seed) {
        this(seed, VillageStyles.TEMPERATE);
    }

    double river(int x) {
        return 74 + 16 * Math.sin(x / 47.0 + (seed & 7));
    }

    @Override
    public int top(int x, int z) {
        double d = Math.abs(z - river(x));
        if (d < 5) return 63;
        double h = 72 + 4.5 * Math.sin(x / 41.0 + (seed % 5)) * Math.cos(z / 57.0) + 2.5 * Math.sin((x - z) / 29.0) + 1.2 * Math.cos(x / 13.0 + z / 17.0);
        if (d < 14) h = 64 + (h - 64) * (d - 5) / 9.0;
        return (int) Math.round(h);
    }

    @Override
    public int water(int x, int z) {
        return Math.abs(z - river(x)) < 5 ? 3 : 0;
    }

    @Override
    public int family(int x, int z) {
        return family;
    }
}
