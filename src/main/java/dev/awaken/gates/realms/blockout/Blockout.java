package dev.awaken.gates.realms.blockout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A gate's big shapes, written as data before any detail exists (SPEC §50.7): data/awaken/realm_blockouts/&lt;id&gt;.json.
 * Masses are added and cut in the order listed; each has a role (ground, wall, roof...) and a name the gate's own code
 * reads it by, so the build can't drift from what was agreed from the painter's camera. Coordinates are the Realm's own,
 * y is the world's. Immutable once made, so any worldgen thread can read it.
 */
public final class Blockout {
    public static final int SCHEMA = 1;

    public enum Role { GROUND, WALL, FLOOR, ROOF, GREAT, WALK, WATER, GLOW, FOLIAGE }

    public enum Shape { BOX, CYLINDER, CONE, DOME, SPHERE, WEDGE }

    public record SiteSpec(Land.SiteKind kind, int x, int y, int z, int radius, String what) {}

    /** A place to look from: where the eye is and which way it faces (the game's yaw and pitch). */
    public record Camera(String name, double x, double y, double z, float yaw, float pitch) {}

    public final String id;
    public final int half;
    public final float mouthYaw;
    public final long seed;
    private final Mass[] masses;
    private final Map<String, Mass> byName = new LinkedHashMap<>();
    private final List<SiteSpec> sites;
    private final List<Camera> cameras;

    private static final ThreadLocal<byte[]> COLUMN = ThreadLocal.withInitial(() -> new byte[V.H]);
    private static final ThreadLocal<int[]> SPANS = ThreadLocal.withInitial(() -> new int[64]);

    private Blockout(String id, int half, float mouthYaw, long seed, Mass[] masses, List<SiteSpec> sites, List<Camera> cameras) {
        this.id = id;
        this.half = half;
        this.mouthYaw = mouthYaw;
        this.seed = seed;
        this.masses = masses;
        for (Mass m : masses) byName.put(m.name, m);
        this.sites = List.copyOf(sites);
        this.cameras = List.copyOf(cameras);
    }

    // ---- reading it -------------------------------------------------------------------------------------------------------

    /** The mod's own blockout of this name. Rough surfaces come out as on seed 0 until it's {@link #seeded}. */
    public static Blockout load(String id) {
        String at = "/data/awaken/realm_blockouts/" + id + ".json";
        try (InputStream in = Blockout.class.getResourceAsStream(at)) {
            if (in == null) throw new IllegalArgumentException("blockout " + id + ": no file at " + at);
            return parse(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("blockout " + id + ": can't be read: " + e.getMessage(), e);
        }
    }

    /** A blockout straight from a file, for the bench: no build needed to see a change. */
    public static Blockout load(Path file) {
        String name = file.getFileName().toString();
        String id = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        try {
            return parse(id, Files.readString(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("blockout " + id + ": can't be read: " + e.getMessage(), e);
        }
    }

    /** Anything wrong is said with the mass and the field it's in. */
    public static Blockout parse(String id, String json) {
        try {
            JsonElement root;
            try {
                root = JsonParser.parseString(json);
            } catch (JsonParseException e) {
                throw new IllegalArgumentException("not JSON: " + e.getMessage());
            }
            if (!root.isJsonObject()) throw new IllegalArgumentException("not a JSON object");
            Fields top = new Fields(root.getAsJsonObject(), "the blockout");
            int schema = top.whole("schemaVersion");
            if (schema != SCHEMA) throw new IllegalArgumentException("unknown schemaVersion " + schema);
            int half = top.whole("half");
            if (half < 16 || half > 2000) throw top.bad("half", "must be from 16 to 2000");
            float yaw = (float) top.number("mouthYaw", 0);
            List<Mass> masses = new ArrayList<>();
            Set<String> names = new HashSet<>();
            JsonArray ms = top.array("masses");
            for (int i = 0; i < ms.size(); i++) {
                Mass m = Mass.parse(ms.get(i), i);
                if (!names.add(m.name)) throw new IllegalArgumentException("mass '" + m.name + "': 'name' is used twice");
                masses.add(m);
            }
            if (masses.isEmpty()) throw top.bad("masses", "is empty");
            List<SiteSpec> sites = new ArrayList<>();
            JsonArray ss = top.array("sites");
            for (int i = 0; i < ss.size(); i++) {
                if (!ss.get(i).isJsonObject()) throw new IllegalArgumentException("site #" + (i + 1) + ": not an object");
                Fields f = new Fields(ss.get(i).getAsJsonObject(), "site #" + (i + 1));
                String k = f.text("kind");
                Land.SiteKind kind;
                try {
                    kind = Land.SiteKind.valueOf(k.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw f.bad("kind", "is '" + k + "', not one of " + Arrays.toString(Land.SiteKind.values()));
                }
                int[] at = f.wholes("at", 3);
                sites.add(new SiteSpec(kind, at[0], at[1], at[2], f.whole("radius", 4), f.text("what", k.toLowerCase(Locale.ROOT))));
                f.done();
            }
            if (sites.stream().noneMatch(s -> s.kind() == Land.SiteKind.MOUTH)) throw top.bad("sites", "has no MOUTH");
            List<Camera> cameras = new ArrayList<>();
            if (top.o.has("cameras")) {
                if (!top.o.get("cameras").isJsonObject()) throw top.bad("cameras", "must be an object of name to [x, y, z, yaw, pitch]");
                top.used.add("cameras");
                Fields cs = new Fields(top.o.getAsJsonObject("cameras"), "cameras");
                for (String name : cs.o.keySet()) {
                    double[] c = cs.numbers(name, 5);
                    cameras.add(new Camera(name, c[0], c[1], c[2], (float) c[3], (float) c[4]));
                }
            }
            top.done();
            return new Blockout(id, half, yaw, 0, masses.toArray(new Mass[0]), sites, cameras);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("blockout " + id + ": " + e.getMessage());
        }
    }

    /** The same shapes with their rough surfaces rolled from this seed: a land passes its own. */
    public Blockout seeded(long seed) {
        Mass[] next = new Mass[masses.length];
        for (int i = 0; i < next.length; i++) next[i] = masses[i].seeded(seed);
        return new Blockout(id, half, mouthYaw, seed, next, sites, cameras);
    }

    // ---- asking it --------------------------------------------------------------------------------------------------------

    public List<Mass> masses() {
        return List.of(masses);
    }

    public List<SiteSpec> sites() {
        return sites;
    }

    public List<Camera> cameras() {
        return cameras;
    }

    /** A mass by its name. A name the data doesn't have is a mistake in the code that asks, so it fails loudly. */
    public Mass mass(String name) {
        Mass m = byName.get(name);
        if (m == null) throw new IllegalArgumentException("blockout " + id + " has no mass '" + name + "' (it has " + byName.keySet() + ")");
        return m;
    }

    public boolean has(String name) {
        return byName.containsKey(name);
    }

    /** What this cell is after every add and cut, or null for nothing. */
    public Role role(int x, int y, int z) {
        for (int i = masses.length - 1; i >= 0; i--) {
            Mass m = masses[i];
            if (m.contains(x, y, z)) return m.cut ? null : m.role;
        }
        return null;
    }

    /** Whether something stands in this cell. Water doesn't count. */
    public boolean solid(int x, int y, int z) {
        Role r = role(x, y, z);
        return r != null && r != Role.WATER;
    }

    /** The top ground, floor or walk cell of a column, or -1. */
    public int ground(int x, int z) {
        byte[] col = COLUMN.get();
        int span = column(x, z, masses, col);
        if (span < 0) return -1;
        for (int y = span & 0xFFFF, lo = span >> 16; y >= lo; y--) {
            int r = col[y];
            if (r == Role.GROUND.ordinal() + 1 || r == Role.FLOOR.ordinal() + 1 || r == Role.WALK.ordinal() + 1) return y;
        }
        return -1;
    }

    /** The masses whose footprint touches a rectangle of columns, in order: ask once per chunk, then draw from these alone. */
    public Mass[] near(int ax, int az, int bx, int bz) {
        int n = 0;
        for (Mass m : masses) if (m.x1 >= ax && m.x0 <= bx && m.z1 >= az && m.z0 <= bz) n++;
        Mass[] out = new Mass[n];
        n = 0;
        for (Mass m : masses) if (m.x1 >= ax && m.x0 <= bx && m.z1 >= az && m.z0 <= bz) out[n++] = m;
        return out;
    }

    /**
     * One column after every add and cut among these masses: {@code roles[y]} is 0 for nothing or a role's ordinal + 1.
     * Returns the span it wrote as {@code lo << 16 | hi}, or -1 for a column with nothing in it. {@code roles} needs
     * {@link V#H} cells; only the span is touched.
     */
    public int column(int x, int z, Mass[] among, byte[] roles) {
        int[] spans = SPANS.get();
        if (spans.length < among.length) {
            spans = new int[among.length];
            SPANS.set(spans);
        }
        int min = Integer.MAX_VALUE, max = -1;
        for (int i = 0; i < among.length; i++) {
            int s = spans[i] = among[i].span(x, z);
            if (s < 0 || among[i].cut) continue;
            min = Math.min(min, s >> 16);
            max = Math.max(max, s & 0xFFFF);
        }
        if (max < 0) return -1;
        Arrays.fill(roles, min, max + 1, (byte) 0);
        for (int i = 0; i < among.length; i++) {
            int s = spans[i];
            if (s < 0) continue;
            Mass m = among[i];
            int lo = Math.max(min, s >> 16), hi = Math.min(max, s & 0xFFFF);
            if (lo <= hi) Arrays.fill(roles, lo, hi + 1, m.cut ? 0 : (byte) (m.role.ordinal() + 1));
        }
        return min << 16 | max;
    }

    /**
     * The lines of a walk that stand at each camera in turn, for a tour script and the preview that reads one. A walk
     * places you by whole blocks from the Mouth, so a camera lands within half a block of where it was written.
     */
    public List<String> tourShots() {
        SiteSpec m = sites.stream().filter(s -> s.kind() == Land.SiteKind.MOUTH).findFirst().orElseThrow();
        List<String> out = new ArrayList<>();
        for (Camera c : cameras) {
            int dx = (int) Math.floor(c.x()) - m.x(), dz = (int) Math.floor(c.z()) - m.z(), dy = (int) Math.round(c.y() - 1.62) - m.y();
            out.add("cmd awaken gate realm_at MOUTH 0 " + dx + " " + dy + " " + dz);
            out.add("look " + trim(c.yaw()) + " " + trim(c.pitch()));
            out.add("wait 200");
            out.add("shot " + id + "_" + c.name());
        }
        return out;
    }

    private static String trim(float f) {
        return f == Math.rint(f) ? Integer.toString((int) f) : Float.toString(f);
    }

    // ---- one shape --------------------------------------------------------------------------------------------------------

    /** One named shape. Its numbers are public; ask it about cells rather than doing its arithmetic again. */
    public static final class Mass {
        public final String name;
        public final boolean cut;
        public final Shape shape;
        /** Null for a cut. */
        public final Role role;
        /** The box it fits in, inclusive (a rough top can rise {@link #rough} above y1). */
        public final int x0, y0, z0, x1, y1, z1;
        /** The middle of its footprint, and for a sphere the height of its middle. */
        public final double cx, cy, cz;
        /** Radius along x and along z (0 for a box or wedge), a ring's inner radius (-1 for none), a cone's top radius. */
        public final double r, rz, inner, r2;
        /** How many blocks tall. */
        public final int h;
        public final int rough;
        private final int upX, upZ;
        private final SimplexNoise noise;

        private Mass(String name, boolean cut, Shape shape, Role role, int x0, int y0, int z0, int x1, int y1, int z1, double cx, double cy, double cz,
                     double r, double rz, double inner, double r2, int rough, int upX, int upZ, long seed) {
            this.name = name;
            this.cut = cut;
            this.shape = shape;
            this.role = role;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.r = r;
            this.rz = rz;
            this.inner = inner;
            this.r2 = r2;
            this.h = y1 - y0 + 1;
            this.rough = rough;
            this.upX = upX;
            this.upZ = upZ;
            // each mass its own bumps, the same on every thread and every run
            noise = rough > 0 ? N.noise(N.hash(seed, name.hashCode(), 0xB10C)) : null;
        }

        Mass seeded(long seed) {
            return rough == 0 ? this : new Mass(name, cut, shape, role, x0, y0, z0, x1, y1, z1, cx, cy, cz, r, rz, inner, r2, rough, upX, upZ, seed);
        }

        /** How far out a column is across the footprint of a round shape: 0 at the middle, 1 at the rim. */
        private double out(int x, int z, double rx, double rz) {
            double a = (x - cx) / (rx + 0.5), b = (z - cz) / (rz + 0.5);
            return Math.sqrt(a * a + b * b);
        }

        private boolean inHole(int x, int z) {
            return inner >= 0 && out(x, z, inner, Math.max(0, rz - (r - inner))) <= 1;
        }

        /** The lowest and highest cell of this mass in a column as {@code lo << 16 | hi}, or -1 where the column misses it. */
        public int span(int x, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return -1;
            int lo = y0, hi = y1;
            boolean tapers = false;
            switch (shape) {
                case BOX -> {}
                case WEDGE -> {
                    double t = upX != 0 ? (x1 == x0 ? 1 : (upX > 0 ? x - x0 : x1 - x) / (double) (x1 - x0)) : (z1 == z0 ? 1 : (upZ > 0 ? z - z0 : z1 - z) / (double) (z1 - z0));
                    hi = y0 + (int) Math.round((y1 - y0) * t);
                }
                case CYLINDER -> {
                    if (out(x, z, r, rz) > 1 || inHole(x, z)) return -1;
                }
                case CONE -> {
                    double d = Math.hypot(x - cx, z - cz);
                    if (r2 < r) {
                        int k = (int) Math.floor((r + 0.5 - d) * h / (r - r2));
                        if (k < 0) return -1;
                        hi = y0 + Math.min(h - 1, k);
                    } else if (r2 > r) {
                        int k = Math.max(0, (int) Math.ceil((d - 0.5 - r) * h / (r2 - r)));
                        if (k > h - 1) return -1;
                        lo = y0 + k;
                    } else if (d > r + 0.5) return -1;
                    tapers = true;
                }
                case DOME -> {
                    double n = out(x, z, r, rz);
                    if (n > 1) return -1;
                    int cells = (int) Math.round(h * Math.sqrt(1 - n * n));
                    if (cells < 1) return -1;
                    hi = y0 + cells - 1;
                    tapers = true;
                }
                case SPHERE -> {
                    double n = out(x, z, r, rz);
                    if (n > 1) return -1;
                    double up = (h / 2.0) * Math.sqrt(1 - n * n);
                    lo = (int) Math.ceil(cy - up);
                    hi = (int) Math.floor(cy + up);
                    tapers = true;
                }
            }
            if (noise != null) {
                // a shape that thins to nothing at its rim keeps a clean rim: the bumps fade out with its height there
                double fade = tapers ? Math.min(1, (hi - lo + 1) / (2.0 * rough)) : 1;
                hi += (int) Math.round(N.fbm(noise, x, z, 1 / 24.0, 2) * rough * fade);
            }
            lo = Math.max(lo, 0);
            hi = Math.min(hi, V.H - 1);
            return hi < lo ? -1 : lo << 16 | hi;
        }

        /** The highest cell of this mass in a column, or -1 where the column misses it. */
        public int top(int x, int z) {
            int s = span(x, z);
            return s < 0 ? -1 : s & 0xFFFF;
        }

        /** The lowest cell of this mass in a column, or -1 where the column misses it. */
        public int bottom(int x, int z) {
            int s = span(x, z);
            return s < 0 ? -1 : s >> 16;
        }

        /** Whether a cell is inside this shape, whatever was added or cut after it. */
        public boolean contains(int x, int y, int z) {
            if (y < y0 || y > y1 + rough) return false;
            int s = span(x, z);
            return s >= 0 && y >= s >> 16 && y <= (s & 0xFFFF);
        }

        /**
         * How deep a cell is inside this shape: the distance in blocks from its middle to the nearest face, so the outer
         * layer reads under 1, the next under 2, and anything outside reads below 0. "The outer two blocks of the tower"
         * is {@code contains && edge < 2}.
         */
        public double edge(int x, int y, int z) {
            double side = switch (shape) {
                case BOX, WEDGE -> Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z)) + 0.5;
                case CYLINDER, DOME -> {
                    double d = (1 - out(x, z, r, rz)) * (Math.min(r, rz) + 0.5);
                    if (inner >= 0) d = Math.min(d, (out(x, z, inner, Math.max(0, rz - (r - inner))) - 1) * (inner + 0.5));
                    yield d;
                }
                case CONE -> {
                    double t = Math.max(0, Math.min(h - 1, y - y0)) / (double) h;
                    yield r + (r2 - r) * t + 0.5 - Math.hypot(x - cx, z - cz);
                }
                case SPHERE -> {
                    double n = out(x, z, r, rz), v = (y - cy) / (h / 2.0);
                    yield (1 - Math.sqrt(n * n + v * v)) * (Math.min(Math.min(r, rz), h / 2.0 - 0.5) + 0.5);
                }
            };
            int s = span(x, z);
            int lo = s < 0 ? y0 : s >> 16, hi = s < 0 ? y1 : s & 0xFFFF;
            return shape == Shape.SPHERE ? side : Math.min(side, Math.min(y - lo, hi - y) + 0.5);
        }

        @Override
        public String toString() {
            return name + " (" + (cut ? "cut " : "") + shape.name().toLowerCase(Locale.ROOT) + " " + x0 + "," + y0 + "," + z0 + " to " + x1 + "," + y1 + "," + z1 + ")";
        }

        private static final Set<String> COMMON = Set.of("name", "op", "shape", "role", "rough", "note");

        static Mass parse(JsonElement e, int index) {
            if (!e.isJsonObject()) throw new IllegalArgumentException("mass #" + (index + 1) + ": not an object");
            JsonObject o = e.getAsJsonObject();
            String name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : null;
            if (name == null || name.isBlank()) throw new IllegalArgumentException("mass #" + (index + 1) + ": 'name' is missing");
            Fields f = new Fields(o, "mass '" + name + "'");
            f.used.addAll(COMMON);
            String op = f.text("op", "add");
            if (!op.equals("add") && !op.equals("cut")) throw f.bad("op", "is '" + op + "', not add or cut");
            boolean cut = op.equals("cut");
            Shape shape = f.pick("shape", Shape.values());
            Role role = cut ? null : f.pick("role", Role.values());
            int rough = f.whole("rough", 0);
            if (rough < 0 || rough > 32) throw f.bad("rough", "must be from 0 to 32");
            Mass m;
            switch (shape) {
                case BOX, WEDGE -> {
                    int[] a = f.wholes("from", 3), b = f.wholes("to", 3);
                    int upX = 0, upZ = 0;
                    if (shape == Shape.WEDGE) {
                        String up = f.text("up");
                        switch (up) {
                            case "+x" -> upX = 1;
                            case "-x" -> upX = -1;
                            case "+z" -> upZ = 1;
                            case "-z" -> upZ = -1;
                            default -> throw f.bad("up", "is '" + up + "', not +x, -x, +z or -z");
                        }
                    }
                    int x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]), y0 = Math.min(a[1], b[1]), y1 = Math.max(a[1], b[1]), z0 = Math.min(a[2], b[2]), z1 = Math.max(a[2], b[2]);
                    m = new Mass(name, cut, shape, role, x0, y0, z0, x1, y1, z1, (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0, 0, 0, -1, 0, rough, upX, upZ, 0);
                }
                case CYLINDER, CONE, DOME -> {
                    int[] at = f.wholes("at", 3);
                    double rx, rz;
                    if (shape != Shape.CONE && o.has("rx")) {
                        rx = f.number("rx");
                        rz = f.number("rz");
                        if (o.has("r")) throw f.bad("r", "is given as well as rx and rz");
                    } else rx = rz = f.number("r");
                    if (rx < 0 || rz < 0) throw f.bad(o.has("rx") ? "rx" : "r", "is below 0");
                    int h = f.whole("h");
                    if (h < 1) throw f.bad("h", "must be 1 or more");
                    double inner = -1, r2 = rx;
                    if (shape == Shape.CYLINDER && o.has("inner")) {
                        inner = f.number("inner");
                        if (inner < 0 || inner >= Math.min(rx, rz)) throw f.bad("inner", "must be from 0 to less than the radius");
                    }
                    if (shape == Shape.CONE) {
                        r2 = f.number("r2", 0);
                        if (r2 < 0) throw f.bad("r2", "is below 0");
                    }
                    int ex = (int) Math.floor(Math.max(rx, r2) + 0.5), ez = (int) Math.floor(Math.max(rz, r2) + 0.5);
                    m = new Mass(name, cut, shape, role, at[0] - ex, at[1], at[2] - ez, at[0] + ex, at[1] + h - 1, at[2] + ez, at[0], at[1], at[2], rx, rz, inner, r2, rough, 0, 0, 0);
                }
                case SPHERE -> {
                    int[] at = f.wholes("at", 3);
                    double rx, ry, rz;
                    if (o.has("rx")) {
                        rx = f.number("rx");
                        ry = f.number("ry");
                        rz = f.number("rz");
                        if (o.has("r")) throw f.bad("r", "is given as well as rx, ry and rz");
                    } else rx = ry = rz = f.number("r");
                    if (rx < 0 || ry < 0 || rz < 0) throw f.bad(o.has("rx") ? "rx" : "r", "is below 0");
                    int ex = (int) Math.floor(rx + 0.5), ey = (int) Math.floor(ry + 0.5), ez = (int) Math.floor(rz + 0.5);
                    m = new Mass(name, cut, shape, role, at[0] - ex, at[1] - ey, at[2] - ez, at[0] + ex, at[1] + ey, at[2] + ez, at[0], at[1], at[2], rx, rz, -1, 0, rough, 0, 0, 0);
                }
                default -> throw new IllegalStateException();
            }
            f.done();
            return m;
        }
    }

    // ---- reading fields, so every complaint says where ----------------------------------------------------------------------

    private static final class Fields {
        final JsonObject o;
        final String where;
        final Set<String> used = new HashSet<>(Set.of("note"));

        Fields(JsonObject o, String where) {
            this.o = o;
            this.where = where;
        }

        IllegalArgumentException bad(String field, String why) {
            return new IllegalArgumentException(where + ": '" + field + "' " + why);
        }

        private JsonElement need(String field) {
            used.add(field);
            if (!o.has(field) || o.get(field).isJsonNull()) throw bad(field, "is missing");
            return o.get(field);
        }

        double number(String field) {
            JsonElement e = need(field);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw bad(field, "must be a number");
            return e.getAsDouble();
        }

        double number(String field, double or) {
            used.add(field);
            return o.has(field) ? number(field) : or;
        }

        int whole(String field) {
            double d = number(field);
            if (d != Math.rint(d)) throw bad(field, "must be a whole number");
            return (int) d;
        }

        int whole(String field, int or) {
            used.add(field);
            return o.has(field) ? whole(field) : or;
        }

        String text(String field) {
            JsonElement e = need(field);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw bad(field, "must be text");
            return e.getAsString();
        }

        String text(String field, String or) {
            used.add(field);
            return o.has(field) ? text(field) : or;
        }

        <E extends Enum<E>> E pick(String field, E[] from) {
            String s = text(field);
            for (E e : from) if (e.name().equalsIgnoreCase(s)) return e;
            throw bad(field, "is '" + s + "', not one of " + Arrays.toString(from).toLowerCase(Locale.ROOT));
        }

        JsonArray array(String field) {
            JsonElement e = need(field);
            if (!e.isJsonArray()) throw bad(field, "must be a list");
            return e.getAsJsonArray();
        }

        double[] numbers(String field, int n) {
            JsonArray a = array(field);
            if (a.size() != n) throw bad(field, "must be " + n + " numbers");
            double[] out = new double[n];
            for (int i = 0; i < n; i++) {
                JsonElement e = a.get(i);
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw bad(field, "must be " + n + " numbers");
                out[i] = e.getAsDouble();
            }
            return out;
        }

        int[] wholes(String field, int n) {
            double[] d = numbers(field, n);
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                if (d[i] != Math.rint(d[i])) throw bad(field, "must be " + n + " whole numbers");
                out[i] = (int) d[i];
            }
            return out;
        }

        /** A field nobody read is a slip of the pen ("ruogh"), and silently dropping it would hide it. */
        void done() {
            for (String k : o.keySet()) if (!used.contains(k) && !k.startsWith("_")) throw bad(k, "is not a field of this");
        }
    }
}
