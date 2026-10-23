#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// The Lucidium dream look: a slow hue drift, a soft wobble with a faint ghost image, a pastel lift and a lilac
// vignette. The lucid variant uses the same shader with smaller numbers: the world sharpens as you wake up inside it.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DreamConfig {
    float Wobble;
    float HueDrift;
    float Vignette;
    float Saturation;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float TAU = 6.2831853;

vec3 hueRotate(vec3 c, float a) {
    const vec3 k = vec3(0.57735027);
    float ca = cos(a);
    float sa = sin(a);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

void main() {
    // GameTime is the day fraction; periods below divide 24000 ticks so the loop never jumps
    float t = GameTime * 24000.0;
    vec2 uv = texCoord;
    uv.x += sin(uv.y * 11.0 + t * TAU / 120.0) * Wobble;
    uv.y += cos(uv.x * 9.0 + t * TAU / 200.0) * Wobble * 0.8;
    vec3 col = texture(InSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;

    vec2 ghost = uv + vec2(sin(t * TAU / 600.0), cos(t * TAU / 480.0)) * Wobble * 3.0;
    col = mix(col, texture(InSampler, clamp(ghost, vec2(0.0), vec2(1.0))).rgb, 0.12 * step(0.001, Wobble));

    col = hueRotate(col, sin(t * TAU / 1200.0) * HueDrift);
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, Saturation);
    col = mix(col, col * 0.86 + vec3(0.06, 0.03, 0.09), 0.5);

    vec2 d = texCoord - 0.5;
    float v = smoothstep(0.35, 0.85, length(d) * 1.2);
    col = mix(col, col * vec3(0.72, 0.60, 0.86), v * Vignette);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
