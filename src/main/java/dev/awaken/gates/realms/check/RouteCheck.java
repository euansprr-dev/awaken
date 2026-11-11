package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a land's run can be played as its sheet says (SPEC §50.9): every leg has a job and can be walked to, the first
 * fight is close to the Mouth, no walk between two legs is long, there is a way round (a loop or a shortcut), a place
 * under a roof goes up or down, and every fight has room for its monsters. A land whose run nobody designed has only the
 * run read off its sites; that is reported and never counted against it.
 */
public final class RouteCheck {
    private RouteCheck() {}

    /** A minute's walk and a first fight within about half of one, in blocks; the room one monster needs to be fought in, in columns. */
    static final int HOP = 260, FIRST_FIGHT = 150, ROOM_EACH = 12, CRAWL_HEIGHT = 20;

    public record Report(String summary, List<String> problems) {}

    public static Report check(Land land, WalkCheck.Walk walk) {
        List<Land.Leg> route = land.route();
        Map<Land.LegKind, Integer> kinds = new EnumMap<>(Land.LegKind.class);
        for (Land.Leg l : route) kinds.merge(l.kind(), 1, Integer::sum);
        StringBuilder mix = new StringBuilder();
        for (var e : kinds.entrySet()) mix.append(mix.isEmpty() ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey().name().toLowerCase(java.util.Locale.ROOT));
        if (!land.designed()) return new Report("route: read off the sites, not designed yet (" + route.size() + " legs: " + mix + ")", List.of());

        List<String> bad = new ArrayList<>();
        Map<String, Land.Leg> byId = new HashMap<>();
        for (Land.Leg l : route) if (byId.put(l.id(), l) != null) bad.add("two legs are called " + l.id());
        Land.Site mouth = land.mouth();
        double firstFight = Double.MAX_VALUE;
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        boolean shortcut = false, lord = false;
        for (Land.Leg l : route) {
            lowest = Math.min(lowest, l.y());
            highest = Math.max(highest, l.y());
            if (walk != null && !walk.reaches(l.x(), l.y(), l.z(), 4)) bad.add(l.id() + " (" + l.kind() + ") at " + l.x() + " " + l.y() + " " + l.z() + " can't be walked to from the Mouth");
            for (String to : l.exits()) {
                Land.Leg next = byId.get(to);
                if (next == null) bad.add(l.id() + " leads on to " + to + ", which isn't a leg");
                else if (dist(l, next) > HOP) bad.add("from " + l.id() + " to " + to + " is " + (int) dist(l, next) + " blocks, over a minute's walk");
            }
            switch (l.kind()) {
                case FIGHT, AMBUSH -> {
                    firstFight = Math.min(firstFight, Math.hypot(l.x() - mouth.x(), l.z() - mouth.z()));
                    if (l.site() < 0 || l.site() >= land.sites(Land.SiteKind.DEN).size()) bad.add(l.id() + " has no den of its own");
                    room(bad, walk, l, l.pack().isEmpty() ? 5 : l.packSize());
                }
                case WARDEN -> {
                    if (l.site() < 0 || l.site() >= land.sites(Land.SiteKind.WARDEN).size()) bad.add(l.id() + " has no Warden of its own");
                    room(bad, walk, l, 4 + (l.pack().isEmpty() ? 3 : l.packSize()));
                }
                case TRAP -> {
                    if (land.traps().stream().noneMatch(t -> t.leg().equals(l.id()))) bad.add(l.id() + " is a trap stretch with no trap in it");
                }
                case MOVEMENT -> {
                    if (land.courses().stream().noneMatch(c -> c.leg().equals(l.id()))) bad.add(l.id() + " is a movement stretch with nowhere to be caught from a fall");
                }
                case SHORTCUT -> {
                    shortcut = true;
                    if (land.change("shortcut_" + l.id()).isEmpty()) bad.add(l.id() + " is a shortcut that opens nothing");
                }
                case LORD -> lord = true;
                default -> {}
            }
        }
        if (!lord) bad.add("the run never reaches the Lord");
        if (firstFight > FIRST_FIGHT) bad.add("the first fight is " + (firstFight == Double.MAX_VALUE ? "nowhere" : (int) firstFight + " blocks from the Mouth") + ", more than " + FIRST_FIGHT);
        // a stand is one room of waves, its Warden and the Lord (SPEC §50.9): there is nothing to walk back through and nothing to climb
        boolean stand = land instanceof dev.awaken.gates.realms.kit.crawl.GeneratedCrawl<?> c && c.stand();
        if (!stand && !shortcut && !loops(route, byId)) bad.add("there is no way round: no loop and no shortcut");
        if (!stand && land.indoors() && highest - lowest < CRAWL_HEIGHT) bad.add("a place under a roof that only rises or falls " + (highest - lowest) + " blocks (wants " + CRAWL_HEIGHT + ")");
        String rolled = land instanceof dev.awaken.gates.realms.kit.crawl.GeneratedCrawl<?> g ? (g.stand() ? ", a stand of waves" : ", rolled " + g.size().name().toLowerCase(java.util.Locale.ROOT) + " " + g.shape().name().toLowerCase(java.util.Locale.ROOT)) : "";
        String head = "route: " + route.size() + " legs (" + mix + "), " + (highest - lowest) + " blocks of height" + rolled;
        return new Report(bad.isEmpty() ? head : head + ", " + bad.size() + " wrong", bad);
    }

    private static void room(List<String> bad, WalkCheck.Walk walk, Land.Leg l, int monsters) {
        if (walk == null) return;
        int have = walk.room(l.x(), l.y(), l.z(), l.radius());
        if (have < monsters * ROOM_EACH) bad.add(l.id() + " has " + have + " columns to fight " + monsters + " monsters in (wants " + monsters * ROOM_EACH + ")");
    }

    private static double dist(Land.Leg a, Land.Leg b) {
        return Math.sqrt((a.x() - b.x()) * (double) (a.x() - b.x()) + (a.y() - b.y()) * (double) (a.y() - b.y()) + (a.z() - b.z()) * (double) (a.z() - b.z()));
    }

    /** Whether the legs' exits, read both ways, close a ring anywhere. */
    private static boolean loops(List<Land.Leg> route, Map<String, Land.Leg> byId) {
        Map<String, String> root = new HashMap<>();
        for (Land.Leg l : route) root.put(l.id(), l.id());
        java.util.Set<String> joined = new java.util.HashSet<>();
        for (Land.Leg l : route) for (String to : l.exits()) {
            if (!byId.containsKey(to) || !joined.add(l.id().compareTo(to) < 0 ? l.id() + ">" + to : to + ">" + l.id())) continue;
            String a = find(root, l.id()), b = find(root, to);
            if (a.equals(b)) return true;
            root.put(a, b);
        }
        return false;
    }

    private static String find(Map<String, String> root, String id) {
        while (!root.get(id).equals(id)) id = root.get(id);
        return id;
    }
}
