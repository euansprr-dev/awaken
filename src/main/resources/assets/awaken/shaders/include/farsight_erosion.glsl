// AWAKEN Far Sight 2: Awakened Alpine's erosion (dev.awaken.terrain.alpine.Erosion) on the GPU, the same formulas and
// hash, for the gullies finer than a ring's cell: the mesh carries the coarse octaves, each pixel works out the slope
// of the fine ones and lights them. Positions are an exact world cell (integers) plus a fraction, so the pattern holds
// at any distance from the origin.

const int EROSION_OCTAVES = 5;
const float EROSION_CELL = 256.0, EROSION_HEIGHT = 40.0, EROSION_GAIN = 0.38, EROSION_CARVE = 0.28;
const float EROSION_FLAT = 0.04, EROSION_STEEP = 0.45, EROSION_GULLY = 0.6, EROSION_FOLLOW = 0.4;

uint erosionHash(int x, int z, int seed) {
    uint h = uint(x) * 0x27D4EB2Du ^ uint(z) * 0x165667B1u ^ uint(seed) * 0x5BD1E995u;
    h ^= h >> 15u;
    h *= 0x2C1B3C6Du;
    h ^= h >> 12u;
    h *= 0x297A2D39u;
    return h ^ (h >> 15u);
}

float erosionUnit(uint h) {
    return float(h & 0xFFFFFFu) / 16777216.0;
}

// one octave at a point given as an integer cell (base) plus a fraction (0..1) in that octave's cells, stripes across
// (px, pz): x the blended stripe (-1..1), yz its gradient in cell units
vec3 erosionOctave(ivec2 base, vec2 f, vec2 across, int octave) {
    float sum = 0.0, val = 0.0;
    vec2 grad = vec2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            ivec2 c = base + ivec2(i, j);
            uint h = erosionHash(c.x, c.y, octave);
            vec2 pivot = vec2(i, j) + 0.25 + 0.5 * vec2(erosionUnit(h), erosionUnit(h * 0x2C1B3C6Du));
            vec2 d = f - pivot;
            float d2 = dot(d, d);
            if (d2 >= 1.5625) continue;
            float k = 1.0 - d2 / 1.5625;
            k *= k * k;
            float phase = 6.2831855 * dot(d, across);
            sum += k;
            val += k * cos(phase);
            grad += -6.2831855 * sin(phase) * k * across;
        }
    }
    return sum <= 1e-6 ? vec3(0.0) : vec3(val, grad) / sum;
}

// the slope erosion's finer octaves add at a point: those whose cell lies between finest and coarsest (blocks), on
// ground whose slope there is g (rise per block; the mesh's, which already holds the coarser octaves, so each finer one
// follows it) at strength 0..1. world = cellOrigin (a multiple of 256) + rel.
vec2 erosionSlope(ivec2 cellOrigin, vec2 rel, vec2 g, float strength, float finest, float coarsest) {
    vec2 slope = vec2(0.0);
    float cell = EROSION_CELL, amp = EROSION_HEIGHT * strength;
    for (int o = 0; o < EROSION_OCTAVES; o++, cell *= 0.5, amp *= EROSION_GAIN) {
        if (cell < finest) break;
        if (cell > coarsest) continue;
        vec2 s = g + EROSION_FOLLOW * slope;
        float len = length(s);
        float fade = smoothstep(EROSION_FLAT, EROSION_STEEP, len);
        if (fade <= 0.0) continue;
        vec2 across = vec2(-s.y, s.x) / len;
        vec2 u = rel / cell;
        ivec2 base = cellOrigin / int(cell) + ivec2(floor(u));
        vec3 st = erosionOctave(base, fract(u), across, o);
        float v = st.x;
        float profile = v < 0.0 ? 1.0 - 2.0 * EROSION_GULLY * v : 1.0;
        slope += amp * fade * profile / cell * st.yz;
    }
    return slope;
}
