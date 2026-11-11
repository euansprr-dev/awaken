// AWAKEN Lens, the final pass's body, shared by lens_composite.fsh (Swift) and lens_composite_lit.fsh (Cinematic and
// Ultra, which define LENS_LIGHT for Lens 2's light: SPEC §22). Include lens_far.glsl first.
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BloomSampler;      // tight glow, quarter size
uniform sampler2D WideSampler;       // wide glow, eighth size
uniform sampler2D SkySampler;        // quarter-size frame: alpha = open, bright sky (hides the sun halo behind hills)
uniform sampler2D ShaftsSampler;
uniform sampler2D FarDepthSampler;   // far terrain's depth (Distant Horizons), when it draws
uniform sampler2D FarTrustSampler;   // whether that depth has proven itself (lens_far_probe)
uniform sampler2D ParamsSampler;
#ifdef LENS_LIGHT
uniform sampler2D LightSampler;      // Lens 2's light buffer (lens_light.fsh): sun, ambient occlusion, distance
uniform sampler2D FrameSampler;      // the frame's matrices and light (LensFrame)
uniform sampler2D HeightSampler;     // the terrain's heights (LensHeightfield)
uniform sampler2D CloudsSampler;     // Lens 2's clouds (lens_clouds.fsh): rgb their light, a what shows through
uniform sampler2D AirSkySampler;     // the Air's sky buffer (lens_airsky.fsh): the galaxy's light and the wisps' cover
#endif

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Cinematic;
#ifdef LENS_LIGHT
    float Quality;                   // 1 Cinematic, 2 Ultra (screen-space reflections)
#endif
};

layout(location = 0) out vec4 fragColor;

#ifdef LENS_LIGHT
#include <awaken:lens_frame.glsl>
#include <awaken:lens_height.glsl>
#include <awaken:lens_normal.glsl>
#include <awaken:lens_storm_frame.glsl>
#include <awaken:lens_air.glsl>
#include <awaken:lens_cloud_cells.glsl>
#include <awaken:lens_storm_blocks.glsl>
#include <awaken:lens_cloud_walk.glsl>
#endif

// The parameter texture (LensParams, 32x1): 0 flags and debug, 1-2 far plane and sun, 3-8 the look, 9 sunset, 10-11 fog,
// 12 sun glow, 13 the story's moments, 14 holo focus, 15 far terrain's planes, 16 motion, 17-20 Lens 2's light, mist
// and magic, 21 the camera under cover (LensParams); 22-31 free.
#include <awaken:lens_block.glsl>

vec4 P(int i) { return LensUse.x > 0.5 ? LensP[i] : texelFetch(ParamsSampler, ivec2(i, 0), 0); }

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - clamp(b, 0.0, 1.0)); }

// Lite (SPEC §22 "Lite"; parameter texel 25 r): the Air's sky, fog, clouds and rays and the grade, with none of Lens 2's
// light. The chain runs this same composite without the light buffer, the heightfield or the far clouds' buffer (their
// samplers are bound to stand-ins), so every read of those is behind this.
bool lensLite() { return P(25).r > 0.5; }

// The player's grade (SPEC §22 "Grades"; LensGrades): its own step at the end of the colour stage, on the finished
// colour, the same on every tier. a: vibrance, dark colour, rise, punch; b: azure, leaf, gold, split (parameter texels
// 22 and 23, each 0..1; all zero is the look as built and the step is skipped). Every part works by how far one
// channel leads the others, so it is smooth across a gradient and has no hue to branch on.
vec3 lensGrade(vec3 c, vec4 a, vec4 b) {
    // azure: a violet blue toward a deeper azure, by blue's lead over the other two (red gives way; green hardly
    // rises, or a bright sky pales to cyan). Purples keep their hue: red over green by more than a share of the blue,
    // so a flower stays purple by moonlight too, where every difference is small
    float lead = max(c.b - max(c.r, c.g), 0.0) * smoothstep(-0.08, 0.0, (c.g - c.r) / max(c.b, 1.0e-3)) * b.r;
    c.g += lead * 0.08;
    c.r -= lead * 0.50;
    // leaf: green's lead brightens toward a sunlit yellow-green
    float leaf = max(c.g - max(c.r, c.b), 0.0) * b.g;
    c.g += leaf * 0.55;
    c.r += leaf * 0.40;
    // gold: warm colours (red leading green, both over blue) glow a little more
    float gold = max(min(c.r, c.g * 1.25) - c.b, 0.0) * smoothstep(0.0, 0.12, c.r - c.g) * b.b;
    c.r += gold * 0.30;
    c.g += gold * 0.14;
    c = max(c, 0.0);
    // rise: the lights and upper mids come up, the darks stay where they are
    float l = clamp(luma(c), 1.0e-4, 1.0);
    c *= 1.0 + a.b * 6.75 * l * (1.0 - l);
    // punch: an S around the middle, at half strength below it (night and shade keep their depth)
    vec3 cc = clamp(c, 0.0, 1.0), s = cc * cc * (3.0 - 2.0 * cc) - cc;
    c += max(s, s * 0.5) * a.a;
    // split: sunlit colours a little warm, shade a little cool (each tint less its own brightness, so neither end
    // gets lighter or darker)
    const vec3 warm = vec3(1.0, 0.92, 0.72), cool = vec3(0.72, 0.90, 1.12);
    l = luma(c);
    c *= 1.0 + b.a * 0.16 * ((warm - luma(warm)) * smoothstep(0.45, 0.95, l) + (cool - luma(cool)) * (1.0 - smoothstep(0.05, 0.45, l)));
    // vibrance: the less saturated a colour, the more it gains (a flower or a glow that is already rich stays put);
    // and the darks keep their colour
    l = luma(c);
    float mx = max(c.r, max(c.g, c.b)), mn = min(c.r, min(c.g, c.b)), dk = 1.0 - clamp(l, 0.0, 1.0);
    float k = 1.0 + a.r * (1.0 - (mx - mn) / max(mx, 1.0e-4)) + a.g * dk * dk;
    return clamp(l + (c - l) * k, 0.0, 1.0);
}

// view distance in blocks from reversed-Z depth; the projection's depth range differs between OpenGL and Vulkan
float viewDistance(float depth, float zFar, bool zeroToOne) {
    const float zNear = 0.05;
    if (zeroToOne) return zNear * zFar / (depth * (zFar - zNear) + zNear);
    float ndc = depth * 2.0 - 1.0;
    return 2.0 * zFar * zNear / (ndc * (zFar - zNear) + (zFar + zNear));
}

// far terrain's view distance from its own projection (forward or reverse Z, built for 0..1 or -1..1 depth). The
// stored depth comes back to the projection's z through the device's own depth range: Distant Horizons builds a 0..1
// matrix even where the device maps -1..1 (OpenGL without clip control).
float farDistance(float depth, float zNear, float zFar, int flags, bool deviceZeroToOne) {
    bool reverse = (flags & 1) != 0, zeroToOne = (flags & 2) != 0;
    float z = deviceZeroToOne ? depth : depth * 2.0 - 1.0;
    if (zeroToOne) return reverse ? zNear * zFar / (z * (zFar - zNear) + zNear) : zNear * zFar / (zFar - z * (zFar - zNear));
    return reverse ? 2.0 * zFar * zNear / (z * (zFar - zNear) + (zFar + zNear))
                   : 2.0 * zFar * zNear / ((zFar + zNear) - z * (zFar - zNear));
}

#ifdef LENS_LIGHT
// the light buffer (a its one-byte distance) at this pixel: of its 2x2 texels with bilinear weights, those that saw
// this surface's distance (so a near silhouette doesn't bleed onto the far side), else the closest match
vec3 upsampleAt(sampler2D buf, vec2 uv, float want, out float got) {
    vec2 size = vec2(textureSize(buf, 0));
    vec2 lp = uv * size - 0.5;
    ivec2 b = ivec2(floor(lp));
    vec2 fr = lp - vec2(b);
    float bestDiff = 2.0, wsum = 0.0;
    vec3 acc = vec3(0.0), best = vec3(1.0, 1.0, 0.0);
    for (int k = 0; k < 4; k++) {
        ivec2 o = ivec2(k & 1, k >> 1);
        vec4 L = texelFetch(buf, clamp(b + o, ivec2(0), ivec2(size) - 1), 0);
        float diff = abs(L.a - want);
        float w = (o.x == 1 ? fr.x : 1.0 - fr.x) * (o.y == 1 ? fr.y : 1.0 - fr.y) * (1.0 - smoothstep(1.5 / 255.0, 2.5 / 255.0, diff));
        acc += L.rgb * w;
        wsum += w;
        if (diff < bestDiff) {
            bestDiff = diff;
            best = L.rgb;
        }
    }
    got = wsum;
    return wsum > 1.0e-3 ? acc / wsum : best;
}

vec3 upsample(sampler2D buf, float want) {
    float got;
    return upsampleAt(buf, texCoord, want, got);
}

// the light's colour: the sun's (warm by the sunrise colour's strength), or the moon's
vec3 lightColour(vec4 p1, vec4 sunset) {
    return F(16).y > 0.5 ? vec3(0.6, 0.7, 0.95) * 0.7 : mix(vec3(1.0, 0.96, 0.88), sunset.rgb, p1.a);
}

// Lens 2's light (SPEC §22), on the frame as the game lit it: a warm key on faces the sun reaches, a cool shade where it
// doesn't (deeper and warmer-keyed as the sun lowers), ambient occlusion in corners. As a multiplier on linear light;
// the frame is display-encoded, where that is about its square root. Fades out under solid cover (a roof, a cave),
// for the hand and holo glass (within arm's reach), toward the end of the render distance and in the game's thick fog.
// how much a pixel looks like one of AWAKEN's glows (runes, vortices, the Signal's blue, eyes): bright and saturated,
// and not a warm hue (a sunlit orange roof is bright and saturated too; the glows are cyan, blue, violet, pink, red)
float glowness(vec3 c) {
    float hi = max(c.r, max(c.g, c.b)), lo = min(c.r, min(c.g, c.b));
    float sat = hi > 1.0e-4 ? (hi - lo) / hi : 0.0;
    float cool = c.b >= c.g * 0.9 || (c.g > c.r && c.b > c.r) ? 1.0 : 0.0;
    return smoothstep(0.7, 0.95, hi) * smoothstep(0.45, 0.8, sat) * cool;
}

// open water under the light: Fresnel reflection of what's above it (the sky, or on Ultra the scene itself, marched in
// screen space), rippling, with the sun's glint on top. The heightfield marks water columns (solid top at base + 0.75).
vec3 lensWater(vec3 c, vec3 p, vec3 n, float vis, float strength) {
    if (F(15).x < 1.0) return c;
    vec4 f12 = F(12), f14 = F(14), f16 = F(16);
    vec3 w = p + f12.xyz;
    float g = heightAt(ivec2(floor(w.xz)), 0).g;
    if (abs(fract(g) - 0.75) > 0.02) return c;
    float surf = floor(g) + 0.889;
    // the depth holds the water's surface, or (water drawn without depth) what lies under it: then the ray crossed the
    // surface on its way, where it reflects
    if (w.y < surf - 0.3) {
        if (f12.y < surf || p.y > -1.0e-3) return c;
        p *= (surf - f12.y) / p.y;
        w = p + f12.xyz;
        g = heightAt(ivec2(floor(w.xz)), 0).g;
        if (abs(fract(g) - 0.75) > 0.02) return c;
    } else if (n.y < 0.9) {
        return c;
    }
    float on = 1.0 - smoothstep(0.1, 0.3, abs(w.y - surf));
    if (on <= 0.0) return c;
    bool z01 = f16.x > 0.5;
    float t = F(17).x;
    vec2 rip = vec2(sin(w.x * 1.7 + t * 1.6) + sin(w.z * 2.3 - t * 1.3 + w.x * 0.7),
                    cos(w.z * 1.9 + t * 1.1) + sin(w.x * 2.9 + w.z * 1.1 + t * 2.0)) * 0.03;
    vec3 N = normalize(vec3(rip.x, 1.0, rip.y)), V = normalize(p), R = reflect(V, N);
    float fres = 0.02 + 0.98 * pow(1.0 - max(dot(-V, N), 0.0), 5.0);
    mat4 vp = frameMat(4);
    // the sky above: the frame itself where the reflected ray meets open sky on screen, else the fog's colour
    vec3 s = project(vp, R * 1000.0, z01);
    vec3 refl = P(10).rgb;
    if (s.x > 0.0 && s.y > 0.0 && s.x < 1.0 && s.y < 1.0 && texture(DepthSampler, s.xy).r < 1.0e-7) refl = texture(InSampler, s.xy).rgb;
    if (Quality > 1.5) {
        // Ultra: march the reflected ray against the depth buffer, nearest first
        vec4 p1 = P(1);
        float zFar = max(p1.r * 255.0 * 256.0 + p1.g * 255.0, 16.0), tt = 0.3;
        for (int i = 0; i < 20; i++) {
            tt *= 1.22;
            vec3 q = project(vp, p + R * tt, z01);
            if (q.x <= 0.0 || q.y <= 0.0 || q.x >= 1.0 || q.y >= 1.0) break;
            float sd = texture(DepthSampler, q.xy).r;
            float zs = linearDepth(sd, zFar, z01), zq = linearDepth(q.z, zFar, z01);
            if (sd > 1.0e-7 && zs > 1.25 && zs < zq - 0.05 && zq - zs < tt * 0.35 + 0.4) {
                vec2 edge = min(q.xy, 1.0 - q.xy);
                refl = mix(refl, texture(InSampler, q.xy).rgb, smoothstep(0.0, 0.08, min(edge.x, edge.y)));
                break;
            }
        }
    }
    vec3 lit = c + (refl - c) * fres * on * strength;
    // the sun's glint off the ripples (none in the water's own shade)
    float glint = pow(max(dot(R, f14.xyz), 0.0), 240.0) * 5.0 * f14.w * vis * on * strength;
    vec3 sunColour = f16.y > 0.5 ? vec3(0.6, 0.7, 0.95) * 0.7 : mix(vec3(1.0, 0.96, 0.88), P(9).rgb, P(1).a);
    return lit + sunColour * glint;
}

// the light buffer as lensLight upsampled it for this pixel (the volumetric term reads the same texels: kept, not
// fetched twice)
vec3 lrKept;
bool lrHave = false;
// how open to the sky this pixel's surface is (1 open, 0 deep under a roof or in a cave): the Air's fog is the sky's
float lensOpen = 1.0;

// The sky's light under a roof, from beside it (SPEC §22 "Steady Light", cover): how much of the open sky a point
// under solid cover still has from the side, 1 at the roof's edge falling to nothing six blocks in, as the game's own
// sky light falls off into a cave. Along the four compass lines from the point: the nearest column each way that
// stands open to the sky at the point's height (the heightfield), each line read in the two rows it runs between, so
// the answer is smooth along a line and across it and no row or block shows as a step. hp: the point, in world blocks.
float skyBeside(vec3 hp) {
    float best = 0.0;
    for (int axis = 0; axis < 2; axis++) {
        float along = axis == 0 ? hp.x : hp.z, across = (axis == 0 ? hp.z : hp.x) - 0.5;
        int a0 = int(floor(along)), r0 = int(floor(across));
        float fr = across - float(r0);
        for (int dir = 0; dir < 2; dir++) {
            float f0 = 0.0, f1 = 0.0;
            for (int d = 1; d <= 6; d++) {
                int a = dir == 0 ? a0 + d : a0 - d;
                // from the point to that column's near edge
                float fall = 1.0 - (dir == 0 ? float(a) - along : along - float(a + 1)) / 6.0;
                if (fall <= 0.0) break;
                ivec2 c0 = axis == 0 ? ivec2(a, r0) : ivec2(r0, a), c1 = axis == 0 ? ivec2(a, r0 + 1) : ivec2(r0 + 1, a);
                float o0 = 1.0 - smoothstep(1.0, 3.0, heightAt(c0, 0).g - hp.y);
                float o1 = 1.0 - smoothstep(1.0, 3.0, heightAt(c1, 0).g - hp.y);
                f0 = max(f0, o0 * fall);
                f1 = max(f1, o1 * fall);
                // (nothing farther along can beat a column fully open in both rows)
                if (o0 >= 1.0 && o1 >= 1.0) break;
            }
            best = max(best, mix(f0, f1, fr));
        }
    }
    return best;
}

vec3 lensLight(vec3 c, vec3 p, vec3 quickNormal, float depth, float rdEnd, float fogFade, vec4 p1, out vec4 dbg) {
    dbg = vec4(0.0);
    vec4 f12 = F(12), f14 = F(14), f16 = F(16), l17 = P(17), l18 = P(18), m20 = P(20);
    if (f16.w <= 0.0) return c;
    bool z01 = f16.x > 0.5;
    mat4 inv = frameMat(0);
    float dist = length(p);
    float near = smoothstep(1.25, 1.45, dist);
    if (near <= 0.0) return c;
    vec3 m = vec3(1.0);
    float rich = 0.0;
    float sun = f14.w * l17.r;
    float air = airOn();
    bool needNormal = sun > 0.0 || (m20.r > 0.0 && F(15).x > 0.0) || air > 0.0;
    // the screen-derivative normal where this pixel's 2x2 quad lies on one surface; the careful four taps across an edge
    vec3 n = quickNormal;
    if (needNormal && n == vec3(0.0)) n = normalAt(clamp(ivec2(texCoord * OutSize), ivec2(0), ivec2(OutSize) - 1), p, depth, inv, z01, OutSize);
    // the light buffer, upsampled by distance: of its 2x2 texels, the ones that saw this surface.
    // The Air: on a block face it is read where the centre of this pixel's 1/16-block texel lies on screen, so every
    // screen pixel of a texel takes one light and a shadow's edge is the texture's own pixel edge at any buffer size
    // (stretched from a smaller buffer it was a soft band, and the shadow had no weight)
    float want = packDistance8(dist), got = 0.0;
    // the buffer at this pixel's own place: the soft shade and the mist always (they are worked out afresh each frame
    // and only settle over several, so one sample held across a whole texel made every grass pixel flicker as a block:
    // "the floor was shimmering", Euan 2026-11-10), and the sun's light everywhere but close to the eye
    vec3 lr = upsampleAt(LightSampler, texCoord, want, got);
    // close to the eye, where a texel is many screen pixels, the sun's light alone is read at the texel's centre (the
    // light pass gives such a texel one unchanging answer); from 10 to 18 blocks it goes over to the smooth read, where
    // a texel is no wider than one of the buffer's own pixels and its centre's pixel is as likely a neighbour's
    float crisp = air > 0.0 ? 1.0 - smoothstep(10.0, 18.0, dist) : 0.0;
    if (crisp > 0.0) {
        vec3 an = abs(n);
        if (max(an.x, max(an.y, an.z)) > 0.99) {
            vec3 w = p + f12.xyz;
            vec3 q = project(frameMat(4), mix((floor(w * 16.0) + 0.5) / 16.0, w, step(0.99, an)) - f12.xyz, z01);
            if (q.x > 0.0 && q.y > 0.0 && q.x < 1.0 && q.y < 1.0) {
                float gotAt = 0.0;
                vec3 at = upsampleAt(LightSampler, q.xy, want, gotAt);
                if (gotAt > 1.0e-3) lr.r = mix(lr.r, at.r, crisp);
            }
        }
    }
    lrKept = lr;
    lrHave = true;
    if (sun > 0.0) {
        // solid blocks above this point in its own column: under a ledge it's shade, deep under a roof it's left alone
        float cover = 0.0, beside = 0.0;
        if (F(15).x > 0.0) {
            vec3 hp = p + f12.xyz + n * 0.1;
            ivec2 col = ivec2(floor(hp.xz));
            cover = heightAt(col, 0).g - hp.y;
            // under an eave with open sky beside it (a roof's overhang, a gable's rake) it's the open ground's shade, not
            // a room's: else a sharp step runs along the roof line (Euan 2026-10-28). A room, a wall's thickness in, keeps its own
            if (cover > 1.0) {
                float side = min(min(heightAt(col + ivec2(1, 0), 0).g, heightAt(col - ivec2(1, 0), 0).g),
                        min(heightAt(col + ivec2(0, 1), 0).g, heightAt(col - ivec2(0, 1), 0).g));
                cover = min(cover, side - hp.y);
            }
            // (still under a roof: what of the sky reaches in from beside it)
            if (cover > 1.0) beside = skyBeside(hp);
        }
        // Deep under a roof the model stands back and the game's own light stays. It stood back by the roof's thickness
        // alone, so a block one step into a cave or under a cliff's overhang, where the game still draws full sky
        // light, kept the game's brightness beside open ground in the Air's deep shade: bright grey blocks against
        // dark blue ones along every roof line (Euan, playing, 11 Nov: "shading issues in this cave"). The shade now
        // carries on in from the roof's edge and fades over six blocks, as the game's sky light does.
        float open = max(1.0 - smoothstep(1.0, 7.0, cover), beside);
        lensOpen = open;
        // faces turned from the sun shade only as it lowers: at noon (straight overhead in Minecraft) the walls keep the
        // game's own face shading and only cast shadows show; by golden hour every face is lit or shaded by its turn
        float lowSun = 1.0 - smoothstep(0.5, 0.92, f14.y);
        // the Sun Pass (frame texel 17 z: the block map's reach): inside the map a face turned from the sun is shaded by
        // the block it belongs to, exactly, so the turn of the face decides nothing there. (Deciding by the face lit the
        // one-pixel sides of path plates and block lips while the ground beside them lay in shade: bright rims round
        // every small edge at dusk, Euan 10 Nov.)
        float inMap = F(17).z > 0.0 ? 1.0 - smoothstep(F(17).z - 6.0, F(17).z - 1.0, max(abs(p.x), abs(p.z))) : 0.0;
        float direct = lr.r * mix(1.0, smoothstep(-0.08, 0.3, dot(n, f14.xyz)), lowSun * (1.0 - inMap));
        // the Air: a low sun gives the ground little. Its light fades into the shade as it sinks, so dusk is one soft
        // light and not a floor of long streaks (each a gap between two far trees, stretched thirty blocks)
        direct *= mix(1.0, smoothstep(0.05, 0.55, f14.y), airOn() * (1.0 - f16.y));
        // golden hour: the key warms and the shade deepens as the sun lowers (by its height, or the sunrise colour's
        // strength, texel 1 a, whichever is more), never by moonlight
        float golden = max(p1.a, 1.0 - smoothstep(0.1, 0.5, f14.y)) * (1.0 - f16.y);
        vec3 key = mix(vec3(1.04, 1.02, 0.97), vec3(1.30, 1.03, 0.72), golden * l18.a);
        key = mix(key, vec3(0.93, 0.98, 1.08), f16.y);
        // the shade: cool, deeper as the sun lowers and with distance (long shadows stay readable across the land, not
        // only up close), a little warmed at golden hour by light bounced off the sunlit ground
        float depthOfShade = l17.g * (1.0 + 0.3 * golden) * (1.0 + 0.35 * smoothstep(24.0, 160.0, dist));
        vec3 shade = l18.rgb * 2.0 * mix(vec3(1.0), vec3(1.08, 0.99, 0.9), golden * 0.6) * (1.0 - min(0.85, depthOfShade));
        // a glow is light, not a lit surface: it keeps its brightness in the shade
        // the Air (Steady Light): sunlight that reads. A brighter, warmer key and a shade about twice as deep, so a
        // cast shadow is a shape on the ground and not a tint; by moonlight the shade deepens less
        if (air > 0.0) {
            key = mix(key, mix(vec3(1.24, 1.17, 0.98), key * vec3(1.14, 1.07, 0.99), max(golden, f16.y)), air);
            // (a shadow with weight: deep and cool by day, less so by moonlight)
            // (deepest under a high sun; as the sun sinks its key fades into the shade, and a shade that stayed this
            // deep left a morning forest black under a bright sky)
            float deep = mix(1.5, 2.5, smoothstep(0.10, 0.50, f14.y) * (1.0 - f16.y));
            shade *= mix(1.0, (1.0 - min(0.9, depthOfShade * deep)) / max(1.0 - min(0.85, depthOfShade), 0.15), air);
        }
        // the Sun Pass (frame texel 17 z: the block map's reach): under cover the heightfield had no sun to give, so the
        // model stood back and left the game's light. The map knows where the sun does come in (a doorway, a window,
        // under an arch): there the key shows as a patch of sunlight; the rest of the room stays the game's
        float kOpen = max(open, direct * inMap);
        float k = kOpen * sun * near * fogFade * (1.0 - smoothstep(0.85 * rdEnd, rdEnd, dist)) * (1.0 - glowness(c) * m20.a);
        m = mix(vec3(1.0), mix(mix(vec3(1.0), shade, open), key, direct), k);
        // golden light is rich light: sunlit colour gains a little saturation as the sun lowers
        rich = golden * k * direct * 0.16;
        dbg = vec4(n * 0.5 + 0.5, direct);
    }
    // ambient occlusion everywhere, day or night (it only darkens), less of it far away
    m *= mix(1.0, lr.g, l17.b * near * (1.0 - smoothstep(48.0, 96.0, dist)));
    c *= sqrt(m);
    c = max(mix(vec3(luma(c)), c, 1.0 + rich), 0.0);
    // rain wets what it falls on (open to the sky in its column; under leaves, a little): darker and a touch richer, and
    // what faces up takes a dull sheen of the sky at a glancing angle (a Rolling Storm's local rain too)
    if (f16.z > 0.0 && F(15).x > 0.0) {
        vec3 hp = p + f12.xyz + n * 0.1;
        vec2 top = heightAt(ivec2(floor(hp.xz)), 0);
        float exposed = max(1.0 - smoothstep(0.3, 1.5, top.r - hp.y), 0.4 * (1.0 - smoothstep(0.3, 1.5, top.g - hp.y)));
        float wet = f16.z * exposed * near * fogFade * (1.0 - smoothstep(0.85 * rdEnd, rdEnd, dist)) * (1.0 - glowness(c));
        c *= 1.0 - 0.16 * wet;
        c = max(mix(vec3(luma(c)), c, 1.0 + 0.1 * wet), 0.0);
        float up = smoothstep(0.6, 0.95, n.y), fres = pow(1.0 - clamp(dot(-p / dist, n), 0.0, 1.0), 4.0);
        c += P(10).rgb * fres * up * wet * 0.22;
    }
    if (m20.r > 0.0) c = lensWater(c, p, n, lr.r, m20.r * fogFade);
    return c;
}

// a smooth filmic curve in place of the plain contrast: past white, colour runs toward white instead of clipping its
// hue, and the top tenth rolls off softly (white itself stays within 3% of white); contrast is an S around mid-grey with
// the look's slope there, and a black point as deep as the plain contrast's
vec3 filmic(vec3 c, float contrast) {
    float m = max(c.r, max(c.g, c.b));
    c = mix(c, vec3(m), smoothstep(0.95, 1.6, m) * 0.6);
    vec3 over = max(c - 0.9, 0.0);
    c = min(c, vec3(0.9)) + 0.1 * tanh(over / 0.1);
    float a = clamp(contrast - 1.0, -0.4, 0.45);
    c = c + 4.0 * a * c * (1.0 - c) * (c - 0.5);
    // and a black point as deep as the plain contrast's, so night and shadow keep their depth
    float black = max(a, 0.0) * 0.45;
    return clamp((c - black) / (1.0 - black), 0.0, 1.0);
}
#endif

#ifdef LENS_LIGHT
// how far frost has crept in from the frame's edge here (0 clear, 1 frosted)
float frostEdge(vec2 uv, float frost) {
    vec2 v = (uv - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    float wobble = sin(atan(v.y, v.x) * 11.0) * 0.012 + sin(atan(v.y, v.x) * 27.0) * 0.006;
    // distance in from the nearest edge, corners reaching furthest in
    vec2 e = min(uv, 1.0 - uv) * vec2(OutSize.x / OutSize.y, 1.0);
    float in_ = min(e.x, e.y) - 0.02 * smoothstep(0.6, 1.0, length(v)) + wobble;
    return (1.0 - smoothstep(0.0, 0.11 * frost, in_)) * frost;
}

// heat shimmer (rippling, more toward the lower frame where hot ground is) and frost creeping in at the frame's edges
// (crystalline refraction there), as an offset to where the frame is read (L3; the look's heat and frost)
vec2 distortion(vec2 uv, float heat, float frost, float t) {
    vec2 o = vec2(0.0);
    if (heat > 0.0) {
        float mask = 0.35 + 0.65 * smoothstep(0.75, 0.05, uv.y);
        o += vec2(sin(uv.y * 95.0 + t * 6.0 + sin(uv.x * 23.0 + t * 1.3) * 2.0), cos(uv.x * 71.0 - t * 4.7 + uv.y * 30.0)) * heat * 0.0018 * mask;
    }
    if (frost > 0.0) {
        float e = frostEdge(uv, frost);
        vec2 cell = floor(uv * vec2(OutSize.x / OutSize.y, 1.0) * 60.0);
        vec2 h = fract(sin(vec2(dot(cell, vec2(127.1, 311.7)), dot(cell, vec2(269.5, 183.3)))) * 43758.5453) - 0.5;
        o += h * e * 0.006;
    }
    return o;
}

#endif

void main() {
    vec2 uvc = texCoord;
#ifdef LENS_LIGHT
    vec4 m20early = P(20);
    if ((m20early.g > 0.0 || m20early.b > 0.0) && int(P(0).r * 255.0 + 0.5) != 8) uvc += distortion(texCoord, m20early.g, m20early.b, F(17).x);
#endif
    // 16: moving fast through the air (LensClient.motion): r strength, g/b the heading on screen. The frame smears
    // toward where you're heading (sharp there, more toward the edges) with a touch of chroma at the rim; nothing at rest
    vec4 mo = P(16);
    vec3 c;
    if (mo.r > 0.004) {
        vec2 toHead = mo.gb - texCoord;
        float edge = clamp(length(toHead * vec2(OutSize.x / OutSize.y, 1.0)) * 1.4, 0.0, 1.0);
        vec2 stepv = toHead * (mo.r * 0.07 * edge * edge) / 6.0;
        vec3 acc = vec3(0.0);
        float wsum = 0.0;
        for (int i = 0; i < 6; i++) {
            float w = 1.0 - float(i) / 7.0;
            acc += texture(InSampler, uvc + stepv * float(i)).rgb * w;
            wsum += w;
        }
        c = acc / wsum;
        float fringe = mo.r * edge * edge;
        c.r = mix(c.r, texture(InSampler, texCoord - stepv * 1.5).r, 0.5 * fringe);
        c.b = mix(c.b, texture(InSampler, texCoord + stepv * 2.5).b, 0.5 * fringe);
    } else {
        c = texture(InSampler, uvc).rgb;
    }
    // the frame as the game drew it: under cover, the floor Lens's darks never fall below
    vec3 game = c;
    vec4 p0 = P(0), p1 = P(1), g = P(3), shadowT = P(4), highT = P(5), b = P(6), h = P(7), hz = P(8), sunset = P(9);
    vec4 fogColor = P(10), fogDist = P(11), p12 = P(12), sunPos = P(2);
    // 13: the story's moments: r the tear from black (0 off), g crushed blacks, b extra bloom (LensParams)
    vec4 rit = P(13);
    float farPlane = max((p1.r * 255.0 * 256.0 + p1.g * 255.0), 16.0);
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = depth < 1.0e-7;
    int flags = farFlags(p0);
    vec4 farTrust = texelFetch(FarTrustSampler, ivec2(0, 0), 0);

    // depth haze: aerial perspective toward the fog colour the game already chose for this place and hour
    float d = sky ? farPlane : viewDistance(depth, farPlane, p0.g > 0.5);
    bool farTerrain = false;
    if (sky && p0.b > 0.5) {
        float fd = texture(FarDepthSampler, texCoord).r;
        if (farHit(fd, flags, farTrust)) {
            vec4 p15 = P(15);
            float fNear = (p15.r * 255.0 * 256.0 + p15.g * 255.0) / 16.0, fFar = p15.b * 255.0 * 256.0 + p15.a * 255.0;
            d = farDistance(fd, max(fNear, 0.05), max(fFar, fNear + 1.0), flags, p0.g > 0.5);
            sky = false;
            farTerrain = true;
        }
    }
    float rdEnd = max(fogDist.r * 255.0 * 256.0 + fogDist.g * 255.0, 16.0);
    float envEnd = fogDist.b * 255.0 * 256.0 + fogDist.a * 255.0;
#ifdef LENS_LIGHT
    // debug view 8: the classic Cinematic (no Lens 2 light, plain contrast), for before/after shots of the same frame
    bool classic = int(p0.r * 255.0 + 0.5) == 8;
    vec4 lightDbg = vec4(0.0);
    vec3 unlit = c;
    // this pixel's camera-relative position (none for sky and far terrain, which the game's depth doesn't hold)
    bool open = sky || farTerrain;
    vec3 pRel = open ? vec3(0.0) : unproject(frameMat(0), texCoord, depth, F(16).x > 0.5);
    // a Holo world plate (glass at a creature's distance): no light, AO or haze on it, as for the panels
    bool plate = !open && onPlate(pRel);
    // the surface normal from screen derivatives (free, but only where the 2x2 quad lies on one surface: across an edge
    // it's left 0 for the careful version); axis-aligned block faces snap
    vec3 dpx = dFdx(pRel), dpy = dFdy(pRel);
    float pd = max(length(pRel), 0.05);
    vec3 quick = vec3(0.0);
#ifndef PROBE_NO_LIGHT
    if (max(abs(dFdx(pd)), abs(dFdy(pd))) < pd * 0.015 && !open) {
        quick = normalize(cross(dpx, dpy));
        if (dot(quick, pRel) > 0.0) quick = -quick;
        vec3 qa = abs(quick);
        float qm = max(qa.x, max(qa.y, qa.z));
        if (qm > 0.9) quick = step(vec3(qm), qa) * sign(quick);
    }
    if (!open && !classic && !plate && !lensLite()) c = lensLight(c, pRel, quick, depth, rdEnd, clamp((envEnd - 8.0) / rdEnd, 0.0, 1.0), p1, lightDbg);
#endif
    // the Air (Steady Light): Lens's own sky, and a far fog of the sky's own horizon colour that takes the land into it,
    // so the world's end is never a line. The sun, the moon and the game's stars are kept from the frame (what it drew
    // over its own sky colour); Lens's larger stars join them by night. Places under cover keep the game's air
    float airK = airOn();
    if (airK > 0.0) {
        vec3 dirA = open ? normalize(unproject(frameMat(0), texCoord, 1.0e-5, F(16).x > 0.5)) : normalize(pRel);
        vec4 f22 = F(22), f23 = F(23);
        float rainA = F(16).z;
        if (sky) {
            vec3 was = mix(fogColor.rgb, f23.rgb, smoothstep(-0.02, 0.30, dirA.y));
            vec3 extra = max(c - was - 0.05, 0.0);
            float keep = smoothstep(0.04, 0.22, luma(extra));
            vec3 s = airSky(dirA, f22.xyz, rainA);
            // the night's own: stars, the galaxy, a shooting star (they come out as the sun goes under); then the high
            // wisps over all of it
            float dark = smoothstep(0.06, -0.20, f22.y) * (1.0 - rainA);
            // (the galaxy's light and the wisps' cover are noise: drawn once a frame into the small sky buffer,
            // lens_airsky.fsh, and read here)
            vec4 slow = texture(AirSkySampler, texCoord);
            if (dark > 0.0) s += airNight(dirA, f22.xyz, F(17).x) * dark + airSlowGalaxy(slow);
            s += extra * keep;
            s = mix(s, airWispColour(dirA, f22.xyz, rainA), airSlowWisp(slow));
            c = mix(c, s, airK);
        } else if (!farTerrain && !plate) {
            // by distance over the render distance (a far renderer's land carries on past it, so the fog thins out
            // over its reach instead), a little thinner on land well above the eye so peaks stand out of it
            float reach = rdEnd * (p0.b > 0.5 ? 5.0 : 1.0);
            float t = d / reach;
            float wy = pRel.y + F(12).y;
            float thin = mix(1.0, exp(-max(wy - F(12).y - 40.0, 0.0) / 220.0), 0.5);
            // and the air between here and there: a haze that starts a few blocks out, thin by day, thicker at dusk and
            // dawn and by night, lying low (full at and under the eye's height, thinner above it), so each line of
            // trees stands a little paler than the one before it and the far fog has something leading into it
            vec3 hrA = airHour(f22.y);
            // (thinner only well above the eye, and slowly: a tree is one thing at one distance, and a haze that thinned
            // over its own height left every far tree a dark crown on a vanished trunk, Euan 10 Nov)
            float lying = mix(0.62, 1.0, exp(-max(wy - F(12).y - 16.0, 0.0) / 130.0));
            // (a grade may thicken it: parameter texel 24 r, the haze over 1, halved)
            float near = (1.0 - exp(-max(d - 4.0, 0.0) * (1.0 + 2.0 * P(24).r) / mix(280.0, 150.0, clamp(max(hrA.y, hrA.z * 0.85) + 0.5 * rainA, 0.0, 1.0)))) * lying;
            // The fog is the air along the view, so what is over the far point doesn't matter: only a camera that is
            // itself under cover, looking at something under cover, is in a cave's air (parameter texel 21 r). (Leaving
            // out every covered point left the ground under each ledge and the mouth of each cave as a dark hole in
            // the haze.)
            // (Lite has no cover by the pixel: a camera under cover keeps the game's air for all it sees)
            float inside = P(21).r * (lensLite() ? 1.0 : 1.0 - lensOpen);
            float fog = (1.0 - (1.0 - near) * exp(-t * t * 3.2 * thin)) * clamp((envEnd - 8.0) / rdEnd, 0.0, 1.0) * (1.0 - inside) * airK;
            vec3 fc = airFog(dirA, f22.xyz, rainA);
            c = mix(c, mix(c, vec3(luma(c)), 0.3), fog);
            c = mix(c, fc, fog);
        }
    }
    // Lens 2's clouds (L4), over the sky and over anything farther than where this ray enters their layer
    // and the storm cells marched with them (lens_storm.glsl): over anything farther than where the ray enters a cell's
    // bounds, never nearer than 16 blocks (its rain fades in from there; nearer, the game's own rain falls)
    vec4 cp = F(18);
    int storms = stormCount();
#ifdef PROBE_NO_CLOUDS
    if (false) {
#else
    if ((cp.x > 0.0 || storms > 0) && !classic) {
#endif
        // (Lite draws no far clouds' buffer: the layer's blocks fade out at the near walk's end, a storm keeps its blocks
        // and loses its soft rain)
        bool noFar = lensLite();
        vec4 cl = noFar ? vec4(0.0, 0.0, 0.0, 1.0) : texture(CloudsSampler, texCoord);
        bool behind = sky;
        if (!sky) {
            vec3 cv = farTerrain ? normalize(unproject(frameMat(0), texCoord, 1.0e-5, F(16).x > 0.5)) : normalize(pRel);
            float camY = F(12).y, dist = farTerrain ? d : length(pRel);
            if (cp.x > 0.0) {
                // (the Air's blocks: a thinner layer, lens_clouds.glsl)
                float base = cp.x - (airK > 0.0 ? 2.0 : 6.0), thick = airK > 0.0 ? 21.0 : 84.0, enter = 1.0e9;
                if (camY >= base && camY <= base + thick) enter = 0.0;
                else if (abs(cv.y) > 1.0e-4) enter = (camY < base ? base - camY : base + thick - camY) / cv.y;
                behind = enter >= 0.0 && dist > enter;
            }
            for (int k = 0; k < 2; k++) {
                if (k >= storms) break;
                behind = behind || dist > max(stormEntry(cv, k, camY), 16.0);
            }
        }
        if (behind) c = c * cl.a + cl.rgb;
        // the Air's near clouds over all of it: walked here, at the frame's own size and against this pixel's own
        // depth (lens_cloud_walk.glsl), so they need no such test and a tree in front of one keeps its own edge
        // A storm cell among them is built of the same blocks (lens_storm_blocks.glsl), walked here too: first, so the
        // layer's clouds in front of it draw over it and the ones behind it are never walked
        if (cloudBlocks() && (cp.x > 0.0 || storms > 0)) {
            bool beyond = sky || farTerrain;
            vec3 nv = beyond ? normalize(unproject(frameMat(0), texCoord, 1.0e-5, F(16).x > 0.5)) : normalize(pRel);
            float reachTo = sky ? 1.0e9 : (farTerrain ? d : length(pRel));
            if (storms > 0) {
                float stormAt;
                vec4 sb = stormBlocks(nv, reachTo, stormAt);
                c = c * sb.a + sb.rgb;
                if (sb.a < 0.5) reachTo = min(reachTo, stormAt);
            }
            if (cp.x > 0.0) {
                vec4 cb = cloudNear(nv, reachTo);
                c = c * cb.a + cb.rgb;
            }
        }
    }
#endif
    float haze = sky ? h.r * 0.12 : smoothstep(h.g * rdEnd, rdEnd * 1.1, d) * h.r;
    // aerial perspective keeps deepening over far terrain: ridges fade layer by layer toward the horizon
    // (unless the far renderer draws its own aerial perspective at real distances, flag 8: Far Sight 2)
    if (farTerrain && (flags & 8) == 0) haze = min(1.0, haze + smoothstep(rdEnd, rdEnd * 8.0, d) * 0.45 * step(0.001, h.r));
    // While far terrain draws, distance haze needs its depth. With none shared (DH's OpenGL engine, DH on Vulkan) or
    // none proven yet (a depth that reads as one value everywhere) distance is the far renderer's own fog: hazing only
    // the near terrain would draw a ring where it ends. A depth that proves itself eases the haze in (trust g).
    if (p0.b > 0.5) haze *= farTrust.g;
#ifdef LENS_LIGHT
    // the Air's fog has already taken the distance
    haze *= 1.0 - airOn();
#endif
    // underwater, in lava or blind, the game's own fog already hides the distance
    haze *= clamp((envEnd - 8.0) / rdEnd, 0.0, 1.0);
#ifdef LENS_LIGHT
    if (plate) haze = 0.0;
#endif
    vec3 hazeColor = mix(fogColor.rgb, hz.rgb, h.b);
#ifdef LENS_LIGHT
    // at golden hour the distance hazes warm, not grey: the sunrise colour lends the haze a share as the sun lowers
    if (!classic) {
        float goldenHaze = max(p1.a, 1.0 - smoothstep(0.1, 0.5, F(14).y)) * (1.0 - F(16).y) * step(0.0001, F(14).w);
        hazeColor = mix(hazeColor, hazeColor * 0.6 + sunset.rgb * 0.5, goldenHaze * 0.35);
    }
#endif
    float l0 = luma(c);
    c = mix(c, mix(c, vec3(l0), h.a), haze);
    c = mix(c, hazeColor, haze * 0.55);
#ifdef LENS_LIGHT
#ifdef PROBE_NO_SCATTER
    if (false) {
#else
    if (!classic) {
#endif
        vec4 v19 = P(19), f14 = F(14);
        // lights glowing in fog: the wide glow spread wider still (the shafts pass's g/b/a), tinted toward the haze, as
        // strong as the place's fogGlow
        if (v19.b > 0.0) c = screen(c, texture(ShaftsSampler, texCoord).gba * v19.b * 3.0 * mix(vec3(1.0), hazeColor * 1.6, 0.35));
        // the sky and the haze scatter the light around the sun's direction (warmer and wider at golden hour)
        if (f14.w > 0.0 && v19.a > 0.0 && (sky || haze > 0.0)) {
            vec3 dir = open ? normalize(unproject(frameMat(0), texCoord, 1.0e-5, F(16).x > 0.5)) : normalize(pRel);
            float mu = max(dot(dir, f14.xyz), 0.0);
            vec3 sc = lightColour(p1, sunset) * (pow(mu, 8.0) * 0.25 + pow(mu, 48.0) * 0.35) * f14.w * v19.a * (0.45 + 0.55 * p1.a);
            c = screen(c, sc * (sky ? 1.0 : haze));
        }
    }
#endif

    // bloom (a tight and a wide layer) and light shafts, screen-blended so nothing clips to white
    vec3 glow = texture(BloomSampler, texCoord).rgb * 0.55 + texture(WideSampler, texCoord).rgb * 0.9;
    c = screen(c, glow * (b.r + rit.b) * 2.0);
    // the sun's halo: light scattering around the disc, warm at sunset, gone when hills or trees hide the sun
    if (p1.b > 0.0 && p12.r > 0.0) {
#ifdef LENS_LIGHT
        // where the world was really drawn (a camera mod's roll included), not where the game's own camera puts it
        vec2 sun = clamp(F(21).xy, -1.0, 2.0);
#else
        vec2 sun = vec2((sunPos.r * 255.0 * 256.0 + sunPos.g * 255.0) / 65535.0, (sunPos.b * 255.0 * 256.0 + sunPos.a * 255.0) / 65535.0) * 3.0 - 1.0;
#endif
        float open = (texture(SkySampler, sun).a + texture(SkySampler, sun + vec2(0.01, 0.0)).a + texture(SkySampler, sun - vec2(0.01, 0.0)).a) / 3.0;
        // (on Cinematic and Ultra the sky's alpha carries Lens 2's clouds too, so the halo dims behind them)
        float r = length((texCoord - sun) * vec2(OutSize.x / OutSize.y, 1.0));
        float halo = (exp(-r * 9.0) * 0.45 + exp(-r * 2.6) * 0.16) * p1.b * p12.r * clamp(open * 1.6, 0.0, 1.0);
        vec3 haloColor = mix(vec3(1.0, 0.93, 0.8), sunset.rgb, p1.a);
        c = screen(c, haloColor * halo);
    }
    if (Cinematic > 0.5) {
        vec3 sunColor = mix(vec3(1.0, 0.96, 0.88), sunset.rgb, p1.a);
#ifdef LENS_LIGHT
        // volumetric light through the terrain's shadows, with the screen-space shafts kept at half for what the sun map
        // can't hold (clouds crossing the sun)
#ifdef PROBE_NO_SCATTER
        if (false) {
#else
        if (!classic) {
#endif
            float vol = lensLite() ? 0.0 : (!open && lrHave ? lrKept.b : upsample(LightSampler, open ? 0.0 : packDistance8(length(pRel))).b);
            // (the Air: the sunlit mist is stronger, so beams stand in the air between the trees)
            c = screen(c, lightColour(p1, sunset) * (vol * mix(1.6, 3.4, airOn())) + texture(ShaftsSampler, texCoord).r * sunColor * mix(0.35, 0.95, airOn()));
        } else
#endif
        c = screen(c, texture(ShaftsSampler, texCoord).r * sunColor * 1.4);
    }

    // grade
    float exposure = min(g.r * 2.0, 1.05);
    float warmth = g.a * 2.0 - 1.0 + p1.a * hz.a;
    c *= exposure * vec3(1.0 + 0.07 * warmth, 1.0 + 0.012 * warmth, 1.0 - 0.07 * warmth);
    float l = luma(c);
    c += (shadowT.rgb - vec3(luma(shadowT.rgb))) * (1.0 - l) * (1.0 - l) * shadowT.a;
    c += (highT.rgb - vec3(luma(highT.rgb))) * l * l * shadowT.a;
#ifdef LENS_LIGHT
#ifdef PROBE_NO_GRADE
    c = clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0);
#else
    c = mix(clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0), filmic(c, g.g * 2.0), classic || sky ? 0.0 : P(17).a);
#endif
#else
    c = clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0);
#endif
    c = mix(vec3(luma(c)), c, g.b * 2.0);
    // the player's grade over the place's own (all zero: the look as built)
    vec4 grA = P(22), grB = P(23);
    if (dot(grA + grB, vec4(1.0)) > 0.0) c = lensGrade(c, grA, grB);
    // Under solid cover (a cave, a room) Lens never takes the dark below the game's own: the grade's black point and
    // shadow tint would crush a cave at light 0 (on Bright, about 6% grey) and a hand or tool held there to pure black.
    // The darks keep at least 85% of what the game drew, so a cave stays as dark and moody as the game made it and
    // never reads as a black void; the mids and lights keep the look's contrast (Euan 2026-10-29, SPEC §22). Cinematic
    // and Ultra read cover per pixel from the heightfield (a dark doorway seen from the street too); Swift by the camera.
#ifdef LENS_LIGHT
    float under = 0.0;
    if (!open && !plate && F(15).x > 0.0 && max(abs(pRel.x), abs(pRel.z)) < F(15).z) {
        // the air in front of the surface (a step toward the camera), so a trunk's or a cliff's own column is never its cover
        vec3 hw = pRel * (1.0 - 0.3 / max(length(pRel), 0.3)) + F(12).xyz;
        under = smoothstep(0.5, 1.5, heightAt(ivec2(floor(hw.xz)), 0).g - hw.y);
    }
    // (Lite: by the camera, as Swift does)
    if (lensLite()) under = sky ? 0.0 : P(21).r;
#else
    float under = sky ? 0.0 : P(21).r;
#endif
    if (under > 0.0) c = mix(c, max(c, game * 0.85), under * (1.0 - smoothstep(0.05, 0.22, luma(game))));
    // Within arm's reach (your hand and what it holds, a wall at your nose) never darker than the game at all: a held
    // torch's handle, lit by its own flame, read dark under a cave look (Euan 2026-10-29). Eased out as Lens's light
    // eases in, 1.25 to 1.45 blocks. (sky || farTerrain, not Lens light's `open`: Swift has no LENS_LIGHT and no `open`.)
    // The reach is by the line of sight, the very distance Lens's light eases in by (lensLight's `near`). It was the
    // depth straight ahead: with a wide view (sprinting widens it) the ground at the bottom of the screen and a wall
    // at its side are two blocks away and yet under 1.45 ahead, so a band along those edges took the game's own
    // brightness back and stood out raw against the shade (Euan, playing, 11 Nov: "the bottom edge and sometimes left
    // edge of my screen has no shaders ... changes based on my pace and how I move my head").
#ifdef LENS_LIGHT
    float handReach = open ? 1.0e9 : length(pRel);
#else
    // Swift has no world position: the hand's own distance, by the fixed field of view the game draws the hand with
    // (tan 35 degrees), which a wider world view only lengthens
    vec2 handRay = (texCoord * 2.0 - 1.0) * vec2(OutSize.x / OutSize.y, 1.0) * 0.7002;
    float handReach = d * sqrt(1.0 + dot(handRay, handRay));
#endif
    if (!(sky || farTerrain)) c = mix(c, max(c, game), 1.0 - smoothstep(1.25, 1.45, handReach));
    // crushed blacks: the darks fall to black and the light that's left looks brighter against them
    float crush = rit.g * 0.2;
    c = max(c - crush, 0.0) / (1.0 - crush);

    // 14: an open holo panel holds the eye (UiHooks.focus): the world past arm's reach dims and cools; the panel's
    // glass writes its own depth a block or so away, so it and its text stay bright
    float focus = P(14).r;
    if (focus > 0.0) {
        float k = focus * (sky ? 1.0 : smoothstep(1.8, 3.0, d));
        c = mix(c, mix(vec3(luma(c)), c, 0.5) * vec3(0.8, 0.88, 1.0) * 0.6, k);
    }

#ifdef LENS_LIGHT
    // frost at the frame's edge: a pale rime with fine crystal streaks over what it covers
    if (P(20).b > 0.0 && !classic) {
        float e = frostEdge(texCoord, P(20).b);
        vec2 fv = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
        float streak = pow(abs(sin(atan(fv.y, fv.x) * 40.0 + length(fv) * 90.0)), 12.0);
        c = mix(c, vec3(0.84, 0.93, 1.0) * (0.55 + 0.45 * luma(c)) + streak * 0.12, e * 0.5);
    }
#endif
    // vignette, rounder on wide screens; it starts further out and darkens to three quarters of a look's value (Euan
    // 2026-11-02: too dark; a 0.3 look's corners keep 82% where they kept 73%)
    vec2 v = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0) * 0.82;
    c *= mix(1.0, smoothstep(1.02, 0.4, length(v)), highT.a * 0.75);

    // triangular dither: smooth haze and sky gradients without 8-bit bands
    vec2 q = gl_FragCoord.xy;
    float n = fract(sin(dot(q, vec2(12.9898, 78.233))) * 43758.5453) + fract(sin(dot(q, vec2(39.3468, 11.135))) * 24634.6345) - 1.0;
    c += n / 255.0;

    // the tear: the black opens from the centre with a ragged, blazing edge (the awakening)
    if (rit.r > 0.0) {
        float t = (rit.r * 255.0 - 1.0) / 254.0;
        vec2 tq = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
        float r = length(tq);
        float a = atan(tq.y, tq.x);
        float edge = t * t * 1.3 + (sin(a * 7.0 + t * 11.0) * 0.5 + 0.5) * 0.06 * (1.0 - t) + sin(a * 19.0 - t * 5.0) * 0.015;
        float open = smoothstep(edge + 0.002, edge - 0.035, r);
        float rim = exp(-abs(r - edge) * 55.0) * (1.0 - t * 0.6);
        c = c * open + vec3(0.62, 0.84, 1.0) * rim * 1.4 * step(0.001, t);
    }
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);

    // development views (texel 0 red byte; 255 = normal)
    int dbg = int(p0.r * 255.0 + 0.5);
    if (dbg == 1) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);                      // the frame, untouched
    } else if (dbg == 2) {
        float vd = texture(DepthSampler, texCoord).r;
        bool farHere = p0.b > 0.5 && farHit(texture(FarDepthSampler, texCoord).r, flags, farTrust);
        // blue: open sky by the game's depth; red: far terrain by DH's depth; green: the game's own geometry
        vec3 m = vd < 1.0e-7 ? (farHere ? vec3(1.0, 0.1, 0.1) : vec3(0.1, 0.2, 1.0)) : vec3(0.1, 0.9, 0.2);
        fragColor = vec4(mix(texture(InSampler, texCoord).rgb, m, 0.6), 1.0);
    } else if (dbg == 3) {
        // raw far depth: r its fine bands, g the depth; b while far terrain draws: 1 Lens trusts its depth, 0.6 shared
        // but not proven (one value so far), 0.3 none shared
        float fd = texture(FarDepthSampler, texCoord).r;
        float trusted = farTrust.r > 0.5 ? 1.0 : (flags & 4) != 0 ? 0.6 : 0.3;
        fragColor = vec4(fract(fd * 16.0), fd, p0.b * trusted, 1.0);
    } else if (dbg == 4) {
        // the haze each pixel gets, grey from none to full: far terrain warm, open sky blue. A ring at the end of the
        // vanilla render distance would show as a step in brightness here.
        vec3 tint = farTerrain ? vec3(1.0, 0.82, 0.66) : sky ? vec3(0.6, 0.75, 1.0) : vec3(1.0);
        fragColor = vec4(tint * (0.08 + 0.92 * haze), 1.0);
    }
#ifdef LENS_LIGHT
    else if (dbg == 5) {
        // Lens 2's light buffer: r sun, g ambient occlusion, b volumetric light
        vec4 L = texture(LightSampler, texCoord);
        fragColor = vec4(L.r, L.g, L.b, 1.0);
    } else if (dbg == 6) {
        // normals from depth (rgb) and how much direct sun each surface gets (shown on the frame: bright = lit)
        fragColor = vec4(mix(lightDbg.rgb, vec3(lightDbg.a), 0.35), 1.0);
    } else if (dbg == 7) {
        // the light alone: the lit frame against the unlit one (grey 0.5 = unchanged)
        fragColor = vec4(clamp(0.5 + (c - unlit) * 2.0, 0.0, 1.0), 1.0);
    }
#endif
}
