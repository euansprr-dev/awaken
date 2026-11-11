#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22), the light buffer at reduced size: how much sun reaches each surface and
// how open it is to the sky, without rendering the world from the sun. Big shadows march the terrain's heightfield
// toward the sun (ridges shade valleys, low suns stretch them, hills off screen still cast; leaves let light through
// in flecks); small ones march the depth buffer (contact shadows: grass, mobs, block edges); ambient occlusion samples a
// hemisphere in the depth buffer. Each frame traces with fresh noise and blends into last frame's result, reprojected
// by the frame's own matrices, so the look is many frames' work at one frame's cost. The same pass gathers volumetric
// light along each view ray (beams through gaps in a canopy, down canyons, past ridges, in any direction) from the sun
// map, so the whole of Lens 2's light is one pass and one history.
// Temporal (frame texel 17 y, the lensTemporal setting): a checkerboard of 8x8-pixel tiles marches each frame,
// alternating (whole tiles, so SIMD groups skip together); on its
// off frame a pixel keeps last frame's answer for its surface where the history saw the same distance, and a march
// blends in more strongly so the light answers as fast as before. No history, off screen or a new surface: it marches.
// Out: r sun visibility, g ambient occlusion, b volumetric light, a the surface's distance (one byte, 0 for sky).
uniform sampler2D DepthSampler;     // the world's depth, full size (first input: InSize is the full frame)
uniform sampler2D HeightSampler;
uniform sampler2D SunmapSampler;    // lens_sunmap.fsh: the height above which the sun reaches each column
uniform sampler2D CloudNoiseSampler; // Lens 2's clouds (L4): their shadows on the land and in the beams
uniform sampler2D HistorySampler;   // last frame's light buffer (persistent)
uniform sampler2D BlocksSampler;
uniform sampler2D BlocksFarSampler;  // the far block map (LensBlocksFar): cells of two blocks by one by two, out to 120 blocks
uniform sampler2D LitSampler;    // LensBlocks: which blocks near the camera stop the sun (the Sun Pass)
uniform sampler2D FrameSampler;
uniform sampler2D ParamsSampler;
uniform sampler2D HandSampler;      // the hand's own depth (LensHand): 0 where there is no hand

#include <awaken:lens_frame.glsl>
#include <awaken:lens_height.glsl>
#include <awaken:lens_normal.glsl>
#include <awaken:lens_clouds.glsl>
#include <awaken:lens_storm_frame.glsl>
#include <awaken:lens_storm.glsl>

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Quality;                  // 1 Cinematic, 2 Ultra
};

layout(location = 0) out vec4 fragColor;

vec4 P(int i) { return LensUse.x > 0.5 ? LensP[i] : texelFetch(ParamsSampler, ivec2(i, 0), 0); }

// The Sun Pass (SPEC §22 "Steady Light"; frame texel 17: z the block map's trusted reach in blocks, 0 without one;
// w its lowest y): from o toward the light through the block map, block by block. Where the heightfield has one top
// a column, the map has every block, so light passes under a canopy, through a doorway and under an arch, and a shadow
// is the block's own shape. Returns what gets through; status 0 when the walk settled it (a block stopped the sun), 1
// when the ray left the map in the open at tOut (the heightfield carries on from there), 2 where the map isn't read
// yet (the heightfield's own answer stands).
// The map's window this frame: its lowest x and z from the camera (frame texels 28 and 29, w; 128 blocks across).
// The walk had gone by a fixed 46 blocks round the camera, the least the window ever reaches; the window itself is
// 48 to 79 on each side, and a section at its rim not read yet says so (status 2)
vec2 mapCorner() { return vec2(F(28).w, F(29).w); }

// How many blocks a walk may cross. It was 72, which under a morning sun 30 degrees up is 26 blocks of height: short
// of a tall tree's crown, so the heightfield, which knows a crown only roughly, had the say on the ground at its
// foot, differently on either side of the map's edge (Euan, playing, 11 Nov, after the first fix: "on one side of the
// tree the shadow is perfect, then if I go to the other side, the shadow is gone"). A texel holds sixteen blocks of
// one column and is read once, so twice the blocks is about the same reads
const int WALK = 144;

float blockRay(vec3 o, vec3 l, out int status, out float tOut) {
    vec4 f17 = F(17);
    vec3 cam = F(12).xyz;
    float yMin = f17.w;
    vec2 corner = mapCorner();
    ivec3 held = ivec3(-2147483647);
    uint bits = 0u;
    ivec3 c = ivec3(floor(o));
    ivec3 stp = ivec3(l.x < 0.0 ? -1 : 1, l.y < 0.0 ? -1 : 1, l.z < 0.0 ? -1 : 1);
    vec3 inv = 1.0 / max(abs(l), vec3(1.0e-5));
    vec3 next = (vec3(c) + max(vec3(stp), 0.0) - o) * vec3(stp) * inv;
    float t = 0.0;
    status = 1;
    tOut = 0.0;
    for (int i = 0; i < WALK; i++) {
        // into the next block the ray crosses (never the one it starts in: a stair's own step, a slab's own top)
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += inv.x;
            c.x += stp.x;
        } else if (next.y < next.z) {
            t = next.y;
            next.y += inv.y;
            c.y += stp.y;
        } else {
            t = next.z;
            next.z += inv.z;
            c.z += stp.z;
        }
        tOut = t;
        vec2 q = vec2(float(c.x) + 0.5 - cam.x, float(c.z) + 0.5 - cam.z) - corner;
        if (q.x < 1.0 || q.y < 1.0 || q.x > 127.0 || q.y > 127.0 || float(c.y) < yMin || float(c.y) >= yMin + 128.0) return 1.0;
        ivec3 key = ivec3(c.x, c.z, c.y >> 4);
        if (key != held) {
            held = key;
            vec4 raw = texelFetch(BlocksSampler, ivec2(c.x & 127, (c.z & 127) * 8 + ((c.y >> 4) & 7)), 0);
            bits = uint(raw.r * 255.0 + 0.5) | (uint(raw.g * 255.0 + 0.5) << 8) | (uint(raw.b * 255.0 + 0.5) << 16) | (uint(raw.a * 255.0 + 0.5) << 24);
        }
        if (bits == 0xFFFFFFFFu) {
            status = 2;
            return 1.0;
        }
        uint k = (bits >> uint(2 * (c.y & 15))) & 3u;
        if (k == 0u) continue;
        if (k == 2u) {
            // leaves: three texels in ten of the face the ray comes in by let the sun through, so a canopy's shade is
            // flecked with light, in the leaf texture's own pixels
            vec3 q = floor((o + l * t) * 16.0 + 0.5);
            if (fract(sin(dot(q, vec3(12.9898, 78.233, 37.719))) * 43758.5453) < 0.3) continue;
        } else if (k == 3u) {
            // a block's lower half: only if the ray is under half height somewhere in it
            float yIn = o.y + l.y * t - float(c.y), yOut = o.y + l.y * min(next.x, min(next.y, next.z)) - float(c.y);
            if (min(yIn, yOut) >= 0.5) continue;
        }
        status = 0;
        return 0.0;
    }
    return 1.0;
}

// The far block map's walk (LensBlocksFar; SPEC §22 "Shadows beyond the block map"): from where the block map's walk
// ended, or from a point outside it, on toward the sun through cells of two blocks by one by two, out to some 120
// blocks. Every layer of leaves and every bough is in it, so a tall tree under a low sun casts one shadow wherever the
// camera stands. Returns what gets through; status 0 when a cell stopped the sun, 1 when the ray left the far map in the
// open at tOut (the heightfield carries on from there), 2 where it isn't read yet. Nothing nearer than skip counts: a
// point that starts here stands on blocks of its own cell and its neighbours
const int FAR_WALK = 176;
bool farMap() { return F(33).w > 0.5; }

float farRay(vec3 o, vec3 l, float skip, out int status, out float tOut) {
    vec3 cam = F(12).xyz;
    vec2 corner = vec2(F(30).w, F(31).w);
    float yMin = F(32).w;
    const vec3 CELL = vec3(2.0, 1.0, 2.0);
    vec3 oc = o / CELL, lc = l / CELL;
    ivec3 c = ivec3(floor(oc));
    ivec3 stp = ivec3(lc.x < 0.0 ? -1 : 1, lc.y < 0.0 ? -1 : 1, lc.z < 0.0 ? -1 : 1);
    vec3 inv = 1.0 / max(abs(lc), vec3(1.0e-5));
    vec3 next = (vec3(c) + max(vec3(stp), 0.0) - oc) * vec3(stp) * inv;
    float t = 0.0, through = 1.0;
    status = 1;
    tOut = 0.0;
    ivec3 held = ivec3(-2147483647);
    uint bits = 0u;
    for (int i = 0; i < FAR_WALK; i++) {
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += inv.x;
            c.x += stp.x;
        } else if (next.y < next.z) {
            t = next.y;
            next.y += inv.y;
            c.y += stp.y;
        } else {
            t = next.z;
            next.z += inv.z;
            c.z += stp.z;
        }
        tOut = t;
        vec2 q = vec2((float(c.x) + 0.5) * 2.0 - cam.x, (float(c.z) + 0.5) * 2.0 - cam.z) - corner;
        if (q.x < 2.0 || q.y < 2.0 || q.x > 254.0 || q.y > 254.0 || float(c.y) < yMin || float(c.y) >= yMin + 256.0) return through;
        if (t < skip) continue;
        ivec3 key = ivec3(c.x, c.z, c.y >> 4);
        if (key != held) {
            held = key;
            vec4 raw = texelFetch(BlocksFarSampler, ivec2(c.x & 127, (c.z & 127) * 16 + ((c.y >> 4) & 15)), 0);
            bits = uint(raw.r * 255.0 + 0.5) | (uint(raw.g * 255.0 + 0.5) << 8) | (uint(raw.b * 255.0 + 0.5) << 16) | (uint(raw.a * 255.0 + 0.5) << 24);
        }
        if (bits == 0xFFFFFFFFu) {
            status = 2;
            return through;
        }
        uint k = (bits >> uint(2 * (c.y & 15))) & 3u;
        if (k == 0u) continue;
        // leaves and a cell with one block in it (a post, a thin trunk) shade soft; two blocks and more stop the sun
        if (k != 1u) through *= k == 2u ? 0.42 : 0.5;
        if (k == 1u || through < 0.04) {
            status = 0;
            return 0.0;
        }
    }
    return through;
}

// the lit air (LensLit; SPEC §22 "Steady Light"): the camera's place in its volume, in blocks from the volume's corner,
// and w whether the texture holds one (frame texels 15, 20 and 21)
vec4 litCam() { return vec4(F(20).zw, F(21).w, F(15).w); }

// how much of the sun reaches a point of air w (camera-relative) by the lit air: x the light, y how far to trust it
// (0 outside the volume, rising over its outer blocks). Smooth across x and y by the texture's own filter; across z
// (slice to slice) by dz, a different -0.5..0.5 each frame
vec2 litNear(vec3 w, vec4 lc, float dz) {
    vec3 q = w + lc.xyz, e = min(q, 128.0 - q);
    float trust = clamp(min(min(e.x, e.z) / 8.0, e.y / 3.0), 0.0, 1.0);
    if (trust <= 0.0) return vec2(1.0, 0.0);
    float zi = clamp(floor(q.z + dz), 0.0, 127.0);
    vec2 uv = (vec2(mod(zi, 16.0), floor(zi / 16.0)) * 128.0 + clamp(q.xy, 0.5, 127.5)) / vec2(2048.0, 1024.0);
    return vec2(texture(LitSampler, uv).r, trust);
}

// what the lit air already knows of a surface point (camera-relative, a little off its face): 1 in full sun, -1 where
// it can't say and the block map must be walked (in shade, near any shadow's edge, under leaves, outside the volume). Three slices, each smoothed across its four cells, must agree: the volume is a block coarse and a second
// old, so it only answers where nothing near the point differs
float litSure(vec3 a) {
    vec4 lc = litCam();
    if (lc.w <= 0.0) return -1.0;
    vec3 q = a + lc.xyz;
    if (any(lessThan(q, vec3(2.0))) || any(greaterThan(q, vec3(126.0)))) return -1.0;
    float zi = floor(q.z), lo = 1.0, hi = 0.0;
    for (int k = -1; k <= 1; k++) {
        float z = zi + float(k);
        float v = texture(LitSampler, (vec2(mod(z, 16.0), floor(z / 16.0)) * 128.0 + q.xy) / vec2(2048.0, 1024.0)).r;
        lo = min(lo, v);
        hi = max(hi, v);
    }
    return lo > 0.996 ? 1.0 : -1.0;
}

// the heightfield, from o (a point in the world's ring) toward the light: 0 under solid blocks, dimmed through leaves
// leafy: whether the ray passes anywhere under a column's leaves (so inside the block map the walk must have its say)
float heightRay(vec3 o, vec3 l, float noise, bool ultra, bool roof, out bool leafy);

// How deep the leaves at a column's top go. The heightfield holds it for every column with no roof (b = 4096 + four
// times the depth, LensHeightfield.roofs): a mega jungle tree's crown is a dozen blocks deep, an oak's four, and a
// guess of seven for all of them lit the ground under the one and shaded it under the other, beyond the block map
// only. Under a roof (b its underside) the depth is not held: seven
const float CANOPY = 7.0;
float crownDepth(vec4 h) { return h.b >= 4096.0 ? (h.b - 4096.0) * 0.25 : CANOPY; }

// where the block map's walk would stop for a ray that meets nothing: the rim of the map's window, or WALK blocks'
// worth of cells, as a distance along l (o in the world's ring). The lit air's sure pixels go on
// from the same place the walked ones do, so the two can never differ
float walkEnd(vec3 o, vec3 l) {
    vec4 f12 = F(12);
    float yMin = F(17).w;
    vec2 corner = mapCorner();
    vec3 e = vec3(1.0e9);
    if (abs(l.x) > 1.0e-4) e.x = (f12.x + corner.x + (l.x > 0.0 ? 127.0 : 1.0) - o.x) / l.x;
    if (abs(l.y) > 1.0e-4) e.y = ((l.y > 0.0 ? yMin + 128.0 : yMin) - o.y) / l.y;
    if (abs(l.z) > 1.0e-4) e.z = (f12.z + corner.y + (l.z > 0.0 ? 127.0 : 1.0) - o.z) / l.z;
    return max(min(min(e.x, min(e.y, e.z)), float(WALK) / (abs(l.x) + abs(l.y) + abs(l.z))), 0.0);
}

// Past the block map: the far map's walk from o, then the heightfield from where that ends (or from o, where the far
// map isn't read or isn't there)
float beyond(vec3 o, vec3 l, float skip, float noise, bool ultra) {
    bool more;
    if (!farMap()) return heightRay(o, l, noise, ultra, false, more);
    int status;
    float tOut;
    float through = farRay(o, l, skip, status, tOut);
    if (status == 0) return 0.0;
    return through * heightRay(status == 2 ? o : o + l * tOut, l, noise, ultra, false, more);
}

// how much of the sun reaches a surface point: the heightfield's answer, and where that is shade and the block map
// reaches, the map's (the heightfield can only say "something stands between"; the map knows what and where)
// Development (/awaken lens paths on; frame texel 34 w): which part answered a pixel, marked over its answer v in the
// world's own blocks so it holds still. 1 the lit air was sure of the sun (lines across the sun's way, one a block),
// 2 the walk left the map in the open (lines along the sun's way), 3 the walk met a section not read yet and the
// heightfield's answer stands (a checker, four squares a block). A mark is the answer turned over, so it shows on
// lit ground and in shade alike. Plain: the heightfield found nothing over the ray, or a block stopped the walk.
float pathMark(int path, vec3 o, float v) {
    if (F(34).w < 0.5) return v;
    bool mark = path == 1 ? fract(o.x) < 0.12 : path == 2 ? fract(o.z) < 0.12 : (fract(o.x * 2.0) < 0.5) != (fract(o.z * 2.0) < 0.5);
    return mark ? (v > 0.5 ? 0.0 : 1.0) : v;
}

float sunVisibility(vec3 p, vec3 n, vec3 l, float noise, bool ultra) {
    vec4 f12 = F(12), f15 = F(15);
    if (f15.x < 1.0) return 1.0;
    vec3 o = p + vec3(f12.x, f12.y, f12.z) + n * 0.06;
    bool leafy, more;
    float vis = heightRay(o, l, noise, ultra, true, leafy);
    if (F(17).z <= 0.0) return vis;
    vec2 inMap = o.xz - f12.xz - mapCorner();
    if (inMap.x < 2.0 || inMap.y < 2.0 || inMap.x > 126.0 || inMap.y > 126.0) {
        // outside the block map: the far map's walk, where the heightfield finds anything over the ray at all and
        // the point lies in the far map's window
        vec2 inFar = o.xz - f12.xz - vec2(F(30).w, F(31).w);
        if (!farMap() || (vis > 0.97 && !leafy) || inFar.x < 4.0 || inFar.y < 4.0 || inFar.x > 252.0 || inFar.y > 252.0
                || o.y < F(32).w || o.y > F(32).w + 255.0) return vis;
        // (not the first block of the way: a cell is two blocks across, and the ground beside the point is in its own)
        int farStatus;
        float farOut;
        float farThrough = farRay(o, l, 1.0, farStatus, farOut);
        if (farStatus == 0) return 0.0;
        if (farStatus == 2) return vis;
        return farThrough * heightRay(o + l * farOut, l, noise, ultra, false, more);
    }
    // inside the map: lit only if nothing at all stands over the ray, leaves at any height included (the walk knows
    // where they really are)
    if (vis > 0.97 && !leafy) return vis;
    // (most of a forest's pixels come this far; the lit air settles the ones well inside the sun or the shade for
    // three texture reads, and the walk is left for the edges, where its exactness shows)
    // (sure of the sun: the walk would have found nothing, so only the walk is saved, and the land beyond the map has
    // its say from where the walk would have stopped. The lit air never shades a surface itself. It reaches farther
    // than the walk does, so where it called a block dark and the walk beside it found the sun, the difference lay on
    // open grass as a patchwork under a low sun, Euan 2026-11-10; a pixel in shade walks, and meets what shades it
    // within a few blocks anyway)
    if (litSure(p + n * 0.55) == 1.0) return pathMark(1, o, beyond(o + l * walkEnd(o, l), l, 0.0, noise, ultra));
    int status;
    float tOut;
    float through = blockRay(o, l, status, tOut);
    if (status == 2) return pathMark(3, o, vis);
    if (status == 0) return 0.0;
    // out of the map in the open: the land beyond it, by the far map and then the heightfield from there
    return pathMark(2, o, through * beyond(o + l * tOut, l, 0.0, noise, ultra));
}

// Leaves: the heightfield knows a column's top with its leaves (r) and without (g), not where the crown ends below.
// It took the whole column under r for leaves, a curtain to the ground: every ray that passed under a crown was
// shaded, and since the block map (which knows better) reaches 46 blocks from the camera, a tall tree's shade on the
// ground was a slab with a straight edge that came and went as the camera moved (Euan, playing, 11 Nov: "the shadow
// moves as I move ... if I move completely to the left of the tree, then the entire shadow of the tree basically
// disappears"). A crown now shades only in its top CANOPY blocks, so beyond the map a tree casts its crown's shadow
// and light passes under it, which is what the map finds too.
float heightRay(vec3 o, vec3 l, float noise, bool ultra, bool roof, out bool leafy) {
    leafy = false;
    vec4 f12 = F(12), f15 = F(15);
    ivec2 own = ivec2(floor(o.xz));
    float maxH = f15.y, trust = f15.z, horiz = max(length(l.xz), 1.0e-3), vis = 1.0;
    ivec2 c = own, dirI = ivec2(l.x >= 0.0 ? 1 : -1, l.z >= 0.0 ? 1 : -1);
    vec2 per = 1.0 / max(abs(l.xz), vec2(1.0e-5));
    vec2 next = (vec2(c) + max(vec2(dirI), 0.0) - o.xz) * vec2(dirI) * per;
    // Under solid: in the ground, in a cave, or under a roof with open air beneath it (a bridge, an arch, a table).
    // Only the last can see the sun, and only where the ray leaves this column still under the roof's underside;
    // from the others nothing (the composite fades its model out there). A heightmap counts a slab, a stair or a fence
    // as its whole block, so its own column only shadows from a block above the surface
    if (roof) {
        vec4 top = heightAll(own, 0);
        if (o.y < top.g - 1.0 && !(o.y >= top.a - 1.0 && o.y + max(l.y, 0.0) * min(next.x, next.y) < top.b)) return 0.0;
    }
    // near: every column the ray crosses, between where it comes in and where it goes out (it climbs), so a step's or
    // a wall's shadow is whole to its far edge. Point samples spaced like the far march skip the thin wedge of a low
    // occluder near the end of its shadow, which faded it and cut it off in a stepped edge (Euan 2026-10-29)
    // A column is solid under its floor (a) and from its roof's underside up to its top (b to g): for most columns
    // that is everything under the top. Before the heightfield held b and a, a roof with air under it was solid to
    // the ground, and beyond the block map a bridge, an arch or a table threw a slab's shadow from its feet (the
    // stone table of run 43001230601), right only once the camera came within 46 blocks.
    float t = 0.0;
    int nearCells = ultra ? 16 : 10;
    for (int i = 0; i < nearCells; i++) {
        if (next.x < next.y) { t = next.x; next.x += per.x; c.x += dirI.x; }
        else { t = next.y; next.y += per.y; c.y += dirI.y; }
        float tOut = min(next.x, next.y);
        float lo = o.y + l.y * (l.y >= 0.0 ? t : tOut), hi = o.y + l.y * (l.y >= 0.0 ? tOut : t);
        if (lo > maxH) return vis;
        if (max(abs(float(c.x) + 0.5 - f12.x), abs(float(c.y) + 0.5 - f12.z)) > trust) return vis;
        vec4 h = heightAll(c, 0);
        if (lo < h.a || (hi >= h.b && lo < h.g)) return 0.0;
        if (lo < h.r) {
            leafy = true;
            // (through the crown itself: where the ray, between coming in and going out, is in its top blocks)
            if (hi > h.r - crownDepth(h)) {
                vis *= exp(-0.9 * (tOut - t));
                if (vis < 0.03) return 0.0;
            }
        }
    }
    // far: steps each longer than the last from there, the same reach as before
    int steps = ultra ? 24 : 15;
    float base = ultra ? 0.25 : 0.32, grow = ultra ? 0.13 : 0.23;
    t += (base + t * grow) * noise;
    for (int i = 0; i < steps; i++) {
        vec3 q = o + l * t;
        if (q.y > maxH) break;
        vec2 rel = q.xz - f12.xz;
        if (max(abs(rel.x), abs(rel.y)) > trust) break;
        float s = base + t * grow;
        // far steps test a whole cell (the max mips), so a long stride can't step over a ridge
        int lvl = int(clamp(log2(s * horiz) + 0.5, 0.0, 4.0));
        ivec2 cell = ivec2(floor(q.xz));
        vec4 h = heightAll(cell, lvl);
        if (lvl == 0 && cell == own) h.rga -= 1.0;
        float up = q.y + max(l.y, 0.0) * s;
        if (q.y < h.a || (up >= h.b && q.y < h.g)) return 0.0;
        if (q.y < h.r) {
            leafy = true;
            if (up > h.r - crownDepth(h)) {
                vis *= exp(-0.9 * s);
                if (vis < 0.03) return 0.0;
            }
        }
        t += s;
    }
    return vis;
}

// the depth buffer, a short way toward the light: what the heightfield can't hold (mobs, grass, slabs, block edges)
float contactShadow(vec3 p, vec3 n, vec3 l, float dist, float noise, mat4 vp, bool z01, float zFar, bool ultra) {
    int steps = ultra ? 12 : 6;
    float len = clamp(0.35 + dist * 0.025, 0.35, 3.0);
    // the Air (frame texel 22 w): near the eye the small things cast whole shadows, a block and a half long (a flower,
    // a plate on the path, a mob), walked in more and even steps so every screen pixel of a texel agrees
    if (F(22).w > 0.0) {
        steps = ultra ? 24 : 16;
        len = max(len, 1.6 * (1.0 - smoothstep(26.0, 60.0, dist)));
        noise = 0.5;
    }
    vec3 o = p + n * (0.02 + dist * 0.0008);
    for (int i = 0; i < steps; i++) {
        float t = (float(i) + noise) / float(steps);
        vec3 s = project(vp, o + l * (len * t * t + 0.01), z01);
        if (s.x <= 0.0 || s.y <= 0.0 || s.x >= 1.0 || s.y >= 1.0) break;
        float sd = texelFetch(DepthSampler, ivec2(s.xy * InSize), 0).r;
        float zs = linearDepth(sd, zFar, z01), zq = linearDepth(s.z, zFar, z01);
        float delta = zq - zs;
        // something stands between this point and the light: in front of the ray, but not a wall far in front of it.
        // Toward the screen's edge a hit counts less, so contact shadows fade out there rather than end in a line where
        // the march runs off the screen (what's off it is the heightfield's, which never depends on the screen)
        if (zs > 1.25 && delta > 0.015 + zq * 0.003 && delta < 0.5 + zq * 0.02)
            return 1.0 - smoothstep(0.0, 0.04, min(min(s.x, s.y), min(1.0 - s.x, 1.0 - s.y)));
    }
    return 1.0;
}

// how open the hemisphere above the surface is, about a block around it
float ambient(vec3 p, vec3 n, float noise, float noise2, mat4 vp, bool z01, float zFar, bool ultra) {
    int samples = ultra ? 10 : 6;
    const float radius = 0.9;
    vec3 t1 = normalize(abs(n.y) < 0.9 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
    vec3 t2 = cross(n, t1);
    vec3 o = p + n * 0.03;
    float occ = 0.0;
    for (int i = 0; i < samples; i++) {
        float fi = (float(i) + noise) / float(samples);
        float a = (float(i) + noise2) * 2.39996;
        float r = sqrt(fi);
        vec3 h = vec3(cos(a) * r, sin(a) * r, sqrt(max(0.0, 1.0 - fi)));
        float len = radius * mix(0.2, 1.0, fract(fi * 7.31 + noise2) );
        vec3 s = project(vp, o + (t1 * h.x + t2 * h.y + n * h.z) * len, z01);
        if (s.x <= 0.0 || s.y <= 0.0 || s.x >= 1.0 || s.y >= 1.0) continue;
        float sd = texelFetch(DepthSampler, ivec2(s.xy * InSize), 0).r;
        float zs = linearDepth(sd, zFar, z01), zq = linearDepth(s.z, zFar, z01);
        float delta = zq - zs;
        if (zs > 1.25 && delta > 0.02) occ += 1.0 - smoothstep(radius, radius * 2.5, delta);
    }
    return 1.0 - occ / float(samples);
}

// whether a point in the air (camera-relative) is in sunlight: 1 lit, 0 in a terrain shadow, dimmer under leaves
float litAt(vec3 w, vec4 f12, float cell) {
    vec4 s = texelFetch(SunmapSampler, ivec2(mod(w.xz + f12.xz, 1024.0) / cell), 0);
    float y = w.y + f12.y, solid = unpackHeight(s.rg), all = unpackHeight(s.ba);
    return smoothstep(solid - 0.5, solid + 0.5, y) * (y < all ? 0.35 : 1.0);
}

// sunlight scattered toward the eye by a thin mist along the view ray v (to distance d), wherever the air is lit: the
// mist lies on the land, thickest at the height of the ground under the camera and thinning above it over the look's
// mistHeight (so valleys fill and the air over a mountain stays clear); forward-peaked toward the sun (Henyey-Greenstein)
float volume(vec3 v, float d, vec3 l, float strength, float jitter, bool ultra) {
    vec4 f12 = F(12), v19 = P(19);
    float cell = 1024.0 / float(textureSize(SunmapSampler, 0).x);
    int steps = ultra ? 16 : 8;
    float ground = heightAt(ivec2(floor(f12.xz)), 0).g;
    vec4 cp = cloudParams();
    float floorY = ground > -1000.0 ? max(ground, 40.0) : 63.0;
    float scaleH = max(v19.g * 256.0, 4.0), acc = 0.0, from = 0.0;
    const float g = 0.6;
    float mu = dot(v, l), phase = 0.06 + 4.0 * (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    // (the Air: the low sun's air holds more light)
    float low = F(22).w > 0.0 ? 1.0 + 1.6 * (1.0 - smoothstep(0.1, 0.5, F(22).y)) * (1.0 - F(16).y) : 1.0;
    vec4 lc = litCam();
    if (lc.w > 0.0) {
        // the sun's rays: the first blocks of the ray go through the lit air, which knows the canopy's gaps, the far
        // side of a trunk and a doorway from the open, in steps short enough to keep them apart. Seen from the side
        // the air close by holds a share the far mist's forward glow leaves out (a shaft is something near you)
        float near = min(d, 60.0), dn = near / float(ultra ? 18 : 12), got = 0.0;
        for (int i = 0; i < (ultra ? 18 : 12); i++) {
            vec3 w = v * ((float(i) + jitter) * dn);
            vec2 ln = litNear(w, lc, fract(jitter * 7.31 + float(i) * 0.618) - 0.5);
            float lit = litAt(w, f12, cell);
            if (ln.y > 0.0) {
                // (the volume knows its own blocks; the land beyond it is the sun map's, asked where the sun's ray
                // leaves the volume, so far shade in the air is the same far shade the ground gets)
                vec3 q = w + lc.xyz, e = vec3(1.0e9);
                if (abs(l.x) > 1.0e-4) e.x = ((l.x > 0.0 ? 128.0 : 0.0) - q.x) / l.x;
                if (abs(l.y) > 1.0e-4) e.y = ((l.y > 0.0 ? 128.0 : 0.0) - q.y) / l.y;
                if (abs(l.z) > 1.0e-4) e.z = ((l.z > 0.0 ? 128.0 : 0.0) - q.z) / l.z;
                float known = ln.x * litAt(w + l * max(min(e.x, min(e.y, e.z)), 0.0), f12, cell);
                lit = mix(lit, known, ln.y);
            }
            got += lit * exp(-max(w.y + f12.y - floorY, 0.0) / (scaleH * 1.5));
        }
        // (into the sun it weighs as the far mist does: more there, with the screen's own rays on top, whitened the
        // whole sky round a low sun)
        acc = got * dn * (1.0 + 0.30 / max(phase, 0.12));
        from = near;
        steps = ultra ? 10 : 5;
    }
    if (d > from + 1.0) {
        float dt = (d - from) / float(steps), far = 0.0;
        for (int i = 0; i < steps; i++) {
            vec3 w = v * (from + (float(i) + jitter) * dt);
            // (on Ultra the clouds' shadows cut the beams too)
            float cs = ultra ? mix(1.0, cloudShadow(w, l, cp, f12.y), 0.5) : 1.0;
            far += litAt(w, f12, cell) * cs * exp(-max(w.y + f12.y - floorY, 0.0) / scaleH);
        }
        acc += far * dt;
    }
    return 1.0 - exp(-acc * 0.0016 * strength * phase * low);
}

// last frame's answer for this surface (or sky direction): xyz the light, w how much of the 2x2 history saw the same
// distance (0: none of it)
vec4 history(vec3 p, bool sky, bool z01) {
    vec3 pp = (sky ? normalize(p) * 4096.0 : p) + F(13).xyz;
    vec3 s = project(frameMat(8), pp, z01);
    if (s.x <= 0.0 || s.y <= 0.0 || s.x >= 1.0 || s.y >= 1.0) return vec4(0.0);
    float want = sky ? 0.0 : packDistance8(length(pp));
    vec2 hp = s.xy * OutSize - 0.5;
    ivec2 b = ivec2(floor(hp));
    vec2 fr = hp - vec2(b);
    ivec2 hl = ivec2(OutSize) - 1;
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int k = 0; k < 4; k++) {
        ivec2 o = ivec2(k & 1, k >> 1);
        vec4 h = texelFetch(HistorySampler, clamp(b + o, ivec2(0), hl), 0);
        float w = (o.x == 1 ? fr.x : 1.0 - fr.x) * (o.y == 1 ? fr.y : 1.0 - fr.y)
                * (1.0 - smoothstep(1.5 / 255.0, 2.5 / 255.0, abs(h.a - want)));
        acc += h.rgb * w;
        wsum += w;
    }
    return wsum > 0.0 ? vec4(acc / wsum, wsum) : vec4(0.0);
}

void main() {
    bool z01 = F(16).x > 0.5, ultra = Quality > 1.5;
    ivec2 last = ivec2(InSize) - 1;
    ivec2 px = clamp(ivec2(texCoord * InSize), ivec2(0), last);
    float depth = texelFetch(DepthSampler, px, 0).r;
    // the frame's values not written yet (the near plane is never 0 once they are)
    if (F(16).w <= 0.0) {
        fragColor = vec4(1.0, 1.0, 0.0, 0.0);
        return;
    }
    bool sky = depth < 1.0e-7;
    vec2 uv = (vec2(px) + 0.5) / InSize;
    mat4 inv = frameMat(0);
    vec3 p = unproject(inv, uv, sky ? 1.0e-5 : depth, z01);
    float dist = sky ? 0.0 : length(p);
    vec4 p1 = P(1), f14 = F(14), f12 = F(12);
    float zFar = max(p1.r * 255.0 * 256.0 + p1.g * 255.0, 16.0);
    float noise = ign(gl_FragCoord.xy, f12.w), noise2 = ign(gl_FragCoord.yx + 17.0, f12.w + 31.0);
    float code = packDistance8(dist);
    vec4 f13 = F(13);
    bool interleave = F(17).y > 0.5 && f13.w > 0.5;
    vec4 hist = f13.w > 0.5 ? history(p, sky, z01) : vec4(0.0);
    if (interleave && (((int(gl_FragCoord.x) >> 3) + (int(gl_FragCoord.y) >> 3) + int(f12.w)) & 1) == 1 && hist.w > 0.4) {
        // this pixel's off frame, and last frame saw the same surface here: keep its answer
        fragColor = vec4(clamp(hist.rgb + (noise - 0.5) / 255.0, 0.0, 1.0), code);
        return;
    }
    float vis = 1.0, ao = 1.0, vol = 0.0;
    // the hand and the holo glass (a world plate over a creature) are never shadowed. The hand by its own depth where
    // Lens has it (parameter texel 26 r): by reach instead, a wall, a door or a ceiling within arm's length went
    // unshadowed with it, a disc of raw light that moved with the camera (Euan, playing, 11 Nov: "this spotlight effect")
    bool hand = P(26).r > 0.5 ? texture(HandSampler, texCoord).r > 0.0 : dist < 1.25;
    if (!sky && !hand && !onPlate(p)) {
        vec3 n = normalAt(px, p, depth, inv, z01, InSize);
        mat4 vp = frameMat(4);
        // (a face turned from the light is left to the composite, except inside the Sun Pass's block map, where the
        // block it belongs to shades it exactly)
        bool mapped = F(17).z > 0.0 && max(abs(p.x), abs(p.z)) < F(17).z - 1.0;
        if (f14.w > 0.0 && P(17).r > 0.0 && (mapped || dot(n, f14.xyz) > -0.1)) {
            // the sun as a small disc (about 1.4 degrees across): each frame's ray leans a little across it, so a
            // shadow's edge is a penumbra that widens away from what casts it, smoothed by the frames below the light
            // buffer's texels (a crisp edge at 960 x 540 would show as stairs of four pixels on a 4K screen)
            vec3 l = f14.xyz, a1 = normalize(cross(l, abs(l.z) < 0.9 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0)));
            float ang = ign(gl_FragCoord.xy + vec2(41.0, 11.0), f12.w + 53.0) * 6.2832, rad = 0.012 * sqrt(noise2);
            // the Air (Steady Light; frame texel 22 w): shadows in the texture's own pixels. A point on a block face asks
            // from the centre of its 1/16-block texel, so every screen pixel of that texel gets one answer and a
            // shadow's edge runs along texel edges, like the art it falls on; the sun's disc is narrower
            vec3 ps = p;
            float still = noise;
            if (F(22).w > 0.0) {
                vec3 an = abs(n);
                // a low sun's shadows are long and their edges wide: the disc grows as it sinks
                rad *= 0.2 + 2.8 * (1.0 - smoothstep(0.08, 0.42, f14.y)) * (1.0 - F(16).y);
                // (a block's face only. The texels are the world's, a sixteenth of a block on its own grid: a creature's
                // side is square to the world now and then, and its shade was then cut along a grid that is not its
                // skin's and does not move with it, stair-steps that crawled as it walked. Euan, playing, 11 Nov:
                // "actual artifacts in the shadows, where the shadows have jagged edges or like zigzags")
                if (max(an.x, max(an.y, an.z)) > 0.99 && (F(15).x < 1.0 || blockFace(p + vec3(f12.x, f12.y, f12.z), n))) {
                    vec3 w = p + vec3(f12.x, f12.y, f12.z), tex = floor(w * 16.0);
                    ps = mix((tex + 0.5) / 16.0, w, step(0.99, an)) - vec3(f12.x, f12.y, f12.z);
                    // close to the eye a texel is many screen pixels, and its light must hold still from frame to
                    // frame: it asks along the sun's own line (no disc: a disc sampled once a texel made a shadow's edge
                    // a dither of light and dark texels, a broken grid, that changed every frame: the shimmer, Euan
                    // 2026-11-10), with a march jitter of its own that never changes. A shadow there is each texel
                    // wholly lit or wholly shaded. From 10 to 18 blocks the disc and the frame's noise come back,
                    // and the frames soften the edge as before
                    float crisp = 1.0 - smoothstep(10.0, 18.0, dist);
                    rad *= 1.0 - crisp;
                    if (crisp > 0.0) still = fract(sin(dot(tex, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
                }
            }
            l = normalize(l + (a1 * cos(ang) + cross(l, a1) * sin(ang)) * rad);
            vis = sunVisibility(ps, n, l, still, ultra);
            if (vis > 0.0 && dist < 96.0) vis *= contactShadow(ps, n, f14.xyz, dist, noise2, vp, z01, zFar, ultra);
            // the clouds' moving shadows
            if (vis > 0.0) vis *= cloudShadow(p, f14.xyz, cloudParams(), f12.y);
            // and a storm cell's, long across the land when the sun is low
            if (vis > 0.0) vis *= stormShadow(p, f14.xyz, f12.y);
        }
        if (dist < 96.0) ao = ambient(p, n, noise, noise2, vp, z01, zFar, ultra);
    }
    float strength = f14.w * P(6).a * P(19).r;
    if (strength > 0.0 && F(15).x > 0.0) vol = volume(normalize(p), sky ? (ultra ? 320.0 : 192.0) : min(dist, ultra ? 320.0 : 192.0), f14.xyz, strength, noise2, ultra);

    vec3 res = vec3(vis, ao, vol);
    // last frame's answer for this surface (or this sky direction): reprojected, its 2x2 texels kept only where they saw
    // the same distance
    if (hist.w > 0.02) {
        vec3 fresh = ultra ? vec3(0.1, 0.1, 0.15) : vec3(0.14, 0.14, 0.2);
        // marching every other frame: the same response over time as a march every frame (1 - (1 - f)^2)
        if (interleave) fresh = 1.0 - (1.0 - fresh) * (1.0 - fresh);
        float k = smoothstep(0.02, 0.4, hist.w);
        res = mix(hist.rgb, res, mix(vec3(1.0), fresh, k));
    }
    // dithered into 8 bits, so slow changes still move
    res += (noise - 0.5) / 255.0;
    fragColor = vec4(clamp(res, 0.0, 1.0), code);
}
