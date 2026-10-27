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
uniform sampler2D VoxelSampler;     // where City Sight draws the real blocks (256x256 world chunks, toroidal)

layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec3 vColor;
layout(location = 3) in vec4 vInfo;
layout(location = 4) in vec4 vCrown;
layout(location = 5) flat in ivec2 vFlags;
layout(location = 6) in float vCell;
layout(location = 7) in float vCold;
layout(location = 8) in vec4 vRocks;
layout(location = 9) in vec4 vBiome;

layout(location = 0) out vec4 fragColor;

const vec3 SCREE = vec3(0.55, 0.53, 0.50), SNOW = vec3(0.93, 0.95, 0.98);
// the dirt texture's own average: risers as bright as the real ones beside them
const vec3 DIRT = vec3(0.53, 0.38, 0.27);
// ice's textures' average (FarPalette.ICE)
const vec3 ICE = vec3(0.557, 0.702, 0.925);
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
float snowOn(vec2 world, float y, vec2 g, float slope, float alpine) {
    float patch = (valueNoise(world, 24.0, 5) - 0.5) * 14.0 + (valueNoise(world, 7.0, 9) - 0.5) * 6.0;
    float snow = 0.0;
    if (alpine > 0.0) snow = alpine * smoothstep(-1.5, 1.5, y + patch - snowLine(world, g));
    snow = max(snow, smoothstep(-1.5, 1.5, vCold + patch * 0.7));
    // snow slides off steep faces
    return snow * (1.0 - smoothstep(SNOW_SLOPE - 0.1, SNOW_SLOPE + 0.25, slope));
}

// The sea floor under far water, seen through it (Euan: the real water shows its floor, the far water showed none, so
// the floor ended where the chunks did). The floor lies `depth` blocks down along the view ray, so it shifts as the
// camera moves as the real one does: blocks of sand, gravel in the deep, seagrass in patches, anchored to the world.
// How much shows: the water's own absorption over the path through it (shallows and steep views see it, deep water and
// grazing views don't), and less and less past the render distance's edge, until the open sea is only water.
vec3 seaFloor(vec3 water, vec2 world, vec3 d, float depth, float horizontal) {
    float facing = max(-d.y, 0.03);
    vec2 floorXZ = world + d.xz / facing * depth;
    ivec2 fb = ivec2(floor(floorXZ));
    float patches = valueNoise(vec2(fb) + 0.5, 7.0, 41), pebble = farHash(fb * 5 + ivec2(13, 7));
    vec3 sand = vec3(0.80, 0.75, 0.58), gravel = vec3(0.50, 0.49, 0.46), grass = vec3(0.20, 0.38, 0.17);
    vec3 bed = mix(sand, gravel, smoothstep(9.0, 18.0, depth + 6.0 * (patches - 0.5)));
    bed = mix(bed, grass, step(0.62, patches) * smoothstep(2.0, 5.0, depth) * (1.0 - smoothstep(14.0, 22.0, depth)));
    bed *= 0.85 + 0.3 * pebble;
    // seen through the water: tinted by it
    vec3 seen = bed * (water * 1.6 + 0.2);
    float clear = exp(-0.09 * depth / facing) * (1.0 - smoothstep(FarDepth.z + 32.0, FarDepth.z + 700.0, horizontal));
    return mix(water, seen, clear);
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
    int flags = vFlags.x;
    bool generated = (flags & 128) != 0;
    // the biome's materials as weights, blended between the corners (vBiome)
    float alpine = vBiome.x, badlands = vBiome.y, stony = vBiome.z * (1.0 - vBiome.y);
    float depth = vInfo.r * 255.0;
    // water is a flat surface: a steep far triangle whose corners hold water (a canal's corner joined to a roof's, a
    // river below a bank) is the bank or the quay wall, not a blue wall of water (Euan, Carillon's river district)
    vec3 facet = cross(dFdx(vRel), dFdy(vRel));
    bool flatWater = abs(facet.y) > 0.9 * length(facet);
    bool water = depth > 0.5 && flatWater;
    bool bank = depth > 0.5 && !flatWater;
    // water where it's cold enough to snow is frozen over, as the chunks freeze it: ice, never open water with its floor
    // showing (frozen rivers' indigo water drew far valleys purple and see-through, Euan 2026-10-26)
    bool frozen = water && (flags & 2) != 0;
    // under the real chunks it stays hidden, but only where they are drawn: where they are loaded (a fresh teleport, a
    // fast flight) and in a section Sodium draws (farRealSection: seen from high up, the sea floor falls out of its reach
    // well inside the horizontal render distance); far water keeps to its own floor's reach,
    // so a real water surface drawn over a floor too far to draw keeps the far sea under it
    // City Sight draws the real blocks there: the far land steps aside (its coverage holds for 2 km)
    // (water by where the ray meets the real water's surface, an eighth under this one at the block's top: by this
    // one's, a strip along the edge of City Sight's chunks found this water in a chunk City Sight draws and the real
    // surface past it in one it doesn't, and neither drew there: a dashed line of sky across a lake, Euan 2026-10-27)
    vec2 coverXZ = water && vRel.y < -0.01 ? vRel.xz * ((vRel.y - 0.125) / vRel.y) : vRel.xz;
    if ((int(FarCam.y + 0.5) & 256) == 0 && length(vRel.xz) < 2000.0) {
        ivec2 world = (FarCamInt.xz >> 4) + ivec2(floor((coverXZ + FarCam.xz) / 16.0));
        // (a chunk dissolving in stands at its share: this land gives way in the complement of its dither)
        if (texelFetch(VoxelSampler, world & 255, 0).r > bayer4(gl_FragCoord.xy)) {
            // debug view 4: where the far land stepped aside for City Sight, magenta instead
            if (FarCam.w > 3.5 && FarCam.w < 4.5) { fragColor = vec4(1.0, 0.0, 1.0, 1.0); return; }
            discard;
        }
    }
    vec3 reach = vec3(vRel.x, vRel.y - (water ? depth : 0.0), vRel.z);
    // far water stays under the real water too (unless the camera is under the sea): wherever a real section isn't drawn
    // this frame (Sodium's culling, a chunk still meshing) the sea shows, never the clear colour through a hole
    // ...but only where the far mesh lies flat as a water surface does (a steep far triangle joining a canal's corner to a
    // roof's counts as water by its corners, and would stand in the loaded city as a wall), and only in the render
    // distance's outer band, where a real floor can be missing: nearer, the real floor is always drawn, and far water
    // there would show as boxes on the city's canals
    bool surface = abs(facet.y) > 0.97 * length(facet) && horizontal > FarDepth.z - 80.0;
    bool underReal = !(water && surface && FarFog.z >= FarFog.w);
    if (farRealSection(reach)) {
        ivec2 chunk = (FarCamInt.xz >> 4) + ivec2(floor(rel / 16.0));
        if (underReal && texelFetch(ChunkSampler, chunk & 63, 0).r > 0.5) {
            // debug view 2: where the mask hides far terrain, red instead (red that shows is a hole the chunks leave)
            if (FarCam.w > 1.5 && FarCam.w < 2.5) { fragColor = vec4(1.0, 0.0, 0.0, 1.0); return; }
            discard;
        }
    }
    vec3 n = normalize(vNormal);
    vec2 g = -n.xz / max(n.y, 0.12);
    float slope = length(g);
    // blocks a pixel spans here (as on screen: a horizon slice's pixels are finer, FarView)
    float footprint = max(fwidth(vRel.x), fwidth(vRel.z)) * FarView.x;

    // the columns: a power of two, at least a block and a half of pixels wide, so each pixel keeps meeting the same face
    // as the camera moves (narrower, a pixel jumps between a column's lit top and its shaded side: far slopes glitter);
    // between two widths each world cell of the wider one picks one, anchored to the land, never a screen pattern
    // that crawls over it. Part "steady" off: the old pixel-wide columns, screen-dithered (to measure the difference).
    float level;
    if ((int(FarCam.y) & 32) != 0) {
        level = clamp(log2(max(footprint * 1.3, 1.0)) + bayer4(gl_FragCoord.xy) - 0.5, 0.0, 6.0);
    } else {
        float lf = clamp(log2(max(footprint * 3.0, 1.0)), 0.0, 6.0), base = floor(lf);
        float pick = farHash(ivec2(floor(rel / exp2(base + 1.0))) * 3 + ivec2(31, 77));
        level = min(base + (pick < lf - base ? 1.0 : 0.0), 6.0);
    }
    float w = exp2(floor(level));
    Hit hit;
    hit.ok = false;
    if (!water) hit = columns(vRel, g, w);
    vec3 pos = hit.ok ? normalize(vRel) * hit.t : vRel;
    vec3 face = hit.ok ? hit.n : n;
    // a column's side is lit by the way the slope faces, not by the axis its column happens to show: a steep slope's
    // staircase shows x sides and z sides by turns as the slope turns, and with the slope read per triangle those turns
    // drew dark spikes down far mountains and grooves down their walls (Euan, 2026-10-26)
    if (hit.ok && hit.n.y < 0.5 && dot(n.xz, n.xz) > 1e-4) face = normalize(vec3(n.x, 0.0, n.z));
    float y = pos.y + FarFog.z;
    vec2 world = vec2(FarCamInt.xz) + pos.xz + FarCam.xz;
    ivec2 blk = hit.ok ? hit.cell : FarCamInt.xz + ivec2(floor(rel));
    float grain = farHash(blk * 7 + ivec2(int(floor(y)), 3));

    // the top's material, as the chunks choose it (the real chunks' colour where seen)
    vec3 top = vColor;
    float density = vInfo.b;
    if (bank) {
        top = mix(top, vec3(0.47, 0.46, 0.44), 0.75);
        density = 0.0;
    }
    float rock = 0.0, snow = 0.0, canopy = 0.0;
    if (frozen) {
        top = ICE * (0.92 + 0.16 * grain);
    } else if (water) {
        if ((int(FarCam.y) & 128) == 0) top = seaFloor(top, world, normalize(vRel), depth, horizontal);
    } else {
        float jag = 0.25 * (grain - 0.5);
        // the rock of the biome this block stands in: where biomes meet, block by block, each biome's share its odds
        // (never one rock per triangle: a border across a steep face drew grey spikes, Euan 2026-10-26)
        float pick = farHash(blk * 13 + ivec2(int(floor(y)), 41));
        float r0 = vRocks.x, r1 = r0 + vRocks.y, r2 = r1 + vRocks.z, r3 = r2 + vRocks.w;
        int ri = pick < r0 ? 0 : pick < r1 ? 1 : pick < r2 ? 2 : pick < r3 ? 3 : 4;
        vec3 STONE = ROCKS[ri];
        if (!generated) {
            rock = smoothstep(ROCK_SLOPE - 0.2, ROCK_SLOPE + 0.1, slope + jag);
            float scree = smoothstep(SCREE_SLOPE - 0.15, SCREE_SLOPE + 0.1, slope + jag) * (1.0 - rock) * float(ri == 0);
            vec3 plain = mix(mix(top, SCREE, scree * 0.85), STONE, rock * 0.9);
            vec3 own = mix(top, STONE, 0.6 + 0.4 * rock);
            vec3 banded = badlands > 0.0 ? mix(top, band(y), smoothstep(0.5, 1.2, slope) * 0.85) : top;
            top = mix(mix(plain, own, stony), banded, badlands);
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
        side = mix(side, SNOW, snow * step(hit.below, 0.22 + 0.6 * alpine * float(!grassy)));
        if (badlands > 0.0 && !generated) side = mix(side, band(y), badlands);
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
    if (water && !frozen) {
        float facing = abs(normalize(vRel).y);
        light = mix(light, vec3(1.0), 0.4);
        // grazing far water reflects the sky by the horizon, as Lens's real water beside it does at every distance (the
        // dusk slabs were holes to the clear colour, not this; limiting it to the far distance left the far sea darker
        // than the real water at the edge)
        c = mix(c, FogColor.rgb, pow(1.0 - facing, 5.0) * 0.55);
    }
    if (FarCam.w > 0.5 && FarCam.w < 1.5 && generated) c = vec3(1.0, 0.0, 1.0);
    // debug view 3: all far terrain blue (with its far trees red), so any far piece over the loaded world is plain
    if (FarCam.w > 2.5) { fragColor = vec4(0.0, 0.3, 1.0, 1.0); return; }
    c *= light * texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;
    // valley cloud fills hollows: the bake's valley shade (1 open, 0.65 a deep hollow) says how deep this one is
    fragColor = vec4(farAir(farMist(c, pos, smoothstep(0.96, 0.78, vInfo.a)), pos), 1.0);
}
