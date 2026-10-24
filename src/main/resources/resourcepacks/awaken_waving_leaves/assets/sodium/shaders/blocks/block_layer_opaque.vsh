#version 330
#extension GL_ARB_separate_shader_objects : require

// Sodium 0.9.3-beta.1+mc26.3's terrain vertex shader (CaffeineMC, PolyForm Shield 1.0.0:
// https://polyformproject.org/licenses/shield/1.0.0) with AWAKEN's waving leaves added. Every change is marked AWAKEN.
// Awaken only enables this pack when the loaded Sodium is exactly that version and its own terrain shaders hash to
// what this file was written against (dev.awaken.terrain.client.WaveSupport); otherwise Sodium's file is used.
//
// AWAKEN: leaf vertices carry colour alpha 254 (baked into leaf models by WavingLeavesModel; the colour pipeline keeps
// alpha, AO and shade only touch RGB). The wind lives in 22 texels in the block atlas's bottom-right corner, outside
// every sprite, written by Awaken once a frame from dev.awaken.terrain.Wind (WindTexels): time, camera, direction,
// steady strength and the gust along the wind, so the canopy sways with the same gust fronts the falling leaves ride.
// Without its signature nothing moves.

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_vertex.glsl>

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;

#ifdef USE_FOG
layout(location = 2) out vec2 v_FragDistance;
layout(location = 3) out float fadeFactor;
#endif

uniform isamplerBuffer u_SectionTimeInfo;

#ifdef VULKAN
layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};
#else
uniform vec3 u_RegionOffset;
uniform int u_CurrentTime;
uniform uint u_RegionID;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D u_LightTex; // The light map texture sampler
#endif

// AWAKEN: the block atlas, read here for the wind texels (the same binding the fragment shader samples)
uniform sampler2D u_BlockTex;

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

// AWAKEN: waving leaves ------------------------------------------------------------------------------------------
const float AWK_LEAF_ALPHA = 254.0 / 255.0;
const uint AWK_SIGNATURE = 0x444E5741u; // "AWND", little-endian
const float AWK_TAU_P = 6.28318530718 / 2048.0; // time wraps every 2048 s, so every frequency is a whole number of turns
const vec3 AWK_REGION = vec3(128.0, 64.0, 128.0); // Sodium's render region: 8 x 4 x 8 sections
const int AWK_TEXELS = 22;

uint _awk_u32(ivec2 p) {
    uvec4 b = uvec4(round(texelFetch(u_BlockTex, p, 0) * 255.0));
    return b.r | (b.g << 8u) | (b.b << 16u) | (b.a << 24u);
}

float _awk_f32(ivec2 p) {
    return uintBitsToFloat(_awk_u32(p));
}

uint _awk_hash(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}

// One gust sample of the profile along the wind (64 bytes, 4 to a texel, after the 6 header texels).
float _awk_gust_sample(ivec2 base, int i) {
    return texelFetch(u_BlockTex, base + ivec2(6 + (i >> 2), 0), 0)[i & 3];
}

// Offset for one leaf vertex. regionPos is the vertex relative to its render region's origin. The offset is a function
// of the vertex's world position and time only, so the corners two leaf blocks share move together (no cracks).
vec3 _awk_leaf_offset(vec3 regionPos) {
    ivec2 base = textureSize(u_BlockTex, 0) - ivec2(AWK_TEXELS, 1);
    if (_awk_u32(base) != AWK_SIGNATURE) return vec3(0.0);
    float t = _awk_f32(base + ivec2(1, 0));
    vec3 cam = vec3(_awk_f32(base + ivec2(2, 0)), _awk_f32(base + ivec2(3, 0)), _awk_f32(base + ivec2(4, 0)));
    vec4 wind = texelFetch(u_BlockTex, base + ivec2(5, 0), 0); // direction x, z (0..1 for -1..1), steady, amplitude

    // World position modulo 4096: the region's origin is a whole number of regions from the camera, and the camera
    // Awaken wrote (last frame's) is far closer than half a region to this frame's.
    vec3 origin = round((u_RegionOffset + cam) / AWK_REGION) * AWK_REGION;
    vec3 wp = origin + regionPos;

    vec2 dir = wind.xy * 2.0 - 1.0;
    dir *= inversesqrt(max(dot(dir, dir), 1e-4));
    float steady = wind.z;
    float amp = wind.w * 0.25;

    // the gust passing this leaf: the profile along the wind through the camera, 4 blocks a sample
    float gi = clamp(dot(wp.xz - cam.xz, dir) * 0.25 + 31.5, 0.0, 63.0);
    int i0 = int(gi);
    float gust = mix(_awk_gust_sample(base, i0), _awk_gust_sample(base, min(i0 + 1, 63)), gi - float(i0));
    float push = min(1.0, steady * (0.6 + 0.8 * gust) + 0.25 * gust); // Wind.strength
    push *= 0.4 + 0.6 * gust; // the sway itself mostly comes with the gusts

    // sway along the wind, a slower cross-sway, and a fast flutter that differs per vertex; the spatial phases are
    // fixed in the world (they never depend on the turning wind), the travelling comes from the gust fronts
    float sway = sin(t * AWK_TAU_P * 551.0 + dot(wp, vec3(0.23, 0.21, 0.31)));
    float crossSway = sin(t * AWK_TAU_P * 945.0 + dot(wp, vec3(0.57, 0.37, -0.42)));
    uint h = _awk_hash(uvec3(ivec3(floor(wp * 16.0 + 0.5)) + 1048576));
    float flutter = sin(t * AWK_TAU_P * 2376.0 + float(h & 1023u) * (6.28318530718 / 1024.0));

    vec2 side = vec2(-dir.y, dir.x);
    vec3 offset;
    offset.xz = dir * (0.5 + 0.5 * sway + 0.2 * flutter) + side * (0.35 * crossSway);
    offset.y = 0.3 * crossSway * sway + 0.25 * flutter;
    return offset * (amp * push);
}
// ----------------------------------------------------------------------------------------------------------------

void main() {
    _vert_init();

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

    // AWAKEN: leaves sway; their marker alpha goes back to opaque so nothing downstream sees it
    vec4 vertColor = _vert_color;
    if (abs(vertColor.a - AWK_LEAF_ALPHA) < (0.5 / 255.0)) {
        position += _awk_leaf_offset(_vert_position + _get_draw_translation(_draw_id));
        vertColor.a = 1.0;
    }

#ifdef USE_FOG
    v_FragDistance = getFragDistance(position);

    int chunkId = int(_draw_id);
    int chunkFade = texelFetch(u_SectionTimeInfo, int((u_RegionID * 256u) + uint(chunkId))).r;
    int fadeTime = u_CurrentTime - chunkFade;
    float elapsed = float(fadeTime);
    float fade = clamp(float(u_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);
    fadeFactor = (chunkFade < 0) ? 1.0 : fade;
#endif

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
#ifndef OIT_ALPHA_ONLY
    v_Color = vertColor * texture(u_LightTex, _vert_tex_light_coord); // AWAKEN: vertColor (was _vert_color)
#else
    v_Color = vertColor; // AWAKEN: vertColor (was _vert_color)
#endif

    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord; // FMA for precision
}
