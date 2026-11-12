#version 330
#extension GL_ARB_separate_shader_objects : require
#define LENS_LIGHT
#define PROBE_NUDGE
#include <awaken:lens_far.glsl>

// MEASUREMENT ONLY (tools/lens/twincheck.py, SPEC §22 "Steady 60"): lens_composite_lit.fsh with every frame and look
// value one step of its last bit up: which pixels of the written composite turn on its own rounding.
#include <awaken:lens_composite.glsl>
