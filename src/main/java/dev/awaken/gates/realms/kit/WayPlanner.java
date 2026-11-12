package dev.awaken.gates.realms.kit;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.piece.Hops;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A designed run for an open land drawn by hand (SPEC §50.9 "A way through"; Euan, 12 Nov: "nothing guided me"), from the
 * places the land already has: a camp within sight of the way in, a stretch of stones to jump along straight after it,
 * then from there a way to each Warden and to the Lord's door, a stop at least every {@link #STEP} blocks (a camp the land
 * already has where one is near, a stretch of stones where a branch has none yet, a new camp otherwise). A camp or a stone
 * stands only where the land says it may ({@link Ground#ok}) and clear of what is built. Nothing is put down unless the
 * whole run can be: a land it can't plan keeps the run read off its sites, and the open-land check says so.
 */
public final class WayPlanner {
    private WayPlanner() {}

    /** The longest walk between two stops, and how far ahead the next one is looked for. Under the check's 90 either way. */
    static final int STEP = 64, MOST = 88;
    /** The first camp is looked for this far in from the Mouth (the check wants one within 60). */
    static final int FIRST_NEAR = 34, FIRST_FAR = 56;

    /** What the land tells the planner: where a camp or a stone may stand, and what its stones are made of. */
    public interface Ground {
        boolean ok(int x, int z);
    }

    public record Dress(BlockState body, BlockState cap, BlockState band) {}

    /** Why the last plan on this thread came to nothing, for the log: what was looked for, where, and what stood in its way. */
    private static final ThreadLocal<String> WHY = new ThreadLocal<>();

    public static String why() {
        String w = WHY.get();
        return w == null ? "" : w;
    }

    private static boolean no(String why) {
        WHY.set(why);
        return false;
    }

    /** What the planner asks the land to do, all at once once the run is whole. */
    public interface Build {
        /** A new den site at the ground there; its index. */
        int den(int x, int z);

        void leg(Land.Leg leg);

        void course(Land.Course c);

        void stones(Hops h);
    }

    private static final class Stop {
        final String id;
        final Land.LegKind kind;
        final String name;
        final int x, y, z, radius, site;
        final List<String> exits = new ArrayList<>();
        Hops hops;
        int[] newDen;

        Stop(String id, Land.LegKind kind, String name, int x, int y, int z, int radius, int site) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.site = site;
        }

        Land.Leg leg() {
            return new Land.Leg(id, kind, name, x, y, z, radius, site, List.of(), "", List.copyOf(exits));
        }
    }

    /** Plans and, when it is whole, builds the run. False when it couldn't be planned: nothing was built. */
    public static boolean plan(Land land, List<? extends Piece<?>> pieces, Ground ok, Dress dress, long seed, Build build) {
        Land.Site mouth = land.mouth();
        List<Land.Site> lords = land.sites(Land.SiteKind.LORD), wardens = land.sites(Land.SiteKind.WARDEN);
        WHY.remove();
        if (lords.isEmpty() || wardens.isEmpty()) return no("no Lord or no Wardens");
        Land.Site lord = lords.getFirst();
        List<Land.Site> dens = land.sites(Land.SiteKind.DEN);
        int nextDen = dens.size();
        Set<Integer> used = new HashSet<>();
        List<Stop> stops = new ArrayList<>();
        List<Hops> laid = new ArrayList<>();
        List<int[]> taken = new ArrayList<>(); // x, z, radius of everything the plan has put down

        Stop arrive = new Stop("arrive", Land.LegKind.ARRIVE, "The way in", mouth.x(), mouth.y(), mouth.z(), 6, -1);
        stops.add(arrive);
        double[] dir = unit(lord.x() - mouth.x(), lord.z() - mouth.z());

        // the first camp: one the land has near the way in, or a new one a short walk toward the Lord
        Stop camp1 = null;
        for (Land.Site d : dens) {
            double dd = Math.hypot(d.x() - mouth.x(), d.z() - mouth.z());
            if (dd <= FIRST_FAR && dd >= 16) {
                camp1 = new Stop("camp1", Land.LegKind.FIGHT, "A camp near the way in", d.x(), d.y(), d.z(), 8, d.index());
                used.add(d.index());
                break;
            }
        }
        if (camp1 == null) {
            int[] at = spot(land, pieces, ok, taken, mouth.x(), mouth.z(), dir, FIRST_NEAR, FIRST_FAR, 30);
            if (at == null) return no("no first camp " + FIRST_NEAR + " to " + FIRST_FAR + " from the Mouth: " + why());
            camp1 = new Stop("camp1", Land.LegKind.FIGHT, "A camp near the way in", at[0], land.ground(at[0], at[1]) + 1, at[1], 8, nextDen++);
            camp1.newDen = at;
            taken.add(new int[]{at[0], at[1], 12});
        }
        arrive.exits.add(camp1.id);
        stops.add(camp1);

        // straight after it, the first stones: every way on starts past them
        Stop climb = stones(land, pieces, ok, dress, taken, seed, "climb1", "Stones beyond the first camp", camp1.x, camp1.z, dir, laid);
        if (climb == null) return no("no stones past the first camp at " + camp1.x + "," + camp1.z + ": " + why());
        stops.add(climb);
        double[] past = new double[]{climb.hops.end()[0], climb.hops.end()[2]};
        Stop hub = stopNear(land, pieces, ok, dens, used, taken, "camp2", "A camp past the stones", past[0] + dir[0] * 28, past[1] + dir[1] * 28, nextDen);
        if (hub == null) return no("no camp past the first stones near " + (int) (past[0] + dir[0] * 28) + "," + (int) (past[1] + dir[1] * 28) + ": " + why());
        if (hub.newDen != null) nextDen++;
        stops.add(hub);
        camp1.exits.add(climb.id);
        camp1.exits.add(hub.id); // or round the stones on foot: the run has a way round
        climb.exits.add(hub.id);

        // a way on to each Warden, nearest first, and to the Lord's door last; a branch that can't be laid yet is tried
        // again once the others are down (a Warden up on a terrace is reached from the one below it)
        List<Land.Site> targets = new ArrayList<>(wardens);
        targets.sort(java.util.Comparator.comparingDouble(s -> Math.hypot(s.x() - hub.x, s.z() - hub.z)));
        targets.add(lord);
        int[] door = door(land, lord);
        int[] n = {3};
        int[] dens2 = {nextDen};
        for (int pass = 0; pass < 3 && !targets.isEmpty(); pass++) {
            for (java.util.Iterator<Land.Site> it = targets.iterator(); it.hasNext(); ) {
                Land.Site t = it.next();
                int stopsAt = stops.size(), laidAt = laid.size(), takenAt = taken.size(), denAt = dens2[0];
                Set<Integer> usedAt = new HashSet<>(used);
                List<List<String>> exitsAt = new ArrayList<>();
                for (Stop s0 : stops) exitsAt.add(new ArrayList<>(s0.exits));
                if (branch(land, pieces, ok, dress, seed, t, door, stops, laid, taken, dens, used, n, dens2)) {
                    it.remove();
                    continue;
                }
                // undo what the branch put down, and try it again next time round
                while (stops.size() > stopsAt) stops.removeLast();
                while (laid.size() > laidAt) laid.removeLast();
                while (taken.size() > takenAt) taken.removeLast();
                for (int i = 0; i < stops.size(); i++) {
                    stops.get(i).exits.clear();
                    stops.get(i).exits.addAll(exitsAt.get(i));
                }
                used.clear();
                used.addAll(usedAt);
                dens2[0] = denAt;
            }
        }
        if (!targets.isEmpty()) {
            StringBuilder left = new StringBuilder();
            for (Land.Site t : targets) left.append(left.isEmpty() ? "" : ", ").append(t.kind()).append(' ').append(t.index()).append(" at ").append(t.x()).append(',').append(t.z());
            return no("no way to " + left + ": " + why());
        }

        // whole: put it all down
        for (Stop s : stops) if (s.newDen != null && build.den(s.newDen[0], s.newDen[1]) != s.site) return no("a new den came out as another index than planned");
        for (Hops h : laid) build.stones(h);
        for (Stop s : stops) {
            build.leg(s.leg());
            if (s.hops != null) build.course(s.hops.course(s.id, land));
        }
        return true;
    }

    /** One branch: stops from the nearest one there is to the target, the first of them stones, then the target's own leg. */
    private static boolean branch(Land land, List<? extends Piece<?>> pieces, Ground ok, Dress dress, long seed, Land.Site t, int[] door, List<Stop> stops,
                                  List<Hops> laid, List<int[]> taken, List<Land.Site> dens, Set<Integer> used, int[] n, int[] nextDen) {
        boolean isLord = t.kind() == Land.SiteKind.LORD;
        // the Lord's leg is its door: the hall itself may stand high in the middle of something no camp can be near
        int tx = isLord ? door[0] : t.x(), ty = isLord ? door[1] : t.y(), tz = isLord ? door[2] : t.z();
        Stop cur = nearest(stops, tx, tz);
        if (cur == null) return no("nowhere to start a branch from");
        boolean branchMoves = false;
        int guard = 0;
        while (Math.hypot(tx - cur.x, tz - cur.z) > MOST && guard++ < 12) {
            double[] u = unit(tx - cur.x, tz - cur.z);
            double px = cur.x + u[0] * STEP, pz = cur.z + u[1] * STEP;
            Stop next = null;
            // each branch more than one walk long gets its own stones, before its first stop
            if (!branchMoves) {
                next = stones(land, pieces, ok, dress, taken, seed + n[0], "climb" + n[0], "Stones on the way", cur.x + u[0] * 14, cur.z + u[1] * 14, u, laid);
                if (next != null) branchMoves = true;
            }
            if (next == null) {
                next = stopNear(land, pieces, ok, dens, used, taken, "camp" + n[0], "A camp on the way", px, pz, nextDen[0]);
                if (next == null) return no("no camp near " + (int) px + "," + (int) pz + " (from " + cur.id + "): " + why());
                if (next.newDen != null) nextDen[0]++;
            }
            n[0]++;
            cur.exits.add(next.id);
            stops.add(next);
            cur = next;
        }
        if (Math.hypot(tx - cur.x, tz - cur.z) > MOST) return no("still " + (int) Math.hypot(tx - cur.x, tz - cur.z) + " short of " + tx + "," + tz);
        String id = isLord ? "lord" : "warden" + t.index();
        Stop end = new Stop(id, isLord ? Land.LegKind.LORD : Land.LegKind.WARDEN, isLord ? "The Lord's door" : "A Warden's ground",
                tx, ty, tz, isLord ? 12 : Math.max(12, t.radius()), isLord ? -1 : t.index());
        cur.exits.add(id);
        if (!isLord) end.exits.add(cur.id);
        stops.add(end);
        return true;
    }

    /** The Lord's door: the middle of what opens nearest the way in when the last Warden falls, or the Lord's own place. */
    static int[] door(Land land, Land.Site lord) {
        List<Land.Edit> open = land.change("heart_open");
        if (open.isEmpty()) return new int[]{lord.x(), lord.y(), lord.z()};
        Land.Site mouth = land.mouth();
        Land.Edit first = open.getFirst();
        for (Land.Edit e : open) if (Math.hypot(e.x() - mouth.x(), e.z() - mouth.z()) < Math.hypot(first.x() - mouth.x(), first.z() - mouth.z())) first = e;
        long sx = 0, sz = 0;
        int c = 0, foot = Integer.MAX_VALUE;
        for (Land.Edit e : open) {
            if (Math.abs(e.x() - first.x()) > 9 || Math.abs(e.z() - first.z()) > 9 || Math.abs(e.y() - first.y()) > 12) continue;
            sx += e.x();
            sz += e.z();
            c++;
            foot = Math.min(foot, e.y());
        }
        return new int[]{(int) (sx / c), foot, (int) (sz / c)};
    }

    /** A stop near a point: a den the land has (unused, within 26), or a new camp on good ground near it. */
    private static Stop stopNear(Land land, List<? extends Piece<?>> pieces, Ground ok, List<Land.Site> dens, Set<Integer> used, List<int[]> taken,
                                 String id, String name, double px, double pz, int nextDen) {
        for (Land.Site d : dens) {
            if (used.contains(d.index()) || Math.hypot(d.x() - px, d.z() - pz) > 26) continue;
            used.add(d.index());
            return new Stop(id, Land.LegKind.FIGHT, name, d.x(), d.y(), d.z(), 8, d.index());
        }
        int[] at = spot(land, pieces, ok, taken, (int) px, (int) pz, null, 0, 22, 12);
        if (at == null) return null;
        taken.add(new int[]{at[0], at[1], 12});
        Stop s = new Stop(id, Land.LegKind.FIGHT, name, at[0], land.ground(at[0], at[1]) + 1, at[1], 8, nextDen);
        s.newDen = at;
        return s;
    }

    /**
     * A stretch of stones starting near (x, z) and running along {@code dir}, or beside that line: tops a few blocks over
     * the ground, every stone on good ground and clear of what is built. Null when no way of laying it fits.
     */
    private static Stop stones(Land land, List<? extends Piece<?>> pieces, Ground ok, Dress dress, List<int[]> taken, long seed,
                               String id, String name, double x, double z, double[] dir, List<Hops> laid) {
        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
        for (int turn : TURNS) for (int along : new int[]{12, 20, 6, 28}) for (int side : new int[]{0, 10, -10, 20, -20}) for (int len : new int[]{34, 26}) {
            double sx = x + dir[0] * along - dir[1] * side, sz = z + dir[1] * along + dir[0] * side;
            // a line up a slope steeper than a block a stone can't climb it, so across the slope is tried too
            double c = Math.cos(Math.toRadians(turn)), sn = Math.sin(Math.toRadians(turn)), lx = dir[0] * c - dir[1] * sn, lz = dir[0] * sn + dir[1] * c;
            int x0 = (int) Math.round(sx), z0 = (int) Math.round(sz), x1 = (int) Math.round(sx + lx * len), z1 = (int) Math.round(sz + lz * len);
            List<Hops.Stone> line = Hops.follow(x0, z0, x1, z1, 3, 1, 2, seed + along * 31L + side, land);
            String why = misfit(land, pieces, ok, taken, line);
            if (why != null) {
                tally.merge(why, 1, Integer::sum);
                continue;
            }
            Hops h = new Hops(line, dress.body(), dress.cap(), dress.band(), "stones to jump along");
            laid.add(h);
            for (Hops.Stone s : line) taken.add(new int[]{s.x(), s.z(), 3});
            int[] a = h.start(), b = h.end();
            Stop st = new Stop(id, Land.LegKind.MOVEMENT, name, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2, 9, -1);
            st.hops = h;
            return st;
        }
        WHY.set("stone lines " + tally);
        return null;
    }

    /** The turns off the way's own line a stretch of stones is tried at, the way itself first. */
    static final int[] TURNS = {0, 35, -35, 70, -70, 100, -100};

    /**
     * Whether every stone stands on good ground, over it by a block or more (the first a jump up from it, {@link Hops#on}),
     * the line reaching three up somewhere so a fall off it counts, clear of what is built and of the plan so far.
     */
    public static boolean fits(Land land, List<? extends Piece<?>> pieces, Ground ok, List<int[]> taken, List<Hops.Stone> line) {
        return misfit(land, pieces, ok, taken, line) == null;
    }

    /** Why a line of stones can't stand here, or null when it can (the reasons {@link #fits} goes by, for the log). */
    public static String misfit(Land land, List<? extends Piece<?>> pieces, Ground ok, List<int[]> taken, List<Hops.Stone> line) {
        int high = 0;
        for (Hops.Stone s : line) {
            int g = land.ground(s.x(), s.z());
            if (land.edge(s.x(), s.z()) > -40) return "edge";
            if (!ok.ok(s.x(), s.z())) return "ground";
            if (s.top() < g + 1) return "buried";
            if (s.top() > g + 8) return "high";
            if (covered(pieces, s.x(), s.z(), s.r() + 2)) return "built";
            if (near(taken, s.x(), s.z(), 3)) return "taken";
            if (onWay(land, s.x(), s.z(), s.r() + 1)) return "way";
            high = Math.max(high, s.top() - g);
        }
        if (high < 3) return "low";
        return line.getFirst().top() - land.ground(line.getFirst().x(), line.getFirst().z()) > 1 ? "first" : null;
    }

    /** A place for a camp: good ground over a ring of it, clear of what is built and of the plan, from {@code near} to {@code far} out (along {@code dir} when given). */
    private static int[] spot(Land land, List<? extends Piece<?>> pieces, Ground ok, List<int[]> taken, int x, int z, double[] dir, int near, int far, int arc) {
        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
        for (int r = near; r <= far; r += 4) for (int k = 0; k <= arc; k += 6) for (int sign = -1; sign <= 1; sign += 2) {
            double a = (dir == null ? 0 : Math.atan2(dir[1], dir[0])) + Math.toRadians(k * sign) + (dir == null ? k * 0.21 : 0);
            int cx = x + (int) Math.round(Math.cos(a) * r), cz = z + (int) Math.round(Math.sin(a) * r);
            String why = unfit(land, pieces, ok, taken, cx, cz);
            if (why == null) return new int[]{cx, cz};
            tally.merge(why, 1, Integer::sum);
            if (k == 0) break;
        }
        WHY.set("camp spots " + tally);
        return null;
    }

    /** Why a camp can't stand here (null: it can): too near the edge, on something built, on the plan, on a way, by a site, bad or uneven ground. */
    private static String unfit(Land land, List<? extends Piece<?>> pieces, Ground ok, List<int[]> taken, int x, int z) {
        if (land.edge(x, z) > -48) return "edge";
        if (covered(pieces, x, z, 10)) return "built";
        if (near(taken, x, z, 14)) return "taken";
        if (onWay(land, x, z, 8)) return "way";
        for (Land.Site s : land.sites()) if (Math.hypot(s.x() - x, s.z() - z) < s.radius() + 14) return "site";
        int g = land.ground(x, z);
        for (int dx = -6; dx <= 6; dx += 3) for (int dz = -6; dz <= 6; dz += 3) {
            if (!ok.ok(x + dx, z + dz)) return "ground";
            if (Math.abs(land.ground(x + dx, z + dz) - g) > 3) return "uneven";
        }
        return null;
    }

    /** Whether something built stands over this ground. A piece the size of the land (a city's pass, its roads) doesn't count. */
    static boolean covered(List<? extends Piece<?>> pieces, int x, int z, int pad) {
        for (Piece<?> p : pieces) {
            if (p instanceof Hops || p.airborne || (long) (p.x1 - p.x0) * (p.z1 - p.z0) > 160L * 160L) continue;
            if (x + pad >= p.x0 && x - pad <= p.x1 && z + pad >= p.z0 && z - pad <= p.z1) return true;
        }
        return false;
    }

    /** Whether a road or a stair of the land runs within {@code pad} of here: nothing of the plan stands on a way. */
    static boolean onWay(Land land, int x, int z, int pad) {
        for (double[] w : land.ways()) {
            double dx = w[2] - w[0], dz = w[3] - w[1], l2 = dx * dx + dz * dz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - w[0]) * dx + (z - w[1]) * dz) / l2));
            if (Math.hypot(x - (w[0] + dx * t), z - (w[1] + dz * t)) < w[4] + pad) return true;
        }
        return false;
    }

    private static boolean near(List<int[]> taken, int x, int z, int pad) {
        for (int[] t : taken) if (Math.hypot(t[0] - x, t[1] - z) < t[2] + pad) return true;
        return false;
    }

    private static Stop nearest(List<Stop> stops, int x, int z) {
        Stop best = null;
        double bd = Double.MAX_VALUE;
        for (Stop s : stops) {
            if (s.kind == Land.LegKind.LORD || s.kind == Land.LegKind.ARRIVE) continue;
            double d = Math.hypot(s.x - x, s.z - z);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    private static double[] unit(double dx, double dz) {
        double l = Math.max(1e-6, Math.hypot(dx, dz));
        return new double[]{dx / l, dz / l};
    }

}
