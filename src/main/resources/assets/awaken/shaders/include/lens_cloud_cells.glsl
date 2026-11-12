// AWAKEN Lens, the clouds' layer and the Air's cloud cells (SPEC §22): what every pass that meets a cloud shares, apart
// from the volumetric march's own noise (lens_clouds.glsl). Needs lens_frame.glsl before it (frame texels 18, 22), and
// CloudCellSampler bound by any pass that reads a cell.
const float CLOUD_THICK = 84.0;

// x layer base (0: none), y cover, z/w the camera's x and z plus the wind (modulo 30720)
vec4 cloudParams() { return F(18); }

// The Air's clouds (SPEC §22 "Steady Light"; frame texel 22 w): the game's own cloud, made soft. Flat-bottomed blocks
// twelve across, in one to three tiers of seven that step in as they rise, so a group mounds up toward its middle;
// lit and shaded through their volume like any cloud here, and thin enough that the sky shows through their edges.
const float CLOUD_CELL = 12.0, CLOUD_TIER = 7.0;
// the Air's blocks nearer than this are drawn at the frame's own size, every frame (lens_cloud_blocks.fsh); the far
// ones, small and going into the haze, keep to the small cloud buffer
const float CLOUD_NEAR = 640.0;

bool cloudBlocks() { return F(22).w > 0.0; }
// under the layer's base by this much, and this thick
float cloudUnder() { return cloudBlocks() ? 2.0 : 6.0; }
float cloudThick() { return cloudBlocks() ? 3.0 * CLOUD_TIER : CLOUD_THICK; }

// The Air's cloud cells: one texel a twelve-block cell, 128 across (the noise repeats every 1536 blocks, which is 128
// cells, so the whole field is this one small texture: tools/lens/cloud_cells.py). 16 bits, red the high byte.
uniform sampler2D CloudCellSampler;

// how deep inside a group of cloud a twelve-block cell stands (0 or less: open sky)
float cloudCell(vec2 cell, float cover) {
    vec2 t = texelFetch(CloudCellSampler, ivec2(cell) & 127, 0).rg;
    return (t.r * 65280.0 + t.g * 255.0) * (1.25 / 65535.0) - (1.0 - cover * 0.72);
}

float cloudBlock(vec3 rel, vec4 cp, float camY) {
    float y = rel.y + camY - (cp.x - 2.0);
    if (y <= 0.0 || y >= 3.0 * CLOUD_TIER) return 0.0;
    float v = cloudCell(floor((rel.xz + cp.zw) / CLOUD_CELL), cp.y);
    if (v <= 0.0) return 0.0;
    float top = CLOUD_TIER * (1.0 + floor(min(v / 0.055, 2.0)));
    return smoothstep(0.0, 1.5, y) * (1.0 - smoothstep(top - 2.0, top, y));
}
