#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// Meditation: slow rings move out from the centre of your sight, and the edges soften.

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
    vec2 d = (uv - 0.5) * aspect;
    float r = length(d);

    float wave = sin(r * 36.0 - ticks * 0.30) * exp(-r * 2.4);
    vec2 dir = r > 0.0001 ? d / r : vec2(0.0);
    vec2 offset = dir * wave * 0.0032 / aspect;
    vec3 col = texture(InSampler, uv + offset).rgb;

    float glow = 0.035 * (0.5 + 0.5 * sin(ticks * 0.07));
    col = col * (1.0 - 0.20 * smoothstep(0.32, 0.95, r)) + vec3(0.55, 0.75, 1.0) * glow * (1.0 - min(r, 1.0));
    fragColor = vec4(col, 1.0);
}
