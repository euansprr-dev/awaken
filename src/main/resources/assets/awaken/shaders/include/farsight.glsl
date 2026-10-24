// AWAKEN Far Sight (dev.awaken.farsight, SPEC §16): what the far-terrain shaders share. The rings' atlases are
// 256 x (256*7) RGBA8 texels, one 256x256 block per ring (ring k's block starts at row 256k), addressed toroidally:
// height texel r, g the drawn top (+2048), b the water's depth (0 on land), a known; colour texel the cell's colour;
// shade texel the smoothed normal's x and z and the valley shade (baked by farsight_bake). A predicted forest's colour
// texel carries its canopy height in alpha (16 * height + 15; 255 elsewhere), for the near rings' procedural treetops.

layout(std140) uniform FarFrame {
    mat4 FarViewRot;
    vec4 FarDepth;      // clip.z = x + y * clip.w (Far Sight's own planes); z where far terrain starts (horizontal
                        // blocks from the camera), w the dither band over which it fades in
    vec4 FarFog;        // x, y Far Sight's horizon fog start and end; z the camera's y; w sea level
    vec4 FarSun;        // xyz the light's direction (world space)
    vec4 FarCam;        // the camera's x and z modulo 4096 (block texture stays put as it moves), y its y
    vec4 FarRing[7];    // per ring: x, z of the window's first cell (blocks from the camera), cell size, 1 if placed
    ivec4 FarTexel[7];  // per ring: the window's first cell as a texel (x, z), the ring's first atlas row
};

float farHeight(vec4 h) {
    return floor(h.r * 255.0 + 0.5) * 256.0 + floor(h.g * 255.0 + 0.5) - 2048.0;
}

// a cheap, well-spread hash of two integers to [0, 1)
float farHash(ivec2 p) {
    uint h = uint(p.x) * 374761393u + uint(p.y) * 668265263u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return float(h ^ (h >> 16u)) / 4294967296.0;
}
