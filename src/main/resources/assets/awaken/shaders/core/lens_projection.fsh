#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens 2 (SPEC §22 "The world's own projection", LensProjection): the frame's matrices from the projection the
// world was really drawn with (the game's bob, hurt tilt and nausea, and whatever a camera mod adds, such as Camera
// Overhaul's roll), read straight from the level's projection uniform. One texel per output, 13x1 RGBA32F:
//   0-3 inverse view-projection, 4-7 view-projection, 8-11 last frame's view-projection, 12 the sun on screen
//   (xy uv, z 1 when it is in front of the camera).

#include <minecraft:projection.glsl>

uniform sampler2D HistorySampler;   // last frame's output

layout(std140) uniform LensProjection {
    mat4 ViewRot;   // the camera's view rotation (the world is drawn camera-relative)
    vec4 Sun;       // xyz toward the sun
    vec4 Flags;     // x 1 when last frame's matrices are usable history
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    int i = int(gl_FragCoord.x);
    mat4 vp = ProjMat * ViewRot;
    if (i < 4) {
        fragColor = inverse(vp)[i];
    } else if (i < 8) {
        fragColor = vp[i - 4];
    } else if (i < 12) {
        fragColor = Flags.x > 0.5 ? texelFetch(HistorySampler, ivec2(i - 4, 0), 0) : vp[i - 8];
    } else {
        vec4 c = vp * vec4(Sun.xyz, 0.0);
        fragColor = c.w > 1.0e-4 ? vec4(c.xy / c.w * 0.5 + 0.5, 1.0, 0.0) : vec4(0.5, 0.5, 0.0, 0.0);
    }
}
