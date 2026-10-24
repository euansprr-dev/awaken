#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

// Awaken's holo glass (docs/modules/holo.md). A flat fill (size 0) is its vertex colour. A panel (size > 0) is deep
// signal-blue glass: a vertical gradient, a soft inner glow along the edges, a hairline edge that stays about 1.5
// framebuffer pixels thick at any distance (fwidth), brighter corner brackets, faint drifting scanlines and a sheen
// across the top. Vertex colour: rgb = the edge accent, a = the panel's opacity (the unfold fades it in).
layout(location = 0) in vec2 local;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in ivec2 size;

layout(location = 0) out vec4 fragColor;

const vec3 GLASS_TOP = vec3(0.086, 0.137, 0.227);
const vec3 GLASS_BOTTOM = vec3(0.047, 0.075, 0.122);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    if (size.x <= 0) {
        vec4 c = vertexColor * ColorModulator;
        if (c.a < 0.004) discard;
        fragColor = c;
        return;
    }
    vec2 s = vec2(size);
    vec2 px = max(fwidth(local), vec2(1e-4));          // panel units per framebuffer pixel
    vec2 edgeDist = min(local, s - local);              // units to the nearest vertical / horizontal edge
    vec2 edgePx = edgeDist / px;
    float dPx = min(edgePx.x, edgePx.y);
    float dUnits = min(edgeDist.x, edgeDist.y);
    vec3 accent = vertexColor.rgb;
    float t = GameTime * 1200.0;                        // seconds

    float v = clamp(local.y / s.y, 0.0, 1.0);
    vec3 col = mix(GLASS_TOP, GLASS_BOTTOM, v);
    float alpha = 0.84;

    // sheen: a soft reflection in the top fifth, strongest at the top-left
    float sheen = smoothstep(0.22, 0.0, v) * (0.55 + 0.45 * (1.0 - local.x / s.x));
    col += vec3(0.55, 0.75, 1.0) * sheen * 0.045;

    // inner glow hugging the edges
    float inner = exp(-dUnits / 9.0);
    col += accent * inner * 0.16;

    // a slow glint sweeping across the glass every nine seconds
    float diag = (local.x + local.y * 0.6) / (s.x + s.y * 0.6);
    float sweep = diag - (fract(t / 9.0) * 1.8 - 0.4);
    col += vec3(0.75, 0.88, 1.0) * exp(-sweep * sweep * 260.0) * 0.045;

    // faint scanlines drifting down, two units apart
    float scan = 0.5 + 0.5 * sin((local.y - t * 6.0) * 3.14159);
    col += accent * scan * 0.012;

    // hairline edge, ~1.5 px, anti-aliased
    float edge = 1.0 - smoothstep(0.6, 1.9, dPx);
    col = mix(col, accent, edge * 0.70);
    alpha = max(alpha, edge * 0.95);

    // corner brackets: 14 units along each edge from every corner, 2.6 px thick
    vec2 fromCorner = min(local, s - local);
    float along = max(fromCorner.x, fromCorner.y);
    float bracketLen = min(14.0, min(s.x, s.y) / 3.0);
    float bracket = (1.0 - smoothstep(1.4, 2.8, dPx)) * (1.0 - smoothstep(bracketLen - 0.5, bracketLen, along));
    col = mix(col, accent * 1.25 + 0.08, bracket);
    alpha = max(alpha, bracket);

    // a breath of grain so the gradient never bands
    col += (hash(gl_FragCoord.xy + fract(t)) - 0.5) / 255.0;

    vec4 c = vec4(col, alpha * vertexColor.a) * ColorModulator;
    if (c.a < 0.004) discard;
    fragColor = c;
}
