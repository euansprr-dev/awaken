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
 * style takes the same targets from its own hold ({@link #sample}). Read once, from the jar (both are client data the
 * server never needs, but plain maths: a GameTest can hold the keys to their rules).
 */
public final class FpSwings {
    private FpSwings() {}

    static final String PATH = "/data/awaken/arms/fp_swings.json";

    /** Floats a key carries: the hand's move (3), its turn (4), the view's lean (yaw, pitch, roll degrees). */
    static final int STRIDE = 10;

    /** Which hand a move strikes with (a weapon in each hand, the Daggers): the main, the off (the main's mirrored), both. */
    public enum Hand { MAIN, OFF, BOTH }

    /**
     * One move's keys: [dx, dy, dz, qx, qy, qz, qw, yaw, pitch, roll] per step of {@code dt} ticks, and its hand; and in
     * third person the wrist's turn of the weapon in each arm (a quaternion [x, y, z, w] per {@code wristDt} ticks, in
     * the forged weapon's item frame, about its grip; empty when it doesn't turn): for the bent clip (Bendable Cuboids)
     * and, where third person's rig solved its arms straight too (tools/art/specs/arms_rig4.py), for the straight one.
     */
    public record Clip(float[] keys, Hand hand, float[] wristRight, float[] wristLeft, float[] straightRight, float[] straightLeft) {
        public int size() {
            return keys.length / STRIDE;
        }
    }

    /** A style's moves, and how a vanilla item held in it is turned to the same targets. */
    /**
     * A style's moves, how a vanilla item held in it is turned to the same targets, the grips (where its forged weapon's
     * and a vanilla item's swing turn about) and where the hand closes on its weapon (hand space, from vanilla's rest: the
     * arm drawn to it, FirstPerson.View's grips).
     */
    public record Style(Map<Archetype.Move, Clip> moves, Quaternionf vanilla, Vector3f grip, Vector3f vanillaGrip, boolean twin,
                        Vector3f hand) {}

    private static final Map<String, Style> STYLES = new HashMap<>();
    private static float dt = 0.25F, wristDt = 0.5F;
    private static final float[] NONE = new float[0];
    /** Weapons turned over in their third-person hold (their single edge on the other side from their style's). */
    private static final java.util.Set<String> FLIPPED = new java.util.HashSet<>();

    /**
     * Loads the swings now (the client calls this on a worker at start): reading the 1.1 MB file on first use cost a
     * 30-80 ms hitch on the render thread the first time anyone with a blade was drawn.
     */
    public static void warm() {
        // the static block below has run by the time this returns
    }

    static {
        try (InputStream in = FpSwings.class.getResourceAsStream(PATH)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                dt = root.get("dt").getAsFloat();
                if (root.has("wristDt")) wristDt = root.get("wristDt").getAsFloat();
                if (root.has("flipped3")) root.getAsJsonArray("flipped3").forEach(id -> FLIPPED.add(id.getAsString()));
                for (var e : root.getAsJsonObject("styles").entrySet()) {
                    JsonObject st = e.getValue().getAsJsonObject();
                    Map<Archetype.Move, Clip> moves = new EnumMap<>(Archetype.Move.class);
                    for (var mv : st.getAsJsonObject("moves").entrySet()) {
                        Archetype.Move m = Archetype.Move.byKey(mv.getKey());
                        if (m == null) continue;
                        JsonArray keys = mv.getValue().getAsJsonObject().getAsJsonArray("keys");
                        float[] k = new float[keys.size() * STRIDE];
                        for (int i = 0; i < keys.size(); i++) {
                            JsonArray row = keys.get(i).getAsJsonArray();
                            for (int j = 0; j < STRIDE && j + 1 < row.size(); j++) k[i * STRIDE + j] = row.get(j + 1).getAsFloat();
                        }
                        JsonElement hand = mv.getValue().getAsJsonObject().get("hand");
                        Hand h = hand == null ? Hand.MAIN : Hand.valueOf(hand.getAsString().toUpperCase(java.util.Locale.ROOT));
                        JsonObject wrist = mv.getValue().getAsJsonObject().getAsJsonObject("wrist3");
                        JsonObject straight = mv.getValue().getAsJsonObject().getAsJsonObject("wrist3s");
                        moves.put(m, new Clip(k, h, floats(wrist, "right"), floats(wrist, "left"), floats(straight, "right"),
                                floats(straight, "left")));
                    }
                    Vector3f grip = vec(st.getAsJsonArray("grip"));
                    STYLES.put(e.getKey(), new Style(moves, quat(st.getAsJsonArray("vanilla")), grip, vec(st.getAsJsonArray("vanillaGrip")),
                            st.has("twin") && st.get("twin").getAsBoolean(), st.has("handGrip") ? vec(st.getAsJsonArray("handGrip")) : grip));
                }
            }
            Awaken.LOG.info("[arms] first-person swings for {} styles", STYLES.size());
        } catch (java.io.IOException | RuntimeException e) {
            Awaken.LOG.error("[arms] {} unreadable: the swings fall back to the rows' own", PATH, e);
        }
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
        return STYLES.get(id);
    }

    /** The hand a move strikes with (MAIN without keys). */
    public static Hand hand(@Nullable Style st, Archetype.@Nullable Move m) {
        Clip c = st == null || m == null ? null : st.moves().get(m);
        return c == null ? Hand.MAIN : c.hand();
    }

    public static float dt() {
        return dt;
    }

    /**
     * The view's lean at {@code t} ticks of a move (yaw, pitch, roll degrees into {@code out}; + pitch looks down): each
     * blow leans the eye into it, a slam's crash pitching it down, a whirl turning it. False without keys.
     */
    public static boolean camera(@Nullable Style st, Archetype.Move m, float t, float[] out) {
        Clip c = st == null ? null : st.moves().get(m);
        if (c == null || c.size() == 0) return false;
        float[] k = c.keys();
        float x = Math.max(0, t / dt);
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
        return c == null ? 0F : Math.max(c.wristRight().length, c.wristLeft().length) / 4 * wristDt;
    }

    /** Whether a weapon is turned over in its third-person hold (its wrist then turns turned over too). */
    public static boolean flipped3(String weapon) {
        return FLIPPED.contains(weapon);
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
        float[] k = c.keys();
        float x = Math.max(0, t / dt);
        int i = Math.min(c.size() - 1, (int) x), j = Math.min(c.size() - 1, i + 1);
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
