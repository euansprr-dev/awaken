// AWAKEN Lens 2's clouds (SPEC §22, L4): a layer of cumulus at the game's own cloud height, from one tileable noise
// texture (tools/art/specs/lens_clouds.py: r coverage, g shape, b billows, a wisps) sampled at scales that divide its
// 30720-block repeat. Needs CloudNoiseSampler and lens_frame.glsl (frame texels 18-19) before it.

const float CLOUD_THICK = 84.0;

// smooth 3D value noise (0..1) for the billows: real depth, where flat noise slid with height drew vertical streaks
float cloudHash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float cloudNoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(cloudHash(i), cloudHash(i + vec3(1, 0, 0)), f.x), mix(cloudHash(i + vec3(0, 1, 0)), cloudHash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(cloudHash(i + vec3(0, 0, 1)), cloudHash(i + vec3(1, 0, 1)), f.x), mix(cloudHash(i + vec3(0, 1, 1)), cloudHash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// x layer base (0: none), y cover, z/w the camera's x and z plus the wind (modulo 30720)
vec4 cloudParams() { return F(18); }

// cloud density at a camera-relative point (0..1); detail 0 skips the billows (light and shadow look-ups), 1 one octave
// of them (Cinematic), 2 two (Ultra)
float cloudDensity(vec3 rel, vec4 cp, float camY, int detail) {
    float base = cp.x - 6.0;
    float h = (rel.y + camY - base) / CLOUD_THICK;
    if (h <= 0.0 || h >= 1.0) return 0.0;
    vec2 xz = rel.xz + cp.zw;
    // the cover slides a little with height (two noise channels, opposite ways), so a cloud's sides lean and break up
    // instead of standing as straight walls
    // (not for the light-step and shadow look-ups, where it changes nothing you can see)
    if (detail > 0) {
        vec2 lean = vec2(texture(CloudNoiseSampler, fract(xz / 700.0 + h * 0.31)).a, texture(CloudNoiseSampler, fract(xz / 700.0 - h * 0.27 + 0.5)).a) - 0.5;
        xz += lean * 90.0 * h;
    }
    float cov = texture(CloudNoiseSampler, fract(xz / 3072.0)).r * 0.7 + texture(CloudNoiseSampler, fract(xz / 960.0 + 0.37)).g * 0.3;
    // cover 0.45 leaves most of the sky open between distinct clouds; toward 0.95 it closes into an overcast deck. A
    // cloud needs denser cover the higher it climbs, so each narrows upward into a dome (not a wall) and only the
    // thickest rise tall
    float dome = h * h;
    float c = clamp((cov - (1.0 - cp.y * 0.72) - 0.16 * dome) / 0.1, 0.0, 1.0);
    float shape = smoothstep(0.0, 0.08, h) * (1.0 - smoothstep(0.85, 1.0, h));
    float d = c * shape;
    if (detail == 0 || d <= 0.0) return d;
    // deep inside a cloud the billows change nothing visible
    if (d > 0.9) return d - 0.2;
    // billows (two octaves of 3D noise, in world space, so they hold still as the camera moves) eat into the edges
    vec3 wp = vec3(xz.x, rel.y + camY, xz.y);
    float b = detail > 1 ? cloudNoise3(wp / 46.0) * 0.65 + cloudNoise3(wp / 17.0) * 0.35 : cloudNoise3(wp / 38.0);
    return clamp(d - (1.0 - b) * 0.55 * (1.0 - h * 0.35), 0.0, 1.0);
}

// how much sunlight the clouds let down to a camera-relative point (the light ray through the layer's middle)
float cloudShadow(vec3 rel, vec3 l, vec4 cp, float camY) {
    if (cp.x <= 0.0 || l.y < 0.03) return 1.0;
    float t = (cp.x - 6.0 + CLOUD_THICK * 0.35 - (rel.y + camY)) / l.y;
    if (t <= 0.0) return 1.0;
    vec3 q = rel + l * t;
    q.y = cp.x - 6.0 + CLOUD_THICK * 0.35 - camY;
    return mix(1.0, 0.28, cloudDensity(q, cp, camY, 0));
}
