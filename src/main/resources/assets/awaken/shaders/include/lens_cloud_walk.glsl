// AWAKEN Lens (Cinematic and Ultra), the Air's block clouds near the camera (SPEC §22 "Steady Light"): one ray's walk of
// the twelve-block grid as far as CLOUD_NEAR blocks, to the pixel and afresh every frame. It runs inside the composite
// (and at half size in the bloom prefilter, for the glow and the rays' mask), where it was a frame-size pass of its own:
// on a tile-based GPU a pass that size costs a third of a millisecond before it draws anything.
// What keeps it cheap: ground and anything nearer than the layer is out at once, a ray crosses four or five cells on
// average, a cell is one read, the clouds are solid so the walk ends at the first one, and their colours are worked out
// only for a ray that met one.
// Needs lens_frame.glsl, lens_air.glsl, lens_cloud_cells.glsl and lens_storm_blocks.glsl (stormClearing) before it. v the ray (unit), scene the distance to what
// the pixel shows (1e9 for sky). Returns rgb the light the clouds send this way (premultiplied), a how much of what is
// behind still shows.
vec4 P(int i);  // the including pass's parameter read (texel 25 r: Lite)

float cloudNearHg(float mu, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * mu, 1.5)); }

// drop: how far under the camera the walk starts (0 for the view itself). Water mirrors the sky from the camera's
// image under its surface, twice the eye's height over the water down: the reflected ray is that eye's own line of
// sight, so the clouds in the water are the clouds in the sky, block for block.
vec4 cloudNearFrom(vec3 v, float scene, float drop) {
    vec4 cp = cloudParams(), f12 = F(12), f14 = F(14), f16 = F(16);
    if (!cloudBlocks() || cp.x <= 0.0 || f16.w <= 0.0) return vec4(0.0, 0.0, 0.0, 1.0);
    vec3 l = f14.xyz;
    float camY = f12.y - drop;
    // (Lite, SPEC §22 "Lite", has no far clouds' pass behind this walk, so here it goes as far as that pass does: only
    // the low band of sky between the two reaches walks farther, some twenty cells at most, and a block twelve wide is
    // still a dozen pixels at a thousand blocks)
    // (and a mirrored walk goes that far on any tier: the far clouds' buffer is the camera's view, not the water's)
    bool lite = P(25).r > 0.5 || drop > 0.0;
    float reach = lite ? CLOUD_NEAR + 600.0 : CLOUD_NEAR;
    // the layer on this ray, as far as the near walk goes
    float oy = camY - (cp.x - cloudUnder()), thick = cloudThick(), t0 = 0.0, t1 = 0.0;
    if (abs(v.y) > 1.0e-4) {
        float a = -oy / v.y, b = (thick - oy) / v.y;
        t0 = max(min(a, b), 0.0);
        t1 = max(a, b);
    } else if (oy > 0.0 && oy < thick) {
        t1 = reach;
    }
    t1 = min(min(t1, scene), reach);
    if (t1 <= t0) return vec4(0.0, 0.0, 0.0, 1.0);

    // the walk (lens_clouds.fsh's blockPiece, with the light kept as two sums until a cloud is met)
    const float sigma = 0.95;
    vec2 pxz = cp.zw + v.xz * t0;
    vec2 cell = floor(pxz / CLOUD_CELL);
    vec2 sgn = vec2(v.x < 0.0 ? -1.0 : 1.0, v.z < 0.0 ? -1.0 : 1.0);
    vec2 inv = 1.0 / max(abs(v.xz), vec2(1.0e-5));
    vec2 next = t0 + ((cell + max(sgn, 0.0)) * CLOUD_CELL - pxz) * sgn * inv;
    float t = t0, T = 1.0, tHit = -1.0, sunSum = 0.0, skySum = 0.0;
#ifdef LENS_CALM
    int axis = 2;
    const int storms = 0;
#else
    int axis = 2, storms = stormCount();
#endif
    bool inside = false;
    vec3 nIn = vec3(0.0, 1.0, 0.0);
    for (int i = 0; i < 80; i++) {
        if (t >= t1 || T < 0.02) break;
        float tExit = min(min(next.x, next.y), t1);
        float val = cloudCell(cell, cp.y);
        // (a storm clears the sky about it, as the far clouds do in lens_clouds.fsh: no white blocks through its tower)
        if (val > 0.0 && storms > 0 && stormClearing((cell + 0.5) * CLOUD_CELL - cp.zw) < 0.5) val = 0.0;
        if (val > 0.0) {
            float top = CLOUD_TIER * (1.0 + floor(min(val / 0.055, 2.0)));
            float ta = t, tb = tExit;
            bool capped = false;
            if (abs(v.y) > 1.0e-5) {
                float y0 = -oy / v.y, y1 = (top - oy) / v.y;
                float lo = min(y0, y1), hi = max(y0, y1);
                if (lo > ta) {
                    ta = lo;
                    capped = true;
                }
                tb = min(tb, hi);
            } else if (oy <= 0.0 || oy >= top) {
                tb = ta;
            }
            if (tb > ta) {
                vec3 n = capped || axis == 2 ? vec3(0.0, v.y > 0.0 ? -1.0 : 1.0, 0.0)
                        : (axis == 0 ? vec3(-sgn.x, 0.0, 0.0) : vec3(0.0, 0.0, -sgn.y));
                if (inside && !capped) n = nIn;
                nIn = n;
                vec3 w = v * (ta + min((tb - ta) * 0.5, 2.5));
                float od = 0.0, s = 3.0, lt = 0.0;
                for (int k = 0; k < 3; k++) {
                    lt += s;
                    od += cloudBlock(w + l * lt, cp, camY) * s;
                    s *= 2.0;
                }
                float h = clamp((w.y + oy) / thick, 0.0, 1.0);
                float beer = max(exp(-od * 0.16), 0.30 * exp(-od * 0.03));
                float face = 0.75 + 0.25 * max(dot(n, l), 0.0);
                float skyOn = n.y < -0.5 ? 0.86 : (n.y > 0.5 ? 1.0 : 0.91 + 0.07 * h);
                float a = 1.0 - exp(-sigma * (tb - ta));
                sunSum += T * a * beer * face;
                skySum += T * a * skyOn;
                if (tHit < 0.0 && a > 0.05) tHit = ta;
                T *= 1.0 - a;
                inside = tb >= tExit - 1.0e-3;
            } else {
                inside = false;
            }
        } else {
            inside = false;
        }
        if (next.x < next.y) {
            cell.x += sgn.x;
            next.x += CLOUD_CELL * inv.x;
            axis = 0;
        } else {
            cell.y += sgn.y;
            next.y += CLOUD_CELL * inv.y;
            axis = 1;
        }
        t = tExit;
    }
    if (T > 0.998) return vec4(0.0, 0.0, 0.0, 1.0);

    // their colours, for a pixel that met one (as lens_clouds.fsh has them for the far ones)
    vec4 f22 = F(22);
    vec3 hr = airHour(f22.y);
    vec3 over = airSky(vec3(0.0, 1.0, 0.0), f22.xyz, f16.z), edge = airSky(normalize(vec3(v.x, 0.12, v.z)), f22.xyz, f16.z);
    vec3 ambient = mix(mix(over, edge, 0.55), vec3(1.0), 0.52 * hr.x) * (0.80 + 0.14 * hr.x + 0.25 * hr.y) + vec3(0.020, 0.028, 0.050) * hr.z;
    vec3 lit = mix(vec3(1.0, 0.97, 0.92), vec3(1.0, 0.60, 0.44), hr.y * (1.0 - 0.6 * hr.x));
    vec3 sunCol = (f16.y > 0.5 ? vec3(0.50, 0.60, 0.90) * 0.10 : lit * (0.40 + 0.3 * hr.y)) * f14.w;
    float phase = 0.85 + 0.9 * cloudNearHg(dot(v, l), 0.6);
    vec3 L = sunCol * (phase * sunSum) + ambient * skySum;
    // into the haze with distance, as the far ones go
    float at = tHit > 0.0 ? tHit : t0;
    float far = smoothstep(260.0, 1500.0, at), gone = smoothstep(520.0, 1500.0, at) * 0.6;
    // (and in Lite the last of them go before the walk's end, where the layer would stop at a line across the sky)
    if (lite) gone = max(gone, smoothstep(reach - 280.0, reach - 20.0, at));
    L = mix(L, airFog(v, f22.xyz, f16.z) * (1.0 - T), far * 0.9) * (1.0 - gone);
    T = 1.0 - (1.0 - T) * (1.0 - gone);
    return vec4(L, T);
}

vec4 cloudNear(vec3 v, float scene) { return cloudNearFrom(v, scene, 0.0); }
