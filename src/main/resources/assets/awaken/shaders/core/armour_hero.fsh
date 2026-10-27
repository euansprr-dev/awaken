#version 330
#extension GL_ARB_separate_shader_objects : require

// Veilwoven's hero hood (pack/armour-hero-piece, never merged). The shade is baked into a dense atlas (folds, the face's recess,
// contact under the cord and circlet); this lights it so each material answers the light its own way. Sampler0 holds the albedo
// (left half) beside the material map (right half: r metal, g gloss, b glow), read with a linear sampler.
// Silk: soft wrapped light and a velvet sheen at grazing angles. Satin: a broad highlight. Silver: the studio it reflects.
// Thread and gem: a little light of their own that shimmers along the face, stronger when the world is dark.

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:light.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 lightMapColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 worldNormal;
layout(location = 5) in vec3 viewNormal;
layout(location = 6) in vec3 viewPos;

layout(location = 0) out vec4 fragColor;

const vec3 SKY = vec3(0.78, 0.86, 1.0);
const vec3 GROUND = vec3(0.36, 0.33, 0.36);
const vec3 STRIP = vec3(1.0, 0.97, 0.92);
const vec3 SHEEN = vec3(0.62, 0.52, 1.0);
const float TAU = 6.2831853;

// the studio a metal reflects: a cool sky over a warm dark floor, a soft-box strip above
vec3 studio(vec3 r) {
    vec3 base = mix(GROUND, SKY, smoothstep(-0.35, 0.55, r.y));
    float strip = exp(-pow((r.y - 0.55) * 4.5, 2.0)) * smoothstep(-0.7, 0.3, -r.x + 0.15);
    return base * 0.75 + STRIP * strip * 0.9;
}

void main() {
    vec4 alb = texture(Sampler0, texCoord0);
    if (alb.a < 0.1) discard;
    vec3 mat = texture(Sampler0, texCoord0 + vec2(0.5, 0.0)).rgb;
    float metal = mat.r, gloss = mat.g, glow = mat.b;

    vec3 nw = normalize(worldNormal);
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float ndv = clamp(dot(n, v), 0.0, 1.0);

    // vanilla's two lights, wrapped so cloth turns softly from light into shade
    float w = 0.15;
    float l0 = clamp((dot(Light0_Direction, nw) + w) / (1.0 + w), 0.0, 1.0);
    float l1 = clamp((dot(Light1_Direction, nw) + w) / (1.0 + w), 0.0, 1.0);
    float diffuse = min(1.0, (l0 + l1) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);

    // cloth: the velvet sheen rises toward the silhouette, so every fold catches a rim of lighter violet
    vec3 cloth = alb.rgb * diffuse;
    float sheen = (1.0 - metal) * (1.0 - gloss) * pow(1.0 - ndv, 2.2);
    cloth += (alb.rgb * 0.6 + SHEEN * 0.32) * sheen * (0.35 + 0.65 * diffuse) * 1.6;

    // metal: the studio it reflects
    vec3 r = reflect(-v, n);
    vec3 metalc = alb.rgb * (0.22 + 0.95 * studio(r)) * (0.7 + 0.3 * diffuse);
    vec3 col = mix(cloth, metalc, metal);

    // highlights: tight on silver and gem, broad on satin, almost none on velvet
    vec3 l0v = normalize(mat3(ModelViewMat) * Light0_Direction);
    vec3 l1v = normalize(mat3(ModelViewMat) * Light1_Direction);
    float ex = 8.0 + 150.0 * gloss * gloss;
    float spec = pow(max(dot(n, normalize(l0v + v)), 0.0), ex) + 0.6 * pow(max(dot(n, normalize(l1v + v)), 0.0), ex);
    spec *= gloss * (0.15 + 0.85 * metal) * (0.3 + 1.2 * gloss);
    col += spec * mix(vec3(1.0), min(alb.rgb * 1.6, vec3(1.0)), metal);

    // the world's light (sky and blocks) on everything but what glows of itself
    col *= lightMapColor.rgb;

    float dark = 1.0 - clamp(dot(lightMapColor.rgb, vec3(0.333)), 0.0, 1.0);
    float t = GameTime * 24000.0 / 50.0 * TAU;
    float shimmer = 0.75 + 0.25 * sin(t - texCoord0.y * 90.0 - texCoord0.x * 40.0);
    col += alb.rgb * glow * shimmer * (0.25 + 1.1 * dark);

    fragColor = apply_fog(vec4(min(col, vec3(1.0)), 1.0) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
