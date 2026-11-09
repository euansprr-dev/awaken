#version 330
#extension GL_ARB_separate_shader_objects : require

// Sampler0: the weapon's albedo (left half) beside its material map (right half: r metal, g gloss, b emissive, a flow).

#include <minecraft:globals.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec4 accentAlong;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec3 viewNormal;
layout(location = 3) flat in vec2 state;

layout(location = 0) out vec4 fragColor;

const vec3 L0 = vec3(0.2316, 0.9648, -0.1234), L1 = vec3(-0.5145, 0.7717, 0.3738);
const vec3 KEY = vec3(-0.4511, 0.7018, 0.5514);
const vec3 SKY = vec3(0.78, 0.86, 1.0), GROUND = vec3(0.40, 0.36, 0.34), STRIP = vec3(1.0, 0.97, 0.9);
const float TAU = 6.2831853;

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
    vec3 accent = accentAlong.rgb;
    float along = accentAlong.a;
    vec3 n = normalize(viewNormal);
    float d = max(dot(n, L0), 0.0) + max(dot(n, L1), 0.0);
    float diffuse = min(1.0, d * 0.6 + 0.4);
    diffuse = floor(diffuse * 5.0 + 0.5) / 5.0 * 0.6 + diffuse * 0.4;
    vec3 v = vec3(0.0, 0.0, 1.0);
    vec3 r = reflect(-v, n);
    vec3 col = mix(alb.rgb * diffuse, alb.rgb * (0.28 + 0.9 * studio(r)) * (0.75 + 0.25 * diffuse), metal);
    col += pow(max(dot(r, KEY), 0.0), 6.0 + 84.0 * gloss) * gloss * mix(vec3(1.0), min(alb.rgb * 1.7, vec3(1.0)), metal) * 0.85;
    float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 3.0);
    col += accent * rim * 0.072;
    float t = GameTime * 240.0;
    float band = exp(-pow((along - (fract(t) * 1.8 - 0.4)) * 7.0, 2.0));
    col += band * (0.12 + 0.55 * metal) * gloss * 0.6;
    float pulse = flow > 0.0 ? 0.55 + 0.45 * sin(t * TAU * 0.6 - flow * 12.0) : 1.0;
    col += alb.rgb * emit * pulse * 1.6;

    int mode = int(state.x + 0.5);
    if (mode == 1) col = vec3(0.018, 0.018, 0.024) + vec3(0.34, 0.42, 0.60) * (rim * 1.5 + pow(max(r.y, 0.0), 4.0) * 0.3);
    else if (mode == 2) col *= 0.74;
    else if (mode == 3) col = vec3(0.06, 0.055, 0.052) + vec3(0.62, 0.56, 0.48) * (rim * 1.5 + pow(max(r.y, 0.0), 4.0) * 0.42);
    float a = state.y;
    fragColor = vec4(min(col, vec3(1.0)) * a, a);
}
