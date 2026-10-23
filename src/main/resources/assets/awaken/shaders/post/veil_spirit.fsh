#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// The spirit's eyes: colder, quieter, a soft vignette, and a faint breathing shimmer at the edges.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    float ticks = GameTime * 24000.0;
    vec2 uv = texCoord;
    vec2 aspect = vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    vec2 c = (uv - 0.5) * aspect;
    float r = length(c);

    float edge = smoothstep(0.30, 0.85, r);
    float wob = sin(uv.y * 34.0 + ticks * 0.12) * 0.0015 * edge;
    float split = 0.0018 * edge;
    vec3 col;
    col.r = texture(InSampler, uv + vec2(wob + split, 0.0)).r;
    col.g = texture(InSampler, uv + vec2(wob, 0.0)).g;
    col.b = texture(InSampler, uv + vec2(wob - split, 0.0)).b;

    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 cool = mix(vec3(luma), col, 0.5) * vec3(0.82, 0.93, 1.10) + vec3(0.015, 0.035, 0.07);
    float breath = 0.5 + 0.5 * sin(ticks * 0.05);
    float vignette = 1.0 - smoothstep(0.38, 0.98, r) * (0.48 + 0.06 * breath);
    fragColor = vec4(cool * vignette, 1.0);
}
