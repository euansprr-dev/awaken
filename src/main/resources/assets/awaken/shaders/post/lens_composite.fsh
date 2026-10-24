#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens, the final pass: depth haze toward the world's own fog colour, bloom, light shafts, the dimension's
// grade (exposure, white balance, contrast, saturation, split toning), vignette and a dither against banding.
// Post passes only get the Globals block, so the frame's fog arrives in the parameter texture with everything else.
// Never lifts shadows: a dark place (a Lightless gate, a night cave) stays as dark as the game made it.
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BloomSampler;
uniform sampler2D ShaftsSampler;
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Cinematic;
};

layout(location = 0) out vec4 fragColor;

vec4 P(int i) { return texelFetch(ParamsSampler, ivec2(i, 0), 0); }

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - clamp(b, 0.0, 1.0)); }

// view distance in blocks from reversed-Z depth; the projection's depth range differs between OpenGL and Vulkan
float viewDistance(float depth, float zFar, bool zeroToOne) {
    const float zNear = 0.05;
    if (zeroToOne) return zNear * zFar / (depth * (zFar - zNear) + zNear);
    float ndc = depth * 2.0 - 1.0;
    return 2.0 * zFar * zNear / (ndc * (zFar - zNear) + (zFar + zNear));
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec4 p0 = P(0), p1 = P(1), g = P(3), shadowT = P(4), highT = P(5), b = P(6), h = P(7), hz = P(8), sunset = P(9);
    vec4 fogColor = P(10), fogDist = P(11);
    float farPlane = max((p1.r * 255.0 * 256.0 + p1.g * 255.0), 16.0);
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = depth < 1.0e-7;

    // depth haze: aerial perspective toward the fog colour the game already chose for this place and hour
    float d = sky ? farPlane : viewDistance(depth, farPlane, p0.g > 0.5);
    float rdEnd = max(fogDist.r * 255.0 * 256.0 + fogDist.g * 255.0, 16.0);
    float envEnd = fogDist.b * 255.0 * 256.0 + fogDist.a * 255.0;
    float haze = sky ? h.r * 0.12 : smoothstep(h.g * rdEnd, rdEnd * 1.1, d) * h.r;
    // underwater, in lava or blind, the game's own fog already hides the distance
    haze *= clamp((envEnd - 8.0) / rdEnd, 0.0, 1.0);
    vec3 hazeColor = mix(fogColor.rgb, hz.rgb, h.b);
    float l0 = luma(c);
    c = mix(c, mix(c, vec3(l0), h.a), haze);
    c = mix(c, hazeColor, haze * 0.55);

    // bloom and light shafts, screen-blended so nothing clips to white
    c = screen(c, texture(BloomSampler, texCoord).rgb * b.r * 2.0);
    if (Cinematic > 0.5) {
        vec3 sunColor = mix(vec3(1.0, 0.96, 0.88), sunset.rgb, p1.a);
        c = screen(c, texture(ShaftsSampler, texCoord).r * sunColor * 1.4);
    }

    // grade
    float exposure = min(g.r * 2.0, 1.05);
    float warmth = g.a * 2.0 - 1.0 + p1.a * hz.a;
    c *= exposure * vec3(1.0 + 0.07 * warmth, 1.0 + 0.012 * warmth, 1.0 - 0.07 * warmth);
    float l = luma(c);
    c += (shadowT.rgb - vec3(luma(shadowT.rgb))) * (1.0 - l) * (1.0 - l) * shadowT.a;
    c += (highT.rgb - vec3(luma(highT.rgb))) * l * l * shadowT.a;
    c = clamp((c - 0.5) * (g.g * 2.0) + 0.5, 0.0, 1.0);
    c = mix(vec3(luma(c)), c, g.b * 2.0);

    // vignette, rounder on wide screens
    vec2 v = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0) * 0.82;
    c *= mix(1.0, smoothstep(0.98, 0.32, length(v)), highT.a);

    // triangular dither: smooth haze and sky gradients without 8-bit bands
    vec2 q = gl_FragCoord.xy;
    float n = fract(sin(dot(q, vec2(12.9898, 78.233))) * 43758.5453) + fract(sin(dot(q, vec2(39.3468, 11.135))) * 24634.6345) - 1.0;
    c += n / 255.0;
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
