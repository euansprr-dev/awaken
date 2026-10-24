#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight landmarks: soft across, thinning toward the top, piercing the haze (fog only half covers a glow).
layout(location = 0) in vec3 vColor;
layout(location = 1) in vec2 vUv;
layout(location = 2) in float vAlpha;
layout(location = 3) in vec3 vRel;

layout(location = 0) out vec4 fragColor;

void main() {
    float across = 1.0 - abs(vUv.x * 2.0 - 1.0);
    float a = vAlpha * across * across * mix(1.0, 0.15, vUv.y);
    if (a < 0.004) discard;
    float fog = max(linear_fog_value(length(vRel), FogEnvironmentalStart, FogEnvironmentalEnd),
                    linear_fog_value(length(vRel.xz), FarFog.x, FarFog.y)) * 0.5;
    fragColor = vec4(mix(vColor * 1.2, FogColor.rgb, fog), a);
}
