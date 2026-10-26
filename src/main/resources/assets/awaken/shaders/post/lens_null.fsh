#version 330
#extension GL_ARB_separate_shader_objects : require

// Measurement only (tools/lens/probes.py, docs/perf/LENS-APPLE.md): stands in for one Lens pass in a probe chain, so the
// GPU time it saves is that pass's cost. Writes a constant; reads nothing.
layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(1.0, 1.0, 0.0, 1.0);
}
