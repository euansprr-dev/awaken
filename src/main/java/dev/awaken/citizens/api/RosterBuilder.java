package dev.awaken.citizens.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.awaken.citizens.Roster;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a settlement's people and ground in code (SPEC §28.4): the street graph, the lots, the places people stand at
 * (doors, anvils, the well, stalls, lamps, bells), the stations that use them, households and workplaces, and each
 * person's day, all as offsets from the settlement's origin. {@link #build()} writes the roster in exactly the shape
 * {@code tools/city/citizens.py} writes Carillon's ({@code data/awaken/city/citizens.json}, schemaVersion 1), so the
 * engine reads both the same way. W3's village builder lays its plan out through this; the GameTest's hamlet is the
 * smallest example ({@code citizens.TestHamlet}).
 *
 * <p>Indices returned are the ones the roster uses: node and place ids in the order made, household, group and person
 * ids likewise. A day is a list of segments ({@link #segment}): the one in force is the last to start at or before the
 * time of day (Carillon's rule), so a day needs one starting near 0 or it wraps from its last.
 */
public final class RosterBuilder implements RosterSource {
    // the ground
    private final List<float[]> nodes = new ArrayList<>(); // x, ry, z
    private final List<Integer> nodeKind = new ArrayList<>(), nodeRef = new ArrayList<>();
    private final List<int[]> edges = new ArrayList<>(); // a, b, lane tenths
    private final List<Lot> lots = new ArrayList<>();
    private final List<int[]> water = new ArrayList<>();
    private final List<String> districts = new ArrayList<>(List.of(""));
    // places, routes, stations
    private final List<Place> places = new ArrayList<>();
    private final List<int[]> routes = new ArrayList<>();
    private final List<String> routeKinds = new ArrayList<>();
    private final JsonArray lamps = new JsonArray(), stalls = new JsonArray(), bells = new JsonArray(), bakeries = new JsonArray(),
            smithies = new JsonArray(), washplaces = new JsonArray(), taverns = new JsonArray();
    // people
    private final List<int[]> households = new ArrayList<>(); // home place, lot
    private final List<String> families = new ArrayList<>(), groupKinds = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();
    private int extras;

    /** The settlement's own name is district 0; returns the new district's id. */
    public int district(String name) {
        districts.add(name);
        return districts.size() - 1;
    }

    // ---- the ground ----------------------------------------------------------------------------------------------

    /** A street node (people walk between nodes; doors and landmarks are ends, never shortcuts). */
    public int junction(double x, int ry, double z) {
        return node(x, ry, z, 0, -1);
    }

    /** A door's node, on the lot it opens (bodies go in and out through the door block a step from it). */
    public int door(double x, int ry, double z, int lot) {
        return node(x, ry, z, 1, lot);
    }

    private int node(double x, int ry, double z, int kind, int ref) {
        nodes.add(new float[]{(float) x, ry, (float) z});
        nodeKind.add(kind);
        nodeRef.add(ref);
        return nodes.size() - 1;
    }

    /** A walkable edge between two nodes; {@code lane}: the keep-right offset in tenths of a block (0 on a path). */
    public RosterBuilder street(int a, int b, int lane) {
        edges.add(new int[]{a, b, Math.clamp(lane, 0, 30)});
        return this;
    }

    public RosterBuilder street(int a, int b) {
        return street(a, b, 0);
    }

    /** A plot (box, roof top above the origin, door cell or MIN_VALUE) with its tags ("house" makes a fire possible). */
    public int lot(int x0, int z0, int x1, int z1, int top, int doorX, int doorZ, String... tags) {
        Set<String> t = new LinkedHashSet<>(Arrays.asList(tags));
        if (t.contains("house") && doorX != Integer.MIN_VALUE) t.add("fire");
        lots.add(new Lot(lots.size(), x0, z0, x1, z1, top, doorX, doorZ, Set.copyOf(t)));
        return lots.size() - 1;
    }

    /** A cell a bucket line can draw water from (a well's shaft, a trough, a pier's edge). */
    public RosterBuilder water(int x, int z) {
        water.add(new int[]{x, z});
        return this;
    }

    // ---- places and stations ---------------------------------------------------------------------------------------

    /** A place people go to (Roster.PLACE_KINDS: door, stand, anvil, wash, lamp, bell, sit, play, tavern_out, ...). */
    public Place place(String kind, int node, double x, int ry, double z) {
        if (Arrays.asList(Roster.PLACE_KINDS).indexOf(kind) < 0) throw new IllegalArgumentException("no place kind " + kind);
        Place p = new Place(places.size(), kind, node, (float) x, ry, (float) z);
        places.add(p);
        return p;
    }

    /** A round or an errand list: the places in order (a lamplighter's lamps, a watchman's posts, a wanderer's stops). */
    public int route(String kind, int... places) {
        routes.add(places.clone());
        routeKinds.add(kind);
        return routes.size() - 1;
    }

    /** A forge: its lot, its door place, the anvil place, and the anvil block (placed once, offsets from the origin). */
    public RosterBuilder smithy(int lot, int doorPlace, int anvilPlace, int bx, int bry, int bz) {
        JsonObject o = new JsonObject();
        o.addProperty("plot", lot);
        o.addProperty("door", doorPlace);
        o.addProperty("anvil", anvilPlace);
        o.add("block", ints(bx, bry, bz));
        smithies.add(o);
        return this;
    }

    /** A wash place (the well's trough): the places washers stand at and the tub block. */
    public RosterBuilder washplace(int tx, int tRy, int tz, int... places) {
        JsonObject o = new JsonObject();
        o.add("places", ints(places));
        o.add("tub", ints(tx, tRy, tz));
        washplaces.add(o);
        return this;
    }

    /** A street lamp: the lamplighter's place by it, his round, and the post (its head stands four over its ground). */
    public RosterBuilder lamp(int place, int route, double postX, double postZ) {
        places.get(place).ref = lamps.size(); // the lamplighter's place names its lamp
        JsonObject o = new JsonObject();
        o.addProperty("place", place);
        o.addProperty("route", route);
        JsonArray post = new JsonArray();
        post.add(postX);
        post.add(postZ);
        o.add("post", post);
        lamps.add(o);
        return this;
    }

    /** A bell (a belfry box the ringer's place serves; the bell block is found inside it). */
    public RosterBuilder bell(String landmark, int place, int x0, int z0, int x1, int z1, int district) {
        JsonObject o = new JsonObject();
        o.addProperty("landmark", landmark);
        o.addProperty("place", place);
        o.add("box", ints(x0, z0, x1, z1));
        o.addProperty("district", district);
        bells.add(o);
        return this;
    }

    public RosterBuilder bakery(int lot, int doorPlace, int frontPlace) {
        JsonObject o = new JsonObject();
        o.addProperty("plot", lot);
        o.addProperty("door", doorPlace);
        o.addProperty("front", frontPlace);
        bakeries.add(o);
        return this;
    }

    public RosterBuilder tavern(int lot, int doorPlace, int outPlace, int district) {
        JsonObject o = new JsonObject();
        o.addProperty("plot", lot);
        o.addProperty("door", doorPlace);
        o.addProperty("out", outPlace);
        o.addProperty("district", district);
        taverns.add(o);
        return this;
    }

    /** A market stall: the box, counter facing (N, E, S, W), keeper's and customers' places, its good, its colour. */
    public RosterBuilder stall(int x0, int z0, int x1, int z1, char facing, int keeperPlace, int frontPlace, String good, int colour) {
        JsonObject o = new JsonObject();
        o.addProperty("part", stalls.size());
        o.add("box", ints(x0, z0, x1, z1));
        o.addProperty("colour", colour);
        o.addProperty("keeper", keeperPlace);
        o.addProperty("front", frontPlace);
        o.addProperty("good", good);
        o.addProperty("facing", String.valueOf(facing));
        stalls.add(o);
        return this;
    }

    // ---- people ----------------------------------------------------------------------------------------------------

    /** A household living behind a door place on a lot. */
    public int household(String family, int homePlace, int lot) {
        households.add(new int[]{homePlace, lot});
        families.add(family);
        return households.size() - 1;
    }

    /** A workplace group (gossip goes round it); its members are the people given it. */
    public int group(String kind) {
        groupKinds.add(kind);
        return groupKinds.size() - 1;
    }

    /** A person (Roster.JOBS: smith, washer, resident, child, ...); give them a household, a home and a day. */
    public Person person(String given, String family, String job) {
        if (Arrays.asList(Roster.JOBS).indexOf(job) < 0) throw new IllegalArgumentException("no job " + job);
        Person p = new Person(people.size(), given, family, job);
        people.add(p);
        return p;
    }

    /** Event actors kept apart (thieves for a burglary or a pickpocket); none by default. */
    public RosterBuilder extras(int n) {
        extras = Math.max(0, n);
        return this;
    }

    public static final class Place {
        final int id, node, ry;
        final String kind;
        final float x, z;
        float yaw;
        int cap = 1, district, ref = -1;
        String refId = "";
        boolean approach;
        final List<float[]> spots = new ArrayList<>();

        private Place(int id, String kind, int node, float x, int ry, float z) {
            this.id = id;
            this.kind = kind;
            this.node = node;
            this.x = x;
            this.ry = ry;
            this.z = z;
        }

        public int id() {
            return id;
        }

        /** Which way people stand at it (degrees, Minecraft yaw). */
        public Place yaw(float yaw) {
            this.yaw = yaw;
            return this;
        }

        public Place cap(int cap) {
            this.cap = cap;
            return this;
        }

        public Place district(int district) {
            this.district = district;
            return this;
        }

        /** What it belongs to: a lot id for a door, a lamp index for a lamp. */
        public Place ref(int ref) {
            this.ref = ref;
            return this;
        }

        /** A named reference instead (a landmark's id: "association" is the infirmary). */
        public Place ref(String refId) {
            this.refId = refId;
            return this;
        }

        /** The last few blocks onto its spots need a vanilla path (round a stall, through a gate). */
        public Place approach(boolean approach) {
            this.approach = approach;
            return this;
        }

        /** A spot to stand on (offsets from the origin), facing the place's way. */
        public Place spot(double x, double z) {
            spots.add(new float[]{(float) x, (float) z, Float.NaN});
            return this;
        }

        public Place spot(double x, double z, float yaw) {
            spots.add(new float[]{(float) x, (float) z, yaw});
            return this;
        }
    }

    public static final class Person {
        final int id;
        final String given, family, job;
        int household = -1, home = -1, work = -1, group = -1, tavern = -1, build, age = 2;
        float scale = 1f, speed = 1f;
        int[] look = {0, 0, 0, 0, 0, 0};
        final List<int[]> day = new ArrayList<>();

        private Person(int id, String given, String family, String job) {
            this.id = id;
            this.given = given;
            this.family = family;
            this.job = job;
        }

        public int id() {
            return id;
        }

        public Person household(int h) {
            household = h;
            return this;
        }

        /** The door place they live behind. */
        public Person home(int place) {
            home = place;
            return this;
        }

        public Person work(int place) {
            work = place;
            return this;
        }

        public Person group(int g) {
            group = g;
            return this;
        }

        public Person tavern(int t) {
            tavern = t;
            return this;
        }

        /** 0 wide, 1 slim. */
        public Person build(int b) {
            build = b;
            return this;
        }

        /** 0 a child, 1 young, 2 grown, 3 old (Carillon's roster's ages). */
        public Person age(int a) {
            age = a;
            return this;
        }

        public Person scale(float s) {
            scale = s;
            return this;
        }

        public Person speed(float s) {
            speed = s;
            return this;
        }

        /**
         * One segment of the day from {@code start} (time of day, 0 = 06:00): {@code act} (Roster.ACTS: home, inside,
         * stand, round, carry, wander) at {@code target} (a place; a route for round and wander; -1 home for "home").
         */
        public Person segment(int start, String act, int target) {
            return segment(start, act, target, -1, 0, 0);
        }

        /** With the act's extras: alt (a round's first stop, a carry's drop route), dwell ticks, flags (1 loop, 2 lights). */
        public Person segment(int start, String act, int target, int alt, int dwell, int flags) {
            int a = Arrays.asList(Roster.ACTS).indexOf(act);
            if (a < 0) throw new IllegalArgumentException("no act " + act);
            day.add(new int[]{Math.floorMod(start, 24000), a, target, alt, dwell, flags});
            return this;
        }
    }

    // ---- the roster ------------------------------------------------------------------------------------------------

    /** The roster (citizens.json's shape) and the ground, ready for a SettlementSpec. */
    @Override
    public Built build() {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", Roster.SCHEMA);
        o.addProperty("planSha256", "");
        o.addProperty("extraCount", extras);

        JsonObject pl = new JsonObject();
        JsonArray pf = new JsonArray();
        for (String f : List.of("kind", "node", "x", "ry", "z", "yaw", "cap", "ref", "district", "approach", "spots")) pf.add(f);
        pl.add("fields", pf);
        JsonArray rows = new JsonArray();
        for (Place p : places) {
            JsonArray a = new JsonArray();
            a.add(p.kind);
            a.add(p.node);
            a.add(p.x);
            a.add(p.ry);
            a.add(p.z);
            a.add(p.yaw);
            a.add(Math.max(p.cap, p.spots.size()));
            if (!p.refId.isEmpty()) a.add(p.refId);
            else if (p.ref >= 0) a.add(p.ref);
            else a.add(com.google.gson.JsonNull.INSTANCE);
            a.add(p.district);
            a.add(p.approach);
            JsonArray spots = new JsonArray();
            for (float[] s : p.spots) {
                JsonArray sa = new JsonArray();
                sa.add(s[0]);
                sa.add(s[1]);
                sa.add(Float.isNaN(s[2]) ? p.yaw : s[2]);
                spots.add(sa);
            }
            a.add(spots);
            rows.add(a);
        }
        pl.add("rows", rows);
        o.add("places", pl);

        JsonArray rs = new JsonArray();
        for (int k = 0; k < routes.size(); k++) {
            JsonObject r = new JsonObject();
            r.addProperty("id", k);
            r.addProperty("kind", routeKinds.get(k));
            r.add("places", ints(routes.get(k)));
            rs.add(r);
        }
        o.add("routes", rs);
        JsonArray lanes = new JsonArray();
        for (int[] e : edges) lanes.add(e[2]);
        o.add("edgeLane", lanes);
        o.add("lamps", lamps);
        o.add("stalls", stalls);
        o.add("bells", bells);
        o.add("bakeries", bakeries);
        o.add("smithies", smithies);
        o.add("washplaces", washplaces);
        o.add("lines", new JsonArray());
        JsonObject quay = new JsonObject();
        quay.add("pickup", new JsonArray());
        quay.add("crates", new JsonArray());
        o.add("quay", quay);
        o.add("taverns", taverns);

        JsonArray hh = new JsonArray();
        for (int h = 0; h < households.size(); h++) {
            JsonObject s = new JsonObject();
            s.addProperty("id", h);
            s.addProperty("home", households.get(h)[0]);
            s.addProperty("plot", households.get(h)[1]);
            s.addProperty("family", families.get(h));
            JsonArray m = new JsonArray();
            for (Person p : people) if (p.household == h) m.add(p.id);
            s.add("members", m);
            hh.add(s);
        }
        o.add("households", hh);
        JsonArray gr = new JsonArray();
        for (int g = 0; g < groupKinds.size(); g++) {
            JsonObject s = new JsonObject();
            s.addProperty("id", g);
            s.addProperty("kind", groupKinds.get(g));
            JsonArray m = new JsonArray();
            for (Person p : people) if (p.group == g) m.add(p.id);
            s.add("members", m);
            gr.add(s);
        }
        o.add("groups", gr);

        JsonObject cz = new JsonObject();
        JsonArray cf = new JsonArray();
        for (String f : List.of("id", "given", "family", "job", "household", "home", "work", "group", "tavern", "build", "age", "scale",
                "speed", "look", "named")) cf.add(f);
        cz.add("fields", cf);
        JsonArray crow = new JsonArray();
        JsonArray sc = new JsonArray();
        for (Person p : people) {
            if (p.home < 0) throw new IllegalStateException(p.given + " has no home");
            JsonArray a = new JsonArray();
            a.add(p.id);
            a.add(p.given);
            a.add(p.family);
            a.add(p.job);
            a.add(p.household);
            a.add(p.home);
            a.add(p.work);
            a.add(p.group);
            a.add(p.tavern);
            a.add(p.build);
            a.add(p.age);
            a.add(p.scale);
            a.add(p.speed);
            a.add(ints(p.look));
            a.add(com.google.gson.JsonNull.INSTANCE);
            crow.add(a);
            JsonArray day = new JsonArray();
            List<int[]> segs = new ArrayList<>(p.day);
            segs.sort((x, y) -> Integer.compare(x[0], y[0]));
            for (int[] s : segs) {
                JsonArray sa = new JsonArray();
                sa.add(s[0]);
                sa.add(Roster.ACTS[s[1]]);
                sa.add(s[2]);
                sa.add(s[3]);
                sa.add(s[4]);
                sa.add(s[5]);
                day.add(sa);
            }
            sc.add(day);
        }
        cz.add("rows", crow);
        o.add("citizens", cz);
        o.add("schedules", sc);

        Roster roster = Roster.parse(o.toString().getBytes(StandardCharsets.UTF_8));
        int n = nodes.size(), m = edges.size();
        float[] gx = new float[n], gz = new float[n];
        int[] gry = new int[n], ref = new int[n];
        byte[] kind = new byte[n];
        for (int i = 0; i < n; i++) {
            gx[i] = nodes.get(i)[0];
            gry[i] = (int) nodes.get(i)[1];
            gz[i] = nodes.get(i)[2];
            kind[i] = (byte) (int) nodeKind.get(i);
            ref[i] = nodeRef.get(i);
        }
        int[] ea = new int[m], eb = new int[m];
        byte[] lane = new byte[m];
        for (int e = 0; e < m; e++) {
            ea[e] = edges.get(e)[0];
            eb[e] = edges.get(e)[1];
            lane[e] = (byte) edges.get(e)[2];
        }
        return new Built(roster, new Graph(gx, gry, gz, kind, ref, ea, eb, lane), List.copyOf(lots), List.copyOf(water), List.copyOf(districts));
    }

    private static JsonArray ints(int... v) {
        JsonArray a = new JsonArray();
        for (int x : v) a.add(x);
        return a;
    }
}
