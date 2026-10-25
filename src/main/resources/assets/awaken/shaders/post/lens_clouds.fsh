#version 330
#extension GL_ARB_separate_shader_objects : require

// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22, L4): volumetric clouds in place of the game's blocky ones. Each pixel's
// view ray is marched through the cloud layer (to where it meets terrain, if that's nearer), lit by a few steps toward
// the light: a silver lining toward the sun (a forward lobe), soft undersides, warm at golden hour, the game's own cloud
// colour (time of day, weather) as the sky's light on them. Distant cloud fades into the haze. Fresh noise each frame,
// blended into last frame's result reprojected by where the ray entered the clouds.
// Out: rgb the light the clouds send this way (premultiplied), a how much of what's behind still shows.
uniform sampler2D DepthSampler;     // the world's depth, full size (first input: InSize is the full frame)
uniform sampler2D CloudNoiseSampler;
uniform sampler2D HistorySampler;
uniform sampler2D FrameSampler;
uniform sampler2D ParamsSampler;

#include <awaken:lens_frame.glsl>
#include <awaken:lens_clouds.glsl>

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    float Quality;                  // 1 Cinematic, 2 Ultra
};

layout(location = 0) out vec4 fragColor;

vec4 P(int i) { return texelFetch(ParamsSampler, ivec2(i, 0), 0); }

float hg(float mu, float g) { return (1.0 - g * g) / (12.566 * pow(1.0 + g * g - 2.0 * g * mu, 1.5)); }

void main() {
    vec4 cp = cloudParams(), f12 = F(12), f14 = F(14), f16 = F(16);
    if (cp.x <= 0.0 || f16.w <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    bool z01 = f16.x > 0.5, ultra = Quality > 1.5;
    ivec2 px = clamp(ivec2(texCoord * InSize), ivec2(0), ivec2(InSize) - 1);
    float depth = texelFetch(DepthSampler, px, 0).r;
    bool sky = depth < 1.0e-7;
    vec3 p = unproject(frameMat(0), (vec2(px) + 0.5) / InSize, sky ? 1.0e-5 : depth, z01);
    vec3 v = normalize(p);
    float scene = sky ? 1.0e9 : length(p), camY = f12.y;
    float base = cp.x - 6.0, top = base + CLOUD_THICK, maxD = ultra ? 4000.0 : 3000.0;
    // where the ray is inside the layer
    float t0 = 0.0, t1 = 0.0;
    if (abs(v.y) > 1.0e-4) {
        float a = (base - camY) / v.y, b = (top - camY) / v.y;
        t0 = max(min(a, b), 0.0);
        t1 = max(a, b);
    } else if (camY > base && camY < top) {
        t1 = maxD;
    }
    t1 = min(min(t1, scene), min(maxD, t0 + (ultra ? 1600.0 : 1200.0)));
    if (t1 <= t0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    int steps = ultra ? 32 : 20, lightSteps = ultra ? 5 : 3;
    float jitter = ign(gl_FragCoord.xy, f12.w), dt = (t1 - t0) / float(steps);
    vec3 l = f14.xyz;
    float mu = dot(v, l);
    // a strong even part (light scattered many times inside a cloud keeps it white from every side) and a forward lobe
    // for the silver lining toward the sun
    float phase = 0.8 + 3.0 * hg(mu, 0.7);
    // the sun's (or moon's) light on them and the sky's: the game's cloud colour is the sky's light by hour and weather
    vec4 p1 = P(1), cc = F(19);
    vec3 sunCol = f16.y > 0.5 ? vec3(0.55, 0.65, 0.9) * 0.6 : mix(vec3(1.0, 0.96, 0.9), P(9).rgb * 1.15, p1.a * 0.9);
    sunCol *= f14.w * 1.25;
    vec3 ambient = cc.rgb * mix(P(10).rgb, vec3(1.0), 0.55) * 0.55;
    float T = 1.0;
    vec3 L = vec3(0.0);
    const float sigma = 0.045;
    for (int i = 0; i < steps; i++) {
        float t = t0 + (float(i) + jitter) * dt;
        vec3 w = v * t;
        float d = cloudDensity(w, cp, camY, false);
        if (d <= 0.004) continue;
        // light reaching this point through the cloud above it, toward the sun
        float od = 0.0, s = 6.0, lt = 0.0;
        for (int k = 0; k < lightSteps; k++) {
            lt += s;
            od += cloudDensity(w + l * lt, cp, camY, true) * s;
            s *= 2.0;
        }
        float h = clamp((w.y + camY - base) / CLOUD_THICK, 0.0, 1.0);
        float beer = exp(-od * sigma * 1.4);
        // powder: the thin edges facing the sun glow, dense cores darken (a cheap stand-in for multiple scattering)
        float powder = 1.0 - exp(-d * 6.0);
        vec3 S = sunCol * beer * phase * mix(1.0, powder, 0.5) + ambient * (0.35 + 0.65 * h);
        float a = 1.0 - exp(-d * sigma * dt);
        L += T * S * a;
        T *= 1.0 - a;
        if (T < 0.02) break;
    }
    // far cloud melts into the haze toward the horizon
    float far = smoothstep(maxD * 0.35, maxD, t0);
    L = mix(L, P(10).rgb * (1.0 - T), far * 0.8);
    T = mix(T, 1.0, far * far * 0.85);
    vec4 res = vec4(L, T);
    // last frame's clouds along this ray, found where it entered them
    vec4 f13 = F(13);
    if (f13.w > 0.5) {
        vec3 s = project(frameMat(8), v * max(t0, 1.0) + f13.xyz, z01);
        if (s.x > 0.0 && s.y > 0.0 && s.x < 1.0 && s.y < 1.0) res = mix(texture(HistorySampler, s.xy), res, ultra ? 0.2 : 0.25);
    }
    fragColor = clamp(res + (jitter - 0.5) / 255.0, 0.0, 1.0);
}
