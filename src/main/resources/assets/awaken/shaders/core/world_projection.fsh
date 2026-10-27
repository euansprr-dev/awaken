#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN (SPEC §22 "The world's own projection", WorldProjection): the frame's matrices from the projection the world
// was really drawn with (the game's bob, hurt tilt and nausea, and whatever a camera mod adds, such as Camera Overhaul's
// roll), read straight from the level's projection uniform. One texel per output, 18x1 RGBA32F:
//   0-3 inverse view-projection, 4-7 view-projection, 8-11 last frame's view-projection (all camera-relative)
//   12 the sun on screen (xy uv, z 1 when it is ahead of the camera)
//   13-16 this frame's NDC to last frame's clip space, the camera's step included (Pixels' steady pass)
//   17 x the roll a camera mod adds on top of the game's own bob (radians, about the view axis), y 1

#include <minecraft:projection.glsl>

uniform sampler2D HistorySampler;   // last frame's output

layout(std140) uniform WorldProjection {
    mat4 ViewRot;   // the camera's view rotation (the world is drawn camera-relative)
    mat4 CamProj;   // the camera's own projection, before any bob
    mat4 Unbob;     // the inverse of the game's own bob and hurt tilt
    vec4 Sun;       // xyz toward the sun
    vec4 Step;      // xyz the camera's move since last frame (0 without history)
    vec4 Flags;     // x 1 when last frame's matrices are usable history
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    int i = int(gl_FragCoord.x);
    mat4 vp = ProjMat * ViewRot;
    bool hist = Flags.x > 0.5;
    if (i < 4) {
        fragColor = inverse(vp)[i];
    } else if (i < 8) {
        fragColor = vp[i - 4];
    } else if (i < 12) {
        fragColor = hist ? texelFetch(HistorySampler, ivec2(i - 4, 0), 0) : vp[i - 8];
    } else if (i == 12) {
        vec4 c = vp * vec4(Sun.xyz, 0.0);
        fragColor = c.w > 1.0e-4 ? vec4(c.xy / c.w * 0.5 + 0.5, 1.0, 0.0) : vec4(0.5, 0.5, 0.0, 0.0);
    } else if (i < 17) {
        mat4 prev = hist ? mat4(texelFetch(HistorySampler, ivec2(4, 0), 0), texelFetch(HistorySampler, ivec2(5, 0), 0),
                texelFetch(HistorySampler, ivec2(6, 0), 0), texelFetch(HistorySampler, ivec2(7, 0), 0)) : vp;
        mat4 step = mat4(1.0);
        step[3] = vec4(Step.xyz, 1.0);
        fragColor = (prev * step * inverse(vp))[i - 13];
    } else {
        // what the world's projection holds beyond the camera's own and the game's bob: the left-hand factor a camera
        // mod puts at the start of the bob stack (Camera Overhaul rotates it about the view axis)
        mat4 extra = inverse(CamProj) * ProjMat * Unbob;
        fragColor = vec4(atan(extra[0][1], extra[0][0]), 1.0, 0.0, 0.0);
    }
}
