package dev.awaken.lens;

/**
 * The Air's colours on the CPU (SPEC §22 "The game's fog under the Air"). The model is lens_air.glsl; this is its
 * {@code airSky} at the horizon, which is what {@code airFog} gives for land seen level or from above. Keep the two in
 * step: a number changed there changes here. Pure maths, no client classes, so a test can hold it to the shader's
 * known points.
 */
public final class LensAir {
    private LensAir() {}

    private static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * The sky at the horizon toward a compass heading, into {@code out} (r, g, b): the colour the Air's far fog has for
     * land seen that way. {@code hx, hz}: the heading, a unit vector on the ground. {@code sunX..Z}: toward the sun
     * (unit; under the horizon by night). {@code rain}: 0..1.
     */
    public static void horizon(float hx, float hz, float sunX, float sunY, float sunZ, float rain, float[] out) {
        // airHour: day, dusk or dawn, night
        float day = smooth(-0.06f, 0.30f, sunY);
        float dusk = smooth(-0.32f, -0.06f, sunY) * (1f - smooth(0.10f, 0.50f, sunY));
        float night = 1f - smooth(-0.26f, -0.02f, sunY);
        // toward the sun round the horizon, 0 away from it to 1 at it
        float flat = (float) Math.sqrt(sunX * sunX + sunZ * sunZ);
        float side = flat < 1.0e-4f ? 0.5f : 0.5f + 0.5f * (hx * sunX + hz * sunZ) / flat;
        float s2 = side * side;
        // the horizon's colour: nightH to dayH by the day, then toward duskH (violet away from the sun, gold at it)
        float r = mix(mix(0.050f, 0.83f, day), mix(0.60f, 1.0f, s2), dusk * 0.9f);
        float g = mix(mix(0.076f, 0.86f, day), mix(0.50f, 0.64f, s2), dusk * 0.9f);
        float b = mix(mix(0.155f, 0.98f, day), mix(0.80f, 0.46f, s2), dusk * 0.9f);
        // the sun's glow in the air
        float toSun = hx * sunX + hz * sunZ, mu = Math.max(toSun, 0f);
        float glow = ((float) Math.pow(mu, 5.0) * 0.10f + (float) Math.pow(mu, 36.0) * 0.20f) * Math.max(day, dusk) * (1f - night);
        r += glow * (1f - r);
        g += mix(0.93f, 0.56f, dusk) * glow * (1f - g);
        b += mix(0.80f, 0.32f, dusk) * glow * (1f - b);
        // the moon's side of the night sky is a little lighter
        float moon = (float) Math.pow(Math.max(-toSun, 0f), 6.0) * night;
        r += 0.020f * moon;
        g += 0.030f * moon;
        b += 0.055f * moon;
        // rain and storm: toward a cool grey as bright as the sky was
        float grey = (r * 0.299f + g * 0.587f + b * 0.114f) * 0.82f, k = rain * 0.85f;
        out[0] = mix(r, grey * 0.90f, k);
        out[1] = mix(g, grey * 0.95f, k);
        out[2] = mix(b, grey * 1.02f, k);
    }
}
