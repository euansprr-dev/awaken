package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.core.api.TheLull;
import dev.awaken.village.world.SettlementGrid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The holdfasts (SPEC §43.2, JOURNEY part 1): the few walled towns that kept their bell ringing and so kept their people.
 * Pure from the seed, so an old world's towns are holdfasts or empty the same way. The first is the lowest-rolled town
 * region whose heart lies {@link #CLEAR} to {@link #FIRST_OUT} blocks past the ridge and whose land takes a town (found
 * at the server's start, like the first town). Then in each {@link #SQUARE}-block square round the centre, the
 * lowest-rolled town region whose heart is {@link #CLEAR} or more past the ridge, unless it lies within
 * {@link #FROM_FIRST} of the first or within {@link #FROM_NEIGHBOUR} of a neighbouring square's lower-rolled pick. A pick
 * whose land takes no town gives its square none.
 */
public final class Holdfasts {
    private Holdfasts() {}

    public static final int CLEAR = 1500, FIRST_OUT = 2500, SQUARE = 3000, FROM_FIRST = 2000, FROM_NEIGHBOUR = 1500;
    private static final long SALT = 0x401D_FA57L;

    private static final Map<Long, Boolean> HELD = new ConcurrentHashMap<>();

    static void clear() {
        HELD.clear();
    }

    /** The ridge's radius in a roads' world (Carillon's, or a classic world's). */
    static int ridge(RoadWorld w) {
        return w.city ? TheLull.RIDGE_CITY : TheLull.RIDGE_CLASSIC;
    }

    static double roll(RoadWorld w, int gx, int gz) {
        return w.regions.roll(gx, gz, SALT);
    }

    private static double out(RoadWorld w, int[] heart) {
        return Math.hypot(heart[0] - w.cx, heart[1] - w.cz);
    }

    /** The first holdfast's region cell, or null (no town region in the ring takes a town). Server start only. */
    static int @Nullable [] findFirst(RoadWorld w) {
        long t0 = System.currentTimeMillis();
        int lo = ridge(w) + CLEAR, hi = ridge(w) + FIRST_OUT, pitch = w.regions.pitch();
        List<int[]> cands = new ArrayList<>();
        for (int i = Math.floorDiv(w.cx - hi, pitch) - 1; i <= Math.floorDiv(w.cx + hi, pitch) + 1; i++)
            for (int j = Math.floorDiv(w.cz - hi, pitch) - 1; j <= Math.floorDiv(w.cz + hi, pitch) + 1; j++) {
                double d = out(w, w.regions.heart(i, j));
                if (d >= lo && d <= hi && RoadNet.townCell(w, i, j)) cands.add(new int[]{i, j});
            }
        cands.sort((a, b) -> Double.compare(roll(w, a[0], a[1]), roll(w, b[0], b[1])));
        for (int[] c : cands) {
            if (RoadNet.sites(w, c[0], c[1])[0] != null) {
                Awaken.LOG.info("[road] the first holdfast: region {},{} ({} candidates, {} ms)", c[0], c[1], cands.size(), System.currentTimeMillis() - t0);
                return c;
            }
        }
        return null;
    }

    /** A square's pick: its lowest-rolled town region whose heart lies {@link #CLEAR} or more past the ridge, or null. */
    static int @Nullable [] pick(RoadWorld w, int sx, int sz) {
        int x0 = w.cx + sx * SQUARE, z0 = w.cz + sz * SQUARE, pitch = w.regions.pitch(), min = ridge(w) + CLEAR;
        int[] best = null;
        double low = 2;
        for (int i = Math.floorDiv(x0, pitch) - 1; i <= Math.floorDiv(x0 + SQUARE, pitch) + 1; i++)
            for (int j = Math.floorDiv(z0, pitch) - 1; j <= Math.floorDiv(z0 + SQUARE, pitch) + 1; j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.floorDiv(h[0] - w.cx, SQUARE) != sx || Math.floorDiv(h[1] - w.cz, SQUARE) != sz) continue;
                if (out(w, h) < min || !RoadNet.townCell(w, i, j)) continue;
                double r = roll(w, i, j);
                if (r < low) {
                    low = r;
                    best = new int[]{i, j};
                }
            }
        return best;
    }

    /** Whether region cell (gx, gz)'s town is a holdfast (its land may still take no town: then there's none). */
    public static boolean is(RoadWorld w, int gx, int gz) {
        int[] f = w.firstHoldfast;
        if (f != null && f[0] == gx && f[1] == gz) return true;
        return HELD.computeIfAbsent(RoadNet.cellKey(gx, gz), k -> held(w, gx, gz));
    }

    private static boolean held(RoadWorld w, int gx, int gz) {
        int[] h = w.regions.heart(gx, gz);
        int sx = Math.floorDiv(h[0] - w.cx, SQUARE), sz = Math.floorDiv(h[1] - w.cz, SQUARE);
        int[] p = pick(w, sx, sz);
        if (p == null || p[0] != gx || p[1] != gz) return false;
        int[] f = w.firstHoldfast;
        if (f != null) {
            int[] fh = w.regions.heart(f[0], f[1]);
            if (Math.hypot(fh[0] - h[0], fh[1] - h[1]) < FROM_FIRST) return false;
        }
        double r = roll(w, gx, gz);
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                int[] q = pick(w, sx + a, sz + b);
                if (q == null || roll(w, q[0], q[1]) >= r) continue;
                int[] qh = w.regions.heart(q[0], q[1]);
                if (Math.hypot(qh[0] - h[0], qh[1] - h[1]) < FROM_NEIGHBOUR) return false;
            }
        return true;
    }

    /** Whether a settlement's key names a holdfast's town ("t" gx "_" gz). */
    public static boolean isKey(String key) {
        RoadWorld w = RoadWorld.get();
        int u = key.indexOf('_');
        if (w == null || !key.startsWith("t") || u < 2) return false;
        try {
            return is(w, Integer.parseInt(key.substring(1, u)), Integer.parseInt(key.substring(u + 1)));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * The holdfasts' towns within a radius of a point. With {@code wait} false (the server thread's ticks) only those
     * whose sites are found already answer; the others are found on the road planners for a later look.
     */
    public static List<SettlementGrid.Anchor> near(int x, int z, int radius, boolean wait) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return List.of();
        List<SettlementGrid.Anchor> found = new ArrayList<>(1);
        int pitch = w.regions.pitch(), look = radius + 220;
        for (int i = Math.floorDiv(x - look, pitch) - 1; i <= Math.floorDiv(x + look, pitch) + 1; i++)
            for (int j = Math.floorDiv(z - look, pitch) - 1; j <= Math.floorDiv(z + look, pitch) + 1; j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.hypot(h[0] - x, h[1] - z) > look || !is(w, i, j)) continue;
                if (!wait && !RoadNet.sitesKnown(i, j)) {
                    RoadNet.sitesSoon(w, i, j);
                    continue;
                }
                SettlementGrid.Anchor a = RoadNet.sites(w, i, j)[0];
                if (a != null && Math.hypot(a.x() - x, a.z() - z) <= radius) found.add(a);
            }
        return found;
    }

    /** The first holdfast's town, or null (none found, or no roads' world). */
    public static SettlementGrid.@Nullable Anchor first() {
        RoadWorld w = RoadWorld.get();
        if (w == null || w.firstHoldfast == null) return null;
        return RoadNet.sites(w, w.firstHoldfast[0], w.firstHoldfast[1])[0];
    }

    /** Tests: the region cells within a square of cells round the centre that hold a holdfast's town. */
    public static List<int[]> within(RoadWorld w, int cells) {
        List<int[]> out = new ArrayList<>();
        int g0 = Math.floorDiv(w.cx, w.regions.pitch()), h0 = Math.floorDiv(w.cz, w.regions.pitch());
        for (int i = g0 - cells; i <= g0 + cells; i++)
            for (int j = h0 - cells; j <= h0 + cells; j++)
                if (RoadNet.townCell(w, i, j) && is(w, i, j)) out.add(new int[]{i, j});
        return out;
    }

    /** Tests: the first holdfast's cell in a roads' world. */
    public static int @Nullable [] firstCell(RoadWorld w) {
        return w.firstHoldfast;
    }
}
