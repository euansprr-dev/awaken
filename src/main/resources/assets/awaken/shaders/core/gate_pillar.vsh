#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Gate pillars (gates.client.Pillars). POSITION_TEX_COLOR: UV0.x is how many times further off the pillar really stands
// than where it is drawn (a far pillar is pulled in along the sight line and shrunk to match), so the fragment knows its
// real view depth and far land in front of it can hide it.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out float trueDepth;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexColor = Color;
    trueDepth = -view.z * UV0.x;
}
