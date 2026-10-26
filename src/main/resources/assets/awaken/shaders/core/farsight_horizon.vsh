#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight's cached horizon (FarHorizon, SPEC §20): a quad over the screen rows the band covers; each corner
// carries its view ray, on the plane one block in front of the eye and turned into the world (both are linear across
// the screen).

layout(std140) uniform Horizon {
    vec4 HorizonBand;    // x a slice's half width (tan), y the band's lowest v (tan), z its highest, w slices
    vec4 HorizonAtlas;   // x a slice's width (px), y the atlas's height, z its width, w the most screen pixels per tan served
    vec4 HorizonDepth;   // x, y Far Sight's planes (ndc z = x / w + y), z 1 if the depth range is zero to one, w the bias
    vec4 HorizonScreen;  // x the screen centre's pixels per tan, y, z the screen's size (debug view 6)
    ivec4 HorizonServed; // x the slices served (bits)
    vec4 HorizonRows;    // x, y the band's lowest and highest row on screen (NDC y)
};

layout(location = 0) out vec3 vView;
layout(location = 1) out vec3 vWorld;

void main() {
    const vec2 CORNERS[6] = vec2[](vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0));
    vec2 q = CORNERS[gl_VertexIndex];
    vec2 ndc = vec2(q.x * 2.0 - 1.0, mix(HorizonRows.x, HorizonRows.y, q.y));
    gl_Position = vec4(ndc, 0.0, 1.0);
    vec4 v = inverse(ProjMat) * vec4(ndc, 0.0, 1.0);
    vec3 ray = v.xyz / v.w;
    ray /= -ray.z;
    vView = ray;
    vWorld = inverse(mat3(FarViewRot)) * ray;
}
