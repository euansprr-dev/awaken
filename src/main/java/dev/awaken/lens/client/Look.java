package dev.awaken.lens.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One dimension's look, as a flat float array so the client can ease between looks every tick without allocating.
 * Field order is the layout {@link LensParams} packs into the shader's parameter texture.
 */
final class Look {
    private Look() {}

    static final int EXPOSURE = 0, CONTRAST = 1, SATURATION = 2, WARMTH = 3, SHADOW_TINT = 4, HIGHLIGHT_TINT = 7,
            SPLIT_TONE = 10, VIGNETTE = 11, BLOOM = 12, BLOOM_THRESHOLD = 13, SKY_BLOOM = 14, SHAFTS = 15, HAZE = 16,
            HAZE_START = 17, HAZE_TINT = 18, HAZE_TINT_MIX = 21, HAZE_DESATURATE = 22, SUNSET_WARMTH = 23, SUN_GLOW = 24,
            // Lens 2 (Cinematic and Ultra, SPEC §22): the sun's light and shade, ambient occlusion, the filmic shoulder
            LIGHT = 25, SHADE = 26, SHADE_TINT = 27, KEY_WARMTH = 30, AO = 31, FILMIC = 32,
            // and its atmosphere: the sunlit mist and how high it reaches, lights glowing in fog, the sky's scattering
            MIST = 33, MIST_HEIGHT = 34, FOG_GLOW = 35, SKY_GLOW = 36,
            // and water and magic: reflections on water, heat shimmer, frost at the frame's edge, emissive glow
            WATER = 37, HEAT = 38, FROST = 39, GLOW = 40,
            // and the sky: how much of it Lens's own volumetric clouds cover (0: none, vanilla's stay)
            CLOUDS = 41, SIZE = 42;

    /** The neutral look: the frame exactly as the game drew it, apart from the dither. */
    static float[] neutral() {
        float[] v = new float[SIZE];
        v[EXPOSURE] = v[CONTRAST] = v[SATURATION] = 1.0f;
        for (int i = 0; i < 3; i++) v[SHADOW_TINT + i] = v[HIGHLIGHT_TINT + i] = v[HAZE_TINT + i] = 0.5f;
        v[BLOOM_THRESHOLD] = 1.0f;
        v[HAZE_START] = 0.4f;
        for (int i = 0; i < 3; i++) v[SHADE_TINT + i] = 1.0f;
        return v;
    }

    /** Reads a look; missing fields keep the values from {@code base}. */
    static float[] parse(JsonObject o, float[] base) {
        float[] v = base.clone();
        num(o, "exposure", v, EXPOSURE);
        num(o, "contrast", v, CONTRAST);
        num(o, "saturation", v, SATURATION);
        num(o, "warmth", v, WARMTH);
        rgb(o, "shadowTint", v, SHADOW_TINT);
        rgb(o, "highlightTint", v, HIGHLIGHT_TINT);
        num(o, "splitTone", v, SPLIT_TONE);
        num(o, "vignette", v, VIGNETTE);
        num(o, "bloom", v, BLOOM);
        num(o, "bloomThreshold", v, BLOOM_THRESHOLD);
        num(o, "skyBloom", v, SKY_BLOOM);
        num(o, "shafts", v, SHAFTS);
        num(o, "haze", v, HAZE);
        num(o, "hazeStart", v, HAZE_START);
        rgb(o, "hazeTint", v, HAZE_TINT);
        num(o, "hazeTintMix", v, HAZE_TINT_MIX);
        num(o, "hazeDesaturate", v, HAZE_DESATURATE);
        num(o, "sunsetWarmth", v, SUNSET_WARMTH);
        num(o, "sunGlow", v, SUN_GLOW);
        num(o, "light", v, LIGHT);
        num(o, "shade", v, SHADE);
        rgb(o, "shadeTint", v, SHADE_TINT);
        num(o, "keyWarmth", v, KEY_WARMTH);
        num(o, "ao", v, AO);
        num(o, "filmic", v, FILMIC);
        num(o, "mist", v, MIST);
        num(o, "mistHeight", v, MIST_HEIGHT);
        num(o, "fogGlow", v, FOG_GLOW);
        num(o, "skyGlow", v, SKY_GLOW);
        num(o, "water", v, WATER);
        num(o, "heat", v, HEAT);
        num(o, "frost", v, FROST);
        num(o, "glow", v, GLOW);
        num(o, "clouds", v, CLOUDS);
        return v;
    }

    private static void num(JsonObject o, String key, float[] v, int i) {
        JsonElement e = o.get(key);
        if (e != null) v[i] = e.getAsFloat();
    }

    private static void rgb(JsonObject o, String key, float[] v, int i) {
        JsonElement e = o.get(key);
        if (e == null) return;
        JsonArray a = e.getAsJsonArray();
        for (int k = 0; k < 3; k++) v[i + k] = a.get(k).getAsFloat();
    }
}
