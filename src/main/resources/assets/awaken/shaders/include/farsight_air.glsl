// AWAKEN Far Sight 2: the air between the camera and far ground, shared by the terrain and the trees. Aerial perspective
// from an exponential atmosphere at real scale (density FarAir.x per block at sea level, scale height FarAir.y: low, so
// valleys hold more haze than ridges and ranges stand out layer by layer), bluer by day, sun-coloured toward the sun; then the game's own weather fog (rain, a gate's fog) and the horizon melting into the sky.
// Needs minecraft:fog.glsl and awaken:farsight.glsl.

// smooth value noise on an exact integer lattice (cell blocks wide, a divisor of 256), in 0..1
float farLattice(vec2 rel, int cell, int seed) {
    vec2 u = (rel + FarCam.xz) / float(cell);
    ivec2 i = FarCamInt.xz / cell + ivec2(floor(u));
    vec2 f = fract(u);
    f = f * f * (3.0 - 2.0 * f);
    float a = farHash(i * 7 + seed), b = farHash((i + ivec2(1, 0)) * 7 + seed);
    float c = farHash((i + ivec2(0, 1)) * 7 + seed), d = farHash((i + ivec2(1, 1)) * 7 + seed);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// valley cloud: patchy cloud lying low in the valleys among the mountains, so ranges rise out of a sea of it; never over
// open lowland (valley 0..1: how deep in a hollow the ground under this ray lies, from the bake). Billowing tops, white
// in the sun with a share of its warmth, grey beneath. Over far ground only, eased in past the real chunks.
vec3 farMist(vec3 c, vec3 rel, float valley) {
    if (FarMist.w <= 0.0 || valley <= 0.0 || (int(FarCam.y) & 4) != 0) return c;
    float dist = length(rel);
    float camY = FarFog.z;
    // where the ray meets the ground: patches of cloud a few hundred blocks across, and how high they billow there
    float cover = farLattice(rel.xz, 256, 11) * 0.65 + farLattice(rel.xz, 64, 23) * 0.35;
    float threshold = 1.0 - 0.75 * FarMist.w;
    float puff = smoothstep(threshold, threshold + 0.2, cover);
    if (puff <= 0.0) return c;
    float y0 = FarMist.x, y1 = FarMist.x + FarMist.y * (0.45 + 0.55 * puff);
    // the stretch of this ray (camera to the ground here) inside that cloud
    float dy = rel.y, t0, t1;
    if (abs(dy) < 1e-3) {
        if (camY < y0 || camY > y1) return c;
        t0 = 0.0; t1 = 1.0;
    } else {
        t0 = clamp((y0 - camY) / dy, 0.0, 1.0);
        t1 = clamp((y1 - camY) / dy, 0.0, 1.0);
        if (t0 > t1) { float k = t0; t0 = t1; t1 = k; }
    }
    float inside = (t1 - t0) * dist;
    if (inside <= 0.0) return c;
    float alpha = (1.0 - exp(-inside * FarMist.z * puff)) * valley * smoothstep(FarDepth.z, FarDepth.z + 600.0, length(rel.xz));
    // lit from above: the deeper into the cloud the ray ends, the greyer
    float top = clamp((camY + rel.y * t1 - y0) / max(y1 - y0, 1.0), 0.0, 1.0);
    vec3 white = mix(FogColor.rgb, vec3(1.0), 0.55) * (0.5 + 0.5 * FarSun.w);
    vec3 sunlit = white * mix(vec3(1.0), FarSunColor.rgb, 0.45) * 1.1;
    vec3 cloud = mix(white * 0.72, sunlit, top);
    return mix(c, cloud, alpha);
}

vec3 farAir(vec3 c, vec3 rel) {
    float dist = length(rel);
    vec3 dir = rel / max(dist, 1e-3);
    float hc = FarFog.z - FarFog.w;
    float below = exp(-max(hc, -200.0) / FarAir.y);
    float od = abs(dir.y) * dist < 1.0 ? FarAir.x * below * dist
             : FarAir.x * below * FarAir.y / dir.y * (1.0 - exp(-dist * dir.y / FarAir.y));
    float t = exp(-max(od, 0.0));
    float mu = dot(dir, FarSun.xyz);
    float phase = (1.0 - 0.72 * 0.72) / pow(1.0 + 0.72 * 0.72 - 1.44 * mu, 1.5) * 0.08;
    // by day the air between far ridges is bluer than the horizon's pale fog (the sky's own blue, scattered toward the
    // eye); at dusk it keeps the fog's warmth
    vec3 blue = FogColor.rgb * vec3(0.82, 0.91, 1.10);
    vec3 air = mix(FogColor.rgb, blue, FarSun.w * (1.0 - FarSunColor.a) * 0.8) + FarSunColor.rgb * FarAir.z * phase * FarSun.w;
    c = mix(air, c, t);
    float fog = max(linear_fog_value(dist, FogEnvironmentalStart, FogEnvironmentalEnd), linear_fog_value(length(rel.xz), FarFog.x, FarFog.y));
    return mix(c, FogColor.rgb, fog * FogColor.a);
}

// light on far things, matched to what Lens 2 does to the real chunks beside them (SPEC §22): faces shaded as the game
// shades block faces; where the sun reaches, a key warming toward golden hour; where a ridge hides it, Lens's cool shade
// (its shadeTint, × 0.82: its 0.68 on linear light, in display terms); faces turned from the sun shade only as it lowers
// (none at noon, when the game's own face shading is all there is). ridgeLit: 1 lit, 0 behind a ridge.
vec3 farLight(vec3 n, float ridgeLit) {
    const vec3 SHADE_TINT = vec3(0.86, 0.93, 1.14);
    float face = (n.y + abs(n.x) * 0.6 + abs(n.z) * 0.8) / max(n.y + abs(n.x) + abs(n.z), 1e-3);
    float low = FarSunColor.a;
    float away = (1.0 - smoothstep(-0.05, 0.2, dot(n, FarSun.xyz))) * low;
    float lit = ridgeLit * (1.0 - away);
    vec3 key = mix(vec3(1.02), sqrt(max(FarSunColor.rgb, vec3(0.0))), low);
    return vec3(face) * mix(vec3(1.0), mix(SHADE_TINT * 0.82, key, lit), FarSun.w);
}
