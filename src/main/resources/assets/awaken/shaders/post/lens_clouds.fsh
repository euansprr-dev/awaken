#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22, L4): volumetric clouds in place of the game's blocky ones. Each pixel's
// view ray is marched through the cloud layer (to where it meets terrain, if that's nearer), lit by a few steps toward
// the light: a silver lining toward the sun (a forward lobe), soft undersides, warm at golden hour, the game's own cloud
// colour (time of day, weather) as the sky's light on them. Distant cloud fades into the haze. Fresh noise each frame,
// blended into last frame's result reprojected by where the ray first met cloud.
// Storm cells (the Living Wilds' Rolling Storm, lens_storm.glsl) are marched in the same pass, band by band along the
// ray (rain, body, anvil), with lightning lighting them from inside; the layer's clouds thin out around them.
// Temporal (frame texel 17 y, the lensTemporal setting): each pixel marches one frame in four, a 2x2 pattern of 8x8-pixel
// tiles turning with the frame counter (whole tiles, so the GPU's SIMD groups skip together: a per-pixel pattern left a
// marching pixel in every group and saved nothing, perf tour 26 Oct); the other three keep last frame's clouds reprojected to where the ray enters the layer, and a
// march blends in more strongly so the clouds answer as fast as before. No history, off screen or lightning: it marches.
// Out: rgb the light the clouds send this way (premultiplied), a how much of what's behind still shows.
uniform sampler2D DepthSampler;     // the world's depth, full size (first input: InSize is the full frame)
uniform sampler2D CloudNoiseSampler;
uniform sampler2D HistorySampler;
uniform sampler2D FrameSampler;
uniform sampler2D ParamsSampler;

#include <awaken:lens_frame.glsl>
#include <awaken:lens_air.glsl>
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

float hg(float mu, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * mu, 1.5)); }

// how far a storm's lightning lights its cloud (blocks from the flash)
const float FLASH_REACH = 180.0;

// this frame's light, shared by every piece of the march
vec3 v, l, sunCol, ambient, stormSky;
float camY, phase, jitter;
bool ultra;
int nStorm;
// what the march has gathered: light, transmittance, where the ray first met cloud, the lightning in it
vec3 L;
float T, tHit, flashSeen;

// the layer's clouds thin to nothing around a storm cell (it clears the sky about it), so a ray's skipped piece
// inside the cell's bounds never cuts a hole in them
float clearing(vec3 w) {
    float k = 1.0;
    for (int s = 0; s < 2; s++) {
        if (s >= nStorm) break;
        vec4 a = stormA(s), b = stormB(s);
        k *= mix(1.0, smoothstep(330.0, 450.0, length(w.xz - a.xy - b.xy * 60.0)), a.w);
    }
    return k;
}

// how far along the ray to look for it in last frame's clouds, for a pixel that met no cloud itself (and for the three
// frames in four it doesn't march): where cloud would be on it (the layer's entry, a storm's body), or the terrain's
// own point when that is nearer. The camera has moved since, so a point at the wrong depth is found at the wrong place
// on screen: a block ahead (what a miss used to take) carried the history of whole storms across the screen as you
// walked, and a storm's bounding volume (hundreds of blocks in front of its surface) slid it against the world.
float historyDepth(float t0, float scene) {
    float at = t0 > 0.0 ? t0 : 150.0;
    for (int s = 0; s < 2; s++) {
        if (s >= nStorm) break;
        at = min(at, stormGuess(v, s, camY));
    }
    return max(min(at, scene), 1.0);
}

// The Air's blocks along the ray from t0 to t1, cell by cell (a walk of the twelve-block grid, so each block is met
// exactly where the ray enters and leaves it: its edges are true edges, with no steps to miss it or smear it). A block
// takes the sun on the face the ray came in by and through the cloud above it, and the sky's light by its height.
void blockPiece(float t0, float t1, vec4 cp) {
    const float sigma = 0.16;
    float oy = camY - (cp.x - 2.0);
    vec2 o = cp.zw;
    vec2 pxz = o + v.xz * t0;
    vec2 cell = floor(pxz / CLOUD_CELL);
    vec2 sgn = vec2(v.x < 0.0 ? -1.0 : 1.0, v.z < 0.0 ? -1.0 : 1.0);
    vec2 inv = 1.0 / max(abs(v.xz), vec2(1.0e-5));
    vec2 next = t0 + ((cell + max(sgn, 0.0)) * CLOUD_CELL - pxz) * sgn * inv;
    float t = t0, thick = 3.0 * CLOUD_TIER;
    int axis = 2;
    // whether the ray left the last cell still inside cloud (then the next block's near side is no face at all: the
    // same mass carries on, and it keeps the light of the face the ray first came in by)
    bool inside = false;
    vec3 nIn = vec3(0.0, 1.0, 0.0);
    for (int i = 0; i < 72; i++) {
        if (t >= t1 || T < 0.02) break;
        float tExit = min(min(next.x, next.y), t1);
        float val = cloudCell(cell, cp.y);
        if (val > 0.0) {
            float top = CLOUD_TIER * (1.0 + floor(min(val / 0.055, 2.0)));
            float ta = t, tb = tExit;
            bool capped = false;
            if (abs(v.y) > 1.0e-5) {
                float y0 = -oy / v.y, y1 = (top - oy) / v.y;
                float lo = min(y0, y1), hi = max(y0, y1);
                if (lo > ta) {
                    ta = lo;
                    capped = true;
                }
                tb = min(tb, hi);
            } else if (oy <= 0.0 || oy >= top) {
                tb = ta;
            }
            if (tb > ta) {
                // the face it came in by: the block's top or underside, or the side of the cell it crossed
                vec3 n = capped || axis == 2 ? vec3(0.0, v.y > 0.0 ? -1.0 : 1.0, 0.0)
                        : (axis == 0 ? vec3(-sgn.x, 0.0, 0.0) : vec3(0.0, 0.0, -sgn.y));
                if (inside && !capped) n = nIn;
                nIn = n;
                vec3 w = v * (ta + min((tb - ta) * 0.5, 2.5));
                float od = 0.0, s = 3.0, lt = 0.0;
                for (int k = 0; k < 3; k++) {
                    lt += s;
                    od += cloudBlock(w + l * lt, cp, camY) * s;
                    s *= 2.0;
                }
                float h = clamp((w.y + oy) / thick, 0.0, 1.0);
                float beer = max(exp(-od * sigma * 1.6), 0.30 * exp(-od * sigma * 0.35));
                float face = 0.40 + 0.60 * max(dot(n, l), 0.0);
                // the underside is the cloud's own shade; the sides and top take the sky by their height; and no two
                // blocks are quite one tone
                float skyOn = n.y < -0.5 ? 0.54 : (n.y > 0.5 ? 1.0 : 0.72 + 0.22 * h);
                float tone = 0.97 + 0.06 * cloudCellHash(cell + 3.7);
                vec3 S = (sunCol * beer * face * phase + ambient * skyOn) * tone;
                float a = 1.0 - exp(-sigma * (tb - ta));
                L += T * S * a;
                if (tHit < 0.0 && a > 0.05) tHit = ta;
                T *= 1.0 - a;
                inside = tb >= tExit - 1.0e-3;
            } else {
                inside = false;
            }
        } else {
            inside = false;
        }
        if (next.x < next.y) {
            cell.x += sgn.x;
            next.x += CLOUD_CELL * inv.x;
            axis = 0;
        } else {
            cell.y += sgn.y;
            next.y += CLOUD_CELL * inv.y;
            axis = 1;
        }
        t = tExit;
    }
}

void layerPiece(float t0, float t1, int steps, vec4 cp) {
    if (t1 <= t0 || steps <= 0 || T < 0.02) return;
    if (cloudBlocks()) {
        blockPiece(t0, t1, cp);
        return;
    }
    bool blocks = false;
    // (the Air's blocks are thin: denser, so one still reads as cloud, and lit by shorter steps)
    float sigma = blocks ? 0.075 : 0.03;
    float dt = (t1 - t0) / float(steps), base = cp.x - cloudUnder(), thick = cloudThick();
    int lightSteps = ultra ? 5 : 3;
    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;
        float t = t0 + (float(i) + jitter) * dt;
        vec3 w = v * t;
        float d = cloudDensity(w, cp, camY, ultra ? 2 : 1);
        if (d <= 0.004) continue;
        if (nStorm > 0) {
            d *= clearing(w);
            if (d <= 0.004) continue;
        }
        // light reaching this point through the cloud above it, toward the sun
        float od = 0.0, s = blocks ? 3.0 : 6.0, lt = 0.0;
        for (int k = 0; k < lightSteps; k++) {
            lt += s;
            od += cloudDensity(w + l * lt, cp, camY, 0) * s;
            s *= 2.0;
        }
        float h = clamp((w.y + camY - base) / thick, 0.0, 1.0);
        // two octaves of Beer's law: the second, weaker and longer, stands in for light scattered many times inside the
        // cloud, so its interior and base stay soft grey-white instead of storm-dark
        float beer = max(exp(-od * sigma * 1.4), 0.35 * exp(-od * sigma * 0.3));
        // powder: the thin edges facing the sun glow, dense cores darken (a cheap stand-in for multiple scattering)
        float powder = 1.0 - exp(-d * 6.0);
        vec3 S = sunCol * beer * phase * mix(1.0, powder, blocks ? 0.25 : 0.5) + ambient * (blocks ? 0.55 + 0.45 * h : 0.6 + 0.4 * h);
        float a = 1.0 - exp(-d * sigma * dt);
        L += T * S * a;
        if (tHit < 0.0 && a > 0.05) tHit = t;
        T *= 1.0 - a;
        if (T < 0.02) break;
    }
}

// one band of storm cell k along the ray: its body lit by steps toward the sun (dark and bruised underneath, warm where
// the sun reaches it), its rain a dark veil, both lit from inside by its lightning
void stormPiece(vec2 span, int steps, int k) {
    if (span.y <= span.x || T < 0.02) return;
    vec4 a = stormA(k), b = stormB(k), c = stormC(k);
    vec3 centre = vec3(a.x, a.z - camY, a.y);
    float groundH = b.z - a.z, dt = (span.y - span.x) / float(steps);
    int lightSteps = 3, detail = ultra ? 2 : 1;
    vec3 flashCol = vec3(0.86, 0.9, 1.0) * c.w;
    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;
        float t = span.x + (float(i) + jitter) * dt;
        vec3 w = v * t, q = w - centre;
        float d = stormCell(q, b.xy, b.w, a.w, detail);
        // the rain fades in from 16 blocks off: nearer, the game's own rain falls on you
        float rn = q.y < 0.0 ? stormRain(q, b.xy, groundH) * smoothstep(0.1, 0.4, a.w) * smoothstep(16.0, 40.0, t) : 0.0;
        if (d + rn <= 0.002) continue;
        // lightning lights the cell from within: its light reaches a point through the cloud between them (one sample
        // at the midpoint stands for it), so the core round the bolt glows, the glow falls off through the volume, and
        // the cell's outer shape stays dark and readable; nothing past FLASH_REACH (only rays near a flash march every
        // frame while it shows: see main)
        float fd = length(w - c.xyz);
        vec3 E = vec3(0.0);
        if (c.w > 0.0 && fd < FLASH_REACH) {
            float through = stormCell((w + c.xyz) * 0.5 - centre, b.xy, b.w, a.w, 0);
            E = flashCol * (3.0 * exp(-fd / 55.0) * exp(-through * fd * STORM_SIGMA * 0.35) * (1.0 - smoothstep(FLASH_REACH - 50.0, FLASH_REACH, fd)));
        }
        float ac = 1.0 - exp(-d * STORM_SIGMA * dt), ar = 1.0 - exp(-rn * STORM_RAIN_SIGMA * dt);
        float hn = clamp(q.y / STORM_H, 0.0, 1.0);
        vec3 col = vec3(0.0);
        if (ac > 0.0) {
            float od = 0.0, s = 6.0, lt = 0.0;
            for (int j = 0; j < lightSteps; j++) {
                lt += s;
                od += stormCell(q + l * lt, b.xy, b.w, a.w, 0) * s;
                s *= 2.3;
            }
            // little light scattered deep inside: its base and its shadowed side go dark
            float beer = max(exp(-od * STORM_SIGMA * 1.1), 0.22 * exp(-od * STORM_SIGMA * 0.22));
            float powder = 1.0 - exp(-d * 4.0);
            // the sky's light: dim and cold under the base (bruised), full on the tops; cool grey-blue, never the
            // horizon's sunset hue (that turned its shadowed side brown)
            float ao = 0.16 + 0.84 * smoothstep(0.0, 1.0, pow(hn, 0.8));
            vec3 sky = stormSky * ao * mix(vec3(0.80, 0.84, 1.0), vec3(1.0), hn);
            col = (sunCol * beer * phase * mix(1.0, powder, 0.5) + sky + E) * ac;
            if (c.w > 0.0) flashSeen = max(flashSeen, T * ac * c.w * exp(-fd / 120.0));
        }
        // rain: a grey veil, darker than the sky behind it, lit by the flash too
        col += (stormSky * 0.4 + E * 0.5) * ar;
        float at = 1.0 - (1.0 - ac) * (1.0 - ar);
        L += T * col;
        if (tHit < 0.0 && at > 0.05) tHit = t;
        T *= 1.0 - at;
        if (T < 0.02) break;
    }
}

// all of storm cell k along the ray, band by band in the ray's order; returns its span (for the layer to skip)
vec2 storm(int k, float scene) {
    vec4 a = stormA(k), b = stormB(k);
    vec2 axis = a.xy;
    float base = a.z;
    vec2 rain = stormBand(v, axis + b.xy * 12.0, STORM_RAIN + 60.0, b.z - 4.0, base - 6.0, camY);
    vec2 body = stormBand(v, axis - b.xy * 30.0, 240.0, base - 6.0, base + STORM_H - STORM_ANVIL, camY);
    vec2 anvil = stormBand(v, axis + b.xy * 150.0, 245.0, base + STORM_H - STORM_ANVIL, base + STORM_H + 46.0, camY);
    rain.y = min(rain.y, scene);
    body.y = min(body.y, scene);
    anvil.y = min(anvil.y, scene);
    int rs = ultra ? 12 : 10, bs = ultra ? 26 : 22, ans = ultra ? 12 : 10;
    int nr = clamp(int((rain.y - rain.x) / (ultra ? 16.0 : 18.0)) + 1, 3, rs);
    int nb = clamp(int((body.y - body.x) / (ultra ? 10.5 : 12.0)) + 1, 4, bs);
    int na = clamp(int((anvil.y - anvil.x) / (ultra ? 7.0 : 8.0)) + 1, 3, ans);
    if (v.y >= 0.0) {
        stormPiece(rain, nr, k);
        stormPiece(body, nb, k);
        stormPiece(anvil, na, k);
    } else {
        stormPiece(anvil, na, k);
        stormPiece(body, nb, k);
        stormPiece(rain, nr, k);
    }
    vec2 whole = stormBand(v, axis + b.xy * 60.0, 320.0, b.z - 4.0, base + STORM_H + 48.0, camY);
    return vec2(whole.x, min(whole.y, scene));
}

void main() {
    vec4 cp = cloudParams(), f12 = F(12), f14 = F(14), f16 = F(16);
    nStorm = stormCount();
    L = vec3(0.0);
    T = 1.0;
    tHit = -1.0;
    flashSeen = 0.0;
    if ((cp.x <= 0.0 && nStorm == 0) || f16.w <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    bool z01 = f16.x > 0.5;
    ultra = Quality > 1.5;
    ivec2 px = clamp(ivec2(texCoord * InSize), ivec2(0), ivec2(InSize) - 1);
    float depth = texelFetch(DepthSampler, px, 0).r;
    bool sky = depth < 1.0e-7;
    vec3 p = unproject(frameMat(0), (vec2(px) + 0.5) / InSize, sky ? 1.0e-5 : depth, z01);
    v = normalize(p);
    float scene = sky ? 1.0e9 : length(p), maxD = 5000.0;
    camY = f12.y;
    jitter = ign(gl_FragCoord.xy, f12.w);
    l = f14.xyz;
    float mu = dot(v, l);
    // a strong even part (light scattered many times inside a cloud keeps it white from every side) and a forward lobe
    // for the silver lining toward the sun
    phase = 0.85 + 0.9 * hg(mu, 0.6);
    // the sun's (or moon's) light on them and the sky's: the game's cloud colour is the sky's light by hour and weather
    vec4 p1 = P(1), cc = F(19);
    sunCol = f16.y > 0.5 ? vec3(0.55, 0.65, 0.9) * 0.6 : mix(vec3(1.0, 0.96, 0.9), P(9).rgb * 1.15, p1.a * 0.9);
    sunCol *= f14.w * 1.05;
    // the sky's light on them: a cool blue from above, a little of the horizon's colour, by the game's cloud colour
    // (overcast keeps it: a grey sky is bright grey, not night); with no layer this frame, by the fog's colour
    vec3 skyTint = cp.x > 0.0 ? max(cc.rgb, vec3(0.5)) : max(P(10).rgb * 1.2, vec3(0.3));
    ambient = skyTint * mix(vec3(0.62, 0.72, 0.92), P(10).rgb, 0.35) * (0.95 + 0.9 * smoothstep(0.6, 0.95, cp.y));
    // the Air: the clouds take the sky's own light. Their shade is the sky overhead and at the horizon mixed, lifted
    // toward white by day; their lit faces the sun's colour for the hour, rose at dusk, the moon's by night
    vec3 airFar = P(10).rgb;
    if (cloudBlocks()) {
        vec4 f22 = F(22);
        vec3 hr = airHour(f22.y);
        vec3 over = airSky(vec3(0.0, 1.0, 0.0), f22.xyz, f16.z), edge = airSky(normalize(vec3(v.x, 0.12, v.z)), f22.xyz, f16.z);
        ambient = mix(mix(over, edge, 0.55), vec3(1.0), 0.30 * hr.x) * (0.80 + 0.5 * hr.x + 0.25 * hr.y) + vec3(0.012, 0.016, 0.03) * hr.z;
        vec3 lit = mix(vec3(1.0, 0.97, 0.92), vec3(1.0, 0.60, 0.44), hr.y * (1.0 - 0.6 * hr.x));
        sunCol = (f16.y > 0.5 ? vec3(0.50, 0.60, 0.90) * 0.30 : lit * (0.72 + 0.2 * hr.y)) * f14.w;
        airFar = airFog(v, f22.xyz, f16.z);
    }
    // a storm cell's: the same brightness, but cool grey-blue
    stormSky = vec3(dot(ambient, vec3(0.3, 0.59, 0.11))) * vec3(0.74, 0.8, 0.92);

    // the layer: where the ray is inside it
    float t0 = 0.0, t1 = 0.0;
    vec4 f13 = F(13);
    bool interleave = F(17).y > 0.5 && f13.w > 0.5;
    if (cp.x > 0.0) {
        float base = cp.x - cloudUnder(), top = base + cloudThick();
        if (abs(v.y) > 1.0e-4) {
            float a = (base - camY) / v.y, b = (top - camY) / v.y;
            t0 = max(min(a, b), 0.0);
            t1 = max(a, b);
        } else if (camY > base && camY < top) {
            t1 = maxD;
        }
        t1 = min(min(t1, scene), min(maxD, t0 + (ultra ? 1100.0 : 800.0)));
        // (the Air's blocks are small: the march keeps to the near thousand blocks, where they can be told apart)
        if (cloudBlocks()) t1 = min(t1, max(t0, 0.0) + (ultra ? 1000.0 : 860.0));
    }
    if (interleave) {
        ivec2 tile = ivec2(gl_FragCoord.xy) >> 3;
        int phase = int(mod(f12.w, 4.0)), mine = (tile.x & 1) + 2 * (tile.y & 1);
        // a ray that passes within reach of a flash marches every frame while the flash is bright (it lights at once,
        // with no tiles catching up); its faint tail fades as an afterglow through the history, and every other ray keeps
        // to its one frame in four (every ray marching for 1.2 s after each strike and flicker cost the whole pass four
        // times over, 61% of a storm's frames: Euan's lost frames and stutter, 29 Oct)
        bool flash = false;
        for (int s = 0; s < 2; s++) {
            if (s >= nStorm) break;
            vec4 fc = stormC(s);
            float tc = dot(fc.xyz, v);
            if (fc.w > 0.08 && tc > -FLASH_REACH && tc < scene + FLASH_REACH && length(fc.xyz - v * max(tc, 0.0)) < FLASH_REACH) flash = true;
        }
        if (mine != phase && !flash) {
            // this pixel's off frame: last frame's clouds along this ray, found where cloud would be on it
            vec3 q = project(frameMat(8), v * historyDepth(t0, scene) + f13.xyz, z01);
            if (q.x > 0.0 && q.y > 0.0 && q.x < 1.0 && q.y < 1.0) {
                fragColor = clamp(texture(HistorySampler, q.xy) + (jitter - 0.5) / 255.0, 0.0, 1.0);
                return;
            }
        }
    }
    int steps = ultra ? 32 : (cloudBlocks() ? 28 : 20);
    float len = max(t1 - t0, 1.0);
    // storms nearest first, each skipped by the layer's march (the layer has thinned to nothing there anyway)
    int first = 0;
    if (nStorm > 1 && stormEntry(v, 1, camY) < stormEntry(v, 0, camY)) first = 1;
    float tFrom = t0;
    for (int s = 0; s < 2; s++) {
        if (s >= nStorm) break;
        int k = s == 0 ? first : 1 - first;
        float e = stormEntry(v, k, camY);
        if (e >= scene || e >= 1.0e8) continue;
        float upTo = min(t1, e);
        layerPiece(tFrom, upTo, int(ceil(float(steps) * max(upTo - tFrom, 0.0) / len)), cp);
        vec2 span = storm(k, scene);
        tFrom = max(tFrom, span.y);
    }
    layerPiece(tFrom, t1, int(ceil(float(steps) * max(t1 - tFrom, 0.0) / len)), cp);

    // far cloud melts into the haze toward the horizon
    // (to nothing at the march's reach, so the deck never ends in a line)
    float at = tHit > 0.0 ? tHit : t0;
    float far = cloudBlocks() ? smoothstep(260.0, 1500.0, at) : smoothstep(1500.0, maxD, at);
    // (the Air's far clouds take the fog's colour first and thin out later, so the sky keeps cloud toward the horizon)
    float gone = cloudBlocks() ? smoothstep(520.0, 1500.0, at) * 0.6 : far;
    L = mix(L, airFar * (1.0 - T), far * (cloudBlocks() ? 0.9 : 0.7)) * (1.0 - gone);
    T = 1.0 - (1.0 - T) * (1.0 - gone);
    vec4 res = vec4(L, T);
    // last frame's clouds along this ray, found where it first met cloud; lightning shows at once (and fades over a few
    // frames, an afterglow)
    if (f13.w > 0.5) {
        vec3 s = project(frameMat(8), v * (tHit > 0.0 ? max(tHit, 1.0) : historyDepth(t0, scene)) + f13.xyz, z01);
        float fresh = ultra ? 0.12 : 0.15;
        // marching one frame in four: the same response over time as a march every frame (1 - (1 - f)^4)
        if (interleave) fresh = 1.0 - pow(1.0 - fresh, 4.0);
        fresh = mix(fresh, 0.85, clamp(flashSeen * 4.0, 0.0, 1.0));
        // the Air's blocks are walked exactly, with no noise for the frames to average away: a marched pixel is its own
        // answer (blending it into a history carried by a camera that sways at rest blurred every edge a little more
        // each frame). A storm cell in the ray is still marched with jitter, and keeps its blend
        if (cloudBlocks() && nStorm == 0) fresh = 1.0;
        if (s.x > 0.0 && s.y > 0.0 && s.x < 1.0 && s.y < 1.0) res = mix(texture(HistorySampler, s.xy), res, fresh);
    }
    fragColor = clamp(res + (jitter - 0.5) / 255.0, 0.0, 1.0);
}
