// AWAKEN Lens, the Air (SPEC §22 "Steady Light"): Lens's own sky, and the colour its far fog and its clouds take from
// it. One model for all three, so the land fades into the very colour the sky has behind it and a cloud's shade is the
// sky's light. Needs lens_frame.glsl (F) before it.
// Frame texel 22: xyz toward the sun (under the horizon by night), w the air's strength (0: off, the look without it).
// Frame texel 23: rgb the game's own sky colour this frame (what it drew behind the sun, moon and stars), a its stars'
// brightness.

float airOn() { return F(22).w; }

// the hour by the sun's height: x day, y dusk or dawn (it peaks as the sun crosses the horizon), z night
vec3 airHour(float sy) {
    float day = smoothstep(-0.06, 0.30, sy);
    float dusk = smoothstep(-0.32, -0.06, sy) * (1.0 - smoothstep(0.10, 0.50, sy));
    float night = 1.0 - smoothstep(-0.26, -0.02, sy);
    return vec3(day, dusk, night);
}

// the sky in a direction, without the sun's disc, the stars or the clouds: a deep violet-blue overhead paling to the
// horizon by day; rose and gold toward the sun and violet away from it at dusk and dawn; near-black blue by night
vec3 airSky(vec3 dir, vec3 sun, float rain) {
    vec3 hr = airHour(sun.y);
    float e = clamp(dir.y, 0.0, 1.0);
    // toward the sun round the horizon (by compass only), 0 away from it to 1 at it
    float side = 0.5 + 0.5 * dot(normalize(dir.xz + vec2(1.0e-5)), normalize(sun.xz + vec2(1.0e-5)));
    vec3 dayZ = vec3(0.33, 0.41, 0.86), dayM = vec3(0.53, 0.61, 0.95), dayH = vec3(0.83, 0.86, 0.98);
    vec3 duskZ = vec3(0.17, 0.16, 0.44), duskM = vec3(0.47, 0.34, 0.66);
    vec3 duskH = mix(vec3(0.60, 0.50, 0.80), vec3(1.0, 0.64, 0.46), side * side);
    vec3 nightZ = vec3(0.010, 0.016, 0.046), nightM = vec3(0.022, 0.036, 0.092), nightH = vec3(0.050, 0.076, 0.155);
    vec3 z = mix(nightZ, dayZ, hr.x), m = mix(nightM, dayM, hr.x), h = mix(nightH, dayH, hr.x);
    z = mix(z, duskZ, hr.y * 0.85);
    m = mix(m, mix(duskM, duskH, 0.35 * side), hr.y * 0.85);
    h = mix(h, duskH, hr.y * 0.9);
    float up = 1.0 - e;
    vec3 c = mix(z, m, smoothstep(0.0, 1.0, pow(up, 1.5)));
    c = mix(c, h, pow(up, 4.0));
    // the sun's glow in the air: wide and faint by day, low, wide and warm at dusk
    float mu = max(dot(dir, sun), 0.0);
    vec3 glowC = mix(vec3(1.0, 0.93, 0.80), vec3(1.0, 0.56, 0.32), hr.y);
    float glow = (pow(mu, 5.0) * 0.10 + pow(mu, 36.0) * 0.20) * max(hr.x, hr.y) * (1.0 - hr.z);
    c = c + glowC * glow * (1.0 - c);
    // the moon's side of the night sky is a little lighter
    c += vec3(0.020, 0.030, 0.055) * pow(max(dot(dir, -sun), 0.0), 6.0) * hr.z;
    // rain and storm: toward a cool grey as bright as the sky was
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    return mix(c, vec3(l) * vec3(0.90, 0.95, 1.02) * 0.82, rain * 0.85);
}

// the fog's colour for land seen in a direction: the sky that stands behind it there (the horizon's, for land seen
// from above), so far land fades into exactly what is drawn behind it
vec3 airFog(vec3 dir, vec3 sun, float rain) {
    return airSky(normalize(vec3(dir.x, max(dir.y, 0.0), dir.z) + vec3(1.0e-5, 0.0, 0.0)), sun, rain);
}

// Lens's own stars, larger and fewer than the game's, each with its own slow twinkle and a faint tint
float airHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 airStars(vec3 dir, float seconds) {
    if (dir.y < -0.02) return vec3(0.0);
    vec3 p = dir * 46.0, cell = floor(p);
    float h = airHash(cell);
    if (h < 0.80) return vec3(0.0);
    vec3 at = cell + 0.25 + 0.5 * vec3(airHash(cell + 7.1), airHash(cell + 13.7), airHash(cell + 29.3));
    float d = length(p - at);
    float size = 0.05 + 0.10 * airHash(cell + 3.3);
    float twinkle = 0.7 + 0.3 * sin(seconds * (0.6 + 2.2 * airHash(cell + 5.9)) + 6.2832 * h);
    float core = smoothstep(size, 0.0, d);
    vec3 tint = mix(vec3(0.75, 0.84, 1.0), vec3(1.0, 0.90, 0.78), airHash(cell + 17.0));
    return tint * core * core * twinkle * 1.6 * smoothstep(-0.02, 0.12, dir.y);
}
