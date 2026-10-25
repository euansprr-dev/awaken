// AWAKEN Lens 2 (SPEC §22): the frame's float values (dev.awaken.lens.client.LensFrame, 24x1 RGBA32F) and the terrain's
// heights (LensHeightfield, 1024x1024 RG16F ring addressed by world block modulo 1024; r the top of everything that
// blocks motion, leaves included, g without leaves; mips 1-4 the max of their 2x2). Needs FrameSampler, HeightSampler
// and DepthSampler (the world's depth, full size) declared before it.
//   0-3  inverse view-projection (NDC -> camera-relative world), with the frame's own bob and nausea
//   4-7  view-projection          8-11 last frame's view-projection (relative to last frame's camera)
//   12   camera in the heightfield ring: x, z modulo 1024, y absolute; w frame counter
//   13   camera moved since last frame (now - then); w 1 when last frame's history is usable
//   14   toward the light (sun, or the moon by night); w how strongly it lights the world (0: no sun model here)
//   15   heightfield: x its size (0: none), y the highest surface in it, z blocks from the camera it can be trusted
//   16   x 1 for zero-to-one depth, y 1 when the light is the moon, z rain, w the near plane

vec4 F(int i) { return texelFetch(FrameSampler, ivec2(i, 0), 0); }

mat4 frameMat(int i) { return mat4(F(i), F(i + 1), F(i + 2), F(i + 3)); }

// camera-relative world position of a depth sample (reversed-Z: the sky keeps the cleared 0)
vec3 unproject(mat4 inv, vec2 uv, float depth, bool zeroToOne) {
    vec4 w = inv * vec4(uv * 2.0 - 1.0, zeroToOne ? depth : depth * 2.0 - 1.0, 1.0);
    return w.xyz / w.w;
}

// a camera-relative point on screen: xy uv, z the depth value it would store
vec3 project(mat4 vp, vec3 p, bool zeroToOne) {
    vec4 c = vp * vec4(p, 1.0);
    vec3 n = c.xyz / c.w;
    return vec3(n.xy * 0.5 + 0.5, zeroToOne ? n.z : n.z * 0.5 + 0.5);
}

// distance along the view axis for a stored depth (26.3's reversed-Z projection; near 0.05)
float linearDepth(float depth, float zFar, bool zeroToOne) {
    const float zNear = 0.05;
    if (zeroToOne) return zNear * zFar / (depth * (zFar - zNear) + zNear);
    float ndc = depth * 2.0 - 1.0;
    return 2.0 * zFar * zNear / (ndc * (zFar - zNear) + (zFar + zNear));
}

// the heightfield texel holding world column c at mip level lvl (ring addressing; & works on negatives)
vec2 heightAt(ivec2 c, int lvl) {
    return texelFetch(HeightSampler, (c & 1023) >> lvl, lvl).rg;
}

// 16-bit log distance, 0 for sky, as two bytes (the light buffer's b and a)
vec2 packDistance(float d) {
    float v = d <= 0.0 ? 0.0 : clamp(log2(d + 1.0) / 13.0, 1.0 / 65535.0, 1.0);
    float q = floor(v * 65535.0 + 0.5);
    return vec2(floor(q / 256.0), mod(q, 256.0)) / 255.0;
}

float unpackDistanceCode(vec2 ba) {
    return (floor(ba.x * 255.0 + 0.5) * 256.0 + floor(ba.y * 255.0 + 0.5)) / 65535.0;
}

float distanceCode(float d) { return d <= 0.0 ? 0.0 : clamp(log2(d + 1.0) / 13.0, 1.0 / 65535.0, 1.0); }

// interleaved gradient noise, stepped every frame (temporal accumulation averages it away)
float ign(vec2 p, float frame) {
    p += 5.588238 * mod(frame, 64.0);
    return fract(52.9829189 * fract(0.06711056 * p.x + 0.00583715 * p.y));
}

// the surface normal from depth: of the two neighbours on each axis, the one that continues the centre's plane (so a
// silhouette doesn't bend it); block faces are axis-aligned, so a normal that's nearly one snaps to it
vec3 normalAt(ivec2 px, vec3 p, float d0, mat4 inv, bool zeroToOne, vec2 size) {
    ivec2 last = ivec2(size) - 1;
    vec2 texel = 1.0 / size, uv = (vec2(px) + 0.5) * texel;
    float dr = texelFetch(DepthSampler, min(px + ivec2(1, 0), last), 0).r;
    float dl = texelFetch(DepthSampler, max(px - ivec2(1, 0), ivec2(0)), 0).r;
    float du = texelFetch(DepthSampler, min(px + ivec2(0, 1), last), 0).r;
    float dd = texelFetch(DepthSampler, max(px - ivec2(0, 1), ivec2(0)), 0).r;
    bool right = abs(dr - d0) < abs(d0 - dl), up = abs(du - d0) < abs(d0 - dd);
    float sx = right ? dr : dl, sy = up ? du : dd;
    if (sx < 1.0e-7 || sy < 1.0e-7) return -normalize(p);
    vec3 dx = right ? unproject(inv, uv + vec2(texel.x, 0.0), dr, zeroToOne) - p : p - unproject(inv, uv - vec2(texel.x, 0.0), dl, zeroToOne);
    vec3 dy = up ? unproject(inv, uv + vec2(0.0, texel.y), du, zeroToOne) - p : p - unproject(inv, uv - vec2(0.0, texel.y), dd, zeroToOne);
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, p) > 0.0) n = -n;
    vec3 a = abs(n);
    float m = max(a.x, max(a.y, a.z));
    if (m > 0.9) n = step(vec3(m), a) * sign(n);
    return n;
}
