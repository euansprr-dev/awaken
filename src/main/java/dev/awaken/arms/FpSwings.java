package dev.awaken.arms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The first-person swings (SPEC §25 "First person"), baked by the animation lab (tools/art/anim_lab.py bake) into
 * {@code data/awaken/arms/fp_swings.json}: for every style and move, keys every {@code dt} ticks of the hand's move from
 * vanilla's rest and its turn about its own place, for the style's own forged weapon, and the view's lean. A cut there is an arc about the
 * sword arm's pivot, the edge leading, through a point just under the crosshair on the hit tick. A vanilla item in the
 * style takes the same targets from its own hold ({@link #sample}). Read from the jar (both are client data the
 * server never needs, but plain maths: a GameTest can hold the keys to their rules); Swing Studio's tuning swaps in a
 * whole new set between moves ({@link #parse}, {@link #swap}; SPEC §25 "Swing Studio").
 */
public final class FpSwings {
    private FpSwings() {}

    static final String PATH = "/data/awaken/arms/fp_swings.json";

    /** Floats a key carries: the hand's move (3), its turn (4), the view's lean (yaw, pitch, roll degrees). */
    static final int STRIDE = 10;
    /** The newest file this build reads: a newer one (a later lab) is refused, never half-read. */
    public static final int SCHEMA = 6;

    /** Which hand a move strikes with (a weapon in each hand, the Daggers): the main, the off (the main's mirrored), both. */
    public enum Hand { MAIN, OFF, BOTH }

    /**
     * One move's keys: [dx, dy, dz, qx, qy, qz, qw, yaw, pitch, roll] per step of {@code dt} ticks, and its hand; and in
     * third person the wrist's turn of the weapon in each arm (a quaternion [x, y, z, w] per {@code wristDt} ticks, in
     * the forged weapon's item frame, about its grip; empty when it doesn't turn): for the bent clip (Bendable Cuboids)
     * and, where third person's rig solved its arms straight too (tools/art/specs/arms_rig4.py), for the straight one.
     */
    /**
     * Swing Studio's per move: {@code swish} the tick its swish starts so the whoosh peaks as the blade crosses the target
     * (0: on the press) and {@code swishSound} the studio's sound in place of the row's own (null: the row's), and the bite, after a landed hit's hit-stop the hands running at {@code biteRate} for
     * {@code biteTicks} through the body (0 ticks: none). {@code idle} (schema 6, round 4; empty for none): a weapon in
     * each hand, the hand not striking, laid out like {@code keys} and authored for its own hand: still until the blow
     * lands, then drawn back to the next strike's set and held there for the press.
     */
    public record Clip(float[] keys, Hand hand, float[] wristRight, float[] wristLeft, float[] straightRight, float[] straightLeft,
                       float swish, @Nullable String swishSound, float biteRate, float biteTicks, float[] idle) {
        public int size() {
            return keys.length / STRIDE;
        }
    }

    /**
     * A style's moves, how a vanilla item held in it is turned to the same targets, the grips (where its forged weapon's
     * and a vanilla item's swing turn about) and where the hand closes on its weapon (hand space, from vanilla's rest: the
     * arm drawn to it, FirstPerson.View's grips); a two-handed weapon's off hand down the handle below it (null: one hand),
     * on the forged weapon and on a vanilla item.
     */
    public record Style(Map<Archetype.Move, Clip> moves, Quaternionf vanilla, Vector3f grip, Vector3f vanillaGrip, boolean twin,
                        Vector3f hand, @Nullable Vector3f offHand, @Nullable Vector3f vanillaOffHand) {}

    /**
     * One whole read of the file: its schema, the studio's round, the key steps, every style, and third person's flipped
     * weapons (their single edge on the other side from their style's) and haft grips (where the fist closes on a weapon
     * held down its haft; the rest at the item's grip). Swapped whole, so nothing reads half of two files.
     */
    public record Data(int schema, int round, float dt, float wristDt, Map<String, Style> styles, java.util.Set<String> flipped,
                       Map<String, Vector3f> grip3) {}

    private static final float[] NONE = new float[0];
    private static final Data EMPTY = new Data(0, 0, 0.25F, 0.5F, Map.of(), java.util.Set.of(), Map.of());
    private static final Data JAR = jar();
    private static volatile Data data = JAR;

    /**
     * Loads the swings now (the client calls this on a worker at start): reading the 1.1 MB file on first use cost a
     * 30-80 ms hitch on the render thread the first time anyone with a blade was drawn.
     */
    public static void warm() {
        // JAR's initializer has run by the time this returns
    }

    private static Data jar() {
        try (InputStream in = FpSwings.class.getResourceAsStream(PATH)) {
            if (in == null) return EMPTY;
            Data d = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            Awaken.LOG.info("[arms] first-person swings for {} styles (round {})", d.styles().size(), d.round());
            return d;
        } catch (java.io.IOException | RuntimeException e) {
            Awaken.LOG.error("[arms] {} unreadable: the swings fall back to the rows' own", PATH, e);
            return EMPTY;
        }
    }

    /** Reads a whole swings file. Throws on a newer schema or a broken file (the caller keeps what it has). */
    public static Data parse(java.io.Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
        if (schema > SCHEMA) throw new IllegalArgumentException("swings schema " + schema + " is newer than this build's " + SCHEMA);
        float dt = root.get("dt").getAsFloat();
        float wristDt = root.has("wristDt") ? root.get("wristDt").getAsFloat() : 0.5F;
        java.util.Set<String> flipped = new java.util.HashSet<>();
        if (root.has("flipped3")) root.getAsJsonArray("flipped3").forEach(id -> flipped.add(id.getAsString()));
        Map<String, Vector3f> grip3 = new HashMap<>();
        if (root.has("grip3")) {
            for (var g : root.getAsJsonObject("grip3").entrySet()) grip3.put(g.getKey(), vec(g.getValue().getAsJsonArray()));
        }
        Map<String, Style> styles = new HashMap<>();
        for (var e : root.getAsJsonObject("styles").entrySet()) {
            JsonObject st = e.getValue().getAsJsonObject();
            Map<Archetype.Move, Clip> moves = new EnumMap<>(Archetype.Move.class);
            for (var mv : st.getAsJsonObject("moves").entrySet()) {
                Archetype.Move m = Archetype.Move.byKey(mv.getKey());
                if (m == null) continue;
                JsonObject row = mv.getValue().getAsJsonObject();
                float[] k = keys(row.getAsJsonArray("keys"));
                float[] idle = row.has("idle") ? keys(row.getAsJsonArray("idle")) : NONE;
                JsonElement hand = row.get("hand");
                Hand h = hand == null ? Hand.MAIN : Hand.valueOf(hand.getAsString().toUpperCase(java.util.Locale.ROOT));
                JsonObject wrist = row.getAsJsonObject("wrist3");
                JsonObject straight = row.getAsJsonObject("wrist3s");
                float swish = row.has("swish") ? row.get("swish").getAsFloat() : 0F;
                JsonArray bite = row.has("bite") ? row.getAsJsonArray("bite") : null;
                String sound = row.has("swishSound") ? row.get("swishSound").getAsString() : null;
                moves.put(m, new Clip(k, h, floats(wrist, "right"), floats(wrist, "left"), floats(straight, "right"),
                        floats(straight, "left"), swish, sound, bite == null ? 1F : bite.get(0).getAsFloat(),
                        bite == null ? 0F : bite.get(1).getAsFloat(), idle));
            }
            Vector3f grip = vec(st.getAsJsonArray("grip"));
            styles.put(e.getKey(), new Style(moves, quat(st.getAsJsonArray("vanilla")), grip, vec(st.getAsJsonArray("vanillaGrip")),
                    st.has("twin") && st.get("twin").getAsBoolean(), st.has("handGrip") ? vec(st.getAsJsonArray("handGrip")) : grip,
                    st.has("offHand") ? vec(st.getAsJsonArray("offHand")) : null,
                    st.has("vanillaOffHand") ? vec(st.getAsJsonArray("vanillaOffHand")) : null));
        }
        return new Data(schema, root.has("round") ? root.get("round").getAsInt() : 0, dt, wristDt, Map.copyOf(styles),
                java.util.Set.copyOf(flipped), Map.copyOf(grip3));
    }

    /** The set the jar carries. */
    public static Data jarData() {
        return JAR;
    }

    /** The set in use. */
    public static Data current() {
        return data;
    }

    /** Puts a whole set in use (Swing Studio's tuning, client thread, between moves); null puts the jar's back. */
    public static void swap(@Nullable Data d) {
        data = d == null ? JAR : d;
    }

    /** Rows of [t, the key's floats...] into STRIDE floats a key (missing ones 0: an idle hand leans no view). */
    private static float[] keys(JsonArray rows) {
        float[] k = new float[rows.size() * STRIDE];
        for (int i = 0; i < rows.size(); i++) {
            JsonArray key = rows.get(i).getAsJsonArray();
            for (int j = 0; j < STRIDE && j + 1 < key.size(); j++) k[i * STRIDE + j] = key.get(j + 1).getAsFloat();
        }
        return k;
    }

    private static float[] floats(@Nullable JsonObject o, String key) {
        if (o == null || !o.has(key)) return NONE;
        JsonArray a = o.getAsJsonArray(key);
        float[] out = new float[a.size() * 4];
        for (int i = 0; i < a.size(); i++) {
            JsonArray q = a.get(i).getAsJsonArray();
            for (int j = 0; j < 4; j++) out[i * 4 + j] = q.get(j).getAsFloat();
        }
        return out;
    }

    private static Quaternionf quat(JsonArray a) {
        return new Quaternionf(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat());
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    public static @Nullable Style style(String id) {
        return data.styles().get(id);
    }

    /** The hand a move strikes with (MAIN without keys). */
    public static Hand hand(@Nullable Style st, Archetype.@Nullable Move m) {
        Clip c = st == null || m == null ? null : st.moves().get(m);
        return c == null ? Hand.MAIN : c.hand();
    }

    public static float dt() {
        return data.dt();
    }

    /** The studio's swish sound for a move in place of its row's ("quick", "katana"), or null. */
    public static @Nullable String swishSound(@Nullable Style st, Archetype.@Nullable Move m) {
        Clip c = st == null || m == null ? null : st.moves().get(m);
        return c == null ? null : c.swishSound();
    }

    /** When a move's swish starts (ticks; 0 on the press, as before the studio). */
    public static float swish(@Nullable Style st, Archetype.@Nullable Move m) {
        Clip c = st == null || m == null ? null : st.moves().get(m);
        return c == null ? 0F : c.swish();
    }

    /**
     * The view's lean at {@code t} ticks of a move (yaw, pitch, roll degrees into {@code out}; + pitch looks down): each
     * blow leans the eye into it, a slam's crash pitching it down, a whirl turning it. False without keys.
     */
    public static boolean camera(@Nullable Style st, Archetype.Move m, float t, float[] out) {
        Clip c = st == null ? null : st.moves().get(m);
        if (c == null || c.size() == 0) return false;
        float[] k = c.keys();
        float x = Math.max(0, t / data.dt());
        int i = Math.min(c.size() - 1, (int) x), j = Math.min(c.size() - 1, i + 1);
        float u = Math.min(1F, x - i);
        for (int n = 0; n < 3; n++) out[n] = k[i * STRIDE + 7 + n] + (k[j * STRIDE + 7 + n] - k[i * STRIDE + 7 + n]) * u;
        return true;
    }

    /**
     * Third person: the wrist's turn of the weapon in the right or the left arm at {@code t} ticks of a move's clip, into
     * {@code out} (about the weapon's grip, in its item frame; ArmsWrist): its working side laid into the blow and the
     * weapon kept out of the body. {@code straight}: the straight clip plays (no Bendable Cuboids), its own keys where it
     * has them. False (out left alone) when it doesn't turn.
     */
    public static boolean wrist(@Nullable Style st, Archetype.Move m, boolean left, boolean straight, float t, Quaternionf out) {
        Clip c = st == null ? null : st.moves().get(m);
        float[] k = c == null ? NONE : left ? c.wristLeft() : c.wristRight();
        if (straight && c != null) {
            float[] s = left ? c.straightLeft() : c.straightRight();
            if (s.length > 0) k = s;
        }
        int n = k.length / 4;
        if (n == 0) return false;
        float wristDt = data.wristDt();
        float x = Math.max(0, t / wristDt);
        int i = Math.min(n - 1, (int) x), j = Math.min(n - 1, i + 1);
        W0.set(k[i * 4], k[i * 4 + 1], k[i * 4 + 2], k[i * 4 + 3]);
        W1.set(k[j * 4], k[j * 4 + 1], k[j * 4 + 2], k[j * 4 + 3]);
        W0.slerp(W1, Math.min(1F, x - i), out);
        return true;
    }

    private static final Quaternionf W0 = new Quaternionf(), W1 = new Quaternionf();

    /** How long a move's wrist keys run (ticks; 0 without them). */
    public static float wristLength(@Nullable Style st, Archetype.Move m) {
        Clip c = st == null ? null : st.moves().get(m);
        return c == null ? 0F : Math.max(c.wristRight().length, c.wristLeft().length) / 4 * data.wristDt();
    }

    /** Whether a weapon is turned over in its third-person hold (its wrist then turns turned over too). */
    public static boolean flipped3(String weapon) {
        return data.flipped().contains(weapon);
    }

    /** Where the fist closes on a weapon in third person, the wrist's pivot (item space): down a haft, or null at its grip. */
    public static @Nullable Vector3f grip3(String weapon) {
        return data.grip3().get(weapon);
    }

    private static final Quaternionf Q0 = new Quaternionf(), Q1 = new Quaternionf();
    private static final Vector3f A = new Vector3f(), B = new Vector3f();

    /**
     * The swing at {@code t} ticks of a move: the hand's move into {@code d} and its turn into {@code q}; a vanilla item
     * ({@code ours} false) is turned from its own hold to the same targets and moved so its grip lands where the forged
     * weapon's would. False when the lab has no keys for it (the row's own swing then plays). Client thread.
     */
    public static boolean sample(@Nullable Style st, Archetype.Move m, float t, boolean ours, Vector3f d, Quaternionf q) {
        Clip c = st == null ? null : st.moves().get(m);
        if (c == null || c.size() == 0) return false;
        return sample(st, c.keys(), t, ours, d, q);
    }

    /**
     * The hand not striking a move at {@code t} ticks of it (Clip's idle; for its own hand, mirrored by the caller like
     * an off-hand strike). False when it has none (it rests).
     */
    public static boolean idle(@Nullable Style st, Archetype.@Nullable Move m, float t, boolean ours, Vector3f d, Quaternionf q) {
        Clip c = st == null || m == null ? null : st.moves().get(m);
        if (c == null || c.idle().length == 0) return false;
        return sample(st, c.idle(), t, ours, d, q);
    }

    private static boolean sample(Style st, float[] k, float t, boolean ours, Vector3f d, Quaternionf q) {
        int n = k.length / STRIDE;
        float x = Math.max(0, t / data.dt());
        int i = Math.min(n - 1, (int) x), j = Math.min(n - 1, i + 1);
        float u = Math.min(1F, x - i);
        int a = i * STRIDE, b = j * STRIDE;
        d.set(k[a] + (k[b] - k[a]) * u, k[a + 1] + (k[b + 1] - k[a + 1]) * u, k[a + 2] + (k[b + 2] - k[a + 2]) * u);
        Q0.set(k[a + 3], k[a + 4], k[a + 5], k[a + 6]);
        Q1.set(k[b + 3], k[b + 4], k[b + 5], k[b + 6]);
        Q0.slerp(Q1, u, q);
        if (!ours) {
            // R_vanilla = R . C; and the grip: d + R grip_ours - R_vanilla grip_vanilla
            q.transform(A.set(st.grip()));
            d.add(A);
            q.mul(st.vanilla());
            q.transform(B.set(st.vanillaGrip()));
            d.sub(B);
        }
        return true;
    }
}
