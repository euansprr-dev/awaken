#version 330
#extension GL_ARB_separate_shader_objects : require
#include <awaken:lens_far.glsl>

// AWAKEN Lens, the final pass: depth haze toward the world's own fog colour, bloom, light shafts, the dimension's
// grade (exposure, white balance, contrast, saturation, split toning), vignette and a dither against banding.
// Post passes only get the Globals block, so the frame's fog arrives in the parameter texture with everything else.
// Never lifts shadows: a dark place (a Lightless gate, a night cave) stays as dark as the game made it.
#include <awaken:lens_composite.glsl>
