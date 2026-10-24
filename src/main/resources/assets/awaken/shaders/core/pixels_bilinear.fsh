#version 330
#extension GL_ARB_separate_shader_objects : require

// Pixels' plain upscaler (SPEC §18): the low-resolution world stretched to the screen through a linear sampler.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
