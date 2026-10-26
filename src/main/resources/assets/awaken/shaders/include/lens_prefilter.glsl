// AWAKEN Lens, the bloom prefilter's body, shared by lens_prefilter.fsh (Swift) and lens_prefilter_lit.fsh (Cinematic
// and Ultra, LENS_LIGHT: Lens 2's clouds join the sky). Include lens_far.glsl first.
uniform sampler2D InSampler;     // the frame, bilinear
uniform sampler2D DepthSampler;  // its depth: reversed-Z, so the sky keeps the cleared 0
uniform sampler2D FarDepthSampler; // far terrain's depth (Distant Horizons), when it draws
uniform sampler2D FarTrustSampler; // whether that depth has proven itself (lens_far_probe)
uniform sampler2D ParamsSampler;
#ifdef LENS_LIGHT
uniform sampler2D CloudsSampler;   // Lens 2's clouds (lens_clouds.fsh): rgb their light, a what shows through
#endif

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

#include <awaken:lens_block.glsl>

vec4 P(int i) { return LensUse.x > 0.5 ? LensP[i] : texelFetch(ParamsSampler, ivec2(i, 0), 0); }

void main() {
    // four bilinear taps, each the mean of a 2x2 block: a cheap box filter that keeps small lights from flickering
    vec2 t = 1.0 / InSize;
    vec3 c = (texture(InSampler, texCoord + t * vec2(-1.0, -1.0)).rgb + texture(InSampler, texCoord + t * vec2(1.0, -1.0)).rgb
            + texture(InSampler, texCoord + t * vec2(-1.0, 1.0)).rgb + texture(InSampler, texCoord + t * vec2(1.0, 1.0)).rgb) * 0.25;
    float sky = texture(DepthSampler, texCoord).r < 1.0e-7 ? 1.0 : 0.0;
#ifdef LENS_LIGHT
    // the clouds are part of the sky that blooms and that the shafts are made of: bright edges glow, cloud cuts the rays
    vec4 cl = texture(CloudsSampler, texCoord);
    // (the shafts read the open sky's own brightness: bright cloud tops below the camera mustn't streak into rays)
    float openLuma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    if (sky > 0.5) c = c * cl.a + cl.rgb;
#endif
    vec4 p0 = P(0);
    // far terrain isn't open sky (no sky bloom or light shafts through it), by its own depth once Lens trusts that
    if (sky > 0.5 && p0.b > 0.5 && farHit(texture(FarDepthSampler, texCoord).r, farFlags(p0), texelFetch(FarTrustSampler, ivec2(0, 0), 0))) sky = 0.0;
    vec4 b = P(6);                    // r bloom strength/2, g threshold, b sky bloom, a shaft strength
    // keyed on the brightest channel, not luminance: Awaken's glows are saturated (pink vortices, blue runes, red
    // eyes) and luminance alone would never let them bloom; plain white surfaces (clouds, snow) bloom less
    float hi = max(c.r, max(c.g, c.b)), lo = min(c.r, min(c.g, c.b));
    float sat = hi > 1.0e-4 ? (hi - lo) / hi : 0.0;
    // soft-knee threshold: lights fade into the bloom instead of popping in
    float knee = 0.22;
    float soft = clamp(hi - b.g + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1.0e-4);
    float w = max(soft, hi - b.g) / max(hi, 1.0e-4);
    w = clamp(w, 0.0, 1.0) * mix(0.45, 1.0, sat) * mix(1.0, b.b, sky);
    // AWAKEN's glows (bright, saturated, not a warm hue: runes, vortices, the Signal's blue, eyes) bloom more than lit
    // surfaces of the same brightness, by the look's glow (texel 20 a; Lens 2)
    float cool = c.b >= c.g * 0.9 || (c.g > c.r && c.b > c.r) ? 1.0 : 0.0;
    w *= 1.0 + 1.5 * P(20).a * smoothstep(0.6, 0.9, hi) * smoothstep(0.45, 0.8, sat) * cool;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
#ifdef LENS_LIGHT
    // (only partly: soft clouds cutting the rays fully streak the whole sky like rain curtains)
    sky *= mix(1.0, cl.a, 0.6);
    luma = openLuma;
#endif
    fragColor = vec4(c * w, sky * smoothstep(0.35, 0.95, luma));
}
