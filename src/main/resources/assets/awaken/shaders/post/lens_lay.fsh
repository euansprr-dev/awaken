#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens (Lite, Cinematic, Ultra), the last pass: the finished picture into the game's frame, and over it the light
// the rest of the mod drew for Lens to lay (LensGlow, SPEC §22 "Light over the Air": the Wilds' windways and updrafts).
// That light was drawn after the level, tested against the world's depth, into a layer of its own: paint over what is
// behind it, colour premultiplied in rgb and how much it covers in a. Laid here, over the sky, fog and clouds Lens put on
// the frame, it is never taken for something solid and never goes under a cloud. On a frame nothing drew, the layer is
// one clear texel and this is the copy it replaces.
uniform sampler2D InSampler;       // the composite's picture
uniform sampler2D GlowSampler;     // LensGlow's layer
uniform sampler2D HandSampler;     // the hand's own depth (LensHand): 0 where there is no hand
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

#include <awaken:lens_block.glsl>

vec4 P(int i) { return LensUse.x > 0.5 ? LensP[i] : texelFetch(ParamsSampler, ivec2(i, 0), 0); }

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec4 g = texture(GlowSampler, texCoord);
    // most pixels of most frames hold none; a development view (texel 0 red: 255 = the picture) shows what it shows
    if (g.a > 0.0 && int(P(0).r * 255.0 + 0.5) == 255) {
        // the layer was tested against the world's depth, which has no hand in it: the hand and what it holds stay in
        // front (known by their own depth where Lens has it, parameter texel 26 r)
        bool hand = P(26).r > 0.5 && texture(HandSampler, texCoord).r > 0.0;
        if (!hand) {
            // what the composite does to the whole picture after its colour, done to this light too, so it sits in the
            // picture and not on it: an open holo panel dims and cools the world behind it (texel 14 r), the vignette
            // (texel 5 a). Both scale the picture, so laying the light after them is the same as before them
            float focus = P(14).r;
            vec3 l = g.rgb;
            l = mix(l, mix(vec3(dot(l, vec3(0.2126, 0.7152, 0.0722))), l, 0.5) * vec3(0.8, 0.88, 1.0) * 0.6, focus);
            vec2 v = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0) * 0.82;
            l *= mix(1.0, smoothstep(1.02, 0.4, length(v)), P(5).a * 0.75);
            c = c * (1.0 - g.a) + l;
        }
    }
    fragColor = vec4(c, 1.0);
}
