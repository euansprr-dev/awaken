// AWAKEN Lens 2: the terrain's heights (dev.awaken.lens.client.LensHeightfield, 1024x1024 RG16F, a ring addressed by
// world block modulo 1024; r the top of everything that blocks motion, leaves included, g without leaves; mips 1-4 the
// max of their 2x2). Needs HeightSampler declared before it.

// the heightfield texel holding world column c at mip level lvl (ring addressing; & works on negatives)
vec2 heightAt(ivec2 c, int lvl) {
    return texelFetch(HeightSampler, (c & 1023) >> lvl, lvl).rg;
}
