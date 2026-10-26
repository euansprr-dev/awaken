#version 330
#extension GL_ARB_separate_shader_objects : require

// Sodium 0.9.3-beta.1+mc26.3's terrain vertex shader (CaffeineMC, PolyForm Shield 1.0.0:
// https://polyformproject.org/licenses/shield/1.0.0) with AWAKEN's waving leaves and swaying plants added. Every change
// is marked AWAKEN. Awaken only enables this pack when the loaded Sodium is exactly that version and its own terrain
// shaders hash to what this file was written against (dev.awaken.terrain.client.WaveSupport); otherwise Sodium's file
// is used.
//
// AWAKEN: leaf vertices carry colour alpha 254 and plant vertices 213..253 (baked into models by WavingModel from
// dev.awaken.terrain.PlantSway; the colour pipeline keeps alpha, AO and shade only touch RGB). The wind lives in 45
// texels in the block atlas's bottom-right corner, outside every sprite, written by Awaken once a frame from
// dev.awaken.terrain.Wind (WindTexels, laid out by SwayMath): the sway clock, camera, direction, steady strength, the
// gust along the wind, the plants' amplitudes and the player's feet, so the canopy and every plant sway with the same
// gust fronts the falling leaves ride. Without its signature nothing moves.

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

// AWAKEN: waving leaves and swaying plants ----------------------------------------------------------------------
// A line-by-line mirror of dev.awaken.terrain.SwayMath (the GameTests and the client's probe run that copy): change one,
// change the other. Smooth by construction: every sine takes a phase in turns reduced with fract first (a GPU's sine is
// imprecise far from zero); time phases are whole turns in 2048 s (the clock's period, so its wrap is seamless);
// spatial phases are whole turns in 4096 blocks (the period of the world positions rebuilt here: the camera is written
// modulo 4096 and wraps at x = 0 and z = 0); wind direction, steady strength and gusts are 16-bit; the gust profile is
// sampled on a grid fixed in the world.
const float AWK_LEAF_ALPHA = 254.0 / 255.0;
const uint AWK_SIGNATURE = 0x444E5741u; // "AWND", little-endian
const float AWK_TAU = 6.28318530718;
const vec3 AWK_REGION = vec3(128.0, 64.0, 128.0); // Sodium's render region: 8 x 4 x 8 sections
const int AWK_TEXELS = 45;
// texels: 0 signature, 1 sway clock (s mod 2048, float), 2-4 camera (x, z mod 4096), 5 wind direction x, z (16-bit),
// 6 steady (16-bit) and leaf amplitude (x 0.25 blocks), 7 gust grid offset (float), 8-39 gust profile (64 x 16-bit),
// 40 plants: air and water amplitude (x 0.5 blocks), sheen (x 0.5), weather wind here, 41-43 the player's feet,
// 44 how far the feet push tips aside (x 1 block)
const int AWK_T_TIME = 1;
const int AWK_T_CAM = 2;
const int AWK_T_DIR = 5;
const int AWK_T_STEADY = 6;
const int AWK_T_PROFILE_OFF = 7;
const int AWK_T_GUST = 8;
const int AWK_T_PLANT = 40;
const int AWK_T_FEET = 41;
const int AWK_T_PUSH = 44;

vec4 _awk_bytes(ivec2 p) {
    return round(texelFetch(u_BlockTex, p, 0) * 255.0);
}

uint _awk_u32(ivec2 p) {
    uvec4 b = uvec4(_awk_bytes(p));
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

// sin of a phase in turns, reduced first
float _awk_sin(float turns) {
    return sin(AWK_TAU * fract(turns));
}

// a time phase: k whole turns in 2048 s
float _awk_tt(float t, float k) {
    return t * (k / 2048.0);
}

// a phase fixed in the world: (nx, nz) whole turns in 4096 blocks
float _awk_wt(vec2 xz, vec2 n) {
    return dot(xz, n) * (1.0 / 4096.0);
}

// The wind header every swaying vertex reads.
struct AwkWind {
    ivec2 base;
    float t;
    vec3 cam;
    vec2 dir;
    float steady;
    float leafAmp;
    float profileOff;
};

bool _awk_wind(out AwkWind w) {
    w.base = textureSize(u_BlockTex, 0) - ivec2(AWK_TEXELS, 1);
    if (_awk_u32(w.base) != AWK_SIGNATURE) return false;
    w.t = _awk_f32(w.base + ivec2(AWK_T_TIME, 0));
    w.cam = vec3(_awk_f32(w.base + ivec2(AWK_T_CAM, 0)), _awk_f32(w.base + ivec2(AWK_T_CAM + 1, 0)), _awk_f32(w.base + ivec2(AWK_T_CAM + 2, 0)));
    vec4 d = _awk_bytes(w.base + ivec2(AWK_T_DIR, 0));
    vec2 dir = vec2(d.r + d.g * 256.0, d.b + d.a * 256.0) * (2.0 / 65535.0) - 1.0;
    w.dir = dir * inversesqrt(max(dot(dir, dir), 1e-4));
    vec4 st = _awk_bytes(w.base + ivec2(AWK_T_STEADY, 0));
    w.steady = (st.r + st.g * 256.0) / 65535.0;
    w.leafAmp = st.b / 255.0 * 0.25;
    w.profileOff = _awk_f32(w.base + ivec2(AWK_T_PROFILE_OFF, 0));
    return true;
}

// World position modulo 4096: the region's origin is a whole number of regions from the camera, and the camera
// Awaken wrote (last frame's) is far closer than half a region to this frame's.
vec3 _awk_world(AwkWind w, vec3 regionPos) {
    return round((u_RegionOffset + w.cam) / AWK_REGION) * AWK_REGION + regionPos;
}

// One gust sample of the profile (16-bit, two to a texel).
float _awk_gust_sample(AwkWind w, int i) {
    vec4 b = _awk_bytes(w.base + ivec2(AWK_T_GUST + (i >> 1), 0));
    return ((i & 1) == 0 ? b.r + b.g * 256.0 : b.b + b.a * 256.0) / 65535.0;
}

// The gust passing a place: the profile along the wind, 4 blocks a sample on a grid fixed in the world.
float _awk_gust_at(AwkWind w, vec2 xz) {
    float gi = clamp((dot(xz - w.cam.xz, w.dir) - w.profileOff) * 0.25 + 31.5, 0.0, 63.0);
    int i0 = int(gi);
    return mix(_awk_gust_sample(w, i0), _awk_gust_sample(w, min(i0 + 1, 63)), gi - float(i0));
}

// Offset for one leaf vertex. regionPos is the vertex relative to its render region's origin. The offset is a function
// of the vertex's world position and time only, so the corners two leaf blocks share move together (no cracks).
vec3 _awk_leaf_offset(vec3 regionPos) {
    AwkWind w;
    if (!_awk_wind(w)) return vec3(0.0);
    vec3 wp = _awk_world(w, regionPos);
    float gust = _awk_gust_at(w, wp.xz);
    float push = min(1.0, w.steady * (0.6 + 0.8 * gust) + 0.25 * gust); // Wind.strength
    push *= 0.4 + 0.6 * gust; // the sway itself mostly comes with the gusts

    // sway along the wind, a slower cross-sway, and a fast flutter that differs per vertex; the spatial phases are
    // fixed in the world (they never depend on the turning wind), the travelling comes from the gust fronts
    float sway = _awk_sin(_awk_tt(w.t, 551.0) + _awk_wt(wp.xz, vec2(150.0, 202.0)) + wp.y * (0.21 / AWK_TAU));
    float crossSway = _awk_sin(_awk_tt(w.t, 945.0) + _awk_wt(wp.xz, vec2(372.0, -274.0)) + wp.y * (0.37 / AWK_TAU));
    ivec3 q = ivec3(floor(wp * 16.0 + 0.5));
    uint h = _awk_hash(uvec3(ivec3(q.x & 65535, q.y + 1048576, q.z & 65535)));
    float flutter = _awk_sin(_awk_tt(w.t, 2376.0) + float(h & 1023u) / 1024.0);

    vec2 side = vec2(-w.dir.y, w.dir.x);
    vec3 offset;
    offset.xz = w.dir * (0.5 + 0.5 * sway + 0.2 * flutter) + side * (0.35 * crossSway);
    offset.y = 0.3 * crossSway * sway + 0.25 * flutter;
    return offset * (w.leafAmp * push);
}

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

// Offset for one plant vertex (rel: the vertex relative to the camera); tint comes back above 1 on grass tips while a
// gust front bends them (the sheen that shows a front rolling across a meadow).
vec3 _awk_plant_offset(vec3 regionPos, vec3 rel, int code, out float tint) {
    tint = 1.0;
    float dist2 = dot(rel, rel);
    if (dist2 > AWK_PLANT_FAR * AWK_PLANT_FAR) return vec3(0.0); // no texel read for far plants
    float dist = sqrt(dist2);
    float near = 1.0 - smoothstep(AWK_PLANT_FADE, AWK_PLANT_FAR, dist);
    AwkWind w;
    if (!_awk_wind(w)) return vec3(0.0);
    vec4 plant = _awk_bytes(w.base + ivec2(AWK_T_PLANT, 0)) / 255.0;
    float air = plant.x * 0.5, water = plant.y * 0.5, sheen = plant.z * 0.5;
    if (air == 0.0 && water == 0.0) return vec3(0.0); // plants switched still (debug 3): skip the rest
    vec3 wp = _awk_world(w, regionPos); // x, z modulo 4096; y is the true height

    if (code == AWK_FLOAT_CODE) {
        // a pad rides the ripples: a small bob, out of step across the pad so it rocks a little, more in a wind
        float bob = _awk_sin(_awk_tt(w.t, 717.0) + _awk_wt(wp.xz, vec2(847.0, 587.0)))
                + 0.4 * _awk_sin(_awk_tt(w.t, 1311.0) + _awk_wt(wp.xz, vec2(-456.0, 717.0)));
        return vec3(0.0, bob * (0.006 + 0.01 * w.steady) * near, 0.0);
    }

    int mode = code >> 3;
    float weight = float((code & 7) + 1) * 0.25;
    vec3 off = vec3(0.0);

    if (mode == AWK_MODE_WATER) {
        // a slow surge, never the air wind: each stretch of seabed sways along its own axis, and a wave climbs a kelp
        // stalk (the phase falls with height)
        float a = 1.1 * _awk_sin(_awk_wt(wp.xz, vec2(14.0, 11.0))) + 0.7 * _awk_sin(_awk_wt(wp.xz, vec2(-8.0, 19.0)));
        vec2 axis = vec2(cos(a), sin(a));
        float ph = _awk_wt(wp.xz, vec2(202.0, 150.0)) - wp.y * (0.45 / AWK_TAU);
        float ph2 = _awk_wt(wp.xz, vec2(323.0, 240.0)) - wp.y * (0.72 / AWK_TAU) + 1.3 / AWK_TAU;
        float surge = _awk_sin(_awk_tt(w.t, 410.0) + ph);
        float roll = _awk_sin(_awk_tt(w.t, 717.0) + ph2);
        off.xz = (axis * (0.8 * surge + 0.2 * roll) + vec2(-axis.y, axis.x) * (0.3 * roll)) * (water * weight);
        off.y = -0.5 * dot(off.xz, off.xz) / max(weight, 0.5);
        return off * near;
    }

    // deep underground (and in a world without weather) the wind gives way to a faint draught
    float expo = smoothstep(30.0, 50.0, wp.y) * plant.w;
    float gust = _awk_gust_at(w, wp.xz) * expo;
    float s = mix(0.12, min(1.0, w.steady * (0.6 + 0.8 * gust) + 0.25 * gust), expo); // Wind.strength

    // spatial phases fixed in the world (never tied to the turning wind): about a radian from one clump to the next,
    // warped so no row moves in step; strands ripple down their length and swing slower than grass
    float warp = (1.7 / AWK_TAU) * _awk_sin(_awk_wt(wp.xz, vec2(-189.0, 267.0)));
    bool hanging = mode != AWK_MODE_ROOTED;
    float yt = hanging ? wp.y * (0.7 / AWK_TAU) : 0.0;
    float ph1 = _awk_wt(wp.xz, vec2(541.0, 306.0)) + warp + yt;
    float ph2 = _awk_wt(wp.xz, vec2(741.0, 420.0)) + 1.37 * (warp + yt) + 2.1 / AWK_TAU;
    float phc = _awk_wt(wp.xz, vec2(-128.0, 655.0)) + 0.5 * (warp + yt);
    float sway = 0.72 * _awk_sin(_awk_tt(w.t, hanging ? 922.0 : 1536.0) + ph1) + 0.28 * _awk_sin(_awk_tt(w.t, hanging ? 1597.0 : 2662.0) + ph2);
    float crossSway = _awk_sin(_awk_tt(w.t, hanging ? 700.0 : 1167.0) + phc);
    float lean = s * (0.35 + 0.65 * s);       // a lean downwind that deepens as a front passes
    float swing = (0.3 + 0.5 * s) * (0.4 + 0.6 * expo); // and the to-and-fro around it
    vec2 side = vec2(-w.dir.y, w.dir.x);
    off.xz = (w.dir * (lean + swing * sway) + side * (0.4 * swing * crossSway)) * (air * weight);

    if (mode == AWK_MODE_ROOTED) {
        // parted by the player's feet: tips within about a block lean away and down (the feet are read only near the
        // camera, which is never far from them)
        if (dist2 < 100.0) {
            vec3 feet = vec3(_awk_f32(w.base + ivec2(AWK_T_FEET, 0)), _awk_f32(w.base + ivec2(AWK_T_FEET + 1, 0)), _awk_f32(w.base + ivec2(AWK_T_FEET + 2, 0)));
            vec3 d = wp - feet;
            d.xz -= round(d.xz / 4096.0) * 4096.0;
            float r = length(d.xz);
            if (r < 1.3 && d.y > -0.5 && d.y < 2.6) {
                float push = _awk_bytes(w.base + ivec2(AWK_T_PUSH, 0)).x / 255.0;
                float k = 1.0 - r / 1.3;
                k *= k;
                float wt = min(weight, 1.0);
                off.xz += d.xz / max(r, 0.05) * (k * push * wt);
                off.y -= k * push * 0.35 * wt;
            }
        }
        tint = 1.0 + sheen * 0.5 * gust * min(weight, 1.0) * near;
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
