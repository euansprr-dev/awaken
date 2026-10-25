// AWAKEN Far Sight 2 (dev.awaken.farsight, SPEC §20): what the far-terrain shaders share. Each ring's atlases are
// 256 x (256*7) texels, one 256x256 block per ring (ring k's block starts at row 256k), addressed toroidally. A texel
// is the ring's grid corner (i * cell, j * cell), so a coarse ring's corners are every other corner of the finer ring:
//   height (R16): (top + 1024) * 16 / 65535, 0 unknown;
//   colour (RGBA8): the ground's colour; alpha flags: 1 alpine (Awakened Alpine's snow line), 2 cold (snows),
//                   4 badlands, 8 stony, 128 generated (the real chunks' colour);
//   info (RGBA8): r the water's depth / 255, g the canopy's height * 4 / 255, b tree density;
//   crown (RGBA8): the trees' crown colour, their shape in alpha (0 broadleaf, 1 conifer, 2 jungle, 3 acacia);
//   shade (RGBA8, baked): the normal's x and z (* 0.5 + 0.5), b valley shade, a sunlight (1 lit, 0 behind a ridge).

layout(std140) uniform FarFrame {
    mat4 FarViewRot;
    vec4 FarDepth;      // clip.z = x + y * clip.w (Far Sight's own planes); z where far terrain starts (horizontal
                        // blocks from the camera), w the dither band over which it fades in
    vec4 FarFog;        // x, y the horizon fade's start and end; z the camera's y; w sea level
    vec4 FarSun;        // xyz toward the light (world space), w the sun's strength (0 night .. 1 noon)
    vec4 FarCam;        // x, z the camera's position less FarCamInt (0..256), y parts off (measuring: 1 detail, 4 mist);
                        // w the debug view
    vec4 FarRing[7];    // per ring: x, z of the window's first corner (blocks from the camera), cell size, 1 if placed
    ivec4 FarTexel[7];  // per ring: the window's first corner as a texel (x, z), the ring's first atlas row
    vec4 FarSunColor;   // rgb the sunlight's colour, a golden hour (0..1)
    vec4 FarAir;        // x haze per block at sea level, y its scale height (blocks), z sun in-scatter, w rain (0..1)
    ivec4 FarCamInt;    // the camera's x and z rounded down to a multiple of 256 (exact world cells for the shaders)
    vec4 FarMist;       // valley cloud: x its floor (y), y its depth (blocks), z density per block, w how much of the sky
                        // it covers (0 none .. 1 a cloud sea)
};

float farHeight(float texel) {
    return texel * (65535.0 / 16.0) - 1024.0;
}

// a cheap, well-spread hash of two integers to [0, 1)
float farHash(ivec2 p) {
    uint h = uint(p.x) * 374761393u + uint(p.y) * 668265263u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return float(h ^ (h >> 16u)) / 4294967296.0;
}

// Awakened Alpine's rule (dev.awaken.terrain.alpine.AlpineSurface): the same numbers
const float SNOW_LINE = 196.0, SNOW_WANDER = 9.0, NORTH_DROP = 22.0, SOUTH_LIFT = 14.0;
const float SNOW_SLOPE = 1.0, SCREE_SLOPE = 1.25, ROCK_SLOPE = 1.7;
const float ERODE_FROM = 74.0, ERODE_FULL = 140.0;
