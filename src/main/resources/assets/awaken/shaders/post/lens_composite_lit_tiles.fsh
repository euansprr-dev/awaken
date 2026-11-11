#version 330
#extension GL_ARB_separate_shader_objects : require
#define LENS_LIGHT
#define LENS_UNIFORMS
#define LENS_NO_DEBUG
#include <awaken:lens_far.glsl>

// AWAKEN Lens 2 (Cinematic and Ultra), the final pass of the Tiles twin (SPEC §22 "Steady 60"): lens_composite_lit.fsh
// with the frame's and the look's values read from Lens's uniform block alone and without the development views. Its
// chains (lens_cinematic_tiles, lens_ultra_tiles; tools/lens/block.py) run only while Tiles binds the live block, where
// the composite already took every value from the block; the texture read beside each one made it a per-pixel register,
// and a pass that size ran some 4 ms slower at 4K for registers alone.
#include <awaken:lens_composite.glsl>
