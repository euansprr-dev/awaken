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
 * level against the gait, the body turning a little toward what it watches, a landing's squash, a hunter's stillness;
 * a flier or swimmer banks, pitches along its path, rides its wingbeat and curves its spine into turns ({@link Flight}).
 * A hand-built body's profile is keyed by its creature id.
 *
 * <p>Plain data (Gson and the classpath, no Minecraft classes), so GameTests load it on the server.
 */
public record MotionProfile(String id, @Nullable Gait gait, Body body, @Nullable Fly fly) {
    public static final String PATH = "/assets/awaken/life/motion.json";

    /**
     * The leg patterns: one foot at a time from the back forward, opposite sets, or the first slow and the second fast;
     * a bound (a walk in the ripple, and running the forefeet land together and the hind feet half a stride later, as an
     * otter lopes), or a hop (every foot at once: the body leaps and lands, as a rabbit or a cricket goes).
     */
    public enum Pattern { RIPPLE, ALTERNATE, RIPPLE_ALTERNATE, BOUND, HOP }

    /**
     * Which way a leg straight at rest bends: a quadruped's forelegs forward and hocks back, all forward, or all back; or
     * not at all (a blocky leg whose knee can't hide a bend moves as one piece, see {@link LegRig}).
     */
    public enum Knees { AUTO, FORWARD, BACK, RIGID }

    /**
     * The feet. {@code duty} is the share of a cycle a foot stands (walking, running), {@code lift} how high a step
     * rises (px, walking and running), {@code reach} how far (blocks) a planted foot may trail before it steps at once,
     * {@code settle} how far from home a foot may rest when the body stops before it steps back under it, {@code crouch}
     * how far (px) the body sinks on its legs, {@code radius} (px from the hip, 0 the rest pose) and {@code fan}
     * (degrees the front feet reach forward and the back ones back) where each foot stands, {@code regrip} the seconds
     * (least, most) between idle re-grips of one foot, {@code swing} the longest a step takes (seconds), {@code stride}
     * the blocks one cycle covers (walking, running; 0: the rig's), {@code bob} how far (px) the body sinks while a foot
     * is up (shared by the feet), {@code roll} how far (radians) it leans off a lifted foot (a quadruped's hind foot counts
     * 0.6, so a diagonal pair still tips it), {@code rock} how far (radians) it noses down while a forefoot is up
     * and up while a hind foot is (front against back ranks), {@code rear} (0..1) how far
     * the front pairs let go to the clips during a wind-up (a spider rearing its forelegs before it strikes),
     * {@code rearPairs} how many pairs that is (a roar lets one more go), {@code lag} the share of a cycle each pair's
     * ripple step follows the pair behind it (0: one wave over the side; a centipede's legs run in several; below 0 the
     * wave runs from the front: a quadruped's forefoot steps before the hind foot on its side, which keeps the two from
     * meeting under the belly), {@code lead} how much further ahead than half its stand a foot lands (a share of the
     * stand; it trails that much less: the leg reaches forward more than back),
     * {@code knees} which way a leg straight at rest bends (one bent at rest keeps its own way), or {@code rigid}: the
     * leg moves as one piece, swung from a pivot {@code shoulder} (a share of the leg's length) above its hip, as a
     * shoulder blade and a pelvis swing a real leg, its top sliding inside the body. {@code arms}: the arms walk too
     * (a knuckle-walker's long arms plant like forelegs). {@code mixed}: a one-bone leg walks rigid beside legs that
     * bend (a toad's stiff forelegs and folded hind legs, an otter's short forelegs and hocked hind legs). {@code hindBack}:
     * how far (px) the hind feet stand behind their rest spot (a toad's hind leg lies nearly straight at rest, so a foot
     * there could step no further forward).
     */
    public record Gait(Pattern pattern, float dutyWalk, float dutyRun, float liftWalk, float liftRun, float reach, float settle,
                       float crouch, float radius, float fan, float regripMin, float regripMax, float swing, float strideWalk,
                       float strideRun, float bob, float roll, float rock, float rear, int rearPairs, float lag, float lead, Knees knees, float shoulder,
                       boolean arms, boolean mixed, float hindBack) {}

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

    /**
     * Flight and swimming (SPEC §23, "Flight"): how a body carries itself through air, water or sand. {@code always}: it
     * is always under way (a serpent in sand, a ray over the dunes), else only while airborne or swimming. The named
     * bones: {@code wings} (the roots of each wing, whose stroke the body rides), {@code tips} (their tips, for a beat of
     * the layer's own), {@code spine} (the links behind the body, front first, that bend into a turn and carry the swim
     * wave), {@code fins} (folded back at speed). {@code flap} is a beat of the layer's own for a body whose clips only
     * glide (radians, cycles a second, the tips' share); {@code heave} how far (px per radian of stroke) the body rises
     * as the wings sweep down; {@code glide} the share of the stroke kept while it glides (diving, or a lull at speed);
     * {@code fast} the speed (blocks a second) of a full cruise; {@code bank} how far it rolls into a turn (radians per
     * radian a second of turn at full cruise); {@code climb} the share of its path's climb or dive the body pitches with;
     * {@code tilt} how far (radians) it noses down at full cruise; {@code level} (0..1) how much the head holds level;
     * {@code bend} how far (radians, the whole spine, per radian a second of turn) the spine curves into a turn;
     * {@code wave} how strong the spine's swim stroke is at rest and at full cruise (1: as the clip has it);
     * {@code tuck} how far (radians) the fins fold back at full cruise.
     */
    public record Fly(boolean always, String[] wings, String[] tips, String[] spine, String[] fins, float flapAmp, float flapHz, float tipAmp,
                      float heave, float glide, float fast, float bank, float climb, float tilt, float level, float bend, float waveSlow,
                      float waveFast, float tuck) {}

    private static final Set<String> FLY = Set.of("always", "wings", "tips", "spine", "fins", "flap", "heave", "glide", "fast", "bank", "climb",
            "tilt", "level", "bend", "wave", "tuck");
    private static final Set<String> GAIT = Set.of("pattern", "duty", "lift", "reach", "settle", "crouch", "radius", "fan", "regrip", "swing",
            "stride", "bob", "roll", "rock", "rear", "rearPairs", "lag", "lead", "knees", "shoulder", "arms", "mixed", "hindBack");
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
            Fly f = c.has("fly") ? fly(e.getKey(), c.getAsJsonObject("fly")) : null;
            m.put(e.getKey(), new MotionProfile(e.getKey(), g, b, f));
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
                num(o, "bob", 0.3F), num(o, "roll", 0.02F), num(o, "rock", 0), Math.clamp(num(o, "rear", 0), 0.0F, 1.0F), (int) num(o, "rearPairs", 1),
                Math.clamp(num(o, "lag", 0), -0.5F, 0.5F), Math.clamp(num(o, "lead", 0), 0.0F, 0.4F), Knees.valueOf(str(o, "knees", "auto").toUpperCase(Locale.ROOT)),
                Math.clamp(num(o, "shoulder", 0), 0.0F, 2.0F), o.has("arms") && o.get("arms").getAsBoolean(),
                o.has("mixed") && o.get("mixed").getAsBoolean(), Math.clamp(num(o, "hindBack", 0), 0.0F, 8.0F));
    }

    private static Fly fly(String id, JsonObject o) {
        for (String k : o.keySet()) if (!FLY.contains(k)) throw new IllegalStateException(PATH + ": " + id + ": unknown fly field " + k);
        float[] wave = pair(o, "wave", 1, 1);
        float[] flap = o.has("flap") ? floats(o.getAsJsonArray("flap")) : new float[3];
        if (flap.length != 3) throw new IllegalStateException(PATH + ": " + id + ": flap is [radians, cycles a second, tips' share]");
        if (o.has("tips") && flap[0] <= 0) throw new IllegalStateException(PATH + ": " + id + ": tips beat only with a flap of the layer's own");
        return new Fly(o.has("always") && o.get("always").getAsBoolean(), names(o, "wings"), names(o, "tips"), names(o, "spine"), names(o, "fins"),
                flap[0], flap[1], flap[2], num(o, "heave", 0), Math.clamp(num(o, "glide", 1), 0.0F, 1.0F), Math.max(0.5F, num(o, "fast", 4)),
                num(o, "bank", 0), num(o, "climb", 0), num(o, "tilt", 0), Math.clamp(num(o, "level", 0), 0.0F, 1.0F), num(o, "bend", 0),
                wave[0], wave[1], num(o, "tuck", 0));
    }

    private static String[] names(JsonObject o, String k) {
        if (!o.has(k)) return new String[0];
        JsonArray a = o.getAsJsonArray(k);
        String[] out = new String[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsString();
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
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
