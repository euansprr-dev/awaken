// AWAKEN Lens, the Air's water (SPEC §22 "Glasswater"; Euan, 11 Nov: "I don't want the usual blue, like, flat blue
// style ... still feels like MC, but doesn't look at all like what it was before ... the fantasy style"). Still water
// under the Air is drawn again by the composite:
//   - a body in jewel tones from the place's own water colour: clear aqua where you look down into it, deep indigo far
//     out, the game's picture of the bed still showing through near you;
//   - a mirror of the Air: its sky the way the reflected ray looks, its block clouds, and the land that stands over
//     the far shore, rippling gently on slow wind lanes;
//   - lace: thin soft lines of light that drift on the lanes' crests;
//   - the sun's road, and the moon's: a long soft glitter with a fine sparkle in it, and by night a scatter of stars;
//   - with the heightfield (Cinematic, Ultra): a soft pale line where water meets land, breathing, and aqua shallows.
// All of it is smooth. The first build drew it on the block's sixteenth grid (a mirror leaning a texel at a time,
// glints a texel square): he saw it and said "I'm really not a fan of the pixelized reflections ... It looks more
// glitchy than anything else", and a stepped mirror is what a broken upscale looks like. What keeps it this game's
// is what it mirrors: block clouds, block shores, the place's own water colour.
// Nothing moves the surface, bends what is under it or is simulated. Only water pixels pay: arithmetic and two or
// three reads on Lite, the mirrored cloud walk and four heights more on Cinematic.
// Needs lens_frame, lens_air, lens_cloud_cells, lens_storm_blocks, lens_cloud_walk, lens_height (LENS_LIGHT) and the
// composite's P, luma, viewDistance, lensLite before it.

const float WATER_TOP = 0.8888889;   // where a still fluid's surface stands in its block (the game's fluid renderer)

float waterHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

// smooth noise, 0..1
float waterNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(waterHash(i), waterHash(i + vec2(1.0, 0.0)), f.x), mix(waterHash(i + vec2(0.0, 1.0)), waterHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Whether this pixel is open still water seen from above: a level surface at the height a still fluid stands. p: the
// pixel, camera-relative. quick: the composite's normal (0 across an edge). raw: the game's own colour here (lava
// stands at the same height, and glows).
bool waterHere(vec3 p, vec3 quick, vec3 raw) {
    if (p.y > -0.02) return false;
    if (abs(fract(p.y + F(12).y) - WATER_TOP) > 0.004) return false;
    if (quick.y < 0.9 && dot(quick, quick) > 0.5) return false;
    return !(raw.r > 0.5 && raw.r > raw.b * 1.6);
}

// c: the pixel as Lens has lit it. raw: as the game drew it. p: camera-relative. foot: how many blocks one screen pixel
// spans on the water here (from the composite's derivatives). sunVis: how much of the sun reaches it (1 on Lite).
// rdEnd: the render distance. k: how much of this to draw (the look's `water`, the game's fog, cover).
vec3 airWater(vec3 c, vec3 raw, vec3 p, float foot, float sunVis, float rdEnd, float k) {
    vec4 f12 = F(12), f14 = F(14), f16 = F(16), f22 = F(22);
    bool z01 = f16.x > 0.5, lite = lensLite();
    vec3 w = p + f12.xyz;
    float d = length(p), t = F(17).x, rain = f16.z;
    vec3 V = p / d;
    vec3 hr = airHour(f22.y);
    // fine detail (the sparkle, the lace) shows while it is about a screen pixel or more, and gives way to its own
    // average beyond: nothing smaller than a pixel is left to flicker far out
    vec2 q = w.xz;
    float fine = 1.0 - smoothstep(0.045, 0.16, foot);

    // wind lanes: long bands that drift across the water (about nine blocks by three), and the ripples that ride them:
    // three slow swells and a little chop. They only lean the mirror; the surface stays where it is
    float lane = waterNoise(q * vec2(0.11, 0.33) + vec2(t * 0.050, -t * 0.020));
    vec2 tilt = vec2(0.80, 0.60) * cos(dot(q, vec2(0.80, 0.60)) * 2.1 + t * 1.10)
              + vec2(-0.45, 0.89) * cos(dot(q, vec2(-0.45, 0.89)) * 3.4 - t * 1.45) * 0.7
              + vec2(0.96, -0.28) * cos(dot(q, vec2(0.96, -0.28)) * 5.3 + t * 1.90) * 0.45;
    tilt *= 0.012 + 0.016 * lane;
    tilt += (vec2(waterNoise(q * 1.6 + vec2(t * 0.35, t * 0.22)), waterNoise(q * 1.6 + vec2(17.3 - t * 0.30, 5.1 + t * 0.27))) - 0.5) * (0.020 + 0.030 * lane);
    if (fine > 0.0) tilt += (vec2(waterNoise(q * 5.0 - vec2(t * 0.5, -t * 0.4)), waterNoise(q * 5.0 + vec2(9.1 + t * 0.45, 3.3))) - 0.5) * 0.022 * fine;
    tilt *= 1.0 + 1.2 * rain;
    vec3 N = normalize(vec3(tilt.x, 1.0, tilt.y));
    vec3 R = reflect(V, N);
    vec3 Rs = normalize(vec3(R.x, max(R.y, 0.02), R.z));

    // how much of a mirror it is: little straight down, nearly all toward the horizon (a wider curve than glass's, so
    // a lake across the way is already sky)
    float cosv = max(-V.y, 0.0);
    float fres = mix(0.05, 0.86, pow(1.0 - cosv, 2.6));

    // --- the mirror: the Air's sky, its clouds, the land over the far shore
    vec3 refl = airSky(Rs, f22.xyz, rain);
    vec4 cp = cloudParams();
    if (cloudBlocks() && cp.x > 0.0) {
        if (lite) {
            // Lite: a block cloud's image in still water is its own footprint, one read where the mirrored ray meets
            // the layer's underside
            float up = cp.x - cloudUnder() - w.y;
            if (up > 0.0) {
                float tt = up / Rs.y;
                vec2 at = p.xz + Rs.xz * tt;
                float cell = cloudCell(floor((at + cp.zw) / CLOUD_CELL), cp.y);
                if (cell > 0.0 && tt < CLOUD_NEAR + 600.0 && (stormCount() == 0 || stormClearing(at) > 0.5)) {
                    vec3 over = airSky(vec3(0.0, 1.0, 0.0), f22.xyz, rain);
                    vec3 under = (mix(mix(over, refl, 0.55), vec3(1.0), 0.52 * hr.x) * (0.80 + 0.14 * hr.x + 0.25 * hr.y) + vec3(0.020, 0.028, 0.050) * hr.z) * 0.88;
                    float far = smoothstep(260.0, 1500.0, tt + d), gone = max(smoothstep(520.0, 1500.0, tt + d) * 0.6, smoothstep(CLOUD_NEAR + 320.0, CLOUD_NEAR + 580.0, tt));
                    refl = mix(refl, mix(under, airFog(Rs, f22.xyz, rain), far * 0.9), 1.0 - gone);
                }
            }
        } else {
            // the clouds themselves, walked from the camera's image under the surface: block for block the sky's
            vec4 cb = cloudNearFrom(Rs, 1.0e9, 2.0 * max(-p.y, 0.0));
            refl = refl * cb.a + cb.rgb;
        }
    }
    // the land: what stands the same angle over the horizon, read from the frame where the mirrored ray looks (true
    // for anything far off; a thing at the water's edge would sit a little wrong, so the near is left to the sky)
    vec3 sp = project(frameMat(4), Rs * 1000.0, z01);
    if (sp.x > 0.0 && sp.y > 0.0 && sp.x < 1.0 && sp.y < 1.0) {
        float sd = texture(DepthSampler, sp.xy).r;
        if (sd > 1.0e-7) {
            vec4 p1 = P(1);
            float ld = viewDistance(sd, max(p1.r * 255.0 * 256.0 + p1.g * 255.0, 16.0), z01);
            vec2 edge = min(sp.xy, 1.0 - sp.xy);
            float on = smoothstep(9.0, 26.0, ld) * smoothstep(0.0, 0.05, min(edge.x, edge.y));
            if (on > 0.0) {
                // as far land looks through the Air: into the sky's own colour with distance
                float tl = ld / rdEnd;
                vec3 land = mix(texture(InSampler, sp.xy).rgb * 0.82, airFog(Rs, f22.xyz, rain), 1.0 - exp(-tl * tl * 3.2));
                refl = mix(refl, land, on);
            }
        }
    }

    // --- the shore (with the heightfield): how far this texel is from land, in blocks
    float shore = 9.0;
#ifdef LENS_LIGHT
    if (!lite && F(15).x >= 1.0 && max(abs(p.x), abs(p.z)) < F(15).x * 0.5 - 3.0) {
        ivec2 col = ivec2(floor(w.xz));
        vec2 fq = fract(w.xz);
        float lvl = floor(w.y) + 0.75;
        // a neighbour column is shore where something stands in it over this water: a bank, a cliff, a lily pad (and a
        // bridge overhead, which the heightfield can't tell from a cliff: the water under its edge ripples too)
        float gx0 = heightAt(col + ivec2(-1, 0), 0).g, gx1 = heightAt(col + ivec2(1, 0), 0).g;
        float gz0 = heightAt(col + ivec2(0, -1), 0).g, gz1 = heightAt(col + ivec2(0, 1), 0).g;
        if (gx0 - lvl > 0.1) shore = min(shore, fq.x);
        if (gx1 - lvl > 0.1) shore = min(shore, 1.0 - fq.x);
        if (gz0 - lvl > 0.1) shore = min(shore, fq.y);
        if (gz1 - lvl > 0.1) shore = min(shore, 1.0 - fq.y);
    }
#endif
    float shallows = 1.0 - smoothstep(0.0, 1.0, shore);

    // --- the body: jewel tones from the place's own water. The hue is the game's (a marsh stays dark, a steppe pale),
    // drawn toward aqua where the water is looked into and toward indigo where it runs deep and far
    vec3 hue = raw / max(max(raw.r, max(raw.g, raw.b)), 0.06);
    vec3 clear = mix(hue, vec3(0.42, 1.0, 0.90), 0.55), deep = mix(hue, vec3(0.16, 0.24, 0.86), 0.55) * 0.40;
    float far = clamp(smoothstep(0.20, 0.92, 1.0 - cosv) - 0.7 * shallows, 0.0, 1.0);
    // the light on it: the hour's, Lens's own shade (the same picture lit and unlit differ by nothing else), the
    // darker water a dark place's own
    float shade = clamp(luma(c) / max(luma(raw), 0.02), 0.25, 1.6);
    float hour = (0.17 + 0.67 * hr.x + 0.22 * hr.y) * (1.0 - 0.35 * rain);
    float murk = clamp(max(raw.r, max(raw.g, raw.b)) * 2.2, 0.25, 1.0);
    vec3 body = mix(clear * 0.80, deep, far) * hour * shade * murk;
    // wind lanes read on the body too: a crest a little lighter
    body *= 0.90 + 0.22 * lane;
    // near you the game's picture of the bed shows through it; far out the body is all there is
    vec3 into = mix(c, body, 0.52 + 0.33 * far);

    vec3 col = mix(into, refl, fres);

    // --- lace: thin soft lines of light drifting on the lanes' crests, long along the lanes and broken into dashes
    // (the light's colour is the Air's own for the hour, as its clouds take it: gold toward dusk)
    vec3 lightCol = f16.y > 0.5 ? vec3(0.6, 0.7, 0.95) * 0.7 : mix(vec3(1.0, 0.97, 0.92), vec3(1.0, 0.62, 0.42), hr.y * (1.0 - 0.6 * hr.x));
    if (fine > 0.0) {
        float lv = waterNoise(q * vec2(0.34, 1.25) + vec2(t * 0.070, t * 0.030)) + 0.4 * waterNoise(q * vec2(0.9, 2.6) - vec2(t * 0.050, -t * 0.060));
        float line = 1.0 - smoothstep(0.010, 0.060, abs(fract(lv * 2.0) - 0.5));
        float dash = smoothstep(0.36, 0.56, waterNoise(q * vec2(1.3, 0.5) + vec2(-t * 0.09, 3.7)));
        float crest = smoothstep(0.30, 0.62, lane);
        col += (vec3(0.80, 1.0, 0.96) * hour + lightCol * 0.25 * f14.w) * line * dash * crest * fine * 0.42 * shade;
    }

    // --- the road of the sun, or the moon: the light's own long reflection on the ripples, a wide soft glow under it,
    // and a fine sparkle that comes and goes along it. Laid over the water, in the hour's colour
    vec3 mirror = reflect(V, vec3(0.0, 1.0, 0.0));
    float road = pow(max(dot(mirror, f14.xyz), 0.0), 26.0);
    float light = min(f14.w * sunVis * 1.15, 1.0);
    if (light > 0.002) {
        float streak = pow(max(dot(R, f14.xyz), 0.0), 140.0);
        float spark = 0.0;
        if (fine > 0.0 && road > 0.01) {
            float a = waterNoise(q * 21.0 + vec2(t * 1.7, -t * 1.3)), b = waterNoise(q * 9.0 - vec2(t * 0.8, t * 0.6));
            spark = smoothstep(0.66, 0.90, a) * smoothstep(0.35, 0.75, b) * fine;
        }
        float g = clamp(streak * 1.6 + road * (0.22 + 1.7 * spark), 0.0, 1.0) * light * (0.55 + 0.45 * fres);
        col = mix(col, lightCol * 1.25, g);
    }
    // by night the water holds a scatter of stars: small, soft, slow to turn
    float dark = smoothstep(0.06, -0.20, f22.y) * (1.0 - rain);
    if (dark > 0.0 && fine > 0.2) {
        float st = smoothstep(0.90, 0.97, waterNoise(q * 7.0 + vec2(t * 0.05, 31.0))) * smoothstep(0.3, 0.8, waterNoise(q * 2.0 - vec2(11.0, t * 0.35)));
        col += vec3(0.62, 0.74, 1.0) * st * 0.55 * dark * (0.3 + 0.7 * fres) * fine;
    }

    // --- the waterline: a soft pale line that breathes in and out along the shore, a faint ring off it now and then
    if (shore < 2.0) {
        float breath = sin(t * 0.9 + waterNoise(q * 0.7) * 6.2832);
        float width = 0.07 + 0.045 * breath + 0.11 * waterNoise(q * 3.1 + vec2(t * 0.11, 0.0));
        float foam = 1.0 - smoothstep(width - 0.035, width + 0.035, shore);
        float ring = (1.0 - smoothstep(0.012, 0.045, abs(shore - (0.44 + 0.12 * breath)))) * 0.26 * smoothstep(0.62, 0.80, waterNoise(q * 2.2 + vec2(0.0, t * 0.35)));
        vec3 pale = (vec3(0.90, 0.98, 1.0) * hour + lightCol * 0.2 * light) * shade;
        col = mix(col, pale, max(foam * 0.74, ring) * (0.4 + 0.6 * fine));
    }
    return mix(c, col, k);
}
