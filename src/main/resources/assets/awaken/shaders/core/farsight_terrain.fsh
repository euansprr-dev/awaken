#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>
#include <awaken:farsight_erosion.glsl>

// AWAKEN Far Sight 2: what far ground looks like, per pixel.
// - Relief finer than the mesh: Awakened Alpine's erosion octaves between the ring's cell and a few pixels, lit.
// - What it's made of, by the same rule the chunks use: snow above a snow line that drops on north faces, scree and
//   bare rock by slope, badlands strata on steep faces, water with shallows; the real blocks' colour where seen.
// - Minecraft while a block is still bigger than a pixel: a block-by-block colour grain and a line at every block of
//   height on slopes (the terraces), both fading out once a block is under a pixel. Forests as a speckled canopy.
// - Light: the game's sky light, faces shaded like block faces, a warm key where the sun reaches and a cool shade where
//   a ridge hides it; valleys darker.
// - Air: aerial perspective from an exponential atmosphere at real scale (km), sun-coloured toward the sun, and the
//   game's own weather fog; at the horizon it fades into the sky. Under the real chunks it stays hidden, dithering in.

uniform sampler2D LightSampler;

layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec3 vColor;
layout(location = 3) in vec4 vInfo;
layout(location = 4) in vec4 vCrown;
layout(location = 5) flat in ivec2 vFlags;
layout(location = 6) in float vCell;

layout(location = 0) out vec4 fragColor;

const vec3 STONE = vec3(0.50, 0.49, 0.47), SCREE = vec3(0.56, 0.54, 0.51), SNOW = vec3(0.93, 0.95, 0.98);
const vec3 SHADE_TINT = vec3(0.86, 0.93, 1.14);

float bayer4(vec2 p) {
    ivec2 q = ivec2(p) & 3;
    int i = q.x + q.y * 4;
    const float m[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (m[i] + 0.5) / 16.0;
}

// badlands strata: terracotta bands a block or three thick, the same at every distance
vec3 band(float y) {
    const vec3 c[8] = vec3[](vec3(0.63, 0.33, 0.15), vec3(0.60, 0.37, 0.27), vec3(0.82, 0.69, 0.63), vec3(0.63, 0.33, 0.15),
                             vec3(0.73, 0.52, 0.21), vec3(0.56, 0.24, 0.18), vec3(0.53, 0.42, 0.38), vec3(0.30, 0.20, 0.14));
    return c[int(floor(farHash(ivec2(int(floor(y / 2.0)), 77)) * 8.0))];
}

// AlpineSurface.wobble: smooth value noise on a 160-block lattice (the snow line's wander)
float wobble(vec2 world) {
    vec2 u = world / 160.0;
    ivec2 i = ivec2(floor(u));
    vec2 f = fract(u);
    f = f * f * (3.0 - 2.0 * f);
    float a = erosionUnit(erosionHash(i.x, i.y, 77)), b = erosionUnit(erosionHash(i.x + 1, i.y, 77));
    float c = erosionUnit(erosionHash(i.x, i.y + 1, 77)), d = erosionUnit(erosionHash(i.x + 1, i.y + 1, 77));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float snowLine(vec2 world, vec2 g) {
    float slope = length(g);
    float wander = SNOW_WANDER * (wobble(world) * 2.0 - 1.0);
    float facing = slope < 1e-4 ? 0.0 : g.y / slope * smoothstep(0.08, 0.45, slope);
    return SNOW_LINE + wander + (facing > 0.0 ? -NORTH_DROP * facing : -SOUTH_LIFT * facing);
}

void main() {
    float horizontal = length(vRel.xz);
    if (horizontal < FarDepth.z + FarDepth.w * bayer4(gl_FragCoord.xy)) discard;
    vec2 rel = vRel.xz + FarCam.xz;                       // blocks from FarCamInt: exact cells, any distance out
    float y = vRel.y + FarFog.z;
    float footprint = max(fwidth(vRel.x), fwidth(vRel.z)); // blocks a pixel spans here
    int flags = vFlags.x;
    bool generated = (flags & 128) != 0, alpine = (flags & 1) != 0;
    float depth = vInfo.r * 255.0;
    bool water = depth > 0.5;

    vec3 n = normalize(vNormal);
    vec2 g = -n.xz / max(n.y, 0.05);
    // relief finer than this ring's mesh: erosion's octaves from two cells down to about four pixels
    float strength = smoothstep(ERODE_FROM, ERODE_FULL, y) * (alpine ? 1.0 : 0.55);
    if (!water && strength > 0.0 && footprint * 4.0 < vCell * 2.0) {
        g += erosionSlope(FarCamInt.xz, rel, g, strength, max(footprint * 4.0, 8.0), vCell * 2.0);
        n = normalize(vec3(-g.x, 1.0, -g.y));
    }
    float slope = length(g);
    vec2 world = vec2(FarCamInt.xz) + rel;
    ivec2 blk = FarCamInt.xz + ivec2(floor(rel));
    float grain = 1.0 - smoothstep(0.6, 1.6, footprint);
    float jitter = farHash(blk + ivec2(13, 7));

    vec3 c = vColor;
    float density = vInfo.b;
    if (water) {
        // shallows show their sandy floor through the water; deep water keeps its colour
        vec3 shallow = mix(vec3(0.80, 0.76, 0.58) * (c.r + c.g + c.b) / 1.2, c * 1.5, 0.5);
        c = mix(shallow, c, smoothstep(1.0, 9.0, depth));
    } else if (!generated) {
        // stone where the ground is too steep to hold soil; scree beneath the cliffs
        float edge = 0.25 * (jitter - 0.5) * grain;
        float rock = smoothstep(ROCK_SLOPE - 0.2, ROCK_SLOPE + 0.1, slope + edge);
        float scree = smoothstep(SCREE_SLOPE - 0.15, SCREE_SLOPE + 0.1, slope + edge) * (1.0 - rock);
        if ((flags & 4) != 0) c = mix(c, band(y), smoothstep(0.5, 1.2, slope) * 0.85);
        else if ((flags & 8) != 0) c = mix(c, STONE, 0.6 + 0.4 * rock);
        else c = mix(mix(c, SCREE, scree * 0.85), STONE, rock * 0.9);
        // forests: until trees stand here, a canopy of crowns speckled tree by tree (gone once a tree is under a pixel)
        if (density > 0.0) {
            float treeGrain = 1.0 - smoothstep(2.0, 5.0, footprint);
            float t = farHash(FarCamInt.xz / 4 + ivec2(floor(rel / 4.0)));
            vec3 crowns = vCrown.rgb * mix(1.0, 0.7 + 0.45 * t, treeGrain);
            c = mix(c, crowns, density * 0.85 * (1.0 - rock));
        }
        // snow: the alpine snow line (by height, aspect and slope), or a cold biome's gentle ground
        float snow = 0.0;
        if (alpine) {
            float line = snowLine(world, g) + 8.0 * (jitter - 0.5) * grain;
            snow = smoothstep(line - 3.0, line + 3.0, y) * (1.0 - smoothstep(SNOW_SLOPE - 0.15, SNOW_SLOPE + 0.15, slope + 0.3 * (jitter - 0.5) * grain));
        } else if ((flags & 2) != 0) {
            snow = 1.0 - smoothstep(SNOW_SLOPE - 0.2, SNOW_SLOPE + 0.2, slope);
        }
        c = mix(c, SNOW, snow);
        // a block-by-block grain while blocks are bigger than a pixel
        c *= 1.0 + (farHash(blk) - 0.5) * 0.14 * grain;
    } else {
        c *= 1.0 + (farHash(blk) - 0.5) * 0.10 * grain;
    }
    if (FarCam.w > 0.5 && generated) c = vec3(1.0, 0.0, 1.0);

    // light: faces shaded as the game shades block faces, a warm key where the sun reaches, a cool shade where it doesn't
    float face = (n.y + abs(n.x) * 0.6 + abs(n.z) * 0.8) / max(n.y + abs(n.x) + abs(n.z), 1e-3);
    float sunlit = max(dot(n, FarSun.xyz), 0.0) * vCrown.a;
    float key = FarSun.w * 0.85;
    vec3 light = vec3(face) * mix(vec3(1.0), mix(SHADE_TINT * 0.74, FarSunColor.rgb * 1.16, sunlit), key);
    light *= mix(1.0, vInfo.a, 0.8);
    // the terraces: a line where the ground crosses each block of height, on slopes, while a block spans pixels
    float fy = fwidth(y);
    if (!water && fy < 0.6) {
        float toInt = abs(fract(y + 0.5) - 0.5);
        float line = (1.0 - smoothstep(fy * 0.5, fy * 1.5 + 0.02, toInt)) * smoothstep(0.12, 0.35, slope);
        light *= 1.0 - 0.2 * line * (1.0 - smoothstep(0.25, 0.6, fy));
    }
    if (water) {
        float facing = abs(normalize(vRel).y);
        light = mix(light, vec3(1.0), 0.4);
        c = mix(c, FogColor.rgb, pow(1.0 - facing, 5.0) * 0.55);
    }
    c *= light * texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;

    // the air between: an exponential atmosphere (density FarAir.x per block at sea level, scale height FarAir.y)
    float dist = length(vRel);
    vec3 dir = vRel / max(dist, 1e-3);
    float hc = FarFog.z - FarFog.w;
    float below = exp(-max(hc, -200.0) / FarAir.y);
    float od = abs(dir.y) * dist < 1.0 ? FarAir.x * below * dist
             : FarAir.x * below * FarAir.y / dir.y * (1.0 - exp(-dist * dir.y / FarAir.y));
    float t = exp(-max(od, 0.0));
    float mu = dot(dir, FarSun.xyz);
    float phase = (1.0 - 0.72 * 0.72) / pow(1.0 + 0.72 * 0.72 - 1.44 * mu, 1.5) * 0.08;
    vec3 air = FogColor.rgb + FarSunColor.rgb * FarAir.z * phase * FarSun.w;
    c = mix(air, c, t);
    // weather (rain, a gate's fog) as the game fogs its own terrain, then the horizon melting into the sky
    float fog = max(linear_fog_value(dist, FogEnvironmentalStart, FogEnvironmentalEnd), linear_fog_value(horizontal, FarFog.x, FarFog.y));
    fragColor = vec4(mix(c, FogColor.rgb, fog * FogColor.a), 1.0);
}
