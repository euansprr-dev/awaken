#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens (Cinematic and Ultra), the Air's sky buffer (SPEC §22 "Steady Light", the trim): the parts of the sky
// that are made of noise and have no sharp edge (the galaxy's light, the high wisps' cover), once a frame at 512 x 288.
// The composite reads them back with one filtered lookup a sky pixel, where it worked eight to fourteen noise values
// out for every one of them. Stars, the shooting star and the sun's glow stay in the composite: they are sharp or cheap.
// Out: lens_air.glsl's airSlow.
uniform sampler2D FrameSampler;

#include <awaken:lens_frame.glsl>
#include <awaken:lens_air.glsl>

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 f22 = F(22);
    if (f22.w <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 dir = normalize(unproject(frameMat(0), texCoord, 1.0e-5, F(16).x > 0.5));
    vec4 slow = airSlow(dir, F(18).zw, F(12).y, f22.xyz, F(16).z, F(17).x);
    // (a step of noise, so the buffer's eight bits never show as bands in a night sky; the same noise every frame, so it
    // never shimmers)
    fragColor = clamp(slow + (ign(gl_FragCoord.xy, 0.0) - 0.5) / 255.0, 0.0, 1.0);
}
