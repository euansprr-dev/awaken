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
#endif

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Cinematic;
};

layout(location = 0) out vec4 fragColor;

#ifdef LENS_LIGHT
#include <awaken:lens_frame.glsl>
#include <awaken:lens_height.glsl>
#include <awaken:lens_normal.glsl>
#endif

// The parameter texture (LensParams, 32x1): 0 flags and debug, 1-2 far plane and sun, 3-8 the look, 9 sunset, 10-11 fog,
// 12 sun glow, 13 the story's moments, 14 holo focus, 15 far terrain's planes, 16 motion; 17-31 free.
vec4 P(int i) { return texelFetch(ParamsSampler, ivec2(i, 0), 0); }

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - clamp(b, 0.0, 1.0)); }

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
vec3 upsample(sampler2D buf, float want) {
    vec2 size = vec2(textureSize(buf, 0));
    vec2 lp = texCoord * size - 0.5;
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
    return wsum > 1.0e-3 ? acc / wsum : best;
}

// the light's colour: the sun's (warm by the sunrise colour's strength), or the moon's
vec3 lightColour(vec4 p1, vec4 sunset) {
    return F(16).y > 0.5 ? vec3(0.6, 0.7, 0.95) * 0.7 : mix(vec3(1.0, 0.96, 0.88), sunset.rgb, p1.a);
}

// Lens 2's light (SPEC §22), on the frame as the game lit it: a warm key on faces the sun reaches, a cool shade where it
// doesn't (deeper and warmer-keyed as the sun lowers), ambient occlusion in corners. As a multiplier on linear light;
// the frame is display-encoded, where that is about its square root. Fades out under solid cover (a roof, a cave),
// for the hand and holo glass (within arm's reach), toward the end of the render distance and in the game's thick fog.
vec3 lensLight(vec3 c, vec3 p, float depth, float rdEnd, float fogFade, vec4 p1, out vec4 dbg) {
    dbg = vec4(0.0);
    vec4 f12 = F(12), f14 = F(14), f16 = F(16), l17 = P(17), l18 = P(18);
    if (f16.w <= 0.0) return c;
    bool z01 = f16.x > 0.5;
    mat4 inv = frameMat(0);
    float dist = length(p);
    float near = smoothstep(1.25, 1.45, dist);
    if (near <= 0.0) return c;
    // the light buffer, upsampled by distance: of its 2x2 texels, the ones that saw this surface
    vec3 lr = upsample(LightSampler, packDistance8(dist));
    vec3 m = vec3(1.0);
    float sun = f14.w * l17.r;
    if (sun > 0.0) {
        vec3 n = normalAt(clamp(ivec2(texCoord * OutSize), ivec2(0), ivec2(OutSize) - 1), p, depth, inv, z01, OutSize);
        // solid blocks above this point in its own column: under a ledge it's shade, deep under a roof it's left alone
        float cover = 0.0;
        if (F(15).x > 0.0) {
            vec3 hp = p + f12.xyz + n * 0.1;
            cover = heightAt(ivec2(floor(hp.xz)), 0).g - hp.y;
        }
        float open = 1.0 - smoothstep(1.0, 7.0, cover);
        // faces turned from the sun shade only as it lowers: at noon (straight overhead in Minecraft) the walls keep the
        // game's own face shading and only cast shadows show; by golden hour every face is lit or shaded by its turn
        float lowSun = 1.0 - smoothstep(0.5, 0.92, f14.y);
        float direct = lr.r * mix(1.0, smoothstep(-0.08, 0.3, dot(n, f14.xyz)), lowSun);
        // golden hour: the key warms and the shade deepens as the sun lowers (by its height, or the sunrise colour's
        // strength, texel 1 a, whichever is more), never by moonlight
        float golden = max(p1.a, 1.0 - smoothstep(0.1, 0.5, f14.y)) * (1.0 - f16.y);
        vec3 key = mix(vec3(1.04, 1.02, 0.97), vec3(1.24, 1.0, 0.74), golden * l18.a);
        key = mix(key, vec3(0.93, 0.98, 1.08), f16.y);
        vec3 shade = l18.rgb * 2.0 * (1.0 - min(0.85, l17.g * (1.0 + 0.3 * golden)));
        float k = open * sun * near * fogFade * (1.0 - smoothstep(0.75 * rdEnd, rdEnd, dist));
        m = mix(vec3(1.0), mix(shade, key, direct), k);
        dbg = vec4(n * 0.5 + 0.5, direct);
    }
    // ambient occlusion everywhere, day or night (it only darkens), less of it far away
    m *= mix(1.0, lr.g, l17.b * near * (1.0 - smoothstep(48.0, 96.0, dist)));
    return c * sqrt(m);
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

void main() {
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
            acc += texture(InSampler, texCoord + stepv * float(i)).rgb * w;
            wsum += w;
        }
        c = acc / wsum;
        float fringe = mo.r * edge * edge;
        c.r = mix(c.r, texture(InSampler, texCoord - stepv * 1.5).r, 0.5 * fringe);
        c.b = mix(c.b, texture(InSampler, texCoord + stepv * 2.5).b, 0.5 * fringe);
    } else {
        c = texture(InSampler, texCoord).rgb;
    }
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
    if (!open && !classic) c = lensLight(c, pRel, depth, rdEnd, clamp((envEnd - 8.0) / rdEnd, 0.0, 1.0), p1, lightDbg);
#endif
    float haze = sky ? h.r * 0.12 : smoothstep(h.g * rdEnd, rdEnd * 1.1, d) * h.r;
    // aerial perspective keeps deepening over far terrain: ridges fade layer by layer toward the horizon
    if (farTerrain) haze = min(1.0, haze + smoothstep(rdEnd, rdEnd * 8.0, d) * 0.45 * step(0.001, h.r));
    // While far terrain draws, distance haze needs its depth. With none shared (DH's OpenGL engine, DH on Vulkan) or
    // none proven yet (a depth that reads as one value everywhere) distance is the far renderer's own fog: hazing only
    // the near terrain would draw a ring where it ends. A depth that proves itself eases the haze in (trust g).
    if (p0.b > 0.5) haze *= farTrust.g;
    // underwater, in lava or blind, the game's own fog already hides the distance
    haze *= clamp((envEnd - 8.0) / rdEnd, 0.0, 1.0);
    vec3 hazeColor = mix(fogColor.rgb, hz.rgb, h.b);
    float l0 = luma(c);
    c = mix(c, mix(c, vec3(l0), h.a), haze);
    c = mix(c, hazeColor, haze * 0.55);
#ifdef LENS_LIGHT
    if (!classic) {
        vec4 v19 = P(19), f14 = F(14);
        // lights glowing in fog: the wide glow spread wider still (eight taps on a ring a tenth of the screen out), tinted
        // toward the haze, as strong as the place's fogGlow
        if (v19.b > 0.0) {
            vec2 ring = vec2(OutSize.y / OutSize.x, 1.0) * 0.1;
            vec3 fog = texture(WideSampler, texCoord).rgb * 0.2;
            for (int k = 0; k < 8; k++) {
                float a = float(k) * 0.785398;
                fog += texture(WideSampler, texCoord + vec2(cos(a), sin(a)) * ring).rgb * 0.1;
            }
            c = screen(c, fog * v19.b * 2.0 * mix(vec3(1.0), hazeColor * 1.6, 0.35));
        }
        // the sky and the haze scatter the light around the sun's direction (warmer and wider at golden hour)
        if (f14.w > 0.0 && v19.a > 0.0 && (sky || haze > 0.0)) {
            vec3 dir = open ? normalize(unproject(frameMat(0), texCoord, 1.0e-5, F(16).x > 0.5)) : normalize(pRel);
            float mu = max(dot(dir, f14.xyz), 0.0);
            vec3 sc = lightColour(p1, sunset) * (pow(mu, 5.0) * 0.3 + pow(mu, 40.0) * 0.4) * f14.w * v19.a * (0.45 + 0.55 * p1.a);
            c = screen(c, sc * (sky ? 1.0 : haze));
        }
    }
#endif

    // bloom (a tight and a wide layer) and light shafts, screen-blended so nothing clips to white
    vec3 glow = texture(BloomSampler, texCoord).rgb * 0.55 + texture(WideSampler, texCoord).rgb * 0.9;
    c = screen(c, glow * (b.r + rit.b) * 2.0);
    // the sun's halo: light scattering around the disc, warm at sunset, gone when hills or trees hide the sun
    if (p1.b > 0.0 && p12.r > 0.0) {
        vec2 sun = vec2((sunPos.r * 255.0 * 256.0 + sunPos.g * 255.0) / 65535.0, (sunPos.b * 255.0 * 256.0 + sunPos.a * 255.0) / 65535.0) * 3.0 - 1.0;
        float open = (texture(SkySampler, sun).a + texture(SkySampler, sun + vec2(0.01, 0.0)).a + texture(SkySampler, sun - vec2(0.01, 0.0)).a) / 3.0;
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
        if (!classic) {
            float vol = upsample(LightSampler, open ? 0.0 : packDistance8(length(pRel))).b;
            c = screen(c, lightColour(p1, sunset) * (vol * 1.6) + texture(ShaftsSampler, texCoord).r * sunColor * 0.7);
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
    c = mix(clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0), filmic(c, g.g * 2.0), classic ? 0.0 : P(17).a);
#else
    c = clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0);
#endif
    c = mix(vec3(luma(c)), c, g.b * 2.0);
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

    // vignette, rounder on wide screens
    vec2 v = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0) * 0.82;
    c *= mix(1.0, smoothstep(0.98, 0.32, length(v)), highT.a);

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
