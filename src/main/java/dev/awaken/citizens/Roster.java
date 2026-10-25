package dev.awaken.citizens;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Carillon's people as data ({@code data/awaken/city/citizens.json}, written by {@code tools/city/citizens.py} from the
 * city's plan): 240 citizens with a home, a job, a household, a workplace group, a tavern, a look and a seeded day, the
 * places they stand and the routes they walk, and the city's stations (lamps, stalls, bells, bakeries, smithies,
 * wash places, laundry lines, the Quay). Read once from the mod's own resources, on both sides (the client takes names
 * and looks from it), never from data packs: the roster belongs to the one designed city.
 *
 * <p>Coordinates are plan coordinates (blocks from the city's centre, x east, z south; ry above the street). Node and
 * edge indices are the plan's street graph, which the world's {@code awaken:city} data copies in the same order.
 */
public final class Roster {
    public static final int SCHEMA = 1;
    public static final String RESOURCE = "/data/awaken/city/citizens.json";
    /** Event actors (thieves) follow the 240 citizens in every per-record array. */
    public static final int EXTRAS = 8;

    // jobs (the generator's order)
    public static final int LAMPLIGHTER = 0, BELLRINGER = 1, PORTER = 2, STALLKEEPER = 3, BAKER = 4, SMITH = 5, WATCH = 6,
            CRIER = 7, BOATMAN = 8, WASHER = 9, MASON = 10, RUNNER = 11, SHOPKEEPER = 12, TAVERNKEEPER = 13, CLERK = 14,
            ARCHIVIST = 15, HEALER = 16, MILLER = 17, CHILD = 18, RESIDENT = 19, THIEF = 20;
    public static final String[] JOBS = {"lamplighter", "bellringer", "porter", "stallkeeper", "baker", "smith", "watch",
            "crier", "boatman", "washer", "mason", "runner", "shopkeeper", "tavernkeeper", "clerk", "archivist", "healer",
            "miller", "child", "resident", "thief"};
    // schedule acts
    public static final byte HOME = 0, INSIDE = 1, STAND = 2, ROUND = 3, CARRY = 4, WANDER = 5;
    public static final String[] ACTS = {"home", "inside", "stand", "round", "carry", "wander"};
    // place kinds
    public static final String[] PLACE_KINDS = {"door", "stand", "lamp", "stall_keeper", "stall_front", "bell", "anvil",
            "wash", "line", "pickup", "post", "crier", "sit", "play", "mill", "landmark", "tavern_out", "shop_door", "mooring", "net"};
    public static final byte P_DOOR = 0, P_STAND = 1, P_LAMP = 2, P_STALL_KEEPER = 3, P_STALL_FRONT = 4, P_BELL = 5,
            P_ANVIL = 6, P_WASH = 7, P_LINE = 8, P_PICKUP = 9, P_POST = 10, P_CRIER = 11, P_SIT = 12, P_PLAY = 13, P_MILL = 14,
            P_LANDMARK = 15, P_TAVERN_OUT = 16, P_SHOP_DOOR = 17, P_MOORING = 18, P_NET = 19;

    /**
     * A market stall (M4 B3a: a street-life prop, plan.json "props"[part]): its box, the side its counter faces (N 0, E 1,
     * S 2, W 3), the keeper's and the customers' places, its good.
     */
    public record Stall(int part, int x0, int z0, int x1, int z1, int colour, int keeper, int front, String good, int facing) {
        /** The counter's cell k (0 or 1): the middle two of the front row's four. */
        public int counterX(int k) {
            return switch (facing) {
                case 0 -> x0 + 1 + k;
                case 2 -> x1 - 1 - k;
                case 1 -> x1;
                default -> x0;
            };
        }

        public int counterZ(int k) {
            return switch (facing) {
                case 0 -> z0;
                case 2 -> z1;
                case 1 -> z0 + 1 + k;
                default -> z1 - 1 - k;
            };
        }
    }

    public record Bell(String landmark, int place, int x0, int z0, int x1, int z1, int district) {}

    public record Bakery(int plot, int door, int front) {}

    public record Smithy(int plot, int door, int anvil, int bx, int bry, int bz) {}

    public record WashPlace(int[] places, int tx, int tRy, int tz) {}

    public record Line(int ax, int az, int bx, int bz, int ry, int place, int cells) {}

    public record Tavern(int plot, int door, int out, int district) {}

    public record Household(int id, int home, int plot, String family, int[] members) {}

    public record Group(int id, String kind, int[] members) {}

    public final String sha256, planSha256;
    public final int bytes;

    // places
    public final int places;
    public final byte[] pKind;
    public final int[] pNode, pRy, pCap, pDistrict, pRef;
    public final String[] pRefId;
    public final float[] pX, pZ, pYaw;
    public final boolean[] pPath;
    /** Spots of place p are spotStart[p] .. spotStart[p + 1] - 1. */
    public final int[] spotStart;
    public final float[] spotX, spotZ, spotYaw;

    // routes
    public final String[] routeKind;
    public final int[][] routePlaces;
    /** Keep-right lane offset per street edge, in tenths of a block. */
    public final byte[] edgeLane;
    /**
     * The wall walk joined to the street graph (M4 Walls; tools/city/citizens.py "wallWalk"): nodes appended after the
     * plan's (plan x, row, plan z) and edges after its (absolute node indices, the move; 9 is a tower's stair, hidden).
     */
    public final float[] wallX, wallZ;
    public final int[] wallRy, wallA, wallB, wallMove;

    // stations
    public final int[] lampPlace, lampRoute;
    public final float[] lampX, lampZ;
    public final List<Stall> stalls;
    public final List<Bell> bells;
    public final List<Bakery> bakeries;
    public final List<Smithy> smithies;
    public final List<WashPlace> washplaces;
    public final List<Line> lines;
    public final int[] pickups;
    public final int[][] crates;
    /** (M4 B3b) The berths along both quays: a boatman's place on the edge, and where his boat lies (plan x, z; yaw). */
    public final List<Mooring> moorings;

    public record Mooring(int place, float boatX, float boatZ, float yaw) {}
    public final List<Tavern> taverns;
    public final List<Household> households;
    public final List<Group> groups;

    // citizens (then the extras)
    public final int citizens, records;
    public final String[] given, family, named;
    public final int[] job, household, home, work, group, tavern, build, age;
    public final float[] scale, speed;
    public final int[][] look;
    /** Segments of record i are segBase[i] .. segBase[i] + segCount[i] - 1. */
    public final int[] segBase, segCount, segStart, segTarget, segAlt, segDwell;
    public final byte[] segAct, segFlags;

    private static volatile @Nullable Roster instance;
    private static volatile boolean missing;

    /** The roster, read once; null when the mod has none (a build without the generated file). */
    public static @Nullable Roster get() {
        Roster r = instance;
        if (r != null || missing) return r;
        synchronized (Roster.class) {
            if (instance == null && !missing) {
                try (InputStream in = Roster.class.getResourceAsStream(RESOURCE)) {
                    if (in == null) {
                        missing = true;
                        Awaken.LOG.warn("[citizens] no {}: Carillon has no people in this build", RESOURCE);
                        return null;
                    }
                    instance = new Roster(in.readAllBytes());
                } catch (IOException e) {
                    missing = true;
                    Awaken.LOG.error("[citizens] cannot read {}", RESOURCE, e);
                }
            }
            return instance;
        }
    }

    private Roster(byte[] raw) {
        bytes = raw.length;
        sha256 = sha256(raw);
        JsonObject o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        int schema = o.get("schemaVersion").getAsInt();
        if (schema != SCHEMA) throw new IllegalStateException("citizens schema " + schema + ", expected " + SCHEMA);
        planSha256 = o.has("planSha256") ? o.get("planSha256").getAsString() : "";

        // places
        JsonObject pl = o.getAsJsonObject("places");
        List<String> pf = strings(pl.getAsJsonArray("fields"));
        JsonArray rows = pl.getAsJsonArray("rows");
        places = rows.size();
        pKind = new byte[places];
        pNode = new int[places];
        pRy = new int[places];
        pCap = new int[places];
        pDistrict = new int[places];
        pRef = new int[places];
        pRefId = new String[places];
        pX = new float[places];
        pZ = new float[places];
        pYaw = new float[places];
        pPath = new boolean[places];
        spotStart = new int[places + 1];
        List<float[]> spots = new ArrayList<>();
        int fKind = pf.indexOf("kind"), fNode = pf.indexOf("node"), fX = pf.indexOf("x"), fRy = pf.indexOf("ry"), fZ = pf.indexOf("z"),
                fYaw = pf.indexOf("yaw"), fCap = pf.indexOf("cap"), fRef = pf.indexOf("ref"), fDist = pf.indexOf("district"),
                fAppr = pf.indexOf("approach"), fSpots = pf.indexOf("spots");
        for (int p = 0; p < places; p++) {
            JsonArray a = rows.get(p).getAsJsonArray();
            pKind[p] = (byte) indexOrName(a.get(fKind), PLACE_KINDS);
            pNode[p] = a.get(fNode).getAsInt();
            pX[p] = a.get(fX).getAsFloat();
            pRy[p] = Math.round(a.get(fRy).getAsFloat());
            pZ[p] = a.get(fZ).getAsFloat();
            pYaw[p] = a.get(fYaw).getAsFloat();
            pCap[p] = a.get(fCap).getAsInt();
            JsonElement ref = a.get(fRef);
            if (ref == null || ref.isJsonNull()) {
                pRef[p] = -1;
                pRefId[p] = "";
            } else if (ref.getAsJsonPrimitive().isNumber()) {
                pRef[p] = ref.getAsInt();
                pRefId[p] = "";
            } else {
                pRef[p] = -1;
                pRefId[p] = ref.getAsString();
            }
            pDistrict[p] = a.get(fDist).getAsInt();
            JsonElement ap = a.get(fAppr);
            pPath[p] = ap != null && !ap.isJsonNull() && (ap.getAsJsonPrimitive().isBoolean() ? ap.getAsBoolean() : ap.getAsInt() != 0);
            spotStart[p] = spots.size();
            for (JsonElement s : a.get(fSpots).getAsJsonArray()) {
                JsonArray sa = s.getAsJsonArray();
                spots.add(new float[]{sa.get(0).getAsFloat(), sa.get(1).getAsFloat(), sa.size() > 2 ? sa.get(2).getAsFloat() : pYaw[p]});
            }
        }
        spotStart[places] = spots.size();
        spotX = new float[spots.size()];
        spotZ = new float[spots.size()];
        spotYaw = new float[spots.size()];
        for (int k = 0; k < spots.size(); k++) {
            spotX[k] = spots.get(k)[0];
            spotZ[k] = spots.get(k)[1];
            spotYaw[k] = spots.get(k)[2];
        }

        // routes (by id)
        JsonArray rs = o.getAsJsonArray("routes");
        int nr = 0;
        for (JsonElement e : rs) nr = Math.max(nr, e.getAsJsonObject().get("id").getAsInt() + 1);
        routeKind = new String[nr];
        routePlaces = new int[nr][];
        for (JsonElement e : rs) {
            JsonObject r = e.getAsJsonObject();
            int id = r.get("id").getAsInt();
            routeKind[id] = r.get("kind").getAsString();
            routePlaces[id] = ints(r.getAsJsonArray("places"));
        }
        for (int r = 0; r < nr; r++) if (routePlaces[r] == null) routePlaces[r] = new int[0];

        JsonObject ww = o.has("wallWalk") ? o.getAsJsonObject("wallWalk") : null;
        JsonArray wn = ww != null ? ww.getAsJsonArray("nodes") : new JsonArray(), we = ww != null ? ww.getAsJsonArray("edges") : new JsonArray();
        wallX = new float[wn.size()];
        wallZ = new float[wn.size()];
        wallRy = new int[wn.size()];
        for (int k = 0; k < wn.size(); k++) {
            JsonArray q = wn.get(k).getAsJsonArray();
            wallX[k] = q.get(0).getAsFloat();
            wallRy[k] = q.get(1).getAsInt();
            wallZ[k] = q.get(2).getAsFloat();
        }
        wallA = new int[we.size()];
        wallB = new int[we.size()];
        wallMove = new int[we.size()];
        for (int k = 0; k < we.size(); k++) {
            JsonArray q = we.get(k).getAsJsonArray();
            wallA[k] = q.get(0).getAsInt();
            wallB[k] = q.get(1).getAsInt();
            wallMove[k] = q.get(2).getAsInt();
        }

        int[] lanes = ints(o.getAsJsonArray("edgeLane"));
        edgeLane = new byte[lanes.length];
        for (int k = 0; k < lanes.length; k++) edgeLane[k] = (byte) Math.clamp(lanes[k], 0, 30);

        JsonArray ls = o.getAsJsonArray("lamps");
        lampPlace = new int[ls.size()];
        lampRoute = new int[ls.size()];
        lampX = new float[ls.size()];
        lampZ = new float[ls.size()];
        for (int k = 0; k < ls.size(); k++) {
            JsonObject l = ls.get(k).getAsJsonObject();
            lampPlace[k] = l.get("place").getAsInt();
            lampRoute[k] = l.has("route") ? l.get("route").getAsInt() : -1;
            JsonArray post = l.getAsJsonArray("post");
            lampX[k] = post.get(0).getAsFloat();
            lampZ[k] = post.get(1).getAsFloat();
        }

        List<Stall> st = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("stalls")) {
            JsonObject s = e.getAsJsonObject();
            int[] b = ints(s.getAsJsonArray("box"));
            int facing = s.has("facing") ? "NESW".indexOf(s.get("facing").getAsString().charAt(0)) : 2;
            st.add(new Stall(s.get("part").getAsInt(), b[0], b[1], b[2], b[3], s.get("colour").getAsInt(), s.get("keeper").getAsInt(),
                    s.get("front").getAsInt(), s.has("good") ? s.get("good").getAsString() : "produce", Math.max(0, facing)));
        }
        stalls = List.copyOf(st);

        List<Bell> bl = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("bells")) {
            JsonObject s = e.getAsJsonObject();
            int[] b = ints(s.getAsJsonArray("box"));
            bl.add(new Bell(s.get("landmark").getAsString(), s.get("place").getAsInt(), b[0], b[1], b[2], b[3],
                    s.has("district") ? s.get("district").getAsInt() : 0));
        }
        bells = List.copyOf(bl);

        List<Bakery> bk = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("bakeries")) {
            JsonObject s = e.getAsJsonObject();
            bk.add(new Bakery(s.get("plot").getAsInt(), s.get("door").getAsInt(), s.get("front").getAsInt()));
        }
        bakeries = List.copyOf(bk);

        List<Smithy> sm = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("smithies")) {
            JsonObject s = e.getAsJsonObject();
            int[] b = ints(s.getAsJsonArray("block"));
            sm.add(new Smithy(s.get("plot").getAsInt(), s.get("door").getAsInt(), s.get("anvil").getAsInt(), b[0], b[1], b[2]));
        }
        smithies = List.copyOf(sm);

        List<WashPlace> wp = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("washplaces")) {
            JsonObject s = e.getAsJsonObject();
            int[] t = ints(s.getAsJsonArray("tub"));
            wp.add(new WashPlace(ints(s.getAsJsonArray("places")), t[0], t[1], t[2]));
        }
        washplaces = List.copyOf(wp);

        List<Line> ln = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("lines")) {
            JsonObject s = e.getAsJsonObject();
            int[] a = ints(s.getAsJsonArray("a")), b = ints(s.getAsJsonArray("b"));
            ln.add(new Line(a[0], a[1], b[0], b[1], s.get("ry").getAsInt(), s.get("place").getAsInt(),
                    s.has("cells") ? s.get("cells").getAsInt() : Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1])) + 1));
        }
        lines = List.copyOf(ln);

        JsonObject q = o.getAsJsonObject("quay");
        pickups = ints(q.getAsJsonArray("pickup"));
        JsonArray cr = q.getAsJsonArray("crates");
        crates = new int[cr.size()][];
        for (int k = 0; k < cr.size(); k++) crates[k] = ints(cr.get(k).getAsJsonArray());
        List<Mooring> mo = new ArrayList<>();
        if (o.has("moorings")) {
            for (JsonElement e : o.getAsJsonArray("moorings")) {
                JsonObject m = e.getAsJsonObject();
                JsonArray b = m.getAsJsonArray("boat");
                mo.add(new Mooring(m.get("place").getAsInt(), b.get(0).getAsFloat(), b.get(1).getAsFloat(), m.get("yaw").getAsFloat()));
            }
        }
        moorings = List.copyOf(mo);

        List<Tavern> tv = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("taverns")) {
            JsonObject s = e.getAsJsonObject();
            tv.add(new Tavern(s.get("plot").getAsInt(), s.get("door").getAsInt(), s.get("out").getAsInt(), s.get("district").getAsInt()));
        }
        taverns = List.copyOf(tv);

        List<Household> hh = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("households")) {
            JsonObject s = e.getAsJsonObject();
            hh.add(new Household(s.get("id").getAsInt(), s.get("home").getAsInt(), s.get("plot").getAsInt(),
                    s.has("family") ? s.get("family").getAsString() : "", ints(s.getAsJsonArray("members"))));
        }
        households = List.copyOf(hh);

        List<Group> gr = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("groups")) {
            JsonObject s = e.getAsJsonObject();
            gr.add(new Group(s.get("id").getAsInt(), s.get("kind").getAsString(), ints(s.getAsJsonArray("members"))));
        }
        groups = List.copyOf(gr);

        // citizens, then extras
        JsonObject cz = o.getAsJsonObject("citizens");
        List<String> cf = strings(cz.getAsJsonArray("fields"));
        JsonArray crow = cz.getAsJsonArray("rows");
        JsonArray ex = o.has("extras") ? o.getAsJsonArray("extras") : new JsonArray();
        citizens = crow.size();
        records = citizens + EXTRAS;
        given = new String[records];
        family = new String[records];
        named = new String[records];
        job = new int[records];
        household = new int[records];
        home = new int[records];
        work = new int[records];
        group = new int[records];
        tavern = new int[records];
        build = new int[records];
        age = new int[records];
        scale = new float[records];
        speed = new float[records];
        look = new int[records][];
        int cId = cf.indexOf("id"), cGiven = cf.indexOf("given"), cFamily = cf.indexOf("family"), cJob = cf.indexOf("job"),
                cHouse = cf.indexOf("household"), cHome = cf.indexOf("home"), cWork = cf.indexOf("work"), cGroup = cf.indexOf("group"),
                cTavern = cf.indexOf("tavern"), cBuild = cf.indexOf("build"), cAge = cf.indexOf("age"), cScale = cf.indexOf("scale"),
                cSpeed = cf.indexOf("speed"), cLook = cf.indexOf("look"), cNamed = cf.indexOf("named");
        for (int k = 0; k < citizens; k++) {
            JsonArray a = crow.get(k).getAsJsonArray();
            int i = cId >= 0 ? a.get(cId).getAsInt() : k;
            if (i != k) throw new IllegalStateException("citizen rows out of order at " + k);
            given[i] = a.get(cGiven).getAsString();
            family[i] = a.get(cFamily).isJsonNull() ? "" : a.get(cFamily).getAsString();
            job[i] = indexOrName(a.get(cJob), JOBS);
            household[i] = a.get(cHouse).getAsInt();
            home[i] = a.get(cHome).getAsInt();
            work[i] = a.get(cWork).getAsInt();
            group[i] = a.get(cGroup).getAsInt();
            tavern[i] = a.get(cTavern).getAsInt();
            build[i] = a.get(cBuild).getAsInt();
            age[i] = a.get(cAge).getAsInt();
            scale[i] = a.get(cScale).getAsFloat();
            speed[i] = a.get(cSpeed).getAsFloat();
            look[i] = ints(a.get(cLook).getAsJsonArray());
            named[i] = cNamed < 0 || a.get(cNamed).isJsonNull() ? "" : a.get(cNamed).getAsString();
        }
        for (int k = 0; k < EXTRAS; k++) {
            int i = citizens + k;
            JsonObject e = k < ex.size() ? ex.get(k).getAsJsonObject() : null;
            given[i] = "";
            family[i] = "";
            named[i] = "";
            job[i] = THIEF;
            household[i] = -1;
            home[i] = -1;
            work[i] = -1;
            group[i] = -1;
            tavern[i] = -1;
            build[i] = e != null && e.has("build") ? e.get("build").getAsInt() : k & 1;
            age[i] = 1;
            scale[i] = e != null && e.has("scale") ? e.get("scale").getAsFloat() : 0.97f;
            speed[i] = 1f;
            look[i] = e != null && e.has("look") ? ints(e.getAsJsonArray("look")) : new int[]{k % 6, k, k, 0, 0, k};
        }

        JsonArray sc = o.getAsJsonArray("schedules");
        segBase = new int[records];
        segCount = new int[records];
        int total = 0;
        for (int i = 0; i < citizens; i++) total += sc.get(i).getAsJsonArray().size();
        segStart = new int[total];
        segTarget = new int[total];
        segAlt = new int[total];
        segDwell = new int[total];
        segAct = new byte[total];
        segFlags = new byte[total];
        int k = 0;
        for (int i = 0; i < citizens; i++) {
            segBase[i] = k;
            JsonArray day = sc.get(i).getAsJsonArray();
            segCount[i] = day.size();
            for (JsonElement s : day) {
                JsonArray a = s.getAsJsonArray();
                segStart[k] = Math.floorMod(a.get(0).getAsInt(), 24000);
                segAct[k] = (byte) indexOrName(a.get(1), ACTS);
                segTarget[k] = a.get(2).getAsInt();
                segAlt[k] = a.size() > 3 ? a.get(3).getAsInt() : -1;
                segDwell[k] = a.size() > 4 ? a.get(4).getAsInt() : 0;
                segFlags[k] = (byte) (a.size() > 5 ? a.get(5).getAsInt() : 0);
                k++;
            }
        }
        for (int i = citizens; i < records; i++) {
            segBase[i] = total;
            segCount[i] = 0;
        }
        Awaken.LOG.info("[citizens] roster read: {} citizens, {} places, {} spots, {} routes, {} lamps, {} stalls, {} households, sha256 {}",
                citizens, places, spotX.length, routePlaces.length, lampPlace.length, stalls.size(), households.size(), sha256.substring(0, 12));
    }

    // ---- lookups ----------------------------------------------------------------------------------------------

    public int spots(int place) {
        return spotStart[place + 1] - spotStart[place];
    }

    public String name(int i) {
        if (i < 0 || i >= records) return "";
        if (job[i] == THIEF) return "";
        return family[i].isEmpty() ? given[i] : given[i] + " " + family[i];
    }

    public boolean isCitizen(int i) {
        return i >= 0 && i < citizens;
    }

    /** The first citizen with this job (or -1), for commands and tours. */
    public int firstOf(int jobId) {
        for (int i = 0; i < citizens; i++) if (job[i] == jobId) return i;
        return -1;
    }

    public int namedIndex(String who) {
        for (int i = 0; i < citizens; i++) if (named[i].equals(who)) return i;
        return -1;
    }

    // ---- json helpers -------------------------------------------------------------------------------------------

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = Math.round(a.get(i).getAsFloat());
        return out;
    }

    private static int indexOrName(JsonElement e, String[] names) {
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
        String s = e.getAsString();
        for (int i = 0; i < names.length; i++) if (names[i].equals(s)) return i;
        throw new IllegalStateException("unknown name " + s);
    }

    static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
