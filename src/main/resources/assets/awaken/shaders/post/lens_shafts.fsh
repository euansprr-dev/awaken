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
uniform sampler2D FrameSampler;   // LensFrame: texel 21 the sun on screen

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

#include <awaken:lens_block.glsl>

vec4 P(int i) { return LensUse.x > 0.5 ? LensP[i] : texelFetch(ParamsSampler, ivec2(i, 0), 0); }
vec4 F(int i) { return LensUse.x > 0.5 ? LensF[i] : texelFetch(FrameSampler, ivec2(i, 0), 0); }

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
    float strength = P(6).a * s1.b;
    if (strength <= 0.0) {
        fragColor = vec4(0.0, fog);
        return;
    }
    // where the world was really drawn (WorldProjection: a camera mod's roll included)
    vec2 sun = clamp(F(21).xy, -1.0, 2.0);
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
