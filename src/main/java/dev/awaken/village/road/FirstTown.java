package dev.awaken.village.road;

import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.TownPlanner;
import dev.awaken.village.world.SettlementGrid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The first town a player meets (SPEC §28.9, PLAN 10.2: "a town over the pass"): placed on purpose, like the first
 * hamlet, in the region the road north runs to past the first hamlet. Of the regions the north road could reach (nearest
 * north first, then round to east and west), the first whose land takes a town, preferring one with a rise between the
 * first hamlet and it (the road crests a ridge and the town lies below: its pass carries a cairn). Found at the server's
 * start, before any chunk generates.
 */
final class FirstTown {
    private FirstTown() {}

    static boolean is(RoadWorld w, int gx, int gz) {
        int[] c = w.firstTown;
        return c != null && c[0] == gx && c[1] == gz;
    }

    static int @Nullable [] find(RoadWorld w) {
        if (!w.city) return null;
        Terrain t = w.terrain();
        SettlementGrid.Anchor f = SettlementGrid.first(w.seed, new int[]{w.cx, w.cz});
        List<int[]> cands = new ArrayList<>();
        int pitch = dev.awaken.living.region.RegionMap.PITCH;
        int g0 = Math.floorDiv(w.cx, pitch), h0 = Math.floorDiv(w.cz, pitch);
        for (int i = g0 - 3; i <= g0 + 3; i++)
            for (int j = h0 - 3; j <= h0 + 3; j++) {
                if (w.regions.swallowed(i, j)) continue;
                int[] h = w.regions.heart(i, j);
                double dx = h[0] - f.x(), dz = h[1] - f.z(), d = Math.hypot(dx, dz);
                // ahead of the first hamlet, northward (within ninety degrees), not on top of it
                if (d < 420 || d > 1700 || -dz / d < 0.0) continue;
                cands.add(new int[]{i, j, (int) (d * (2.0 - (-dz / d)))});
            }
        cands.sort((u, v) -> Integer.compare(u[2], v[2]));
        int[] fallback = null;
        for (int[] c : cands) {
            int[] h = w.regions.heart(c[0], c[1]);
            int[] site = TownPlanner.site(t, h[0], h[1], (x, z) -> dev.awaken.terrain.api.CityBasin.reservedWithin(x, z, 140));
            if (site == null) continue;
            if (fallback == null) fallback = new int[]{c[0], c[1]};
            if (crest(t, f.x(), f.z(), site[0], site[1])) return new int[]{c[0], c[1]};
        }
        return fallback;
    }

    /** Whether the straight way from a to b rises well above both ends somewhere in its last two thirds. */
    static boolean crest(Terrain t, int ax, int az, int bx, int bz) {
        int ya = t.top(ax, az), yb = t.top(bx, bz);
        int top = Integer.MIN_VALUE;
        for (int k = 8; k <= 26; k++) {
            double s = k / 30.0;
            top = Math.max(top, t.top((int) (ax + (bx - ax) * s), (int) (az + (bz - az) * s)));
        }
        return top >= Math.max(ya, yb) + 10;
    }
}
