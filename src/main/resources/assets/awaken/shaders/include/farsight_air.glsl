// AWAKEN Far Sight 2: the air between the camera and far ground, shared by the terrain and the trees. Aerial perspective
// from an exponential atmosphere at real scale (density FarAir.x per block at sea level, scale height FarAir.y), sun-
// coloured toward the sun; then the game's own weather fog (rain, a gate's fog) and the horizon melting into the sky.
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

// valley cloud: a slab of patchy cloud lying low among the mountains, so ranges rise out of a sea of it and valleys fill;
// sunlit on top, shadowed beneath, warm at golden hour. Over far ground only, eased in past the real chunks.
vec3 farMist(vec3 c, vec3 rel) {
    if (FarMist.w <= 0.0 || (int(FarCam.y) & 4) != 0) return c;
    float dist = length(rel);
    float camY = FarFog.z;
    float y0 = FarMist.x, y1 = FarMist.x + FarMist.y;
    // the stretch of this ray (camera to the ground here) inside the slab
    float dy = rel.y;
    float t0, t1;
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
    // where the ray crosses it: patches of cloud a few hundred blocks across, billowed
    vec2 mid = rel.xz * (t0 + t1) * 0.5;
    float cover = farLattice(mid, 256, 11) * 0.65 + farLattice(mid, 64, 23) * 0.35;
    float threshold = 1.0 - 0.75 * FarMist.w;
    float puff = smoothstep(threshold, threshold + 0.18, cover);
    float alpha = (1.0 - exp(-inside * FarMist.z * puff)) * smoothstep(FarDepth.z, FarDepth.z + 600.0, length(rel.xz));
    // lit from above: the deeper into the slab the ray ends, the more shaded
    float top = clamp((camY + rel.y * t1 - y0) / max(FarMist.y, 1.0), 0.0, 1.0);
    vec3 lit = mix(FogColor.rgb * 0.85, FarSunColor.rgb * (0.95 + 0.25 * FarSun.w), FarSun.w);
    vec3 cloud = mix(lit * 0.78, lit * 1.05 + 0.04, top);
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
    vec3 air = FogColor.rgb + FarSunColor.rgb * FarAir.z * phase * FarSun.w;
    c = mix(air, c, t);
    float fog = max(linear_fog_value(dist, FogEnvironmentalStart, FogEnvironmentalEnd), linear_fog_value(length(rel.xz), FarFog.x, FarFog.y));
    return mix(c, FogColor.rgb, fog * FogColor.a);
}

// light on far things: faces shaded as the game shades block faces, a warm key where the sun reaches, a cool shade
// where it doesn't (sunlit: 0..1, the ridge shadow already in it)
vec3 farLight(vec3 n, float sunlit) {
    const vec3 SHADE_TINT = vec3(0.86, 0.93, 1.14);
    float face = (n.y + abs(n.x) * 0.6 + abs(n.z) * 0.8) / max(n.y + abs(n.x) + abs(n.z), 1e-3);
    float key = FarSun.w * 0.85;
    return vec3(face) * mix(vec3(1.0), mix(SHADE_TINT * 0.74, FarSunColor.rgb * 1.16, sunlit), key);
}
