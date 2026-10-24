#version 330
#extension GL_ARB_separate_shader_objects : require

// Pixels' one-pass upscaler (SPEC §18): a bilinear stretch sharpened in the same pass with FSR 1's RCAS (robust
// contrast-adaptive sharpening), its ring taken one world pixel apart through the linear sampler. Most of FSR's
// crispness for about a third of its cost on Apple's GPUs, where EASU's twelve taps and the second full-screen pass
// cost more than the lower resolution saves. RCAS is AMD FidelityFX Super Resolution 1.0's (ffx_fsr1.h); AMD's
// licence is at the end of this file. SHARPNESS comes from the pipeline (PixelsPipelines).

#ifndef SHARPNESS
#define SHARPNESS 0.87
#endif
#define RCAS_LIMIT (0.25 - 1.0 / 16.0)

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float luma(vec3 c) {
    return c.b * 0.5 + (c.r * 0.5 + c.g);
}

void main() {
    vec2 px = 1.0 / vec2(textureSize(InSampler, 0));
    //    b
    //  d e f
    //    h
    vec3 b = texture(InSampler, texCoord + vec2(0.0, -px.y)).rgb;
    vec3 d = texture(InSampler, texCoord + vec2(-px.x, 0.0)).rgb;
    vec3 e = texture(InSampler, texCoord).rgb;
    vec3 f = texture(InSampler, texCoord + vec2(px.x, 0.0)).rgb;
    vec3 h = texture(InSampler, texCoord + vec2(0.0, px.y)).rgb;
    float bL = luma(b), dL = luma(d), eL = luma(e), fL = luma(f), hL = luma(h);

    float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
    float range = max(max(max(bL, dL), max(eL, fL)), hL) - min(min(min(bL, dL), min(eL, fL)), hL);
    nz = range > 0.0 ? clamp(abs(nz) / range, 0.0, 1.0) : 0.0;
    nz = -0.5 * nz + 1.0;

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
