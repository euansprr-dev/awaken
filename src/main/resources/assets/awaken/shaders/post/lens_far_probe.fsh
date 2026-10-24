#version 330
#extension GL_ARB_separate_shader_objects : require
#include <awaken:lens_far.glsl>

// AWAKEN Lens, pass 0 (one pixel): has far terrain's depth proven itself? A depth that reads as one value everywhere (a
// placeholder, a texture that doesn't sample on this graphics API) can't be told from sky: trusting it would haze the
// sky as far terrain, or haze only the near terrain and draw a ring where it ends. So Lens trusts a shared far depth
// once it has seen two different depths where far terrain drew, and keeps that answer in a persistent pixel
// (awaken:lens_far_trust) while the depth stays shared. Until then, far terrain gets no distance haze.
uniform sampler2D FarDepthSampler;
uniform sampler2D TrustSampler;     // last frame's answer
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 p0 = texelFetch(ParamsSampler, ivec2(0, 0), 0);
    int flags = farFlags(p0);
    // nothing shared: whatever is shared next proves itself again
    if ((flags & 4) == 0) {
        fragColor = vec4(0.0);
        return;
    }
    vec4 last = texelFetch(TrustSampler, ivec2(0, 0), 0);
    bool proven = last.r > 0.5;
    if (!proven && p0.b > 0.5) {
        // 8x6 taps across the frame: the nearest and farthest depth where far terrain drew
        float lo = 1.0, hi = 0.0;
        int hits = 0;
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 8; x++) {
                float d = textureLod(FarDepthSampler, (vec2(x, y) + 0.5) / vec2(8.0, 6.0), 0.0).r;
                if (farDrew(d, flags)) {
                    lo = min(lo, d);
                    hi = max(hi, d);
                    hits++;
                }
            }
        }
        proven = hits >= 2 && hi - lo > 1.0e-5;
    }
    // once proven, the haze over far terrain eases in over 32 frames (about half a second) instead of popping on
    fragColor = vec4(proven ? 1.0 : 0.0, proven ? min(1.0, last.g + 1.0 / 32.0) : 0.0, 0.0, 1.0);
}
