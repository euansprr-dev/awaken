#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight landmarks (dev.awaken.farsight.FarLandmarks): each one a column that always faces the camera, a
// bright core and a wide faint halo (vertices 8i..8i+7), never thinner than a few pixels however far it stands.
layout(std140) uniform FarMarks {
    vec4 Marks[144];    // per landmark: base from the camera (xyz) and height; colour (rgb) and core width; from, -, -, -
};

layout(location = 0) out vec3 vColor;
layout(location = 1) out vec2 vUv;
layout(location = 2) out float vAlpha;
layout(location = 3) out vec3 vRel;

void main() {
    int v = gl_VertexIndex;
    int mark = v / 8, quad = (v >> 2) & 1, corner = v & 3;
    vec4 a = Marks[mark * 3], b = Marks[mark * 3 + 1], c = Marks[mark * 3 + 2];
    float dist = length(a.xz);
    vec2 toCam = dist > 0.001 ? -a.xz / dist : vec2(0.0, 1.0);
    vec2 side = vec2(-toCam.y, toCam.x);
    float width = max(b.w, dist * 0.0035) * (quad == 1 ? 3.5 : 1.0);
    float along = (corner == 1 || corner == 2) ? 1.0 : 0.0, up = corner < 2 ? 1.0 : 0.0;
    vec3 pos = a.xyz + vec3(side.x, 0.0, side.y) * (along - 0.5) * width + vec3(0.0, up * a.w, 0.0);
    vColor = b.rgb;
    vUv = vec2(along, up);
    // fades in where its owner stops drawing it, and the halo is only a breath of the core
    vAlpha = smoothstep(c.x, c.x + 200.0, dist) * (quad == 1 ? 0.22 : 0.9);
    vRel = pos;
    vec4 clip = ProjMat * (FarViewRot * vec4(pos, 1.0));
    clip.z = FarDepth.x + FarDepth.y * clip.w;
    gl_Position = clip;
}
