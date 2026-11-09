#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// The crossing into a gate (SPEC 50.5). A tear opens down the middle of the dark and the gate's painting is behind it; the
// painting drifts and breathes while the land loads, then burns away from the centre into the real place.
// The quad's colour carries the clock: R and G the time (2 ms steps), B how far the tear is open, A how much painting is left.
uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

const float ART_ASPECT = 16.0 / 9.0;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

// dust rising through the light, three depths
float motes(vec2 p, float t) {
    float m = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float scale = 7.0 + fi * 6.0;
        vec2 q = p * scale + vec2(sin(t * 0.13 + fi) * 0.6, t * (0.32 - fi * 0.06));
        vec2 id = floor(q), f = fract(q) - 0.5;
        float h = hash(id + fi * 7.1);
        if (h < 0.6) continue;
        vec2 o = vec2(hash(id + 1.3), hash(id + 2.7)) - 0.5;
        float twinkle = 0.45 + 0.55 * sin(t * (0.8 + h * 2.2) + h * 40.0);
        m += smoothstep(0.075 + 0.05 * h, 0.0, length(f - o * 0.7)) * twinkle * (0.75 - fi * 0.2);
    }
    return m;
}

void main() {
    float t = (floor(vertexColor.r * 255.0 + 0.5) * 256.0 + floor(vertexColor.g * 255.0 + 0.5)) * 0.002;
    float reveal = clamp(vertexColor.b, 0.0, 1.0);
    float keep = clamp(vertexColor.a * 1.004 - 0.004, 0.0, 1.0);

    vec2 s = texCoord0;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 p = (s - 0.5) * vec2(aspect, 1.0);
    float r = length(p);

    // the painting, cover-fitted, pushing in very slowly and never quite still
    vec2 fit = aspect > ART_ASPECT ? vec2(1.0, ART_ASPECT / aspect) : vec2(aspect / ART_ASPECT, 1.0);
    float zoom = mix(0.94, 0.86, smoothstep(0.0, 16.0, t));
    vec2 uv = 0.5 + (s - 0.5) * fit * zoom + vec2(0.012 * sin(t * 0.11), 0.008 * cos(t * 0.09));
    uv += 0.0013 * vec2(sin(uv.y * 24.0 + t * 0.7), cos(uv.x * 21.0 + t * 0.55));

    // the painting's own colour lights everything around it
    vec3 avg = (texture(Sampler0, vec2(0.25, 0.3)).rgb + texture(Sampler0, vec2(0.75, 0.3)).rgb + texture(Sampler0, vec2(0.5, 0.5)).rgb
            + texture(Sampler0, vec2(0.3, 0.7)).rgb + texture(Sampler0, vec2(0.7, 0.7)).rgb) / 5.0;
    vec3 glow = mix(avg / max(max(avg.r, max(avg.g, avg.b)), 0.05), vec3(1.0), 0.18);

    // the tear: a ragged seam down the middle that widens past the screen's edges
    float open = reveal * reveal * (3.0 - 2.0 * reveal);
    float rag = (fbm(vec2(p.y * 5.0, t * 0.55)) - 0.5) * 0.17 + (fbm(vec2(p.y * 19.0 + 3.0, -t * 0.9)) - 0.5) * 0.05;
    float lens = 1.0 - (1.0 - open) * smoothstep(0.1, 0.56, abs(p.y));
    float half_width = open * open * (aspect * 0.5 + 0.7) * lens + 0.004 * lens;
    float d = abs(p.x + rag * (1.0 - open * 0.8)) - half_width;
    float inside = 1.0 - smoothstep(-0.008, 0.008, d);
    float settling = 1.0 - smoothstep(0.82, 1.0, open);
    float rim = exp(-abs(d) * 20.0) * settling;

    // near the seam the painting bends and its colours part
    vec2 bend = p / max(r, 0.001) * rim * 0.035;
    vec2 part = p * (0.0022 + rim * 0.011);
    vec3 col;
    col.r = texture(Sampler0, uv + bend + part).r;
    col.g = texture(Sampler0, uv + bend).g;
    col.b = texture(Sampler0, uv + bend - part).b;

    // a slow band of light travels across it, and dust hangs in front
    col += glow * 0.06 * pow(0.5 + 0.5 * sin(s.x * 3.0 + s.y * 2.0 - t * 0.28), 8.0);
    col += glow * motes(p, t) * 0.5;

    // the frame: darker to the edges, and darker low down where the words sit
    col *= 1.0 - 0.58 * smoothstep(0.38, 1.0, length(p * vec2(0.78, 1.12)));
    col *= mix(1.0, 0.24, smoothstep(0.52, 0.98, s.y));

    // outside the tear: dark, with smoke lit from the seam and light rushing outward
    float smoke = fbm(p * 3.2 + vec2(t * 0.18, -t * 0.11));
    vec3 dark = vec3(0.010, 0.010, 0.016) + glow * smoke * 0.10 * exp(-abs(p.x) * 2.6);
    float ang = atan(p.y, p.x);
    float ray = pow(noise(vec2(ang * 40.0, 3.0)), 6.0);
    float rush = fract(r * 1.5 - t * 1.25 + ray * 5.0);
    dark += glow * ray * smoothstep(0.62, 1.0, rush) * 1.6 * smoothstep(0.02, 0.5, r) * (1.0 - open);

    col = mix(dark, col, inside);
    col += glow * rim * 1.5 + vec3(1.0) * exp(-abs(d) * 95.0) * settling * 1.25;

    // the world comes through from the middle, the painting burning back at its edge
    float alpha = 1.0;
    if (keep < 0.999) {
        float m = fbm(p * 4.3) * 0.56 + r * 0.64;
        float th = (1.0 - keep) * 1.42 - 0.12;
        alpha = smoothstep(th - 0.06, th + 0.06, m);
        col += glow * exp(-abs(m - th) * 24.0) * 1.3 * step(0.001, 1.0 - keep);
    }

    // black bars while it plays; they slide away with the painting
    float bar = 0.078 * smoothstep(0.1, 0.75, open) * smoothstep(0.0, 0.55, keep);
    if (abs(s.y - 0.5) > 0.5 - bar) {
        col = vec3(0.0);
        alpha = 1.0;
    }

    if (alpha <= 0.002) discard;
    fragColor = vec4(col, alpha);
}
