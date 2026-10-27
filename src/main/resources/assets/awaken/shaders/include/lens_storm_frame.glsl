// AWAKEN Lens 2's storm cells (SPEC §21.6 "The Rolling Storm", §22 "Storm cells"): where they are this frame and the
// bounds of their volume, for passes that only need to know whether a ray meets one (lens_storm.glsl has their shape).
// Frame texels 42-47 (living.client.StormSky), F(20).y their count:
//   42 + 3k  centre x, z (camera-relative, on its base), base y (absolute), strength 0..1
//   43 + 3k  downwind x, z (unit), the ground under it (absolute y), noise time (s)
//   44 + 3k  the latest flash: camera-relative x, y, z and its strength (0: none)
// Needs lens_frame.glsl before it.

const float STORM_H = 300.0;      // the anvil's top above the base

int stormCount() { return int(F(20).y + 0.5); }

vec4 stormA(int k) { return F(42 + k * 3); }
vec4 stormB(int k) { return F(43 + k * 3); }
vec4 stormC(int k) { return F(44 + k * 3); }

// how far along a camera-relative ray v (from the camera) its path enters the band of y from y0 to y1 inside a vertical
// cylinder (centre c on the xz plane, radius r): the interval [t0, t1], empty when t1 <= t0
vec2 stormBand(vec3 v, vec2 c, float r, float y0, float y1, float camY) {
    float a = dot(v.xz, v.xz), b = -2.0 * dot(c, v.xz), cc = dot(c, c) - r * r;
    float disc = b * b - 4.0 * a * cc;
    if (disc <= 0.0 || a < 1.0e-8) {
        if (cc > 0.0) return vec2(0.0);   // straight up or down outside the cylinder
        disc = 0.0;
    }
    float sq = sqrt(disc);
    float t0 = a < 1.0e-8 ? 0.0 : (-b - sq) / (2.0 * a), t1 = a < 1.0e-8 ? 1.0e9 : (-b + sq) / (2.0 * a);
    if (abs(v.y) > 1.0e-5) {
        float ta = (y0 - camY) / v.y, tb = (y1 - camY) / v.y;
        t0 = max(t0, min(ta, tb));
        t1 = min(t1, max(ta, tb));
    } else if (camY < y0 || camY > y1) {
        return vec2(0.0);
    }
    return vec2(max(t0, 0.0), t1);
}

// where a camera-relative ray v first enters storm k's whole volume (its bounding cylinder), or 1e9
float stormEntry(vec3 v, int k, float camY) {
    vec4 a = stormA(k), b = stormB(k);
    vec2 i = stormBand(v, a.xy + b.xy * 60.0, 320.0, b.z - 4.0, a.z + STORM_H + 48.0, camY);
    return i.y > i.x ? i.x : 1.0e9;
}

// a good guess at where a camera-relative ray v first meets storm k's cloud, 1e9 when it meets none: the tight
// cylinders its parts fill (body, shelf, anvil plate, rain core), where stormEntry's bounding volume lies hundreds of
// blocks in front of the surface. For reprojecting last frame's clouds: a point at the wrong depth slides on screen
// when the camera moves, so a storm looked up at its bounds' entry swam as you walked.
float stormGuess(vec3 v, int k, float camY) {
    vec4 a = stormA(k), b = stormB(k);
    float base = a.z, best = 1.0e9;
    vec2 i = stormBand(v, a.xy + b.xy * 12.0, 78.0, base, base + STORM_H - 72.0, camY);
    if (i.y > i.x) best = min(best, i.x);
    i = stormBand(v, a.xy + b.xy * 8.0, 108.0, base - 4.0, base + 30.0, camY);
    if (i.y > i.x) best = min(best, i.x);
    i = stormBand(v, a.xy + b.xy * 90.0, 170.0, base + STORM_H - 40.0, base + STORM_H + 8.0, camY);
    if (i.y > i.x) best = min(best, i.x);
    i = stormBand(v, a.xy + b.xy * 12.0, 50.0, b.z - 4.0, base - 6.0, camY);
    if (i.y > i.x) best = min(best, i.x);
    return best;
}
