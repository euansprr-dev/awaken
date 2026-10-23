#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN impact frame: for a few frames after an ultimate lands the picture goes hard and graphic, like an anime
// impact frame. High contrast, mostly desaturated, smeared outward from the centre with split colour fringes, and a
// dark vignette. Strength steps down tick by tick (power_fx_impact_3 .. _0).
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ImpactConfig {
    vec4 Params; // x = strength 0..1, y = radial smear, z = colour split, w = invert mix
};

layout(location = 0) out vec4 fragColor;

void main() {
    float s = Params.x;
    vec2 d = texCoord - vec2(0.5);
    float r = length(d);
    // radial smear: average a few taps toward the centre
    vec3 acc = vec3(0.0);
    float smear = Params.y * s;
    for (int i = 0; i < 6; i++) {
        float k = 1.0 - smear * float(i) / 6.0;
        acc += texture(InSampler, vec2(0.5) + d * k).rgb;
    }
    vec3 c = acc / 6.0;
    // split colour fringes toward the edges
    float split = Params.z * s * r;
    c.r = mix(c.r, texture(InSampler, vec2(0.5) + d * (1.0 + split)).r, 0.7);
    c.b = mix(c.b, texture(InSampler, vec2(0.5) + d * (1.0 - split)).b, 0.7);
    // hard contrast and desaturation
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    vec3 grey = vec3(smoothstep(0.18, 0.62, luma));
    c = mix(c, mix(grey, c, 0.25), s * 0.85);
    // a frame of inverted ink at full strength
    c = mix(c, vec3(1.0) - c, Params.w * s);
    c *= mix(1.0, smoothstep(0.95, 0.3, r), 0.6 * s);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
