#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens, pass 3: one wide, soft blur at quarter size. Bilinear taps at two rings make thirteen texels' worth of
// filter from nine samples; at this size the glow spreads across about forty screen pixels.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 t = 1.0 / InSize;
    vec4 s = texture(InSampler, texCoord) * 0.20;
    s += (texture(InSampler, texCoord + t * vec2(-1.5, -1.5)) + texture(InSampler, texCoord + t * vec2(1.5, -1.5))
        + texture(InSampler, texCoord + t * vec2(-1.5, 1.5)) + texture(InSampler, texCoord + t * vec2(1.5, 1.5))) * 0.12;
    s += (texture(InSampler, texCoord + t * vec2(-3.5, 0.0)) + texture(InSampler, texCoord + t * vec2(3.5, 0.0))
        + texture(InSampler, texCoord + t * vec2(0.0, -3.5)) + texture(InSampler, texCoord + t * vec2(0.0, 3.5))) * 0.08;
    fragColor = s;
}
