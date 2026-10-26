#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Far Sight 2 bake (the fragment-pass FarGpu): per ring corner, from the heights around it in its own ring
// (generated where seen, else predicted; a corner not known yet counts as level with this one):
//   rg the normal's x and z, from the corners either side;
//   b  valley shade: how far the ground rises on both sides of this corner (hollows darken a little);
//   a  sunlight: 1 where the sun reaches, falling to 0 where a ridge toward it stands higher than the sun, marched
//      across the window in growing steps (a soft edge from how far under the ridge line it sits).
// Neighbours past the window's edge aren't this place (the atlas wraps): the march stops there, and the outer band is
// drawn from the coarser ring anyway.
layout(std140) uniform FarBake {
    ivec4 Bake;         // rings to bake (bit mask), atlas width, atlas height
    vec4 BakeSun;       // xyz toward the light (world space), w 1 if the light is above the horizon
    ivec4 BakeWindow[7]; // per ring: the window's first corner as a texel (x, z)
};

uniform sampler2D HeightSampler;
uniform sampler2D GenHeightSampler;

layout(location = 0) in vec2 texel;

layout(location = 0) out vec4 fragColor;

float heightAt(ivec2 t, int ring, ivec2 l, float fallback) {
    if (l.x < 0 || l.y < 0 || l.x > 255 || l.y > 255) return fallback;
    ivec2 origin = BakeWindow[ring].xy;
    ivec2 at = ivec2((origin.x + l.x) & 255, ring * 256 + ((origin.y + l.y) & 255));
    float h = texelFetch(GenHeightSampler, at, 0).r;
    if (h <= 0.0) h = texelFetch(HeightSampler, at, 0).r;
    if (h <= 0.0) return fallback;
    return h * (65535.0 / 16.0) - 1024.0;
}

void main() {
    ivec2 t = ivec2(floor(texel));
    int ring = t.y >> 8;
    if (((Bake.x >> ring) & 1) == 0) discard;
    // this texel's corner in window coordinates
    ivec2 l = ivec2((t.x - BakeWindow[ring].x) & 255, ((t.y & 255) - BakeWindow[ring].y) & 255);
    float h = heightAt(t, ring, l, -10000.0);
    if (h < -9000.0) {
        fragColor = vec4(0.5, 0.5, 1.0, 1.0);
        return;
    }
    float s = float(2 << ring);
    float east = heightAt(t, ring, l + ivec2(1, 0), h), west = heightAt(t, ring, l - ivec2(1, 0), h);
    float south = heightAt(t, ring, l + ivec2(0, 1), h), north = heightAt(t, ring, l - ivec2(0, 1), h);
    vec3 n = normalize(vec3((west - east) / (2.0 * s), 1.0, (north - south) / (2.0 * s)));
    // a hollow rises on both sides: along each of four directions, the lower of the two sides (one and two corners
    // out) over this corner. The foot of a cliff or a face under a summit rises on one side only and isn't one (an
    // average of the 5x5 round it shaded every cliff foot, and per corner across the long triangles of a cliff those
    // made grey wedges: Euan, 2026-10-26)
    const ivec2 DIRS[4] = ivec2[](ivec2(1, 0), ivec2(0, 1), ivec2(1, 1), ivec2(1, -1));
    float hollow = 0.0;
    for (int k = 0; k < 4; k++) {
        ivec2 d = DIRS[k];
        float a = 0.5 * (heightAt(t, ring, l + d, h) + heightAt(t, ring, l + 2 * d, h));
        float b = 0.5 * (heightAt(t, ring, l - d, h) + heightAt(t, ring, l - 2 * d, h));
        hollow = max(hollow, min(a, b) - h);
    }
    float valley = 1.0 - 0.35 * smoothstep(0.0, 2.5, hollow / s);
    // ridges toward the sun
    float sun = 1.0;
    vec3 L = BakeSun.xyz;
    float horiz = length(L.xz);
    if (BakeSun.w > 0.5 && horiz > 1e-3) {
        vec2 dir = L.xz / horiz;
        float rise = L.y / horiz;      // how much the sun ray climbs per block across the ground
        float dist = 0.0, stride = 1.0, worst = 1.0;
        for (int i = 0; i < 40; i++) {
            dist += stride;
            stride *= 1.12;
            ivec2 at = l + ivec2(round(dir * dist));
            if (at.x < 0 || at.y < 0 || at.x > 255 || at.y > 255) break;
            float hh = heightAt(t, ring, at, -10000.0);
            if (hh < -9000.0) continue;
            float ray = h + 1.5 + rise * dist * s;
            // how many blocks under the ridge line the ray passes, softened by distance (a penumbra) and never sharper
            // than a cell and a half (sharper, a shadow's edge steps from corner to corner and the triangles draw it as
            // a saw)
            worst = min(worst, clamp((ray - hh) / max(3.0 + 0.02 * dist * s, 1.5 * s) + 1.0, 0.0, 1.0));
            if (worst <= 0.0) break;
        }
        sun = worst;
    }
    fragColor = vec4(n.x * 0.5 + 0.5, n.z * 0.5 + 0.5, valley, sun);
}
