package dev.awaken.village.road;

import dev.awaken.terrain.api.CityBasin;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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

    /**
     * A highroad's way before any of it is laid (SPEC §28.9, Cost): where each piece runs from and to, stop by stop, and
     * what the pieces keep out of. Piece k is laid as {@code key#k}, alone, so a chunk can ask for only the pieces that
     * may come near it: each keeps inside its search's ellipse.
     */
    record Way(String key, int cls, List<RoadNet.Entry> from, List<RoadNet.Entry> to, RoadPlanner.Keep keep) {
        int pieces() {
            return from.size();
        }

        /** Piece k's search ellipse {sx, sz, ex, ez, reach} (a little wider than RoadPlanner's, for rounding). */
        double[] ellipse(int k) {
            RoadNet.Entry f = from.get(k), e = to.get(k);
            double sx = f.x() + f.dx() * 12.0, sz = f.z() + f.dz() * 12.0, ex = e.x() + e.dx() * 12.0, ez = e.z() + e.dz() * 12.0;
            double d = Math.hypot(ex - sx, ez - sz);
            int c = (int) Math.clamp(Math.round(d / 40.0), 12, 32) + 1;
            return new double[]{sx, sz, ex, ez, d * RoadPlanner.STRETCH + 2.0 * c + 2};
        }

        /** Whether piece k may have a point in the square {@code r} each way of (x, z). */
        boolean mayCome(int k, int x, int z, int r) {
            double[] el = ellipse(k);
            double ds = toSquare(el[0], el[1], x, z, r), de = toSquare(el[2], el[3], x, z, r);
            double dm = toSquare((el[0] + el[2]) / 2, (el[1] + el[3]) / 2, x, z, r);
            return Math.max(ds + de, 2 * dm) <= el[4];
        }

        /** How near piece k's points may come to (x, z) at best (every point of the ellipse is at least this far). */
        double nearest(int k, double x, double z) {
            double[] el = ellipse(k);
            return (Math.hypot(x - el[0], z - el[1]) + Math.hypot(x - el[2], z - el[3]) - el[4]) / 2 - 1;
        }

        private static double toSquare(double px, double pz, int x, int z, int r) {
            return Math.hypot(Math.max(0, Math.abs(px - x) - r), Math.max(0, Math.abs(pz - z) - r));
        }
    }

    static Way trunkWay(RoadWorld w, int gx, int gz, int dir) {
        RoadNet.Hub a = RoadNet.hub(w, gx, gz), b = RoadNet.hub(w, gx + (dir == 0 ? 1 : 0), gz + (dir == 1 ? 1 : 0));
        String key = "T" + gx + "," + gz + "," + dir;
        if (a.kind() == RoadNet.HubKind.NONE || b.kind() == RoadNet.HubKind.NONE) return new Way(key, Road.HIGHROAD, List.of(), List.of(), (x, z) -> false);
        return way(w, key, Road.HIGHROAD, a, b, null);
    }

    /** Lays a way's piece k (null where no way is found). */
    static @Nullable Road piece(RoadWorld w, Way way, int k) {
        return lay(w, way.key() + "#" + k, way.cls(), way.from().get(k), way.to().get(k), way.keep(), w.terrain());
    }

    /**
     * A highroad from hub a to hub b through the settlements on its way (each entered at one end of its main street and
     * left by the other), kept off every other settlement's ground. {@code start}, when given, replaces a's end (a port).
     */
    static Way way(RoadWorld w, String key, int cls, RoadNet.@Nullable Hub a, RoadNet.Hub b, RoadNet.@Nullable Entry start) {
        Terrain t = w.terrain();
        double ax = start != null ? start.x() : a.x(), az = start != null ? start.z() : a.z();
        double d = Math.hypot(b.x() - ax, b.z() - az);
        if (d < 48) return new Way(key, cls, List.of(), List.of(), (x, z) -> false);
        // the settlements near the straight way: those well between the ends ride on it, the rest are kept clear of (one
        // that can't ride on it wherever its site moves is planned only if a road's search comes near it: SPEC §28.9, Cost)
        List<VillagePlan> on = new ArrayList<>(), clear = new ArrayList<>();
        List<SettlementGrid.Anchor> aside = new ArrayList<>();
        List<double[]> along = new ArrayList<>();
        int[] centre = {w.cx, w.cz};
        double mx = (ax + b.x()) / 2, mz = (az + b.z()) / 2;
        for (SettlementGrid.Anchor s : SettlementGrid.gridNear(w.seed, (int) mx, (int) mz, (int) (d / 2 + 0.36 * d + 120), centre)) {
            if (a != null && s.equals(a.anchor()) || s.equals(b.anchor())) continue;
            if (!corridor(ax, az, b.x(), b.z(), s.x(), s.z(), 0.36 * d + 140)) continue;
            if (!mayRide(ax, az, b.x(), b.z(), d, s)) {
                aside.add(s);
                continue;
            }
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
        RoadPlanner.Keep keep = keep(w, keepOut, aside, start);
        // the way, stop by stop
        List<RoadNet.Entry> froms = new ArrayList<>(), tos = new ArrayList<>();
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
            // piece k (k counts the pieces: a stop dropped above doesn't take a number)
            froms.add(from);
            tos.add(to);
            from = next;
        }
        return new Way(key, cls, froms, tos, keep);
    }

    /** How far a grid settlement's site moves from its anchor at most (the planner's search, on its 16-block steps), and its radius. */
    static final int MOVE = 46, FIRST_MOVE = 91, MAX_R = 88;

    /**
     * Whether a grid settlement could ride on the way from a to b ({@link #way}'s test on its plan's middle) wherever
     * its site moves from its anchor: false, and it can only be kept clear of.
     */
    static boolean mayRide(double ax, double az, double bx, double bz, double d, SettlementGrid.Anchor s) {
        double move = (s.first() ? FIRST_MOVE : MOVE) + 1e-6;
        double tt = ((s.x() - ax) * (bx - ax) + (s.z() - az) * (bz - az)) / (d * d);
        double perp = Math.abs((s.x() - ax) * (bz - az) - (s.z() - az) * (bx - ax)) / d;
        return tt > 0.12 - move / d && tt < 0.88 + move / d && perp < Math.min(0.22 * d, 200) + move;
    }

    static RoadPlanner.Keep keep(RoadWorld w, List<VillagePlan> around, RoadNet.@Nullable Entry port) {
        return keep(w, around, List.of(), port);
    }

    /**
     * The columns a road keeps out of: other settlements' planned ground (its streets, houses, yards, fields and pens, and
     * a few blocks round them), the city's reserve (but a port's own way out), the camp. The settlements {@code aside}
     * are planned only when the road's search first comes within reach of their ground (most never are).
     */
    static RoadPlanner.Keep keep(RoadWorld w, List<VillagePlan> around, List<SettlementGrid.Anchor> aside, RoadNet.@Nullable Entry port) {
        int n = around.size(), m = aside.size();
        VillagePlan[] ps = around.toArray(new VillagePlan[0]);
        SettlementGrid.Anchor[] as = aside.toArray(new SettlementGrid.Anchor[0]);
        java.util.concurrent.atomic.AtomicReferenceArray<Optional<VillagePlan>> got = new java.util.concurrent.atomic.AtomicReferenceArray<>(m);
        int campX = w.cx + CAMP_X, campZ = w.cz + CAMP_Z;
        return (x, z) -> {
            for (int i = 0; i < n; i++) if (kept(ps[i], x, z)) return true;
            for (int i = 0; i < m; i++) {
                SettlementGrid.Anchor s = as[i];
                int dx = x - s.x(), dz = z - s.z(), reach = (s.first() ? FIRST_MOVE : MOVE) + MAX_R + 9;
                if (dx * dx + dz * dz > reach * reach) continue;
                Optional<VillagePlan> p = got.get(i);
                if (p == null) got.set(i, p = Optional.ofNullable(Plans.of(s, w.terrain())));
                if (p.isPresent() && kept(p.get(), x, z)) return true;
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

    /** Whether a road keeps out of a column for a settlement: its built ground, or within three blocks of it. */
    static boolean kept(VillagePlan p, int x, int z) {
        int dx = x - p.ox, dz = z - p.oz;
        if (dx * dx + dz * dz > (p.r + 8) * (p.r + 8)) return false;
        return built(p, x, z) || built(p, x + 3, z) || built(p, x - 3, z) || built(p, x, z + 3) || built(p, x, z - 3);
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

    /**
     * Carillon's road out as ways laid piece by piece (SPEC §28.9, Cost): north, past the camp to the first hamlet (one
     * piece, P0#0) and on from its far end; east and south, to the nearest region that way a road reaches. Which region
     * turns on whether a way has any piece at all, so {@code reaches} lays a way's pieces in order only until one is.
     */
    static List<Way> portWays(RoadWorld w, int which, Predicate<Way> reaches) {
        if (!w.city) return List.of();
        int[] s = PORTS[which];
        Terrain t = w.terrain();
        int x = w.cx + s[0], z = w.cz + s[1];
        RoadNet.Entry start = new RoadNet.Entry(x, z, t.top(x, z), s[2], s[3], PORT_NAME, "", Road.PORT, -1);
        List<int[]> cells = portCells(w, which);
        if (which == 0) {
            // north: past the camp to the first hamlet, then on from its far end
            SettlementGrid.Anchor f = SettlementGrid.first(w.seed, new int[]{w.cx, w.cz});
            VillagePlan fp = Plans.of(f, t);
            if (fp != null) {
                List<Way> out = new ArrayList<>();
                RoadNet.Entry in = RoadNet.entryToward(fp, x, z);
                if (in != null) out.add(new Way("P0", Road.HIGHROAD, List.of(start), List.of(in), keep(w, List.of(fp), start)));
                for (int[] cell : cells) {
                    RoadNet.Hub b = RoadNet.hub(w, cell[0], cell[1]);
                    RoadNet.Entry on = RoadNet.entryToward(fp, b.x(), b.z());
                    if (on == null || b.kind() == RoadNet.HubKind.NONE || in != null && on.x() == in.x() && on.z() == in.z()) continue;
                    Way way = way(w, "P0+", Road.HIGHROAD, null, b, on);
                    if (!reaches.test(way)) continue;
                    out.add(way);
                    break;
                }
                return out;
            }
        }
        // the nearest region that way a road reaches
        for (int[] cell : cells) {
            RoadNet.Hub b = RoadNet.hub(w, cell[0], cell[1]);
            if (b.kind() == RoadNet.HubKind.NONE) continue;
            Way way = way(w, "P" + which, Road.HIGHROAD, null, b, start);
            if (reaches.test(way)) return List.of(way);
        }
        return List.of();
    }

    // ---- lanes ---------------------------------------------------------------------------------------------------------

    /**
     * The roads a settlement's lane may join, in the order it looks at them: the highroads ({gx, gz, dir}) whose straight
     * line between hearts comes within a lane's reach of it, then Carillon's roads out ({which}).
     */
    static List<int[]> laneJoins(RoadWorld w, VillagePlan p) {
        List<int[]> out = new ArrayList<>();
        int pitch = dev.awaken.living.region.RegionMap.PITCH;
        int gx = Math.floorDiv(p.ox, pitch), gz = Math.floorDiv(p.oz, pitch);
        for (int i = gx - 2; i <= gx + 1; i++)
            for (int j = gz - 2; j <= gz + 1; j++)
                for (int dir = 0; dir < 2; dir++) {
                    int ni = i + (dir == 0 ? 1 : 0), nj = j + (dir == 1 ? 1 : 0);
                    if (w.regions.swallowed(i, j) || w.regions.swallowed(ni, nj)) continue;
                    int[] ha = w.regions.heart(i, j), hb = w.regions.heart(ni, nj);
                    if (mayPass(ha[0], ha[1], hb[0], hb[1], p.ox, p.oz, LANE_REACH)) out.add(new int[]{i, j, dir});
                }
        if (w.city) for (int k = 0; k < 3; k++) {
            for (int[] seg : portReach(w, k)) {
                if (!mayPass(seg[0], seg[1], seg[2], seg[3], p.ox, p.oz, LANE_REACH)) continue;
                out.add(new int[]{k});
                break;
            }
        }
        return out;
    }

    /**
     * Whether a settlement's lane could start a leg in a lattice chunk, judged from the roads it may join that are laid
     * already (SPEC §28.9, Cost). The lane runs from one of its street ends to the point of those roads nearest the end,
     * the nearest point to the settlement's middle being under {@link #LANE_REACH} away, and keeps inside its search's
     * ellipse (stretch {@link RoadPlanner#STRETCH}, a lattice of at most 32, its ends 12 out): every point of it lies
     * within (1 + stretch) / 2 (δ + E + 24) + 32 + 12 of the street end it leaves by, where δ bounds how far the nearest
     * point is (any point of a road laid already, else the lane's reach) and E is how far the furthest street end is from
     * the middle. A leg that
     * starts in a chunk has every point within {@link RoadNet#LEG_REACH} of the chunk's middle each way. False means the
     * lane starts nothing there, so it needn't be laid to say so.
     */
    static boolean laneMayStart(RoadWorld w, VillagePlan p, int chunkX, int chunkZ) {
        List<RoadNet.Entry> ends = RoadNet.entries(p);
        if (ends.isEmpty()) return false;
        double far = 0, delta = LANE_REACH;
        for (RoadNet.Entry e : ends) far = Math.max(far, Math.hypot(e.x() - p.ox, e.z() - p.oz));
        for (int[] c : laneJoins(w, p)) {
            List<Road> laid = RoadNet.laidAs(c.length == 3 ? "T" + c[0] + "," + c[1] + "," + c[2] : "P" + c[0]);
            if (laid == null) continue;
            for (Road r : laid) delta = Math.min(delta, nearest(r, p));
        }
        double reach = (1 + RoadPlanner.STRETCH) / 2 * (delta + far + 24) + 32 + 12;
        int cx = (chunkX << 4) + 8, cz = (chunkZ << 4) + 8;
        for (RoadNet.Entry e : ends) {
            double dx = Math.max(0, Math.abs(e.x() - cx) - RoadNet.LEG_REACH), dz = Math.max(0, Math.abs(e.z() - cz) - RoadNet.LEG_REACH);
            if (Math.hypot(dx, dz) <= reach) return true;
        }
        return false;
    }

    /** How near a road's points (past its first and last two, off bridges and boardwalks) come to a settlement's middle. */
    static double nearest(Road r, VillagePlan p) {
        double best = Double.MAX_VALUE;
        for (int i = 2; i < r.points() - 2; i++)
            if ((r.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK)) == 0) best = Math.min(best, Math.hypot(r.x()[i] - p.ox, r.z()[i] - p.oz));
        return best;
    }

    static List<Road> branch(RoadWorld w, SettlementGrid.Anchor a) {
        Terrain t = w.terrain();
        VillagePlan p = Plans.of(a, t);
        if (p == null) return List.of();
        // the highroads and Carillon's roads near it, in order, piece by piece: a piece only where it starts or ends here
        // or could hold a point nearer than the nearest yet (SPEC §28.9, Cost)
        List<Way> ways = new ArrayList<>();
        List<Integer> ks = new ArrayList<>();
        List<Road> laid = new ArrayList<>();
        for (int[] c : laneJoins(w, p))
            for (Way way : c.length == 3 ? List.of(RoadNet.way(w, c[0], c[1], c[2])) : RoadNet.portWays(w, c[0]))
                for (int k = 0; k < way.pieces(); k++) {
                    ways.add(way);
                    ks.add(k);
                    laid.add(RoadNet.laidPiece(way, k));
                }
        int n = laid.size();
        boolean[] tried = new boolean[n];
        for (int i = 0; i < n; i++) {
            Way way = ways.get(i);
            int k = ks.get(i);
            tried[i] = laid.get(i) != null;
            if (!tried[i] && (way.from().get(k).key().equals(p.key) || way.to().get(k).key().equals(p.key))) {
                laid.set(i, RoadNet.piece(w, way, k));
                tried[i] = true;
            }
        }
        // if one runs through it, it needs no lane
        for (Road r : laid) if (r != null && (r.from().settlement().equals(p.key) || r.to().settlement().equals(p.key))) return List.of();
        double near = LANE_REACH;
        for (Road r : laid) if (r != null) near = Math.min(near, nearest(r, p));
        List<double[]> rest = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!tried[i]) rest.add(new double[]{ways.get(i).nearest(ks.get(i), p.ox, p.oz), i});
        rest.sort((u, v) -> Double.compare(u[0], v[0]));
        for (double[] u : rest) {
            if (u[0] > near) break;
            int i = (int) u[1];
            Road r = RoadNet.piece(w, ways.get(i), ks.get(i));
            laid.set(i, r);
            if (r != null) near = Math.min(near, nearest(r, p));
        }
        // the nearest point of a road to the settlement (a piece left unlaid has none as near)
        List<Road> roads = new ArrayList<>();
        for (Road r : laid) if (r != null) roads.add(r);
        Road best = null;
        int bi = -1;
        double bd = LANE_REACH;
        for (Road r : roads) {
            for (int i = 2; i < r.points() - 2; i++) {
                if ((r.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK | RoadPlanner.VIADUCT)) != 0) continue;
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
            if ((best.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK | RoadPlanner.VIADUCT)) != 0) continue;
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
