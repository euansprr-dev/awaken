package dev.awaken.city;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Carillon's authored plan ({@code data/awaken/city/plan.json}, written by {@code tools/city/plan.py}): the one designed
 * city, the same in every world. Read once from the mod's own resources (never from data packs: the layout is fixed
 * and its hash is pinned by a GameTest). Plan coordinates are blocks from the site centre, x east, z south; heights
 * are relative to the street (ry 0 is the paving's top block).
 */
public final class CityPlan {
    /** 2 (M4 B2): the lift raster is signed (Choir Square's bowl), plus crossings, named spots and the relief. */
    public static final int SCHEMA = 2;
    public static final String RESOURCE = "/data/awaken/city/plan.json";

    // ground kinds, one byte per cell (tools/city/plan.py writes the same numbers)
    public static final byte VOID = 0, FREE = 1, YARD = 2, GARDEN = 3, PLOT = 4, AVENUE = 5, STREET = 6, LANE = 7,
            ALLEY = 8, SQUARE = 9, PLAZA = 10, QUAY = 11, RIVER = 12, CANAL = 13, WALL = 14, LANDMARK = 15, STAIR = 16,
            WATERGATE = 17, TRAINING = 18, RACE = 19, RING = 20, LOWQUAY = 21;
    public static final String[] DISTRICTS = {"none", "market", "bellwatch", "quay", "terrace", "works", "garden", "wallring"};
    public static final int MARKET = 1, BELLWATCH = 2, QUAY_D = 3, TERRACE = 4, WORKS = 5, GARDEN_D = 6, WALLRING = 7;
    public static final int N = 0, E = 1, S = 2, W = 3;

    public final int size, origin;
    final byte[] kind, district, lift;
    /** The plot standing on each cell, or -1. */
    private final int[] plotId;
    public final List<Plot> plots;
    public final List<Part> parts;
    public final List<Bridge> bridges;
    public final List<Stair> stairs;
    /** The streets as laid out (centre lines): avenues rank 1, streets 2, lanes 3, alleys 4. */
    public final List<Street> streets;
    /** The crossings over the avenues and the Ring (walkways built now; hang-line sites B3 dresses). */
    public final List<Crossing> crossings;
    /** Named spots for other modules (CityHooks.spot): plan x, ry (the block a thing stands in), z. */
    public final java.util.Map<String, int[]> spots;
    public final List<int[]> trees;   // x, z, kind (0 oak, 1 birch, 2 cherry, 3 avenue), size
    /** The cells the trees' trunks stand in (a facade keeps its climb lines clear of them). */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet treeCells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    public final List<int[]> lamps;   // x, z
    /** The street-life layer (M4 B3a, look bible §7): stalls, carts, wells, benches, lines... (tools/city/props.py). */
    public final List<Prop> props;
    public final List<Landmark> landmarks;
    public final List<District> districts;
    public final List<int[]> roads;   // outside the wall: x0, z0, x1, z1
    public final int fieldsFrom, fieldsTo, roadWidth;
    public final int wallRadius, wallThickness, wallHeight, towerHeight, terraceLift, streetY;
    public final int startX, startRy, startZ;
    public final float startYaw;
    public final int squareX, squareZ, squareR;
    public final List<float[]> river;
    public final List<float[]> streetRoute;
    public final Graph streetGraph, roofGraph;
    public final List<String> moves;
    public final String sha256;
    public final int bytes;

    /** One building plot (SPEC §16.2: the grammar fills it; enterable ones stay open inside). */
    public record Plot(int id, int district, int x0, int z0, int x1, int z1, int front, int storeys, Roof roof,
                       boolean ridgeX, boolean steep, int eave, int top, int lift, int doorX, int doorZ, String use,
                       boolean enter, int variant) {
        public boolean hasDoor() {
            return doorX != Integer.MIN_VALUE;
        }
    }

    public enum Roof { GABLE, HIP, MANSARD, FLAT, SAW }

    /** One piece of a landmark, at most 14x14 (with a 2-block margin for what hangs off it). */
    public record Part(String landmark, String kind, int x0, int z0, int x1, int z1, int h, String style, String axis,
                       String face, int colour, boolean breakable) {}

    public record Landmark(String id, String name, int x, int z, boolean breakable) {}

    public record Bridge(String id, String name, int x0, int z0, int x1, int z1, boolean alongX, boolean breakable, boolean canal) {}

    public record Stair(String name, int x0, int z0, int x1, int z1, int down, int lift) {}

    /** A street's centre line, its width and rank; the name ("" when it has none). */
    public record Street(int rank, float width, float ax, float az, float bx, float bz, String name) {}

    /**
     * A line across an avenue (look bible §3.5): a walkway (an enclosed bridge between two upper floors, its roof the
     * road at ry ary) or a hang line between the facing first-floor courses (anchor "facades") or to a post at the far
     * kerb (anchor "post"). Ends a and b in plan coordinates; plotA / plotB the houses (-1 for a post).
     */
    public record Crossing(String kind, String street, float ax, float ary, float az, float bx, float bry, float bz,
                           int plotA, int plotB, String anchor, int @org.jspecify.annotations.Nullable [] hung) {}

    /**
     * One street-life prop (M4 B3a): its kind, the cells it covers (x0..x1, z0..z1), the ground row it stands on (for a
     * line: the rope's row), a facing ("N".."W", "" or, for a line, the ends that stand on poles: "a", "b", "ab"), a
     * variant number and a detail string (a stall's good, a cart's load, a line's hang...).
     */
    public record Prop(String kind, int x0, int z0, int x1, int z1, int ry, String facing, int v, String s) {
        public boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        /** The facing as a side (N 0, E 1, S 2, W 3), or -1. */
        public int side() {
            return facing.isEmpty() ? -1 : "NESW".indexOf(facing.charAt(0));
        }
    }

    public record District(int id, String key, String name, int bellX, int bellZ) {}

    /** Nodes: x, ry, z (plan); kinds and refs as strings. Edges: a, b, move index, seconds, tag. A roof edge's b < 0
     * is the street node -1 - b. */
    public record Graph(float[] nx, int[] nry, float[] nz, String[] nkind, String[] nref, int[] ea, int[] eb, int[] emove,
                        float[] ecost, String[] etag) {
        public int nodes() {
            return nx.length;
        }

        public int edges() {
            return ea.length;
        }
    }

    private static volatile CityPlan instance;

    /** The plan, read once. */
    public static CityPlan get() {
        CityPlan p = instance;
        if (p == null) {
            synchronized (CityPlan.class) {
                p = instance;
                if (p == null) instance = p = load();
            }
        }
        return p;
    }

    static CityPlan load() {
        try (InputStream in = CityPlan.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing " + RESOURCE);
            byte[] raw = in.readAllBytes();
            return new CityPlan(raw);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    private CityPlan(byte[] raw) {
        bytes = raw.length;
        sha256 = sha256(raw);
        JsonObject o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        int schema = o.get("schemaVersion").getAsInt();
        if (schema != SCHEMA) throw new IllegalStateException("city plan schema " + schema + ", expected " + SCHEMA);
        streetY = o.get("streetY").getAsInt();
        JsonObject r = o.getAsJsonObject("raster");
        size = r.get("size").getAsInt();
        origin = r.get("origin").getAsInt();
        kind = inflate(r.get("kind").getAsString(), size * size);
        district = inflate(r.get("district").getAsString(), size * size);
        lift = inflate(r.get("lift").getAsString(), size * size);

        JsonArray fields = o.getAsJsonArray("plotFields");
        List<String> f = new ArrayList<>();
        for (JsonElement e : fields) f.add(e.getAsString());
        List<Plot> pl = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("plots")) {
            JsonArray a = e.getAsJsonArray();
            String dname = a.get(f.indexOf("district")).getAsString();
            JsonElement dx = a.get(f.indexOf("doorX")), dz = a.get(f.indexOf("doorZ"));
            pl.add(new Plot(a.get(0).getAsInt(), districtId(dname), a.get(f.indexOf("x0")).getAsInt(), a.get(f.indexOf("z0")).getAsInt(),
                    a.get(f.indexOf("x1")).getAsInt(), a.get(f.indexOf("z1")).getAsInt(), side(a.get(f.indexOf("front")).getAsString()),
                    a.get(f.indexOf("storeys")).getAsInt(), Roof.valueOf(a.get(f.indexOf("roof")).getAsString().toUpperCase(java.util.Locale.ROOT)),
                    a.get(f.indexOf("ridge")).getAsString().equals("x"), a.get(f.indexOf("pitch")).getAsString().equals("steep"),
                    a.get(f.indexOf("eave")).getAsInt(), a.get(f.indexOf("top")).getAsInt(), a.get(f.indexOf("lift")).getAsInt(),
                    dx.isJsonNull() ? Integer.MIN_VALUE : dx.getAsInt(), dz.isJsonNull() ? Integer.MIN_VALUE : dz.getAsInt(),
                    a.get(f.indexOf("use")).getAsString(), a.get(f.indexOf("enter")).getAsBoolean(), a.get(f.indexOf("variant")).getAsInt()));
        }
        plots = List.copyOf(pl);
        plotId = new int[size * size];
        java.util.Arrays.fill(plotId, -1);
        for (Plot q : plots)
            for (int z = q.z0(); z <= q.z1(); z++)
                for (int x = q.x0(); x <= q.x1(); x++) if (inRaster(x, z)) plotId[(z - origin) * size + (x - origin)] = q.id();

        List<Part> pa = new ArrayList<>();
        List<Landmark> lm = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("landmarks")) {
            JsonObject l = e.getAsJsonObject();
            String id = l.get("id").getAsString();
            boolean brk = l.has("breakable") && l.get("breakable").getAsBoolean();
            int ax = 0, az = 0;
            if (l.has("at")) {
                ax = l.getAsJsonArray("at").get(0).getAsInt();
                az = l.getAsJsonArray("at").get(1).getAsInt();
            }
            lm.add(new Landmark(id, l.get("name").getAsString(), ax, az, brk));
            for (JsonElement pe : l.getAsJsonArray("parts")) {
                JsonObject p = pe.getAsJsonObject();
                JsonArray b = p.getAsJsonArray("box");
                pa.add(new Part(id, p.get("kind").getAsString(), b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(),
                        b.get(3).getAsInt(), p.has("h") ? p.get("h").getAsInt() : 0, p.has("style") ? p.get("style").getAsString() : "",
                        p.has("axis") ? p.get("axis").getAsString() : "", p.has("face") ? p.get("face").getAsString() : "",
                        p.has("colour") ? p.get("colour").getAsInt() : 0, brk));
            }
        }
        parts = List.copyOf(pa);
        landmarks = List.copyOf(lm);

        List<Bridge> br = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("bridges")) {
            JsonObject b = e.getAsJsonObject();
            JsonArray x = b.getAsJsonArray("box");
            br.add(new Bridge(b.get("id").getAsString(), b.get("name").getAsString(), x.get(0).getAsInt(), x.get(1).getAsInt(),
                    x.get(2).getAsInt(), x.get(3).getAsInt(), b.get("axis").getAsString().equals("x"), b.get("breakable").getAsBoolean(),
                    b.get("canal").getAsBoolean()));
        }
        bridges = List.copyOf(br);

        List<Stair> st = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("stairs")) {
            JsonObject s = e.getAsJsonObject();
            JsonArray x = s.getAsJsonArray("box");
            st.add(new Stair(s.get("name").getAsString(), x.get(0).getAsInt(), x.get(1).getAsInt(), x.get(2).getAsInt(),
                    x.get(3).getAsInt(), side(s.get("down").getAsString().toUpperCase(java.util.Locale.ROOT)), s.get("lift").getAsInt()));
        }
        stairs = List.copyOf(st);

        List<Street> sts = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("streets")) {
            JsonObject t = e.getAsJsonObject();
            JsonArray a = t.getAsJsonArray("a"), b = t.getAsJsonArray("b");
            sts.add(new Street(t.get("rank").getAsInt(), t.get("width").getAsFloat(), a.get(0).getAsFloat(), a.get(1).getAsFloat(),
                    b.get(0).getAsFloat(), b.get(1).getAsFloat(), t.has("name") ? t.get("name").getAsString() : ""));
        }
        streets = List.copyOf(sts);

        List<Crossing> cr = new ArrayList<>();
        if (o.has("crossings")) {
            for (JsonElement e : o.getAsJsonArray("crossings")) {
                JsonObject c = e.getAsJsonObject();
                JsonArray a = c.getAsJsonArray("a"), b = c.getAsJsonArray("b"), pp = c.getAsJsonArray("plots");
                int[] hung = null;
                if (c.has("hung") && c.get("hung").isJsonArray()) {
                    JsonArray h = c.getAsJsonArray("hung");
                    hung = new int[h.size()];
                    for (int k = 0; k < hung.length; k++) hung[k] = h.get(k).getAsInt();
                }
                cr.add(new Crossing(c.get("kind").getAsString(), c.get("street").getAsString(), a.get(0).getAsFloat(), a.get(1).getAsFloat(),
                        a.get(2).getAsFloat(), b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat(),
                        pp.get(0).isJsonNull() ? -1 : pp.get(0).getAsInt(), pp.get(1).isJsonNull() ? -1 : pp.get(1).getAsInt(),
                        c.has("anchor") ? c.get("anchor").getAsString() : "", hung));
            }
        }
        crossings = List.copyOf(cr);
        java.util.Map<String, int[]> sp = new java.util.LinkedHashMap<>();
        if (o.has("spots")) {
            for (var e : o.getAsJsonObject("spots").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                sp.put(e.getKey(), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
            }
        }
        spots = java.util.Collections.unmodifiableMap(sp);

        List<int[]> tr = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("trees")) {
            JsonArray a = e.getAsJsonArray();
            String k = a.get(2).getAsString();
            int kk = switch (k) {
                case "birch" -> 1;
                case "cherry" -> 2;
                case "avenue" -> 3;
                default -> 0;
            };
            tr.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), kk, a.get(3).getAsInt()});
        }
        trees = List.copyOf(tr);
        for (int[] t : trees) treeCells.add(cellKey(t[0], t[1]));
        List<int[]> la = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("lamps")) la.add(new int[]{e.getAsJsonArray().get(0).getAsInt(), e.getAsJsonArray().get(1).getAsInt()});
        lamps = List.copyOf(la);
        List<Prop> pr = new ArrayList<>();
        if (o.has("props")) {
            for (JsonElement e : o.getAsJsonArray("props")) {
                JsonArray a = e.getAsJsonArray();
                pr.add(new Prop(a.get(0).getAsString(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt(), a.get(4).getAsInt(),
                        a.get(5).getAsInt(), a.get(6).getAsString(), a.get(7).getAsInt(), a.get(8).getAsString()));
            }
        }
        props = List.copyOf(pr);

        List<District> ds = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("districts")) {
            JsonObject d = e.getAsJsonObject();
            JsonArray b = d.getAsJsonArray("bell");
            ds.add(new District(d.get("id").getAsInt(), d.get("key").getAsString(), d.get("name").getAsString(), b.get(0).getAsInt(), b.get(1).getAsInt()));
        }
        districts = List.copyOf(ds);

        JsonObject out = o.getAsJsonObject("outside");
        fieldsFrom = out.get("fieldsFrom").getAsInt();
        fieldsTo = out.get("fieldsTo").getAsInt();
        roadWidth = out.get("roadWidth").getAsInt();
        List<int[]> rd = new ArrayList<>();
        for (JsonElement e : out.getAsJsonArray("roads")) {
            JsonArray a = e.getAsJsonArray();
            rd.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()});
        }
        roads = List.copyOf(rd);

        JsonObject wall = o.getAsJsonObject("wall");
        wallRadius = wall.get("radius").getAsInt();
        wallThickness = wall.get("thickness").getAsInt();
        wallHeight = wall.get("height").getAsInt();
        towerHeight = wall.get("towerHeight").getAsInt();
        terraceLift = o.get("terraceLift").getAsInt();
        JsonObject s = o.getAsJsonObject("start");
        startX = s.get("x").getAsInt();
        startRy = s.get("ry").getAsInt();
        startZ = s.get("z").getAsInt();
        startYaw = s.get("yaw").getAsFloat();
        JsonObject sq = o.getAsJsonObject("square");
        squareX = sq.get("x").getAsInt();
        squareZ = sq.get("z").getAsInt();
        squareR = sq.get("r").getAsInt();
        List<float[]> rv = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("river")) rv.add(new float[]{e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()});
        river = List.copyOf(rv);
        List<float[]> route = new ArrayList<>();
        for (JsonElement e : o.getAsJsonObject("routes").getAsJsonArray("street"))
            route.add(new float[]{e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()});
        streetRoute = List.copyOf(route);

        JsonObject g = o.getAsJsonObject("graphs");
        List<String> mv = new ArrayList<>();
        for (JsonElement e : g.getAsJsonArray("moves")) mv.add(e.getAsString());
        moves = List.copyOf(mv);
        streetGraph = graph(g.getAsJsonObject("street"));
        roofGraph = graph(g.getAsJsonObject("roof"));
        Awaken.LOG.info("[city] plan read: {} plots, {} landmark parts, {} bridges, street graph {}/{}, roof graph {}/{}, sha256 {}",
                plots.size(), parts.size(), bridges.size(), streetGraph.nodes(), streetGraph.edges(), roofGraph.nodes(),
                roofGraph.edges(), sha256.substring(0, 12));
    }

    private static Graph graph(JsonObject g) {
        JsonArray ns = g.getAsJsonArray("nodes"), es = g.getAsJsonArray("edges");
        int n = ns.size(), m = es.size();
        float[] nx = new float[n], nz = new float[n];
        int[] nry = new int[n];
        String[] nk = new String[n], nr = new String[n];
        for (int i = 0; i < n; i++) {
            JsonArray a = ns.get(i).getAsJsonArray();
            nx[i] = a.get(0).getAsFloat();
            nry[i] = Math.round(a.get(1).getAsFloat());
            nz[i] = a.get(2).getAsFloat();
            nk[i] = a.get(3).getAsString();
            nr[i] = a.get(4).isJsonNull() ? "" : a.get(4).getAsString();
        }
        int[] ea = new int[m], eb = new int[m], em = new int[m];
        float[] ec = new float[m];
        String[] et = new String[m];
        for (int i = 0; i < m; i++) {
            JsonArray a = es.get(i).getAsJsonArray();
            ea[i] = a.get(0).getAsInt();
            eb[i] = a.get(1).getAsInt();
            em[i] = a.get(2).getAsInt();
            ec[i] = a.get(3).getAsFloat();
            et[i] = a.get(4).getAsString();
        }
        return new Graph(nx, nry, nz, nk, nr, ea, eb, em, ec, et);
    }

    static int districtId(String key) {
        for (int i = 0; i < DISTRICTS.length; i++) if (DISTRICTS[i].equals(key)) return i;
        return 0;
    }

    static int side(String s) {
        return switch (s) {
            case "N" -> N;
            case "E" -> E;
            case "S" -> S;
            default -> W;
        };
    }

    private static byte[] inflate(String b64, int n) {
        byte[] packed = Base64.getDecoder().decode(b64);
        Inflater inf = new Inflater();
        inf.setInput(packed);
        byte[] out = new byte[n];
        try {
            int got = 0;
            while (got < n && !inf.finished()) got += inf.inflate(out, got, n - got);
            if (got != n) throw new IllegalStateException("raster layer has " + got + " cells, expected " + n);
        } catch (DataFormatException e) {
            throw new IllegalStateException("bad raster layer", e);
        } finally {
            inf.end();
        }
        return out;
    }

    static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- raster lookups (plan coordinates) -------------------------------------------------------------------

    public boolean inRaster(int x, int z) {
        int i = x - origin, j = z - origin;
        return i >= 0 && j >= 0 && i < size && j < size;
    }

    public byte kind(int x, int z) {
        int i = x - origin, j = z - origin;
        if (i < 0 || j < 0 || i >= size || j >= size) return VOID;
        return kind[j * size + i];
    }

    public int district(int x, int z) {
        int i = x - origin, j = z - origin;
        if (i < 0 || j < 0 || i >= size || j >= size) return 0;
        return district[j * size + i];
    }

    /** The ground's top block at a cell, relative to the street (signed: Choir Square's bowl goes below it). */
    public int lift(int x, int z) {
        int i = x - origin, j = z - origin;
        if (i < 0 || j < 0 || i >= size || j >= size) return 0;
        return lift[j * size + i];
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Whether a tree's trunk stands in this cell. */
    public boolean treeAt(int x, int z) {
        return treeCells.contains(cellKey(x, z));
    }

    /** The id of the plot on this cell, or -1. */
    public int plotAt(int x, int z) {
        int i = x - origin, j = z - origin;
        if (i < 0 || j < 0 || i >= size || j >= size) return -1;
        return plotId[j * size + i];
    }

    public static boolean water(byte k) {
        return k == RIVER || k == CANAL || k == WATERGATE || k == RACE;
    }

    public static boolean walkable(byte k) {
        return k == AVENUE || k == STREET || k == LANE || k == ALLEY || k == SQUARE || k == PLAZA || k == QUAY || k == STAIR
                || k == RING || k == TRAINING || k == LOWQUAY;
    }

    /** The inside of the wall: everything the city owns (its plots, streets, water, the wall itself). */
    public boolean inside(int x, int z) {
        byte k = kind(x, z);
        return k != VOID;
    }

    /** For tests and tools: the plan's text as a stable byte count (logged). */
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }
}
