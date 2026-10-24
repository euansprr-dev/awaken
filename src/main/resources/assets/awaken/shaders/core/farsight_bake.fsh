#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Far Sight bake (the fragment-pass FarGpu): each cell's smoothed normal and valley shade, from the 5x5 cells
// around it in its own ring (wrapping inside the ring's block; a cell not known yet counts as level with this one).
// The outermost cells wrap to the far side of the window, but those are drawn from the coarser ring anyway.
layout(std140) uniform FarBake {
    ivec4 Bake;
};

uniform sampler2D HeightSampler;

layout(location = 0) in vec2 texel;

layout(location = 0) out vec4 fragColor;

float heightAt(ivec2 t, int dx, int dz, float fallback) {
    vec4 h = texelFetch(HeightSampler, ivec2((t.x + dx) & 255, (t.y & ~255) | ((t.y + dz) & 255)), 0);
    if (h.a < 0.5) return fallback;
    return floor(h.r * 255.0 + 0.5) * 256.0 + floor(h.g * 255.0 + 0.5) - 2048.0;
}

void main() {
    ivec2 t = ivec2(floor(texel));
    int ring = t.y >> 8;
    if (((Bake.x >> ring) & 1) == 0) discard;
    float h = heightAt(t, 0, 0, -10000.0);
    if (h < -9000.0) {
        fragColor = vec4(0.5, 0.5, 1.0, 1.0);
        return;
    }
    float s = float(2 << ring);
    float east = heightAt(t, 1, 0, h), west = heightAt(t, -1, 0, h), north = heightAt(t, 0, -1, h), south = heightAt(t, 0, 1, h);
    vec3 n = normalize(vec3((west - east) / (2.0 * s), 1.0, (north - south) / (2.0 * s)));
    float sum = 0.0;
    for (int dz = -2; dz <= 2; dz++) {
        for (int dx = -2; dx <= 2; dx++) sum += heightAt(t, dx, dz, h);
    }
    // how far the ground around rises above this cell, in cells: valleys and hollows darken a little
    float above = (sum / 25.0 - h) / s;
    float valley = 1.0 - 0.35 * smoothstep(0.0, 2.5, above);
    fragColor = vec4(n.x * 0.5 + 0.5, n.z * 0.5 + 0.5, valley, 1.0);
}
