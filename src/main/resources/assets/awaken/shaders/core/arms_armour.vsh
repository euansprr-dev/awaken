#version 330
#extension GL_ARB_separate_shader_objects : require

// The premium test's armour shader (docs/armour/PREMIUM-TEST.md; Level 2): arms_weapon's, with an armour model part's inputs.
// A model part sends its tint, here the rim colour (rgb) and the set's level (a: a stir, the half bonus, the whole set), and its
// overlay, which this shader does not use.

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
layout(location = 2) out vec4 rimLevel;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 texCoord0;
layout(location = 5) out vec3 worldNormal;
layout(location = 6) out vec3 viewNormal;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    rimLevel = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    worldNormal = Normal;
    viewNormal = mat3(ModelViewMat) * Normal;
}
