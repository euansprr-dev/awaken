// AWAKEN Lens 2's clouds (SPEC §22, L4): a layer of cumulus at the game's own cloud height, from one tileable noise
// texture (tools/art/specs/lens_clouds.py: r coverage, g shape, b billows, a wisps) sampled at scales that divide its
// 30720-block repeat. Needs CloudNoiseSampler and lens_frame.glsl (frame texels 18-19) before it.

const float CLOUD_THICK = 84.0;

// x layer base (0: none), y cover, z/w the camera's x and z plus the wind (modulo 30720)
vec4 cloudParams() { return F(18); }

// cloud density at a camera-relative point (0..1); coarse skips the billows (for light and shadow look-ups)
float cloudDensity(vec3 rel, vec4 cp, float camY, bool coarse) {
    float base = cp.x - 6.0;
    float h = (rel.y + camY - base) / CLOUD_THICK;
    if (h <= 0.0 || h >= 1.0) return 0.0;
    vec2 xz = rel.xz + cp.zw;
    float cov = texture(CloudNoiseSampler, fract(xz / 3072.0)).r * 0.7 + texture(CloudNoiseSampler, fract(xz / 960.0 + 0.37)).g * 0.3;
    // cover 0.45 leaves most of the sky open between distinct clouds; toward 0.95 it closes into an overcast deck
    float c = clamp((cov - (1.0 - cp.y * 0.72)) / 0.1, 0.0, 1.0);
    // flat-ish bases, rounded tops that rise where the cloud is thick
    float shape = smoothstep(0.0, 0.1, h) * (1.0 - smoothstep(0.25 + 0.7 * c, 0.3 + 0.7 * c + 0.05, h));
    float d = c * shape;
    if (coarse || d <= 0.0) return d;
    // billows and wisps eat into the edges (shifted with height: a little depth from flat noise)
    float b = texture(CloudNoiseSampler, fract(xz / 256.0 + rel.y / 512.0)).b;
    float w = texture(CloudNoiseSampler, fract(xz / 120.0 - rel.y / 300.0)).a;
    return clamp(d - (1.0 - b) * 0.45 * (1.0 - h * 0.4) - (1.0 - w) * 0.12, 0.0, 1.0);
}

// how much sunlight the clouds let down to a camera-relative point (the light ray through the layer's middle)
float cloudShadow(vec3 rel, vec3 l, vec4 cp, float camY) {
    if (cp.x <= 0.0 || l.y < 0.03) return 1.0;
    float t = (cp.x - 6.0 + CLOUD_THICK * 0.35 - (rel.y + camY)) / l.y;
    if (t <= 0.0) return 1.0;
    vec3 q = rel + l * t;
    q.y = cp.x - 6.0 + CLOUD_THICK * 0.35 - camY;
    return mix(1.0, 0.28, cloudDensity(q, cp, camY, true));
}
