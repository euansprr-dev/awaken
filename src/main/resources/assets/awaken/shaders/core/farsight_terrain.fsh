#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight: lit by the game's own light (the lightmap's full-sky texel: day, night, storms and the dimension's
// ambient all come with it), fogged like vanilla terrain with the horizon fog moved out to Far Sight's end. Under the
// real chunks it stays hidden, and it dithers in over the last stretch before they end.

uniform sampler2D LightSampler;

layout(location = 0) in vec3 vColor;
layout(location = 1) in vec3 vRel;
layout(location = 2) in float vWater;

layout(location = 0) out vec4 fragColor;

float bayer4(vec2 p) {
    ivec2 q = ivec2(p) & 3;
    int i = q.x + q.y * 4;
    const float m[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (m[i] + 0.5) / 16.0;
}

void main() {
    float horizontal = length(vRel.xz);
    if (horizontal < FarDepth.z + FarDepth.w * bayer4(gl_FragCoord.xy)) discard;
    vec3 c = vColor;
    if (vWater > 0.5) {
        // shallows show their sandy floor through the water; deep water keeps its colour; all of it catches the sky
        // toward grazing angles
        vec3 shallow = mix(vec3(0.80, 0.76, 0.58) * (vColor.r + vColor.g + vColor.b) / 1.2, vColor * 1.5, 0.5);
        c = mix(shallow, vColor, smoothstep(1.0, 9.0, vWater));
        float facing = abs(normalize(vRel).y);
        c = mix(c, FogColor.rgb, pow(1.0 - facing, 5.0) * 0.55);
    }
    c *= texture(LightSampler, vec2(0.5 / 16.0, 15.5 / 16.0)).rgb;
    float fog = max(linear_fog_value(length(vRel), FogEnvironmentalStart, FogEnvironmentalEnd),
                    linear_fog_value(horizontal, FarFog.x, FarFog.y));
    fragColor = vec4(mix(c, FogColor.rgb, fog * FogColor.a), 1.0);
}
