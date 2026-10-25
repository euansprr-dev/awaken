#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight 2: one ring piece as a continuous mesh, no vertex buffer. Vertex v of a piece is its grid corner
// (v % 65, v / 65) from the piece's first corner; one index buffer per piece width makes the triangles. A corner takes
// its ring's ground (the generated layer where the real chunks were seen, else the prediction), and over the window's
// outer band eases into the next coarser ring's surface (bilinear between its corners), reaching it exactly at the edge,
// where the coarser ring's mesh begins: the rings meet with no crack and no pop. A corner whose own data hasn't arrived
// takes the coarser rings' surface whole, so there is never a hole.

layout(std140) uniform FarDraw {
    ivec4 FarPiece;     // ring, first corner x and z (window-local)
};

uniform sampler2D HeightSampler;
uniform sampler2D ColorSampler;
uniform sampler2D InfoSampler;
uniform sampler2D CrownSampler;
uniform sampler2D GenHeightSampler;
uniform sampler2D GenColorSampler;
uniform sampler2D GenInfoSampler;
uniform sampler2D GenCrownSampler;
uniform sampler2D ShadeSampler;

layout(location = 0) out vec3 vRel;          // blocks from the camera
layout(location = 1) out vec3 vNormal;
layout(location = 2) out vec3 vColor;        // the ground's colour
layout(location = 3) out vec4 vInfo;         // water depth, canopy height, tree density (as stored), valley shade
layout(location = 4) out vec4 vCrown;        // crown colour, sunlight (1 lit)
layout(location = 5) flat out ivec2 vFlags;  // colour flags, crown shape
layout(location = 6) out float vCell;        // this ring's cell size (blocks)

const int STRIDE = 65;
const float MORPH = 16.0;   // cells of the window's outer band over which a ring eases into the next

struct Corner {
    float h;
    vec2 n;        // the normal's x and z
    vec3 col;
    vec4 info;     // r depth, g canopy, b density, a valley
    vec4 crown;    // rgb, a sunlight
    int flags, shape;
};

ivec2 texelOf(int r, ivec2 l) {
    ivec4 t = FarTexel[r];
    return ivec2((t.x + l.x) & 255, t.z + ((t.y + l.y) & 255));
}

bool inWindow(ivec2 l) {
    return l.x >= 0 && l.y >= 0 && l.x < 256 && l.y < 256;
}

// one corner's data: the generated layer where it has it, else the prediction; false if neither has arrived
bool cornerAt(int r, ivec2 l, out Corner c) {
    c.h = 0.0; c.n = vec2(0.0); c.col = vec3(0.0); c.info = vec4(0.0); c.crown = vec4(0.0, 0.0, 0.0, 1.0); c.flags = 0; c.shape = 0;
    if (!inWindow(l)) return false;
    ivec2 t = texelOf(r, l);
    float gh = texelFetch(GenHeightSampler, t, 0).r;
    bool gen = gh > 0.0;
    float hh = gen ? gh : texelFetch(HeightSampler, t, 0).r;
    if (hh <= 0.0) return false;
    c.h = farHeight(hh);
    vec4 col = gen ? texelFetch(GenColorSampler, t, 0) : texelFetch(ColorSampler, t, 0);
    vec4 info = gen ? texelFetch(GenInfoSampler, t, 0) : texelFetch(InfoSampler, t, 0);
    vec4 predCrown = texelFetch(CrownSampler, t, 0);
    vec4 crown = gen ? texelFetch(GenCrownSampler, t, 0) : predCrown;
    vec4 shade = texelFetch(ShadeSampler, t, 0);
    c.col = col.rgb;
    c.flags = int(col.a * 255.0 + 0.5);
    // a generated texel keeps the prediction's flags (snow line, cold) as well as its own
    if (gen) c.flags |= int(texelFetch(ColorSampler, t, 0).a * 255.0 + 0.5) & 127;
    c.info = vec4(info.rgb, shade.b);
    c.crown = vec4(crown.rgb, shade.a);
    c.shape = int(predCrown.a * 255.0 + 0.5);
    c.n = shade.rg * 2.0 - 1.0;
    return true;
}

// the surface of ring r (or the next coarser one that has it) under point p (blocks from the camera)
bool surfaceAt(int r, vec2 p, out Corner c) {
    for (int k = r; k <= 6; k++) {
        if (FarRing[k].w < 0.5) continue;
        vec2 u = (p - FarRing[k].xy) / FarRing[k].z;
        ivec2 i0 = ivec2(floor(u));
        vec2 f = u - vec2(i0);
        Corner a, b, cc, d;
        bool ka = cornerAt(k, i0, a);
        bool kb = f.x <= 1e-4 || cornerAt(k, i0 + ivec2(1, 0), b);
        bool kc = f.y <= 1e-4 || cornerAt(k, i0 + ivec2(0, 1), cc);
        bool kd = f.x <= 1e-4 || f.y <= 1e-4 || cornerAt(k, i0 + ivec2(1, 1), d);
        if (!(ka && kb && kc && kd)) continue;
        if (f.x <= 1e-4) { b = a; d = cc; }
        if (f.y <= 1e-4) { cc = a; d = b; }
        float wa = (1.0 - f.x) * (1.0 - f.y), wb = f.x * (1.0 - f.y), wc = (1.0 - f.x) * f.y, wd = f.x * f.y;
        c.h = a.h * wa + b.h * wb + cc.h * wc + d.h * wd;
        c.n = a.n * wa + b.n * wb + cc.n * wc + d.n * wd;
        c.col = a.col * wa + b.col * wb + cc.col * wc + d.col * wd;
        c.info = a.info * wa + b.info * wb + cc.info * wc + d.info * wd;
        c.crown = a.crown * wa + b.crown * wb + cc.crown * wc + d.crown * wd;
        c.flags = a.flags;
        c.shape = a.shape;
        return true;
    }
    return false;
}

// nothing known here yet (the first second, before even the horizon ring has arrived): open sea, which the horizon fog
// and the coarse ring's first patches soon replace. Never a collapsed vertex: those leave slivers across the screen.
Corner sea() {
    Corner c;
    c.h = FarFog.w;
    c.n = vec2(0.0);
    c.col = vec3(0.17, 0.27, 0.42);
    c.info = vec4(64.0 / 255.0, 0.0, 0.0, 1.0);
    c.crown = vec4(0.0, 0.0, 0.0, 1.0);
    c.flags = 0;
    c.shape = 0;
    return c;
}

void main() {
    int v = gl_VertexIndex;
    int r = FarPiece.x;
    ivec2 l = FarPiece.yz + ivec2(v % STRIDE, v / STRIDE);
    float s = FarRing[r].z;
    vec2 p = FarRing[r].xy + vec2(l) * s;
    // how far into the outer band: 1 at the window's edge (the coarser ring's surface exactly), 0 inside
    float e = float(min(min(l.x, l.y), min(256 - l.x, 256 - l.y)));
    float m = r < 6 ? clamp((MORPH - e) / (MORPH - 2.0), 0.0, 1.0) : 0.0;
    Corner c;
    bool own = cornerAt(r, l, c);
    // the outermost ring has nothing coarser: its far edge takes the corner just inside
    if (!own && r == 6) own = cornerAt(r, min(l, ivec2(255)), c);
    if (!own) m = 1.0;
    if (m > 0.0) {
        Corner k;
        if (surfaceAt(r + 1, p, k)) {
            if (own) {
                c.h = mix(c.h, k.h, m);
                c.n = mix(c.n, k.n, m);
                c.col = mix(c.col, k.col, m);
                c.info = mix(c.info, k.info, m);
                c.crown = mix(c.crown, k.crown, m);
                if (m > 0.5) { c.flags = k.flags; c.shape = k.shape; }
            } else {
                c = k;
            }
        } else if (!own) {
            c = sea();
        }
    }
    vec3 n = vec3(c.n.x, 0.0, c.n.y);
    n.y = sqrt(max(1.0 - dot(c.n, c.n), 0.0));
    vRel = vec3(p.x, c.h - FarFog.z, p.y);
    vNormal = n;
    vColor = c.col;
    vInfo = c.info;
    vCrown = c.crown;
    vFlags = ivec2(c.flags, c.shape);
    vCell = s;
    vec4 clip = ProjMat * (FarViewRot * vec4(vRel, 1.0));
    clip.z = FarDepth.x + FarDepth.y * clip.w;
    gl_Position = clip;
}
