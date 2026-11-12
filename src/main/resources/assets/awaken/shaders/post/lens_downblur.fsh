#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens, the wide glow in one pass (SPEC §22 "Steady Light", the trim): lens_down then lens_blur, as one shader
// that reads the quarter-size picture and writes the eighth-size glow. Each of the blur's nine taps is the
// downsample's five reads taken at that tap's own place, so the filter is the same two steps' and the eighth-size
// picture between them is never stored. 45 reads a pixel on 36 thousand pixels is nothing; a pass and a target are not.
uniform sampler2D InSampler;      // quarter size, bilinear

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

// lens_down at a place: the eighth-size picture's value there
vec4 down(vec2 at) {
    vec2 t = 1.0 / InSize;
    vec4 s = texture(InSampler, at) * 4.0;
    s += texture(InSampler, at + t * vec2(-1.0, -1.0));
    s += texture(InSampler, at + t * vec2(1.0, -1.0));
    s += texture(InSampler, at + t * vec2(-1.0, 1.0));
    s += texture(InSampler, at + t * vec2(1.0, 1.0));
    return s * 0.125;
}

void main() {
    // lens_blur's rings, in texels of the eighth-size picture (this pass's own output)
    vec2 t = 1.0 / OutSize;
    vec4 s = down(texCoord) * 0.20;
    s += (down(texCoord + t * vec2(-1.5, -1.5)) + down(texCoord + t * vec2(1.5, -1.5))
        + down(texCoord + t * vec2(-1.5, 1.5)) + down(texCoord + t * vec2(1.5, 1.5))) * 0.12;
    s += (down(texCoord + t * vec2(-3.5, 0.0)) + down(texCoord + t * vec2(3.5, 0.0))
        + down(texCoord + t * vec2(0.0, -3.5)) + down(texCoord + t * vec2(0.0, 3.5))) * 0.08;
    fragColor = s;
}
