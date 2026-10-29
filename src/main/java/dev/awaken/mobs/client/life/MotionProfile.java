package dev.awaken.mobs.client.life;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How a body of ours moves on its legs and carries its weight (SPEC §23, "Motion"), from
 * {@code assets/awaken/life/motion.json}, keyed by rig id. A body with no entry moves as before; one with an entry gets
 * the layer's motion on top of its clips: feet planted on the ground that step in a gait ({@link Legs}), the head held
 * level against the gait, the body turning a little toward what it watches, a landing's squash, a hunter's stillness.
 *
 * <p>Plain data (Gson and the classpath, no Minecraft classes), so GameTests load it on the server.
 */
public record MotionProfile(String id, @Nullable Gait gait, Body body) {
    public static final String PATH = "/assets/awaken/life/motion.json";

    /** The leg patterns: one foot at a time from the back forward, opposite sets, or the first slow and the second fast. */
    public enum Pattern { RIPPLE, ALTERNATE, RIPPLE_ALTERNATE }

    /** Which way a leg straight at rest bends: a quadruped's forelegs forward and hocks back, all forward, or all back. */
    public enum Knees { AUTO, FORWARD, BACK }

    /**
     * The feet. {@code duty} is the share of a cycle a foot stands (walking, running), {@code lift} how high a step
     * rises (px, walking and running), {@code reach} how far (blocks) a planted foot may trail before it steps at once,
     * {@code settle} how far from home a foot may rest when the body stops before it steps back under it, {@code crouch}
     * how far (px) the body sinks on its legs, {@code radius} (px from the hip, 0 the rest pose) and {@code fan}
     * (degrees the front feet reach forward and the back ones back) where each foot stands, {@code regrip} the seconds
     * (least, most) between idle re-grips of one foot, {@code swing} the longest a step takes (seconds), {@code stride}
     * the blocks one cycle covers (walking, running; 0: the rig's), {@code bob} how far (px) the body sinks while a foot
     * is up (shared by the feet), {@code roll} how far (radians) it leans off a lifted foot, {@code rear} (0..1) how far
     * the front pairs let go to the clips during a wind-up (a spider rearing its forelegs before it strikes),
     * {@code rearPairs} how many pairs that is (a roar lets one more go), {@code lag} the share of a cycle each pair's
     * ripple step follows the pair behind it (0: one wave over the side; a centipede's legs run in several),
     * {@code knees} which way a leg straight at rest bends (one bent at rest keeps its own way).
     */
    public record Gait(Pattern pattern, float dutyWalk, float dutyRun, float liftWalk, float liftRun, float reach, float settle,
                       float crouch, float radius, float fan, float regripMin, float regripMax, float swing, float strideWalk,
                       float strideRun, float bob, float roll, float rear, int rearPairs, float lag, Knees knees) {}

    /**
     * The body. {@code headLevel} (0..1) how much the head holds level against the gait's pitch and roll,
     * {@code twist} (0..1) the share of its look the body turns, {@code slope} (0..1) how much it pitches and rolls with
     * the ground under its feet, {@code land} how hard a landing squashes it, {@code still}: a hunter that freezes
     * while it stalks (no fidgets, no re-grips, a held breath), {@code creep} how far (px) it sinks on its legs then,
     * {@code shift} how far (px) its weight drifts over its planted feet while it stands, {@code nod} how far (radians)
     * the neck dips while a forefoot is up (a grazer's walk).
     */
    public record Body(float headLevel, float twist, float slope, float land, boolean still, float creep, float shift, float nod) {
        static final Body NONE = new Body(0, 0, 0, 0, false, 0, 0, 0);
    }

    private static final Set<String> GAIT = Set.of("pattern", "duty", "lift", "reach", "settle", "crouch", "radius", "fan", "regrip", "swing",
            "stride", "bob", "roll", "rear", "rearPairs", "lag", "knees");
    private static final Set<String> BODY = Set.of("headLevel", "twist", "slope", "land", "still", "creep", "shift", "nod");

    private static @Nullable Map<String, MotionProfile> all;

    /** A rig's profile, or null when it moves the old way. */
    public static @Nullable MotionProfile of(String rig) {
        return all().get(rig);
    }

    public static synchronized Map<String, MotionProfile> all() {
        if (all == null) all = load();
        return all;
    }

    private static Map<String, MotionProfile> load() {
        JsonObject root;
        try (InputStream in = MotionProfile.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing " + PATH);
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("can't read " + PATH, e);
        }
        if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException(PATH + ": unknown schemaVersion");
        Map<String, MotionProfile> m = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("creatures").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            Gait g = c.has("gait") ? gait(e.getKey(), c.getAsJsonObject("gait")) : null;
            Body b = c.has("body") ? body(e.getKey(), c.getAsJsonObject("body")) : Body.NONE;
            m.put(e.getKey(), new MotionProfile(e.getKey(), g, b));
        }
        return Map.copyOf(m);
    }

    private static Gait gait(String id, JsonObject o) {
        for (String k : o.keySet()) if (!GAIT.contains(k)) throw new IllegalStateException(PATH + ": " + id + ": unknown gait field " + k);
        Pattern p = Pattern.valueOf(str(o, "pattern", "alternate").toUpperCase(Locale.ROOT).replace('-', '_'));
        float[] duty = pair(o, "duty", 0.65F, 0.5F), lift = pair(o, "lift", 2.5F, 4.0F), regrip = pair(o, "regrip", 0, 0),
                stride = pair(o, "stride", 0, 0);
        if (duty[0] <= 0.2F || duty[0] >= 0.95F || duty[1] <= 0.2F || duty[1] >= 0.95F) throw new IllegalStateException(PATH + ": " + id + ": duty out of 0.2..0.95");
        return new Gait(p, duty[0], duty[1], lift[0], lift[1], num(o, "reach", 0.6F), num(o, "settle", 0.1F), num(o, "crouch", 0),
                num(o, "radius", 0), num(o, "fan", 0), regrip[0], regrip[1], num(o, "swing", 0.35F), stride[0], stride[1],
                num(o, "bob", 0.3F), num(o, "roll", 0.02F), Math.clamp(num(o, "rear", 0), 0.0F, 1.0F), (int) num(o, "rearPairs", 1),
                Math.clamp(num(o, "lag", 0), 0.0F, 0.5F), Knees.valueOf(str(o, "knees", "auto").toUpperCase(Locale.ROOT)));
    }

    private static Body body(String id, JsonObject o) {
        for (String k : o.keySet()) if (!BODY.contains(k)) throw new IllegalStateException(PATH + ": " + id + ": unknown body field " + k);
        return new Body(num(o, "headLevel", 0), num(o, "twist", 0), num(o, "slope", 0), num(o, "land", 0),
                o.has("still") && o.get("still").getAsBoolean(), num(o, "creep", 0), num(o, "shift", 0), num(o, "nod", 0));
    }

    private static float num(JsonObject o, String k, float d) {
        return o.has(k) ? o.get(k).getAsFloat() : d;
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }

    private static float[] pair(JsonObject o, String k, float a, float b) {
        if (!o.has(k)) return new float[]{a, b};
        JsonArray arr = o.getAsJsonArray(k);
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat()};
    }
}
