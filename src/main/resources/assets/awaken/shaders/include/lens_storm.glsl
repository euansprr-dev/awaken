// AWAKEN Lens 2's storm cells (SPEC §21.6 "The Rolling Storm", §22 "Storm cells"): the Rolling Storm's cumulonimbus in
// Lens's cloud volume. A flat dark base, a broad tower of cauliflower turrets narrowing and leaning downwind, an
// overshooting dome, a flanking line of smaller towers stepping down upwind, a thin flat anvil spreading downwind, and
// rain shafts hanging from the base, slanting with the wind. Shapes are in blocks relative to the cell's centre on its
// base, carried with it. Needs CloudNoiseSampler, lens_frame.glsl, lens_clouds.glsl (cloudHash) and
// lens_storm_frame.glsl before it.

const float STORM_R = 95.0;       // the body's radius at its base
const float STORM_DOWN = 260.0;   // the anvil's reach downwind, upwind and across
const float STORM_UP = 60.0;
const float STORM_SIDE = 150.0;
const float STORM_ANVIL = 56.0;   // the anvil band: from this far under the top
const float STORM_RAIN = 58.0;    // the rain core's radius, 12 blocks downwind of the centre
const float STORM_SIGMA = 0.085;  // extinction per block of the body: defined turrets, a solid mass
const float STORM_RAIN_SIGMA = 0.016;

// cheap cellular noise: 1 - the distance to the nearest of the 8 jittered points around p (each within the middle half
// of its cell), so round puffs bulge with sharp creases between them: cauliflower
float stormPuffs(vec3 p) {
    vec3 q = p - 0.5, i = floor(q), f = q - i;
    float best = 9.0;
    for (int k = 0; k < 8; k++) {
        vec3 o = vec3(float(k & 1), float((k >> 1) & 1), float((k >> 2) & 1));
        float h = cloudHash(i + o);
        vec3 d = o + 0.25 + 0.5 * fract(vec3(h, h * 7.13, h * 51.7)) - f;
        best = min(best, dot(d, d));
    }
    return 1.0 - sqrt(best);
}

float stormBlob(float dist, float r, float puff) { return clamp((r * (0.66 + 0.5 * puff) - dist) / 9.0, 0.0, 1.0); }

// the flanking line: (along the wind, across it, radius, height of its top)
const vec4 STORM_FLANK0 = vec4(-118.0, 26.0, 48.0, 170.0);
const vec4 STORM_FLANK1 = vec4(-168.0, 48.0, 40.0, 118.0);
const vec4 STORM_FLANK2 = vec4(-210.0, 66.0, 32.0, 78.0);

float stormFlank(vec2 aw, float h, vec4 f, float puff, float base) {
    vec3 d = vec3(aw.x - f.x, aw.y - f.y, max(h - (f.w - f.z), 0.0));
    return stormBlob(length(d), f.z, puff) * base;
}

// the cell's density at q (relative to its centre on the base plane), w downwind; grow 0..1 how far it has built (it
// rises from its base as it builds and rains out from the top down); detail 0 (light steps and shadows) keeps the big
// forms and skips the puffs and the anvil's fibres
float stormCell(vec3 q, vec2 w, float time, float grow, int detail) {
    float h = q.y;
    float cut = mix(-10.0, STORM_H + 50.0, grow);
    if (h < -6.0 || h > min(STORM_H + 46.0, cut)) return 0.0;
    vec2 aw = vec2(dot(q.xz, w), dot(q.xz, vec2(-w.y, w.x)));   // along the wind, across it
    float hn = clamp(h / STORM_H, 0.0, 1.0);
    // the turrets: two octaves of puffs carried with the cell, rising slowly through it (the cloud boils)
    vec3 pq = q - vec3(0.0, time * 0.35, 0.0);
    float puff = detail > 0 ? stormPuffs(pq / 50.0) * 0.7 + stormPuffs(pq / 20.0 + 3.1) * 0.3 : 0.62;
    float base = smoothstep(-2.0, 3.0, h + 3.0 * (puff - 0.6));
    float d = 0.0;
    if (h < STORM_H - 28.0) {
        // the body: broad and flat-based, narrowing a little as it towers, leaning downwind; its axis wanders and its
        // girth swells and pinches with height (stacked thermals), and big lobes push out on some sides
        vec4 s = texture(CloudNoiseSampler, vec2(h / 900.0 + 0.13, 0.37));
        float lean = hn * hn * 35.0;
        vec2 b = vec2(aw.x - lean - 70.0 * (s.r - 0.5), aw.y - 70.0 * (s.g - 0.5));
        float lobes = 0.75 + 0.5 * texture(CloudNoiseSampler, fract(q.xz / 420.0 + vec2(h / 900.0, 0.0))).r;
        float r = STORM_R * (1.0 - 0.32 * pow(hn, 1.4)) * (0.8 + 0.4 * s.b) * lobes;
        d = stormBlob(length(b), r, puff) * base * (1.0 - smoothstep(STORM_H - 30.0, STORM_H, h));
        if (h < STORM_FLANK0.w + 8.0) {
            d = max(d, stormFlank(aw, h, STORM_FLANK0, puff, base));
            d = max(d, stormFlank(aw, h, STORM_FLANK1, puff, base));
            d = max(d, stormFlank(aw, h, STORM_FLANK2, puff, base));
        }
    }
    if (h > STORM_H - 60.0) {
        // the overshooting top: a dome over the updraft
        vec3 o = vec3(aw.x + 10.0, aw.y, max(h - STORM_H + 40.0, 0.0) * 1.3);
        d = max(d, stormBlob(length(o), 70.0, puff) * smoothstep(STORM_H - 60.0, STORM_H - 40.0, h));
        // the anvil: a thin flat sheet spreading downwind, flat on top, its underside rising to a thin rim; ice, so its
        // edges are fibrous streaks along the wind, not puffs
        float ra = length(vec2(aw.x / (aw.x > 0.0 ? STORM_DOWN : STORM_UP), aw.y / STORM_SIDE));
        if (detail > 0) ra += 0.16 * (texture(CloudNoiseSampler, fract(vec2(aw.x / 900.0, aw.y / 140.0))).a - 0.5);
        float top = STORM_H + 6.0 - 4.0 * ra, lo = STORM_H - 52.0 + 46.0 * pow(clamp(ra, 0.0, 1.0), 0.6);
        float a = clamp((1.0 - ra) * 5.0, 0.0, 1.0) * clamp((h - lo) / 6.0, 0.0, 1.0) * clamp((top - h) / 3.0, 0.0, 1.0);
        d = max(d, a * smoothstep(0.0, 0.25, 1.2 - ra));
    }
    return d * (1.0 - smoothstep(cut - 40.0, cut, h)) * min(1.0, grow * 3.0);
}

// rain under the base: a core of soft shafts a little downwind of the centre, slanting downwind as it falls, with a
// faint vertical grain; the ground stops it (q.y relative to the base, groundH the ground's height under the base)
float stormRain(vec3 q, vec2 w, float groundH) {
    float below = -q.y;
    if (below <= 0.0 || q.y < groundH - 4.0) return 0.0;
    vec2 x = q.xz - w * (below * 0.3);
    float rr = length(x - w * 12.0);
    if (rr > STORM_RAIN) return 0.0;
    float core = 1.0 - smoothstep(STORM_RAIN * 0.55, STORM_RAIN, rr);
    float shafts = 0.35 + 0.65 * smoothstep(0.35, 0.65, texture(CloudNoiseSampler, fract(x / 300.0)).g);
    float grain = 0.7 + 0.3 * texture(CloudNoiseSampler, fract(x / 40.0)).a;
    return core * shafts * grain * smoothstep(0.0, 10.0, below) * (1.0 - smoothstep(-6.0, 4.0, groundH - q.y));
}

// how much sunlight storm cells let down to a camera-relative point p, toward the light l: the light ray sampled where
// it crosses three heights of the body (a long shadow when the sun is low)
float stormShadow(vec3 p, vec3 l, float camY) {
    int n = stormCount();
    if (n == 0 || l.y < 0.03) return 1.0;
    float od = 0.0;
    for (int k = 0; k < 2; k++) {
        if (k >= n) break;
        vec4 a = stormA(k), b = stormB(k);
        float baseRel = a.z - camY;   // the base's height relative to the camera
        // the light ray's closest approach to the cell's axis: far off, nothing to sample
        vec2 rel = p.xz - a.xy;
        for (int j = 0; j < 3; j++) {
            float hh = 40.0 + float(j) * 105.0;
            float t = (baseRel + hh - p.y) / l.y;
            if (t <= 0.0) continue;
            vec3 q = vec3(rel.x + l.x * t, hh, rel.y + l.z * t);
            if (dot(q.xz, q.xz) > 330.0 * 330.0) continue;
            od += stormCell(q, b.xy, b.w, a.w, 0) * 105.0;
        }
    }
    return exp(-od * STORM_SIGMA * 0.35);
}
