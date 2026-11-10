package dev.awaken.gates.realms.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The game's own light: a port of Minecraft 26.3's {@code shaders/core/lightmap.fsh}, fed the way
 * {@code LightmapRenderStateExtractor} feeds it, with no flicker, night vision, darkness or boss fog. A block and a sky
 * level in, the colour the game multiplies a texture by out (SPEC §50.8). Pure numbers, so the preview and a test can
 * both use it.
 */
public final class TrueLight {
    private TrueLight() {}

    /** {@code BlockFactor} with the torch flicker at rest. */
    public static final float BLOCK_FACTOR = 1.4F;
    /** The brightness option the CI walks run at. */
    public static final float DEFAULT_GAMMA = 0.5F;
    /** {@code EnvironmentAttributes}' defaults: black ambient, white sky light at full, the warm torch tint. */
    public static final int DEFAULT_AMBIENT = 0x000000, DEFAULT_SKY_COLOUR = 0xFFFFFF, DEFAULT_BLOCK_TINT = 0xFFD88C;

    /** What a place does to light: colours as 0xRRGGBB, the sky factor 0 to 1, gamma the brightness option 0 to 1. */
    public record LightEnv(int ambient, int skyColour, float skyFactor, int blockTint, float gamma) {
        public static final LightEnv DEFAULT = new LightEnv(DEFAULT_AMBIENT, DEFAULT_SKY_COLOUR, 1.0F, DEFAULT_BLOCK_TINT, DEFAULT_GAMMA);
    }

    /**
     * A land's light, from its first biome's {@code attributes}, then the realms dimension's, then the game's defaults.
     * Either object may be null. Gamma is 0.5 unless {@code AWAKEN_PREVIEW_GAMMA} says otherwise.
     */
    public static LightEnv env(JsonObject biome, JsonObject dimension) {
        float gamma = DEFAULT_GAMMA;
        String g = System.getenv("AWAKEN_PREVIEW_GAMMA");
        if (g != null && !g.isBlank()) gamma = Math.max(0, Float.parseFloat(g.trim()));
        return new LightEnv(
                colour(biome, dimension, "minecraft:visual/ambient_light_color", DEFAULT_AMBIENT),
                colour(biome, dimension, "minecraft:visual/sky_light_color", DEFAULT_SKY_COLOUR),
                Math.min(1, Math.max(0, number(biome, dimension, "minecraft:visual/sky_light_factor", 1.0F))),
                colour(biome, dimension, "minecraft:visual/block_light_tint", DEFAULT_BLOCK_TINT),
                gamma);
    }

    /** The multiplier for a block level and a sky level, each 0 to 15: red, green, blue, each 0 to 1. */
    public static float[] rgb(int block, int sky, LightEnv env) {
        float blockLevel = Math.min(15, Math.max(0, block)) / 15.0F, skyLevel = Math.min(15, Math.max(0, sky)) / 15.0F;
        float blockBrightness = brightness(blockLevel) * BLOCK_FACTOR;
        float skyBrightness = brightness(skyLevel) * env.skyFactor;
        // the tint gives way to white at both ends of the scale: 0.9 * (2 * level - 1)^2
        float white = 0.9F * (2 * blockLevel - 1) * (2 * blockLevel - 1);
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) {
            float tint = channel(env.blockTint, i);
            float v = channel(env.ambient, i) + channel(env.skyColour, i) * skyBrightness + (tint + (1 - tint) * white) * blockBrightness;
            c[i] = Math.min(1, Math.max(0, v));
        }
        float max = Math.max(c[0], Math.max(c[1], c[2]));
        if (max <= 0) return c; // the shader divides by zero here; black stays black
        float inv = 1 - max, lifted = (1 - inv * inv * inv * inv) / max;
        for (int i = 0; i < 3; i++) c[i] += (c[i] * lifted - c[i]) * env.gamma;
        return c;
    }

    /** Every pair at once, indexed {@code block * 16 + sky}, so shading a pixel is one lookup. */
    public static float[][] table(LightEnv env) {
        float[][] t = new float[256][];
        for (int b = 0; b < 16; b++) for (int s = 0; s < 16; s++) t[b * 16 + s] = rgb(b, s, env);
        return t;
    }

    /**
     * Two values worked by hand from the shader, to a hundredth: a torch seven blocks off in the dark, and the Hour Crypt's
     * ambient with no light at all. Empty when both hold; the test realm_truelight_matches_the_shader asks.
     */
    public static String problem() {
        String a = off("block 7, sky 0, black ambient", rgb(7, 0, LightEnv.DEFAULT), 0.469F, 0.397F, 0.258F);
        if (!a.isEmpty()) return a;
        return off("block 0, sky 0, ambient #252e40", rgb(0, 0, new LightEnv(0x252E40, DEFAULT_SKY_COLOUR, 1.0F, DEFAULT_BLOCK_TINT, DEFAULT_GAMMA)), 0.271F, 0.337F, 0.468F);
    }

    private static String off(String what, float[] got, float r, float g, float b) {
        if (Math.abs(got[0] - r) <= 0.01F && Math.abs(got[1] - g) <= 0.01F && Math.abs(got[2] - b) <= 0.01F) return "";
        return String.format(java.util.Locale.ROOT, "%s gives (%.3f, %.3f, %.3f), the shader gives (%.3f, %.3f, %.3f)", what, got[0], got[1], got[2], r, g, b);
    }

    private static float brightness(float level) {
        return level / (4.0F - 3.0F * level);
    }

    private static float channel(int rgb, int i) {
        return (rgb >> (16 - 8 * i) & 255) / 255.0F;
    }

    /** An attribute's plain value: the first of the two objects that has it written as a value and not as a modifier. */
    private static JsonElement plain(JsonObject first, JsonObject second, String key) {
        for (JsonObject o : new JsonObject[]{first, second}) {
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key);
        }
        return null;
    }

    private static int colour(JsonObject first, JsonObject second, String key, int fallback) {
        JsonElement e = plain(first, second, key);
        if (e == null) return fallback;
        String v = e.getAsString();
        return (v.startsWith("#") ? Integer.parseInt(v.substring(1), 16) : e.getAsInt()) & 0xFFFFFF;
    }

    private static float number(JsonObject first, JsonObject second, String key, float fallback) {
        JsonElement e = plain(first, second, key);
        return e == null ? fallback : e.getAsFloat();
    }
}
