#version 330
#extension GL_ARB_separate_shader_objects : require

#include <awaken:farsight.glsl>

// AWAKEN Far Sight's cached horizon (FarHorizon, SPEC §20): lays the panorama's served slices onto the screen. Each
// pixel's ray finds its slice (by its bearing) and its texel there; where the slice has land, the land's colour, and
// Far Sight's depth for that point as the screen sees it (a hair nearer, so the same land drawn live behind it loses).
// Where a slice isn't served, the band ends, or a slice texel would be magnified past what it holds (the screen's
// stretched corners), nothing: the live draw covers it.

layout(std140) uniform Horizon {
    vec4 HorizonBand;    // x a slice's half width (tan), y the band's lowest v (tan), z its highest, w slices
    vec4 HorizonAtlas;   // x a slice's width (px), y the atlas's height, z its width, w the most screen pixels per tan served
    vec4 HorizonDepth;   // x, y Far Sight's planes (ndc z = x / w + y), z 1 if the depth range is zero to one, w the bias
    vec4 HorizonScreen;  // x the screen centre's pixels per tan, y, z the screen's size (debug view 6)
    ivec4 HorizonServed; // x the slices served (bits)
    vec4 HorizonRows;    // x, y the band's lowest and highest row on screen (NDC y)
};

uniform sampler2D HorizonColorSampler;
uniform sampler2D HorizonDepthSampler;

layout(location = 0) in vec3 vView;
layout(location = 1) in vec3 vWorld;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530718;

void main() {
    // debug view 6: the panorama itself over the top half of the screen (every slice across, the band down; no land blue)
    if (FarCam.w > 5.5 && FarCam.w < 6.5 && gl_FragCoord.y > 0.0) {
        vec2 q = gl_FragCoord.xy / vec2(HorizonScreen.y, HorizonScreen.z);
        if (q.y > 0.5) {
            vec2 t = vec2(q.x, (q.y - 0.5) * 2.0) * HorizonAtlas.zy;
            float dd = texelFetch(HorizonDepthSampler, ivec2(t), 0).r;
            vec4 cc = texelFetch(HorizonColorSampler, ivec2(t), 0);
            fragColor = dd <= 0.0 ? vec4(0.0, 0.0, 0.4, 1.0) : vec4(cc.rgb, 1.0);
            gl_FragDepth = 0.5;
            return;
        }
    }
    vec3 d = vWorld;
    // above or under the band at any bearing (a slice's v is at least the bearing's own tan): nothing here
    float t = d.y * inversesqrt(max(dot(d.xz, d.xz), 1e-12));
    if (t <= HorizonBand.y || t >= HorizonBand.z) discard;
    float n = HorizonBand.w;
    float az = atan(d.x, -d.z);
    if (az < 0.0) az += TAU;
    int i = min(int(az * n / TAU), int(n) - 1);
    if (((HorizonServed.x >> i) & 1) == 0) discard;
    // the screen's pixels per tan here, radially (the most): past what a slice holds, the live draw takes the pixel
    if (HorizonScreen.x * (1.0 + dot(vView.xy, vView.xy)) > HorizonAtlas.w) discard;
    float psi = (float(i) + 0.5) * TAU / n;
    vec3 forward = vec3(sin(psi), 0.0, -cos(psi)), right = vec3(cos(psi), 0.0, sin(psi));
    float zf = dot(d, forward);
    float u = dot(d, right) / zf, v = d.y / zf;
    if (v <= HorizonBand.y || v >= HorizonBand.z) discard;
    vec2 px = vec2((float(i) + clamp(u / HorizonBand.x * 0.5 + 0.5, 0.0, 1.0)) * HorizonAtlas.x,
                   (v - HorizonBand.y) / (HorizonBand.z - HorizonBand.y) * HorizonAtlas.y);
    float depth = texelFetch(HorizonDepthSampler, ivec2(px), 0).r;
    // debug view 5: served with land magenta, served without (the sky, or the live rings in front) cyan
    if (FarCam.w > 4.5 && FarCam.w < 5.5) {
        fragColor = depth <= 0.0 ? vec4(0.0, 0.8, 0.8, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
        gl_FragDepth = depth <= 0.0 ? 0.0 : depth;
        return;
    }
    // no land there (the cleared depth): the sky, or the live rings in front
    if (depth <= 0.0) discard;
    // colour by its four nearest texels, the sky's (alpha 0) left out, so a ridge's edge never darkens
    vec4 c = texture(HorizonColorSampler, px / HorizonAtlas.zy);
    fragColor = vec4(c.rgb / max(c.a, 1e-3), 1.0);
    // Far Sight's depth for this point as the screen sees it: the slice's distance along its axis, along the ray, then
    // along the screen's (the ray is on the plane one block in front of the eye, so that's the same distance)
    float ndc = HorizonDepth.z > 0.5 ? depth : depth * 2.0 - 1.0;
    float w = HorizonDepth.x / (ndc - HorizonDepth.y) / zf;
    float screen = HorizonDepth.x / w + HorizonDepth.y;
    gl_FragDepth = (HorizonDepth.z > 0.5 ? screen : screen * 0.5 + 0.5) * (1.0 + HorizonDepth.w);
}
