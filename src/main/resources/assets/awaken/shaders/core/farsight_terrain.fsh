#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>
#include <awaken:farsight_air.glsl>

// AWAKEN Far Sight 2: far ground as Minecraft draws ground, in blocks, at every distance (Euan, 2026-10-25: "it feels
// like an extension of the world"). Each pixel walks its view ray through block columns standing on the ground here (the
// mesh's local plane): a column as wide as about a pixel (one block near the real chunks, two, four, ... further out,
// as Distant Horizons' and Voxy's levels of detail do), its top rounded to a whole block. Where the ray first meets a
// column it shows that face: the top, or a side facing x or z. Faces shade as the game shades them (top 1, north and
// south 0.8, east and west 0.6), grass tops stand over dirt risers, cliffs are stone, snow lies on the tops, and every
// block has its own grain. No smooth gradients, no triangle facets: terraces, as on the real slopes beside it.
// - What it's made of, by the rules the chunks use: the alpine snow line and the game's own cold line, both broken into
//   patches at the edge; scree and bare rock by slope; badlands strata; water with shallows; forest floor under crowns.
// - Light: the game's sky light, a warm key where the sun reaches past the ridges and a cool shade where it doesn't.
// - Air: farsight_air.glsl. Under the real chunks it stays hidden, where they are there, dithering in at their edge.

uniform sampler2D LightSampler;
uniform sampler2D ChunkSampler;     // which real chunks the client has (64x64, chunk coordinate masked to 63)

layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec3 vColor;
layout(location = 3) in vec4 vInfo;
layout(location = 4) in vec4 vCrown;
layout(location = 5) flat in ivec2 vFlags;
layout(location = 6) in float vCell;
layout(location = 7) in float vCold;

layout(location = 0) out vec4 fragColor;

const vec3 SCREE = vec3(0.55, 0.53, 0.50), SNOW = vec3(0.93, 0.95, 0.98);
// the dirt texture's own average: risers as bright as the real ones beside them
const vec3 DIRT = vec3(0.53, 0.38, 0.27);
// what steep faces show, by the biome (colour flags bits 4-6): stone, or an Awakened biome's own block
const vec3 ROCKS[5] = vec3[](vec3(0.49, 0.48, 0.46), vec3(0.68, 0.80, 0.86), vec3(0.23, 0.23, 0.28), vec3(0.78, 0.74, 0.65), vec3(0.24, 0.22, 0.18));

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

// smooth value noise in 0..1 (the snow line's wander, the patches at the snow's edge)
float valueNoise(vec2 world, float cell, int seed) {
    vec2 u = world / cell;
    ivec2 i = ivec2(floor(u));
    vec2 f = fract(u);
    f = f * f * (3.0 - 2.0 * f);
    float a = farHash(i * 3 + seed), b = farHash((i + ivec2(1, 0)) * 3 + seed);
    float c = farHash((i + ivec2(0, 1)) * 3 + seed), d = farHash((i + ivec2(1, 1)) * 3 + seed);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float snowLine(vec2 world, vec2 g) {
    float slope = length(g);
    float wander = SNOW_WANDER * (valueNoise(world, 160.0, 77) * 2.0 - 1.0);
    float facing = slope < 1e-4 ? 0.0 : g.y / slope * smoothstep(0.08, 0.45, slope);
    return SNOW_LINE + wander + (facing > 0.0 ? -NORTH_DROP * facing : -SOUTH_LIFT * facing);
}

// how much snow lies on a top at height y: the alpine line and the game's cold line, each broken into patches over a
// band (drifts reaching down, bare ground reaching up), never one hard line
float snowOn(vec2 world, float y, vec2 g, float slope, bool alpine) {
    float patch = (valueNoise(world, 24.0, 5) - 0.5) * 14.0 + (valueNoise(world, 7.0, 9) - 0.5) * 6.0;
    float snow = 0.0;
    if (alpine) snow = smoothstep(-1.5, 1.5, y + patch - snowLine(world, g));
    snow = max(snow, smoothstep(-1.5, 1.5, vCold + patch * 0.7));
    // snow slides off steep faces
    return snow * (1.0 - smoothstep(SNOW_SLOPE - 0.1, SNOW_SLOPE + 0.25, slope));
}

struct Hit {
    bool ok;
    float t;        // along the ray from the camera
    vec3 n;         // the face's normal
    float below;    // blocks under its column's top (0 on a top face)
    ivec2 cell;     // the column, in cells of the walk's width
};

// walks the ray through columns of width w (blocks) standing on the plane through this pixel's ground with slope g
Hit columns(vec3 rel, vec2 g, float w) {
    Hit h;
    h.ok = false;
    float tp = length(rel);
    vec3 d = rel / tp;
    // how fast the ray drops toward the plane per block along it, and where it stands well above the columns
    float approach = d.y - dot(g, d.xz);
    if (approach > -0.015) return h;
    float slope = length(g);
    float above = 0.6 + w * (0.5 + 0.75 * slope);
    float t = max(tp + above / approach, tp * 0.5);
    float tEnd = tp + (tp - t) * 1.5 + w * 4.0;
    // the grid: columns at whole multiples of w in the world (FarCamInt is a multiple of 256, w divides it)
    vec2 off = FarCam.xz;
    vec2 p = d.xz * t + off;
    ivec2 cell = ivec2(floor(p / w));
    vec2 stepDir = sign(d.xz);
    vec2 inv = 1.0 / max(abs(d.xz), vec2(1e-6));
    vec2 next = (vec2(cell) + max(stepDir, 0.0)) * w;
    vec2 tMax = t + (next - p) * stepDir * inv;
    vec2 tDelta = w * inv;
    int axis = -1;
    float camY = FarFog.z;
    for (int i = 0; i < 28; i++) {
        float tOut = min(tMax.x, tMax.y);
        // this column's top: the plane at its centre, rounded to a whole block
        vec2 centre = (vec2(cell) + 0.5) * w - off;
        float top = floor(rel.y + dot(g, centre - rel.xz) + camY + 0.5) - camY;
        float yIn = d.y * t;
        if (yIn <= top && axis >= 0) {
            h.ok = true;
            h.t = t;
            h.n = axis == 0 ? vec3(-stepDir.x, 0.0, 0.0) : vec3(0.0, 0.0, -stepDir.y);
            h.below = top - yIn;
            h.cell = cell;
            return h;
        }
        if (d.y < 0.0 && d.y * tOut <= top) {
            h.ok = true;
            h.t = max(t, top / d.y);
            h.n = vec3(0.0, 1.0, 0.0);
            h.below = 0.0;
            h.cell = cell;
            return h;
        }
        if (tOut > tEnd) break;
        t = tOut;
        if (tMax.x < tMax.y) { tMax.x += tDelta.x; cell.x += int(stepDir.x); axis = 0; }
        else { tMax.y += tDelta.y; cell.y += int(stepDir.y); axis = 1; }
    }
    return h;
}

void main() {
    float horizontal = length(vRel.xz);
    vec2 rel = vRel.xz + FarCam.xz;                       // blocks from FarCamInt: exact cells, any distance out
    // under the real chunks it stays hidden, but only where they are there (a fresh teleport, a fast flight)
    if (horizontal < FarDepth.z + FarDepth.w * bayer4(gl_FragCoord.xy)) {
        ivec2 chunk = (FarCamInt.xz >> 4) + ivec2(floor(rel / 16.0));
        if (texelFetch(ChunkSampler, chunk & 63, 0).r > 0.5) discard;
    }
    int flags = vFlags.x;
    bool generated = (flags & 128) != 0, alpine = (flags & 1) != 0;
    float depth = vInfo.r * 255.0;
    bool water = depth > 0.5;
    vec3 n = normalize(vNormal);
    vec2 g = -n.xz / max(n.y, 0.12);
    float slope = length(g);
    float footprint = max(fwidth(vRel.x), fwidth(vRel.z)); // blocks a pixel spans here

    // the columns: about a pixel wide, a power of two, dithered between widths so no ring of change shows
    float level = clamp(log2(max(footprint * 1.3, 1.0)) + bayer4(gl_FragCoord.xy) - 0.5, 0.0, 6.0);
    float w = exp2(floor(level));
    Hit hit;
    hit.ok = false;
    if (!water) hit = columns(vRel, g, w);
    vec3 pos = hit.ok ? normalize(vRel) * hit.t : vRel;
    vec3 face = hit.ok ? hit.n : n;
    float y = pos.y + FarFog.z;
    vec2 world = vec2(FarCamInt.xz) + pos.xz + FarCam.xz;
    ivec2 blk = hit.ok ? hit.cell : FarCamInt.xz + ivec2(floor(rel));
    float grain = farHash(blk * 7 + ivec2(int(floor(y)), 3));

    // the top's material, as the chunks choose it (the real chunks' colour where seen)
    vec3 top = vColor;
    float density = vInfo.b;
    float rock = 0.0, snow = 0.0, canopy = 0.0;
    if (water) {
        // shallows show their sandy floor through the water; deep water keeps its colour
        vec3 shallow = mix(vec3(0.80, 0.76, 0.58) * (top.r + top.g + top.b) / 1.2, top * 1.5, 0.5);
        top = mix(shallow, top, smoothstep(1.0, 9.0, depth));
    } else {
        float jag = 0.25 * (grain - 0.5);
        vec3 STONE = ROCKS[min((flags >> 4) & 7, 4)];
        if (!generated) {
            rock = smoothstep(ROCK_SLOPE - 0.2, ROCK_SLOPE + 0.1, slope + jag);
            float scree = smoothstep(SCREE_SLOPE - 0.15, SCREE_SLOPE + 0.1, slope + jag) * (1.0 - rock) * float(((flags >> 4) & 7) == 0);
            if ((flags & 4) != 0) top = mix(top, band(y), smoothstep(0.5, 1.2, slope) * 0.85);
            else if ((flags & 8) != 0) top = mix(top, STONE, 0.6 + 0.4 * rock);
            else top = mix(mix(top, SCREE, scree * 0.85), STONE, rock * 0.9);
        }
        // forests: the floor under them takes the crowns' tint (the trees themselves stand on it)
        if (density > 0.0) top = mix(top, vCrown.rgb * (0.8 + 0.3 * grain), density * 0.6 * (1.0 - rock));
        // past a few hundred blocks the drawn trees thin to dots and a forest is its canopy: crowns over every face
        // of the slope, risers too, speckled crown by crown, so forested far slopes read as forest, not as bare terraces
        canopy = density * (1.0 - rock) * smoothstep(600.0, 2400.0, horizontal);
        if (!generated) snow = snowOn(world, y, g, slope, alpine);
    }
    bool grassy = top.g > top.r * 1.05 && top.g > top.b && rock < 0.5;
    vec3 c;
    if (hit.ok && hit.n.y < 0.5) {
        // a column's side: stone, sand, snow blocks and the like are the same all through; under grass, dirt with the
        // grass's fringe along its top edge; snow on the top shows as a white fringe
        vec3 side = grassy ? mix(DIRT, top * 0.9, step(hit.below, 0.19)) : top;
        side = mix(side, SNOW, snow * step(hit.below, 0.22 + 0.6 * float(alpine && !grassy)));
        if ((flags & 4) != 0 && !generated) side = band(y);
        c = side;
    } else {
        c = mix(top, SNOW, snow);
    }
    if (canopy > 0.0) {
        float crownGrain = farHash(blk * 5 + ivec2(17, int(floor(y / 3.0))));
        c = mix(c, vCrown.rgb * (0.72 + 0.4 * crownGrain), canopy * 0.9);
    }
    // every block its own shade, as block textures vary
    c *= 0.9 + 0.2 * grain;

    // light: the game's face shading, a warm key where the sun reaches past the ridges, a cool shade where it doesn't
    vec3 light = farLight(face, vCrown.a);
    light *= mix(1.0, vInfo.a, 0.8);
    if (water) {
        float facing = abs(normalize(vRel).y);
        light = mix(light, vec3(1.0), 0.4);
        c = mix(c, FogColor.rgb, pow(1.0 - facing, 5.0) * 0.55);
    }
    if (FarCam.w > 0.5 && generated) c = vec3(1.0, 0.0, 1.0);
    c *= light * texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;
    // valley cloud fills hollows: the bake's valley shade (1 open, 0.65 a deep hollow) says how deep this one is
    fragColor = vec4(farAir(farMist(c, pos, smoothstep(0.96, 0.78, vInfo.a)), pos), 1.0);
}
