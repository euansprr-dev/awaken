#ifndef LENS_BLOCK
#define LENS_BLOCK
// AWAKEN Lens on Apple (SPEC §22, docs/perf/LENS-APPLE.md): the frame's values (LensFrame, F) and the look's parameters
// (LensParams, P) in one uniform block, bound live by Tiles' hook. A value every pixel shares is one constant load from
// a uniform block, against a texture fetch per pixel per use from the 48×1 and 32×1 textures (about 70 a pixel in the lit
// composite, ~0.1 ms each at 4.7 MP on the M4). The chain declares the block with zeros, so where the hook isn't
// bound LensUse.x is 0 and F() and P() read the textures exactly as before.
layout(std140) uniform LensBlock {
    vec4 LensF[48];
    vec4 LensP[32];
    vec4 LensUse;
};
#endif
