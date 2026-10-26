package dev.awaken.core.audio.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A soundscape as the Conductor reads it: {@code assets/<ns>/soundscape/<path>.json} for the id {@code <ns>:<path>}.
 * The overworld's families are {@code awaken:family/<family>} (biomes join a family through the tag
 * {@code #awaken:soundscape/<family>}, {@link Families}); {@code awaken:world} plays everywhere under them (wind,
 * leaves). Any module adds a place of its own the same way. docs/sound/PLAN.md §6.6.
 *
 * <pre>
 * { "schemaVersion": 1, "title": "Greenwood",
 *   "air":      [ {"file": "awaken:world/air_greenwood_a", "gain": 0.8, "when": {"dawn": 1, "night": 0.5},
 *                  "rain": 0.6, "drive": "constant"} ],                        at most 3; one streamed mix
 *   "textures": [ {"sound": "awaken:amb.lands_rustle", "anchor": "canopy", "gain": 1, "drive": "rustle"} ],
 *   "events":   [ {"sound": "awaken:fauna.hollow_owl.ambient", "anchor": "leaves", "per_minute": 1.2,
 *                  "when": {"dawn": 0, "day": 0, "night": 1}, "rain": 0.2, "answer": 0.4, "answer_delay": [1, 3],
 *                  "hush": true, "volume": [0.4, 0.8], "pitch": [0.92, 1.08], "distance": [12, 36]} ],
 *   "lull":     {"every": [150, 300], "length": [20, 40], "depth": 0.1},
 *   "echo":     {"chance": 0.35, "delay": [0.6, 1.2], "volume": 0.35, "distance": [14, 22], "pitch": 0.97} }
 * </pre>
 * {@code rain}: the multiplier in full rain (eased from 1 by the rain level). {@code drive}: what else scales a layer
 * ({@link Drive}). Events per minute are at the curve's full value; a lull scales every event's rate by its depth.
 * {@code echo}: a place answers the sounds you make (Mirror Marsh): now and then one comes back a moment later from
 * across the water.
 */
public record SoundscapeDef(Identifier id, String title, List<Air> air, List<Texture> textures, List<Event> events, Lull lull,
                            Echo echo) {
    public static final int SCHEMA = 1, MAX_AIR = 3, MAX_TEXTURES = 4, MAX_EVENTS = 24;

    /** What scales a layer besides its day curve and the rain: the listener's own state. */
    public enum Drive {
        /** Nothing else. */
        CONSTANT,
        /** Leaves overhead in the wind: canopy × (0.12 + 0.88 strength) × (0.75 + 0.25 gust). */
        RUSTLE,
        /** Wind that grows with height and open sky and softens under trees. */
        HIGH_WIND,
        /** Open sky over the listener (outdoor air that a roof takes away). */
        OPEN,
        /** The wind's strength where you stand, swelling in gusts (0.15 + 0.85 strength × (0.6 + 0.4 gust)). */
        WIND,
        /** Shallow under the ground: 1 down to y 48, falling to 0 at y -64 (the Hollows' upper air). */
        SHALLOW,
        /** Deep under the ground: 0 down to y 48, rising to 1 at y -64 (the Hollows' undertone: the deeper, the lower). */
        DEEP
    }

    /** Where a positional sound comes from: real blocks found around the listener. */
    public enum Anchor {
        /** The canopy overhead: the leaves columns of the listener's ring, at leaf height. */
        CANOPY,
        /** A leaves block in the ring (a bird in a tree). */
        LEAVES,
        /** The ground surface in the ring (grass, scrub, stone). */
        GROUND,
        /** A water surface in the ring. */
        WATER,
        /** Open air above the ring (a bird overhead). */
        SKY,
        /** Far off on the horizon, out of the ring (a distant call). */
        FAR
    }

    public record Air(Identifier file, float gain, DayCurve when, float rain, Drive drive) {}

    public record Texture(Identifier sound, Anchor anchor, float gain, DayCurve when, float rain, Drive drive) {}

    public record Event(Identifier sound, Anchor anchor, float perMinute, DayCurve when, float rain, float answer,
                        float answerMin, float answerMax, boolean hush, float volMin, float volMax, float pitchMin,
                        float pitchMax, float distMin, float distMax) {}

    public record Echo(float chance, float delayMin, float delayMax, float volume, float distMin, float distMax, float pitch) {
        public static final Echo NONE = new Echo(0, 0, 0, 0, 0, 0, 1);
    }

    public record Lull(float everyMin, float everyMax, float lengthMin, float lengthMax, float depth) {
        public static final Lull NONE = new Lull(0, 0, 0, 0, 1);

        public boolean none() {
            return everyMax <= 0 || depth >= 1;
        }
    }

    /** Where a soundscape's file lives. */
    public static Identifier path(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "soundscape/" + id.getPath() + ".json");
    }

    /** Parses a soundscape; throws IllegalArgumentException with the reason when it is malformed. */
    public static SoundscapeDef parse(Identifier id, JsonObject o) {
        int schema = o.has("schemaVersion") ? o.get("schemaVersion").getAsInt() : 0;
        if (schema != SCHEMA) throw new IllegalArgumentException(id + ": schemaVersion " + schema + " (expected " + SCHEMA + ")");
        List<Air> air = new ArrayList<>();
        for (JsonElement e : arr(o, "air")) {
            JsonObject a = e.getAsJsonObject();
            air.add(new Air(Identifier.parse(a.get("file").getAsString()), f(a, "gain", 1F), DayCurve.parse(obj(a, "when")),
                    f(a, "rain", 1F), drive(id, a)));
        }
        List<Texture> textures = new ArrayList<>();
        for (JsonElement e : arr(o, "textures")) {
            JsonObject t = e.getAsJsonObject();
            textures.add(new Texture(Identifier.parse(t.get("sound").getAsString()), anchor(id, t), f(t, "gain", 1F),
                    DayCurve.parse(obj(t, "when")), f(t, "rain", 1F), drive(id, t)));
        }
        List<Event> events = new ArrayList<>();
        for (JsonElement e : arr(o, "events")) {
            JsonObject v = e.getAsJsonObject();
            float[] delay = range(v, "answer_delay", 1F, 3F), vol = range(v, "volume", 0.6F, 1F),
                    pitch = range(v, "pitch", 0.94F, 1.06F), dist = range(v, "distance", 8F, 32F);
            float perMinute = v.get("per_minute").getAsFloat();
            if (perMinute <= 0 || perMinute > 60) throw new IllegalArgumentException(id + ": per_minute " + perMinute);
            events.add(new Event(Identifier.parse(v.get("sound").getAsString()), anchor(id, v), perMinute,
                    DayCurve.parse(obj(v, "when")), f(v, "rain", 1F), f(v, "answer", 0F), delay[0], delay[1],
                    !v.has("hush") || v.get("hush").getAsBoolean(), vol[0], vol[1], pitch[0], pitch[1], dist[0], dist[1]));
        }
        if (air.size() > MAX_AIR) throw new IllegalArgumentException(id + ": " + air.size() + " air layers (at most " + MAX_AIR + ")");
        if (textures.size() > MAX_TEXTURES) throw new IllegalArgumentException(id + ": " + textures.size() + " textures (at most " + MAX_TEXTURES + ")");
        if (events.size() > MAX_EVENTS) throw new IllegalArgumentException(id + ": " + events.size() + " events (at most " + MAX_EVENTS + ")");
        Lull lull = Lull.NONE;
        JsonObject l = obj(o, "lull");
        if (l != null) {
            float[] every = range(l, "every", 0F, 0F), length = range(l, "length", 0F, 0F);
            lull = new Lull(every[0], every[1], length[0], length[1], f(l, "depth", 1F));
        }
        Echo echo = Echo.NONE;
        JsonObject ec = obj(o, "echo");
        if (ec != null) {
            float[] delay = range(ec, "delay", 0.6F, 1.2F), dist = range(ec, "distance", 14F, 22F);
            echo = new Echo(Math.min(1F, f(ec, "chance", 0.35F)), delay[0], delay[1], f(ec, "volume", 0.35F), dist[0], dist[1], f(ec, "pitch", 0.97F));
        }
        String title = o.has("title") ? o.get("title").getAsString() : id.toString();
        return new SoundscapeDef(id, title, List.copyOf(air), List.copyOf(textures), List.copyOf(events), lull, echo);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key) : null;
    }

    private static float f(JsonObject o, String key, float def) {
        return o.has(key) ? Math.max(0F, o.get(key).getAsFloat()) : def;
    }

    private static float[] range(JsonObject o, String key, float lo, float hi) {
        if (!o.has(key)) return new float[]{lo, hi};
        JsonArray a = o.getAsJsonArray(key);
        float x = a.get(0).getAsFloat(), y = a.size() > 1 ? a.get(1).getAsFloat() : x;
        return new float[]{Math.min(x, y), Math.max(x, y)};
    }

    private static Drive drive(Identifier id, JsonObject o) {
        String s = o.has("drive") ? o.get("drive").getAsString() : "constant";
        try {
            return Drive.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(id + ": drive " + s);
        }
    }

    private static Anchor anchor(Identifier id, JsonObject o) {
        String s = o.get("anchor").getAsString();
        try {
            return Anchor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(id + ": anchor " + s);
        }
    }
}
