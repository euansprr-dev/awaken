#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight: what a far column is made of. Tops keep their colour with a block-by-block texture (fading out
// once a block is smaller than a pixel); terrace edges get a sunlit rim where the ground drops toward the camera and a
// contact shadow where it rises; walls show what lies under the top: dirt then stone under grass, snow over stone,
// sandstone under sand, and in badlands the banded terracotta of the real thing. Lit by the game's own light (the
// lightmap's full-sky texel: day, night, storms and the dimension's ambient), fogged like vanilla terrain with the
// horizon fog moved out to Far Sight's end. Under the real chunks it stays hidden, and dithers in at their edge.

uniform sampler2D LightSampler;

layout(location = 0) in vec3 vColor;
layout(location = 1) in vec3 vRel;
layout(location = 2) in float vWater;
layout(location = 3) in float vLight;
layout(location = 4) in vec4 vEdge;
layout(location = 5) in float vDepth;

layout(location = 0) out vec4 fragColor;

const vec3 STONE = vec3(0.47, 0.46, 0.45), DIRT = vec3(0.45, 0.32, 0.22);

float bayer4(vec2 p) {
    ivec2 q = ivec2(p) & 3;
    int i = q.x + q.y * 4;
    const float m[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (m[i] + 0.5) / 16.0;
}

// badlands strata: terracotta bands a block or three thick, the same at every distance
vec3 band(float y) {
    const vec3 c[8] = vec3[](vec3(0.63, 0.33, 0.15), vec3(0.60, 0.37, 0.27), vec3(0.82, 0.69, 0.63), vec3(0.63, 0.33, 0.15),
                             vec3(0.73, 0.52, 0.21), vec3(0.56, 0.24, 0.18), vec3(0.53, 0.42, 0.38), vec3(0.30, 0.20, 0.14));
    return c[int(floor(farHash(ivec2(int(floor(y / 2.0)), 77)) * 8.0))];
}

bool badlands(vec3 c) { return c.r > c.g * 1.3 && c.r > c.b * 1.6; }

vec3 wall(vec3 top, float depth, float y) {
    if (badlands(top)) return band(y);
    bool green = top.g > top.r * 1.08 && top.g > top.b;
    if (green) return depth < 0.7 ? top : mix(DIRT, STONE, smoothstep(3.0, 8.0, depth));
    if (min(top.r, min(top.g, top.b)) > 0.72) return depth < 0.7 ? top : mix(top * 0.9, STONE, smoothstep(1.0, 5.0, depth));
    if (top.r > 0.6 && top.g > 0.55 && top.b < top.g) return mix(top * 0.95, vec3(0.73, 0.67, 0.5), smoothstep(0.5, 3.0, depth));
    return mix(top, STONE, smoothstep(2.0, 6.0, depth));
}

void main() {
    float horizontal = length(vRel.xz);
    if (horizontal < FarDepth.z + FarDepth.w * bayer4(gl_FragCoord.xy)) discard;
    vec3 world = vec3(vRel.x + FarCam.x, vRel.y + FarFog.z, vRel.z + FarCam.z);
    // how many blocks one pixel spans here: the block texture fades before it would shimmer
    float footprint = max(fwidth(world.x), fwidth(world.z));
    float grain = 1.0 - smoothstep(0.6, 1.6, footprint);
    vec3 c = vColor;
    float shade = vLight;
    if (vDepth >= 0.0) {
        c = wall(vColor, vDepth, world.y);
        // walls darken toward their foot, where the ground below crowds them
        shade *= mix(1.0, 0.78, smoothstep(1.0, 6.0, vDepth));
        ivec2 blk = ivec2(floor(world.y), int(floor(world.x + world.z)));
        c *= 1.0 + (farHash(blk) - 0.5) * 0.14 * grain;
    } else if (vWater > 0.5) {
        // shallows show their sandy floor through the water; deep water keeps its colour; all of it catches the sky
        // toward grazing angles
        vec3 shallow = mix(vec3(0.80, 0.76, 0.58) * (c.r + c.g + c.b) / 1.2, c * 1.5, 0.5);
        c = mix(shallow, c, smoothstep(1.0, 9.0, vWater));
        float facing = abs(normalize(vRel).y);
        c = mix(c, FogColor.rgb, pow(1.0 - facing, 5.0) * 0.55);
    } else {
        if (badlands(c)) c = mix(c, band(world.y - 0.5), 0.45);
        ivec2 blk = ivec2(floor(world.x), floor(world.z));
        c *= 1.0 + (farHash(blk) - 0.5) * 0.16 * grain;
        // terrace lines at the camera-facing edges: a rim where the ground drops away, a shadow where it rises
        vec2 dist = vEdge.xy, rise = vEdge.zw;
        for (int i = 0; i < 2; i++) {
            float d = dist[i], s = rise[i];
            if (s < -0.5) shade *= 1.0 + 0.10 * (1.0 - smoothstep(0.0, 0.5 + footprint, d));
            else if (s > 0.5) shade *= 1.0 - 0.28 * min(s / 3.0, 1.0) * (1.0 - smoothstep(0.0, 1.5 + footprint, d));
        }
    }
    c *= shade * texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;
    float fog = max(linear_fog_value(length(vRel), FogEnvironmentalStart, FogEnvironmentalEnd),
                    linear_fog_value(horizontal, FarFog.x, FarFog.y));
    fragColor = vec4(mix(c, FogColor.rgb, fog * FogColor.a), 1.0);
}
