#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// Awaken's holo glow: true additive light (blend ONE, ONE; alpha is left alone). Shapes, chosen by UV2:
//   (0, 0)   flat: the vertex colour, premultiplied
//   (w, h)   halo: UV0 is in units around a w×h panel; light falls off outside its rectangle
//   (-1, 0)  blob: UV0 in [-1, 1]², a soft round light
//   (-2, 0)  line: UV0.y in [-1, 1] across the line, a bright core with a soft skirt
layout(location = 0) in vec2 local;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in ivec2 shape;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 c = vertexColor * ColorModulator;
    float k = 1.0;
    if (shape.x > 0) {
        vec2 s = vec2(shape);
        vec2 outside = max(max(-local, local - s), vec2(0.0));
        float d = length(outside);
        float inside = (local.x > 0.0 && local.y > 0.0 && local.x < s.x && local.y < s.y) ? 1.0 : 0.0;
        k = (1.0 - inside) * exp(-d / 3.2) * smoothstep(0.0, 0.6, d);
    } else if (shape.x == -1) {
        float r2 = dot(local, local);
        k = r2 >= 1.0 ? 0.0 : (1.0 - r2) * (1.0 - r2);
    } else if (shape.x == -2) {
        float y = abs(local.y);
        k = exp(-y * y * 9.0) * 0.75 + exp(-y * y * 90.0) * 0.6;
        float x = abs(local.x);
        k *= 1.0 - smoothstep(0.82, 1.0, x);
    }
    vec3 light = c.rgb * c.a * k;
    if (max(light.r, max(light.g, light.b)) < 0.002) discard;
    fragColor = vec4(light, 0.0);
}
