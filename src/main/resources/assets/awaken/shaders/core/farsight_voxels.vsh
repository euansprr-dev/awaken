#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight 3, City Sight (SPEC §20): real chunks' blocks drawn by Far Sight where Sodium doesn't draw them.
// No vertex buffer: vertex v is corner v % 4 of quad v / 4, pulled from the page's quads (VoxelMesher's four ints:
// least corner in sixteenths, face, extents along the face's u and v axes, level, look, chunk slot, tint, light).
// Each slot's origin comes from the chunk table, each look's atlas rectangle from the look table.

uniform usamplerBuffer Quads;
uniform isamplerBuffer Chunks;   // per slot: block x, the level's floor y, block z
uniform samplerBuffer Looks;     // per look: u0 v0 u1 v1, then flags and the sprite's average colour

layout(location = 0) out vec3 vRel;
layout(location = 1) out vec2 vUv;          // blocks along the face's u and v (the texture repeats per block)
layout(location = 2) flat out vec4 vRect;   // the sprite in the atlas
layout(location = 3) flat out vec4 vLook;   // flags, average colour
layout(location = 4) flat out vec3 vTint;
layout(location = 5) flat out vec3 vNormal;
layout(location = 6) flat out vec2 vLight;  // block, sky (0..15)
layout(location = 7) flat out int vLod;

void main() {
    int quad = gl_VertexIndex >> 2, corner = gl_VertexIndex & 3;
    uvec4 q = texelFetch(Quads, quad);
    int x16 = int(q.x & 511u), z16 = int((q.x >> 9) & 511u), dir = int((q.x >> 18) & 7u), eu = int((q.x >> 21) & 511u);
    int y16 = int(q.y & 16383u), ev = int((q.y >> 14) & 16383u);
    int look = int(q.z & 65535u), slot = int(q.z >> 16);
    ivec4 origin = texelFetch(Chunks, slot);
    float cu = (corner == 1 || corner == 2) ? 1.0 : 0.0, cv = corner >= 2 ? 1.0 : 0.0;
    vec3 U = dir < 4 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0);
    vec3 V = dir < 2 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 local = (vec3(float(x16), float(y16), float(z16)) + U * (float(eu) * cu) + V * (float(ev) * cv)) / 16.0;
    // camera-relative: the chunk's origin less the camera's 256-block cell exactly, then the camera's remainder
    vec2 xz = vec2(origin.xz - FarCamInt.xz) + local.xz - FarCam.xz;
    vRel = vec3(xz.x, float(origin.y) + local.y - FarFog.z, xz.y);
    // texture space in blocks: across the face, and down its sides (a texture's top at the block's top)
    vec2 uv = vec2(dot(local, U), dot(local, V));
    if (dir >= 2) uv.y = -uv.y;
    vUv = uv;
    vRect = texelFetch(Looks, look * 2);
    vLook = texelFetch(Looks, look * 2 + 1);
    uint t = q.w;
    vTint = vec3(float((t >> 16) & 255u), float((t >> 8) & 255u), float(t & 255u)) / 255.0;
    const vec3 NORMALS[6] = vec3[](vec3(0.0, -1.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0),
            vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0));
    vNormal = NORMALS[dir];
    vLight = vec2(float((t >> 28) & 15u), float((t >> 24) & 15u));
    vLod = int(q.x >> 30);
    vec4 clip = ProjMat * (FarViewRot * vec4(vRel, 1.0));
    clip.z = FarDepth.x + FarDepth.y * clip.w;
    gl_Position = clip;
}
