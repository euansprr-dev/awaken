// AWAKEN Lens, the Rolling Storm in the Air's own clouds (SPEC §22 "Storm cells", the block storm; Euan, playing,
// 2026-11-11: "the custom storm is still the old clouds ... fix that so it's the new clouds and looks great without
// costing much extra performance"). The same cumulonimbus lens_storm.glsl shapes as soft vapour (a flat dark shelf, a
// leaning tower of turrets, flanking towers stepping down upwind, an overshooting dome and an anvil spreading
// downwind), built of the block clouds' own cells: twelve blocks across and seven high, solid, flat-faced, each face lit
// by where it looks. Walked once a ray, to the pixel and afresh every frame, inside the composite (and at half size in
// the bloom prefilter), as lens_cloud_walk.glsl walks the layer's clouds.
// What keeps it cheap: only a ray that enters the cell's bounds walks at all; a cell is a few lines of arithmetic and
// two hashes, no texture read; the blocks are solid, so the walk ends at the first one; and colours are worked out only
// for a ray that met one. It takes the soft storm's whole march out of the cloud buffer (only the rain is left there).
// The grid rides with the cell (its centre on the base), turned to the world's axes like every other block.
// Needs lens_frame.glsl, lens_storm_frame.glsl, lens_air.glsl and lens_cloud_cells.glsl before it.

// the soft storm's own measures (lens_storm.glsl has them for the vapour; that file needs the cloud noise, this doesn't)
const float SB_R = 95.0, SB_SHELF = 125.0, SB_DOWN = 340.0, SB_UP = 70.0, SB_SIDE = 185.0;
const vec4 SB_FLANK0 = vec4(-118.0, 26.0, 48.0, 170.0), SB_FLANK1 = vec4(-168.0, 48.0, 40.0, 118.0), SB_FLANK2 = vec4(-210.0, 66.0, 32.0, 78.0);
const vec3 SB_GRID = vec3(CLOUD_CELL, CLOUD_TIER, CLOUD_CELL);

float sbHash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// how far a turret swells here, 0..1: lumps three cells across (the cauliflower), each cell a little ragged
float sbPuff(vec3 ci) { return 0.72 * sbHash(floor(ci / 3.0) + 7.0) + 0.28 * sbHash(ci + 31.0); }

// whether grid cell ci of a storm is cloud: w downwind, grow 0..1 how far the cell has built (it rises from its base).
// Each part is first tried at its fullest (a turret swollen all the way): most cells a ray crosses are open sky and are
// out there, before the two hashes that say how far this cell's turret really swells.
bool sbSolid(vec3 ci, vec2 w, float grow) {
    vec3 q = (ci + 0.5) * SB_GRID;
    float h = q.y;
    if (h < -CLOUD_TIER || h > min(STORM_H + 12.0, mix(-10.0, STORM_H + 50.0, grow))) return false;
    vec2 aw = vec2(dot(q.xz, w), dot(q.xz, vec2(-w.y, w.x)));   // along the wind, across it
    if (h < 0.0) {
        // under the flat base some lumps hang one tier lower (a plain slab seen from below showed no blocks at all)
        float rd2 = dot(aw - vec2(8.0, 0.0), aw - vec2(8.0, 0.0));
        return rd2 < SB_SHELF * SB_SHELF * 0.72 && sbHash(floor(ci / vec3(2.0, 1.0, 2.0)) + 53.0) > 0.66;
    }
    float hn = clamp(h / STORM_H, 0.0, 1.0);
    const float full = 1.16;
    bool shelf = false, body = false, flank = false, dome = false, anvil = false;
    float rd2 = 0.0, br = 0.0, ra = 0.0;
    vec2 bx = vec2(0.0);
    if (h < 34.0) {
        // the shelf: a flat base wider than the tower, a few tiers deep at its middle and one at its ragged rim
        rd2 = dot(aw - vec2(8.0, 0.0), aw - vec2(8.0, 0.0));
        shelf = rd2 < SB_SHELF * SB_SHELF * 1.3;
    }
    if (h < STORM_H - 28.0) {
        // the body: narrowing as it towers, leaning downwind, its axis wandering and its girth swelling with height
        if (dot(aw - vec2(17.0, 0.0), aw - vec2(17.0, 0.0)) < 156.0 * 156.0) {
            bx = vec2(aw.x - hn * hn * 35.0 - 18.0 * sin(h * 0.031 + 1.3), aw.y - 16.0 * sin(h * 0.043 + 4.1));
            br = SB_R * (1.0 - 0.32 * pow(hn, 1.4)) * (0.92 + 0.14 * sin(h * 0.057 + 0.6));
            body = dot(bx, bx) < br * br * full * full;
        }
        // the flanking towers stepping down upwind
        flank = h < SB_FLANK0.w + 8.0 && dot(aw - vec2(-160.0, 45.0), aw - vec2(-160.0, 45.0)) < 112.0 * 112.0;
    }
    if (h > STORM_H - 72.0) {
        // the overshooting dome over the updraft, and the anvil: a flat plate far downwind, thick where the tower
        // spreads into it and one tier at its ragged rim
        dome = h > STORM_H - 52.0 && dot(aw + vec2(10.0, 0.0), aw + vec2(10.0, 0.0)) < 82.0 * 82.0;
        ra = length(vec2(aw.x / (aw.x > 0.0 ? SB_DOWN : SB_UP), aw.y / SB_SIDE));
        anvil = ra < 1.07 && h < STORM_H + 8.0 - 3.0 * ra;
    }
    if (!(shelf || body || flank || dome || anvil)) return false;
    float puff = sbPuff(ci), swell = 0.66 + 0.5 * puff;
    if (shelf) {
        float rd = sqrt(rd2) / (SB_SHELF * (0.84 + 0.3 * puff));
        if (rd < 1.0 && h < mix(32.0, 7.0, smoothstep(0.5, 1.0, rd))) return true;
    }
    if (body && dot(bx, bx) < br * br * swell * swell) return true;
    if (flank) {
        vec3 d = vec3(aw - SB_FLANK0.xy, max(h - (SB_FLANK0.w - SB_FLANK0.z), 0.0));
        if (dot(d, d) < SB_FLANK0.z * SB_FLANK0.z * swell * swell) return true;
        d = vec3(aw - SB_FLANK1.xy, max(h - (SB_FLANK1.w - SB_FLANK1.z), 0.0));
        if (dot(d, d) < SB_FLANK1.z * SB_FLANK1.z * swell * swell) return true;
        d = vec3(aw - SB_FLANK2.xy, max(h - (SB_FLANK2.w - SB_FLANK2.z), 0.0));
        if (dot(d, d) < SB_FLANK2.z * SB_FLANK2.z * swell * swell) return true;
    }
    if (dome) {
        vec3 o = vec3(aw.x + 10.0, aw.y, max(h - STORM_H + 40.0, 0.0) * 1.3);
        if (dot(o, o) < 70.0 * 70.0 * swell * swell) return true;
    }
    if (anvil) {
        float rr = ra + 0.14 * (puff - 0.5);
        if (rr < 1.0 && h < STORM_H + 8.0 - 3.0 * rr && h > STORM_H - 70.0 + 58.0 * pow(clamp(rr, 0.0, 1.0), 0.55)) return true;
    }
    return false;
}

// One storm cell on a ray: v the ray (unit, camera-relative), [t0, t1] the stretch to walk, cap the most cells to try.
// Returns the distance to the first block met (negative: none), n its face and ci its grid cell.
float sbWalk(vec3 v, float t0, float t1, vec3 org, vec2 w, float grow, int cap, out vec3 n, out vec3 ci) {
    vec3 p = org + v * t0;
    ci = floor(p / SB_GRID);
    vec3 sgn = vec3(v.x < 0.0 ? -1.0 : 1.0, v.y < 0.0 ? -1.0 : 1.0, v.z < 0.0 ? -1.0 : 1.0);
    vec3 inv = 1.0 / max(abs(v), vec3(1.0e-5));
    vec3 next = t0 + ((ci + max(sgn, 0.0)) * SB_GRID - p) * sgn * inv;
    vec3 step3 = SB_GRID * inv;
    // (the face the ray comes in by: the bounds' own side at first, then the side of each cell it steps across)
    n = abs(v.y) > 0.5 ? vec3(0.0, -sgn.y, 0.0) : (abs(v.x) > abs(v.z) ? vec3(-sgn.x, 0.0, 0.0) : vec3(0.0, 0.0, -sgn.z));
    float t = t0;
    for (int i = 0; i < 64; i++) {
        if (i >= cap || t >= t1) break;
        if (sbSolid(ci, w, grow)) return t;
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += step3.x;
            ci.x += sgn.x;
            n = vec3(-sgn.x, 0.0, 0.0);
        } else if (next.y < next.z) {
            t = next.y;
            next.y += step3.y;
            ci.y += sgn.y;
            n = vec3(0.0, -sgn.y, 0.0);
        } else {
            t = next.z;
            next.z += step3.z;
            ci.z += sgn.z;
            n = vec3(0.0, 0.0, -sgn.z);
        }
    }
    return -1.0;
}

// Every storm cell on a ray. v the ray (unit), scene the distance to what the pixel shows (1e9 for sky). Returns rgb the
// light the storm sends this way (premultiplied) and a how much of what is behind still shows; hit the distance to its
// first block (1e9: none), so the layer's clouds behind it can be left out.
vec4 stormBlocks(vec3 v, float scene, out float hit) {
    hit = 1.0e9;
    int count = stormCount();
    vec4 f16 = F(16);
    if (count == 0 || !cloudBlocks() || f16.w <= 0.0) return vec4(0.0, 0.0, 0.0, 1.0);
    vec4 f12 = F(12), f14 = F(14);
    float camY = f12.y;
    vec3 l = f14.xyz;
    int best = -1;
    vec3 bn = vec3(0.0, 1.0, 0.0), bci = vec3(0.0);
    for (int k = 0; k < 2; k++) {
        if (k >= count) break;
        vec4 a = stormA(k), b = stormB(k);
        if (a.w <= 0.02) continue;
        // its three bounds, each walked on its own, so a ray never crosses the open air between them cell by cell:
        // the shelf and the tower, the flanking towers upwind, and the dome and anvil over them
        vec3 org = vec3(-a.x, camY - a.z, -a.y);
        vec2 across = vec2(-b.y, b.x);
        for (int part = 0; part < 3; part++) {
            vec2 span = part == 0 ? stormBand(v, a.xy + b.xy * 14.0, 168.0, a.z - CLOUD_TIER, a.z + STORM_H - 26.0, camY)
                    : (part == 1 ? stormBand(v, a.xy - b.xy * 160.0 + across * 45.0, 114.0, a.z, a.z + SB_FLANK0.w + 10.0, camY)
                    : stormBand(v, a.xy + b.xy * 135.0, 232.0, a.z + STORM_H - 72.0, a.z + STORM_H + 14.0, camY));
            float t1 = min(span.y, min(scene, hit));
            if (t1 <= span.x) continue;
            vec3 n, ci;
            float t = sbWalk(v, span.x + 0.01, t1, org, b.xy, a.w, part == 1 ? 44 : 64, n, ci);
            if (t >= 0.0 && t < hit) {
                hit = t;
                best = k;
                bn = n;
                bci = ci;
            }
        }
    }
    if (best < 0) return vec4(0.0, 0.0, 0.0, 1.0);

    // its colours, for a ray that met a block
    vec4 a = stormA(best), b = stormB(best), c = stormC(best);
    vec4 f22 = F(22);
    vec3 hr = airHour(f22.y);
    vec3 w = v * hit;                                         // the point, camera-relative
    float hn = clamp((w.y + camY - a.z) / STORM_H, 0.0, 1.0);
    // how much of the cell stands between this block and the sun: three cells along the light, farther each time
    float shade = 0.0;
    for (int j = 0; j < 3; j++) {
        vec3 at = (bci + 0.5 + bn * 0.6) * SB_GRID + l * (10.0 + 22.0 * float(j) * float(j + 1));
        if (sbSolid(floor(at / SB_GRID), b.xy, a.w)) shade += 1.0;
    }
    float open = 1.0 - shade / 3.0;
    float face = max(dot(bn, l), 0.0);
    // the sky's light on it: dark and blue-grey at the shelf, paling to the layer's own white at the dome and anvil; a
    // face looking down is the darkest thing in the sky
    vec3 over = airSky(vec3(0.0, 1.0, 0.0), f22.xyz, f16.z), edge = airSky(normalize(vec3(v.x, 0.12, v.z)), f22.xyz, f16.z);
    vec3 ambient = mix(mix(over, edge, 0.55), vec3(1.0), 0.52 * hr.x) * (0.80 + 0.14 * hr.x + 0.25 * hr.y) + vec3(0.020, 0.028, 0.050) * hr.z;
    float rise = smoothstep(0.02, 0.62, hn);
    float look = bn.y < -0.5 ? 0.74 : (bn.y > 0.5 ? 1.0 : 0.86);
    // (each block a little its own tone, so the grid reads on a flat face; the shelf lighter toward its rim, where the
    // open sky is beside it)
    vec3 qh = (bci + 0.5) * SB_GRID;
    vec2 awh = vec2(dot(qh.xz, b.xy), dot(qh.xz, vec2(-b.y, b.x)));
    float rim = smoothstep(0.45, 1.05, length(awh - vec2(8.0, 0.0)) / SB_SHELF) * (1.0 - rise);
    float tone = (0.93 + 0.14 * sbHash(bci + 3.0)) * (1.0 + 0.45 * rim);
    vec3 sky = ambient * mix(vec3(0.30, 0.34, 0.43), vec3(0.80, 0.83, 0.90), rise) * look * (0.72 + 0.28 * open) * tone;
    // the sun on it: only on faces turned to it that the cell doesn't hide, strongest high on the tower
    vec3 lit = mix(vec3(1.0, 0.97, 0.92), vec3(1.0, 0.60, 0.44), hr.y * (1.0 - 0.6 * hr.x));
    vec3 sunCol = (f16.y > 0.5 ? vec3(0.50, 0.60, 0.90) * 0.10 : lit * (0.40 + 0.3 * hr.y)) * f14.w;
    vec3 L = sky + sunCol * (face * open * mix(0.35, 1.15, rise));
    // its lightning: the blocks round the flash glow from within, the whole cell a little
    if (c.w > 0.0) {
        vec3 d = w - c.xyz;
        float d2 = dot(d, d);
        L += vec3(0.72, 0.82, 1.0) * c.w * (1.1 * exp(-d2 / (60.0 * 60.0)) + 0.30 * exp(-d2 / (190.0 * 190.0)));
    }
    // into the haze with distance, as the layer's clouds go (a storm is a landmark: it keeps its shape farther)
    float far = smoothstep(320.0, 2600.0, hit);
    L = mix(L, airFog(v, f22.xyz, f16.z), far * 0.86);
    // a cell building or fading: its blocks come and go whole, the last of it thin
    float alpha = smoothstep(0.02, 0.14, a.w);
    return vec4(L * alpha, 1.0 - alpha);
}

// How much of the layer's clouds a storm leaves round itself (it clears the sky about it, as lens_clouds.fsh's far
// clouds do): 0 inside, 1 away. at: a point camera-relative on the xz plane.
float stormClearing(vec2 at) {
    int count = stormCount();
    float k = 1.0;
    for (int s = 0; s < 2; s++) {
        if (s >= count) break;
        vec4 a = stormA(s), b = stormB(s);
        k *= mix(1.0, smoothstep(330.0, 450.0, length(at - a.xy - b.xy * 60.0)), a.w);
    }
    return k;
}
