#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN: the grey around a broken gate. Desaturates toward a cold grey and lifts the blacks a little,
// with a soft vignette so the edges of the world drain first.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlightConfig {
    float Amount;
    vec3 Tint;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);
    float luma = dot(c.rgb, vec3(0.3, 0.59, 0.11));
    vec3 grey = vec3(luma) * Tint + 0.04;
    vec2 d = texCoord - 0.5;
    float vignette = clamp(dot(d, d) * 1.8, 0.0, 1.0);
    float k = clamp(Amount + vignette * 0.25, 0.0, 1.0);
    fragColor = vec4(mix(c.rgb, grey, k), 1.0);
}
