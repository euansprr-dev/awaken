#version 330
#extension GL_ARB_separate_shader_objects : require

// Pixels, FSR 1 pass 2 (SPEC §18): RCAS, robust contrast-adaptive sharpening of EASU's output, at the screen's
// resolution. A port of FsrRcasF from AMD FidelityFX Super Resolution 1.0 (ffx_fsr1.h), with its noise detection on,
// exact reciprocals instead of the approximations. SHARPNESS comes from the pipeline (PixelsPipelines): exp2(-stops).
// AMD's licence is at the end of this file.

#ifndef SHARPNESS
#define SHARPNESS 0.87
#endif
// the most a ring sample may be weighted against the centre (AMD: 0.25 - 1/16)
#define RCAS_LIMIT (0.25 - 1.0 / 16.0)

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float luma(vec3 c) {
    return c.b * 0.5 + (c.r * 0.5 + c.g);
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 mx = size - 1;
    ivec2 p = clamp(ivec2(texCoord * vec2(size)), ivec2(0), mx);
    //    b
    //  d e f
    //    h
    vec3 b = texelFetch(InSampler, clamp(p + ivec2(0, -1), ivec2(0), mx), 0).rgb;
    vec3 d = texelFetch(InSampler, clamp(p + ivec2(-1, 0), ivec2(0), mx), 0).rgb;
    vec3 e = texelFetch(InSampler, p, 0).rgb;
    vec3 f = texelFetch(InSampler, clamp(p + ivec2(1, 0), ivec2(0), mx), 0).rgb;
    vec3 h = texelFetch(InSampler, clamp(p + ivec2(0, 1), ivec2(0), mx), 0).rgb;
    float bL = luma(b), dL = luma(d), eL = luma(e), fL = luma(f), hL = luma(h);

    // noise detection: a lone pixel against its ring is grain, not detail; sharpen it less
    float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
    float range = max(max(max(bL, dL), max(eL, fL)), hL) - min(min(min(bL, dL), min(eL, fL)), hL);
    nz = range > 0.0 ? clamp(abs(nz) / range, 0.0, 1.0) : 0.0;
    nz = -0.5 * nz + 1.0;

    // the strongest negative lobe that keeps every channel inside [0, 1] around the ring
    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));
    vec3 hitMin = mn4 / max(4.0 * mx4, vec3(1.0 / 65536.0));
    vec3 hitMax = (1.0 - mx4) / min(4.0 * mn4 - 4.0, vec3(-1.0 / 65536.0));
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0)) * SHARPNESS;
    lobe *= nz;

    fragColor = vec4((lobe * (b + d + f + h) + e) / (4.0 * lobe + 1.0), 1.0);
}

// AMD FidelityFX Super Resolution 1.0 (ffx_fsr1.h)
// Copyright (c) 2021 Advanced Micro Devices, Inc. All rights reserved.
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
// rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
// Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
// COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
