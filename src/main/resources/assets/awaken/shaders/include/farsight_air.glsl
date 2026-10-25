// AWAKEN Far Sight 2: the air between the camera and far ground, shared by the terrain and the trees. Aerial perspective
// from an exponential atmosphere at real scale (density FarAir.x per block at sea level, scale height FarAir.y), sun-
// coloured toward the sun; then the game's own weather fog (rain, a gate's fog) and the horizon melting into the sky.
// Needs minecraft:fog.glsl and awaken:farsight.glsl.

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
