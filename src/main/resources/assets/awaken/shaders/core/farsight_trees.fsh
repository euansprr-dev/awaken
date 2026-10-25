#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>
#include <awaken:farsight_air.glsl>

// AWAKEN Far Sight 2: a far tree's faces. Leaves get a block-by-block grain while a block spans a pixel; faces are lit
// like the terrain (block-face shading, the warm key where the ground's sunlight reaches, the cool shade where a ridge
// hides it) and hazed by the same air. Under the real chunks they stay hidden, where the chunks are there.

uniform sampler2D LightSampler;
uniform sampler2D ChunkSampler;

layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vColor;
layout(location = 2) flat in vec4 vFace;
layout(location = 3) in vec2 vFaceUv;

layout(location = 0) out vec4 fragColor;

float bayer4(vec2 p) {
    ivec2 q = ivec2(p) & 3;
    int i = q.x + q.y * 4;
    const float m[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (m[i] + 0.5) / 16.0;
}

void main() {
    float horizontal = length(vRel.xz);
    vec2 rel = vRel.xz + FarCam.xz;
    if (horizontal < FarDepth.z + FarDepth.w * bayer4(gl_FragCoord.xy)) {
        ivec2 chunk = (FarCamInt.xz >> 4) + ivec2(floor(rel / 16.0));
        if (texelFetch(ChunkSampler, chunk & 63, 0).r > 0.5) discard;
    }
    vec3 c = vColor;
    if (vFaceUv.x >= 0.0) {
        float footprint = max(fwidth(vFaceUv.x), fwidth(vFaceUv.y));
        float grain = 1.0 - smoothstep(0.6, 1.6, footprint);
        c *= 1.0 + (farHash(ivec2(floor(vFaceUv * 1.0)) + ivec2(floor(rel))) - 0.5) * 0.3 * grain;
    }
    vec3 n = vFace.xyz;
    c *= farLight(n, max(dot(n, FarSun.xyz), 0.0) * vFace.w) * texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;
    fragColor = vec4(farAir(farMist(c, vRel), vRel), 1.0);
}
