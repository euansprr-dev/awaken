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
 * A creature's temperament for the life layer (SPEC §23), from {@code assets/awaken/life/creatures.json}: how fast it
 * breathes and blinks, how its head follows, how springy its ears and tails are, how heavy its steps, and which idle
 * fidgets it picks. Cute creatures are quick and bouncy, hunters smooth and still, beasts slow and heavy.
 *
 * <p>Plain data (Gson and the classpath, no Minecraft classes), so GameTests load it on the server.
 */
public record LifeProfile(String name, float breath, float breathDepth, float blinkMin, float blinkMax, float headSpeed, float headDamping,
                          float eyeLead, float springHz, float springDamping, float springGain, float bob, float sway, float lean,
                          float fidgetMin, float fidgetMax, float[] idles, float arms, float stagger, float mass) {

    /** The idle fidgets a body can play, each needing some of its bones ({@link Life}). */
    public enum Idle { LOOK, SNIFF, FLICK, SHAKE, STRETCH, YAWN, SCRATCH, PERK, SETTLE, GRAZE }

    public static final String PATH = "/assets/awaken/life/creatures.json";
    private static final Set<String> FIELDS = Set.of("temperament", "breath", "breathDepth", "blink", "headSpeed", "headDamping", "eyeLead",
            "springHz", "springDamping", "springGain", "bob", "sway", "lean", "fidget", "idles", "arms", "stagger", "mass");

    public boolean blinks() {
        return blinkMax > 0;
    }

    /** The file: temperaments (each over "default"), the role defaults, and the creature entries as written. */
    public record Data(Map<String, JsonObject> temperaments, Map<String, String> fauna, Map<String, String> tier, Map<String, JsonObject> creatures) {
        /** The temperament a creature gets: its entry's, else its fauna role's, else its tier's, else "default". */
        public String temperamentOf(String id, @Nullable String faunaRole, @Nullable String tierName) {
            JsonObject own = creatures.get(id);
            if (own != null && own.has("temperament")) return own.get("temperament").getAsString();
            String t = faunaRole != null ? fauna.get(faunaRole) : null;
            if (t == null && tierName != null) t = tier.get(tierName);
            return t != null ? t : "default";
        }
    }

    private static @Nullable Data data;
    private static final Map<String, LifeProfile> CACHE = new HashMap<>();

    public static synchronized Data data() {
        if (data == null) data = load();
        return data;
    }

    /** A creature's profile: its temperament with its own entry's fields on top. Cached; throws on bad data. */
    public static synchronized LifeProfile of(String id, @Nullable String faunaRole, @Nullable String tierName) {
        Data d = data();
        String t = d.temperamentOf(id, faunaRole, tierName);
        return CACHE.computeIfAbsent(id + "@" + t, k -> {
            JsonObject base = d.temperaments.get(t);
            if (base == null) throw new IllegalStateException(PATH + ": " + id + " names no temperament " + t);
            JsonObject own = d.creatures.get(id);
            return parse(t, own != null ? merge(base, own) : base);
        });
    }

    /** A temperament by name (another module's bodies, e.g. "citizen"); "default" if there is none. */
    public static synchronized LifeProfile temperament(String name) {
        Data d = data();
        String t = d.temperaments.containsKey(name) ? name : "default";
        return CACHE.computeIfAbsent("@" + t, k -> parse(t, d.temperaments.get(t)));
    }

    private static Data load() {
        JsonObject root;
        try (InputStream in = LifeProfile.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing " + PATH);
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("can't read " + PATH, e);
        }
        if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException(PATH + ": unknown schemaVersion");
        JsonObject temps = root.getAsJsonObject("temperaments");
        JsonObject base = temps.getAsJsonObject("default");
        Map<String, JsonObject> t = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : temps.entrySet()) {
            JsonObject m = merge(base, e.getValue().getAsJsonObject());
            parse(e.getKey(), m); // fail on load, not on first draw
            t.put(e.getKey(), m);
        }
        JsonObject roles = root.getAsJsonObject("roles");
        Map<String, String> fauna = strings(roles.getAsJsonObject("fauna")), tier = strings(roles.getAsJsonObject("tier"));
        for (String v : fauna.values()) if (!t.containsKey(v)) throw new IllegalStateException(PATH + ": no temperament " + v);
        for (String v : tier.values()) if (!t.containsKey(v)) throw new IllegalStateException(PATH + ": no temperament " + v);
        Map<String, JsonObject> creatures = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("creatures").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            String tn = c.has("temperament") ? c.get("temperament").getAsString() : "default";
            if (!t.containsKey(tn)) throw new IllegalStateException(PATH + ": " + e.getKey() + " names no temperament " + tn);
            parse(e.getKey(), merge(t.get(tn), c));
            creatures.put(e.getKey(), c);
        }
        return new Data(Map.copyOf(t), Map.copyOf(fauna), Map.copyOf(tier), Map.copyOf(creatures));
    }

    private static JsonObject merge(JsonObject under, JsonObject over) {
        JsonObject m = under.deepCopy();
        for (Map.Entry<String, JsonElement> e : over.entrySet()) {
            if (!FIELDS.contains(e.getKey())) throw new IllegalStateException(PATH + ": unknown field " + e.getKey());
            m.add(e.getKey(), e.getValue().deepCopy());
        }
        return m;
    }

    private static LifeProfile parse(String name, JsonObject o) {
        JsonArray blink = o.getAsJsonArray("blink"), fidget = o.getAsJsonArray("fidget");
        float[] idles = new float[Idle.values().length];
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("idles").entrySet()) {
            idles[Idle.valueOf(e.getKey().toUpperCase(Locale.ROOT)).ordinal()] = e.getValue().getAsFloat();
        }
        return new LifeProfile(name, f(o, "breath"), f(o, "breathDepth"), blink.get(0).getAsFloat(), blink.get(1).getAsFloat(),
                f(o, "headSpeed"), f(o, "headDamping"), f(o, "eyeLead"), f(o, "springHz"), f(o, "springDamping"), f(o, "springGain"),
                f(o, "bob"), f(o, "sway"), f(o, "lean"), fidget.get(0).getAsFloat(), fidget.get(1).getAsFloat(), idles, f(o, "arms"),
                f(o, "stagger"), f(o, "mass"));
    }

    private static float f(JsonObject o, String key) {
        return o.get(key).getAsFloat();
    }

    private static Map<String, String> strings(JsonObject o) {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
        return m;
    }
}
