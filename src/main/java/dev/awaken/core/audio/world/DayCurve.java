package dev.awaken.core.audio.world;

import com.google.gson.JsonObject;

/**
 * How loud something is over a day: six anchors on the overworld clock, eased into each other with a cosine, so a
 * dawn chorus swells and fades rather than switching. Ticks: dawn 23500 (05:30), morning 2000 (08:00), day 6000
 * (noon), dusk 12500 (18:30), night 15000 (21:00), late 21000 (03:00). JSON {@code {"dawn": 1, "day": 0.3, ...}}:
 * a missing anchor takes its neighbour's value (morning = day, late = night), the rest default to 1.
 */
public record DayCurve(float dawn, float morning, float day, float dusk, float night, float late) {
    public static final DayCurve ALWAYS = new DayCurve(1, 1, 1, 1, 1, 1);
    private static final int[] AT = {-500, 2000, 6000, 12500, 15000, 21000, 23500};

    /** The curve's value at a time of day (overworld clock ticks, any value; taken mod 24000). */
    public float at(long dayTime) {
        int t = (int) Math.floorMod(dayTime + 500, 24000L) - 500; // -500 .. 23499, dawn first
        int i = 0;
        while (i < AT.length - 2 && t >= AT[i + 1]) i++;
        float k = (t - AT[i]) / (float) (AT[i + 1] - AT[i]);
        float e = 0.5F - 0.5F * (float) Math.cos(Math.PI * k);
        float a = anchor(i), b = anchor(i + 1);
        return a + (b - a) * e;
    }

    private float anchor(int i) {
        return switch (i) {
            case 1 -> morning;
            case 2 -> day;
            case 3 -> dusk;
            case 4 -> night;
            case 5 -> late;
            default -> dawn;
        };
    }

    public static DayCurve parse(JsonObject o) {
        if (o == null) return ALWAYS;
        float day = f(o, "day", 1F), night = f(o, "night", 1F);
        return new DayCurve(f(o, "dawn", 1F), f(o, "morning", day), day, f(o, "dusk", 1F), night, f(o, "late", night));
    }

    private static float f(JsonObject o, String key, float def) {
        return o.has(key) ? Math.max(0F, Math.min(1F, o.get(key).getAsFloat())) : def;
    }
}
