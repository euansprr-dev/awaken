#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens (Cinematic and Ultra), light shafts: march from each pixel toward the sun on screen, gathering how much
// open, bright sky lies on the way. Clouds, hills and trees between you and the sun cut the rays. The same pass spreads
// the wide glow once more for lights glowing in fog (Lens 2: the gates' light pools), at this quarter size rather than
// in the full-size composite.
// Out: r the shafts, g/b/a the fog glow.
uniform sampler2D InSampler;      // quarter-size frame; alpha = open-sky brightness
uniform sampler2D WideSampler;    // the wide glow, eighth size
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

vec4 P(int i) { return texelFetch(ParamsSampler, ivec2(i, 0), 0); }

float unpack16(vec2 v) { return (v.x * 255.0 * 256.0 + v.y * 255.0) / 65535.0; }

void main() {
    // fog glow: the wide glow on two rings around this pixel (a soft halo several times the glow's own reach)
    vec3 fog = vec3(0.0);
    if (P(19).b > 0.0) {
        vec2 aspect = vec2(OutSize.y / OutSize.x, 1.0);
        fog = texture(WideSampler, texCoord).rgb * 0.2;
        for (int k = 0; k < 6; k++) {
            float a = float(k) * 1.0472;
            vec2 d = vec2(cos(a), sin(a)) * aspect;
            fog += texture(WideSampler, texCoord + d * 0.035).rgb * 0.08 + texture(WideSampler, texCoord + d.yx * vec2(-1.0, 1.0) * 0.075).rgb * 0.053;
        }
    }
    vec4 s1 = P(1);                   // b sun visible, a sunset strength
    vec4 s2 = P(2);                   // sun position on screen, 16 bits each, mapped from [-1, 2]
    float strength = P(6).a * s1.b;
    if (strength <= 0.0) {
        fragColor = vec4(0.0, fog);
        return;
    }
    vec2 sun = vec2(unpack16(s2.rg), unpack16(s2.ba)) * 3.0 - 1.0;
    const int STEPS = 24;
    vec2 dir = (sun - texCoord) * (0.92 / float(STEPS));
    vec2 p = texCoord;
    float decay = 1.0, light = 0.0;
    for (int i = 0; i < STEPS; i++) {
        p += dir;
        light += texture(InSampler, p).a * decay;
        decay *= 0.93;
    }
    // fades out toward the screen edges the sun is beyond, and with distance from the sun
    float close = 1.0 - smoothstep(0.0, 1.1, length((sun - texCoord) * vec2(OutSize.x / OutSize.y, 1.0)));
    fragColor = vec4(light / float(STEPS) * strength * (0.35 + 0.65 * close), fog);
}
