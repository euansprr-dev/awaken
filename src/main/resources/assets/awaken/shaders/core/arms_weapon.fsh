#version 330
#extension GL_ARB_separate_shader_objects : require

// Arms' weapon shader (SPEC §24 "Weapons"), mirrored line for line by tools/art/mesh_render.py shade(): keep in step.
// Sampler0 holds the weapon's albedo (left half, where the uvs point) beside its material map (right half):
// r metal, g gloss, b emissive, a flow (0 still; else the phase a glow pulses at, so runes light in turn).
// A forged flaw and what the rank's acts left (script.z: the flaw in its low two bits, 4 the twist's ripples, 8 the
// runes woken; SPEC §46.7, §46.9) are drawn here only: mesh_render.py renders weapons as they come, unforged.

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:light.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 accentAlong;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec2 texCoord0;
layout(location = 5) in vec3 worldNormal;
layout(location = 6) in vec3 viewNormal;
layout(location = 7) flat in vec4 state;
layout(location = 8) flat in vec3 script;

layout(location = 0) out vec4 fragColor;

const vec3 KEY = vec3(-0.4511, 0.7018, 0.5514); // the studio key, view space (upper left front), normalised
const vec3 SKY = vec3(0.78, 0.86, 1.0);
const vec3 GROUND = vec3(0.40, 0.36, 0.34);
const vec3 STRIP = vec3(1.0, 0.97, 0.9);
const float TAU = 6.2831853;
// each enchantment's own light (SPEC §45.11), by the palette index the renderer packs: 1 Heat Ward ember, 2 Frost Ward
// ice, 3 Charge Ward gold, 4 Gravity Ward violet, 5 Spirit Ward teal, 6 the other wards steel, 7 Clear Blood green,
// 8 Bane red, 9 Spoils gold, 10 Carrying blue, 11 Keeping bone white, 12 anything else lapis-violet
const vec3 SCRIPT[13] = vec3[](vec3(0.0), vec3(1.0, 0.54, 0.30), vec3(0.56, 0.85, 1.0), vec3(1.0, 0.85, 0.29),
        vec3(0.73, 0.58, 1.0), vec3(0.31, 0.88, 0.76), vec3(0.73, 0.76, 0.82), vec3(0.62, 0.88, 0.48),
        vec3(1.0, 0.37, 0.39), vec3(0.95, 0.77, 0.35), vec3(0.31, 0.71, 0.92), vec3(0.95, 0.92, 0.84), vec3(0.55, 0.42, 1.0));
// a Force's colour in the steel while its stone is set (SPEC §46.8), by Forces.Force ordinal + 1: Heat, Charge, Gravity, Spirit, Frost;
// 6 the starlight an S-rank weapon is quenched in (§46.9)
const vec3 FORCE_GLOW[7] = vec3[](vec3(0.0), vec3(1.0, 0.55, 0.18), vec3(0.55, 0.85, 1.0), vec3(0.62, 0.42, 1.0),
        vec3(0.62, 0.69, 1.0), vec3(0.70, 0.90, 1.0), vec3(0.88, 0.94, 1.0));

// the studio a metal reflects: a cool sky over a warm dark floor, a soft-box strip above
vec3 studio(vec3 r) {
    vec3 base = mix(GROUND, SKY, smoothstep(-0.3, 0.5, r.y));
    float strip = exp(-pow((r.y - 0.62) * 5.0, 2.0)) * smoothstep(-0.6, 0.2, -r.x + 0.1);
    return base * 0.8 + STRIP * strip * 0.8;
}

// metal's colour as it heats: dull red, orange, yellow, near white
vec3 heatGlow(float h) {
    vec3 c = vec3(0.30, 0.03, 0.01) * smoothstep(0.05, 0.25, h);
    c = mix(c, vec3(0.95, 0.32, 0.05), smoothstep(0.25, 0.55, h));
    c = mix(c, vec3(1.0, 0.78, 0.28), smoothstep(0.55, 0.8, h));
    return mix(c, vec3(1.0, 0.96, 0.86), smoothstep(0.8, 1.0, h));
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

// a brittle blade's crazing: where this crosses zero between two texels, a hairline runs
float craze(vec2 px) {
    return vnoise(px / 5.0) + 0.5 * vnoise(px / 2.3 + 17.0) - 0.75;
}

void main() {
    vec4 alb = texture(Sampler0, texCoord0);
    if (alb.a < 0.1) discard;
    vec4 mat = texture(Sampler0, texCoord0 + vec2(0.5, 0.0));
    float metal = mat.r, gloss = mat.g, emit = mat.b, flow = mat.a;
    float energy = state.x, flash = state.y, foil = state.z, heat = 0.0, front = 0.0;
    if (state.w > 0.5) { heat = energy; front = flash; energy = 0.0; flash = 0.0; } // in the making: x heat, y its front
    vec3 accent = accentAlong.rgb;
    float along = accentAlong.a;

    vec3 nw = normalize(worldNormal);
    vec3 nv = normalize(viewNormal);
    if (!gl_FrontFacing) { nw = -nw; nv = -nv; }

    // vanilla's two item lights, partly banded: painted light rather than plastic
    float d = max(dot(Light0_Direction, nw), 0.0) + max(dot(Light1_Direction, nw), 0.0);
    float diffuse = min(1.0, d * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    diffuse = floor(diffuse * 5.0 + 0.5) / 5.0 * 0.6 + diffuse * 0.4;

    vec3 v = vec3(0.0, 0.0, 1.0);
    vec3 r = reflect(-v, nv);
    vec3 e = studio(r);
    vec3 dielectric = alb.rgb * diffuse;
    vec3 metallic = alb.rgb * (0.28 + 0.9 * e) * (0.75 + 0.25 * diffuse);
    vec3 col = mix(dielectric, metallic, metal);
    float spec = pow(max(dot(r, KEY), 0.0), 6.0 + 84.0 * gloss) * gloss;
    col += spec * mix(vec3(1.0), min(alb.rgb * 1.7, vec3(1.0)), metal) * 0.85;
    float rim = pow(1.0 - clamp(dot(nv, v), 0.0, 1.0), 3.0);
    col += accent * rim * (0.12 + 0.55 * energy * rim) * 0.6;

    // a forged flaw on the blade (SPEC §46.7): a burnt tip darkens toward the point, into dull blue-grey scale on metal and
    // brown-black scorch on bone or tusk (D's blades); a brittle blade is crazed mid-length with pale hairlines a texel
    // wide, so they sit in the pixel art rather than over it. Bone shows both a little less than steel, never not at all.
    int forged = int(script.z + 0.5), flaw = forged & 3;
    if (flaw > 0) {
        float mk = smoothstep(0.25, 0.6, metal), m = 0.7 + 0.3 * mk;
        if (flaw == 1) {
            float t = smoothstep(0.62, 0.97, along) * m;
            vec3 scorch = mix(vec3(0.3, 0.22, 0.17), vec3(0.38, 0.36, 0.46), mk);
            col = mix(col, col * scorch + vec3(0.015, 0.012, 0.03), t);
        } else {
            vec2 px = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
            float a = craze(px), b = craze(px + vec2(1.0, 0.0)), c = craze(px + vec2(0.0, 1.0));
            float on = a * b < 0.0 || a * c < 0.0 ? 1.0 : 0.0;
            float mid = smoothstep(0.3, 0.45, along) * (1.0 - smoothstep(0.72, 0.86, along));
            col = mix(col, vec3(0.86, 0.9, 0.95) * (0.55 + 0.45 * diffuse), on * mid * m * 0.75);
        }
    }

    // a well-twisted A blade (SPEC §46.9): the twist's ripples in the steel, light and dark bands a texel wide that wave
    // across it like folded steel, on the metal from the guard up, set in the pixel art rather than over it
    if ((forged & 4) != 0) {
        vec2 px = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
        float w = (px.x + px.y) * 0.42 + 1.8 * sin(px.y * 0.31 + px.x * 0.17) + 1.4 * vnoise(px / 3.0);
        float b = smoothstep(0.35, 0.65, abs(fract(w * 0.5) * 2.0 - 1.0));
        float on = smoothstep(0.35, 0.6, metal) * smoothstep(0.24, 0.34, along);
        col *= mix(1.0, mix(0.8, 1.14, b), on);
    }

    // the world's light (sky and blocks) on everything but what glows of itself
    col *= lightMapColor.rgb;

    // hot from the Forge past its front (the point's side while it heats; the hilt's while the quench cools it from the
    // point): the metal darkens under its own glow, which needs no light from the world. The texture's light and dark
    // run hotter and cooler (bright metal, dark scale) and the faces turned from the light glow less, so it reads as a
    // shaped blade, not a lump of colour.
    if (state.w > 0.5) {
        float side = state.w > 1.5 ? front - along : along - front;
        float hot = smoothstep(-0.04, 0.04, side) * heat;
        float lum = dot(alb.rgb, vec3(0.30, 0.59, 0.11));
        float h = hot * (0.7 + 0.6 * lum);
        vec3 glow = heatGlow(h);
        // worked again (SPEC §46.8) the glow can take a colour: a Force's as its stone is set, sinking into the metal even as
        // it cools, or a Lord's (the weapon's accent) as its weapon is reforged
        if (script.x > 0.5) {
            vec3 tint = script.x > 14.5 ? accent : FORCE_GLOW[min(int(script.x + 0.5), 6)];
            glow = mix(glow, tint * (0.25 + 0.85 * h), script.y);
            col += tint * script.y * smoothstep(0.25, 0.6, metal) * (0.2 + 0.25 * diffuse);
        }
        col = mix(col, col * 0.25, smoothstep(0.0, 0.3, hot)) + glow * (0.6 + 0.3 * h) * (0.3 + 0.7 * diffuse);
    }

    // a sheen runs hilt to tip every five seconds (GameTime is a day per 1.0: 240 a day is one per 100 ticks)
    float cycle = fract(GameTime * 240.0);
    float band = exp(-pow((along - (cycle * 1.8 - 0.4)) * 7.0, 2.0));
    col += band * (0.12 + 0.55 * metal) * gloss * (0.6 + flash);

    // what glows of itself, pulsing in turn along its flow
    float pulse = flow > 0.0 ? 0.55 + 0.45 * sin(GameTime * 240.0 * TAU * 0.6 - flow * 12.0) : 1.0;
    col += alb.rgb * emit * pulse * (1.0 + 1.5 * energy) * 1.6;
    col += accent * flash * (0.18 + 0.6 * emit + 0.35 * rim);

    // an S blade quenched in starlight (§46.9): its runes woke. What glows of itself burns brighter and cooler, waking in
    // turn from the hilt even where it was still, and now and then a point of the steel catches the starlight
    if ((forged & 8) != 0) {
        vec3 star = FORCE_GLOW[6];
        float t = GameTime * 240.0;
        float wake = 0.6 + 0.4 * sin(t * TAU * 0.4 - along * 9.0);
        col += mix(alb.rgb, star, 0.55) * emit * wake * 0.9;
        vec2 px = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
        float glint = fract(sin(dot(px + floor(t * 2.0), vec2(12.9898, 78.233))) * 43758.5453) > 0.994 ? 1.0 : 0.0;
        col += star * glint * smoothstep(0.35, 0.6, metal) * 0.5;
    }

    // an enchantment's own light (SPEC §45.11): a thin line of script along the piece's edges in that enchantment's
    // colour, its letters drifting hilt to tip, what glows of itself taking the colour, a few pixels catching it; with
    // several on the piece the renderer lights each in turn. Foil with none of ours keeps the violet shimmer.
    if (script.x > 0.5 && state.w < 0.5) {
        vec3 ink = SCRIPT[int(script.x + 0.5)];
        float lit = script.y, t = GameTime * 240.0;
        float cell = along * 26.0 - t * 1.4;
        float h = fract(sin(floor(cell) * 12.9898) * 43758.5453), f = fract(cell);
        float letter = h > 0.2 && f > 0.12 && f < 0.3 + 0.6 * h ? 1.0 : 0.0;
        float edge = smoothstep(0.4, 0.8, pow(1.0 - clamp(dot(nv, v), 0.0, 1.0), 2.0));
        float wave = 0.45 + 0.55 * pow(0.5 + 0.5 * sin((along * 2.5 - t * 0.7) * TAU), 2.0);
        vec2 px = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
        float twinkle = fract(sin(dot(px + floor(t * 6.0), vec2(12.9898, 78.233))) * 43758.5453) > 0.988 ? 0.3 + metal : 0.0;
        col += ink * (letter * edge * wave * 1.1 + emit * pulse * 0.9 + rim * 0.22 + twinkle * 0.8) * lit;
    } else if (foil > 0.5) {
        float g = sin((along * 9.0 + nv.x * 2.0) - GameTime * 240.0 * TAU * 0.35) * 0.5 + 0.5;
        col += vec3(0.55, 0.32, 1.0) * pow(g, 6.0) * 0.45;
    }

    fragColor = apply_fog(vec4(min(col, vec3(1.0)), 1.0) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
