#version 330
#extension GL_ARB_separate_shader_objects : require

// Pixels' steady pass (SPEC §18, PixelsSteady): last frame's steady image, carried to where it now lies on screen
// through the depth, kept within this frame's own neighbourhood, with this frame blended in. At the world's resolution,
// before the upscale. Reversed-Z, as 26.3 draws: the sky and an empty hand keep the cleared 0.

uniform sampler2D InSampler;       // the world
uniform sampler2D DepthSampler;    // its depth
uniform sampler2D HandSampler;     // the hand's depth: the hand moves with the camera, it keeps no history
uniform sampler2D HistorySampler;  // last frame's steady image (linear)

layout(std140) uniform PixelsSteady {
    mat4 Reproject;  // this frame's NDC to last frame's clip space
    vec4 Params;     // x 1 when last frame's image holds, y 1 for zero-to-one depth, z this frame's share
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Catmull-Rom from five bilinear taps (Jimenez, "Filmic SMAA", 2016): resampling the history every frame stays sharp
vec3 history(vec2 uv) {
    vec2 size = vec2(textureSize(HistorySampler, 0));
    vec2 pos = uv * size, c = floor(pos - 0.5) + 0.5, f = pos - c;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 t0 = (c - 1.0) / size, t3 = (c + 2.0) / size, t12 = (c + w2 / w12) / size;
    vec3 r = texture(HistorySampler, vec2(t12.x, t0.y)).rgb * (w12.x * w0.y)
           + texture(HistorySampler, vec2(t0.x, t12.y)).rgb * (w0.x * w12.y)
           + texture(HistorySampler, t12).rgb * (w12.x * w12.y)
           + texture(HistorySampler, vec2(t3.x, t12.y)).rgb * (w3.x * w12.y)
           + texture(HistorySampler, vec2(t12.x, t3.y)).rgb * (w12.x * w3.y);
    float n = w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
    return max(r / n, vec3(0.0));
}

void main() {
    // the pixel as Lens finds it (from the screen quad's coordinates, the same on both graphics APIs)
    ivec2 size = textureSize(InSampler, 0), mx = size - 1, p = clamp(ivec2(texCoord * vec2(size)), ivec2(0), mx);
    vec3 c = texelFetch(InSampler, p, 0).rgb;
    // what this frame allows here: the colours of its four neighbours and its own
    vec3 n = texelFetch(InSampler, clamp(p + ivec2(0, 1), ivec2(0), mx), 0).rgb;
    vec3 s = texelFetch(InSampler, clamp(p - ivec2(0, 1), ivec2(0), mx), 0).rgb;
    vec3 e = texelFetch(InSampler, clamp(p + ivec2(1, 0), ivec2(0), mx), 0).rgb;
    vec3 w = texelFetch(InSampler, clamp(p - ivec2(1, 0), ivec2(0), mx), 0).rgb;
    vec3 lo = min(c, min(min(n, s), min(e, w))), hi = max(c, max(max(n, s), max(e, w)));
    bool hand = texelFetch(HandSampler, p, 0).r > 0.0;
    // the sky as a point very far away: its direction carries over, the camera's steps don't move it
    float d = max(texelFetch(DepthSampler, p, 0).r, 1.0e-5);
    vec2 uv = (vec2(p) + 0.5) / vec2(size);
    vec4 then = Reproject * vec4(uv * 2.0 - 1.0, Params.y > 0.5 ? d : d * 2.0 - 1.0, 1.0);
    vec2 at = then.xy / then.w * 0.5 + 0.5;
    bool held = Params.x > 0.5 && !hand && then.w > 0.0 && all(greaterThanEqual(at, vec2(0.0))) && all(lessThanEqual(at, vec2(1.0)));
    vec3 h = held ? clamp(history(at), lo, hi) : c;
    fragColor = vec4(mix(h, c, held ? Params.z : 1.0), 1.0);
}
