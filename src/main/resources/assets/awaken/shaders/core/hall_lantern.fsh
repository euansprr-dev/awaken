#version 330
#extension GL_ARB_separate_shader_objects : require

// The Hall's light (SPEC §52): the lit picture shows through round the lantern and everywhere below the tide (your
// level). The vertex colour isn't a colour: 12 bits of lantern x, 12 of lantern y (stage pixels), 8 of the tide's y / 4 + 1.

#include <minecraft:globals.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) flat in vec4 lamp;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    ivec4 c = ivec4(lamp * 255.0 + 0.5);
    vec2 at = vec2(float((c.r << 4) | (c.g >> 4)), float(((c.g & 15) << 8) | c.b));
    float tide = float(c.a - 1) * 4.0;
#ifdef PIC
    vec2 p = vec2(texCoord0.x * 1708.0, (1.0 - texCoord0.y) * 960.0);
#else
    vec2 p = texCoord0 * vec2(1708.0, 960.0);
#endif
    float s = GameTime * 24000.0;
    float radius = 280.0 * (1.0 + 0.022 * sin(s * 0.41) + 0.012 * sin(s * 1.13));
    float t = clamp(length(p - at) / radius, 0.0, 1.0);
    float light = t < 0.38 ? mix(1.0, 0.85, t / 0.38) : mix(0.85, 0.0, (t - 0.38) / 0.62);
    float below = clamp((p.y - (tide - 90.0)) / 96.0, 0.0, 1.0);
    below = below < 0.73 ? below / 0.73 * 0.35 : mix(0.35, 1.0, (below - 0.73) / 0.27);
    float m = max(light, below);
#ifdef PIC
    fragColor = tex * m; // premultiplied
#else
    fragColor = vec4(tex.rgb, tex.a * m);
#endif
}
