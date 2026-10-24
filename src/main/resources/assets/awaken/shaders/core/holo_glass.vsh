#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// PARTICLE vertex format: Position, UV0, Color, UV2.
// UV0 = position on the panel in panel units; UV2 = panel size in units (0,0 for a flat fill).
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV2;

layout(location = 0) out vec2 local;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out ivec2 size;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    vertexColor = Color;
    size = UV2;
}
