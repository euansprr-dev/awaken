#version 330
#extension GL_ARB_separate_shader_objects : require

// A weapon drawn in the Hall of Arms (SPEC §52). The look is the approved prototype's, arms_weapon's in a GUI's axes.
// UV1: x the mode (0 itself, 1 a shape you haven't found, 2 known but dim, 3 Hale's iron), y its alpha (0..255).

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 5) in vec3 Normal;

layout(location = 0) out vec4 accentAlong;
layout(location = 1) out vec2 texCoord0;
layout(location = 2) out vec3 viewNormal;
layout(location = 3) flat out vec2 state;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    accentAlong = Color;
    texCoord0 = UV0;
    viewNormal = vec3(Normal.x, -Normal.y, Normal.z); // the GUI's y runs down; the light and the studio's run up
    state = vec2(float(UV1.x), float(UV1.y) / 255.0);
}
