// AWAKEN Lens 2: surface normals from the world's depth. Needs DepthSampler and lens_frame.glsl before it.

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
