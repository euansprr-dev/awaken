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
 * vanilla's rest and its turn about its own place, for the style's own forged weapon. A cut there is an arc about the
 * sword arm's pivot, the edge leading, through a point just under the crosshair on the hit tick. A vanilla item in the
 * style takes the same targets from its own hold ({@link #sample}). Read once, from the jar (both are client data the
 * server never needs, but plain maths: a GameTest can hold the keys to their rules).
 */
public final class FpSwings {
    private FpSwings() {}

    static final String PATH = "/data/awaken/arms/fp_swings.json";

    /** One move's keys: [dx, dy, dz, qx, qy, qz, qw] per step of {@code dt} ticks. */
    public record Clip(float[] keys) {
        public int size() {
            return keys.length / 7;
        }
    }

    /** A style's moves, and how a vanilla item held in it is turned to the same targets. */
    public record Style(Map<Archetype.Move, Clip> moves, Quaternionf vanilla, Vector3f grip, Vector3f vanillaGrip) {}

    private static final Map<String, Style> STYLES = new HashMap<>();
    private static float dt = 0.25F;

    static {
        try (InputStream in = FpSwings.class.getResourceAsStream(PATH)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                dt = root.get("dt").getAsFloat();
                for (var e : root.getAsJsonObject("styles").entrySet()) {
                    JsonObject st = e.getValue().getAsJsonObject();
                    Map<Archetype.Move, Clip> moves = new EnumMap<>(Archetype.Move.class);
                    for (var mv : st.getAsJsonObject("moves").entrySet()) {
                        Archetype.Move m = Archetype.Move.byKey(mv.getKey());
                        if (m == null) continue;
                        JsonArray keys = mv.getValue().getAsJsonObject().getAsJsonArray("keys");
                        float[] k = new float[keys.size() * 7];
                        for (int i = 0; i < keys.size(); i++) {
                            JsonArray row = keys.get(i).getAsJsonArray();
                            for (int j = 0; j < 7; j++) k[i * 7 + j] = row.get(j + 1).getAsFloat();
                        }
                        moves.put(m, new Clip(k));
                    }
                    STYLES.put(e.getKey(), new Style(moves, quat(st.getAsJsonArray("vanilla")), vec(st.getAsJsonArray("grip")),
                            vec(st.getAsJsonArray("vanillaGrip"))));
                }
            }
            Awaken.LOG.info("[arms] first-person swings for {} styles", STYLES.size());
        } catch (java.io.IOException | RuntimeException e) {
            Awaken.LOG.error("[arms] {} unreadable: the swings fall back to the rows' own", PATH, e);
        }
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

    public static float dt() {
        return dt;
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
        int a = i * 7, b = j * 7;
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
