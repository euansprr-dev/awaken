package dev.awaken.village.road;

import dev.awaken.terrain.api.CityBasin;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How each road is laid (SPEC §28.9): a highroad between two regions' hubs through the settlements on its way, Carillon's
 * roads out, a settlement's lane to the nearest road; and what each one carries: waymarkers with the next place's name
 * where it leaves a settlement, where a lane meets a road, at a bare crossroads and at the pass where a town comes into
 * view, and lanterns along the last stretch into a settlement.
 */
final class Highroads {
    private Highroads() {}

    /** A lane reaches this far for a road to join. */
    static final int LANE_REACH = 650;
    /** Lanterns light this many points (four blocks each) of a road out of a settlement. */
    static final int LIT = 18;
    /** The Wayfarer camp's fire, from the centre (the city trail ends at its footpath); the road north starts past it. */
    static final int CAMP_X = 6, CAMP_Z = -450, CAMP_CLEAR = 26, NORTH_START = -474;
    /** Carillon's roads east and south end at its painter's reach (326): ours start a few blocks on. */
    static final int PORT_OUT = 336;

    // ---- the pre-filter ------------------------------------------------------------------------------------------------

    /** Whether a road between two points could come within {@code reach} of (x, z): its search keeps inside an ellipse. */
    static boolean mayPass(int ax, int az, int bx, int bz, int x, int z, int reach) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 == 0 ? 0 : Math.clamp(((x - ax) * dx + (z - az) * dz) / l2, 0, 1);
        double d = Math.hypot(ax + dx * t - x, az + dz * t - z);
        return d <= 0.36 * Math.sqrt(l2) + 260 + reach;
    }

    /** Whether (x, z) is within {@code reach} of the segment a-b. */
    static boolean corridor(double ax, double az, double bx, double bz, double x, double z, double reach) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 == 0 ? 0 : Math.clamp(((x - ax) * dx + (z - az) * dz) / l2, 0, 1);
        return Math.hypot(ax + dx * t - x, az + dz * t - z) <= reach;
    }

    // ---- hubs as ends --------------------------------------------------------------------------------------------------

    /** What a road's end at a hub is: a street's end toward the other end, or the crossroads itself. */
    static RoadNet.@Nullable Entry hubEnd(RoadWorld w, RoadNet.Hub h, double tx, double tz) {
        Terrain t = w.terrain();
        if (h.anchor() != null) {
            VillagePlan p = Plans.of(h.anchor(), t);
            if (p != null) return RoadNet.entryToward(p, tx, tz);
        }
        if (h.kind() == RoadNet.HubKind.NONE) return null;
        return new RoadNet.Entry(h.x(), h.z(), t.top(h.x(), h.z()), 0, 0, h.region(), "", Road.CROSSROADS, -1);
    }

    static String nameOf(RoadWorld w, RoadNet.Hub h) {
        if (h.anchor() != null) {
            VillagePlan p = Plans.cached(h.anchor().key());
            if (p != null) return p.name;
        }
        return h.region();
    }

    // ---- highroads -----------------------------------------------------------------------------------------------------

    static List<Road> trunk(RoadWorld w, int gx, int gz, int dir) {
        RoadNet.Hub a = RoadNet.hub(w, gx, gz), b = RoadNet.hub(w, gx + (dir == 0 ? 1 : 0), gz + (dir == 1 ? 1 : 0));
        if (a.kind() == RoadNet.HubKind.NONE || b.kind() == RoadNet.HubKind.NONE) return List.of();
        String key = "T" + gx + "," + gz + "," + dir;
        return through(w, key, Road.HIGHROAD, a, b, null);
    }

    /**
     * A highroad from hub a to hub b through the settlements on its way (each entered at one end of its main street and
     * left by the other), kept off every other settlement's ground. {@code start}, when given, replaces a's end (a port).
     */
    static List<Road> through(RoadWorld w, String key, int cls, RoadNet.@Nullable Hub a, RoadNet.Hub b, RoadNet.@Nullable Entry start) {
        Terrain t = w.terrain();
        double ax = start != null ? start.x() : a.x(), az = start != null ? start.z() : a.z();
        double d = Math.hypot(b.x() - ax, b.z() - az);
        if (d < 48) return List.of();
        // the settlements near the straight way: those well between the ends ride on it, the rest are kept clear of
        List<VillagePlan> on = new ArrayList<>(), clear = new ArrayList<>();
        List<double[]> along = new ArrayList<>();
        int[] centre = {w.cx, w.cz};
        double mx = (ax + b.x()) / 2, mz = (az + b.z()) / 2;
        for (SettlementGrid.Anchor s : SettlementGrid.gridNear(w.seed, (int) mx, (int) mz, (int) (d / 2 + 0.36 * d + 120), centre)) {
            if (a != null && s.equals(a.anchor()) || s.equals(b.anchor())) continue;
            if (!corridor(ax, az, b.x(), b.z(), s.x(), s.z(), 0.36 * d + 140)) continue;
            VillagePlan p = Plans.of(s, t);
            if (p == null) continue;
            double tt = ((p.ox - ax) * (b.x() - ax) + (p.oz - az) * (b.z() - az)) / (d * d);
            double perp = Math.abs((p.ox - ax) * (b.z() - az) - (p.oz - az) * (b.x() - ax)) / d;
            if (tt > 0.12 && tt < 0.88 && perp < Math.min(0.22 * d, 200)) {
                on.add(p);
                along.add(new double[]{tt, on.size() - 1});
            } else clear.add(p);
        }
        along.sort((u, v) -> Double.compare(u[0], v[0]));
        List<VillagePlan> stops = new ArrayList<>();
        for (double[] u : along) stops.add(on.get((int) u[1]));
        // the hubs' own settlements are kept clear too, but for their ends
        List<VillagePlan> keepOut = new ArrayList<>(clear);
        keepOut.addAll(stops);
        if (a != null && a.anchor() != null && Plans.cached(a.anchor().key()) != null) keepOut.add(Plans.cached(a.anchor().key()));
        if (b.anchor() != null && Plans.cached(b.anchor().key()) != null) keepOut.add(Plans.cached(b.anchor().key()));
        RoadPlanner.Keep keep = keep(w, keepOut, start);
        // the way, stop by stop
        List<Road> out = new ArrayList<>();
        RoadNet.Entry from = start != null ? start : hubEnd(w, a, stops.isEmpty() ? b.x() : stops.getFirst().ox, stops.isEmpty() ? b.z() : stops.getFirst().oz);
        for (int k = 0; k <= stops.size() && from != null; k++) {
            RoadNet.Entry to;
            RoadNet.Entry next = null;
            if (k < stops.size()) {
                VillagePlan s = stops.get(k);
                to = RoadNet.entryToward(s, from.x(), from.z());
                double nx = k + 1 < stops.size() ? stops.get(k + 1).ox : b.x(), nz = k + 1 < stops.size() ? stops.get(k + 1).oz : b.z();
                next = RoadNet.entryToward(s, nx, nz);
                // in and out by the same end: it isn't on the way after all, just near it
                if (to != null && next != null && to.x() == next.x() && to.z() == next.z()) {
                    stops.remove(k);
                    k--;
                    continue;
                }
            } else to = hubEnd(w, b, from.x(), from.z());
            if (to == null) break;
            Road r = lay(w, key + "#" + k, cls, from, to, keep, t);
            if (r != null) out.add(r);
            from = next;
        }
        return out;
    }

    /**
     * The columns a road keeps out of: other settlements' planned ground (its streets, houses, yards, fields and pens, and
     * a few blocks round them), the city's reserve (but a port's own way out), the camp.
     */
    static RoadPlanner.Keep keep(RoadWorld w, List<VillagePlan> around, RoadNet.@Nullable Entry port) {
        int n = around.size();
        VillagePlan[] ps = around.toArray(new VillagePlan[0]);
        int campX = w.cx + CAMP_X, campZ = w.cz + CAMP_Z;
        return (x, z) -> {
            for (int i = 0; i < n; i++) {
                VillagePlan p = ps[i];
                int dx = x - p.ox, dz = z - p.oz;
                if (dx * dx + dz * dz > (p.r + 8) * (p.r + 8)) continue;
                if (built(p, x, z) || built(p, x + 3, z) || built(p, x - 3, z) || built(p, x, z + 3) || built(p, x, z - 3)) return true;
            }
            if (w.city) {
                int dx = x - campX, dz = z - campZ;
                if (dx * dx + dz * dz < CAMP_CLEAR * CAMP_CLEAR) return true;
                if (CityBasin.reservedWithin(x, z, 6)) {
                    // a port's road leaves along its own line out of the reserve
                    return port == null || Math.abs((x - port.x()) * port.dz() - (z - port.z()) * port.dx()) > 40
                            || (x - port.x()) * port.dx() + (z - port.z()) * port.dz() < -8;
                }
            }
            return false;
        };
    }

    /** A settlement's built ground: its houses, streets, square, walls and landmarks (a road may pass its fields and pens). */
    static boolean built(VillagePlan p, int wx, int wz) {
        byte k = p.kind(wx - p.ox, wz - p.oz);
        return k == dev.awaken.city.CityPlan.PLOT || k == dev.awaken.city.CityPlan.STREET || k == dev.awaken.city.CityPlan.LANE
                || k == dev.awaken.city.CityPlan.SQUARE || k == dev.awaken.city.CityPlan.WALL || k == dev.awaken.city.CityPlan.LANDMARK
                || k == VillagePlan.DOCK || k == VillagePlan.KITCHEN || k == dev.awaken.city.CityPlan.GARDEN;
    }

    /** Lays one road between two ends, with its waymarkers and lanterns; null where no way is found. */
    static @Nullable Road lay(RoadWorld w, String key, int cls, RoadNet.Entry from, RoadNet.Entry to, RoadPlanner.Keep keep, Terrain t) {
        RoadPlanner.Path p = RoadPlanner.plan(new RoadPlanner.Request(from.x(), from.z(), from.kind() == Road.CROSSROADS ? Integer.MIN_VALUE : from.y(),
                from.dx(), from.dz(), to.x(), to.z(), to.kind() == Road.CROSSROADS ? Integer.MIN_VALUE : to.y(), to.dx(), to.dz(), keep), t);
        if (p == null && key.startsWith("P0")) {
            // the road north out of the camp is the one every player follows: where no narrower way is found, a long bridge
            RoadNet.log("no narrow way for {} from {} to {} ({}): trying wide water", key, from.name(), to.name(), RoadPlanner.WHY.get());
            p = RoadPlanner.plan(new RoadPlanner.Request(from.x(), from.z(), from.kind() == Road.CROSSROADS ? Integer.MIN_VALUE : from.y(),
                    from.dx(), from.dz(), to.x(), to.z(), to.kind() == Road.CROSSROADS ? Integer.MIN_VALUE : to.y(), to.dx(), to.dz(), keep, true), t);
        }
        if (p == null) {
            RoadNet.log("no way for {} from {} to {} ({})", key, from.name(), to.name(), RoadPlanner.WHY.get());
            return null;
        }
        RoadNet.counted(p.nanos(), p.reads());
        Road.End a = end(from), b = end(to);
        byte[] fam = new byte[p.points()];
        for (int i = 0; i < fam.length; i++) fam[i] = (byte) t.family(Math.round(p.x()[i]), Math.round(p.z()[i]));
        int lf = from.kind() == Road.CROSSROADS || from.kind() == Road.PORT && !key.startsWith("P0") ? 0 : LIT;
        int lt = to.kind() == Road.CROSSROADS ? 0 : LIT;
        Road bare = new Road(key, cls, p.x(), p.z(), p.y(), p.flags(), fam, a, b, List.of(), lf, lt);
        List<Road.Marker> marks = new ArrayList<>();
        // leaving a settlement (or the camp, or Carillon): the next place on, and the place behind you
        if (from.kind() != Road.CROSSROADS) marks.add(Waymarks.leaving(bare, true));
        if (to.kind() != Road.CROSSROADS && to.kind() != Road.JUNCTION) marks.add(Waymarks.leaving(bare, false));
        Road.Marker pass = Waymarks.pass(w, bare);
        if (pass != null) marks.add(pass);
        if (cls == Road.HIGHROAD) marks.addAll(Waymarks.marches(w, bare)); // SPEC §21.10: where a higher land begins
        marks.removeIf(m -> m == null);
        RoadNet.log("{} {} from {} to {}: {} blocks, {} points, {} bridged, in {} ms ({} reads)", cls == Road.HIGHROAD ? "highroad" : "lane", key,
                from.name(), to.name(), (int) bare.between(0, bare.points() - 1), bare.points(), bridged(p.flags()),
                String.format(java.util.Locale.ROOT, "%.0f", p.nanos() / 1e6), p.reads());
        return new Road(key, cls, p.x(), p.z(), p.y(), p.flags(), fam, a, b, marks, lf, lt);
    }

    static Road.End end(RoadNet.Entry e) {
        return new Road.End(e.name(), e.kind(), e.key(), e.x(), e.z(), e.place());
    }

    static int bridged(byte[] f) {
        int n = 0;
        for (byte b : f) if ((b & RoadPlanner.BRIDGE) != 0) n++;
        return n;
    }

    // ---- Carillon's roads out ------------------------------------------------------------------------------------------

    /** The ports' starts (from the centre) and the ways they leave: north past the camp, east, south. */
    static final int[][] PORTS = {{CAMP_X, NORTH_START, 0, -1}, {PORT_OUT, 0, 1, 0}, {0, PORT_OUT, 0, 1}};
    static final String PORT_NAME = "Carillon";

    /** A port's start and the hearts of the regions it may head for (the pre-filter's lines). */
    static List<int[]> portReach(RoadWorld w, int which) {
        List<int[]> out = new ArrayList<>();
        int[] s = PORTS[which];
        for (int[] cell : portCells(w, which)) {
            int[] h = w.regions.heart(cell[0], cell[1]);
            out.add(new int[]{w.cx + s[0], w.cz + s[1], h[0], h[1]});
        }
        return out;
    }

    /** The regions a port's road may run to, nearest first: up to three outside the Commons within sixty degrees of its way out. */
    static List<int[]> portCells(RoadWorld w, int which) {
        int[] s = PORTS[which];
        double sx = w.cx + s[0], sz = w.cz + s[1];
        List<int[]> out = new ArrayList<>();
        int g0 = Math.floorDiv(w.cx, dev.awaken.living.region.RegionMap.PITCH), h0 = Math.floorDiv(w.cz, dev.awaken.living.region.RegionMap.PITCH);
        for (int i = g0 - 3; i <= g0 + 3; i++)
            for (int j = h0 - 3; j <= h0 + 3; j++) {
                if (w.regions.swallowed(i, j)) continue;
                int[] h = w.regions.heart(i, j);
                double dx = h[0] - sx, dz = h[1] - sz, d = Math.hypot(dx, dz);
                if (d < 1 || (dx * s[2] + dz * s[3]) / d < 0.5) continue; // within sixty degrees
                out.add(new int[]{i, j, (int) d});
            }
        out.sort((u, v) -> Integer.compare(u[2], v[2]));
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    static List<Road> port(RoadWorld w, int which) {
        if (!w.city) return List.of();
        int[] s = PORTS[which];
        Terrain t = w.terrain();
        int x = w.cx + s[0], z = w.cz + s[1];
        RoadNet.Entry start = new RoadNet.Entry(x, z, t.top(x, z), s[2], s[3], PORT_NAME, "", Road.PORT, -1);
        List<int[]> cells = portCells(w, which);
        List<Road> out = new ArrayList<>();
        if (which == 0) {
            // north: past the camp to the first hamlet, then on from its far end
            SettlementGrid.Anchor f = SettlementGrid.first(w.seed, new int[]{w.cx, w.cz});
            VillagePlan fp = Plans.of(f, t);
            if (fp != null) {
                RoadNet.Entry in = RoadNet.entryToward(fp, x, z);
                if (in != null) {
                    Road r = lay(w, "P0#0", Road.HIGHROAD, start, in, keep(w, List.of(fp), start), t);
                    if (r != null) out.add(r);
                }
                for (int[] cell : cells) {
                    RoadNet.Hub b = RoadNet.hub(w, cell[0], cell[1]);
                    RoadNet.Entry on = RoadNet.entryToward(fp, b.x(), b.z());
                    if (on == null || b.kind() == RoadNet.HubKind.NONE || in != null && on.x() == in.x() && on.z() == in.z()) continue;
                    List<Road> on2 = through(w, "P0+", Road.HIGHROAD, null, b, on);
                    if (on2.isEmpty()) continue;
                    out.addAll(on2);
                    break;
                }
                return out;
            }
        }
        // the nearest region that way a road reaches
        for (int[] cell : cells) {
            RoadNet.Hub b = RoadNet.hub(w, cell[0], cell[1]);
            if (b.kind() == RoadNet.HubKind.NONE) continue;
            List<Road> rs = through(w, "P" + which, Road.HIGHROAD, null, b, start);
            if (!rs.isEmpty()) return rs;
        }
        return out;
    }

    // ---- lanes ---------------------------------------------------------------------------------------------------------

    static List<Road> branch(RoadWorld w, SettlementGrid.Anchor a) {
        Terrain t = w.terrain();
        VillagePlan p = Plans.of(a, t);
        if (p == null) return List.of();
        // the highroads and Carillon's roads near it: if one runs through it, it needs no lane
        List<Road> roads = new ArrayList<>();
        int pitch = dev.awaken.living.region.RegionMap.PITCH;
        int gx = Math.floorDiv(p.ox, pitch), gz = Math.floorDiv(p.oz, pitch);
        for (int i = gx - 2; i <= gx + 1; i++)
            for (int j = gz - 2; j <= gz + 1; j++)
                for (int dir = 0; dir < 2; dir++) {
                    int ni = i + (dir == 0 ? 1 : 0), nj = j + (dir == 1 ? 1 : 0);
                    if (w.regions.swallowed(i, j) || w.regions.swallowed(ni, nj)) continue;
                    int[] ha = w.regions.heart(i, j), hb = w.regions.heart(ni, nj);
                    if (mayPass(ha[0], ha[1], hb[0], hb[1], p.ox, p.oz, LANE_REACH)) roads.addAll(RoadNet.trunk(w, i, j, dir));
                }
        if (w.city) for (int k = 0; k < 3; k++) {
            for (int[] seg : portReach(w, k)) {
                if (!mayPass(seg[0], seg[1], seg[2], seg[3], p.ox, p.oz, LANE_REACH)) continue;
                roads.addAll(RoadNet.port(w, k));
                break;
            }
        }
        for (Road r : roads) if (r.from().settlement().equals(p.key) || r.to().settlement().equals(p.key)) return List.of();
        // the nearest point of a road to the settlement
        Road best = null;
        int bi = -1;
        double bd = LANE_REACH;
        for (Road r : roads) {
            for (int i = 2; i < r.points() - 2; i++) {
                if ((r.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK)) != 0) continue;
                double d = Math.hypot(r.x()[i] - p.ox, r.z()[i] - p.oz);
                if (d < bd) {
                    bd = d;
                    best = r;
                    bi = i;
                }
            }
        }
        if (best == null) return List.of();
        RoadNet.Entry e = RoadNet.entryToward(p, best.x()[bi], best.z()[bi]);
        if (e == null) return List.of();
        // the lane meets the road where it's nearest the street's end
        double ed = Double.MAX_VALUE;
        for (int i = 2; i < best.points() - 2; i++) {
            if ((best.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK)) != 0) continue;
            double d = Math.hypot(best.x()[i] - e.x(), best.z()[i] - e.z());
            if (d < ed) {
                ed = d;
                bi = i;
            }
        }
        // the lane arrives square to the road
        float tx = best.x()[Math.min(best.points() - 1, bi + 1)] - best.x()[bi - 1], tz = best.z()[Math.min(best.points() - 1, bi + 1)] - best.z()[bi - 1];
        float tl = (float) Math.hypot(tx, tz);
        float nx = -tz / tl, nz = tx / tl;
        if (nx * (e.x() - best.x()[bi]) + nz * (e.z() - best.z()[bi]) < 0) {
            nx = -nx;
            nz = -nz;
        }
        RoadNet.Entry j = new RoadNet.Entry(Math.round(best.x()[bi]), Math.round(best.z()[bi]), best.y()[bi], nx, nz,
                best.to().name(), best.key() + "@" + bi, Road.JUNCTION, -1);
        List<VillagePlan> around = new ArrayList<>(List.of(p));
        Road lane = lay(w, "B" + a.key(), Road.LANE, e, j, keep(w, around, null), t);
        if (lane == null) return List.of();
        List<Road.Marker> marks = new ArrayList<>(lane.markers());
        Road.Marker fork = Waymarks.fork(lane, best, bi);
        if (fork != null) marks.add(fork);
        return List.of(new Road(lane.key(), lane.cls(), lane.x(), lane.z(), lane.y(), lane.flags(), lane.fam(), lane.from(), lane.to(), marks, lane.litFrom(), 0));
    }
}
