#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Far Sight bake: one triangle over the whole shade atlas; texel says which cell a fragment is.
layout(std140) uniform FarBake {
    ivec4 Bake;         // rings to bake (bit mask), atlas width, atlas height
};

layout(location = 0) out vec2 texel;

void main() {
    vec2 p = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
    texel = p * vec2(Bake.yz);
}
