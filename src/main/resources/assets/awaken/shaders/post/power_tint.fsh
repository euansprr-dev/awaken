#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// AWAKEN power tint: desaturate, tint, vignette and an optional slow pulse. One shader, many post effects.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform TintConfig {
    vec4 Tint;    // rgb tint, a = tint strength
    vec4 Params;  // x = desaturate, y = vignette, z = pulse, w = unused
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, vec3(luma), Params.x);
    float pulse = 1.0 + Params.z * 0.25 * sin(GameTime * 2400.0);
    c = mix(c, c * Tint.rgb * 1.6, Tint.a * pulse);
    vec2 d = texCoord - vec2(0.5);
    float v = smoothstep(0.85, 0.25, length(d) * (1.0 + Params.y));
    c *= mix(1.0, v, Params.y);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
