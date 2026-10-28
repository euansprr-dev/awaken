#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight 2: far forests, one opaque voxel tree per instance, no vertex buffer. Vertex v is tree v / 20,
// corner v % 20: five quads, the crown's top and the two crown sides that face the camera, then the trunk's two. A
// tree's data (FarTrees) sits in the Trees buffer by its patch's slot; each slot's patch origin in TreeSlots. Shapes:
// broadleaf (a block crown on a trunk), conifer (a crown narrowing to its tip), jungle (a tall trunk under a wide
// crown), acacia (a flat wide crown). A predicted tree steps aside where the real chunks' trees are known. Every tree
// stands on the far mesh exactly as drawn there (meshGround: the ring's corners, real where known, morphed into the
// coarser ring at the window's edge, split along the mesh's own diagonals), never on the height it was planted at, so
// no tree floats where the ground has since been learned from the chunks or the city basin lowered it.

uniform usamplerBuffer Trees;      // per tree: position, ground, canopy, shape, crown colour, width
uniform isamplerBuffer TreeSlots;  // per patch slot: origin x, z (world blocks), ring
uniform sampler2D ShadeSampler;
uniform sampler2D GenHeightSampler;
uniform sampler2D HeightSampler;

layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vColor;
layout(location = 2) flat out vec4 vFace;   // xyz the face's normal, w the ground's sunlight
layout(location = 3) out vec2 vFaceUv;      // blocks across and up the face (leaf grain)
layout(location = 4) flat out float vValley; // how deep in a hollow the tree stands (valley cloud)

const int VERTS = 20, MAX_TREES = 512;

void hideTree() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vRel = vec3(0.0);
    vColor = vec3(0.0);
    vFace = vec4(0.0);
    vFaceUv = vec2(0.0);
    vValley = 0.0;
}

const float MORPH = 16.0;   // as farsight_terrain.vsh

ivec2 texelOf(int r, ivec2 l) {
    ivec4 t = FarTexel[r];
    return ivec2((t.x + l.x) & 255, t.z + ((t.y + l.y) & 255));
}

// a corner's height: the generated layer where it has it, else the prediction; < -1e8 if neither has arrived
float cornerH(int r, ivec2 l) {
    if (l.x < 0 || l.y < 0 || l.x > 255 || l.y > 255) return -1e9;
    ivec2 t = texelOf(r, l);
    float g = texelFetch(GenHeightSampler, t, 0).r;
    float h = g > 0.0 ? g : texelFetch(HeightSampler, t, 0).r;
    return h > 0.0 ? farHeight(h) : -1e9;
}

// the surface of ring r (or the next coarser one that has it) under p, bilinear as farsight_terrain.vsh's surfaceAt
bool surfaceH(int r, vec2 p, out float h) {
    h = 0.0;
    for (int k = r; k <= 6; k++) {
        if (FarRing[k].w < 0.5) continue;
        vec2 u = (p - FarRing[k].xy) / FarRing[k].z;
        ivec2 i0 = ivec2(floor(u));
        vec2 f = u - vec2(i0);
        float a = cornerH(k, i0), b = cornerH(k, i0 + ivec2(1, 0)), c = cornerH(k, i0 + ivec2(0, 1)), d = cornerH(k, i0 + ivec2(1, 1));
        if (f.x <= 1e-4) { b = a; d = c; }
        if (f.y <= 1e-4) { c = a; d = b; }
        if (min(min(a, b), min(c, d)) < -1e8) continue;
        h = mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        return true;
    }
    return false;
}

// a mesh vertex's height, as farsight_terrain.vsh's main builds it (its own corner eased into the coarser ring)
bool vertexH(int r, ivec2 l, out float h) {
    vec2 p = FarRing[r].xy + vec2(l) * FarRing[r].z;
    float e = float(min(min(l.x, l.y), min(256 - l.x, 256 - l.y)));
    float m = r < 6 ? clamp((MORPH - e) / (MORPH - 2.0), 0.0, 1.0) : 0.0;
    h = cornerH(r, l);
    bool own = h > -1e8;
    if (!own) m = 1.0;
    if (m > 0.0) {
        float k;
        if (surfaceH(r + 1, p, k)) h = own ? mix(h, k, m) : k;
        else if (!own) return false;
    }
    return true;
}

// the ground the far mesh draws under p: the cell's two triangles, split as the index buffer splits them
bool meshGround(int r, vec2 p, out float h) {
    h = 0.0;
    vec2 u = (p - FarRing[r].xy) / FarRing[r].z;
    ivec2 i0 = ivec2(floor(u));
    vec2 f = u - vec2(i0);
    float a, b, c, d;
    if (!vertexH(r, i0, a) || !vertexH(r, i0 + ivec2(1, 0), b) || !vertexH(r, i0 + ivec2(0, 1), c) || !vertexH(r, i0 + ivec2(1, 1), d)) return false;
    if (((i0.x ^ i0.y) & 1) == 0) {
        // diagonal from (0, 0) to (1, 1)
        h = f.y >= f.x ? a + f.x * (d - c) + f.y * (c - a) : a + f.x * (b - a) + f.y * (d - b);
    } else {
        // diagonal from (1, 0) to (0, 1)
        h = f.x + f.y <= 1.0 ? a + f.x * (b - a) + f.y * (c - a) : d + (1.0 - f.x) * (c - d) + (1.0 - f.y) * (b - d);
    }
    return true;
}

void main() {
    int v = gl_VertexIndex;
    int tree = v / VERTS, corner = v % VERTS, quad = corner / 4, q = corner & 3;
    int slotIndex = tree / MAX_TREES;
    bool generated = (slotIndex & 1) != 0;
    ivec4 slot = texelFetch(TreeSlots, slotIndex >> 1);
    uvec4 d = texelFetch(Trees, tree);
    int ring = slot.z;
    vec2 local = vec2(float(d.x & 0xFFFFu), float(d.x >> 16u)) / 16.0;
    // world position less FarCamInt, exactly, then the camera's fraction
    vec2 p = vec2(slot.xy - FarCamInt.xz) + local - FarCam.xz;
    float ground;
    float tall = float((d.y >> 16u) & 255u) / 4.0;
    int shape = int((d.y >> 24u) & 3u);
    float width = float(d.z >> 24u) / 8.0;
    vec3 crown = vec3(float(d.z & 255u), float((d.z >> 8u) & 255u), float((d.z >> 16u) & 255u)) / 255.0;
    // where the ring's own surface is: its corner under the tree (sunlight, and whether the real chunks know it)
    vec4 rp = FarRing[ring];
    ivec2 l = ivec2(floor((p - rp.xy) / rp.z + 0.5));
    if (rp.w < 0.5 || l.x < 0 || l.y < 0 || l.x > 255 || l.y > 255) { hideTree(); return; }
    ivec4 tx = FarTexel[ring];
    ivec2 texel = ivec2((tx.x + l.x) & 255, tx.z + ((tx.y + l.y) & 255));
    if (!generated && texelFetch(GenHeightSampler, texel, 0).r > 0.0) { hideTree(); return; }
    if (!meshGround(ring, p, ground)) { hideTree(); return; }
    // the finer ring draws its own trees over its window
    if (ring > 1) {
        vec4 inner = FarRing[ring - 1];
        vec2 a = inner.xy, b = inner.xy + 256.0 * inner.z;
        if (inner.w > 0.5 && p.x > a.x && p.y > a.y && p.x < b.x && p.y < b.y) { hideTree(); return; }
    }
    vec4 shade = texelFetch(ShadeSampler, texel, 0);
    float sun = shade.a;
    vValley = smoothstep(0.96, 0.78, shade.b);

    // the tree's parts: trunk to crown, by shape (heights from the ground, half widths; the crown's base and width as
    // FarTrees grows them from the tree's height)
    float crownBase = float(d.w & 255u) / 4.0, crownTop = tall, trunkTop = crownBase + 1.0;
    float halfW = width * 0.5, tip = halfW, trunkHalf = 0.5;
    if (shape == 1) {        // conifer
        tip = 0.5; trunkHalf = 0.4;
    } else if (shape == 2) { // jungle
        trunkHalf = 0.9;
    } else if (shape == 3) { // acacia
        crownTop = crownBase + 2.0; trunkTop = crownBase + 0.5;
    } else if (tall > 12.0) { // a tall broadleaf stands on a trunk two blocks wide, as the lumen and elderwood trees do
        trunkHalf = 1.0;
    }
    // the sides that face the camera
    float sx = p.x > 0.0 ? -1.0 : 1.0, sz = p.y > 0.0 ? -1.0 : 1.0;
    float a = (q == 1 || q == 2) ? 1.0 : 0.0, b = q >= 2 ? 1.0 : 0.0;   // along, up
    vec3 pos, n;
    vec3 col = crown;
    vec2 uv;
    if (quad == 0) {             // crown top
        pos = vec3(p.x + (a * 2.0 - 1.0) * tip, ground + crownTop, p.y + (b * 2.0 - 1.0) * tip);
        n = vec3(0.0, 1.0, 0.0);
        uv = vec2(a, b) * tip * 2.0;
    } else if (quad <= 2) {      // crown sides, narrowing toward the tip
        float w = mix(halfW, tip, b), y = ground + mix(crownBase, crownTop, b);
        if (quad == 1) { pos = vec3(p.x + sx * halfW * mix(1.0, tip / halfW, b), y, p.y + (a * 2.0 - 1.0) * w); n = vec3(sx, 0.0, 0.0); }
        else { pos = vec3(p.x + (a * 2.0 - 1.0) * w, y, p.y + sz * halfW * mix(1.0, tip / halfW, b)); n = vec3(0.0, 0.0, sz); }
        uv = vec2(a * w * 2.0, (crownTop - crownBase) * b);
    } else {                     // trunk
        float y = ground - 0.5 + (trunkTop + 0.5) * b;
        if (quad == 3) { pos = vec3(p.x + sx * trunkHalf, y, p.y + (a * 2.0 - 1.0) * trunkHalf); n = vec3(sx, 0.0, 0.0); }
        else { pos = vec3(p.x + (a * 2.0 - 1.0) * trunkHalf, y, p.y + sz * trunkHalf); n = vec3(0.0, 0.0, sz); }
        col = vec3(0.33, 0.25, 0.16);
        uv = vec2(-1.0);
    }
    vRel = vec3(pos.x, pos.y - FarFog.z, pos.z);
    vColor = col;
    vFace = vec4(n, sun);
    vFaceUv = uv;
    vec4 clip = ProjMat * (FarViewRot * vec4(vRel, 1.0));
    clip.z = FarDepth.x + FarDepth.y * clip.w;
    gl_Position = clip;
}
