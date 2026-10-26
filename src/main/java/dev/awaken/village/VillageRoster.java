package dev.awaken.village;

import dev.awaken.citizens.api.RosterBuilder;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.village.plan.VillagePlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A settlement's people on the people engine (SPEC §28.6, PLAN 5.4): its street graph from the plan's streets, a door
 * for every building, places for its work (the fields, the pen, the forge, the well, the bell, the benches and the
 * green, the woodlot, the pier's end, the mill, the inn's taproom, the lamps, the Watch's posts and the roads out), the
 * households from its homes and each person's day from their trade. Deterministic from the plan and the world seed, so
 * the saved records always tie to the same roster.
 *
 * <p>The day (time of day, 0 = 06:00): the bell at dawn and dusk; work from seven; the midday meal at home; work again
 * after one; home at dusk, and the inn of an evening for many; the Watch on the edge after dark; the lamplighter's round
 * at dusk and again at dawn. Hunters and miners go out by the road at dawn and come back at dusk, the hunter with a kill
 * on his pole.
 */
public final class VillageRoster {
    private VillageRoster() {}

    private final RosterBuilder b = new RosterBuilder();
    private VillagePlan p;
    private long worldSeed;
    /** Each street's point nodes. */
    private int[][] streetNode;
    private final Map<String, Integer> places = new HashMap<>();
    private final List<Integer> fieldPlaces = new ArrayList<>(), penPlaces = new ArrayList<>(), benchPlaces = new ArrayList<>(),
            postPlaces = new ArrayList<>(), roadPlaces = new ArrayList<>(), innPlaces = new ArrayList<>();
    private int[] doorPlace, doorNode, lotOf, lampPlace;
    private int green, wash = -1, bell = -1, notice = -1, play = -1, anvil = -1, net = -1, mill = -1, woods = -1, inn = -1, innOut = -1, stallKeeper = -1;
    private int lampRoute = -1, watchRoute = -1, errands = -1, carterRoute = -1, eveningRoute = -1;

    public static RosterBuilder of(VillagePlan plan, long worldSeed) {
        VillageRoster r = new VillageRoster();
        r.p = plan;
        r.worldSeed = worldSeed;
        r.ground();
        r.placesAndStations();
        r.people();
        r.emitters();
        return r.b;
    }

    private long hash(long a, long b) {
        long z = p.seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    // ---- the ground: streets, doors, the paths to places off the street --------------------------------------------

    private void ground() {
        green = b.district("the green");
        int ns = p.streets.size();
        streetNode = new int[ns][];
        for (int s = 0; s < ns; s++) {
            VillagePlan.Street st = p.streets.get(s);
            streetNode[s] = new int[st.points()];
            for (int k = 0; k < st.points(); k++) {
                int same = s > 0 && k == 0 ? shared(s, st.x()[0], st.z()[0]) : -1;
                streetNode[s][k] = same >= 0 ? same : b.junction(st.x()[k], st.lift()[k], st.z()[k]);
                if (k > 0) b.street(streetNode[s][k - 1], streetNode[s][k], st.rank() == 0 ? 6 : 0);
            }
        }
        doorPlace = new int[p.buildings.size()];
        doorNode = new int[p.buildings.size()];
        lotOf = new int[p.buildings.size()];
        for (int k = 0; k < p.buildings.size(); k++) {
            VillagePlan.Building bd = p.buildings.get(k);
            CityPlan.Plot q = p.plots.get(bd.plot());
            String tag = bd.use().equals("house") || bd.use().equals("inn") ? "house" : bd.use();
            lotOf[k] = b.lot(q.x0(), q.z0(), q.x1(), q.z1(), q.top(), bd.outX(), bd.outZ(), tag, bd.use());
            int door = b.door(bd.outX() + 0.5, q.lift(), bd.outZ() + 0.5, lotOf[k]);
            doorNode[k] = door;
            link(door, bd.outX() + 0.5, q.lift(), bd.outZ() + 0.5, streetNode[bd.street()][bd.point()], bd.street(), bd.point());
            doorPlace[k] = b.place("door", door, bd.outX() + 0.5, q.lift(), bd.outZ() + 0.5).ref(lotOf[k]).cap(8).id();
        }
    }

    /** A lane's first point stands on an earlier street's point: the same node. */
    private int shared(int s, float x, float z) {
        for (int o = 0; o < s; o++) {
            VillagePlan.Street st = p.streets.get(o);
            for (int k = 0; k < st.points(); k++) if (st.x()[k] == x && st.z()[k] == z) return streetNode[o][k];
        }
        return -1;
    }

    /** Joins a node off the street to a street node, with a node every six blocks between (people walk the line). */
    private void link(int node, double x, int ry, double z, int to, int s, int k) {
        VillagePlan.Street st = p.streets.get(s);
        double tx = st.x()[k], tz = st.z()[k];
        int tr = st.lift()[k];
        double d = Math.hypot(tx - x, tz - z);
        int steps = (int) Math.floor(d / 6);
        int prev = node;
        for (int i = 1; i <= steps; i++) {
            double t = i / (steps + 1.0);
            int mid = b.junction(x + (tx - x) * t, (int) Math.round(ry + (tr - ry) * t), z + (tz - z) * t);
            b.street(prev, mid);
            prev = mid;
        }
        b.street(prev, to);
    }

    /** The street point nearest a cell: {street, point}. */
    private int[] nearest(double x, double z) {
        int[] best = {0, 0};
        double bd = Double.MAX_VALUE;
        for (int s = 0; s < p.streets.size(); s++) {
            VillagePlan.Street st = p.streets.get(s);
            for (int k = 0; k < st.points(); k++) {
                double d = Math.hypot(st.x()[k] - x, st.z()[k] - z);
                if (d < bd) {
                    bd = d;
                    best = new int[]{s, k};
                }
            }
        }
        return best;
    }

    /** A node at a spot off the street, joined to its nearest street point. */
    private int spot(double x, int ry, double z) {
        int[] n = nearest(x, z);
        int node = b.junction(x, ry, z);
        link(node, x, ry, z, streetNode[n[0]][n[1]], n[0], n[1]);
        return node;
    }

    private static float yawTo(double fx, double fz, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - fx), tz - fz));
    }

    // ---- places and stations ------------------------------------------------------------------------------------------

    private void placesAndStations() {
        // the well: the washer at its south side, the trough beside her
        int wx = p.well[0], wz = p.well[1];
        b.water(wx, wz);
        int wnode = spot(wx + 0.5, 0, wz + 2.5);
        wash = b.place("wash", wnode, wx + 0.5, 0, wz + 2.5).yaw(180).district(green).spot(wx + 0.5, wz + 2.5).id();
        b.washplace(wx + 1, 0, wz + 2, wash);
        // the notice board: read from its face
        int f = p.notice[2];
        double nx = p.notice[0] + 0.5 + (f == 1 ? 1.2 : f == 3 ? -1.2 : 0), nz = p.notice[1] + 0.5 + (f == 2 ? 1.2 : f == 0 ? -1.2 : 0);
        notice = b.place("stand", spot(nx, 0, nz), nx, 0, nz).yaw(yawTo(nx, nz, p.notice[0] + 0.5, p.notice[1] + 0.5)).district(green)
                .cap(2).spot(nx, nz).id();
        // benches round the well, and the children's play on the green
        for (int i = 0; i + 2 < p.benches.length; i += 3) {
            int bx = p.benches[i], bz = p.benches[i + 1];
            int pl = b.place("sit", spot(bx + 0.5, 0, bz + 0.5), bx + 0.5, 0, bz + 0.5).yaw(yawTo(bx + 0.5, bz + 0.5, wx + 0.5, wz + 0.5))
                    .district(green).spot(bx + 0.5, bz + 0.5).id();
            benchPlaces.add(pl);
        }
        int gx = wx - 3, gz = wz - 2;
        play = b.place("play", spot(gx + 0.5, 0, gz + 0.5), gx + 0.5, 0, gz + 0.5).district(green).cap(4)
                .spot(gx + 0.5, gz + 0.5).spot(gx + 1.5, gz + 0.5).spot(gx + 0.5, gz - 0.5).id();
        // the bell: its ringer at the foot of the post or the tower
        VillagePlan.Bell bl = p.bell;
        if (bl != null) {
            bell = b.place("bell", spot(bl.ringerX() + 0.5, 0, bl.ringerZ() + 0.5), bl.ringerX() + 0.5, 0, bl.ringerZ() + 0.5)
                    .yaw(yawTo(bl.ringerX(), bl.ringerZ(), bl.bx(), bl.bz())).district(green).spot(bl.ringerX() + 0.5, bl.ringerZ() + 0.5).id();
            b.bell("village_bell", bell, bl.x0(), bl.z0(), bl.x1(), bl.z1(), green);
        }
        // the forge
        if (p.forge != null) {
            VillagePlan.Forge fg = p.forge;
            anvil = b.place("anvil", spot(fg.smithX() + 0.5, fg.lift(), fg.smithZ() + 0.5), fg.smithX() + 0.5, fg.lift(), fg.smithZ() + 0.5)
                    .yaw(yawTo(fg.smithX(), fg.smithZ(), fg.anvilX(), fg.anvilZ())).spot(fg.smithX() + 0.5, fg.smithZ() + 0.5).id();
            int k = buildingOf(fg.plot());
            if (k >= 0) b.smithy(lotOf[k], doorPlace[k], anvil, fg.anvilX(), fg.lift(), fg.anvilZ());
        }
        // fields, pens, the woodlot, the pier's end, the mill's door
        for (VillagePlan.Field fd : p.fields) {
            double cx = (fd.x0() + fd.x1()) / 2.0 + 0.5, cz = (fd.z0() + fd.z1()) / 2.0 + 0.5;
            int wkx = fd.workX(), wkz = fd.workZ();
            int pl = b.place("stand", spot(wkx + 0.5, fd.lift() + 1, wkz + 0.5), wkx + 0.5, fd.lift() + 1, wkz + 0.5).yaw(yawTo(wkx, wkz, cx, cz))
                    .cap(2).spot(wkx + 0.5, wkz + 0.5).id();
            fieldPlaces.add(pl);
        }
        for (VillagePlan.Pen pn : p.pens) {
            int sx = pn.gateSide() == 1 ? 1 : pn.gateSide() == 3 ? -1 : 0, sz = pn.gateSide() == 2 ? 1 : pn.gateSide() == 0 ? -1 : 0;
            double x = pn.gateX() + 0.5 + sx, z = pn.gateZ() + 0.5 + sz;
            int pl = b.place("stand", spot(x, pn.lift(), z), x, pn.lift(), z).yaw(yawTo(x, z, pn.gateX() + 0.5, pn.gateZ() + 0.5)).spot(x, z).id();
            penPlaces.add(pl);
        }
        for (int k = 0; k < p.posts.size(); k++) {
            int[] po = p.posts.get(k);
            int node = spot(po[0] + 0.5, po[2], po[1] + 0.5);
            if (k < 2) {
                postPlaces.add(b.place("post", node, po[0] + 0.5, po[2], po[1] + 0.5).spot(po[0] + 0.5, po[1] + 0.5).id());
                // the road out: where hunters, miners and carters leave and come back
                roadPlaces.add(b.place("landmark", node, po[0] + 0.5, po[2], po[1] + 0.5).ref("road").cap(8).id());
            } else {
                woods = b.place("stand", node, po[0] + 0.5, po[2], po[1] + 0.5).spot(po[0] + 0.5, po[1] + 0.5).id();
            }
        }
        if (p.dock != null) {
            VillagePlan.Dock dk = p.dock;
            int ex = dk.endX(), ez = dk.endZ();
            int node = spot(ex + 0.5, dk.lift(), ez + 0.5);
            net = b.place("net", node, ex + 0.5, dk.lift(), ez + 0.5).spot(ex + 0.5, ez + 0.5).id();
        }
        for (int k = 0; k < p.buildings.size(); k++) {
            VillagePlan.Building bd = p.buildings.get(k);
            CityPlan.Plot q = p.plots.get(bd.plot());
            switch (bd.use()) {
                case "mill" -> {
                    mill = b.place("mill", doorNode[k], bd.outX() + 0.5, q.lift(), bd.outZ() + 0.5).spot(bd.outX() + 0.5, bd.outZ() + 0.5).id();
                }
                case "inn" -> {
                    inn = k;
                    // the taproom: a node a step inside the door, guests standing where the room has floor
                    int fx = bd.outX() - bd.doorX(), fz = bd.outZ() - bd.doorZ();
                    int ix = bd.doorX() - fx, iz = bd.doorZ() - fz;
                    int inner = b.junction(ix + 0.5, q.lift(), iz + 0.5);
                    b.street(inner, doorNode[k]);
                    List<int[]> cells = SettlementPaint.standingCells(p, bd.plot(), worldSeed);
                    cells.removeIf(c -> c[0] == ix && c[1] == iz);
                    RosterBuilder.Place in = b.place("tavern_out", inner, ix + 0.5, q.lift(), iz + 0.5).cap(Math.max(1, Math.min(8, cells.size())));
                    for (int c = 0; c < Math.min(8, cells.size()); c++) in.spot(cells.get(c)[0] + 0.5, cells.get(c)[1] + 0.5);
                    if (cells.isEmpty()) in.spot(ix + 0.5, iz + 0.5);
                    innOut = in.id();
                    innPlaces.add(innOut);
                    b.tavern(lotOf[k], doorPlace[k], innOut, 0);
                }
                default -> {
                }
            }
        }
        // a market stall on a village's green, its keeper behind the counter
        for (CityPlan.Prop pr : p.props) {
            if (!pr.kind().equals("stall")) continue;
            double kx = (pr.x0() + pr.x1()) / 2.0 + 0.5, kz = pr.z0() + 0.5, fx = kx, fz = pr.z1() + 1.5;
            int keeperNode = spot(kx, 0, kz);
            stallKeeper = b.place("stall_keeper", keeperNode, kx, 0, kz).yaw(0).spot(kx, kz).id();
            int front = b.place("stall_front", spot(fx, 0, fz), fx, 0, fz).yaw(180).cap(2).spot(fx, fz).spot(fx - 1, fz).id();
            b.stall(pr.x0(), pr.z0(), pr.x1(), pr.z1(), 'S', stallKeeper, front, "produce", Math.floorMod(pr.v(), 6));
            break;
        }
        // the lamps: a place on the street beside each post, the lamplighter's round of them all (round the green by angle)
        lampPlace = new int[p.lamps.size()];
        Integer[] order = new Integer[p.lamps.size()];
        for (int k = 0; k < p.lamps.size(); k++) {
            VillagePlan.Lamp l = p.lamps.get(k);
            VillagePlan.Street st = p.streets.get(l.street());
            int node = streetNode[l.street()][l.point()];
            double sx = st.x()[l.point()], sz = st.z()[l.point()];
            lampPlace[k] = b.place("lamp", node, sx, st.lift()[l.point()], sz).yaw(yawTo(sx, sz, l.x() + 0.5, l.z() + 0.5)).spot(sx, sz).id();
            order[k] = k;
        }
        if (lampPlace.length > 0) {
            java.util.Arrays.sort(order, (u, v) -> Double.compare(Math.atan2(p.lamps.get(u).z(), p.lamps.get(u).x()),
                    Math.atan2(p.lamps.get(v).z(), p.lamps.get(v).x())));
            int[] round = new int[order.length];
            for (int k = 0; k < order.length; k++) round[k] = lampPlace[order[k]];
            lampRoute = b.route("round", round);
            for (int k = 0; k < p.lamps.size(); k++) b.lamp(lampPlace[k], lampRoute, p.lamps.get(k).x() + 0.5, p.lamps.get(k).z() + 0.5);
        }
        if (postPlaces.size() >= 2) watchRoute = b.route("round", postPlaces.get(0), notice, postPlaces.get(1), notice);
        List<Integer> er = new ArrayList<>(List.of(wash, notice));
        er.addAll(benchPlaces);
        if (play >= 0) er.add(play);
        errands = b.route("wander", er.stream().mapToInt(Integer::intValue).toArray());
        List<Integer> cr = new ArrayList<>();
        for (int k = 0; k < p.buildings.size(); k++) {
            String u = p.buildings.get(k).use();
            if (u.equals("barn") || u.equals("granary") || u.equals("inn") || u.equals("mill") || u.equals("smithy")) cr.add(doorPlace[k]);
        }
        if (!roadPlaces.isEmpty()) cr.add(postPlaces.getFirst());
        if (cr.size() >= 2) carterRoute = b.route("round", cr.stream().mapToInt(Integer::intValue).toArray());
        List<Integer> ev = new ArrayList<>(innPlaces);
        ev.addAll(benchPlaces);
        eveningRoute = b.route("wander", ev.isEmpty() ? new int[]{notice} : ev.stream().mapToInt(Integer::intValue).toArray());
    }

    private int buildingOf(int plot) {
        for (int k = 0; k < p.buildings.size(); k++) if (p.buildings.get(k).plot() == plot) return k;
        return -1;
    }

    // ---- people ----------------------------------------------------------------------------------------------------

    private void people() {
        int[] hh = new int[p.households.size()];
        for (int h = 0; h < hh.length; h++) {
            VillagePlan.Household ho = p.households.get(h);
            hh[h] = b.household(ho.family(), doorPlace[ho.building()], lotOf[ho.building()]);
        }
        Map<String, Integer> groups = new HashMap<>();
        boolean stallTaken = false;
        for (int i = 0; i < p.people.size(); i++) {
            VillagePlan.Person v = p.people.get(i);
            String job = v.job();
            if (job.equals("resident") && stallKeeper >= 0 && !stallTaken && v.age() >= 1 && v.age() <= 2) {
                job = "stallkeeper";
                stallTaken = true;
            }
            VillagePlan.Household ho = p.households.get(v.household());
            int home = doorPlace[ho.building()];
            RosterBuilder.Person pr = b.person(v.given(), v.family(), job).household(hh[v.household()]).home(home).build(v.build()).age(v.age());
            if (v.age() == 0) pr.scale(0.68f).speed(1.08f);
            else if (v.age() == 3) pr.speed(0.78f);
            String g = switch (job) {
                case "farmer", "shepherd" -> "fields";
                case "smith", "carter", "miller" -> "work";
                case "tavernkeeper", "stallkeeper" -> "inn";
                case "watch", "lamplighter", "bellringer" -> "watch";
                case "child" -> "children";
                default -> "village";
            };
            pr.group(groups.computeIfAbsent(g, b::group));
            if (inn >= 0) pr.tavern(0);
            day(pr, i, job, v, home);
        }
    }

    /** A segment start nudged by up to ±k ticks, per person (a village never moves as one). */
    private int at(int i, int start, int k) {
        return Math.floorMod(start + (int) Math.floorMod(hash(i, start), (long) (2 * k + 1)) - k, 24000);
    }

    /** One person's day by their trade. */
    private void day(RosterBuilder.Person pr, int i, String job, VillagePlan.Person v, int home) {
        boolean evening = inn >= 0 && v.age() >= 1 && Math.floorMod(hash(i, 77), 10) < 5;
        int work = -1;
        switch (job) {
            case "farmer" -> work = fieldPlaces.isEmpty() ? errands : fieldPlaces.get(Math.floorMod(v.workIndex(), fieldPlaces.size()));
            case "shepherd" -> work = penPlaces.isEmpty() ? errands : penPlaces.get(Math.floorMod(v.workIndex(), penPlaces.size()));
            case "woodcutter" -> work = woods;
            case "smith" -> work = anvil;
            case "fisher" -> work = net;
            case "miller" -> work = mill;
            case "scribe" -> work = notice;
            case "stallkeeper" -> work = stallKeeper;
            default -> {
            }
        }
        switch (job) {
            case "farmer", "shepherd", "woodcutter", "smith", "fisher", "miller", "scribe", "stallkeeper" -> {
                if (work < 0) {
                    resident(pr, i, home, evening);
                    return;
                }
                pr.work(work);
                pr.segment(0, "home", -1).segment(at(i, 900, 250), "stand", work).segment(at(i, 5400, 150), "home", -1)
                        .segment(at(i, 6900, 200), "stand", work).segment(at(i, 11700, 250), "home", -1);
                if (evening) pr.segment(at(i, 12500, 250), "wander", eveningRoute, -1, 900, 0).segment(at(i, 16200, 300), "home", -1);
            }
            case "tavernkeeper" -> {
                pr.work(innOut >= 0 ? innOut : home);
                pr.segment(0, "home", -1).segment(at(i, 4000, 100), "stand", innOut >= 0 ? innOut : home).segment(at(i, 9000, 100), "home", -1)
                        .segment(at(i, 11800, 100), "stand", innOut >= 0 ? innOut : home).segment(at(i, 17500, 100), "home", -1);
            }
            case "hunter", "miner" -> {
                int road = roadPlaces.isEmpty() ? home : roadPlaces.get(Math.floorMod(i, roadPlaces.size()));
                pr.work(road);
                // out by the road at dawn (inside: gone into the wilds), back at dusk with the day's take
                pr.segment(0, "home", -1).segment(at(i, job.equals("hunter") ? 300 : 700, 200), "inside", road)
                        .segment(at(i, 11900, 250), "home", -1);
                if (evening) pr.segment(at(i, 12900, 250), "wander", eveningRoute, -1, 900, 0).segment(at(i, 16300, 300), "home", -1);
            }
            case "carter" -> {
                pr.segment(0, "home", -1);
                if (carterRoute >= 0) pr.segment(at(i, 1000, 200), "round", carterRoute, 0, 400, 1);
                pr.segment(at(i, 5400, 150), "home", -1);
                if (carterRoute >= 0) pr.segment(at(i, 6900, 200), "round", carterRoute, 1, 400, 1);
                pr.segment(at(i, 11000, 200), "home", -1);
            }
            case "washer" -> {
                pr.work(wash);
                pr.segment(0, "home", -1).segment(at(i, 1500, 200), "stand", wash).segment(at(i, 5400, 150), "home", -1)
                        .segment(at(i, 7000, 200), "wander", errands, -1, 300, 0).segment(at(i, 10400, 200), "home", -1);
            }
            case "lamplighter" -> {
                // the round at dusk lights them (flag 2); the one at dawn puts them out
                pr.segment(0, "home", -1);
                if (lampRoute >= 0) pr.segment(at(i, 23050, 50), "round", lampRoute, 0, 40, 0).segment(at(i, 600, 50), "home", -1);
                pr.segment(at(i, 3000, 200), "wander", errands, -1, 400, 0).segment(at(i, 5400, 150), "home", -1);
                if (lampRoute >= 0) pr.segment(at(i, 12150, 40), "round", lampRoute, 0, 40, 2);
                pr.segment(at(i, 13600, 100), "home", -1);
            }
            case "bellringer" -> {
                // at the bell for dawn and dusk (it rings the hour only while he's there)
                if (bell < 0) {
                    resident(pr, i, home, evening);
                    return;
                }
                pr.work(bell);
                pr.segment(0, "stand", bell).segment(at(i, 700, 100), "home", -1).segment(at(i, 2500, 200), "wander", errands, -1, 400, 0)
                        .segment(at(i, 5400, 150), "home", -1).segment(11500, "stand", bell).segment(at(i, 12600, 100), "home", -1)
                        .segment(23300, "stand", bell);
            }
            case "watch" -> {
                int post = postPlaces.isEmpty() ? home : postPlaces.get(Math.floorMod(i, postPlaces.size()));
                pr.work(post);
                pr.segment(0, "home", -1).segment(at(i, 11400, 200), "stand", post);
                if (watchRoute >= 0) pr.segment(at(i, 13200, 200), "round", watchRoute, Math.floorMod(i, 2) * 2, 600, 1);
                pr.segment(at(i, 22800, 200), "home", -1);
            }
            case "elder" -> {
                int bench = benchPlaces.isEmpty() ? notice : benchPlaces.get(Math.floorMod(i, benchPlaces.size()));
                pr.work(bench);
                pr.segment(0, "home", -1).segment(at(i, 2200, 300), "stand", bench).segment(at(i, 5400, 150), "home", -1)
                        .segment(at(i, 7500, 300), "stand", bench).segment(at(i, 10500, 200), "home", -1);
                if (inn >= 0) pr.segment(at(i, 13000, 200), "stand", innOut).segment(at(i, 15800, 200), "home", -1);
            }
            case "child" -> {
                pr.work(play);
                pr.segment(0, "home", -1).segment(at(i, 1800, 300), "stand", play).segment(at(i, 5400, 150), "home", -1)
                        .segment(at(i, 7200, 300), "wander", errands, -1, 300, 0).segment(at(i, 10800, 200), "home", -1);
            }
            default -> resident(pr, i, home, evening);
        }
    }

    private void resident(RosterBuilder.Person pr, int i, int home, boolean evening) {
        pr.segment(0, "home", -1).segment(at(i, 2200, 400), "wander", errands, -1, 400, 0).segment(at(i, 5400, 150), "home", -1)
                .segment(at(i, 7400, 400), "wander", errands, -1, 400, 0).segment(at(i, 10800, 300), "home", -1);
        if (evening) pr.segment(at(i, 12600, 300), "wander", eveningRoute, -1, 900, 0).segment(at(i, 16000, 300), "home", -1);
    }

    // ---- what the client draws: chimneys, the mill's wheel, the well ---------------------------------------------------

    private void emitters() {
        for (int[] c : SettlementPaint.chimneys(p, p.plots.size())) {
            String use = p.plots.get(c[3]).use();
            int kind = switch (use) {
                case "smithy" -> 3;
                case "inn" -> 2;
                case "barn", "granary" -> -1;
                default -> 1;
            };
            if (kind >= 0) b.emitter(c[0], c[1], c[2], kind);
        }
        if (p.wheel != null) b.emitter(p.wheel.cx(), p.wheel.axle(), p.wheel.cz(), 6);
        b.emitter(p.well[0], 1, p.well[1], 7);
    }
}
