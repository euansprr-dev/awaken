#version 330
#extension GL_ARB_separate_shader_objects : require

// Arms' weapon shader (SPEC §24 "Weapons"); its look is mirrored by tools/art/mesh_render.py, keep them in step.
// Vanilla's ENTITY inputs, read our way: Color is the weapon's accent (rgb) and how far along it this corner lies (a);
// UV1 is not an overlay but the blade's live state: x its energy (0..255: a heavy's charge, the Art ready), y its
// flash (0..255: the glint, a perfect blow) with 256 added when the item has foil; bits 8-14 of x the enchantment light
// on it now (SPEC §45.11): a palette index in 8-11 (0 none), how brightly it shows (0..7) in 12-14. With 512 added to y
// it is metal in the making at the War Anvil (SPEC §46.3): x's low byte its heat, y's low byte the heat's front along
// the blade, 1024 added while the quench cools it from the point. Bits 11-12 of y a forged flaw (SPEC §46.7: 1 a burnt
// tip, 2 a brittle blade).

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
layout(location = 7) flat out vec4 state;
layout(location = 8) flat out vec3 script;

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
    int y = UV1.y;
    state = vec4(float(UV1.x & 255) / 255.0, float(y & 255) / 255.0, (y & 256) != 0 ? 1.0 : 0.0,
            (y & 512) == 0 ? 0.0 : (y & 1024) != 0 ? 2.0 : 1.0);
    script = vec3(float((UV1.x >> 8) & 15), float((UV1.x >> 12) & 7) / 7.0, float((y >> 11) & 3));
}
