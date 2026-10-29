#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Gate pillars (gates.client.Pillars): hidden by any land in front of where they really stand. A far pillar is drawn
// pulled in along the sight line, so by the depth it is drawn at, the chunks between there and its real place never hid
// it; and Far Sight's land isn't in the game's depth at all (it keeps its own). Euan, 29 Oct: "One of the gate beams is
// showing on top of the cliff here even tho the gate is behind it."
uniform sampler2D FarDepthSampler;   // compat.client.FarTerrain's depth (a 1x1 placeholder while none is shared)

layout(std140) uniform GatePillar {
    vec4 FarPlanes;   // x near, y far, z flags (1 reverse Z, 2 built for 0..1, 4 the device's depth is 0..1), w 1 to test
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in float trueDepth;

layout(location = 0) out vec4 fragColor;

// far land's view depth from its own projection, as Lens reads it (farDistance, lens_composite.glsl)
float farDistance(float depth, int flags) {
    bool reverse = (flags & 1) != 0, zeroToOne = (flags & 2) != 0;
    float zNear = FarPlanes.x, zFar = FarPlanes.y;
    float z = (flags & 4) != 0 ? depth : depth * 2.0 - 1.0;
    if (zeroToOne) return reverse ? zNear * zFar / (z * (zFar - zNear) + zNear) : zNear * zFar / (zFar - z * (zFar - zNear));
    return reverse ? 2.0 * zFar * zNear / (z * (zFar - zNear) + (zFar + zNear))
                   : 2.0 * zFar * zNear / ((zFar + zNear) - z * (zFar - zNear));
}

void main() {
    int flags = int(FarPlanes.z + 0.5);
    // the game's depth test sees the pillar at its real depth (past the far plane: at it, behind every chunk)
    vec4 clip = ProjMat * vec4(0.0, 0.0, -trueDepth, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = clamp((flags & 4) != 0 ? ndc : ndc * 0.5 + 0.5, 0.0, 1.0);
    vec4 color = vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    if (FarPlanes.w > 0.5) {
        ivec2 size = textureSize(FarDepthSampler, 0);
        float d = texelFetch(FarDepthSampler, clamp(ivec2(gl_FragCoord.xy), ivec2(0), size - 1), 0).r;
        // open sky reads as the far plane, kilometres past any pillar
        if (farDistance(d, flags) < trueDepth) {
            discard;
        }
    }
    fragColor = color * ColorModulator;
}
