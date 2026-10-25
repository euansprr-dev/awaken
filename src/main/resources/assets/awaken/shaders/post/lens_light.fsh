#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22), the light buffer at reduced size: how much sun reaches each surface and
// how open it is to the sky, without rendering the world from the sun. Big shadows march the terrain's heightfield
// toward the sun (ridges shade valleys, low suns stretch them, hills off screen still cast; leaves let light through
// in flecks); small ones march the depth buffer (contact shadows: grass, mobs, block edges); ambient occlusion samples a
// hemisphere in the depth buffer. Each frame traces with fresh noise and blends into last frame's result, reprojected
// by the frame's own matrices, so the look is many frames' work at one frame's cost. The same pass gathers volumetric
// light along each view ray (beams through gaps in a canopy, down canyons, past ridges, in any direction) from the sun
// map, so the whole of Lens 2's light is one pass and one history.
// Out: r sun visibility, g ambient occlusion, b volumetric light, a the surface's distance (one byte, 0 for sky).
uniform sampler2D DepthSampler;     // the world's depth, full size (first input: InSize is the full frame)
uniform sampler2D HeightSampler;
uniform sampler2D SunmapSampler;    // lens_sunmap.fsh: the height above which the sun reaches each column
uniform sampler2D HistorySampler;   // last frame's light buffer (persistent)
uniform sampler2D FrameSampler;
uniform sampler2D ParamsSampler;

#include <awaken:lens_frame.glsl>
#include <awaken:lens_height.glsl>
#include <awaken:lens_normal.glsl>

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Quality;                  // 1 Cinematic, 2 Ultra
};

layout(location = 0) out vec4 fragColor;

vec4 P(int i) { return texelFetch(ParamsSampler, ivec2(i, 0), 0); }

// the heightfield, from the surface toward the light: 0 under solid blocks, dimmed through leaves
float sunVisibility(vec3 p, vec3 n, vec3 l, float noise, bool ultra) {
    vec4 f12 = F(12), f15 = F(15);
    if (f15.x < 1.0) return 1.0;
    vec3 o = p + vec3(f12.x, f12.y, f12.z) + n * 0.06;
    // under a roof or in a cave: nothing from the sun (the composite fades its model out there). A heightmap counts a
    // slab, a stair or a carpet as its whole block, so its own column only shadows from a block above the surface
    ivec2 own = ivec2(floor(o.xz));
    if (o.y < heightAt(own, 0).g - 1.0) return 0.0;
    float maxH = f15.y, trust = f15.z, horiz = max(length(l.xz), 1.0e-3);
    int steps = ultra ? 40 : 24;
    float base = ultra ? 0.25 : 0.32, grow = ultra ? 0.13 : 0.23;
    float t = base * (0.4 + noise), vis = 1.0;
    for (int i = 0; i < steps; i++) {
        vec3 q = o + l * t;
        if (q.y > maxH) break;
        vec2 rel = q.xz - f12.xz;
        if (max(abs(rel.x), abs(rel.y)) > trust) break;
        float s = base + t * grow;
        // far steps test a whole cell (the max mips), so a long stride can't step over a ridge
        int lvl = int(clamp(log2(s * horiz) + 0.5, 0.0, 4.0));
        ivec2 cell = ivec2(floor(q.xz));
        vec2 h = heightAt(cell, lvl) - (lvl == 0 && cell == own ? 1.0 : 0.0);
        if (q.y < h.g) return 0.0;
        if (q.y < h.r) {
            vis *= exp(-0.9 * s);
            if (vis < 0.03) return 0.0;
        }
        t += s;
    }
    return vis;
}

// the depth buffer, a short way toward the light: what the heightfield can't hold (mobs, grass, slabs, block edges)
float contactShadow(vec3 p, vec3 n, vec3 l, float dist, float noise, mat4 vp, bool z01, float zFar, bool ultra) {
    int steps = ultra ? 12 : 6;
    float len = clamp(0.35 + dist * 0.025, 0.35, 3.0);
    vec3 o = p + n * (0.02 + dist * 0.0008);
    for (int i = 0; i < steps; i++) {
        float t = (float(i) + noise) / float(steps);
        vec3 s = project(vp, o + l * (len * t * t + 0.01), z01);
        if (s.x <= 0.0 || s.y <= 0.0 || s.x >= 1.0 || s.y >= 1.0) break;
        float sd = texelFetch(DepthSampler, ivec2(s.xy * InSize), 0).r;
        float zs = linearDepth(sd, zFar, z01), zq = linearDepth(s.z, zFar, z01);
        float delta = zq - zs;
        // something stands between this point and the light: in front of the ray, but not a wall far in front of it
        if (zs > 1.25 && delta > 0.015 + zq * 0.003 && delta < 0.5 + zq * 0.02) return 0.0;
    }
    return 1.0;
}

// how open the hemisphere above the surface is, about a block around it
float ambient(vec3 p, vec3 n, float noise, float noise2, mat4 vp, bool z01, float zFar, bool ultra) {
    int samples = ultra ? 10 : 6;
    const float radius = 0.9;
    vec3 t1 = normalize(abs(n.y) < 0.9 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
    vec3 t2 = cross(n, t1);
    vec3 o = p + n * 0.03;
    float occ = 0.0;
    for (int i = 0; i < samples; i++) {
        float fi = (float(i) + noise) / float(samples);
        float a = (float(i) + noise2) * 2.39996;
        float r = sqrt(fi);
        vec3 h = vec3(cos(a) * r, sin(a) * r, sqrt(max(0.0, 1.0 - fi)));
        float len = radius * mix(0.2, 1.0, fract(fi * 7.31 + noise2) );
        vec3 s = project(vp, o + (t1 * h.x + t2 * h.y + n * h.z) * len, z01);
        if (s.x <= 0.0 || s.y <= 0.0 || s.x >= 1.0 || s.y >= 1.0) continue;
        float sd = texelFetch(DepthSampler, ivec2(s.xy * InSize), 0).r;
        float zs = linearDepth(sd, zFar, z01), zq = linearDepth(s.z, zFar, z01);
        float delta = zq - zs;
        if (zs > 1.25 && delta > 0.02) occ += 1.0 - smoothstep(radius, radius * 2.5, delta);
    }
    return 1.0 - occ / float(samples);
}

// whether a point in the air (camera-relative) is in sunlight: 1 lit, 0 in a terrain shadow, dimmer under leaves
float litAt(vec3 w, vec4 f12, float cell) {
    vec4 s = texelFetch(SunmapSampler, ivec2(mod(w.xz + f12.xz, 1024.0) / cell), 0);
    float y = w.y + f12.y, solid = unpackHeight(s.rg), all = unpackHeight(s.ba);
    return smoothstep(solid - 0.5, solid + 0.5, y) * (y < all ? 0.35 : 1.0);
}

// sunlight scattered toward the eye by a thin mist along the view ray v (to distance d), wherever the air is lit: the
// mist lies on the land, thickest at the height of the ground under the camera and thinning above it over the look's
// mistHeight (so valleys fill and the air over a mountain stays clear); forward-peaked toward the sun (Henyey-Greenstein)
float volume(vec3 v, float d, vec3 l, float strength, float jitter, bool ultra) {
    vec4 f12 = F(12), v19 = P(19);
    float cell = 1024.0 / float(textureSize(SunmapSampler, 0).x);
    int steps = ultra ? 16 : 8;
    float ground = heightAt(ivec2(floor(f12.xz)), 0).g;
    float floorY = ground > -1000.0 ? max(ground, 40.0) : 63.0;
    float dt = d / float(steps), scaleH = max(v19.g * 256.0, 4.0), acc = 0.0;
    for (int i = 0; i < steps; i++) {
        vec3 w = v * ((float(i) + jitter) * dt);
        acc += litAt(w, f12, cell) * exp(-max(w.y + f12.y - floorY, 0.0) / scaleH);
    }
    const float g = 0.6;
    float phase = 0.06 + 4.0 * (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * dot(v, l), 1.5));
    return 1.0 - exp(-acc * dt * 0.0016 * strength * phase);
}

void main() {
    bool z01 = F(16).x > 0.5, ultra = Quality > 1.5;
    ivec2 last = ivec2(InSize) - 1;
    ivec2 px = clamp(ivec2(texCoord * InSize), ivec2(0), last);
    float depth = texelFetch(DepthSampler, px, 0).r;
    // the frame's values not written yet (the near plane is never 0 once they are)
    if (F(16).w <= 0.0) {
        fragColor = vec4(1.0, 1.0, 0.0, 0.0);
        return;
    }
    bool sky = depth < 1.0e-7;
    vec2 uv = (vec2(px) + 0.5) / InSize;
    mat4 inv = frameMat(0);
    vec3 p = unproject(inv, uv, sky ? 1.0e-5 : depth, z01);
    float dist = sky ? 0.0 : length(p);
    vec4 p1 = P(1), f14 = F(14), f12 = F(12);
    float zFar = max(p1.r * 255.0 * 256.0 + p1.g * 255.0, 16.0);
    float noise = ign(gl_FragCoord.xy, f12.w), noise2 = ign(gl_FragCoord.yx + 17.0, f12.w + 31.0);
    float vis = 1.0, ao = 1.0, vol = 0.0;
    // the hand and the holo glass (within arm's reach) are never shadowed
    if (!sky && dist >= 1.25) {
        vec3 n = normalAt(px, p, depth, inv, z01, InSize);
        mat4 vp = frameMat(4);
        if (f14.w > 0.0 && P(17).r > 0.0 && dot(n, f14.xyz) > -0.1) {
            vis = sunVisibility(p, n, f14.xyz, noise, ultra);
            if (vis > 0.0 && dist < 96.0) vis *= contactShadow(p, n, f14.xyz, dist, noise2, vp, z01, zFar, ultra);
        }
        if (dist < 96.0) ao = ambient(p, n, noise, noise2, vp, z01, zFar, ultra);
    }
    float strength = f14.w * P(6).a * P(19).r;
    if (strength > 0.0 && F(15).x > 0.0) vol = volume(normalize(p), sky ? (ultra ? 320.0 : 192.0) : min(dist, ultra ? 320.0 : 192.0), f14.xyz, strength, noise2, ultra);

    vec3 res = vec3(vis, ao, vol);
    float code = packDistance8(dist);
    // last frame's answer for this surface (or this sky direction): reprojected, its 2x2 texels kept only where they saw
    // the same distance
    vec4 f13 = F(13);
    if (f13.w > 0.5) {
        vec3 pp = (sky ? normalize(p) * 4096.0 : p) + f13.xyz;
        vec3 s = project(frameMat(8), pp, z01);
        if (s.x > 0.0 && s.y > 0.0 && s.x < 1.0 && s.y < 1.0) {
            float want = sky ? 0.0 : packDistance8(length(pp));
            vec2 hp = s.xy * OutSize - 0.5;
            ivec2 b = ivec2(floor(hp));
            vec2 fr = hp - vec2(b);
            ivec2 hl = ivec2(OutSize) - 1;
            vec3 acc = vec3(0.0);
            float wsum = 0.0;
            for (int k = 0; k < 4; k++) {
                ivec2 o = ivec2(k & 1, k >> 1);
                vec4 h = texelFetch(HistorySampler, clamp(b + o, ivec2(0), hl), 0);
                float w = (o.x == 1 ? fr.x : 1.0 - fr.x) * (o.y == 1 ? fr.y : 1.0 - fr.y)
                        * (1.0 - smoothstep(1.5 / 255.0, 2.5 / 255.0, abs(h.a - want)));
                acc += h.rgb * w;
                wsum += w;
            }
            if (wsum > 0.02) {
                float k = smoothstep(0.02, 0.4, wsum);
                res = mix(acc / wsum, res, mix(vec3(1.0), ultra ? vec3(0.1, 0.1, 0.15) : vec3(0.14, 0.14, 0.2), k));
            }
        }
    }
    // dithered into 8 bits, so slow changes still move
    res += (noise - 0.5) / 255.0;
    fragColor = vec4(clamp(res, 0.0, 1.0), code);
}
