#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <awaken:farsight.glsl>

// AWAKEN Far Sight: one ring piece, no vertex buffer. Cell i of the piece is vertices 12i..12i+11: its flat top, then
// the wall on the side facing the camera in x, then in z. A wall stands only where this cell is higher than that
// neighbour, so every visible wall is drawn once, by the higher cell. Near its window's edge a ring eases into the
// next coarser one, so the two meet without a seam; a cell not predicted yet takes the coarser ring's value. In the
// near rings a predicted forest grows treetops (a deterministic hash per cell, the same whichever ring asks).

layout(std140) uniform FarDraw {
    ivec4 FarPiece;     // ring, first cell x and z (window-local), width in cells
};

uniform sampler2D HeightSampler;
uniform sampler2D ColorSampler;
uniform sampler2D ShadeSampler;
uniform sampler2D GenHeightSampler;    // the generated layer: what the real chunks seen there show; wins where it has a cell
uniform sampler2D GenColorSampler;

layout(location = 0) out vec3 vColor;  // unlit
layout(location = 1) out vec3 vRel;
layout(location = 2) out float vWater; // the water's depth in blocks (0 on land)
layout(location = 3) out float vLight;
layout(location = 4) out vec4 vEdge;   // top: blocks from the camera-facing x and z edges, and the steps there (+ up, - down)
layout(location = 5) out float vDepth; // wall: blocks below its top edge (-1 on tops)

const vec3 ROCK = vec3(0.50, 0.49, 0.47);

ivec2 texelOf(int r, ivec2 l) {
    ivec4 t = FarTexel[r];
    return ivec2((t.x + l.x) & 255, t.z + ((t.y + l.y) & 255));
}

bool inWindow(ivec2 l) {
    return l.x >= 0 && l.y >= 0 && l.x < 256 && l.y < 256;
}

// treetops of a predicted forest in rings 0-2: most cells rise a little, the rest are gaps nearer the forest floor
void canopy(int r, ivec2 t, float trees, inout float h, inout vec3 col) {
    if (r > 2 || trees < 0.5) return;
    float k = farHash(t + ivec2(r * 7919, 0));
    if (k < 0.66) {
        h += floor(farHash(t.yx + ivec2(31, r)) * 2.5);
        col *= 1.06;
    } else {
        h -= floor(trees * 0.6);
        col *= 0.72;
    }
}

// one texel's cell: the generated layer where it has one, else the prediction (with its treetops)
bool cellAt(int r, ivec2 t, out float h, out vec3 col, out float water) {
    vec4 hv = texelFetch(GenHeightSampler, t, 0);
    bool generated = hv.a > 0.5;
    if (!generated) hv = texelFetch(HeightSampler, t, 0);
    h = farHeight(hv);
    water = floor(hv.b * 255.0 + 0.5);
    vec4 c = generated ? texelFetch(GenColorSampler, t, 0) : texelFetch(ColorSampler, t, 0);
    col = c.rgb;
    float a = floor(c.a * 255.0 + 0.5);
    if (!generated && a < 254.5) canopy(r, t, (a - 15.0) / 16.0, h, col);
    return hv.a > 0.5;
}

// ring r's cell under a point (blocks from the camera), if that ring has it
bool sampleAt(int r, vec2 p, out float h, out vec3 col, out vec4 shade, out float water) {
    h = 0.0;
    col = vec3(0.0);
    shade = vec4(0.5, 0.5, 1.0, 1.0);
    water = 0.0;
    if (r > 6 || FarRing[r].w < 0.5) return false;
    ivec2 l = ivec2(floor((p - FarRing[r].xy) / FarRing[r].z));
    if (!inWindow(l)) return false;
    ivec2 t = texelOf(r, l);
    if (!cellAt(r, t, h, col, water)) return false;
    shade = texelFetch(ShadeSampler, t, 0);
    return true;
}

// what cell l of ring r draws: its own value, eased toward the coarser ring over its window's outer 14 cells (fully
// there in the outer 2), or the coarser rings' value while its own is still being predicted
bool cellValue(int r, ivec2 l, out float h, out vec3 col, out vec4 shade, out float water) {
    vec2 centre = FarRing[r].xy + (vec2(l) + 0.5) * FarRing[r].z;
    ivec2 t = texelOf(r, l);
    bool own = cellAt(r, t, h, col, water);
    shade = texelFetch(ShadeSampler, t, 0);
    float e = float(min(min(l.x, l.y), min(255 - l.x, 255 - l.y)));
    float w = own ? clamp((14.0 - e) / 12.0, 0.0, 1.0) : 1.0;
    if (w <= 0.0 || r >= 6) return own;
    float h2, w2;
    vec3 c2;
    vec4 s2;
    if (sampleAt(r + 1, centre, h2, c2, s2, w2)) {
        h = mix(h, h2, w);
        col = mix(col, c2, w);
        shade = mix(shade, s2, w);
        water = w > 0.5 ? w2 : water;
        return true;
    }
    if (own) return true;
    return sampleAt(r + 2, centre, h, col, shade, water);
}

// the neighbour's drawn top: inside the window as that cell draws itself, past the edge from the coarser ring
bool neighbourTop(int r, ivec2 l, out float h) {
    float w;
    vec3 c;
    vec4 s;
    if (inWindow(l)) return cellValue(r, l, h, c, s, w);
    return sampleAt(r + 1, FarRing[r].xy + (vec2(l) + 0.5) * FarRing[r].z, h, c, s, w);
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vColor = vec3(0.0);
    vRel = vec3(0.0);
    vWater = 0.0;
    vLight = 0.0;
    vEdge = vec4(0.0);
    vDepth = -1.0;
}

void main() {
    int v = gl_VertexIndex;
    int cell = v / 12, quad = (v >> 2) % 3, corner = v & 3;
    int r = FarPiece.x;
    ivec2 l = ivec2(FarPiece.y + cell % FarPiece.w, FarPiece.z + cell / FarPiece.w);
    float s = FarRing[r].z;
    vec2 o = FarRing[r].xy + vec2(l) * s;
    float h, water;
    vec3 col;
    vec4 shade;
    if (!cellValue(r, l, h, col, shade, water)) {
        hide();
        return;
    }
    float top = h - FarFog.z;
    // the camera sits at the origin: the cell's sides that face it, in x and in z
    int sideX = o.x + s * 0.5 < 0.0 ? 1 : -1, sideZ = o.y + s * 0.5 < 0.0 ? 1 : -1;
    // corners go round the quad: along it (a) 0 1 1 0, up it (b) 1 1 0 0
    float a = (corner == 1 || corner == 2) ? 1.0 : 0.0;
    float b = corner < 2 ? 1.0 : 0.0;
    vec3 pos, n;
    float face;
    vEdge = vec4(0.0);
    vDepth = -1.0;
    if (quad == 0) {
        float x = a, z = 1.0 - b;
        pos = vec3(o.x + x * s, top, o.y + z * s);
        n = vec3(shade.r * 2.0 - 1.0, 0.0, shade.g * 2.0 - 1.0);
        n.y = sqrt(max(1.0 - dot(n.xz, n.xz), 0.0));
        face = 1.0;
        // the steps at the two camera-facing edges, for the terrace lines (rim where it drops, shadow where it rises)
        float hx = h, hz = h;
        neighbourTop(r, l + ivec2(sideX, 0), hx);
        neighbourTop(r, l + ivec2(0, sideZ), hz);
        vEdge = vec4((sideX > 0 ? 1.0 - x : x) * s, (sideZ > 0 ? 1.0 - z : z) * s, hx - h, hz - h);
    } else {
        bool xWall = quad == 1;
        int side = xWall ? sideX : sideZ;
        float hn;
        if (!neighbourTop(r, l + (xWall ? ivec2(side, 0) : ivec2(0, side)), hn) || hn >= h - 0.01) {
            hide();
            return;
        }
        float y = mix(hn - FarFog.z, top, b);
        vDepth = (1.0 - b) * (h - hn);
        if (xWall) {
            pos = vec3(side > 0 ? o.x + s : o.x, y, o.y + a * s);
            n = vec3(float(side), 0.0, 0.0);
            face = 0.8;
        } else {
            pos = vec3(o.x + a * s, y, side > 0 ? o.y + s : o.y);
            n = vec3(0.0, 0.0, float(side));
            face = 0.9;
        }
    }
    // steep ground is bare rock, whatever grows on the flats around it
    if (quad == 0 && water < 0.5) {
        float slope = length(n.xz) / max(n.y, 0.05);
        col = mix(col, ROCK, smoothstep(0.9, 1.8, slope) * 0.8);
    }
    // sun from the slope (soft: the game's own light already sets day and night), valleys a little darker
    float lambert = max(dot(n, normalize(FarSun.xyz)), 0.0);
    vLight = face * (0.72 + 0.28 * lambert) * (quad == 0 ? shade.b : mix(1.0, shade.b, 0.5));
    vColor = col;
    vRel = pos;
    vWater = quad == 0 ? water : 0.0;
    vec4 clip = ProjMat * (FarViewRot * vec4(pos, 1.0));
    clip.z = FarDepth.x + FarDepth.y * clip.w;
    gl_Position = clip;
}
