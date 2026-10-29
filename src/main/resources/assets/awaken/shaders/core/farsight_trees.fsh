#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>
#include <awaken:farsight_air.glsl>

// AWAKEN Far Sight 2: a far tree's faces. Leaves get a block-by-block grain while a block spans a pixel; faces are lit
// like the terrain (block-face shading, the warm key where the ground's sunlight reaches, the cool shade where a ridge
// hides it) and hazed by the same air. Where the real trees are drawn they step aside whole (farsight_trees.vsh).

uniform sampler2D LightSampler;

layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vColor;
layout(location = 2) flat in vec4 vFace;
layout(location = 3) in vec2 vFaceUv;
layout(location = 4) flat in float vValley;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 rel = vRel.xz + FarCam.xz;
    vec3 c = vColor;
    if (vFaceUv.x >= 0.0) {
        float footprint = max(fwidth(vFaceUv.x), fwidth(vFaceUv.y)) * FarView.x;
        float grain = 1.0 - smoothstep(0.6, 1.6, footprint);
        c *= 1.0 + (farHash(ivec2(floor(vFaceUv * 1.0)) + ivec2(floor(rel))) - 0.5) * 0.3 * grain;
    }
    vec3 n = vFace.xyz;
    c *= farLight(n, vFace.w) * texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;
    fragColor = vec4(farAir(farMist(c, vRel, vValley), vRel), 1.0);
    if (FarCam.w > 2.5) fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
