package dev.awaken.village;

import dev.awaken.city.VillageStyles;
import dev.awaken.village.plan.Terrain;

/**
 * A made-up valley for tests and the studio (no generator needed): rolling ground round {@code base} with a river
 * winding across the south a few blocks under it, so a village planned on it has a mill and a dock to make. Centred on
 * (ox, oz), so a GameTest can lay it under its own floor.
 */
public record TestLand(long seed, int family, int base, int ox, int oz) implements Terrain {
    public TestLand(long seed) {
        this(seed, VillageStyles.TEMPERATE, 72, 0, 0);
    }

    /** The same valley with its middle at (x, base, z) (a GameTest's own floor). */
    public static TestLand at(long seed, int x, int base, int z) {
        return new TestLand(seed, VillageStyles.TEMPERATE, base, x, z);
    }

    /** Tests: a settlement planned on this valley round its middle (null if none fits, which it always does). */
    public dev.awaken.village.plan.@org.jspecify.annotations.Nullable VillagePlan plan(String key, long planSeed, boolean village) {
        return dev.awaken.village.plan.VillagePlanner.plan(new dev.awaken.village.plan.VillagePlanner.Request(key, planSeed, ox, oz,
                village ? dev.awaken.village.plan.VillagePlan.Kind.VILLAGE : dev.awaken.village.plan.VillagePlan.Kind.HAMLET, !village,
                ox - 124, oz - 124, ox + 139, oz + 139, (x, z) -> false), this);
    }

    double river(int x) {
        return 74 + 16 * Math.sin(x / 47.0 + (seed & 7));
    }

    @Override
    public int top(int wx, int wz) {
        int x = wx - ox, z = wz - oz;
        double d = Math.abs(z - river(x));
        if (d < 5) return base - 9;
        double h = 72 + 4.5 * Math.sin(x / 41.0 + (seed % 5)) * Math.cos(z / 57.0) + 2.5 * Math.sin((x - z) / 29.0) + 1.2 * Math.cos(x / 13.0 + z / 17.0);
        if (d < 14) h = 64 + (h - 64) * (d - 5) / 9.0;
        return (int) Math.round(h) + base - 72;
    }

    @Override
    public int water(int wx, int wz) {
        return Math.abs(wz - oz - river(wx - ox)) < 5 ? 3 : 0;
    }

    @Override
    public int family(int x, int z) {
        return family;
    }

    @Override
    public int seaLevel() {
        return base - 9;
    }
}
