#version 330
#extension GL_ARB_separate_shader_objects : require
#include <awaken:lens_far.glsl>

// AWAKEN Lens, the final pass: depth haze toward the world's own fog colour, bloom, light shafts, the dimension's
// grade (exposure, white balance, contrast, saturation, split toning), vignette and a dither against banding.
// Post passes only get the Globals block, so the frame's fog arrives in the parameter texture with everything else.
// Never lifts shadows: a dark place (a Lightless gate, a night cave) stays as dark as the game made it.
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BloomSampler;      // tight glow, quarter size
uniform sampler2D WideSampler;       // wide glow, eighth size
uniform sampler2D SkySampler;        // quarter-size frame: alpha = open, bright sky (hides the sun halo behind hills)
uniform sampler2D ShaftsSampler;
uniform sampler2D FarDepthSampler;   // far terrain's depth (Distant Horizons), when it draws
uniform sampler2D FarTrustSampler;   // whether that depth has proven itself (lens_far_probe)
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Cinematic;
};

layout(location = 0) out vec4 fragColor;

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
        c = screen(c, texture(ShaftsSampler, texCoord).r * sunColor * 1.4);
    }

    // grade
    float exposure = min(g.r * 2.0, 1.05);
    float warmth = g.a * 2.0 - 1.0 + p1.a * hz.a;
    c *= exposure * vec3(1.0 + 0.07 * warmth, 1.0 + 0.012 * warmth, 1.0 - 0.07 * warmth);
    float l = luma(c);
    c += (shadowT.rgb - vec3(luma(shadowT.rgb))) * (1.0 - l) * (1.0 - l) * shadowT.a;
    c += (highT.rgb - vec3(luma(highT.rgb))) * l * l * shadowT.a;
    c = clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0);
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
}
