#version 330
#extension GL_ARB_separate_shader_objects : require

// The armour shader (docs/armour/PLAN.md): the weapon shader (arms_weapon.fsh, tools/art/mesh_render.py shade()) on armour.
// Sampler0 holds the set's albedo (left half, where the uvs point) beside its material map (right half):
// r metal, g gloss, b emissive, a flow (0 still; else the phase a glow pulses at, so runes light in turn).

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
layout(location = 8) flat in vec2 mastery;
layout(location = 9) flat in vec2 script;
layout(location = 10) flat in float skyLight;

layout(location = 0) out vec4 fragColor;

const vec3 KEY = vec3(-0.4511, 0.7018, 0.5514); // the studio key, view space (upper left front), normalised
const vec3 SKY = vec3(0.78, 0.86, 1.0);
const vec3 GROUND = vec3(0.40, 0.36, 0.34);
const vec3 STRIP = vec3(1.0, 0.97, 0.9);
const vec3 MOON = vec3(0.50, 0.62, 0.92); // what an edge takes from an open night sky
const float TAU = 6.2831853;
// each enchantment's own light (SPEC §45.11), by the palette index the renderer packs: 1 Heat Ward ember, 2 Frost Ward
// ice, 3 Charge Ward gold, 4 Gravity Ward violet, 5 Spirit Ward teal, 6 the other wards steel, 7 Clear Blood green,
// 8 Bane red, 9 Spoils gold, 10 Carrying blue, 11 Keeping bone white, 12 anything else lapis-violet
const vec3 SCRIPT[13] = vec3[](vec3(0.0), vec3(1.0, 0.54, 0.30), vec3(0.56, 0.85, 1.0), vec3(1.0, 0.85, 0.29),
        vec3(0.73, 0.58, 1.0), vec3(0.31, 0.88, 0.76), vec3(0.73, 0.76, 0.82), vec3(0.62, 0.88, 0.48),
        vec3(1.0, 0.37, 0.39), vec3(0.95, 0.77, 0.35), vec3(0.31, 0.71, 0.92), vec3(0.95, 0.92, 0.84), vec3(0.55, 0.42, 1.0));

// the studio a metal reflects: a cool sky over a warm dark floor, a soft-box strip above
vec3 studio(vec3 r) {
    vec3 base = mix(GROUND, SKY, smoothstep(-0.3, 0.5, r.y));
    float strip = exp(-pow((r.y - 0.62) * 5.0, 2.0)) * smoothstep(-0.6, 0.2, -r.x + 0.1);
    return base * 0.8 + STRIP * strip * 0.8;
}

void main() {
    vec4 alb = texture(Sampler0, texCoord0);
    if (alb.a < 0.1) discard;
    vec4 mat = texture(Sampler0, texCoord0 + vec2(0.5, 0.0));
    float metal = mat.r, gloss = mat.g, emit = mat.b, flow = mat.a;
    float energy = state.x, flash = state.y, foil = state.z;
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
    // form light (SPEC §44.1, the Armour Finish): every edge turned up to the open sky takes a little of it, bright
    // metal most, so plates part from one another and from what stands behind them; cloth and hide take a soft fuzz
    // instead. Up is where the two lights come from (the world's y, or a menu's own, whose y runs down).
    vec3 upDir = Light0_Direction + Light1_Direction;
    upDir = dot(upDir, upDir) > 0.01 ? normalize(upDir) : vec3(0.0, 1.0, 0.0);
    float up = smoothstep(-0.5, 0.6, dot(nw, upDir));
    // In the plate's own colour, and never more than a little: adding the sky's white washed dark iron grey and a red
    // stone pale, and a forearm seen edge-on from your own eyes took the whole of it.
    col += (alb.rgb * 1.3 + SKY * 0.04) * min(rim, 0.6) * up * (0.06 + 0.18 * gloss) * (1.0 - 0.6 * emit);
    col += alb.rgb * rim * 0.25 * (1.0 - metal) * (1.0 - gloss);
    col += accent * rim * (0.12 + 0.55 * energy * rim) * 0.6;

    // the world's light (sky and blocks) on everything but what glows of itself
    vec3 world = lightMapColor.rgb;
    float dark = 1.0 - clamp(dot(world, vec3(0.30, 0.59, 0.11)), 0.0, 1.0);
    col *= world;
    // under an open sky at night what is turned up to it, and every edge, still catches the moon: a set keeps its
    // shape in the dark (never its colours), bright metal most; none of it underground or by a fire
    col += MOON * (0.30 * max(dot(nw, upDir), 0.0) + 0.45 * rim * up) * (0.03 + 0.20 * gloss) * (0.35 + 0.65 * max(alb.r, max(alb.g, alb.b))) * skyLight * dark;

    // a sheen runs feet to crown every five seconds (GameTime is a day per 1.0: 240 a day is one per 100 ticks)
    // under the world's light, like the plate it runs on (it used to cross a figure standing in the dark)
    float cycle = fract(GameTime * 240.0);
    float band = exp(-pow((along - (cycle * 1.8 - 0.4)) * 7.0, 2.0));
    col += band * (0.12 + 0.55 * metal) * gloss * (0.6 + flash) * max(world, vec3(flash));

    // mastery (SPEC §44.5): from II the trims (bright gloss metal) catch more light; from III what glows of itself
    // glows more, and when the wearer is low it beats with their heart (a double beat, about seventy a minute)
    float step = mastery.x;
    float trims = step >= 1.0 ? metal * gloss * smoothstep(0.35, 0.7, max(alb.r, max(alb.g, alb.b))) : 0.0;
    col += (alb.rgb * trims * (0.22 + 0.25 * spec) + accent * trims * rim * 0.25) * world; // lit by the world too
    float beat = 0.0;
    if (step >= 2.0 && mastery.y > 0.5) {
        float ph = fract(GameTime * 1200.0 * 1.15);
        beat = exp(-pow((ph - 0.06) * 22.0, 2.0)) + 0.6 * exp(-pow((ph - 0.26) * 22.0, 2.0));
    }
    float seams = step >= 2.0 ? 1.35 + 1.4 * beat : 1.0;

    // what glows of itself, pulsing in turn along its flow
    float pulse = flow > 0.0 ? 0.55 + 0.45 * sin(GameTime * 240.0 * TAU * 0.6 - flow * 12.0) : 1.0;
    col += alb.rgb * emit * pulse * (1.0 + 1.5 * energy) * 1.6 * seams;
    col += accent * beat * (0.12 + 0.5 * emit) * 0.8;
    col += accent * flash * (0.18 + 0.6 * emit + 0.35 * rim);

    // an enchantment's own light (SPEC §45.11): a thin line of script along the piece's edges in that enchantment's
    // colour, its letters drifting feet to crown, what glows of itself taking the colour, a few pixels catching it; with
    // several on the piece the renderer lights each in turn. Foil with none of ours keeps the violet shimmer.
    if (script.x > 0.5) {
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

    // metal in the making (the Forge Song): the work glows by its heat, dull red through orange and yellow to white,
    // lit from within so it shows in a dark smithy; cold metal keeps its own colour
    if (state.w > 0.5) {
        float h = energy * 1.2;
        vec3 hot = h < 0.45 ? mix(vec3(0.30, 0.03, 0.01), vec3(0.85, 0.16, 0.03), h / 0.45)
                : h < 0.75 ? mix(vec3(0.85, 0.16, 0.03), vec3(1.0, 0.58, 0.12), (h - 0.45) / 0.3)
                : h < 1.0 ? mix(vec3(1.0, 0.58, 0.12), vec3(1.0, 0.9, 0.5), (h - 0.75) / 0.25)
                : vec3(1.0, 0.97, 0.88);
        // white heat (the Starfire) keeps the piece's form: its darker metal glows a little less, so it never
        // flattens into one white shape
        float white = smoothstep(0.95, 1.15, h);
        float lum = dot(col, vec3(0.3, 0.59, 0.11));
        hot *= mix(1.0, 0.6 + 0.4 * smoothstep(0.04, 0.5, lum), white);
        float glow = smoothstep(0.08, 0.6, h);
        col = mix(col, col * 0.35 + hot * (0.75 + 0.25 * rim), glow);
        col += hot * glow * (0.25 + 0.3 * sin(GameTime * 240.0 * TAU * 0.4 + along * 6.0) * 0.5 + 0.15) * (1.0 - 0.75 * white);
    }

    fragColor = apply_fog(vec4(min(col, vec3(1.0)), 1.0) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
