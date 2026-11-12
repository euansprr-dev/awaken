package dev.awaken.lens;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One grade (SPEC §22 "Grades"): the player's choice of colour and atmosphere over every look, as data. A grade is a
 * file {@code assets/<namespace>/lens_grade/<id>.json}, so a new one is a file and nothing else:
 *
 * <pre>
 * { "schemaVersion": 1, "name": "Vivid", "order": 10,
 *   "colour": { "vibrance": 0.4, "dark": 0.3, "rise": 0.1, "punch": 0.25, "azure": 0.5, "leaf": 0.5, "gold": 0.3, "split": 0.25 },
 *   "air":    { "haze": 1.3 },
 *   "look":   { "scale": { "bloom": 1.15 }, "add": { "warmth": 0.02 } } }
 * </pre>
 *
 * {@code colour} is the grade's own step at the end of Lens's colour stage (lens_composite.glsl, lensGrade), each 0..1
 * and 0 for none: {@code vibrance} (the less saturated a colour, the more it gains), {@code dark} (the darks keep their
 * colour), {@code rise} (the lights and upper mids come up, the darks stay), {@code punch} (an S around the middle),
 * {@code azure} (a violet blue toward azure), {@code leaf} (greens toward a sunlit yellow-green), {@code gold} (warm
 * colours glow), {@code split} (warm lights, cool shade). {@code air.haze} thickens the Air's near haze (1 as it is, to
 * 3). {@code look} scales or adds to any number of the place's look by its name in the looks files (Look.parse).
 *
 * <p>Pure data, no client classes: the client's state is {@code lens.client.LensGrades}.
 */
public record LensGrade(String id, String name, int order, float[] colour, float haze, Map<String, Float> scale, Map<String, Float> add) {
    public static final int SCHEMA_VERSION = 1;
    /** The colour step's numbers, in the order the shader's two parameter texels hold them. */
    public static final String[] COLOUR = {"vibrance", "dark", "rise", "punch", "azure", "leaf", "gold", "split"};
    /** The look as it was built: every place's own colours, nothing over them. Always first, never from a file. */
    public static final LensGrade AWAKEN = new LensGrade("awaken", "Awaken", 0, new float[COLOUR.length], 1f, Map.of(), Map.of());

    /** Reads a grade; throws IllegalArgumentException naming what is wrong with it. */
    public static LensGrade parse(String id, JsonObject o) {
        int version = o.has("schemaVersion") ? o.get("schemaVersion").getAsInt() : 0;
        if (version != SCHEMA_VERSION) throw new IllegalArgumentException("schemaVersion " + version + " (expected " + SCHEMA_VERSION + ")");
        if (!o.has("name")) throw new IllegalArgumentException("no name");
        float[] colour = new float[COLOUR.length];
        if (o.has("colour")) {
            JsonObject c = o.getAsJsonObject("colour");
            for (Map.Entry<String, JsonElement> e : c.entrySet()) {
                int at = java.util.Arrays.asList(COLOUR).indexOf(e.getKey());
                if (at < 0) throw new IllegalArgumentException("colour has no \"" + e.getKey() + "\"");
                float v = e.getValue().getAsFloat();
                if (!(v >= 0f && v <= 1f)) throw new IllegalArgumentException("colour." + e.getKey() + " " + v + " is outside 0..1");
                colour[at] = v;
            }
        }
        float haze = 1f;
        if (o.has("air")) {
            JsonObject a = o.getAsJsonObject("air");
            if (a.has("haze")) haze = a.get("haze").getAsFloat();
            if (!(haze >= 1f && haze <= 3f)) throw new IllegalArgumentException("air.haze " + haze + " is outside 1..3");
        }
        Map<String, Float> scale = new LinkedHashMap<>(), add = new LinkedHashMap<>();
        if (o.has("look")) {
            JsonObject l = o.getAsJsonObject("look");
            if (l.has("scale")) for (Map.Entry<String, JsonElement> e : l.getAsJsonObject("scale").entrySet()) scale.put(e.getKey(), e.getValue().getAsFloat());
            if (l.has("add")) for (Map.Entry<String, JsonElement> e : l.getAsJsonObject("add").entrySet()) add.put(e.getKey(), e.getValue().getAsFloat());
        }
        return new LensGrade(id, o.get("name").getAsString(), o.has("order") ? o.get("order").getAsInt() : 100, colour, haze, scale, add);
    }
}
