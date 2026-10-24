#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// PARTICLE vertex format: Position, UV0, Color, UV2 (see holo_glow.fsh for what UV0 and UV2 mean per shape).
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV2;

layout(location = 0) out vec2 local;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out ivec2 shape;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    vertexColor = Color;
    shape = UV2;
}
