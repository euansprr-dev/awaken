#version 330
#extension GL_ARB_separate_shader_objects : require
#define LENS_LIGHT
#define PROBE_NO_CLOUDS
// measurement only (tools/lens/probes.py): the lit composite without one part
#include <awaken:lens_far.glsl>

// AWAKEN Lens 2 (Cinematic and Ultra), the final pass: Lens's composite with the light (sun, shade, ambient occlusion
// from lens_light.fsh) applied before the haze, and a filmic curve in place of the plain contrast (SPEC §22).
// Post passes only get the Globals block, so the frame's fog arrives in the parameter texture with everything else.
// Never lifts shadows: a dark place (a Lightless gate, a night cave) stays as dark as the game made it.
#include <awaken:lens_composite.glsl>
