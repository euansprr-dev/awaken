#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens, pass 2: halve again (dual-filter downsample: the centre plus four diagonal bilinear taps).
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 t = 1.0 / InSize;
    vec4 s = texture(InSampler, texCoord) * 4.0;
    s += texture(InSampler, texCoord + t * vec2(-1.0, -1.0));
    s += texture(InSampler, texCoord + t * vec2(1.0, -1.0));
    s += texture(InSampler, texCoord + t * vec2(-1.0, 1.0));
    s += texture(InSampler, texCoord + t * vec2(1.0, 1.0));
    fragColor = s * 0.125;
}
