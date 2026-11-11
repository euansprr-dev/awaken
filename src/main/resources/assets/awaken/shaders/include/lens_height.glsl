// AWAKEN Lens 2: the terrain's heights (dev.awaken.lens.client.LensHeightfield, 1024x1024 RG16F, a ring addressed by
// world block modulo 1024; r the top of everything that blocks motion, leaves included, g without leaves; mips 1-4 the
// max of their 2x2). Needs HeightSampler declared before it.

// the heightfield texel holding world column c at mip level lvl (ring addressing; & works on negatives)
vec2 heightAt(ivec2 c, int lvl) {
    return texelFetch(HeightSampler, (c & 1023) >> lvl, lvl).rg;
}

// Whether a surface is a block's own face: the point just behind it lies in its column's solid. A creature's flank, a
// dropped item, anything that is not a block has open air behind it there. (Leaves are not counted: the solid top is
// without them. Under a solid roof everything reads as a block, a creature too: the heightfield has one top a column.)
// w: the point in the world, n: its normal.
bool blockFace(vec3 w, vec3 n) {
    vec3 q = w - n * 0.06;
    return q.y < heightAt(ivec2(floor(q.xz)), 0).g;
}
