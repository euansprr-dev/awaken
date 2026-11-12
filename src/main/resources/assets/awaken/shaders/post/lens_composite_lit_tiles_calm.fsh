#version 330
#extension GL_ARB_separate_shader_objects : require
#define LENS_LIGHT
#define LENS_UNIFORMS
#define LENS_NO_DEBUG
#define LENS_NOT_LITE
#define LENS_CALM
#include <awaken:lens_far.glsl>

// AWAKEN Lens 2 (Cinematic and Ultra), the Tiles twin's calm composite (SPEC §22 "Steady 60", the calm composite): the
// twin's composite (lens_composite_lit_tiles.fsh) without the paths that are off in a calm frame (LENS_CALM in
// lens_composite.glsl). Both sit in the twin's chain, this one first, each drawing into swap; Tiles leaves one out
// every frame (LensClient.leftOut): this one unless LensBlock found the frame calm, the twin's own when it did. A
// smaller shader keeps more pixels in flight on Apple's GPUs (some 0.5 to 1 ms of a 4K frame).
#include <awaken:lens_composite.glsl>
