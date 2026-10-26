#version 330
#extension GL_ARB_separate_shader_objects : require

// Sodium 0.9.3-beta.1+mc26.3's terrain vertex shader (CaffeineMC, PolyForm Shield 1.0.0:
// https://polyformproject.org/licenses/shield/1.0.0) with AWAKEN's waving leaves and swaying plants added. Every change
// is marked AWAKEN. Awaken only enables this pack when the loaded Sodium is exactly that version and its own terrain
// shaders hash to what this file was written against (dev.awaken.terrain.client.WaveSupport); otherwise Sodium's file
// is used.
//
// AWAKEN: leaf vertices carry colour alpha 254 and plant vertices 213..253 (baked into models by WavingModel from
// dev.awaken.terrain.PlantSway; the colour pipeline keeps alpha, AO and shade only touch RGB). The wind lives in 27
// texels in the block atlas's bottom-right corner, outside every sprite, written by Awaken once a frame from
// dev.awaken.terrain.Wind (WindTexels): time, camera, direction, steady strength, the gust along the wind, the plants'
// amplitudes and the player's feet, so the canopy and every plant sway with the same gust fronts the falling leaves
// ride. Without its signature nothing moves.

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
const int AWK_TEXELS = 27;

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

// AWAKEN: swaying plants -----------------------------------------------------------------------------------------
// A plant vertex's alpha is 253 - code (PlantSway): code = mode * 8 + level - 1, the vertex moving level / 4 of its
// mode's amplitude, or 40 for a pad floating on water. Bases and anchors keep 255 and never move. Like the leaves, the
// offset is a function of the vertex's world position, its weight and time only, so vertices two parts share (the
// halves of tall grass, the blocks of a stalk) move together and nothing cracks.
const float AWK_PLANT_MIN = 212.5; // alpha * 255 above this and below 253.5: a plant
const int AWK_MODE_ROOTED = 0;
const int AWK_MODE_HANGING = 1;
const int AWK_MODE_WATER = 2;
const int AWK_MODE_WALL_X = 3;
const int AWK_MODE_WALL_Z = 4;
const int AWK_FLOAT_CODE = 40;
// Plants sway out to AWK_PLANT_FAR blocks from the camera, fading from AWK_PLANT_FADE (a few pixels of sway there, and
// far vertices are most of a meadow's: cross models have no faces to cull, so every plant quad is drawn)
const float AWK_PLANT_FADE = 72.0;
const float AWK_PLANT_FAR = 112.0;

// The gust passing a place: the profile along the wind through the camera, 4 blocks a sample (as for the leaves).
float _awk_gust_at(ivec2 base, vec3 cam, vec2 dir, vec2 xz) {
    float gi = clamp(dot(xz - cam.xz, dir) * 0.25 + 31.5, 0.0, 63.0);
    int i0 = int(gi);
    return mix(_awk_gust_sample(base, i0), _awk_gust_sample(base, min(i0 + 1, 63)), gi - float(i0));
}

// Offset for one plant vertex (rel: the vertex relative to the camera); tint comes back above 1 on grass tips while a
// gust front bends them (the sheen that shows a front rolling across a meadow).
vec3 _awk_plant_offset(vec3 regionPos, vec3 rel, int code, out float tint) {
    tint = 1.0;
    float dist2 = dot(rel, rel);
    if (dist2 > AWK_PLANT_FAR * AWK_PLANT_FAR) return vec3(0.0); // no texel read for far plants
    float near = 1.0 - smoothstep(AWK_PLANT_FADE, AWK_PLANT_FAR, sqrt(dist2));
    ivec2 base = textureSize(u_BlockTex, 0) - ivec2(AWK_TEXELS, 1);
    if (_awk_u32(base) != AWK_SIGNATURE) return vec3(0.0);
    float t = _awk_f32(base + ivec2(1, 0));
    vec3 cam = vec3(_awk_f32(base + ivec2(2, 0)), _awk_f32(base + ivec2(3, 0)), _awk_f32(base + ivec2(4, 0)));
    vec4 wind = texelFetch(u_BlockTex, base + ivec2(5, 0), 0);   // direction x, z (0..1 for -1..1), steady, leaf amplitude
    vec4 plant = texelFetch(u_BlockTex, base + ivec2(22, 0), 0); // air and water amplitude, sheen (x 0.5), weather wind here
    if (plant.x == 0.0 && plant.y == 0.0) return vec3(0.0); // plants switched still (debug 3): skip the rest
    vec3 origin = round((u_RegionOffset + cam) / AWK_REGION) * AWK_REGION;
    vec3 wp = origin + regionPos; // x, z modulo 4096; y is the true height

    if (code == AWK_FLOAT_CODE) {
        // a pad rides the ripples: a small bob, out of step across the pad so it rocks a little, more in a wind
        float bob = sin(t * AWK_TAU_P * 717.0 + dot(wp.xz, vec2(1.3, 0.9))) + 0.4 * sin(t * AWK_TAU_P * 1311.0 + dot(wp.xz, vec2(-0.7, 1.1)));
        return vec3(0.0, bob * (0.006 + 0.01 * wind.z) * near, 0.0);
    }

    int mode = code >> 3;
    float weight = float((code & 7) + 1) * 0.25;
    vec3 off = vec3(0.0);

    if (mode == AWK_MODE_WATER) {
        // a slow surge, never the air wind: each stretch of seabed sways along its own axis, and a wave climbs a kelp
        // stalk (the phase falls with height)
        float a = 1.1 * sin(dot(wp.xz, vec2(0.021, 0.017))) + 0.7 * sin(dot(wp.xz, vec2(-0.013, 0.029)));
        vec2 axis = vec2(cos(a), sin(a));
        float ph = dot(wp.xz, vec2(0.31, 0.23)) - wp.y * 0.45;
        float surge = sin(t * AWK_TAU_P * 410.0 + ph);
        float roll = sin(t * AWK_TAU_P * 717.0 + 1.6 * ph + 1.3);
        off.xz = (axis * (0.8 * surge + 0.2 * roll) + vec2(-axis.y, axis.x) * (0.3 * roll)) * (plant.y * 0.5 * weight);
        off.y = -0.5 * dot(off.xz, off.xz) / max(weight, 0.5);
        return off * near;
    }

    vec2 dir = wind.xy * 2.0 - 1.0;
    dir *= inversesqrt(max(dot(dir, dir), 1e-4));
    // deep underground (and in a world without weather) the wind gives way to a faint draught
    float expo = smoothstep(30.0, 50.0, wp.y) * plant.w;
    float gust = _awk_gust_at(base, cam, dir, wp.xz) * expo;
    float s = mix(0.12, min(1.0, wind.z * (0.6 + 0.8 * gust) + 0.25 * gust), expo); // Wind.strength

    // spatial phases fixed in the world (never tied to the turning wind): about a radian from one clump to the next,
    // warped so no row moves in step; strands ripple down their length and swing slower than grass
    float ph = dot(wp.xz, vec2(0.83, 0.47)) + 1.7 * sin(dot(wp.xz, vec2(-0.29, 0.41)));
    bool hanging = mode != AWK_MODE_ROOTED;
    if (hanging) ph += wp.y * 0.7;
    float sway = 0.72 * sin(t * AWK_TAU_P * (hanging ? 922.0 : 1536.0) + ph) + 0.28 * sin(t * AWK_TAU_P * (hanging ? 1597.0 : 2662.0) + 1.37 * ph + 2.1);
    float crossSway = sin(t * AWK_TAU_P * (hanging ? 700.0 : 1167.0) + dot(wp.xz, vec2(-0.61, 0.77)) + 0.5 * ph);
    float lean = s * (0.35 + 0.65 * s);       // a lean downwind that deepens as a front passes
    float swing = (0.3 + 0.5 * s) * (0.4 + 0.6 * expo); // and the to-and-fro around it
    vec2 side = vec2(-dir.y, dir.x);
    off.xz = (dir * (lean + swing * sway) + side * (0.4 * swing * crossSway)) * (plant.x * 0.5 * weight);

    if (mode == AWK_MODE_ROOTED) {
        // parted by the player's feet: tips within about a block lean away and down (the feet are read only near the
        // camera, which is never far from them)
        if (dist2 < 100.0) {
            vec3 feet = vec3(_awk_f32(base + ivec2(23, 0)), _awk_f32(base + ivec2(24, 0)), _awk_f32(base + ivec2(25, 0)));
            vec3 d = wp - feet;
            d.xz -= round(d.xz / 4096.0) * 4096.0;
            float r = length(d.xz);
            if (r < 1.3 && d.y > -0.5 && d.y < 2.6) {
                float push = texelFetch(u_BlockTex, base + ivec2(26, 0), 0).x;
                float k = 1.0 - r / 1.3;
                k *= k;
                float w = min(weight, 1.0);
                off.xz += d.xz / max(r, 0.05) * (k * push * w);
                off.y -= k * push * 0.35 * w;
            }
        }
        tint = 1.0 + plant.z * 0.5 * gust * min(weight, 1.0) * near;
    }
    // a bending stem's tip dips as it leans; a swinging strand's rises
    off.y += (hanging ? 0.5 : -0.5) * dot(off.xz, off.xz) / max(weight, 0.5);
    // a vine stays flat on its wall
    if (mode == AWK_MODE_WALL_X) off.x = 0.0;
    if (mode == AWK_MODE_WALL_Z) off.z = 0.0;
    return off * near;
}
// ----------------------------------------------------------------------------------------------------------------

void main() {
    _vert_init();

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

    // AWAKEN: leaves and plants sway; their marker alpha goes back to opaque so nothing downstream sees it
    vec4 vertColor = _vert_color;
    float awkA = vertColor.a * 255.0;
    if (awkA > AWK_PLANT_MIN && awkA < 254.5) {
        vec3 regionPos = _vert_position + _get_draw_translation(_draw_id);
        if (abs(vertColor.a - AWK_LEAF_ALPHA) < (0.5 / 255.0)) {
            position += _awk_leaf_offset(regionPos);
        } else {
            float tint;
            position += _awk_plant_offset(regionPos, position, 253 - int(awkA + 0.5), tint);
            vertColor.rgb = min(vertColor.rgb * tint, vec3(1.0));
        }
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
