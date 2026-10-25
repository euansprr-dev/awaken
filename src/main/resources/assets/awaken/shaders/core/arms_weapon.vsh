#version 330
#extension GL_ARB_separate_shader_objects : require

// Arms' weapon shader (SPEC §24 "Weapons"); its look is mirrored by tools/art/mesh_render.py, keep them in step.
// Vanilla's ENTITY inputs, read our way: Color is the weapon's accent (rgb) and how far along it this corner lies (a);
// UV1 is not an overlay but the blade's live state: x its energy (0..255: a heavy's charge, the Art ready), y its
// flash (0..255: the glint, a perfect blow) with 256 added when the item has foil.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 accentAlong;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 texCoord0;
layout(location = 5) out vec3 worldNormal;
layout(location = 6) out vec3 viewNormal;
layout(location = 7) flat out vec3 state;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    accentAlong = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    worldNormal = Normal;
    viewNormal = mat3(ModelViewMat) * Normal;
    state = vec3(float(UV1.x) / 255.0, float(UV1.y & 255) / 255.0, UV1.y >= 256 ? 1.0 : 0.0);
}
