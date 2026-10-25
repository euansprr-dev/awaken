#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens 2 (SPEC §22), the sun map: for each column of the terrain's heightfield ring, the height above which the
// sun reaches it (the highest point of every ridge, roof and canopy toward the sun, less the light's drop over the
// distance). Volumetric light then knows whether any point in the air is lit with one read. Rebuilt every frame from
// the heightfield; a march stops as soon as nothing further toward the sun could rise above what it has (most stop
// within a few steps). 512x512 cells of 2 blocks on Cinematic, 1024x1024 of 1 block on Ultra.
// Out: r/g the height without leaves (solid), b/a with leaves (packHeight).
uniform sampler2D HeightSampler;
uniform sampler2D FrameSampler;

#include <awaken:lens_frame.glsl>
#include <awaken:lens_height.glsl>

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 f12 = F(12), f14 = F(14), f15 = F(15);
    float cell = 1024.0 / OutSize.x;
    ivec2 tx = clamp(ivec2(texCoord * OutSize), ivec2(0), ivec2(OutSize) - 1);
    vec2 ring = (vec2(tx) + 0.5) * cell;
    // where this column is from the camera (the ring wraps every 1024 blocks)
    vec2 rel = mod(ring - f12.xz + 512.0, 1024.0) - 512.0;
    if (f15.x < 1.0 || f14.w <= 0.0 || max(abs(rel.x), abs(rel.y)) > f15.z || f14.y <= 0.0) {
        fragColor = vec4(packHeight(-256.0), packHeight(-256.0));
        return;
    }
    vec3 l = f14.xyz;
    float horiz = max(length(l.xz), 1.0e-3), maxH = f15.y;
    int lvl0 = cell > 1.5 ? 1 : 0;
    vec2 own = heightAt(ivec2(floor(ring)), lvl0);
    float solid = own.g, all = own.r;
    bool ultra = cell < 1.5;
    int steps = ultra ? 32 : 24;
    float base = ultra ? 0.5 : 1.0, grow = ultra ? 0.16 : 0.2;
    float t = base;
    for (int i = 0; i < steps; i++) {
        float drop = l.y * t;
        if (maxH - drop <= solid) break;
        vec2 q = ring + l.xz * t;
        vec2 qr = rel + l.xz * t;
        if (max(abs(qr.x), abs(qr.y)) > f15.z) break;
        float s = base + t * grow;
        int lvl = max(lvl0, int(clamp(log2(s * horiz) + 0.5, 0.0, 4.0)));
        vec2 h = heightAt(ivec2(floor(q)), lvl);
        solid = max(solid, h.g - drop);
        all = max(all, h.r - drop);
        t += s;
    }
    fragColor = vec4(packHeight(solid), packHeight(max(all, solid)));
}
