#version 330
#extension GL_ARB_separate_shader_objects : require

// MEASUREMENT ONLY (tools/lens/twincheck.py; SPEC §22 "Steady 60"): two composites of the same frame, from the same
// inputs, side by side. Where they differ at all, in this pixel or one beside it (a mark three pixels wide survives the
// upscale to the screen), the mark says by how much, in steps of 8 bits in the channel that differs most: magenta one
// step, yellow two to four, cyan more. Elsewhere the picture at half its brightness, so nothing of it reads as a mark.
uniform sampler2D ASampler;
uniform sampler2D BSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy), last = textureSize(ASampler, 0) - 1;
    float most = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 q = clamp(px + ivec2(x, y), ivec2(0), last);
            vec3 d = abs(texelFetch(ASampler, q, 0).rgb - texelFetch(BSampler, q, 0).rgb) * 255.0;
            most = max(most, max(d.r, max(d.g, d.b)));
        }
    }
    if (most < 0.5) fragColor = vec4(texelFetch(ASampler, px, 0).rgb * 0.5, 1.0);
    else if (most < 1.5) fragColor = vec4(1.0, 0.0, 1.0, 1.0);
    else if (most < 4.5) fragColor = vec4(1.0, 1.0, 0.0, 1.0);
    else fragColor = vec4(0.0, 1.0, 1.0, 1.0);
}
