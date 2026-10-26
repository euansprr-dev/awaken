#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>
#include <awaken:farsight_air.glsl>

// AWAKEN Far Sight 3, City Sight: a real block's face from afar. Its sprite from the block atlas (mip-mapped, so a
// texture fades to its colour with distance as the real chunks' do), tinted as the game tints it, shaded and lit as
// the far terrain beside it (farLight, matched to Lens), by the game's lightmap at the face's own block and sky light
// (dark under roofs, lit windows at night), and hazed by the same air. Hidden where Sodium draws the real section;
// levels cross-fade, dithered, over a band past each level's reach.

uniform samplerBuffer Looks;     // per look: u0 v0 u1 v1, then flags and the sprite's average colour
uniform sampler2D AtlasSampler;
uniform sampler2D LightSampler;
uniform sampler2D SectionSampler; // which sections Sodium has built (FarRenderer.sectionTex)

layout(std140) uniform FarVoxel {
    vec4 VoxelReach;   // x, y, z: levels 0-2's reach (blocks); w the cross-fade band as a share of it
    vec4 VoxelMore;    // x the level's floor (y), y sections in the section mask
};

layout(location = 0) in vec3 vRel;
layout(location = 1) in vec2 vUv;
layout(location = 2) flat in int vLookId;
layout(location = 3) flat in vec3 vTint;
layout(location = 4) flat in vec3 vNormal;
layout(location = 5) flat in vec2 vLight;
layout(location = 6) flat in int vLod;
layout(location = 7) flat in float vFade;

layout(location = 0) out vec4 fragColor;

float bayer4(vec2 p) {
    ivec2 q = ivec2(p) & 3;
    int i = q.x + q.y * 4;
    const float m[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (m[i] + 0.5) / 16.0;
}

void main() {
#ifndef NO_DISCARD
    // hidden only in a section Sodium draws: inside its distance and built (a section it hasn't built keeps this copy
    // of the same blocks, never a hole)
    if (farRealSection(vRel)) {
        vec2 rel = vRel.xz + FarCam.xz;
        ivec2 chunk = (FarCamInt.xz >> 4) + ivec2(floor(rel / 16.0));
        int sy = int(floor((vRel.y + FarFog.z - VoxelMore.x) / 16.0)), sections = int(VoxelMore.y + 0.5);
        if (sy >= 0 && sy < sections && texelFetch(SectionSampler, ivec2(chunk.x & 63, (chunk.y & 63) * sections + sy), 0).r > 0.5) {
            // debug view 2: hidden here for the real chunks, red instead (red that shows is a hole they leave)
            if (FarCam.w > 1.5 && FarCam.w < 2.5) { fragColor = vec4(1.0, 0.0, 0.0, 1.0); return; }
            discard;
        }
    }
    // a level fades in over the band past the finer level's reach and out over the band past its own
    float d = length(vRel.xz), dither = bayer4(gl_FragCoord.xy);
    // a chunk new to City Sight dissolves in as the far terrain under it dissolves out (the coverage mask's complement)
    if (dither >= vFade) discard;
    if (vLod > 0) {
        float from = VoxelReach[vLod - 1], band = from * VoxelReach.w;
        if (d < from || (d < from + band && dither > (d - from) / band)) discard;
    }
    if (vLod < 3) {
        float to = VoxelReach[vLod], band = to * VoxelReach.w;
        if (d > to + band || (d > to && dither <= (d - to) / band)) discard;
    }
#endif
    vec4 vRect = texelFetch(Looks, vLookId * 2), vLook = texelFetch(Looks, vLookId * 2 + 1);
    vec2 size = vRect.zw - vRect.xy;
    vec2 uv = vRect.xy + fract(vUv) * size;
    // (the mip the screen would pick: a horizon slice's pixels are finer, FarView)
    vec4 tex = textureGrad(AtlasSampler, uv, dFdx(vUv) * size * FarView.x, dFdy(vUv) * size * FarView.x);
    int flags = int(vLook.x + 0.5);
    vec3 albedo = tex.rgb;
    // a window closing a dark room: its glass shows the dark room, never a hole
    if ((flags & 16) != 0) albedo = mix(vLook.yzw * 0.3, tex.rgb, tex.a * tex.a);
#ifndef NO_DISCARD
    if ((flags & 1) != 0 && tex.a < 0.5) discard;                    // cut-out: glass frames, bars
    if ((flags & 8) != 0) albedo = mix(vLook.yzw, tex.rgb, tex.a);  // leaves: their holes take the leaves' colour
#else
    // never discarding (the GPU's hidden-surface removal stays on): a cut-out's holes (glass) take the dim room behind
    // them, leaves' holes the leaves' colour
    if ((flags & 1) != 0) albedo = mix(vLook.yzw * 0.45, tex.rgb, tex.a);
    else if ((flags & 8) != 0) albedo = mix(vLook.yzw, tex.rgb, tex.a);
#endif
    float blockLight = (flags & 4) != 0 ? 15.0 : vLight.x;
    vec3 c = albedo * vTint * farLight(vNormal, smoothstep(9.0, 15.0, vLight.y));
    c *= texture(LightSampler, (vec2(blockLight, vLight.y) + 0.5) / 16.0).rgb;
    if (FarCam.w > 2.5) { fragColor = vec4(0.1, 0.9, 0.2, 1.0); return; } // debug view 3: City Sight in green
    fragColor = vec4(farAir(c, vRel), 1.0);
}
