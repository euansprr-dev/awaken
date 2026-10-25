#version 330
#extension GL_ARB_separate_shader_objects : require
#define LENS_LIGHT
#include <awaken:lens_far.glsl>

// AWAKEN Lens, pass 1: the bright parts of the frame at a fraction of its size (what blooms), and in alpha how bright
// the open sky is (what light shafts are made of). Parameters arrive in a small texture (dev.awaken.lens.client.LensParams).
#include <awaken:lens_prefilter.glsl>
