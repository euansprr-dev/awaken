package dev.awaken.village.road;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a road's waymarkers stand and what their arms say (SPEC §28.9): a post beside the road a little way out of every
 * settlement (the next place on, and the place behind), one at every fork where a lane meets a road (both ways along
 * the road and up the lane), and a cairn at the crest where a town first comes into view. Arms point out of the post's
 * sides toward each way; two that would share a side take the nearest free one.
 */
final class Waymarks {
    private Waymarks() {}

    /** How far out of a settlement the post stands (points). */
    static final int OUT = 4;

    /** The side (0 N, 1 E, 2 S, 3 W) a direction points out of. */
    static int side(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
    }

    /** The road's direction at point i (unit x, z). */
    static float[] tangent(Road r, int i) {
        int a = Math.max(0, i - 1), b = Math.min(r.points() - 1, i + 1);
        float dx = r.x()[b] - r.x()[a], dz = r.z()[b] - r.z()[a];
        float l = (float) Math.max(1e-3, Math.hypot(dx, dz));
        return new float[]{dx / l, dz / l};
    }

    /** Arms on free sides: each wants a direction; a taken side passes it to the free side nearest its direction. */
    static List<Road.Arm> arms(double[][] dirs, String[] names, int[] dist) {
        boolean[] taken = new boolean[4];
        List<Road.Arm> out = new ArrayList<>();
        for (int k = 0; k < dirs.length; k++) {
            if (names[k] == null || names[k].isEmpty()) continue;
            double ang = Math.atan2(dirs[k][1], dirs[k][0]);
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int s = 0; s < 4; s++) {
                if (taken[s]) continue;
                double sa = Math.atan2(s == 2 ? 1 : s == 0 ? -1 : 0, s == 1 ? 1 : s == 3 ? -1 : 0);
                double d = Math.abs(Math.atan2(Math.sin(ang - sa), Math.cos(ang - sa)));
                if (d < bd) {
                    bd = d;
                    best = s;
                }
            }
            if (best < 0) break;
            taken[best] = true;
            out.add(new Road.Arm(best, names[k], dist[k]));
        }
        return out;
    }

    /** A post on the right of the way at point i, {@code off} out from the centre line. */
    static int[] beside(Road r, int i, double off, int sign) {
        float[] t = tangent(r, i);
        double nx = -t[1] * sign, nz = t[0] * sign;
        return new int[]{(int) Math.floor(r.x()[i] + nx * off), (int) Math.floor(r.z()[i] + nz * off)};
    }

    /** Leaving (or coming into) a settlement: the next place on, and this place, on a post on the road's right. */
    static Road.@Nullable Marker leaving(Road r, boolean atStart) {
        int n = r.points();
        if (n < OUT * 3) return null;
        int i = atStart ? OUT : n - 1 - OUT;
        float[] t = tangent(r, i);
        int[] at = beside(r, i, r.halfWidth() + 2.0, atStart ? 1 : -1);
        String ahead = atStart ? r.to().name() : r.from().name(), behind = atStart ? r.from().name() : r.to().name();
        int far = (int) Math.round(atStart ? r.between(i, n - 1) : r.between(0, i));
        double s = atStart ? 1 : -1;
        List<Road.Arm> arms = arms(new double[][]{{t[0] * s, t[1] * s}, {-t[0] * s, -t[1] * s}}, new String[]{ahead, behind}, new int[]{far, 0});
        return new Road.Marker(i, at[0], r.y()[i], at[1], arms, false);
    }

    /**
     * Where a lane meets a road: a post at the corner past the lane's mouth, its arms both ways along the road (to the
     * places at its ends) and up the lane.
     */
    static Road.@Nullable Marker fork(Road lane, Road road, int bi) {
        float[] t = tangent(road, bi);
        int last = lane.points() - 1;
        // the lane comes in from this side of the road
        float lx = lane.x()[Math.max(0, last - 3)] - road.x()[bi], lz = lane.z()[Math.max(0, last - 3)] - road.z()[bi];
        int sign = -t[1] * lx + t[0] * lz >= 0 ? 1 : -1;
        double nx = -t[1] * sign, nz = t[0] * sign;
        double off = road.halfWidth() + 2.0;
        int x = (int) Math.floor(road.x()[bi] + nx * off + t[0] * (lane.halfWidth() + 3)), z = (int) Math.floor(road.z()[bi] + nz * off + t[1] * (lane.halfWidth() + 3));
        int back = (int) Math.round(road.between(0, bi)), on = (int) Math.round(road.between(bi, road.points() - 1));
        int up = (int) Math.round(lane.between(0, last));
        List<Road.Arm> arms = arms(new double[][]{{-t[0], -t[1]}, {t[0], t[1]}, {nx, nz}}, new String[]{road.from().name(), road.to().name(), lane.from().name()},
                new int[]{back, on, up});
        return new Road.Marker(last, x, road.y()[bi], z, arms, false);
    }

    /**
     * March posts (SPEC §21.10, decision 11): where a road first passes into land a rank above the land behind it, a post on the
     * walker's right whose one arm names the land and what lives in it ("Black Glades · Lv 10–24"), for a walker from each end
     * (a boundary has to hold for 32 blocks, so one that flickers makes no post; bridges wait for dry ground; clear of the settlements'
     * own posts). The land speaks first: this is the part of it written in the world.
     */
    static List<Road.Marker> marches(RoadWorld w, Road r) {
        if (w == null) return new ArrayList<>(); // (a test's road studio has no world: no land to read)
        return marches(r, (x, z) -> w.regions.levelAt(x, z), (x, z) -> w.regions.regionAt(x, z).name());
    }

    /** As above over any land: its level at a column and the name of the region there. */
    static List<Road.Marker> marches(Road r, java.util.function.IntBinaryOperator levelAt, java.util.function.BiFunction<Integer, Integer, String> nameAt) {
        List<Road.Marker> out = new ArrayList<>();
        int n = r.points();
        if (n < OUT * 6) return out;
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) rank[i] = dev.awaken.living.region.RegionMap.rankOf(levelAt.applyAsInt(Math.round(r.x()[i]), Math.round(r.z()[i])));
        for (int dir = 1; dir >= -1; dir -= 2) {
            int start = dir > 0 ? OUT * 2 : n - 1 - OUT * 2, end = dir > 0 ? n - 1 - OUT * 2 : OUT * 2;
            int cur = rank[start];
            for (int i = start; i != end; i += dir) {
                if (rank[i] <= cur) continue;
                boolean holds = true;
                for (int k = 1; k <= 8 && holds; k++) {
                    int j = i + k * dir;
                    if (dir > 0 ? j > end : j < end) break;
                    if (rank[j] < rank[i]) holds = false;
                }
                if (!holds || (r.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK | RoadPlanner.VIADUCT)) != 0) continue;
                cur = rank[i];
                float[] t = tangent(r, i);
                int[] at = beside(r, i, r.halfWidth() + 2.0, dir);
                String land = nameAt.apply(Math.round(r.x()[i]), Math.round(r.z()[i]));
                List<Road.Arm> arms = arms(new double[][]{{t[0] * dir, t[1] * dir}}, new String[]{land + " · Lv " + rankRange(rank[i])}, new int[]{0});
                out.add(new Road.Marker(i, at[0], r.y()[i], at[1], arms, false));
            }
        }
        return out;
    }

    /** A land rank's levels as a post writes them: "10–24", "100+". */
    static String rankRange(int rank) {
        int[] from = dev.awaken.living.region.RegionMap.RANK_FROM;
        return rank + 1 < from.length ? from[rank] + "–" + (from[rank + 1] - 1) : from[rank] + "+";
    }

    /**
     * The pass: on a road to (or from) a town, the crest of the last rise before it, well above the town and at least 120
     * blocks out, where it first comes into view. A cairn and a post.
     */
    static Road.@Nullable Marker pass(RoadWorld w, Road r) {
        boolean toTown = r.to().town(), fromTown = r.from().town();
        if (!toTown && !fromTown) return null;
        int n = r.points();
        int townY = toTown ? r.y()[n - 1] : r.y()[0];
        int best = -1, by = Integer.MIN_VALUE;
        for (int k = 30; k < Math.min(n - 25, 150); k++) {
            int i = toTown ? n - 1 - k : k;
            if ((r.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK | RoadPlanner.VIADUCT)) != 0) continue;
            if (r.y()[i] > by) {
                by = r.y()[i];
                best = i;
            }
        }
        if (best < 0 || by < townY + 8) return null;
        // a crest: lower on both sides
        int lo = Math.max(0, best - 25), hi = Math.min(n - 1, best + 25);
        if (r.y()[lo] > by - 4 || r.y()[hi] > by - 4) return null;
        float[] t = tangent(r, best);
        int[] at = beside(r, best, r.halfWidth() + 2.5, 1);
        int toward = (int) Math.round(toTown ? r.between(best, n - 1) : r.between(0, best));
        int away = (int) Math.round(toTown ? r.between(0, best) : r.between(best, n - 1));
        String town = toTown ? r.to().name() : r.from().name(), other = toTown ? r.from().name() : r.to().name();
        double s = toTown ? 1 : -1;
        List<Road.Arm> arms = arms(new double[][]{{t[0] * s, t[1] * s}, {-t[0] * s, -t[1] * s}}, new String[]{town, other}, new int[]{toward, away});
        return new Road.Marker(best, at[0], r.y()[best], at[1], arms, true);
    }
}
