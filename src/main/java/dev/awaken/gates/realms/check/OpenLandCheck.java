package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an open land has to be before it may come back (SPEC §50.9 "Open lands come back on the check"; Euan, 12 Nov: open
 * lands were "not fun whatsoever", the dungeon runs "much better, with the movement, the jumping"). On top of the run check
 * ({@link RouteCheck}): a run that was designed, a first fight within a short walk of the Mouth, no long empty walk
 * between two legs, at least two stretches of movement, one of them on a way to each Warden, and each Warden's light in
 * view from the Mouth over the land's ground. The ground alone is read for the view: pieces built on it are left out.
 */
public final class OpenLandCheck {
    private OpenLandCheck() {}

    /** A first fight within about a quarter of a minute's walk, and no walk between two legs much over half a minute. */
    public static final int FIRST_FIGHT = 60, HOP = 90, MOVEMENT = 2;
    /** Where on a Warden's light the view is looked for: from this far above it to this far. */
    static final int LIGHT_LOW = 40, LIGHT_HIGH = 140;

    public static RouteCheck.Report check(Land land) {
        List<String> bad = new ArrayList<>();
        if (!land.designed()) return new RouteCheck.Report("open land: no designed run", List.of("its run isn't designed: it is read off its sites"));
        for (String p : RouteCheck.check(land, null).problems()) bad.add(p);
        List<Land.Leg> route = land.route();
        Map<String, Land.Leg> byId = new HashMap<>();
        for (Land.Leg l : route) byId.put(l.id(), l);
        Land.Leg arrive = null;
        for (Land.Leg l : route) if (l.kind() == Land.LegKind.ARRIVE) arrive = l;
        if (arrive == null) return new RouteCheck.Report("open land: no way in", List.of("the run has no leg to arrive on"));

        // the first fight: the nearest one the run leads to from the way in
        Land.Site mouth = land.mouth();
        double first = Double.MAX_VALUE;
        for (Land.Leg l : reach(arrive, byId, null)) {
            if (l.kind() == Land.LegKind.FIGHT || l.kind() == Land.LegKind.AMBUSH) first = Math.min(first, Math.hypot(l.x() - mouth.x(), l.z() - mouth.z()));
        }
        if (first > FIRST_FIGHT) bad.add("the first fight is " + (first == Double.MAX_VALUE ? "nowhere" : (int) first + " blocks from the Mouth") + ", more than " + FIRST_FIGHT);

        for (Land.Leg l : route) for (String to : l.exits()) {
            Land.Leg next = byId.get(to);
            if (next != null && Math.hypot(l.x() - next.x(), l.z() - next.z()) > HOP) {
                bad.add("from " + l.id() + " to " + to + " is " + (int) Math.hypot(l.x() - next.x(), l.z() - next.z()) + " blocks with nothing on the way (wants " + HOP + ")");
            }
        }

        int moves = 0;
        for (Land.Leg l : route) if (l.kind() == Land.LegKind.MOVEMENT) moves++;
        if (moves < MOVEMENT) bad.add("only " + moves + " stretch" + (moves == 1 ? "" : "es") + " of movement (wants " + MOVEMENT + ")");

        for (Land.Leg w : route) {
            if (w.kind() != Land.LegKind.WARDEN) continue;
            if (!movementOnTheWay(arrive, w, byId)) bad.add(w.id() + " can be reached without a stretch of movement on the way");
            if (!inView(land, mouth, w)) bad.add(w.id() + "'s light can't be seen from the Mouth over the ground");
        }
        String head = "open land: " + route.size() + " legs, " + moves + " of movement, first fight " + (first == Double.MAX_VALUE ? "none" : (int) first + " blocks in");
        return new RouteCheck.Report(bad.isEmpty() ? head : head + ", " + bad.size() + " wrong", bad);
    }

    /** Every leg the run leads to from {@code from} along its exits, not going through {@code avoid}. */
    private static Set<Land.Leg> reach(Land.Leg from, Map<String, Land.Leg> byId, Land.LegKind avoid) {
        Set<Land.Leg> seen = new HashSet<>();
        ArrayDeque<Land.Leg> todo = new ArrayDeque<>();
        todo.add(from);
        seen.add(from);
        while (!todo.isEmpty()) {
            Land.Leg l = todo.poll();
            for (String to : l.exits()) {
                Land.Leg n = byId.get(to);
                if (n == null || (avoid != null && n.kind() == avoid) || !seen.add(n)) continue;
                todo.add(n);
            }
        }
        return seen;
    }

    /** Whether a stretch of movement lies on some way from the arrival to this Warden: one the run reaches that itself leads on to it. */
    static boolean movementOnTheWay(Land.Leg arrive, Land.Leg warden, Map<String, Land.Leg> byId) {
        for (Land.Leg m : reach(arrive, byId, null)) {
            if (m.kind() == Land.LegKind.MOVEMENT && reach(m, byId, null).contains(warden)) return true;
        }
        return false;
    }

    /** Whether a line from head height at the Mouth to some part of a Warden's light passes over the land's ground the whole way. */
    static boolean inView(Land land, Land.Site mouth, Land.Leg w) {
        double ex = mouth.x(), ey = mouth.y() + 1.6, ez = mouth.z();
        for (int h = LIGHT_LOW; h <= LIGHT_HIGH; h += 20) {
            double tx = w.x(), ty = w.y() + h, tz = w.z();
            double len = Math.hypot(tx - ex, tz - ez);
            int steps = Math.max(1, (int) (len / 2));
            boolean clear = true;
            for (int i = 1; i < steps && clear; i++) {
                double f = i / (double) steps, x = ex + (tx - ex) * f, y = ey + (ty - ey) * f, z = ez + (tz - ez) * f;
                if (land.ground((int) Math.floor(x), (int) Math.floor(z)) >= y) clear = false;
            }
            if (clear) return true;
        }
        return false;
    }
}
